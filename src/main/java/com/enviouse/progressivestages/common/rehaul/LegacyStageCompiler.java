package com.enviouse.progressivestages.common.rehaul;

import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.config.StageDefinition;
import com.enviouse.progressivestages.common.lock.CategoryLocks;
import com.enviouse.progressivestages.common.lock.ConditionalRule;
import com.enviouse.progressivestages.common.lock.LockDefinition;
import com.enviouse.progressivestages.common.lock.PrefixEntry;
import com.enviouse.progressivestages.common.rehaul.action.ActionChain;
import com.enviouse.progressivestages.common.rehaul.condition.SubjectScope;
import com.enviouse.progressivestages.common.rehaul.lifecycle.CompiledLifecycleRule;
import com.enviouse.progressivestages.common.rehaul.lifecycle.LifecycleDirection;
import com.enviouse.progressivestages.common.rehaul.lifecycle.RepeatMode;
import com.enviouse.progressivestages.common.trigger.TriggerCondition;
import com.enviouse.progressivestages.common.trigger.TriggerMode;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class LegacyStageCompiler {

    private LegacyStageCompiler() {}

    public static CompiledStage compile(StageDefinition stage, String sourceId) {
        ConfigProvenance root = stage.getProvenance() != null
            ? stage.getProvenance()
            : ConfigProvenance.legacy(sourceId, "stage", "");
        List<CompiledRule> rules = new ArrayList<>();
        LockDefinition locks = stage.getLocks();

        addCategory(rules, stage, "items", "use", locks.items(), root);
        addCategory(rules, stage, "blocks", "interact", locks.blocks(), root);
        addCategory(rules, stage, "fluids", "interact", locks.fluids(), root);
        addCategory(rules, stage, "entities", "presence", locks.entities(), root);
        addCategory(rules, stage, "enchants", "apply", locks.enchants(), root);
        addCategory(rules, stage, "crops", "grow", locks.crops(), root);
        addCategory(rules, stage, "screens", "open", locks.screens(), root);
        addCategory(rules, stage, "loot", "generate", locks.loot(), root);
        addCategory(rules, stage, "trades", "trade", locks.trades(), root);
        addCategory(rules, stage, "professions", "trade", locks.professions(), root);
        addCategory(rules, stage, "advancements", "display", locks.advancements(), root);
        addCategory(rules, stage, "beacon", "apply", locks.beacon(), root);
        addCategory(rules, stage, "brewing", "brew", locks.brewing(), root);
        addCategory(rules, stage, "pets.taming", "tame", locks.petsTaming(), root);
        addCategory(rules, stage, "pets.breeding", "breed", locks.petsBreeding(), root);
        addCategory(rules, stage, "pets.commanding", "command", locks.petsCommanding(), root);
        addCategory(rules, stage, "mobs", "spawn", locks.mobSpawns(), root);
        addCategory(rules, stage, "recipes.ids", "craft", locks.recipeIds(), root);
        addCategory(rules, stage, "recipes.outputs", "craft", locks.recipeOutputs(), root);
        addCategory(rules, stage, "structures", "enter", locks.structures().lockedEntry(), root);

        CategoryLocks dimensions = CategoryLocks.builder().addLocked(
            locks.lockedDimensions().stream().map(ResourceLocation::toString).toList()).build();
        addCategory(rules, stage, "dimensions", "enter", dimensions, root);
        addConditionalRules(rules, stage, root);

        return new CompiledStage(
            stage.getId(),
            stage.getDisplayName(),
            stage.getDescription(),
            stage.getPriority(),
            stage.getSchemaVersion(),
            sourceId,
            rules,
            compileProgression(stage, root),
            Map.of(
                "scope", stage.getScope(),
                "category", stage.getCategory(),
                "tags", stage.getTags(),
                "legacy", stage.getSchemaVersion() < 4),
            stage,
            root);
    }

    private static CompiledProgression compileProgression(StageDefinition stage, ConfigProvenance root) {
        List<CompiledLifecycleRule> lifecycle = new ArrayList<>();
        int index = 0;
        for (var trigger : stage.getTriggers()) {
            List<ConditionNode> children = trigger.conditions().stream().map(LegacyStageCompiler::legacyTrigger).toList();
            ConditionNode condition = children.size() == 1 ? children.getFirst()
                : trigger.mode() == TriggerMode.ANY_OF ? new ConditionNode.Any(children) : new ConditionNode.All(children);
            lifecycle.add(new CompiledLifecycleRule(
                lifecycleId(stage.getId(), "grant", index++), stage.getId(), LifecycleDirection.GRANT,
                condition, stage.getPriority(), SubjectScope.PLAYER, RepeatMode.ONCE,
                false, false, 0, 0, 0, "", ActionChain.EMPTY, ActionChain.EMPTY,
                root.child("triggers", Integer.toString(index - 1))));
        }
        if (stage.getRevoke().onDeath()) {
            lifecycle.add(new CompiledLifecycleRule(lifecycleId(stage.getId(), "revoke_death", 0),
                stage.getId(), LifecycleDirection.REVOKE,
                new ConditionNode.Leaf(ResourceLocation.fromNamespaceAndPath("progressivestages", "death"),
                    Map.of("count", 1)), stage.getPriority(), SubjectScope.PLAYER, RepeatMode.EDGE,
                false, false, 0, 0, 0, "", ActionChain.EMPTY, ActionChain.EMPTY,
                root.child("revoke", "on_death")));
        }
        if (stage.getRevoke().maintainsXp()) {
            lifecycle.add(new CompiledLifecycleRule(lifecycleId(stage.getId(), "revoke_xp", 0),
                stage.getId(), LifecycleDirection.REVOKE,
                new ConditionNode.Comparison(ResourceLocation.fromNamespaceAndPath("progressivestages", "xp"),
                    ConditionNode.Comparison.Operator.LESS, stage.getRevoke().xpBelow(), Map.of()),
                stage.getPriority(), SubjectScope.PLAYER, RepeatMode.LEVEL,
                false, false, 0, 0, 0, "", ActionChain.EMPTY, ActionChain.EMPTY,
                root.child("revoke", "xp_below")));
        }
        if (stage.isTemporary()) {
            lifecycle.add(new CompiledLifecycleRule(lifecycleId(stage.getId(), "expiry", 0),
                stage.getId(), LifecycleDirection.REVOKE,
                new ConditionNode.Leaf(ResourceLocation.fromNamespaceAndPath("progressivestages", "stage_held_duration"),
                    Map.of("id", stage.getId().toString(), "minimum", stage.getDurationMillis())),
                stage.getPriority(), SubjectScope.PLAYER, RepeatMode.LEVEL,
                false, false, 0, 0, 0, "", ActionChain.EMPTY, ActionChain.EMPTY,
                root.child("stage", "duration")));
        }
        return new CompiledProgression(lifecycle, List.of(), List.of(), List.of(), List.of(), List.of(), Map.of(),
            List.of(), List.of(), Map.of());
    }

    private static ConditionNode legacyTrigger(TriggerCondition condition) {
        Map<String, Object> arguments = new LinkedHashMap<>();
        if (!condition.target().isBlank()) arguments.put("id", condition.target());
        arguments.put("count", condition.count());
        if (!condition.with().isBlank()) arguments.put("with", condition.with());
        if (condition.provider() != null) arguments.put("provider", condition.provider().toString());
        if (condition.requiredSessionStage() != null) {
            arguments.put("required_session_stage", condition.requiredSessionStage().toString());
        }
        if (!condition.outcomes().isEmpty()) {
            arguments.put("outcomes", condition.outcomes().stream().map(Enum::name).toList());
        }
        return new ConditionNode.Leaf(ResourceLocation.fromNamespaceAndPath("progressivestages",
            condition.type().name().toLowerCase(java.util.Locale.ROOT)), arguments);
    }

    private static ResourceLocation lifecycleId(StageId stage, String kind, int index) {
        return ResourceLocation.fromNamespaceAndPath(stage.getNamespace(),
            stage.getPath() + "/lifecycle/" + kind + "/" + index);
    }

    private static void addCategory(List<CompiledRule> output, StageDefinition stage, String category,
                                    String action, CategoryLocks locks, ConfigProvenance root) {
        List<ResourceLocation> parentIds = new ArrayList<>();
        int index = 0;
        for (PrefixEntry prefix : locks.locked()) {
            ResourceLocation id = ruleId(stage.getId(), category, index++, "lock");
            parentIds.add(id);
            output.add(new CompiledRule(id, stage.getId(), category, action, RuleEffect.LOCK,
                SelectorSpec.fromPrefix(prefix), stage.getPriority(), RuleLifetime.PERMANENT,
                new ConditionNode.Constant(true), null, ViewerPolicy.INHERIT, Map.of(),
                root.child(category, "locked")));
        }

        int exclusionIndex = 0;
        for (ResourceLocation allowed : locks.alwaysUnlocked()) {
            for (ResourceLocation parent : parentIds) {
                String raw = allowed.toString();
                output.add(new CompiledRule(
                    ruleId(stage.getId(), category, exclusionIndex++, "exclude"),
                    stage.getId(), category, action, RuleEffect.EXCLUDE,
                    SelectorSpec.parse(raw).orElseThrow(), stage.getPriority(), RuleLifetime.PERMANENT,
                    new ConditionNode.Constant(true), parent, ViewerPolicy.INHERIT, Map.of(),
                    root.child(category, "always_unlocked")));
            }
        }
    }

    private static void addConditionalRules(List<CompiledRule> output, StageDefinition stage,
                                            ConfigProvenance root) {
        for (ConditionalRule conditional : stage.getConditionalRules()) {
            RuleEffect effect = conditional.effect() == ConditionalRule.Effect.LOCK
                ? RuleEffect.LOCK : RuleEffect.UNLOCK;
            RuleLifetime lifetime = conditional.isTriggered() ? RuleLifetime.DURATION : RuleLifetime.LIVE;
            ConditionNode condition = legacyCondition(conditional);
            for (ConditionalRule.TargetType targetType : conditional.targets().types()) {
                List<ResourceLocation> parentIds = new ArrayList<>();
                int index = 0;
                for (PrefixEntry target : conditional.targets().included(targetType)) {
                    ResourceLocation id = childRuleId(conditional.id(), targetType.name(), index++, "target");
                    parentIds.add(id);
                    output.add(new CompiledRule(id, stage.getId(), targetType.name().toLowerCase(),
                        targetType.name().toLowerCase(), effect, SelectorSpec.fromPrefix(target),
                        conditional.priority(), lifetime, condition, null, ViewerPolicy.INHERIT,
                        Map.of("duration_millis", conditional.durationMillis(),
                            "refresh_duration", conditional.refreshDuration()),
                        root.child("conditional_rules", conditional.id().toString())));
                }
                int exclusionIndex = 0;
                for (PrefixEntry excluded : conditional.targets().excluded(targetType)) {
                    for (ResourceLocation parent : parentIds) {
                        output.add(new CompiledRule(
                            childRuleId(conditional.id(), targetType.name(), exclusionIndex++, "exclude"),
                            stage.getId(), targetType.name().toLowerCase(),
                            targetType.name().toLowerCase(), RuleEffect.EXCLUDE,
                            SelectorSpec.fromPrefix(excluded), conditional.priority(), lifetime,
                            condition, parent, ViewerPolicy.INHERIT, Map.of(),
                            root.child("conditional_rules", conditional.id().toString())));
                    }
                }
            }
        }
    }

    private static ConditionNode legacyCondition(ConditionalRule rule) {
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("stage_state", rule.stageState().name().toLowerCase());
        arguments.put("context_mode", rule.context().mode().name().toLowerCase());
        arguments.put("dimensions", rule.context().dimensions().stream().map(PrefixEntry::raw).toList());
        arguments.put("structures", rule.context().structures().stream().map(PrefixEntry::raw).toList());
        arguments.put("biomes", rule.context().biomes().stream().map(PrefixEntry::raw).toList());
        arguments.put("trigger", rule.triggerType().name().toLowerCase());
        return new ConditionNode.Leaf(
            ResourceLocation.fromNamespaceAndPath("progressivestages", "legacy_context"), arguments);
    }

    private static ResourceLocation ruleId(StageId stage, String category, int index, String kind) {
        String path = stage.getPath() + "/" + sanitize(category) + "/" + kind + "/" + index;
        return ResourceLocation.fromNamespaceAndPath(stage.getNamespace(), path);
    }

    private static ResourceLocation childRuleId(ResourceLocation parent, String category, int index, String kind) {
        return ResourceLocation.fromNamespaceAndPath(parent.getNamespace(),
            parent.getPath() + "/" + sanitize(category) + "/" + kind + "/" + index);
    }

    private static String sanitize(String value) {
        return value.toLowerCase().replace('.', '/');
    }
}
