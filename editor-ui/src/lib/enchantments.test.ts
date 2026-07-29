import { describe, expect, it } from "vitest";
import {
  enchantmentGenerationRules,
  validateEnchantmentGenerationRule,
  writeEnchantmentGenerationRules
} from "./enchantments";

describe("enchantment generation editor model", () => {
  it("combines level caps and selection weights by exact enchantment id", () => {
    const source = `[enchants]
max_levels = ["minecraft:sharpness:3", "minecraft:mending:0"]
selection_weights = ["minecraft:mending:0", "minecraft:fortune:10"]
`;

    expect(enchantmentGenerationRules(source)).toEqual([
      { enchantment: "minecraft:sharpness", maxLevel: 3, selectionWeight: null },
      { enchantment: "minecraft:mending", maxLevel: 0, selectionWeight: 0 },
      { enchantment: "minecraft:fortune", maxLevel: null, selectionWeight: 10 }
    ]);
  });

  it("writes deterministic lists and preserves unrelated toml", () => {
    const source = `[enchants]
locked = ["id:minecraft:silk_touch"]

[custom.extension]
value = "keep me"
`;
    const expected = [
      { enchantment: "minecraft:mending", maxLevel: 0, selectionWeight: 0 },
      { enchantment: "minecraft:fortune", maxLevel: 2, selectionWeight: 10 }
    ];

    const updated = writeEnchantmentGenerationRules(source, expected);

    expect(enchantmentGenerationRules(updated)).toEqual(expected);
    expect(updated).toContain('locked = ["id:minecraft:silk_touch"]');
    expect(updated).toContain("[custom.extension]");
    expect(updated).toContain('value = "keep me"');
  });

  it("rejects missing values, duplicates, invalid ids, and out of range weights", () => {
    const existing = [
      { enchantment: "minecraft:mending", maxLevel: 0, selectionWeight: null }
    ];

    expect(validateEnchantmentGenerationRule(
      { enchantment: "Mending", maxLevel: null, selectionWeight: 1025 },
      existing
    )).toEqual([
      "Choose one exact namespaced enchantment identifier.",
      "Selection weight must be a whole number from 0 to 1024."
    ]);
    expect(validateEnchantmentGenerationRule(
      { enchantment: "minecraft:mending", maxLevel: null, selectionWeight: null },
      existing
    )).toEqual([
      "Set a maximum level, a selection weight, or both.",
      "This enchantment already has generation settings in this stage."
    ]);
  });
});
