import { describe, expect, it } from "vitest";
import { GUIDE_BYTE_LIMIT, GUIDE_CODE_POINT_LIMIT, GUIDE_PLACEHOLDERS, GUIDE_PLACEHOLDER_VALUES, expandGuideTemplate, guideTextMetrics, hasInlineStageRequirements, truncateGuideText } from "./guide";

describe("guide templates", () => {
  it("explains when live remaining requirements may be unavailable", () => {
    const remaining = GUIDE_PLACEHOLDERS.find(({ token }) => token === "{remaining_requirements}");
    expect(remaining?.help).toContain("Says Unavailable");
    expect(remaining?.help).toContain("group limits");
    expect(remaining?.help).toContain("separately for each trigger or purchase route");
  });

  it("explains that the unlock summary already includes the full route and progress is not overall readiness", () => {
    const requirements = GUIDE_PLACEHOLDERS.find(({ token }) => token === "{stage_requirements}");
    const progress = GUIDE_PLACEHOLDERS.find(({ token }) => token === "{stage_progress}");
    expect(requirements?.label).toBe("Unlock summary");
    expect(requirements?.help).toContain("replaces the separate summary under How to unlock");
    expect(requirements?.help).toContain("Put it on its own line");
    expect(requirements?.help).toContain("Do not put words like Complete before it");
    expect(requirements?.example).toBe(GUIDE_PLACEHOLDER_VALUES.stage_requirements);
    expect(requirements?.example).toContain("2s cooldown");
    expect(requirements?.example).toContain("0 of 2 slots are active");
    expect(requirements?.example).toContain("When full: Deny");
    expect(progress?.help).toContain("not overall readiness");
  });

  it("expands fixed tokens and escaped braces", () => {
    expect(expandGuideTemplate("{{stage_name}} {stage_name} {unknown}", { stage_name: "Miner" })).toEqual({
      text: "{stage_name} Miner {unknown}",
      unknown: ["unknown"]
    });
  });

  it("flags the complete unlock summary inside a sentence but allows standalone and escaped tokens", () => {
    expect(hasInlineStageRequirements("Collect {stage_requirements}.")).toBe(true);
    expect(hasInlineStageRequirements("{stage_requirements}\nThen visit the village.")).toBe(false);
    expect(hasInlineStageRequirements("{{stage_requirements}}")).toBe(false);
    expect(hasInlineStageRequirements("{{stage_requirements}")).toBe(false);
  });

  it("measures unicode by code point and utf 8 byte length", () => {
    const metrics = guideTextMetrics("🪨".repeat(GUIDE_CODE_POINT_LIMIT));
    expect(metrics.codePoints).toBe(GUIDE_CODE_POINT_LIMIT);
    expect(metrics.bytes).toBe(GUIDE_BYTE_LIMIT);
    expect(metrics.valid).toBe(true);
    expect(guideTextMetrics("🪨".repeat(GUIDE_CODE_POINT_LIMIT + 1)).valid).toBe(false);
  });

  it("truncates without splitting unicode or exceeding either limit", () => {
    const value = truncateGuideText("🪨".repeat(GUIDE_CODE_POINT_LIMIT));
    const metrics = guideTextMetrics(value);
    expect(metrics.valid).toBe(true);
    expect([...value].every(character => character === "🪨")).toBe(true);
  });
});
