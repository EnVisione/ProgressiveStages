import { CATEGORIES } from "../data";
import type { ProgressionModel, RuleModel, StagePackage } from "../types";
import {
  booleanValue,
  extractArrayBlocks,
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
    ruleCount: ruleModels(files[rulesPath] || "").length,
    grantCount: progression.filter(entry => entry.kind === "grants").length,
    revokeCount: progression.filter(entry => entry.kind === "revokes").length,
    dependencies,
    dependencyMode: stringValue(readTomlValue(stageText, "stage.dependency_mode")) || "all",
    dependencyCount: Math.max(1, numberValue(readTomlValue(stageText, "stage.dependency_count"), 1))
  };
}

export function dependenciesFor(text: string, legacy = false): string[] {
  const raw = readTomlValue(text, legacy ? "stage.dependency" : "stage.dependencies") || readTomlValue(text, "stage.dependency");
  const array = parseSimpleArray(raw);
  return array.length ? array : stringValue(raw) ? [stringValue(raw)] : [];
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
    extractArrayBlocks(text, table).forEach(block => models.push(parseRuleBlock(block.text, table, block.index)));
  }
  for (const [category, definition] of Object.entries(CATEGORIES)) {
    for (const [field, effect] of [["locked", "lock"], ["allowed", "allow"], ["always_unlocked", "allow"]] as const) {
      const selectors = parseSimpleArray(readTomlValue(text, `${category}.${field}`));
      selectors.forEach((selector, selectorIndex) => {
        const priorityMatch = selector.match(/\|priority=(-?\d+)$/);
        models.push({
          table: "classic",
          tableIndex: selectorIndex,
          category,
          action: definition.actions[0],
          effect,
          selector: selector.replace(/\|priority=-?\d+$/, ""),
          priority: priorityMatch ? Number(priorityMatch[1]) : numberValue(readTomlValue(text, `${category}.priority`)),
          viewer: "inherit",
          lifetime: "permanent",
          duration: "",
          conditionType: "none",
          conditionTarget: "",
          count: 1,
          exception: "",
          exceptionPriority: 0,
          sourceText: selector
        });
      });
    }
  }
  return models;
}

function parseRuleBlock(text: string, table: "rules" | "temporary_rules", tableIndex: number): RuleModel {
  const targetMatch = text.match(/^\s*targets\.([A-Za-z0-9_.-]+)\s*=\s*([^\n]+)/m);
  const category = targetMatch?.[1] || stringValue(readBlockValue(text, "category")) || "items";
  const selectorRaw = targetMatch?.[2] || readBlockValue(text, "selector");
  const selector = parseSimpleArray(selectorRaw)[0] || stringValue(selectorRaw) || "id:minecraft:stone";
  const condition = readBlockValue(text, "while") || readBlockValue(text, "when") || readBlockValue(text, "condition");
  const exceptionRaw = readBlockValue(text, "exceptions");
  return {
    table,
    tableIndex,
    category,
    action: stringValue(readBlockValue(text, "action")) || CATEGORIES[category]?.actions[0] || "use",
    effect: stringValue(readBlockValue(text, "effect")) || "lock",
    selector,
    priority: numberValue(readBlockValue(text, "priority")),
    viewer: stringValue(readBlockValue(text, "viewer")) || "inherit",
    lifetime: stringValue(readBlockValue(text, "lifetime")) || (table === "temporary_rules" ? "live" : "permanent"),
    duration: stringValue(readBlockValue(text, "duration")),
    conditionType: inlineObjectValue(condition, "type") || "none",
    conditionTarget: inlineObjectValue(condition, "id") || inlineObjectValue(condition, "value") || inlineObjectValue(condition, "callback"),
    count: numberValue(inlineObjectValue(condition, "count"), 1),
    exception: parseSimpleArray(exceptionRaw)[0] || stringValue(exceptionRaw),
    exceptionPriority: numberValue(readBlockValue(text, "exception_priority")),
    sourceText: text
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
  const clean = key.replace(/^(id|mod|tag|name):/, "");
  if (mode === "name") return `name:${(search || clean).trim()}`;
  return `${mode}:${clean}`;
}

export function selectorMode(selector: string): string {
  return selector.match(/^(id|mod|tag|name):/)?.[1] || "id";
}

export function stripSelectorPrefix(value: string): string {
  return value.replace(/^id:/, "");
}
