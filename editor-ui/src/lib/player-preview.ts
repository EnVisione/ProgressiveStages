import { dependencySummary } from "./model";
import type { StagePackage } from "../types";

export type PlayerPreviewScenario = "locked" | "ready" | "unlocked";

export function simulatedOwnedStageIds(
  stages: StagePackage[],
  scenario: PlayerPreviewScenario
): Set<string> {
  if (scenario === "locked") return new Set();

  const stageIds = new Set(stages.map(stage => stage.id));
  if (scenario === "unlocked") return stageIds;

  const owned = new Set<string>();
  const visited = new Set<string>();
  const includePrerequisites = (stageId: string) => {
    if (visited.has(stageId)) return;
    visited.add(stageId);
    const stage = stages.find(value => value.id === stageId);
    if (!stage) return;
    for (const dependency of stage.dependencies) {
      if (!stageIds.has(dependency)) continue;
      owned.add(dependency);
      includePrerequisites(dependency);
    }
  };

  for (const stage of stages) includePrerequisites(stage.id);
  return owned;
}

export function stagePrerequisitesSatisfied(stage: StagePackage, owned: ReadonlySet<string>): boolean {
  if (!stage.dependencies.length) return true;
  const satisfied = stage.dependencies.filter(id => owned.has(id)).length;
  switch (stage.dependencyMode.toLowerCase()) {
    case "any":
      return satisfied > 0;
    case "at_least":
      return satisfied >= Math.max(1, Math.min(stage.dependencies.length, stage.dependencyCount));
    default:
      return satisfied === stage.dependencies.length;
  }
}

export function playerPreviewRequirementSummary(
  stages: StagePackage[],
  stage: StagePackage,
  visibleStageIds: ReadonlySet<string>
): string {
  const visibleDependencies = stage.dependencies.filter(id => visibleStageIds.has(id));
  const hasHiddenDependencies = visibleDependencies.length !== stage.dependencies.length;
  if (!hasHiddenDependencies) return dependencySummary(stages, stage);
  if (!visibleDependencies.length) return "Some required stages are hidden in this preview.";

  const visibleStages = stages.filter(value => visibleStageIds.has(value.id));
  const visibleStage = { ...stage, dependencies: visibleDependencies };
  return `${dependencySummary(visibleStages, visibleStage)}. Some required stages are hidden in this preview.`;
}
