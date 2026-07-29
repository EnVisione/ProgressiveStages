import { parseSimpleArray, readTomlValue, upsertToml } from "./toml";

export const MAX_ENCHANTMENT_SELECTION_WEIGHT = 1024;
export const MAX_ENCHANTMENT_LEVEL = 2_147_483_647;

export interface EnchantmentGenerationRule {
  enchantment: string;
  maxLevel: number | null;
  selectionWeight: number | null;
}

export function normalizeEnchantmentId(value: string): string {
  return value.trim().replace(/^id:/, "");
}

export function enchantmentGenerationRules(text: string): EnchantmentGenerationRule[] {
  const rules = new Map<string, EnchantmentGenerationRule>();
  mergeEntries(rules, parseSimpleArray(readTomlValue(text, "enchants.max_levels")), "maxLevel");
  mergeEntries(rules, parseSimpleArray(readTomlValue(text, "enchants.selection_weights")), "selectionWeight");
  return [...rules.values()];
}

export function writeEnchantmentGenerationRules(
  text: string,
  rules: EnchantmentGenerationRule[]
): string {
  const maxLevels = rules
    .filter(rule => rule.maxLevel !== null)
    .map(rule => `${rule.enchantment}:${rule.maxLevel}`);
  const selectionWeights = rules
    .filter(rule => rule.selectionWeight !== null)
    .map(rule => `${rule.enchantment}:${rule.selectionWeight}`);
  let updated = upsertToml(text, "enchants.max_levels", maxLevels);
  updated = upsertToml(updated, "enchants.selection_weights", selectionWeights);
  return updated;
}

export function validateEnchantmentGenerationRule(
  rule: EnchantmentGenerationRule,
  existing: EnchantmentGenerationRule[],
  originalId = ""
): string[] {
  const errors: string[] = [];
  const enchantment = normalizeEnchantmentId(rule.enchantment);
  if (!/^[a-z0-9_.-]+:[a-z0-9/._-]+$/.test(enchantment)) {
    errors.push("Choose one exact namespaced enchantment identifier.");
  }
  if (rule.maxLevel === null && rule.selectionWeight === null) {
    errors.push("Set a maximum level, a selection weight, or both.");
  }
  if (rule.maxLevel !== null
      && (!Number.isInteger(rule.maxLevel) || rule.maxLevel < 0 || rule.maxLevel > MAX_ENCHANTMENT_LEVEL)) {
    errors.push("Maximum level must be a whole number of zero or greater.");
  }
  if (rule.selectionWeight !== null
      && (!Number.isInteger(rule.selectionWeight)
        || rule.selectionWeight < 0
        || rule.selectionWeight > MAX_ENCHANTMENT_SELECTION_WEIGHT)) {
    errors.push(`Selection weight must be a whole number from 0 to ${MAX_ENCHANTMENT_SELECTION_WEIGHT}.`);
  }
  if (existing.some(entry => entry.enchantment === enchantment && entry.enchantment !== originalId)) {
    errors.push("This enchantment already has generation settings in this stage.");
  }
  return errors;
}

function mergeEntries(
  rules: Map<string, EnchantmentGenerationRule>,
  entries: string[],
  field: "maxLevel" | "selectionWeight"
): void {
  for (const entry of entries) {
    const separator = entry.lastIndexOf(":");
    if (separator <= 0 || separator === entry.length - 1) continue;
    const enchantment = normalizeEnchantmentId(entry.slice(0, separator));
    const value = Number(entry.slice(separator + 1));
    if (!Number.isSafeInteger(value) || value < 0) continue;
    const rule = rules.get(enchantment) || { enchantment, maxLevel: null, selectionWeight: null };
    rule[field] = value;
    rules.set(enchantment, rule);
  }
}
