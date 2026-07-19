package com.enviouse.progressivestages.common.rehaul.condition;

import com.enviouse.progressivestages.common.rehaul.ConditionNode;
import com.enviouse.progressivestages.common.rehaul.RuleLifetime;
import com.enviouse.progressivestages.common.rehaul.lifecycle.ActivationPolicy;
import com.enviouse.progressivestages.common.rehaul.lifecycle.TemporaryRuleEngine;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConditionEngineTest {

    private final ConditionCompiler compiler = new ConditionCompiler(ConditionRegistry.get());
    private final ConditionStateStore states = new ConditionStateStore();
    private final ConditionEvaluator evaluator = new ConditionEvaluator(ConditionRegistry.get(), states);

    @Test
    void compilesNestedBooleanAndComparisonTrees() {
        ConditionNode condition = compiler.compile(Map.of("all", List.of(
            Map.of("type", "dimension", "id", "minecraft:the_end"),
            Map.of("any", List.of(
                Map.of("type", "current_health", "minimum", 10),
                Map.of("comparison", Map.of("provider", "pack:rank", "operator", "greater_or_equal", "expected", 3)))))));
        ConditionContext context = context(100, Map.of(
            "dimension.minecraft:the_end", "minecraft:the_end",
            "health", 4,
            "pack:rank", 5));
        assertTrue(evaluator.evaluate(condition, context).result().matched());
    }

    @Test
    void sequenceAdvancesInOrderAndTimesOut() {
        ConditionNode sequence = compiler.compile(Map.of("sequence", Map.of(
            "timeout", "5s",
            "children", List.of(
                Map.of("type", "boolean", "expected", true),
                Map.of("type", "stage_owned", "id", "pack:trial")))));
        assertFalse(evaluator.evaluate(sequence, context(100, Map.of("value", true))).result().matched());
        assertTrue(evaluator.evaluate(sequence, context(200,
            Map.of("stages.pack:trial", Set.of("pack:trial")))).result().matched());

        assertFalse(evaluator.evaluate(sequence, context(1_000, Map.of("value", true))).result().matched());
        assertFalse(evaluator.evaluate(sequence, context(7_000,
            Map.of("stages.pack:trial", Set.of("pack:trial")))).result().matched());
    }

    @Test
    void everyLifetimeUsesTheSameConditionTree() {
        ConditionNode condition = compiler.compile(Map.of("type", "boolean"));
        TemporaryRuleEngine engine = new TemporaryRuleEngine(evaluator);
        ResourceLocation rule = ResourceLocation.parse("test:rule");
        ConditionContext yes = context(100, Map.of("value", true));
        ConditionContext no = context(200, Map.of("value", false));

        assertTrue(engine.evaluate(rule, ActivationPolicy.live(), condition, null, yes).active());
        assertFalse(engine.evaluate(rule, ActivationPolicy.live(), condition, null, no).active());

        ActivationPolicy duration = new ActivationPolicy(RuleLifetime.DURATION, 1_000, 0, 0, 0,
            0, 0, false, false, "");
        assertTrue(engine.evaluate(ResourceLocation.parse("test:duration"), duration, condition, null, yes).active());
        assertTrue(engine.evaluate(ResourceLocation.parse("test:duration"), duration, condition, null,
            context(500, Map.of("value", false))).active());
        assertFalse(engine.evaluate(ResourceLocation.parse("test:duration"), duration, condition, null,
            context(1_500, Map.of("value", false))).active());

        ActivationPolicy latched = new ActivationPolicy(RuleLifetime.LATCHED, 0, 0, 0, 0,
            0, 0, false, false, "");
        ConditionNode reset = compiler.compile(Map.of("type", "boolean", "expected", false));
        assertTrue(engine.evaluate(ResourceLocation.parse("test:latched"), latched, condition, reset, yes).active());
        assertFalse(engine.evaluate(ResourceLocation.parse("test:latched"), latched, condition, reset, no).active());
    }

    @Test
    void playerAndStructureLifecycleConditionsUseRuntimeValues() {
        ConditionNode playerKill = compiler.compile(Map.of("type", "player_kill", "count", 1));
        ConditionNode otherPlayerDeath = compiler.compile(Map.of("type", "other_player_death", "count", 1));
        ConditionNode structureTime = compiler.compile(Map.of("type", "structure_time",
            "id", "minecraft:stronghold", "count", 30));
        ConditionNode structureLeave = compiler.compile(Map.of("type", "leave_structure",
            "id", "minecraft:stronghold", "count", 1));
        ConditionContext context = context(100, Map.of(
            "player_kill", 1D,
            "other_player_death", 1D,
            "structure_time.minecraft:stronghold", 35D,
            "leave_structure.minecraft:stronghold", 1D));

        assertTrue(evaluator.evaluate(playerKill, context).result().matched());
        assertTrue(evaluator.evaluate(otherPlayerDeath, context).result().matched());
        assertTrue(evaluator.evaluate(structureTime, context).result().matched());
        assertTrue(evaluator.evaluate(structureLeave, context).result().matched());
    }

    private static ConditionContext context(long now, Map<String, Object> values) {
        return new ConditionContext("player", SubjectScope.PLAYER, now, values, Set.of(), Map.of());
    }
}
