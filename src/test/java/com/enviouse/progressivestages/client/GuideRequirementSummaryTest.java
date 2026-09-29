package com.enviouse.progressivestages.client;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuideRequirementSummaryTest {

    @Test
    void purchaseSummaryIncludesPrerequisitesCostAndStageGroup() {
        var input = new GuideRequirementSummary.Input(true, false,
            false,
            List.of("Copper Age", "Iron Age"), "at_least", 1, List.of(),
            true, "16x Iron Ingot, 2s cooldown", false,
            "Beginner Paths", 0, 2, "Deny", false);

        var lines = GuideRequirementSummary.project(input);

        assertEquals(List.of(
            "gui.progressivestages.tree.guide.requirements.stages.count",
            "gui.progressivestages.tree.guide.requirements.purchase",
            "gui.progressivestages.tree.guide.requirements.slots"),
            lines.stream().map(GuideRequirementSummary.Line::translationKey).toList());
        assertEquals(List.of("1", "Copper Age, Iron Age"), lines.get(0).arguments());
        assertEquals(List.of("16x Iron Ingot, 2s cooldown"), lines.get(1).arguments());
        assertEquals(List.of("Beginner Paths", "0", "2", "Deny"), lines.get(2).arguments());

        var display = GuideRequirementSummary.displayPlan(lines, false);
        assertTrue(display.showGeneratedSummary());
        assertTrue(display.suppressPrerequisiteDetails());
        assertTrue(display.suppressSlotDetails());
    }

    @Test
    void embeddedSummaryStillHidesDuplicateRequirementAndSlotSections() {
        var input = new GuideRequirementSummary.Input(true, false, false,
            List.of(), "all", 0, List.of(), true, "16x Iron Ingot", false,
            "Beginner Paths", 0, 2, "Deny", false);

        var display = GuideRequirementSummary.displayPlan(GuideRequirementSummary.project(input), true);

        assertFalse(display.showGeneratedSummary());
        assertTrue(display.suppressPrerequisiteDetails());
        assertTrue(display.suppressSlotDetails());
    }

    @Test
    void unavailableSummaryKeepsSeparateRequirementAndSlotDetailsVisible() {
        var input = new GuideRequirementSummary.Input(false, false, false,
            List.of(), "all", 0, List.of(), false, "", false,
            "Beginner Paths", 0, 2, "Deny", false);
        var noRouteInput = new GuideRequirementSummary.Input(true, false, false,
            List.of(), "all", 0, List.of(), false, "", false,
            "Beginner Paths", 0, 2, "Deny", false);

        var display = GuideRequirementSummary.displayPlan(GuideRequirementSummary.project(input), false);
        var noRouteDisplay = GuideRequirementSummary.displayPlan(
            GuideRequirementSummary.project(noRouteInput), false);

        assertTrue(display.showGeneratedSummary());
        assertFalse(display.suppressPrerequisiteDetails());
        assertFalse(display.suppressSlotDetails());
        assertFalse(noRouteDisplay.suppressPrerequisiteDetails());
        assertFalse(noRouteDisplay.suppressSlotDetails());
    }

    @Test
    void unlimitedSlotGroupRemainsVisibleWhenSummaryDoesNotContainIt() {
        var input = new GuideRequirementSummary.Input(true, false, false,
            List.of("Copper Age"), "all", 0, List.of(), true, "16x Iron Ingot", false,
            "Beginner Paths", 0, 0, "Deny", false);

        var lines = GuideRequirementSummary.project(input);
        var display = GuideRequirementSummary.displayPlan(lines, false);

        assertTrue(lines.stream().noneMatch(line ->
            line.translationKey().equals("gui.progressivestages.tree.guide.requirements.slots")));
        assertTrue(display.suppressPrerequisiteDetails());
        assertFalse(display.suppressSlotDetails());
    }

    @Test
    void triggerRoutesKeepAnyAndAllConditionsSeparateFromPurchaseCosts() {
        var input = new GuideRequirementSummary.Input(true, false, false, List.of(), "all", 0,
            List.of(
                new GuideRequirementSummary.TriggerRoute("any_of", "Find a village",
                    List.of(new GuideRequirementSummary.Condition("Visit a village", 1),
                        new GuideRequirementSummary.Condition("Find a map", 2))),
                new GuideRequirementSummary.TriggerRoute("all_of", "Prepare supplies",
                    List.of(new GuideRequirementSummary.Condition("Mine iron", 16)))),
            true, "16x Iron Ingot", true, "", 0, 0, "deny", true);

        var lines = GuideRequirementSummary.project(input);

        assertEquals("gui.progressivestages.tree.guide.requirements.trigger.any",
            lines.get(1).translationKey());
        assertEquals("1", lines.get(1).arguments().get(0));
        assertTrue(lines.get(1).arguments().get(1).contains("Visit a village (need 1)"));
        assertTrue(lines.get(1).arguments().get(1).contains("Find a map (need 2)"));
        assertEquals("gui.progressivestages.tree.guide.requirements.trigger.all",
            lines.get(2).translationKey());
        assertEquals("gui.progressivestages.tree.guide.requirements.purchase.with_triggers",
            lines.get(3).translationKey());
    }

    @Test
    void unknownOrConcealedAcquisitionNeverClaimsThereAreNoRequirements() {
        var unknown = new GuideRequirementSummary.Input(false, false, false, List.of(), "all", 0,
            List.of(), false, "", false, "", 0, 0, "deny", false);
        var concealed = new GuideRequirementSummary.Input(true, true, false, List.of("Hidden stage"), "all", 0,
            List.of(), true, "16x Iron Ingot", false, "", 0, 0, "deny", false);

        assertEquals("gui.progressivestages.tree.guide.requirements.unavailable",
            GuideRequirementSummary.project(unknown).getFirst().translationKey());
        assertEquals("gui.progressivestages.tree.guide.requirements.unavailable",
            GuideRequirementSummary.project(concealed).getFirst().translationKey());
    }

    @Test
    void emptyConfirmedProjectionNamesTheMissingAutomaticRoute() {
        var input = new GuideRequirementSummary.Input(true, false, false, List.of(), "all", 0,
            List.of(), false, "", false, "", 0, 0, "deny", false);

        assertEquals("gui.progressivestages.tree.guide.requirements.no_route",
            GuideRequirementSummary.project(input).getFirst().translationKey());
    }

    @Test
    void configuredTriggerRouteWithMissingProgressDetailsIsUnavailable() {
        var input = new GuideRequirementSummary.Input(true, false, false, List.of(), "all", 0,
            List.of(), false, "", false, "", 0, 0, "deny", true);

        assertEquals("gui.progressivestages.tree.guide.requirements.unavailable",
            GuideRequirementSummary.project(input).getFirst().translationKey());
    }

    @Test
    void malformedConfiguredTriggerRouteShowsUnavailableInsteadOfBlankText() {
        var input = new GuideRequirementSummary.Input(true, false, false, List.of(), "all", 0,
            List.of(new GuideRequirementSummary.TriggerRoute("unknown", "", List.of())),
            false, "", false, "", 0, 0, "deny", true);

        assertEquals("gui.progressivestages.tree.guide.requirements.unavailable",
            GuideRequirementSummary.project(input).getFirst().translationKey());
    }

    @Test
    void hiddenPrerequisitesThatAreAlreadyMetDoNotLeakTheirNames() {
        var input = new GuideRequirementSummary.Input(true, false, true, List.of(), "all", 0,
            List.of(new GuideRequirementSummary.TriggerRoute("all_of", "Mine supplies",
                List.of(new GuideRequirementSummary.Condition("Mine iron", 4)))),
            false, "", false, "", 0, 0, "deny", true);

        var lines = GuideRequirementSummary.project(input);

        assertEquals("gui.progressivestages.tree.guide.requirements.hidden_met",
            lines.getFirst().translationKey());
        assertTrue(lines.stream().noneMatch(line -> line.arguments().contains("Hidden stage")));
    }

    @Test
    void oversizedRequirementProjectionFailsClosedInsteadOfClipping() {
        var input = new GuideRequirementSummary.Input(true, false, false, List.of(), "all", 0,
            List.of(new GuideRequirementSummary.TriggerRoute("all_of", "a".repeat(9000), List.of())),
            false, "", false, "", 0, 0, "deny", true);

        assertEquals("gui.progressivestages.tree.guide.requirements.unavailable",
            GuideRequirementSummary.project(input).getFirst().translationKey());
    }
}
