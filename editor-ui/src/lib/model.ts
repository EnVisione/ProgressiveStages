import { extractRows } from "./tomlRows";
import { readInventoryCondition } from "./inventoryInsertion";
import { readRuleField } from "./ruleSource";
import { CATEGORIES, LEGACY_RULE_BINDINGS } from "../data";
import type { ProgressionModel, RuleModel, StagePackage } from "../types";
import { enchantmentGenerationRules } from "./enchantments";
import {
  booleanValue,
  extractArrayBlocks,
  extractArrayGroups,
  inlineObjectValue,
  numberValue,
  parseSimpleArray,
  readBlockValue,
  readTomlValue,
  stringValue
} from "./toml";

export function title(value: string): string {
  return String(value || "")
    .replace(/[_./:-]+/g, " ")
    .replace(/\b\w/g, character => character.toUpperCase())
    .trim();
}

export function slug(value: string): string {
  return value.toLowerCase().trim().replace(/[^a-z0-9_.-]+/g, "_").replace(/^_+|_+$/g, "");
}

export function stageIdentity(name: string, namespace: string): { name: string; namespace: string; path: string; id: string } {
  const cleanName = name.trim().replace(/^[a-z0-9_.-]+:/i, "");
  const cleanNamespace = slug(namespace) || "pack";
  const path = slug(cleanName);
  return { name: cleanName, namespace: cleanNamespace, path, id: `${cleanNamespace}:${path}` };
}

export function discoverStages(files: Record<string, string>): StagePackage[] {
  const output: StagePackage[] = [];
  const packageStagePaths = Object.keys(files).filter(path => path.endsWith("/stage.toml"));
  for (const stagePath of packageStagePaths) {
    const folder = stagePath.slice(0, -"stage.toml".length);
    output.push(stageSummary(files, folder, stagePath, `${folder}rules.toml`, `${folder}progression.toml`, false));
  }
  for (const path of Object.keys(files)) {
    if (!path.startsWith("stages/") || !path.endsWith(".toml") || path.includes("/.editor-")) continue;
    if (packageStagePaths.some(stagePath => path.startsWith(stagePath.slice(0, -"stage.toml".length)))) continue;
    output.push(stageSummary(files, path, path, path, path, true));
  }
  return output.sort((left, right) => left.archived === right.archived
    ? left.name.localeCompare(right.name)
    : Number(left.archived) - Number(right.archived));
}

function stageSummary(files: Record<string, string>, key: string, stagePath: string,
                      rulesPath: string, progressionPath: string, legacy: boolean): StagePackage {
  const stageText = files[stagePath] || "";
  const id = stringValue(readTomlValue(stageText, "stage.id")) || stagePath.split("/").pop()?.replace(/\.toml$/, "") || "stage";
  const name = stringValue(readTomlValue(stageText, "stage.display_name")) || title(id.split(":").pop() || id);
  const progression = progressionModels(files[progressionPath] || "");
  const dependencies = dependenciesFor(stageText, legacy);
  return {
    key,
    folder: legacy ? "" : key,
    stagePath,
    rulesPath,
    progressionPath,
    legacy,
    archived: key.includes("/.editor-archive/"),
    id,
    name,
    description: stringValue(readTomlValue(stageText, "stage.description")),
    icon: stringValue(readTomlValue(stageText, "stage.icon")) || "minecraft:stone",
    category: stringValue(readTomlValue(stageText, "stage.category")) || "Uncategorized",
    color: stringValue(readTomlValue(stageText, "stage.color")) || "#d9a83e",
    hidden: booleanValue(readTomlValue(stageText, "stage.hidden")),
    ruleCount: ruleModels(files[rulesPath] || "").length
      + enchantmentGenerationRules(files[rulesPath] || "").length,
    grantCount: progression.filter(entry => entry.kind === "grants").length,
    revokeCount: progression.filter(entry => entry.kind === "revokes").length,
    dependencies,
    dependencyMode: stringValue(readTomlValue(stageText, "stage.dependency_mode")) || "all",
    dependencyCount: Math.max(1, numberValue(readTomlValue(stageText, "stage.dependency_count"), 1))
  };
}

export function dependenciesFor(text: string, legacy = false): string[] {
  const raw = readTomlValue(text, legacy ? "stage.dependency" : "stage.dependencies") || readTomlValue(text, "stage.dependency");
  if (raw.trim().startsWith("[") && raw.trim().endsWith("]")) return parseSimpleArray(raw);
  const scalar = stringValue(raw);
  return scalar ? [scalar] : [];
}

export function stageName(stages: StagePackage[], id: string): string {
  return stages.find(stage => stage.id === id)?.name || title(id.split(":").pop() || id);
}

export function stageDependsOn(stages: StagePackage[], startId: string, targetId: string,
                               visited = new Set<string>()): boolean {
  if (startId === targetId) return true;
  if (visited.has(startId)) return false;
  visited.add(startId);
  const stage = stages.find(value => value.id === startId);
  if (!stage) return false;
  return stage.dependencies.some(parent => stageDependsOn(stages, parent, targetId, visited));
}

export function dependencySummary(stages: StagePackage[], stage: StagePackage): string {
  if (!stage.dependencies.length) return "Beginner path with no prerequisites";
  const names = stage.dependencies.map(id => stageName(stages, id));
  if (stage.dependencyMode === "any") return `Any one of ${names.join(", ")}`;
  if (stage.dependencyMode === "at_least") return `${Math.min(names.length, stage.dependencyCount)} of ${names.join(", ")}`;
  return names.length === 1 ? `Requires ${names[0]}` : `Requires ${names.join(" and ")}`;
}

export function ruleModels(text: string): RuleModel[] {
  const models: RuleModel[] = [];
  for (const table of ["rules", "temporary_rules"] as const) {
    extractArrayGroups(text, table).forEach(block => models.push(parseRuleBlock(block.text, table, block.index)));
  }
  extractRows(text, "interactions").forEach((block, index) => {
    const type = stringValue(readBlockValue(block.text, "type"));
    if (type !== "item_into_inventory") return;
    const targetKind = stringValue(readBlockValue(block.text, "target_kind"));
    const destination = stringValue(readBlockValue(block.text, "target"));
    const heldItem = stringValue(readBlockValue(block.text, "held_item"));
    const condition = interactionCondition(block.text, "while") || interactionCondition(block.text, "when")
      || interactionCondition(block.text, "condition");
    const resetCondition = interactionCondition(block.text, "reset_condition");
    if (!heldItem || !destination || !["block", "menu", "inventory"].includes(targetKind)) return;
    models.push({
      table: "interactions",
      tableIndex: index,
      id: stringValue(readBlockValue(block.text, "id")),
      category: "interactions",
      action: type,
      effect: stringValue(readBlockValue(block.text, "effect")) || "lock",
      selector: heldItem,
      priority: numberValue(readBlockValue(block.text, "priority")),
      viewer: "inherit",
      lifetime: stringValue(readBlockValue(block.text, "lifetime")) || "permanent",
      duration: stringValue(readBlockValue(block.text, "duration")),
      conditionType: inlineObjectValue(condition, "type") || (condition ? "custom" : "none"),
      conditionTarget: inlineObjectValue(condition, "id") || inlineObjectValue(condition, "value") || inlineObjectValue(condition, "callback"),
      count: numberValue(inlineObjectValue(condition, "count"), 1),
      exception: "",
      exceptionPriority: 0,
      sourceText: block.text,
      targetKind: targetKind as "block" | "menu" | "inventory",
      destination,
      resetConditionType: inlineObjectValue(resetCondition, "type") || (resetCondition ? "custom" : "none"),
      resetConditionTarget: inlineObjectValue(resetCondition, "id") || inlineObjectValue(resetCondition, "value") || inlineObjectValue(resetCondition, "callback"),
      resetCount: numberValue(inlineObjectValue(resetCondition, "count"), 1),
      conditionSource: condition,
      resetConditionSource: resetCondition
    });
  });
  for (const [category, binding] of Object.entries(LEGACY_RULE_BINDINGS)) {
    for (const action of binding.actions) {
      const lockedField = typeof binding.locked === "string" ? binding.locked : binding.locked[action];
      if (!lockedField) continue;
      const fields = [[lockedField, "lock"] as const,
        ...(binding.alwaysUnlocked ? [[binding.alwaysUnlocked, "exclude"] as const] : [])];
      for (const [field, effect] of fields) {
        const selectors = parseSimpleArray(readTomlValue(text, `${category}.${field}`));
        selectors.forEach((selector, selectorIndex) => {
          const hasIgnoredPriority = /\|priority=-?\d+$/.test(selector);
          models.push({
            table: "classic",
            classicField: field,
            tableIndex: selectorIndex,
            category,
            action,
            effect,
            selector: selector.replace(/\|priority=-?\d+$/, ""),
            priority: numberValue(readTomlValue(text, "stage.priority")),
            viewer: "inherit",
            lifetime: "permanent",
            duration: "",
            conditionType: "none",
            conditionTarget: "",
            count: 1,
            exception: "",
            exceptionPriority: 0,
            sourceText: selector,
            ambiguous: category === "recipes",
            ineffectivePriority: hasIgnoredPriority
          });
        });
      }
    }
  }
  for (const [field, table, recipeKind] of [
    ["locked_items", "recipe_items", "output"],
    ["locked_ids", "recipe_ids", "identifier"]
  ] as const) {
    const selectors = parseSimpleArray(readTomlValue(text, `recipes.${field}`));
    selectors.forEach((selector, selectorIndex) => {
      const priorityMatch = selector.match(/\|priority=(-?\d+)$/);
      models.push({
        table,
        tableIndex: selectorIndex,
        category: "recipes",
        action: "craft",
        effect: "lock",
        selector: selector.replace(/\|priority=-?\d+$/, ""),
        priority: priorityMatch ? Number(priorityMatch[1]) : numberValue(readTomlValue(text, "recipes.priority")),
        viewer: "inherit",
        lifetime: "permanent",
        duration: "",
        conditionType: "none",
        conditionTarget: "",
        count: 1,
        exception: "",
        exceptionPriority: 0,
        sourceText: selector,
        recipeKind
      });
    });
  }
  return models;
}

const interactionCondition = readInventoryCondition;

function parseRuleBlock(text: string, table: "rules" | "temporary_rules", tableIndex: number): RuleModel {
  const category = Object.keys(CATEGORIES).find(category => readRuleField(text, `targets.${category}`))
    || stringValue(readBlockValue(text, "category")) || "items";
  const selectorRaw = readRuleField(text, `targets.${category}`) || readBlockValue(text, "selector");
  const selector = parseSimpleArray(selectorRaw)[0] || stringValue(selectorRaw) || "id:minecraft:stone";
  const condition = readInventoryCondition(text, "while") || readInventoryCondition(text, "when") || readInventoryCondition(text, "condition");
  const resetCondition = readInventoryCondition(text, "reset_condition") || "";
  const exception = extractArrayGroups(text, `${table}.exceptions`)[0]?.text || "";
  const exceptionRaw = exception ? readRuleField(exception, `targets.${category}`) : "";
  const jei = stringValue(readRuleField(text, "presentation.jei")) || "inherit";
  const emi = stringValue(readRuleField(text, "presentation.emi")) || "inherit";
  return {
    table,
    tableIndex,
    category,
    stageState: stringValue(readRuleField(text, "stage_state")) || (table === "temporary_rules"
      || !["lock", "exclude"].includes(stringValue(readBlockValue(text, "effect")) || "lock") ? "owned" : "missing"),
    action: stringValue(readBlockValue(text, "action")) || CATEGORIES[category]?.actions[0] || "use",
    effect: stringValue(readBlockValue(text, "effect")) || "lock",
    selector,
    priority: numberValue(readBlockValue(text, "priority")),
    viewer: jei === emi ? jei : "custom",
    lifetime: stringValue(readBlockValue(text, "lifetime")) || (table === "temporary_rules" ? "live" : "permanent"),
    duration: stringValue(readBlockValue(text, "duration")),
    conditionType: inlineObjectValue(condition, "type") || (condition ? "custom" : "none"),
    conditionTarget: inlineObjectValue(condition, "id") || inlineObjectValue(condition, "value") || inlineObjectValue(condition, "callback"),
    count: numberValue(inlineObjectValue(condition, "count"), 1),
    exception: parseSimpleArray(exceptionRaw)[0] || stringValue(exceptionRaw),
    exceptionPriority: numberValue(exception ? readBlockValue(exception, "priority") : ""),
    sourceText: text,
    id: stringValue(readBlockValue(text, "id")),
    conditionSource: condition,
    resetConditionSource: resetCondition,
    resetConditionType: inlineObjectValue(resetCondition, "type") || (resetCondition ? "custom" : "none"),
    resetConditionTarget: inlineObjectValue(resetCondition, "id") || inlineObjectValue(resetCondition, "value") || inlineObjectValue(resetCondition, "callback"),
    resetCount: numberValue(inlineObjectValue(resetCondition, "count"), 1)
  };
}

export function progressionModels(text: string): ProgressionModel[] {
  const models: ProgressionModel[] = [];
  for (const kind of ["grants", "revokes"] as const) {
    extractArrayBlocks(text, kind).forEach(block => {
      const condition = readBlockValue(block.text, "condition") || readBlockValue(block.text, "when");
      models.push({
        kind,
        tableIndex: block.index,
        conditionType: inlineObjectValue(condition, "type") || stringValue(readBlockValue(block.text, "type")) || "advancement",
        conditionTarget: inlineObjectValue(condition, "id") || inlineObjectValue(condition, "value") || stringValue(readBlockValue(block.text, "target")),
        count: numberValue(inlineObjectValue(condition, "count") || readBlockValue(block.text, "count"), 1),
        repeat: stringValue(readBlockValue(block.text, "repeat")) || "once",
        scope: stringValue(readBlockValue(block.text, "scope")) || "player",
        priority: numberValue(readBlockValue(block.text, "priority")),
        cooldown: stringValue(readBlockValue(block.text, "cooldown")),
        sourceText: block.text
      });
    });
  }
  return models;
}

export function purchaseSummary(text: string): { enabled: boolean; items: string[]; xpLevels: number; xpPoints: number; cooldown: string; refund: number; bypass: boolean } {
  const items = parseSimpleArray(readTomlValue(text, "cost.items"));
  const xpLevels = numberValue(readTomlValue(text, "cost.xp_levels"));
  const xpPoints = numberValue(readTomlValue(text, "cost.xp_points"));
  const cooldown = stringValue(readTomlValue(text, "cost.cooldown"));
  const refund = numberValue(readTomlValue(text, "cost.refund_percent"));
  const bypass = booleanValue(readTomlValue(text, "cost.bypass_requirements"));
  return { enabled: items.length > 0 || xpLevels > 0 || xpPoints > 0, items, xpLevels, xpPoints, cooldown, refund, bypass };
}

export function featureCounts(stage: StagePackage, files: Record<string, string>): Record<string, number> {
  const stageText = files[stage.stagePath] || "";
  const rulesText = files[stage.rulesPath] || "";
  const progressionText = files[stage.progressionPath] || "";
  return {
    abilities: parseSimpleArray(readTomlValue(stageText, "abilities.locked")).length
      + parseSimpleArray(readTomlValue(rulesText, "abilities.locked")).length,
    attributes: extractArrayBlocks(stageText, "attribute").length + extractArrayBlocks(stageText, "attributes").length
      + extractArrayBlocks(rulesText, "attribute").length + extractArrayBlocks(rulesText, "attributes").length,
    modifiers: extractArrayBlocks(rulesText, "item_modifiers").length,
    dropModifiers: extractArrayBlocks(rulesText, "drop_modifiers").length,
    challenges: extractArrayBlocks(progressionText, "challenges").length,
    variables: extractArrayBlocks(progressionText, "variables").length,
    profiles: extractArrayBlocks(progressionText, "profiles").length,
    templates: extractArrayBlocks(progressionText, "templates").length,
    formulas: (progressionText.match(/^\s*[A-Za-z0-9_.-]+\s*=.+$/gm) || []).length
  };
}

export function normalizeSelector(mode: string, key: string, search = ""): string {
  if (mode === "all") return "all:*";
  const clean = key.replace(/^(all|id|mod|tag|name):/, "");
  if (mode === "name") return `name:${(search || clean).trim()}`;
  return `${mode}:${clean}`;
}

export function selectorMode(selector: string): string {
  return selector.match(/^(all|id|mod|tag|name):/)?.[1] || "id";
}

export function stripSelectorPrefix(value: string): string {
  return value.replace(/^id:/, "");
}
