import { describe, expect, it } from "vitest";
import type { StagePackage } from "../types";
import { discoverStages } from "./model";
import { playerPreviewRequirementSummary, simulatedOwnedStageIds, stagePrerequisitesSatisfied } from "./player-preview";

const stage = (id: string, name: string, dependencies: string[] = [], dependencyMode = "all", dependencyCount = 1): StagePackage => ({
  key: id,
  folder: id,
  stagePath: `${id}/stage.toml`,
  rulesPath: `${id}/rules.toml`,
  progressionPath: `${id}/progression.toml`,
  legacy: false,
  archived: false,
  id,
  name,
  description: "",
  icon: "minecraft:stone",
  category: "base",
  color: "gold",
  hidden: false,
  ruleCount: 0,
  grantCount: 0,
  revokeCount: 0,
  dependencies,
  dependencyMode,
  dependencyCount
});

describe("player preview ownership simulation", () => {
  it("does not invent a hidden prerequisite for an explicit empty dependency list", () => {
    const stages = discoverStages({
      "stages/mage/stage.toml": '[stage]\nid = "pack:mage"\ndisplay_name = "Mage"\ndependencies = []\n',
      "stages/mage/rules.toml": "",
      "stages/mage/progression.toml": ""
    });

    expect(stages[0].dependencies).toEqual([]);
    expect(simulatedOwnedStageIds(stages, "ready")).toEqual(new Set());
    expect(playerPreviewRequirementSummary(stages, stages[0], new Set([stages[0].id])))
      .toBe("Beginner path with no prerequisites");
  });

  it("builds empty, prerequisite-closed, and fully owned scenarios", () => {
    const stages = [
      stage("pack:root", "Root"),
      stage("pack:middle", "Middle", ["pack:root"]),
      stage("pack:next", "Next", ["pack:middle"])
    ];

    expect(simulatedOwnedStageIds(stages, "locked").size).toBe(0);
    expect(simulatedOwnedStageIds(stages, "ready")).toEqual(new Set(["pack:root", "pack:middle"]));
    expect(simulatedOwnedStageIds(stages, "unlocked")).toEqual(new Set(stages.map(value => value.id)));
  });

  it("evaluates ANY, ALL, and AT_LEAST policies using the simulated owned set", () => {
    const dependencies = ["pack:a", "pack:b", "pack:c"];
    expect(stagePrerequisitesSatisfied(stage("pack:any", "Any", dependencies, "any"), new Set(["pack:b"]))).toBe(true);
    expect(stagePrerequisitesSatisfied(stage("pack:all", "All", dependencies, "all"), new Set(["pack:a", "pack:b"]))).toBe(false);
    expect(stagePrerequisitesSatisfied(stage("pack:count", "Count", dependencies, "at_least", 2), new Set(["pack:a", "pack:c"]))).toBe(true);
    expect(stagePrerequisitesSatisfied(stage("pack:count", "Count", dependencies, "at_least", 2), new Set(["pack:a"]))).toBe(false);
  });

  it("uses generic guidance instead of naming hidden prerequisites", () => {
    const visible = stage("pack:root", "Root");
    const hidden = stage("pack:secret", "Secret");
    const next = stage("pack:next", "Next", [visible.id, hidden.id], "all", 2);
    const summary = playerPreviewRequirementSummary([visible, hidden, next], next, new Set([visible.id, next.id]));

    expect(summary).toContain("Root");
    expect(summary).toContain("Some required stages are hidden in this preview.");
    expect(summary).not.toContain("Secret");
    expect(summary).not.toContain("pack:secret");
  });
});
