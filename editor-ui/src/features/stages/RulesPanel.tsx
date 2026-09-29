import { extractRows, replaceRows } from "../../lib/tomlRows";
import { useEffect, useMemo, useRef, useState } from "react";
import { ACTION_LABELS, CATEGORIES, CONDITIONS, EFFECTS, EXACT_SELECTOR_CATEGORIES, LEGACY_RULE_BINDINGS, SPECIALIZED_RULE_GUIDANCE, ruleEffects } from "../../data";
import { InlineCatalogSearch } from "../../components/CatalogPicker";
import { Badge, Button, EmptyState, Field, Section, Toggle } from "../../components/ui";
import { Icon } from "../../components/Icon";
import {
  enchantmentGenerationRules,
  MAX_ENCHANTMENT_LEVEL,
  MAX_ENCHANTMENT_SELECTION_WEIGHT,
  normalizeEnchantmentId,
  validateEnchantmentGenerationRule,
  writeEnchantmentGenerationRules
} from "../../lib/enchantments";
import { serializeInventoryCondition, serializeInventoryInsertionRule, updateInventoryInsertionRule } from "../../lib/inventoryInsertion";
import { ruleModels, selectorMode, title } from "../../lib/model";
import { moveRuleTable, updateGenericRule } from "../../lib/ruleSource";
import { appendTomlBlock, booleanValue, conditionToml, encodeToml, extractArrayGroups, lineValues, numberValue, parseSimpleArray, readBlockValue, readTomlValue, removeTomlValue, replaceArrayGroups, stringValue, upsertToml } from "../../lib/toml";
import { newline, requireEditable, samePath, scanToml } from "../../lib/tomlSource";
import { useEditor } from "../../store/EditorContext";
import type { EnchantmentGenerationRule } from "../../lib/enchantments";
import type { RuleModel, StagePackage } from "../../types";

const MIN_STRUCTURE_PRIORITY = -2147483648;
const MAX_STRUCTURE_PRIORITY = 2147483647;

interface RuleDraft {
  stageState: string;
  ruleId: string;
  category: string;
  action: string;
  effect: string;
  selector: string;
  mode: string;
  priority: number;
  viewer: string;
  lifetime: string;
  duration: string;
  conditionType: string;
  conditionTarget: string;
  count: number;
  resetConditionType: string;
  resetConditionTarget: string;
  resetCount: number;
  conditionSource: string;
  resetConditionSource: string;
  exception: string;
  exceptionPriority: number;
  recipeKind: "output" | "identifier";
  targetKind: "block" | "menu" | "inventory";
  destination: string;
  destinationMode: string;
}

function effectLabel(category: string, effect: string, action = ""): string {
  if (category === "interactions" && action === "item_into_inventory") {
    if (effect === "lock" || effect === "deny") return "Deny insertion until this stage is owned";
    if (effect === "allow" || effect === "unlock") return "Allow insertion after this stage is owned";
    if (effect === "exclude") return "Always allow this exact item and destination";
  }
  if (category === "structures") {
    if (effect === "allow" || effect === "unlock") return "Allow access to structure";
    if (effect === "lock" || effect === "deny") return "Deny access to structure";
  }
  if (category === "abilities") {
    if (effect === "allow" || effect === "unlock") return "Allow this ability";
    if (effect === "lock" || effect === "deny") return "Block this ability";
  }
  if (category === "entities" && action === "presence") {
    if (effect === "allow" || effect === "unlock") return "Allow the entity to exist for this player";
    if (effect === "lock" || effect === "deny") return "Hide the entity and make it pacifist";
  }
  return EFFECTS.find(value => value.value === effect)?.label || title(effect);
}

function ruleGroups(text: string, table: "rules" | "temporary_rules"): { text: string }[] {
  return extractArrayGroups(text, table);
}

function replaceRuleGroup(text: string, table: "rules" | "temporary_rules", index: number, replacement: string | null): string {
  const groups = ruleGroups(text, table);
  if (!groups[index]) throw new Error("The rule changed in another edit. Reopen it and try again.");
  const replacements = groups.map(entry => entry.text);
  if (replacement == null) replacements.splice(index, 1);
  else replacements[index] = replacement;
  return replaceArrayGroups(text, table, replacements);
}

function replaceInteractionGroup(text: string, index: number, replacement: string | null): string {
  const groups = extractRows(text, "interactions");
  if (!groups[index]) throw new Error("The interaction changed in another edit. Reopen it and try again.");
  const replacements = groups.map(entry => entry.text);
  if (replacement == null) replacements.splice(index, 1);
  else replacements[index] = replacement;
  return replaceRows(text, "interactions", replacements);
}

function removeClassicRule(text: string, rule: RuleModel): string {
  if (rule.table === "recipe_items" || rule.table === "recipe_ids") {
    const field = rule.table === "recipe_items" ? "locked_items" : "locked_ids";
    const path = `recipes.${field}`;
    const values = parseSimpleArray(readTomlValue(text, path));
    const index = values.findIndex(value => value.replace(/\|priority=-?\d+$/, "") === rule.selector);
    if (index >= 0) {
      values.splice(index, 1);
      return upsertToml(text, path, values);
    }
    return text;
  }
  for (const field of rule.classicField ? [rule.classicField] : ["locked", "allowed", "always_unlocked"]) {
    const path = `${rule.category}.${field}`;
    const values = parseSimpleArray(readTomlValue(text, path));
    const index = values.findIndex(value => value.replace(/\|priority=-?\d+$/, "") === rule.selector);
    if (index >= 0) {
      values.splice(index, 1);
      return upsertToml(text, path, values);
    }
  }
  return text;
}

function canonicalRecipeField(recipeKind: RuleDraft["recipeKind"]): "locked_items" | "locked_ids" {
  return recipeKind === "output" ? "locked_items" : "locked_ids";
}

function legacyField(category: string, action: string, effect: string): string | undefined {
  const binding = LEGACY_RULE_BINDINGS[category];
  if (!binding) return undefined;
  if (effect === "exclude") return binding.alwaysUnlocked;
  return typeof binding.locked === "string" ? binding.locked : binding.locked[action];
}

function exactSelector(value: string): boolean {
  return !/^(?:all|mod|tag|tags|name):/i.test(value.trim()) && !value.trim().startsWith("#");
}

function genericRuleIssue(category: string, action: string, effect: string, selector: string): string | undefined {
  const specialized = SPECIALIZED_RULE_GUIDANCE[category];
  if (specialized) return `This generic rule does not run. ${specialized.detail}`;
  if (category === "recipes") return "Generic recipe rules do not run. Use the recipe output item or exact recipe ID fields.";
  if (category === "interactions") return "Inventory insertion rules use [[interactions]] with an item and destination. Other interaction pairs use Access controls.";
  if (category === "loot") return "Loot rules use [loot].locked or [loot].always_unlocked with a loot table ID.";
  if (category === "curios") return "Curios rules use [curios].locked_slots with an exact slot ID.";
  if (!CATEGORIES[category]) return "This category has no built-in rule editor. Its source is preserved; edit it through the owning integration or remove it.";
  if (action === "replace" && category === "mobs") return "Mob replacement uses [[mobs.replacements]], not a generic rule.";
  if (action === "ride" && category === "pets") return "Pet riding uses targets.entities with action = \"mount\".";
  if ((action === "leave" || action === "stay") && category === "structures") return "Use progression conditions for leaving a structure or time spent inside it.";
  if (!CATEGORIES[category].actions.includes(action)) return `The server does not handle action \"${action}\" for ${CATEGORIES[category].label.toLowerCase()}. Choose a listed action.`;
  if (!ruleEffects(category, action).some(value => value.value === effect)) return `The server does not apply result \"${effect}\" to ${CATEGORIES[category].label.toLowerCase()} rules. Choose a supported result.`;
  if (EXACT_SELECTOR_CATEGORIES.has(category) && !exactSelector(selector)) return `${CATEGORIES[category].label} rules need one exact ID. Mod, tag, name, and all selectors do not match these checks.`;
  return undefined;
}

function genericRuleCanBeRepaired(category: string): boolean {
  return Boolean(CATEGORIES[category])
    && !SPECIALIZED_RULE_GUIDANCE[category]
    && !["recipes", "interactions", "loot", "curios"].includes(category);
}

function normalizeLegacySelector(category: string, selector: string): string {
  const binding = LEGACY_RULE_BINDINGS[category];
  const value = selector.trim();
  return binding && ["exact", "ability", "slot"].includes(binding.selector)
    ? value.replace(/^id:/i, "") : value;
}

type BlockOverrideTable = "blocks.overrides" | "ores.overrides";
interface BlockOverrideModel {
  table: BlockOverrideTable;
  tableIndex: number;
  targets: string[];
  displayAs: string;
  dropAs: string;
  priority?: number;
  sourceText: string;
}

function blockOverrideModels(text: string): BlockOverrideModel[] {
  return (["blocks.overrides", "ores.overrides"] as const).flatMap(table =>
    extractArrayGroups(text, table).map(block => {
      const multiple = readBlockValue(block.text, "targets");
      const single = readBlockValue(block.text, "target");
      const targets = multiple ? parseSimpleArray(multiple).map(stringValue)
        : single ? [stringValue(single)] : [];
      const rawPriority = readBlockValue(block.text, "priority");
      const priority = rawPriority ? Number(rawPriority) : undefined;
      return { table, tableIndex: block.index, targets,
        displayAs: stringValue(readBlockValue(block.text, "display_as")),
        dropAs: stringValue(readBlockValue(block.text, "drop_as")),
        priority: Number.isInteger(priority) ? priority : undefined,
        sourceText: block.text };
    }));
}

function normalizeBlockSelector(mode: string, value: string): string {
  const selector = value.trim();
  if (/^(?:id|tag|tags|mod):/i.test(selector)) {
    return selector.replace(/^tags:/i, "tag:");
  }
  return mode === "mod" ? `mod:${selector}`
    : mode === "tag" ? `tag:${selector.replace(/^#/, "")}`
      : `id:${selector}`;
}

function patchBlockOverride(source: string, table: BlockOverrideTable,
                            draft: { targets: string[]; displayAs: string; dropAs: string; priority: string }): string {
  const parsed = scanToml(source);
  requireEditable(parsed);
  const header = parsed.tables[0];
  if (!header?.array || !samePath(header.path, table.split("."))) {
    throw new Error("The override source is not an array entry. Reopen it and try again.");
  }
  const targetKey = draft.targets.length === 1 ? "target" : "targets";
  const desired = new Map<string, string>([
    [targetKey, encodeToml(draft.targets.length === 1 ? draft.targets[0] : draft.targets)],
    ["display_as", encodeToml(draft.displayAs.trim().replace(/^id:/i, ""))],
    ["drop_as", encodeToml(draft.dropAs.trim().replace(/^id:/i, ""))]
  ]);
  if (draft.priority.trim()) desired.set("priority", String(Number(draft.priority)));
  const edits: Array<{ start: number; end: number; text: string }> = [];
  const seen = new Set<string>();
  for (const value of parsed.values.filter(entry => entry.table === header && entry.key.length === 1)) {
    const key = value.key[0];
    if (!["target", "targets", "display_as", "drop_as", "priority"].includes(key)) continue;
    const destination = key === "target" || key === "targets" ? targetKey : key;
    const encoded = desired.get(destination);
    const suffix = source.slice(value.valueEnd, value.end);
    if (encoded !== undefined && !seen.has(destination)) {
      const indent = source.slice(value.start, value.valueStart).match(/^\s*/)?.[0] || "";
      edits.push({ start: value.start, end: value.end,
        text: `${indent}${destination} = ${encoded}${suffix}` });
      seen.add(destination);
    } else {
      const note = suffix.match(/#[^\r\n]*/)?.[0];
      edits.push({ start: value.start, end: value.end, text: note ? `${note}${newline(source)}` : "" });
    }
  }
  const added = [...desired].filter(([key]) => !seen.has(key))
    .map(([key, encoded]) => `${key} = ${encoded}${newline(source)}`).join("");
  if (added) edits.push({ start: header.end, end: header.end, text: added });
  let updated = source;
  for (const edit of edits.sort((left, right) => right.start - left.start)) {
    updated = updated.slice(0, edit.start) + edit.text + updated.slice(edit.end);
  }
  return updated;
}

function serializeBlockOverride(draft: { targets: string[]; displayAs: string; dropAs: string; priority: string }): string {
  const lines = ["[[blocks.overrides]]"];
  lines.push(`${draft.targets.length === 1 ? "target" : "targets"} = ${encodeToml(draft.targets.length === 1 ? draft.targets[0] : draft.targets)}`);
  lines.push(`display_as = ${encodeToml(draft.displayAs.trim().replace(/^id:/i, ""))}`);
  lines.push(`drop_as = ${encodeToml(draft.dropAs.trim().replace(/^id:/i, ""))}`);
  if (draft.priority.trim()) lines.push(`priority = ${Number(draft.priority)}`);
  return lines.join("\n");
}

function canonicalRecipeSelector(draft: RuleDraft): string {
  const selector = draft.recipeKind === "identifier" ? draft.selector.replace(/^id:/, "").trim() : draft.selector.trim();
  return `${selector}|priority=${draft.priority}`;
}

function saveCanonicalRecipeRule(text: string, draft: RuleDraft, previous?: RuleModel): string {
  let updated = text;
  if (previous) {
    if (previous.table === "recipe_items" || previous.table === "recipe_ids" || previous.table === "classic") {
      updated = removeClassicRule(updated, previous);
    } else if (previous.table === "interactions") {
      updated = replaceInteractionGroup(updated, previous.tableIndex, null);
    } else {
      updated = replaceRuleGroup(updated, previous.table, previous.tableIndex, null);
    }
  }
  const path = `recipes.${canonicalRecipeField(draft.recipeKind)}`;
  const values = parseSimpleArray(readTomlValue(updated, path));
  const selector = canonicalRecipeSelector(draft);
  if (!values.includes(selector)) values.push(selector);
  return upsertToml(updated, path, values);
}

function saveInventoryInsertionRule(text: string, draft: RuleDraft, previous?: RuleModel): string {
  const next = {
    id: draft.ruleId,
    selector: draft.selector,
    targetKind: draft.targetKind,
    destination: draft.destination,
    effect: draft.effect as "lock" | "deny" | "allow" | "unlock" | "exclude",
    priority: draft.priority,
    lifetime: draft.lifetime,
    duration: draft.duration,
    condition: serializeInventoryCondition(draft.conditionSource, draft.conditionType, draft.conditionTarget, draft.count),
    resetCondition: serializeInventoryCondition(draft.resetConditionSource, draft.resetConditionType, draft.resetConditionTarget, draft.resetCount)
  };
  const block = serializeInventoryInsertionRule(next);
  if (previous?.table === "interactions") {
    if (extractRows(text, "interactions")[previous.tableIndex]?.text !== previous.sourceText) throw new Error("The interaction changed in another edit. Reopen it and try again.");
    const prior = {
      id: previous.id || "", selector: previous.selector,
      targetKind: previous.targetKind || "block", destination: previous.destination || "",
      effect: (previous.effect === "deny" ? "lock" : previous.effect === "unlock" ? "allow" : previous.effect) as "lock" | "allow" | "exclude",
      priority: previous.priority, lifetime: previous.lifetime, duration: previous.duration,
      condition: serializeInventoryCondition(previous.conditionSource || "", previous.conditionType, previous.conditionTarget, previous.count),
      resetCondition: serializeInventoryCondition(previous.resetConditionSource || "", previous.resetConditionType || "none", previous.resetConditionTarget || "", previous.resetCount || 1)
    };
    return replaceInteractionGroup(text, previous.tableIndex, updateInventoryInsertionRule(previous.sourceText, next, prior));
  }
  if (previous) {
    const withoutPrevious = previous.table === "classic" || previous.table === "recipe_items" || previous.table === "recipe_ids"
      ? removeClassicRule(text, previous)
      : replaceRuleGroup(text, previous.table as "rules" | "temporary_rules", previous.tableIndex, null);
    return replaceRows(withoutPrevious, "interactions", [...extractRows(withoutPrevious, "interactions").map(row => row.text), block]);
  }
  return replaceRows(text, "interactions", [...extractRows(text, "interactions").map(row => row.text), block]);
}

function serializeRule(stage: StagePackage, draft: RuleDraft, table: "rules" | "temporary_rules", previous?: RuleModel): string {
  const previousId = previous?.sourceText.match(/^\s*id\s*=\s*["']([^"']+)/m)?.[1];
  const id = previousId || `${stage.id}/${table === "rules" ? "rule" : "temporary_rule"}_${Date.now().toString(36)}`;
  const lines = [
    `[[${table}]]`,
    `id = ${encodeToml(id)}`,
    `effect = ${encodeToml(draft.effect)}`,
    `stage_state = ${encodeToml(draft.stageState)}`,
    `priority = ${draft.priority}`,
    `action = ${encodeToml(draft.action)}`,
    `targets.${draft.category} = ${encodeToml([draft.selector])}`
  ];
  if (table === "temporary_rules") lines.push(`lifetime = ${encodeToml(draft.lifetime)}`);
  if (draft.duration) lines.push(`duration = ${encodeToml(draft.duration)}`);
  if (draft.conditionType !== "none") lines.push(`while = ${conditionToml(draft.conditionType, draft.conditionTarget, draft.count)}`);
  if (draft.resetConditionType !== "none") lines.push(`reset_condition = ${conditionToml(draft.resetConditionType, draft.resetConditionTarget, draft.resetCount)}`);
  if (draft.viewer !== "inherit") {
    lines.push(`presentation.jei = ${encodeToml(draft.viewer)}`);
    lines.push(`presentation.emi = ${encodeToml(draft.viewer)}`);
  }
  if (draft.exception) {
    lines.push("", `[[${table}.exceptions]]`, `effect = "exclude"`,
      `priority = ${draft.exceptionPriority}`, `targets.${draft.category} = ${encodeToml([draft.exception])}`);
  }
  return lines.join("\n");
}

function RuleForm({ stage, rule }: { stage: StagePackage; rule?: RuleModel }) {
  const { boot, mutateFile, closeDialog } = useEditor();
  const [saveError, setSaveError] = useState("");
  const source = boot?.draft.files[stage.rulesPath] || "";
  const initialCategory = rule?.category || "items";
  const [draft, setDraft] = useState<RuleDraft>({
    stageState: rule?.stageState || (["allow", "unlock", "deny"].includes(rule?.effect || "lock") ? "owned" : "missing"),
    ruleId: rule?.id || "",
    category: initialCategory,
    action: rule?.action || (stage.legacy
      ? LEGACY_RULE_BINDINGS[initialCategory]?.actions[0] || (initialCategory === "interactions" ? "item_into_inventory" : CATEGORIES[initialCategory].actions[0])
      : CATEGORIES[initialCategory].actions[0]),
    effect: rule?.action === "item_into_inventory"
      ? rule.effect === "deny" ? "lock" : rule.effect === "unlock" ? "allow" : rule.effect || "lock"
      : rule?.effect || "lock",
    selector: rule?.selector || "",
    mode: selectorMode(rule?.selector || ""),
    priority: rule?.priority ?? (stage.legacy ? numberValue(readTomlValue(source, "stage.priority")) : 100),
    viewer: rule?.viewer || "inherit",
    lifetime: rule?.lifetime || "permanent",
    duration: rule?.duration || "",
    conditionType: rule?.conditionType || "none",
    conditionTarget: rule?.conditionTarget || "",
    count: rule?.count || 1,
    resetConditionType: rule?.resetConditionType || "none",
    resetConditionTarget: rule?.resetConditionTarget || "",
    resetCount: rule?.resetCount || 1,
    conditionSource: rule?.conditionSource || "",
    resetConditionSource: rule?.resetConditionSource || "",
    exception: rule?.exception || "",
    exceptionPriority: rule?.exception ? rule.exceptionPriority : (rule?.priority ?? 100) + 1,
    recipeKind: rule?.recipeKind || "output",
    targetKind: rule?.targetKind || "block",
    destination: rule?.destination || "",
    destinationMode: selectorMode(rule?.destination || "")
  });
  const update = <K extends keyof RuleDraft>(key: K, value: RuleDraft[K]) => setDraft(current => ({ ...current, [key]: value }));
  const category = CATEGORIES[draft.category] || { label: title(draft.category), catalog: "", actions: [], description: "" };
  const specializedRuleGuidance = SPECIALIZED_RULE_GUIDANCE[draft.category];
  const condition = CONDITIONS.find(entry => entry.id === draft.conditionType);
  const temporary = draft.lifetime !== "permanent";
  const selectsEverything = draft.mode === "all";
  const legacyBinding = LEGACY_RULE_BINDINGS[draft.category];
  const canonicalRecipe = draft.category === "recipes" && draft.action === "craft"
    && draft.effect === "lock" && !temporary;
  const inventoryInsertion = draft.category === "interactions" && draft.action === "item_into_inventory";
  const ambiguousRecipe = Boolean(rule?.ambiguous);
  const legacyCategory = (stage.legacy || ["loot", "curios"].includes(draft.category)) && !canonicalRecipe && !inventoryInsertion;
  const effects = legacyCategory
    ? EFFECTS.filter(effect => effect.value === "lock" || effect.value === "exclude" && Boolean(legacyBinding?.alwaysUnlocked))
    : ruleEffects(draft.category, draft.action);
  const actions = inventoryInsertion ? ["item_into_inventory"]
    : legacyCategory ? legacyBinding?.actions || [] : category.actions;
  const actionOptions = actions.includes(draft.action) ? actions : [draft.action, ...actions];
  const targetCatalog = canonicalRecipe
    ? draft.recipeKind === "output" ? "items" : "recipes"
    : category.catalog;
  const targetLabel = canonicalRecipe
    ? draft.recipeKind === "output" ? "Recipe output item" : "Exact recipe identifier"
    : "Selected target";
  const targetPlaceholder = canonicalRecipe && draft.recipeKind === "identifier" ? "minecraft:diamond_sword"
    : draft.mode === "mod" ? "mod:minecraft"
    : draft.mode === "tag" ? targetCatalog === "blocks" ? "tag:minecraft:logs" : "tag:namespace:group"
    : draft.mode === "name" ? "name:diamond"
    : targetCatalog === "blocks" ? "id:minecraft:stone" : "id:minecraft:diamond_sword";
  const categoryOptions = Object.entries(CATEGORIES).filter(([id]) => {
    const supportedByStageFormat = !stage.legacy || id === "interactions" || Object.hasOwn(LEGACY_RULE_BINDINGS, id);
    return supportedByStageFormat && (!SPECIALIZED_RULE_GUIDANCE[id] || id === draft.category);
  });
  const selectorMethods = legacyCategory
    ? draft.effect === "exclude" || legacyBinding?.selector !== "resource"
      ? ["id"] : ["all", "id", "mod", "tag", "name"]
    : EXACT_SELECTOR_CATEGORIES.has(draft.category) ? ["id"] : ["all", "id", "mod", "tag", "name"];
  const existingGenericRule = rule?.table === "rules" || rule?.table === "temporary_rules";
  const conversionIssue = existingGenericRule && inventoryInsertion
    ? "An existing generic rule cannot be converted into an inventory insertion here. Add a separate [[interactions]] entry so its conditions and exceptions stay intact."
    : existingGenericRule && canonicalRecipe
      ? "An existing generic rule cannot be converted into a recipe list here. Add the recipe lock separately so its settings stay intact."
      : undefined;
  const issue = conversionIssue || (!legacyCategory && !canonicalRecipe && !inventoryInsertion
    ? genericRuleIssue(draft.category, draft.action, draft.effect, draft.selector) : undefined);
  const save = async (event: React.FormEvent) => {
    event.preventDefault();
    setSaveError("");
    try {
      if (specializedRuleGuidance) throw new Error(`This generic rule does not run. ${specializedRuleGuidance.detail}`);
      if (issue) throw new Error(issue);
      if (ambiguousRecipe) throw new Error("[recipes].locked is ambiguous. Remove it, then create either a recipe output item or exact recipe identifier lock.");
      if (draft.category === "recipes" && draft.action === "craft"
        && (temporary || draft.conditionType !== "none" || draft.resetConditionType !== "none" || draft.effect !== "lock")) {
        throw new Error("Crafting locks require a permanent stage rule without an activation condition. Use progression conditions to grant or revoke the stage.");
      }
      if (draft.category === "interactions" && !inventoryInsertion) throw new Error("Use Access, Direct interactions to edit an item and block or entity pair. This rule requires both selectors.");
      if (!draft.selector || inventoryInsertion && !draft.destination) return;
      let content = boot?.draft.files[stage.rulesPath] || "";
      if (inventoryInsertion) {
        if (rule?.table === "rules" || rule?.table === "temporary_rules") throw new Error("Changing a generic rule into an inventory insertion rule requires an explicit Source edit to preserve its existing settings.");
        content = saveInventoryInsertionRule(content, draft, rule);
      } else if (canonicalRecipe) {
        if (rule?.table === "rules" || rule?.table === "temporary_rules") throw new Error("Changing a generic rule into a crafting lock requires an explicit Source edit to preserve its existing settings.");
        if (draft.viewer !== "inherit" || draft.exception) throw new Error("Crafting lists cannot store these presentation or exception settings. Edit them in Source before saving this lock.");
        content = saveCanonicalRecipeRule(content, draft, rule);
      } else if (legacyCategory) {
        if (temporary || draft.conditionType !== "none") throw new Error("Conditional rules need a three file stage package.");
        if (draft.viewer !== "inherit" || draft.exception) throw new Error("Static category lists cannot store per rule presentation or nested exceptions. Add a separate always allowed entry instead.");
        if (!["lock", "exclude"].includes(draft.effect)) throw new Error("Static category lists support locks and always allowed entries. Use a generic rule for other results.");
        if (rule && rule.table !== "classic") throw new Error("Edit this rule in Source to preserve its format.");
        const binding = LEGACY_RULE_BINDINGS[draft.category];
        if (!binding) throw new Error("This category has no legacy list form. Use its nearby specialized controls or edit the accepted table in Source.");
        if (draft.effect === "exclude" && (!binding.alwaysUnlocked || !exactSelector(draft.selector))) {
          throw new Error("Always allowed entries need an exact ID. Do not use a tag, mod, name, or wildcard selector.");
        }
        if (rule) content = removeClassicRule(content, rule);
        const field = rule?.effect === draft.effect && rule.classicField ? rule.classicField
          : legacyField(draft.category, draft.action, draft.effect);
        if (!field) throw new Error("This category does not support that result in legacy stage files.");
        const path = `${draft.category}.${field}`;
        content = upsertToml(content, path,
          [...parseSimpleArray(readTomlValue(content, path)), normalizeLegacySelector(draft.category, draft.selector)]);
      } else {
        const previousGeneric = rule?.table === "rules" || rule?.table === "temporary_rules" ? rule : undefined;
        if (previousGeneric && ruleGroups(content, previousGeneric.table as "rules" | "temporary_rules")[previousGeneric.tableIndex]?.text !== previousGeneric.sourceText) {
          throw new Error("The rule changed in another edit. Reopen it and try again.");
        }
        const table: "rules" | "temporary_rules" = previousGeneric && draft.lifetime === previousGeneric.lifetime
          ? previousGeneric.table as "rules" | "temporary_rules" : temporary ? "temporary_rules" : "rules";
        const block = previousGeneric ? moveRuleTable(updateGenericRule(previousGeneric.sourceText, draft, previousGeneric), table, previousGeneric)
          : serializeRule(stage, draft, table, rule);
        if (rule?.table === "interactions") content = appendTomlBlock(replaceInteractionGroup(content, rule.tableIndex, null), block);
        else if (rule?.table === "classic" || rule?.table === "recipe_items" || rule?.table === "recipe_ids") content = appendTomlBlock(removeClassicRule(content, rule), block);
        else if (rule && rule.table !== table) {
          content = replaceRuleGroup(content, rule.table as "rules" | "temporary_rules", rule.tableIndex, null);
          content = appendTomlBlock(content, block);
        } else if (rule) content = replaceRuleGroup(content, table, rule.tableIndex, block);
        else content = appendTomlBlock(content, block);
      }
      await mutateFile(stage.rulesPath, content, "Rule saved to the draft");
      closeDialog();
    } catch (failure) { setSaveError(failure instanceof Error ? failure.message : "The rule was not saved."); }
  };
  return <form className="dialog-form" onSubmit={save}>
    <div className="rule-sentence"><span>When a player tries to</span><strong>{ACTION_LABELS[draft.action] || title(draft.action)}</strong><span>the server will</span><strong>{effectLabel(draft.category, draft.effect, draft.action)}</strong></div>
    <div className="form-grid">
      <Field label="Rule category"><select value={draft.category} onChange={event => {
        const next = event.target.value;
        if (!CATEGORIES[next]) {
          setSaveError("Choose a supported rule category. Ore display and drop changes belong in Block overrides.");
          return;
        }
        const binding = LEGACY_RULE_BINDINGS[next];
        const action = next === "interactions" ? "item_into_inventory"
          : stage.legacy ? binding?.actions[0] || CATEGORIES[next].actions[0] : CATEGORIES[next].actions[0];
        setDraft(current => ({ ...current, category: next, action,
          effect: ["recipes", "interactions"].includes(next) || stage.legacy && !binding?.alwaysUnlocked ? "lock" : current.effect,
          mode: EXACT_SELECTOR_CATEGORIES.has(next) || stage.legacy && binding?.selector !== "resource" ? "id" : current.mode,
          selector: current.mode === "all" && !EXACT_SELECTOR_CATEGORIES.has(next) ? "all:*" : "", destination: "" }));
      }}>{categoryOptions.map(([id, value]) => <option key={id} value={id}>{value.label}</option>)}</select></Field>
      <Field label="Player action" help={legacyCategory ? "Static category lists use the stage priority." : "Choose one action, or choose Any action in this category to cover every matching server check."}><select disabled={legacyCategory} value={draft.action} onChange={event => {
        const action = event.target.value;
        setDraft(current => ({
          ...current,
          action,
          effect: action === "item_into_inventory" || current.category === "recipes" && action === "craft"
            ? "lock"
            : current.effect
        }));
      }}>{actionOptions.map((action, index) => <option key={`${action}-${index}`} value={action}>{actions.includes(action) ? ACTION_LABELS[action] || title(action) : `Unsupported action: ${action}`}</option>)}</select></Field>
      <Field label="Result" help="Choose whether a matching action is denied, allowed after this stage, or exempt from the broader rule. The choices update for this rule category."><select value={draft.effect} onChange={event => setDraft(current => ({ ...current, effect: event.target.value, stageState: ["lock", "exclude"].includes(event.target.value) ? "missing" : "owned" }))}>{effects.map(effect => <option key={effect.value} value={effect.value}>{effectLabel(draft.category, effect.value, draft.action)}</option>)}</select></Field>
      <Field label={legacyCategory ? "Stage priority" : "Rule priority"} help={legacyCategory
        ? "Legacy lists use the stage priority. Change it in the stage settings or use a three file stage for per rule priorities."
        : "A larger number wins when rules overlap."}><input type="number" value={draft.priority} disabled={legacyCategory} onChange={event => update("priority", Number(event.target.value))}/></Field>
    </div>
    {issue || specializedRuleGuidance ? <div className="form-errors" role="alert"><strong>{issue || `This generic rule is not applied. ${specializedRuleGuidance?.title}.`}</strong><p>Change the category, action, result, or selector to a supported choice. Remove the old entry if it should not remain in the file.</p></div> : null}
    <section className="dialog-section"><header><span className="step-number">1</span><div><h3>{inventoryInsertion ? "Choose what moves where" : "Choose the target"}</h3><p>{inventoryInsertion ? "The server checks the inserted item and destination together before it changes any slot." : `The registry only shows content valid for ${category.label.toLowerCase()}.`}</p></div></header><div className="form-grid">
      {inventoryInsertion ? <>
        <Field label="Item selection method"><select value={draft.mode} onChange={event => {
          const mode = event.target.value;
          setDraft(current => ({ ...current, mode, selector: mode === "all" ? "all:*" : current.mode === "all" ? "" : current.selector }));
        }}><option value="all">Every item</option><option value="id">One exact item</option><option value="mod">Everything from a mod</option><option value="tag">Everything in a tag</option><option value="name">Anything with a matching name</option></select></Field>
        <Field label="Inserted item" help={draft.mode === "all" ? "This matches every registered item." : "Choose the item the player is trying to insert."}><input value={draft.selector} onChange={event => update("selector", event.target.value)} placeholder="id:minecraft:diamond" readOnly={draft.mode === "all"} required/></Field>
        {draft.mode !== "all" ? <div className="field-wide"><InlineCatalogSearch catalogId="items" mode={draft.mode} onPick={value => update("selector", value)}/></div> : null}
        <Field label="Destination type" help="Block uses the container block. Menu uses the open menu type. Inventory uses a stable server owner identity."><select value={draft.targetKind} onChange={event => setDraft(current => ({ ...current, targetKind: event.target.value as RuleDraft["targetKind"], destination: "", destinationMode: "id" }))}><option value="block">Container block</option><option value="menu">Open menu type</option><option value="inventory">Inventory owner</option></select></Field>
        <Field label="Destination selection method"><select value={draft.destinationMode} onChange={event => {
          const mode = event.target.value;
          setDraft(current => ({ ...current, destinationMode: mode, destination: mode === "all" ? "all:*" : current.destinationMode === "all" ? "" : current.destination }));
        }}><option value="all">Every matching destination</option><option value="id">One exact identifier</option><option value="mod">Everything from a mod</option><option value="tag">Everything in a tag</option><option value="name">Anything with a matching name</option></select></Field>
        <Field label="Destination" help={draft.destinationMode === "all" ? "This matches every destination of the selected type." : "Choose the receiving inventory identity."}><input value={draft.destination} onChange={event => update("destination", event.target.value)} placeholder={draft.targetKind === "inventory" ? "id:minecraft:player_inventory" : "id:minecraft:chest"} readOnly={draft.destinationMode === "all"} required/></Field>
        {draft.destinationMode !== "all" ? <div className="field-wide"><InlineCatalogSearch catalogId={draft.targetKind === "block" ? "blocks" : draft.targetKind === "menu" ? "menus" : "inventory_targets"} mode={draft.destinationMode} onPick={value => update("destination", value)}/></div> : null}
      </> : <>
      {canonicalRecipe ? <Field label="Recipe lock kind" help="Output locks use item selectors. Identifier locks name one exact recipe."><select value={draft.recipeKind} onChange={event => setDraft(current => ({ ...current, recipeKind: event.target.value as RuleDraft["recipeKind"], mode: event.target.value === "identifier" ? "id" : current.mode, selector: "" }))}><option value="output">Recipe output item</option><option value="identifier">Exact recipe identifier</option></select></Field> : null}
      {canonicalRecipe && draft.recipeKind === "identifier" ? null : <Field label="Selection method"><select value={draft.mode} onChange={event => {
        const mode = event.target.value;
        setDraft(current => ({ ...current, mode, selector: mode === "all" ? "all:*"
          : current.mode === "all" || selectorMode(current.selector) !== mode ? "" : current.selector }));
      }}>{!selectorMethods.includes(draft.mode) ? <option value={draft.mode}>Unsupported method: {title(draft.mode)}</option> : null}{selectorMethods.map(mode => <option key={mode} value={mode}>{mode === "all" ? "Everything in this category" : mode === "id" ? "One exact identifier" : mode === "mod" ? "Everything from a mod" : mode === "tag" ? "Everything in a tag" : "Anything with a matching name"}</option>)}</select></Field>}
      <Field label={targetLabel} help={selectsEverything ? `This matches every registered ${canonicalRecipe && draft.recipeKind === "output" ? "recipe output item" : category.label.toLowerCase()}. Add a higher priority exception to allow selected content.` : undefined}><input value={draft.selector} onChange={event => update("selector", event.target.value)} placeholder={targetPlaceholder} readOnly={selectsEverything} required/></Field>
      {!selectsEverything && selectorMethods.includes(draft.mode) ? <div className="field-wide"><InlineCatalogSearch catalogId={targetCatalog} mode={canonicalRecipe && draft.recipeKind === "identifier" ? "id" : draft.mode} onPick={value => update("selector", canonicalRecipe && draft.recipeKind === "identifier"
        ? value.replace(/^id:/, "") : legacyCategory ? normalizeLegacySelector(draft.category, value) : value)}/></div> : null}
      </>}
    </div></section>
    <section className="dialog-section"><header><span className="step-number">2</span><div><h3>Choose when it participates</h3><p>Permanent rules follow stage ownership. Conditional rules can follow locations, events, sessions, and scripts.</p></div></header><div className="form-grid">
      {!inventoryInsertion && !canonicalRecipe && !legacyCategory ? <Field label="Stage ownership" help="Choose whether this rule participates before or after the player obtains this stage."><select value={draft.stageState} onChange={event => update("stageState", event.target.value)}><option value="missing">While the stage is missing</option><option value="owned">While the stage is owned</option><option value="always">Regardless of stage ownership</option>{!["missing", "owned", "always"].includes(draft.stageState) ? <option value={draft.stageState}>{title(draft.stageState)}</option> : null}</select></Field> : null}
      {inventoryInsertion ? <Field label="Rule identity" help="Optional. Set this when a timed rule must keep the same timer through reordering."><input value={draft.ruleId} onChange={event => update("ruleId", event.target.value)} placeholder="yourpack:ore_bin_window"/></Field> : null}
      {!legacyCategory ? <Field label="Activation condition"><select value={draft.conditionType} onChange={event => update("conditionType", event.target.value)}>{draft.conditionType === "custom" ? <option value="custom">Keep existing compound condition</option> : null}{CONDITIONS.map(entry => <option key={entry.id} value={entry.id}>{entry.label}</option>)}</select></Field> : <p className="field-wide">This static list follows stage ownership. It cannot use extra conditions or a timer.</p>}
      {!legacyCategory ? <><Field label="Condition target" help={condition?.help}><input disabled={draft.conditionType === "custom"} value={draft.conditionTarget} onChange={event => update("conditionTarget", event.target.value)} placeholder={condition?.catalog ? "Choose a registered identifier" : "Optional value"}/></Field>
      {condition?.catalog ? <div className="field-wide"><InlineCatalogSearch catalogId={condition.catalog} mode="id" onPick={value => update("conditionTarget", value.replace(/^id:/, ""))}/></div> : null}
      <Field label="Required amount"><input type="number" min={1} disabled={draft.conditionType === "custom"} value={draft.count} onChange={event => update("count", Number(event.target.value))}/></Field>
      <Field label="Lifetime"><select value={draft.lifetime} onChange={event => update("lifetime", event.target.value)}><option value="permanent">Permanent stage rule</option><option value="live">Only while the condition is true</option><option value="duration">Timed after the trigger</option><option value="session">Current session</option><option value="latched">Active until reset</option><option value="schedule">Scheduled lifetime</option></select></Field>
      {draft.lifetime === "duration" || draft.lifetime === "schedule" ? <Field label="Duration or schedule"><input value={draft.duration} onChange={event => update("duration", event.target.value)} placeholder="30s or 5m"/></Field> : null}
      {draft.lifetime !== "permanent" ? <><Field label="Reset condition"><select value={draft.resetConditionType} onChange={event => update("resetConditionType", event.target.value)}>{draft.resetConditionType === "custom" ? <option value="custom">Keep existing compound condition</option> : null}{CONDITIONS.map(entry => <option key={entry.id} value={entry.id}>{entry.label}</option>)}</select></Field>
      {draft.resetConditionType !== "none" ? <><Field label="Reset target"><input disabled={draft.resetConditionType === "custom"} value={draft.resetConditionTarget} onChange={event => update("resetConditionTarget", event.target.value)} placeholder="Optional value or identifier"/></Field><Field label="Reset amount"><input type="number" min={1} disabled={draft.resetConditionType === "custom"} value={draft.resetCount} onChange={event => update("resetCount", Number(event.target.value))}/></Field></> : null}</> : null}</> : null}
    </div></section>
    {!inventoryInsertion && !canonicalRecipe && !legacyCategory ? <section className="dialog-section"><header><span className="step-number">3</span><div><h3>Presentation and exception</h3><p>An exception normally needs a larger priority than the broader rule.</p></div></header><div className="form-grid">
      <Field label="JEI and EMI"><select value={draft.viewer} onChange={event => update("viewer", event.target.value)}><option value="inherit">Follow normal policy</option>{draft.viewer === "custom" ? <option value="custom">Keep separate viewer settings</option> : null}<option value="show">Always show</option><option value="hide">Hide</option><option value="overlay">Show with a locked overlay</option></select></Field>
      <Field label="Optional exception selector"><input value={draft.exception} onChange={event => update("exception", event.target.value)} placeholder="tag:c:swords"/></Field>
      <Field label="Exception priority"><input type="number" value={draft.exceptionPriority} onChange={event => update("exceptionPriority", Number(event.target.value))}/></Field>
    </div></section> : null}
    {saveError ? <p role="alert">{saveError}</p> : null}
    {ambiguousRecipe ? <div className="form-errors" role="alert"><strong>This legacy recipe field is ambiguous.</strong><p>Remove it, then create a recipe output item or exact recipe identifier lock.</p></div> : null}
    <footer className="dialog-actions"><Button type="button" tone="quiet" onClick={closeDialog}>Cancel</Button><Button type="submit" tone="primary" disabled={Boolean(specializedRuleGuidance) || Boolean(issue) || ambiguousRecipe || !draft.selector || inventoryInsertion && !draft.destination}>{rule ? "Update rule" : "Add rule"}</Button></footer>
  </form>;
}

function RuleCard({ stage, rule, index, total, onEdit, onDelete, onMove }:
  { stage: StagePackage; rule: RuleModel; index: number; total: number; onEdit: () => void; onDelete: () => void; onMove: (direction: number) => void }) {
  const category = CATEGORIES[rule.category];
  const generic = rule.table === "rules" || rule.table === "temporary_rules";
  const issue = generic ? genericRuleIssue(rule.category, rule.action, rule.effect, rule.selector) : undefined;
  const canEdit = !rule.ambiguous && (!issue || genericRuleCanBeRepaired(rule.category));
  const movable = rule.table === "rules" || rule.table === "temporary_rules";
  const selector = rule.table === "interactions"
    ? `${rule.selector} into ${title(rule.targetKind || "inventory")} ${rule.destination || ""}`.trim()
    : rule.selector;
  const moveUnavailable = !movable ? "This older rule list uses a fixed order." : "This rule is already at the edge of the list.";
  return <article className="rule-card-new">
    <div className="rule-card-icon"><Icon name="rules" size={19}/></div>
    <div className="rule-card-main"><div className="rule-card-title"><strong>{category?.label || title(rule.category)}</strong><Badge tone={rule.effect === "allow" || rule.effect === "unlock" || rule.effect === "exclude" ? "success" : "danger"} help={effectLabel(rule.category, rule.effect, rule.action)}>{title(rule.effect)}</Badge>{rule.table !== "classic" ? <Badge help={rule.ineffectivePriority ? "This static list ignores its inline priority and uses the stage priority." : "Higher-priority matching rules take precedence when rules conflict."}>Priority {rule.priority}</Badge> : null}</div><code>{selector}</code><p>{issue ? issue : rule.ambiguous ? "This legacy recipe field is ambiguous. Remove it, then create an output item or exact recipe identifier lock." : rule.ineffectivePriority ? "This static list ignores the inline priority. It uses the stage priority." : rule.table === "classic" ? "This static list uses the stage priority." : rule.table === "interactions" && rule.conditionType === "none" ? "The server checks both selectors before changing the inventory." : rule.conditionType === "none" ? "Follows stage ownership." : `Active during ${CONDITIONS.find(value => value.id === rule.conditionType)?.label.toLowerCase() || title(rule.conditionType)}.`}</p></div>
    <div className="rule-card-actions"><button type="button" aria-label="Move rule up" aria-disabled={index === 0 || !movable} title={index === 0 || !movable ? moveUnavailable : "Move this rule earlier in the list."} onClick={() => { if (movable && index > 0) onMove(-1); }}>↑</button><button type="button" aria-label="Move rule down" aria-disabled={index === total - 1 || !movable} title={index === total - 1 || !movable ? moveUnavailable : "Move this rule later in the list."} onClick={() => { if (movable && index < total - 1) onMove(1); }}>↓</button><Button tone="quiet" disabled={!canEdit} onClick={onEdit}>Edit</Button><Button tone="danger" onClick={onDelete}>Remove</Button></div>
  </article>;
}

function EnchantmentGenerationForm({ stage, rule, rules }:
  { stage: StagePackage; rule?: EnchantmentGenerationRule; rules: EnchantmentGenerationRule[] }) {
  const { boot, mutateFile, closeDialog } = useEditor();
  const [enchantment, setEnchantment] = useState(rule?.enchantment || "");
  const [maxLevel, setMaxLevel] = useState(rule?.maxLevel === null || rule?.maxLevel === undefined
    ? "" : String(rule.maxLevel));
  const [selectionWeight, setSelectionWeight] = useState(
    rule?.selectionWeight === null || rule?.selectionWeight === undefined ? "" : String(rule.selectionWeight));
  const [errors, setErrors] = useState<string[]>([]);
  const candidate = (): EnchantmentGenerationRule => ({
    enchantment: normalizeEnchantmentId(enchantment),
    maxLevel: maxLevel.trim() === "" ? null : Number(maxLevel),
    selectionWeight: selectionWeight.trim() === "" ? null : Number(selectionWeight)
  });
  const save = async (event: React.FormEvent) => {
    event.preventDefault();
    const nextRule = candidate();
    const validation = validateEnchantmentGenerationRule(nextRule, rules, rule?.enchantment);
    if (validation.length) {
      setErrors(validation);
      return;
    }
    const next = [...rules];
    if (rule) {
      const index = next.findIndex(entry => entry.enchantment === rule.enchantment);
      if (index >= 0) next[index] = nextRule;
      else next.push(nextRule);
    } else next.push(nextRule);
    const content = boot?.draft.files[stage.rulesPath] || "";
    await mutateFile(stage.rulesPath, writeEnchantmentGenerationRules(content, next),
      "Enchantment generation settings saved");
    closeDialog();
  };
  return <form className="dialog-form" onSubmit={save}>
    <div className="explanation-card"><Icon name="spark" size={22}/><div><strong>Control enchantments before the game rolls them.</strong><p>Maximum level affects new rolls and enchantments players already hold. Selection weight changes new enchanting table and player aware loot rolls only.</p></div></div>
    {errors.length ? <div className="form-errors" role="alert"><strong>Fix these fields before saving.</strong><ul>{errors.map(error => <li key={error}>{error}</li>)}</ul></div> : null}
    <section className="dialog-section"><header><span className="step-number">1</span><div><h3>Choose one enchantment</h3><p>Generation controls use exact enchantment identifiers so the result is predictable.</p></div></header><div className="form-grid">
      <Field label="Exact enchantment" help="Choose from the running server registry or enter namespace:id." wide><input value={enchantment} onChange={event => setEnchantment(event.target.value)} placeholder="minecraft:mending" autoFocus required/></Field>
      <div className="field-wide"><InlineCatalogSearch catalogId="enchantments" mode="id" onPick={value => setEnchantment(normalizeEnchantmentId(value))}/></div>
    </div></section>
    <section className="dialog-section"><header><span className="step-number">2</span><div><h3>Choose what changes until this stage is owned</h3><p>Leave either field empty when that part should keep its normal behavior.</p></div></header><div className="form-grid">
      <Field label="Maximum level" help="Zero prevents new rolls and removes existing copies. A positive value caps generated and retained levels."><input type="number" min={0} max={MAX_ENCHANTMENT_LEVEL} step={1} value={maxLevel} onChange={event => setMaxLevel(event.target.value)} placeholder="Normal maximum"/></Field>
      <Field label="Selection weight" help="Zero prevents new rolls without stripping existing copies. Larger values make this enchantment more likely."><input type="number" min={0} max={MAX_ENCHANTMENT_SELECTION_WEIGHT} step={1} value={selectionWeight} onChange={event => setSelectionWeight(event.target.value)} placeholder="Normal weight"/></Field>
    </div></section>
    <div className="enchantment-behavior-preview">
      <div><span>Existing enchantments</span><strong>{maxLevel === "" ? "Keep normal levels" : Number(maxLevel) === 0 ? "Remove this enchantment" : `Cap at level ${maxLevel}`}</strong></div>
      <div><span>New rolls</span><strong>{selectionWeight === "" ? "Use normal weight" : Number(selectionWeight) === 0 ? "Do not roll" : `Use weight ${selectionWeight}`}</strong></div>
    </div>
    <footer className="dialog-actions"><Button type="button" tone="quiet" onClick={closeDialog}>Cancel</Button><Button type="submit" tone="primary">{rule ? "Save changes" : "Add enchantment control"}</Button></footer>
  </form>;
}

function EnchantmentGenerationCard({ rule, onEdit, onDelete }:
  { rule: EnchantmentGenerationRule; onEdit: () => void; onDelete: () => void }) {
  return <article className="enchantment-setting-card">
    <div className="rule-card-icon"><Icon name="spark" size={19}/></div>
    <div className="rule-card-main">
      <div className="rule-card-title"><strong>{title(rule.enchantment.split(":").pop() || rule.enchantment)}</strong>
        {rule.maxLevel !== null ? <Badge tone="gold">Maximum level {rule.maxLevel}</Badge> : null}
        {rule.selectionWeight !== null ? <Badge tone={rule.selectionWeight === 0 ? "warning" : "neutral"}>Roll weight {rule.selectionWeight}</Badge> : null}
      </div>
      <code>{rule.enchantment}</code>
      <p>{rule.maxLevel === 0 ? "Existing copies are removed. " : rule.maxLevel !== null ? `Levels above ${rule.maxLevel} are reduced. ` : ""}
        {rule.selectionWeight === 0 ? "New table and player aware loot rolls are disabled." : rule.selectionWeight !== null ? "New rolls use the configured relative weight." : "New rolls keep their normal weight."}</p>
    </div>
    <div className="rule-card-actions"><Button tone="quiet" onClick={onEdit}>Edit</Button><Button tone="danger" onClick={onDelete}>Remove</Button></div>
  </article>;
}

function StructureControls({ stage }: { stage: StagePackage }) {
  const { boot, mutateFile, setLocalError } = useEditor();
  const content = boot?.draft.files[stage.rulesPath] || "";
  const mutationQueues = useRef(new Map<string, { chain: Promise<void>; content: string; pending: number }>());
  const mutateFileRef = useRef(mutateFile);
  mutateFileRef.current = mutateFile;
  const stagePathRef = useRef(stage.rulesPath);
  const priorityErrorKey = `structure:${stage.rulesPath}:priority`;
  const paddingErrorKey = `structure:${stage.rulesPath}:padding`;
  const [entries, setEntries] = useState(parseSimpleArray(readTomlValue(content, "structures.locked_entry")).join("\n"));
  const [priority, setPriority] = useState(readTomlValue(content, "structures.rules.priority"));
  const [priorityError, setPriorityError] = useState("");
  const [padding, setPadding] = useState(readTomlValue(content, "structures.rules.entry_padding") || readTomlValue(content, "structures.entry_padding"));
  const [paddingError, setPaddingError] = useState("");
  const [entryAllowed, setEntryAllowed] = useState(booleanValue(readTomlValue(content, "structures.rules.entry_allowed") || "false"));
  const [preventBreak, setPreventBreak] = useState(booleanValue(readTomlValue(content, "structures.rules.prevent_block_break") || "false"));
  const [preventPlace, setPreventPlace] = useState(booleanValue(readTomlValue(content, "structures.rules.prevent_block_place") || "false"));
  const [preventExplosions, setPreventExplosions] = useState(booleanValue(readTomlValue(content, "structures.rules.prevent_explosions") || "false"));
  const [disableSpawns, setDisableSpawns] = useState(booleanValue(readTomlValue(content, "structures.rules.disable_mob_spawning") || "false"));
  useEffect(() => {
    const state = mutationQueues.current.get(stage.rulesPath);
    const previousPath = stagePathRef.current;
    const pathChanged = previousPath !== stage.rulesPath;
    stagePathRef.current = stage.rulesPath;
    if (pathChanged) {
      setLocalError(`structure:${previousPath}:priority`, null);
      setLocalError(`structure:${previousPath}:padding`, null);
    }
    const source = state?.pending ? state.content : content;
    if (state && !state.pending) state.content = content;
    setEntries(parseSimpleArray(readTomlValue(source, "structures.locked_entry")).join("\n"));
    setPriority(readTomlValue(source, "structures.rules.priority"));
    setPriorityError("");
    setPadding(readTomlValue(source, "structures.rules.entry_padding") || readTomlValue(source, "structures.entry_padding"));
    setPaddingError("");
    setEntryAllowed(booleanValue(readTomlValue(source, "structures.rules.entry_allowed") || "false"));
    setPreventBreak(booleanValue(readTomlValue(source, "structures.rules.prevent_block_break") || "false"));
    setPreventPlace(booleanValue(readTomlValue(source, "structures.rules.prevent_block_place") || "false"));
    setPreventExplosions(booleanValue(readTomlValue(source, "structures.rules.prevent_explosions") || "false"));
    setDisableSpawns(booleanValue(readTomlValue(source, "structures.rules.disable_mob_spawning") || "false"));
    if (pathChanged || !state?.pending) {
      setLocalError(priorityErrorKey, null);
      setLocalError(paddingErrorKey, null);
    }
  }, [content, stage.rulesPath]);
  useEffect(() => () => {
    setLocalError(priorityErrorKey, null);
    setLocalError(paddingErrorKey, null);
  }, [paddingErrorKey, priorityErrorKey, setLocalError]);
  const enqueue = (path: string, update: (source: string) => string, message: string) => {
    const state = mutationQueues.current.get(path) || { chain: Promise.resolve(), content: path === stage.rulesPath ? content : "", pending: 0 };
    mutationQueues.current.set(path, state);
    state.pending += 1;
    const operation = state.chain.then(async () => {
      const previous = state.content;
      const next = update(state.content);
      state.content = next;
      try {
        await mutateFileRef.current(path, next, message);
      } catch {
        state.content = previous;
        if (stagePathRef.current === path) {
          setEntries(parseSimpleArray(readTomlValue(previous, "structures.locked_entry")).join("\n"));
          setPriority(readTomlValue(previous, "structures.rules.priority"));
          setPriorityError("");
          setPadding(readTomlValue(previous, "structures.rules.entry_padding") || readTomlValue(previous, "structures.entry_padding"));
          setPaddingError("");
          setEntryAllowed(booleanValue(readTomlValue(previous, "structures.rules.entry_allowed") || "false"));
          setPreventBreak(booleanValue(readTomlValue(previous, "structures.rules.prevent_block_break") || "false"));
          setPreventPlace(booleanValue(readTomlValue(previous, "structures.rules.prevent_block_place") || "false"));
          setPreventExplosions(booleanValue(readTomlValue(previous, "structures.rules.prevent_explosions") || "false"));
          setDisableSpawns(booleanValue(readTomlValue(previous, "structures.rules.disable_mob_spawning") || "false"));
        }
        return;
      }
    });
    state.chain = operation.then(() => undefined, () => undefined);
    operation.then(() => { state.pending -= 1; }, () => { state.pending -= 1; });
    return operation;
  };
  const save = (path: string, value: unknown, message: string) =>
    enqueue(stage.rulesPath, source => upsertToml(source, path, value), message);
  const savePriority = () => {
    const next = priority.trim();
    if (next && !/^-?\d+$/.test(next)) {
      const message = "Enter a whole number.";
      setPriorityError(message);
      setLocalError(priorityErrorKey, message);
      return;
    }
    const number = Number(next);
    if (next && (!Number.isSafeInteger(number) || number < MIN_STRUCTURE_PRIORITY || number > MAX_STRUCTURE_PRIORITY)) {
      const message = `Use a value from ${MIN_STRUCTURE_PRIORITY} to ${MAX_STRUCTURE_PRIORITY}.`;
      setPriorityError(message);
      setLocalError(priorityErrorKey, message);
      return;
    }
    setPriorityError("");
    setLocalError(priorityErrorKey, null);
    const update = (source: string) => next ? upsertToml(source, "structures.rules.priority", Number(next)) : removeTomlValue(source, "structures.rules.priority");
    return enqueue(stage.rulesPath, update, "Structure priority saved");
  };
  const savePadding = () => {
    const next = padding.trim();
    if (!/^\d+$/.test(next) && next !== "") {
      const message = "Use a whole number of 0 or more.";
      setPaddingError(message);
      setLocalError(paddingErrorKey, message);
      return;
    }
    setPaddingError("");
    setLocalError(paddingErrorKey, null);
    return enqueue(stage.rulesPath, source => next
      ? upsertToml(source, "structures.rules.entry_padding", Number(next))
      : removeTomlValue(removeTomlValue(source, "structures.rules.entry_padding"), "structures.entry_padding"),
      "Structure entry padding saved");
  };
  return <Section title="Structure access" description="Keep entry separate from protections inside the structure. A permanent locked stage can protect blocks after another stage allows entry.">
    <div className="form-grid">
      <Field label="Structures to protect" help="One exact structure id per line. Use the id:minecraft:ancient_city form. These entries are checked by the server structure session." wide><textarea rows={4} value={entries} onChange={event => setEntries(event.target.value)} onBlur={() => void save("structures.locked_entry", lineValues(entries), "Structure list saved")} placeholder="id:minecraft:ancient_city" /></Field>
      <Field label="Rule priority" help="Higher signed values win when structure rules overlap. Leave blank to inherit the normal priority cascade."><input type="number" min={MIN_STRUCTURE_PRIORITY} max={MAX_STRUCTURE_PRIORITY} step={1} value={priority} onChange={event => setPriority(event.target.value)} onBlur={() => void savePriority()} placeholder="Inherited" aria-invalid={Boolean(priorityError)} />{priorityError ? <span className="setting-error" role="alert">{priorityError}</span> : null}</Field>
      <Field label="Entry padding" help="Extra blocks around the structure boundary that still count as inside. Leave blank for the default."><input type="number" min={0} step={1} value={padding} onChange={event => setPadding(event.target.value)} onBlur={() => void savePadding()} placeholder="0" aria-invalid={Boolean(paddingError)} />{paddingError ? <span className="setting-error" role="alert">{paddingError}</span> : null}</Field>
      <Toggle label="Allow entry" help="Lets players enter while the other structure protections remain active. Use this for a permanent locked protection stage." checked={entryAllowed} onChange={value => { setEntryAllowed(value); void save("structures.rules.entry_allowed", value, "Structure entry policy saved"); }} />
      <Toggle label="Prevent block breaking" help="Cancel block breaks inside the protected structure." checked={preventBreak} onChange={value => { setPreventBreak(value); void save("structures.rules.prevent_block_break", value, "Structure break policy saved"); }} />
      <Toggle label="Prevent block placement" help="Cancel block placement inside the protected structure." checked={preventPlace} onChange={value => { setPreventPlace(value); void save("structures.rules.prevent_block_place", value, "Structure placement policy saved"); }} />
      <Toggle label="Prevent explosions" help="Remove blocks inside the structure from explosion damage." checked={preventExplosions} onChange={value => { setPreventExplosions(value); void save("structures.rules.prevent_explosions", value, "Structure explosion policy saved"); }} />
      <Toggle label="Disable mob spawning" help="Stop mobs from spawning inside the protected structure." checked={disableSpawns} onChange={value => { setDisableSpawns(value); void save("structures.rules.disable_mob_spawning", value, "Structure spawn policy saved"); }} />
    </div>
  </Section>;
}

function BlockOverrideForm({ stage, override, duplicate = false }:
  { stage: StagePackage; override?: BlockOverrideModel; duplicate?: boolean }) {
  const { boot, mutateFile, closeDialog } = useEditor();
  const content = boot?.draft.files[stage.rulesPath] || "";
  const [targets, setTargets] = useState(override?.targets || []);
  const [targetMode, setTargetMode] = useState("id");
  const [targetInput, setTargetInput] = useState("");
  const [displayAs, setDisplayAs] = useState(override?.displayAs || "");
  const [dropAs, setDropAs] = useState(override?.dropAs || "");
  const [priority, setPriority] = useState(override?.priority === undefined ? "" : String(override.priority));
  const [error, setError] = useState("");

  const addTarget = () => {
    const selector = normalizeBlockSelector(targetMode, targetInput);
    if (!targetInput.trim()) return;
    setTargets(current => current.includes(selector) ? current : [...current, selector]);
    setTargetInput("");
  };

  const save = async (event: React.FormEvent) => {
    event.preventDefault();
    setError("");
    try {
      const normalizedTargets = targets.map(value => value.trim().replace(/^tags:/i, "tag:"));
      if (!normalizedTargets.length || normalizedTargets.some(value => !value)) {
        throw new Error("Add at least one block, block tag, or mod target.");
      }
      if (new Set(normalizedTargets).size !== normalizedTargets.length) {
        throw new Error("Remove repeated target selectors before saving.");
      }
      if (!displayAs.trim() || !dropAs.trim()) throw new Error("Choose both a display block and a drop item.");
      if (priority.trim()) {
        const value = Number(priority);
        if (!Number.isInteger(value) || value < -2147483648 || value > 2147483647) {
          throw new Error("Priority must be a signed whole number from -2147483648 to 2147483647.");
        }
      }

      let updated: string;
      if (override && !duplicate) {
        const currentRows = extractArrayGroups(content, override.table);
        if (currentRows[override.tableIndex]?.text !== override.sourceText) {
          throw new Error("This override changed in another edit. Reopen it and try again.");
        }
        const replacements = currentRows.map(row => row.text);
        replacements[override.tableIndex] = patchBlockOverride(override.sourceText, override.table,
          { targets: normalizedTargets, displayAs, dropAs, priority });
        updated = replaceArrayGroups(content, override.table, replacements);
      } else {
        updated = appendTomlBlock(content,
          serializeBlockOverride({ targets: normalizedTargets, displayAs, dropAs, priority }));
      }
      await mutateFile(stage.rulesPath, updated, "Block override saved to the draft");
      closeDialog();
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : "The block override was not saved.");
    }
  };

  return <form className="dialog-form" onSubmit={save}>
    <p>Show selected blocks as another block and change the item they drop until this stage is owned. Block locks remain separate.</p>
    {override?.table === "ores.overrides" ? <p>This older ore override stays in its original table when you edit it. New overrides use <code>[[blocks.overrides]]</code>.</p> : null}
    <div className="form-grid">
      <Field label="Target type" help="A mod target includes every block from that mod, not only ores."><select value={targetMode} onChange={event => setTargetMode(event.target.value)}><option value="id">Exact block</option><option value="tag">Block tag</option><option value="mod">Mod</option></select></Field>
      <Field label="Target selector" help="Choose one or more exact block IDs, block tags, or mod namespaces."><input value={targetInput} onChange={event => setTargetInput(event.target.value)} placeholder={targetMode === "id" ? "minecraft:diamond_ore" : targetMode === "tag" ? "c:ores" : "immersiveengineering"} onKeyDown={event => { if (event.key === "Enter") { event.preventDefault(); addTarget(); } }}/></Field>
      <div className="field-wide"><InlineCatalogSearch catalogId="blocks" mode={targetMode} onPick={value => setTargetInput(value)}/></div>
      <div className="field-wide"><Button type="button" onClick={addTarget}>Add target</Button><div className="rule-list-new">{targets.map((target, index) => <div className="rule-card-new" key={`${target}:${index}`}><code>{target}</code><Button type="button" tone="quiet" aria-label={`Remove target ${target}`} onClick={() => setTargets(current => current.filter((_, targetIndex) => targetIndex !== index))}>Remove</Button></div>)}</div></div>
      <Field label="Show as block" help="This selects the block the client sees. Choose a registered block ID."><input value={displayAs} onChange={event => setDisplayAs(event.target.value)} placeholder="minecraft:stone" required/></Field>
      <div className="field-wide"><InlineCatalogSearch catalogId="blocks" mode="id" onPick={value => setDisplayAs(value.replace(/^id:/, ""))}/></div>
      <Field label="Drop item" help="This is one registered item ID used for each legitimate harvest. It does not replace the server block."><input value={dropAs} onChange={event => setDropAs(event.target.value)} placeholder="minecraft:cobblestone" required/></Field>
      <div className="field-wide"><InlineCatalogSearch catalogId="items" mode="id" onPick={value => setDropAs(value.replace(/^id:/, ""))}/></div>
      <Field label="Priority" help="Higher values win when several unowned stages override the same block. Blank uses zero."><input type="number" min={-2147483648} max={2147483647} step={1} value={priority} onChange={event => setPriority(event.target.value)} placeholder="0"/></Field>
    </div>
    {error ? <p role="alert">{error}</p> : null}
    <footer className="dialog-actions"><Button type="button" tone="quiet" onClick={closeDialog}>Cancel</Button><Button type="submit" tone="primary" disabled={!targets.length || !displayAs.trim() || !dropAs.trim()}>{override && !duplicate ? "Save override" : duplicate ? "Add copy" : "Add override"}</Button></footer>
  </form>;
}

function BlockOverridesControls({ stage }: { stage: StagePackage }) {
  const { boot, mutateFile, openDialog, closeDialog, setLocalError } = useEditor();
  const content = boot?.draft.files[stage.rulesPath] || "";
  const overrides = useMemo(() => blockOverrideModels(content), [content]);
  const open = (override?: BlockOverrideModel, duplicate = false) => openDialog({
    title: override && !duplicate ? "Edit block override" : duplicate ? "Copy block override" : "Add block override",
    description: "Choose what the player sees and receives while the owning stage is missing.",
    content: <BlockOverrideForm stage={stage} override={override} duplicate={duplicate}/>,
    width: "wide"
  });
  const remove = (override: BlockOverrideModel) => openDialog({
    title: "Remove block override",
    description: "This change remains undoable until it is applied.",
    content: <div className="confirmation"><p>Remove the override for <code>{override.targets.join(", ")}</code>.</p><footer className="dialog-actions"><Button tone="quiet" onClick={closeDialog}>Keep override</Button><Button tone="danger" onClick={async () => {
      try {
        const rows = extractArrayGroups(content, override.table);
        if (rows[override.tableIndex]?.text !== override.sourceText) {
          throw new Error("This override changed in another edit. Reopen it and try again.");
        }
        const next = rows.map(row => row.text);
        next.splice(override.tableIndex, 1);
        await mutateFile(stage.rulesPath, replaceArrayGroups(content, override.table, next), "Block override removed from the draft");
        closeDialog();
      } catch (failure) {
        setLocalError(stage.rulesPath, failure instanceof Error ? failure.message : "The block override was not removed.");
      }
    }}>Remove</Button></footer></div>,
    width: "compact"
  });
  return <Section title="Block overrides" description="Choose blocks, block tags, or a whole mod. Set the shown block and dropped item separately from block placement locks." action={<Button tone="primary" icon="plus" onClick={() => open()}>Add override</Button>}>
    {overrides.length ? <div className="rule-list-new">{overrides.map(override => <article className="rule-card-new" key={`${override.table}:${override.tableIndex}`}>
      <div className="rule-card-main"><div className="rule-card-title"><strong>{override.table === "ores.overrides" ? "Older ore override" : "Block override"}</strong><Badge help="When several overrides match the same block, the higher-priority override takes precedence.">{override.priority ?? 0} priority</Badge></div><code>{override.targets.join(", ") || "Missing target"}</code><p>Show as {override.displayAs || "missing block"}. Drop {override.dropAs || "missing item"}. Server validation checks selector matches.</p></div>
      <div className="rule-card-actions"><Button tone="quiet" onClick={() => open(override, true)}>Duplicate</Button><Button tone="quiet" onClick={() => open(override)}>Edit</Button><Button tone="danger" onClick={() => remove(override)}>Remove</Button></div>
    </article>)}</div> : <EmptyState icon="blocks" title="No block overrides" description="Add a target, then choose the block players see and the item they receive." action={<Button tone="primary" icon="plus" onClick={() => open()}>Add override</Button>}/>}</Section>;
}

export function RulesPanel({ stage }: { stage: StagePackage }) {
  const { boot, mutateFile, openDialog, closeDialog, runDraftAction } = useEditor();
  const content = boot?.draft.files[stage.rulesPath] || "";
  const rules = useMemo(() => ruleModels(content), [content]);
  const enchantmentRules = useMemo(() => enchantmentGenerationRules(content), [content]);
  const ignoredOreLocks = readTomlValue(content, "ores.locked");
  const openRule = (rule?: RuleModel) => openDialog({ title: rule ? "Edit rule" : "Create a rule", description: "Build one server decision in plain language.", content: <RuleForm stage={stage} rule={rule}/>, width: "wide" });
  const openEnchantmentRule = (rule?: EnchantmentGenerationRule) => openDialog({
    title: rule ? "Edit enchantment generation" : "Add enchantment generation",
    description: "Limit levels or change how often one enchantment is rolled until this stage is owned.",
    content: <EnchantmentGenerationForm stage={stage} rule={rule} rules={enchantmentRules}/>,
    width: "standard"
  });
  const remove = (rule: RuleModel) => openDialog({ title: "Remove rule", description: "This change remains undoable until it is applied.", content: <div className="confirmation"><p>Remove the rule for <code>{rule.selector}</code> from this stage.</p><footer className="dialog-actions"><Button tone="quiet" onClick={closeDialog}>Keep rule</Button><Button tone="danger" onClick={async () => {
    let updated = content;
    if (rule.table === "classic" || rule.table === "recipe_items" || rule.table === "recipe_ids") updated = removeClassicRule(content, rule);
    else if (rule.table === "interactions") updated = replaceInteractionGroup(content, rule.tableIndex, null);
    else updated = replaceRuleGroup(content, rule.table, rule.tableIndex, null);
    await mutateFile(stage.rulesPath, updated, "The rule was removed"); closeDialog();
  }}>Remove rule</Button></footer></div>, width: "compact" });
  const move = async (rule: RuleModel, direction: number) => {
    if (rule.table === "classic" || rule.table === "recipe_items" || rule.table === "recipe_ids" || rule.table === "interactions") return;
    const groups = ruleGroups(content, rule.table);
    const target = rule.tableIndex + direction;
    if (target < 0 || target >= groups.length) return;
    const values = groups.map(group => group.text);
    [values[rule.tableIndex], values[target]] = [values[target], values[rule.tableIndex]];
    const updated = replaceArrayGroups(content, rule.table, values);
    await mutateFile(stage.rulesPath, updated, "Rule order saved");
  };
  const removeEnchantmentRule = (rule: EnchantmentGenerationRule) => openDialog({
    title: "Remove enchantment generation",
    description: "Normal generation behavior returns after this draft is applied.",
    content: <div className="confirmation"><p>Remove the generation settings for <code>{rule.enchantment}</code>.</p><footer className="dialog-actions"><Button tone="quiet" onClick={closeDialog}>Keep settings</Button><Button tone="danger" onClick={async () => {
      const next = enchantmentRules.filter(entry => entry.enchantment !== rule.enchantment);
      await mutateFile(stage.rulesPath, writeEnchantmentGenerationRules(content, next),
        "Enchantment generation settings removed");
      closeDialog();
    }}>Remove settings</Button></footer></div>,
    width: "compact"
  });
  const simulate = () => openDialog({ title: "Simulate a candidate decision", description: "Ask the server how the current draft resolves a category and target.", content: <SimulationForm run={runDraftAction}/>, width: "standard" });
  return <div className="stage-panel-stack">
    <StructureControls stage={stage}/>
    <BlockOverridesControls stage={stage}/>
    {ignoredOreLocks ? <div className="form-errors" role="alert"><strong><code>[ores].locked</code> does not create block overrides.</strong><p>Choose <code>[[blocks.overrides]]</code> in Block overrides and set both Show as and Drop item. The editor will not guess those replacement values.</p></div> : null}
    <Section title="Rules" description={`${rules.length} active decision${rules.length === 1 ? "" : "s"}. Highest priority wins when several rules match.`} action={<div className="section-actions"><Button onClick={simulate}>Simulate</Button><Button tone="primary" icon="plus" onClick={() => openRule()}>Add rule</Button></div>}>
      <div className="rule-primer"><Icon name="spark" size={22}/><div><strong>Rules combine target, action, result, activation, and priority.</strong><p>Use an exception with a higher priority to carve content out of a broad lock. Temporary rules participate only while their condition and lifetime are active.</p></div></div>
      {rules.length ? <div className="rule-list-new">{rules.map((rule, index) => <RuleCard key={`${rule.table}:${rule.tableIndex}:${rule.selector}`} stage={stage} rule={rule} index={index} total={rules.length} onEdit={() => openRule(rule)} onDelete={() => remove(rule)} onMove={direction => void move(rule, direction)}/>)}</div>
        : <EmptyState icon="rules" title="No rules currently active" description="Add a rule, choose a category, then select content from the running server registry." action={<Button tone="primary" icon="plus" onClick={() => openRule()}>Create the first rule</Button>}/>}
    </Section>
    <Section title="Enchantment generation" description={`${enchantmentRules.length} enchantment control${enchantmentRules.length === 1 ? "" : "s"}. These settings apply until the stage is owned.`} action={<Button tone="primary" icon="plus" onClick={() => openEnchantmentRule()}>Add enchantment</Button>}>
      <div className="rule-primer"><Icon name="spark" size={22}/><div><strong>Shape enchanting table and player aware loot rolls before they happen.</strong><p>Use a maximum level to cap or remove retained enchantments. Use selection weight zero when only new rolls should stop.</p></div></div>
      {enchantmentRules.length ? <div className="rule-list-new">{enchantmentRules.map(rule => <EnchantmentGenerationCard key={rule.enchantment} rule={rule} onEdit={() => openEnchantmentRule(rule)} onDelete={() => removeEnchantmentRule(rule)}/>)}</div>
        : <EmptyState icon="spark" title="Normal enchantment generation" description="Add an exact enchantment to limit its level, change its roll weight, or prevent new rolls." action={<Button tone="primary" icon="plus" onClick={() => openEnchantmentRule()}>Add the first enchantment</Button>}/>}
    </Section>
  </div>;
}

function SimulationForm({ run }: { run: <T>(payload: Record<string, unknown>, message?: string) => Promise<T> }) {
  const { closeDialog, notify } = useEditor();
  const [category, setCategory] = useState("items");
  const [action, setAction] = useState("use");
  const [target, setTarget] = useState("minecraft:diamond");
  const submit = async (event: React.FormEvent) => {
    event.preventDefault();
    const result = await run<{ explanation: string }>({ action: "simulate", category: `${category}.${action}`, target });
    notify("info", "Candidate simulation", result.explanation);
    closeDialog();
  };
  const simulationCategories = Object.entries(CATEGORIES).filter(([id]) => !SPECIALIZED_RULE_GUIDANCE[id]);
  return <form className="dialog-form" onSubmit={submit}><div className="form-grid"><Field label="Category"><select value={category} onChange={event => { setCategory(event.target.value); setAction(CATEGORIES[event.target.value].actions[0]); }}>{simulationCategories.map(([id, value]) => <option key={id} value={id}>{value.label}</option>)}</select></Field><Field label="Action"><select value={action} onChange={event => setAction(event.target.value)}>{CATEGORIES[category].actions.map(value => <option key={value} value={value}>{ACTION_LABELS[value] || title(value)}</option>)}</select></Field><Field label="Target identifier" wide><input value={target} onChange={event => setTarget(event.target.value)}/></Field></div><footer className="dialog-actions"><Button tone="quiet" type="button" onClick={closeDialog}>Cancel</Button><Button tone="primary" type="submit">Run simulation</Button></footer></form>;
}
