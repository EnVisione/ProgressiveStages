import { describe, expect, it } from "vitest";
import { CATEGORIES, EXACT_SELECTOR_CATEGORIES, ruleEffects } from "./data";

describe("rule effect choices", () => {
  it("only exposes exclusion for paired inventory insertion rules", () => {
    expect(ruleEffects("items", "use").map(effect => effect.value)).not.toContain("exclude");
    expect(ruleEffects("interactions", "item_into_inventory").map(effect => effect.value)).toContain("exclude");
  });
});

describe("rule editor capabilities", () => {
  it("lists actions that have runtime checks or a dedicated storage form", () => {
    expect(CATEGORIES.items.actions).not.toContain("mouse_pickup");
    expect(CATEGORIES.fluids.actions).toContain("place");
    expect(CATEGORIES.recipes.actions).toEqual(["craft"]);
    expect(CATEGORIES.interactions.actions).toEqual(["item_into_inventory"]);
    expect(CATEGORIES.mobs.actions).not.toContain("replace");
    expect(CATEGORIES.pets.actions).not.toContain("ride");
    expect(CATEGORIES.structures.actions).not.toContain("leave");
    expect(CATEGORIES.screens.catalog).toBe("menus");
  });

  it("marks exact-only runtime targets", () => {
    expect([...EXACT_SELECTOR_CATEGORIES].sort()).toEqual(["abilities", "dimensions", "structures"]);
  });
});
