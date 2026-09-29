package com.enviouse.progressivestages.client;

import java.util.ArrayList;
import java.util.List;

/** Builds a safe, read-only guide summary from the accepted stage and live server snapshot. */
public final class GuideRequirementSummary {

    public record Condition(String label, int threshold) {
        public Condition {
            label = label == null ? "" : label;
            threshold = Math.max(0, threshold);
        }
    }

    public record TriggerRoute(String mode, String description, List<Condition> conditions) {
        public TriggerRoute {
            mode = mode == null ? "" : mode;
            description = description == null ? "" : description;
            conditions = conditions == null ? List.of() : List.copyOf(conditions);
        }
    }

    public record Line(String translationKey, List<String> arguments) {
        public Line {
            arguments = List.copyOf(arguments);
        }
    }

    public record DisplayPlan(boolean showGeneratedSummary, boolean suppressPrerequisiteDetails,
                              boolean suppressSlotDetails) {}

    public record Input(boolean verified, boolean concealedUnmetRequirement,
                 boolean hiddenRequirementAlreadyMet,
                 List<String> requiredStages, String dependencyMode, int dependencyCount,
                 List<TriggerRoute> triggerRoutes, boolean purchasable, String purchaseCost,
                 boolean purchaseRequiresTriggers, String slotGroup, int activeSlots,
                 int slotLimit, String fullSlotPolicy, boolean triggerRouteExpected) {
        public Input {
            requiredStages = requiredStages == null ? List.of() : List.copyOf(requiredStages);
            dependencyMode = dependencyMode == null ? "all" : dependencyMode;
            triggerRoutes = triggerRoutes == null ? List.of() : List.copyOf(triggerRoutes);
            purchaseCost = purchaseCost == null ? "" : purchaseCost;
            slotGroup = slotGroup == null ? "" : slotGroup;
            fullSlotPolicy = fullSlotPolicy == null ? "deny" : fullSlotPolicy;
        }
    }

    private GuideRequirementSummary() {}

    public static DisplayPlan displayPlan(List<Line> lines, boolean summaryEmbedded) {
        boolean available = lines != null && !lines.isEmpty() && lines.stream().noneMatch(line ->
            line.translationKey().equals("gui.progressivestages.tree.guide.requirements.unavailable")
                || line.translationKey().equals("gui.progressivestages.tree.guide.requirements.no_route"));
        boolean includesSlots = available && lines.stream().anyMatch(line ->
            line.translationKey().equals("gui.progressivestages.tree.guide.requirements.slots"));
        return new DisplayPlan(!summaryEmbedded, available, includesSlots);
    }

    public static List<Line> project(Input input) {
        if (input == null || !input.verified() || input.concealedUnmetRequirement()) {
            return List.of(line("gui.progressivestages.tree.guide.requirements.unavailable"));
        }
        if (input.triggerRouteExpected() != !input.triggerRoutes().isEmpty()) {
            return List.of(line("gui.progressivestages.tree.guide.requirements.unavailable"));
        }

        List<Line> lines = new ArrayList<>();
        if (input.requiredStages().isEmpty()) {
            lines.add(line(input.hiddenRequirementAlreadyMet()
                ? "gui.progressivestages.tree.guide.requirements.hidden_met"
                : "gui.progressivestages.tree.guide.requirements.none"));
        } else {
            String stages = String.join(", ", input.requiredStages());
            switch (input.dependencyMode()) {
                case "any" -> lines.add(line("gui.progressivestages.tree.guide.requirements.stages.any", stages));
                case "at_least" -> lines.add(line("gui.progressivestages.tree.guide.requirements.stages.count",
                    Integer.toString(input.dependencyCount()), stages));
                default -> lines.add(line("gui.progressivestages.tree.guide.requirements.stages.all", stages));
            }
        }

        int routeIndex = 1;
        for (TriggerRoute route : input.triggerRoutes()) {
            List<String> requirements = new ArrayList<>();
            if (!route.description().isBlank()) requirements.add(route.description());
            for (Condition condition : route.conditions()) {
                if (!condition.label().isBlank()) {
                    requirements.add(condition.threshold() > 0
                        ? condition.label() + " (need " + condition.threshold() + ")"
                        : condition.label());
                }
            }
            if (requirements.isEmpty()) {
                return List.of(line("gui.progressivestages.tree.guide.requirements.unavailable"));
            }
            boolean any = "any_of".equals(route.mode());
            String key = any
                ? "gui.progressivestages.tree.guide.requirements.trigger.any"
                : "gui.progressivestages.tree.guide.requirements.trigger.all";
            lines.add(line(key, Integer.toString(routeIndex++), String.join("; ", requirements)));
        }

        if (input.purchasable()) {
            if (input.purchaseCost().isBlank()) {
                return List.of(line("gui.progressivestages.tree.guide.requirements.unavailable"));
            }
            String key = input.purchaseRequiresTriggers() && !input.triggerRoutes().isEmpty()
                ? "gui.progressivestages.tree.guide.requirements.purchase.with_triggers"
                : "gui.progressivestages.tree.guide.requirements.purchase";
            lines.add(line(key, input.purchaseCost()));
        }

        if (!input.slotGroup().isBlank() && input.slotLimit() > 0) {
            lines.add(line("gui.progressivestages.tree.guide.requirements.slots",
                input.slotGroup(), Integer.toString(input.activeSlots()), Integer.toString(input.slotLimit()),
                input.fullSlotPolicy()));
        }

        if (input.triggerRoutes().isEmpty() && !input.purchasable()) {
            return List.of(line("gui.progressivestages.tree.guide.requirements.no_route"));
        }
        return withinGuideLimit(lines)
            ? List.copyOf(lines)
            : List.of(line("gui.progressivestages.tree.guide.requirements.unavailable"));
    }

    private static boolean withinGuideLimit(List<Line> lines) {
        StringBuilder text = new StringBuilder();
        for (Line line : lines) {
            for (String argument : line.arguments()) {
                if (!text.isEmpty()) text.append(' ');
                text.append(argument);
            }
        }
        return text.codePointCount(0, text.length())
                <= com.enviouse.progressivestages.common.config.StageGuide.MAX_TEXT_CODE_POINTS
            && text.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                <= com.enviouse.progressivestages.common.config.StageGuide.MAX_TEXT_BYTES;
    }

    private static Line line(String key, String... arguments) {
        return new Line(key, List.of(arguments));
    }
}
