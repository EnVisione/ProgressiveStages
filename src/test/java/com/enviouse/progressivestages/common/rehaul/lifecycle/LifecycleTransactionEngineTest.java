package com.enviouse.progressivestages.common.rehaul.lifecycle;

import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.rehaul.ConditionNode;
import com.enviouse.progressivestages.common.rehaul.ConfigProvenance;
import com.enviouse.progressivestages.common.rehaul.action.ActionChain;
import com.enviouse.progressivestages.common.rehaul.action.ActionContext;
import com.enviouse.progressivestages.common.rehaul.action.ActionExecutor;
import com.enviouse.progressivestages.common.rehaul.action.ActionRegistry;
import com.enviouse.progressivestages.common.rehaul.action.ActionSubject;
import com.enviouse.progressivestages.common.rehaul.condition.ConditionContext;
import com.enviouse.progressivestages.common.rehaul.condition.ConditionEvaluator;
import com.enviouse.progressivestages.common.rehaul.condition.ConditionRegistry;
import com.enviouse.progressivestages.common.rehaul.condition.ConditionStateStore;
import com.enviouse.progressivestages.common.rehaul.condition.SubjectScope;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LifecycleTransactionEngineTest {

    @Test
    void grantsAndRevokesSymmetricallyWithoutOscillation() {
        TestSubject subject = new TestSubject();
        StageId stage = StageId.parse("test:trial");
        CompiledLifecycleRule grant = rule("grant", stage, LifecycleDirection.GRANT, 100,
            new ConditionNode.Leaf(id("boolean"), Map.of("expected", true)));
        CompiledLifecycleRule revoke = rule("revoke", stage, LifecycleDirection.REVOKE, 200,
            new ConditionNode.Leaf(id("boolean"), Map.of("expected", true)));
        LifecycleTransactionEngine engine = engine();

        assertTrue(engine.evaluate(List.of(grant), condition(100, true), action(subject, 100), Map.of()).committed());
        assertTrue(subject.hasStage(stage));
        assertTrue(engine.evaluate(List.of(grant, revoke), condition(200, true), action(subject, 200), Map.of()).committed());
        assertFalse(subject.hasStage(stage));
        assertTrue(engine.evaluate(List.of(grant, revoke), condition(300, true), action(subject, 300), Map.of()).committed());
        assertTrue(subject.hasStage(stage));
    }

    @Test
    void rejectsDependencyCyclesBeforeMutation() {
        TestSubject subject = new TestSubject();
        StageId one = StageId.parse("test:one");
        StageId two = StageId.parse("test:two");
        LifecycleTransactionResult result = engine().evaluate(
            List.of(rule("grant_one", one, LifecycleDirection.GRANT, 100, new ConditionNode.Constant(true))),
            condition(100, true), action(subject, 100), Map.of(one, Set.of(two), two, Set.of(one)));
        assertFalse(result.committed());
        assertFalse(subject.hasStage(one));
    }

    private static LifecycleTransactionEngine engine() {
        ConditionEvaluator evaluator = new ConditionEvaluator(ConditionRegistry.get(), new ConditionStateStore());
        return new LifecycleTransactionEngine(evaluator, new ActionExecutor(ActionRegistry.get()),
            new TransitionHistory(100), 32);
    }

    private static CompiledLifecycleRule rule(String name, StageId stage, LifecycleDirection direction,
                                              int priority, ConditionNode condition) {
        return new CompiledLifecycleRule(ResourceLocation.fromNamespaceAndPath("test", name), stage, direction,
            condition, priority, SubjectScope.PLAYER, RepeatMode.REPEATABLE, false, false,
            0, 0, 0, "", ActionChain.EMPTY, ActionChain.EMPTY,
            ConfigProvenance.legacy("test", "test", "progression"));
    }

    private static ConditionContext condition(long now, boolean value) {
        return new ConditionContext("player", SubjectScope.PLAYER, now, Map.of("value", value), Set.of(), Map.of());
    }

    private static ActionContext action(ActionSubject subject, long now) {
        return new ActionContext(subject, now, Map.of(), Map.of());
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("progressivestages", path);
    }

    private static final class TestSubject implements ActionSubject {
        private Set<StageId> stages = new HashSet<>();
        private Map<StageId, String> states = new HashMap<>();
        private Map<String, Double> variables = new HashMap<>();
        private final List<String> messages = new ArrayList<>();

        @Override public String id() { return "player"; }
        @Override public boolean hasStage(StageId stage) { return stages.contains(stage); }
        @Override public boolean grantStage(StageId stage) { return stages.add(stage); }
        @Override public boolean revokeStage(StageId stage) { return stages.remove(stage); }
        @Override public String stageState(StageId stage) { return states.getOrDefault(stage, "missing"); }
        @Override public boolean setStageState(StageId stage, String state) { states.put(stage, state); return true; }
        @Override public double variable(String id) { return variables.getOrDefault(id, 0.0); }
        @Override public void setVariable(String id, double value) { variables.put(id, value); }
        @Override public void sendMessage(String message) { messages.add(message); }
        @Override public Object snapshot() { return new Snapshot(Set.copyOf(stages), Map.copyOf(states), Map.copyOf(variables)); }
        @Override public void restore(Object snapshot) {
            Snapshot state = (Snapshot) snapshot;
            stages = new HashSet<>(state.stages());
            states = new HashMap<>(state.states());
            variables = new HashMap<>(state.variables());
        }
    }

    private record Snapshot(Set<StageId> stages, Map<StageId, String> states, Map<String, Double> variables) {}
}
