package com.enviouse.progressivestages.common.rehaul.challenge;

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

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ChallengeEngineTest {

    @Test
    void genericHitBudgetFailsOnConfiguredBoundary() {
        ResourceLocation challengeId = ResourceLocation.parse("pack:wither_no_hit");
        ChallengeBudget hits = new ChallengeBudget(ResourceLocation.parse("pack:hits"),
            ResourceLocation.parse("progressivestages:hits_taken"), BudgetMode.MAXIMUM,
            0, 2, 0, "", Map.of("entity", "minecraft:wither"));
        CompiledChallenge challenge = new CompiledChallenge(challengeId, "Wither trial", SubjectScope.PLAYER,
            new ConditionNode.Constant(true), new ConditionNode.Constant(false), new ConditionNode.Constant(false),
            List.of(hits), List.of(), 0, 0, ActionChain.EMPTY, ActionChain.EMPTY,
            ChallengeHud.defaults(), ConfigProvenance.legacy("test", "test", "challenges"));
        ChallengeEngine engine = new ChallengeEngine(
            new ConditionEvaluator(ConditionRegistry.get(), new ConditionStateStore()),
            new ActionExecutor(ActionRegistry.get()), ChallengeMeasureRegistry.get());
        engine.rebuild(List.of(challenge));
        Subject subject = new Subject();
        engine.reconcile(challengeId, condition(100), action(subject, 100));
        for (int hit = 1; hit <= 3; hit++) {
            engine.record(new ChallengeEvent("player", ResourceLocation.parse("progressivestages:hits_taken"),
                1, 100 + hit, Map.of("entity", "minecraft:wither")), action(subject, 100 + hit));
        }
        assertEquals(ChallengeStatus.FAILED, engine.session("player", challengeId).orElseThrow().status());
        assertFalse(engine.session("player", challengeId).orElseThrow().explanation().isBlank());
    }

    @Test
    void failedChallengeCanRestartWithinItsConfiguredRetryBudget() {
        ResourceLocation challengeId = ResourceLocation.parse("pack:retry_trial");
        ChallengeBudget hits = new ChallengeBudget(ResourceLocation.parse("pack:retry_hits"),
            ResourceLocation.parse("progressivestages:hits_taken"), BudgetMode.MAXIMUM,
            0, 0, 0, "", Map.of());
        CompiledChallenge challenge = new CompiledChallenge(challengeId, "Retry trial", SubjectScope.PLAYER,
            new ConditionNode.Constant(true), new ConditionNode.Constant(false), new ConditionNode.Constant(false),
            List.of(hits), List.of(), 0, 1, ActionChain.EMPTY, ActionChain.EMPTY,
            ChallengeHud.defaults(), ConfigProvenance.legacy("test", "test", "challenges"));
        ChallengeEngine engine = new ChallengeEngine(
            new ConditionEvaluator(ConditionRegistry.get(), new ConditionStateStore()),
            new ActionExecutor(ActionRegistry.get()), ChallengeMeasureRegistry.get());
        engine.rebuild(List.of(challenge));
        Subject subject = new Subject();
        engine.reconcile(challengeId, condition(100), action(subject, 100));
        engine.record(new ChallengeEvent("player", ResourceLocation.parse("progressivestages:hits_taken"),
            1, 101, Map.of()), action(subject, 101));
        assertEquals(ChallengeStatus.FAILED, engine.session("player", challengeId).orElseThrow().status());
        assertEquals(ChallengeStatus.ACTIVE,
            engine.reconcile(challengeId, condition(102), action(subject, 102)).status());
        assertEquals(2, engine.session("player", challengeId).orElseThrow().attempts());
    }

    private static ConditionContext condition(long now) {
        return new ConditionContext("player", SubjectScope.PLAYER, now, Map.of(), Set.of(), Map.of());
    }

    private static ActionContext action(ActionSubject subject, long now) {
        return new ActionContext(subject, now, Map.of(), Map.of());
    }

    private static final class Subject implements ActionSubject {
        private Set<StageId> stages = new HashSet<>();
        private Map<String, Double> variables = new HashMap<>();
        @Override public String id() { return "player"; }
        @Override public boolean hasStage(StageId stage) { return stages.contains(stage); }
        @Override public boolean grantStage(StageId stage) { return stages.add(stage); }
        @Override public boolean revokeStage(StageId stage) { return stages.remove(stage); }
        @Override public String stageState(StageId stage) { return hasStage(stage) ? "owned" : "missing"; }
        @Override public boolean setStageState(StageId stage, String state) { return true; }
        @Override public double variable(String id) { return variables.getOrDefault(id, 0.0); }
        @Override public void setVariable(String id, double value) { variables.put(id, value); }
        @Override public void sendMessage(String message) {}
        @Override public Object snapshot() { return new Snapshot(Set.copyOf(stages), Map.copyOf(variables)); }
        @Override public void restore(Object snapshot) { Snapshot value = (Snapshot) snapshot; stages = new HashSet<>(value.stages()); variables = new HashMap<>(value.variables()); }
    }

    private record Snapshot(Set<StageId> stages, Map<String, Double> variables) {}
}
