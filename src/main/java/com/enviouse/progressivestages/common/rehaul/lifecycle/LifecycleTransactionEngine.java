package com.enviouse.progressivestages.common.rehaul.lifecycle;

import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.rehaul.action.ActionContext;
import com.enviouse.progressivestages.common.rehaul.action.ActionExecution;
import com.enviouse.progressivestages.common.rehaul.action.ActionExecutor;
import com.enviouse.progressivestages.common.rehaul.condition.ConditionContext;
import com.enviouse.progressivestages.common.rehaul.condition.ConditionEvaluator;
import com.enviouse.progressivestages.common.rehaul.condition.ConditionTrace;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class LifecycleTransactionEngine {

    public static final int EMERGENCY_TRANSITION_CEILING = 4_096;

    private final ConditionEvaluator conditionEvaluator;
    private final ActionExecutor actionExecutor;
    private final TransitionHistory history;
    private final int transitionBudget;
    private final AtomicLong transactionIds = new AtomicLong();
    private final Map<StateKey, RuleState> states = new ConcurrentHashMap<>();
    private final Set<StateKey> armed = ConcurrentHashMap.newKeySet();

    public LifecycleTransactionEngine(ConditionEvaluator conditionEvaluator, ActionExecutor actionExecutor,
                                      TransitionHistory history, int transitionBudget) {
        this.conditionEvaluator = conditionEvaluator;
        this.actionExecutor = actionExecutor;
        this.history = history;
        if (transitionBudget < 1 || transitionBudget > EMERGENCY_TRANSITION_CEILING) {
            throw new IllegalArgumentException("Transition budget is outside the allowed range");
        }
        this.transitionBudget = transitionBudget;
    }

    public void arm(String subject, ResourceLocation rule) {
        armed.add(new StateKey(subject, rule));
    }

    public void reset(String subject, ResourceLocation rule) {
        StateKey key = new StateKey(subject, rule);
        states.remove(key);
        armed.remove(key);
    }

    public List<LifecycleStateSnapshot> snapshot() {
        return states.entrySet().stream().map(entry -> {
            RuleState value = entry.getValue();
            return new LifecycleStateSnapshot(entry.getKey().subject(), entry.getKey().rule(),
                value.lastMatched, value.committedOnce, value.matchSince,
                value.lastCommittedAt, value.cooldownUntil, armed.contains(entry.getKey()));
        }).toList();
    }

    public void restore(List<LifecycleStateSnapshot> snapshot, long nextTransactionId) {
        states.clear();
        armed.clear();
        transactionIds.set(Math.max(0, nextTransactionId));
        if (snapshot == null) return;
        for (LifecycleStateSnapshot value : snapshot) {
            StateKey key = new StateKey(value.subject(), value.rule());
            RuleState state = new RuleState();
            state.lastMatched = value.lastMatched();
            state.committedOnce = value.committedOnce();
            state.matchSince = value.matchSince();
            state.lastCommittedAt = value.lastCommittedAt();
            state.cooldownUntil = value.cooldownUntil();
            states.put(key, state);
            if (value.armed()) armed.add(key);
        }
    }

    public long transactionId() {
        return transactionIds.get();
    }

    public LifecycleTransactionResult evaluate(List<CompiledLifecycleRule> rules,
                                               ConditionContext conditionContext,
                                               ActionContext actionContext,
                                               Map<StageId, Set<StageId>> dependencies) {
        long transactionId = transactionIds.incrementAndGet();
        long now = conditionContext.nowMillis();
        List<Candidate> candidates = new ArrayList<>();
        for (CompiledLifecycleRule rule : rules == null ? List.<CompiledLifecycleRule>of() : rules) {
            if (!eligibleOwnership(rule, actionContext)) continue;
            StateKey key = new StateKey(actionContext.subject().id(), rule.id());
            RuleState state = states.computeIfAbsent(key, ignored -> new RuleState());
            if (rule.repeatMode() == RepeatMode.MANUAL && !armed.contains(key)) continue;
            if (rule.repeatMode() == RepeatMode.ONCE && state.committedOnce) continue;
            if (now < state.cooldownUntil) continue;
            ConditionTrace trace = conditionEvaluator.evaluate(rule.condition(), conditionContext);
            boolean matched = trace.result().matched();
            if (matched && !state.lastMatched) state.matchSince = now;
            if (!matched) state.matchSince = 0;
            boolean edgeAllowed = rule.repeatMode() != RepeatMode.EDGE || matched && !state.lastMatched;
            boolean debounced = matched && now - state.matchSince >= rule.debounceMillis();
            state.lastMatched = matched;
            if (debounced && edgeAllowed) candidates.add(new Candidate(rule, trace));
        }
        Map<StageId, Candidate> winners = winners(candidates);
        if (winners.size() > transitionBudget || winners.size() > EMERGENCY_TRANSITION_CEILING) {
            return result(false, false, "transition_budget", List.of(), Map.of(),
                "The transaction exceeded its transition budget");
        }
        String dependencyError = validateDependencies(winners, dependencies, actionContext);
        if (dependencyError != null) return result(false, false, "dependency_cycle", List.of(), Map.of(), dependencyError);

        Object snapshot = actionContext.subject().snapshot();
        List<ResourceLocation> applied = new ArrayList<>();
        Map<StageId, LifecycleDirection> transitions = new LinkedHashMap<>();
        for (Candidate candidate : winners.values().stream()
                .sorted(Comparator.comparingInt((Candidate value) -> value.rule().priority()).reversed()
                    .thenComparing(value -> value.rule().id().toString())).toList()) {
            CompiledLifecycleRule rule = candidate.rule();
            boolean changed = applyTransition(rule, actionContext);
            if (!changed && rule.direction() == LifecycleDirection.SET_STATE) {
                actionContext.subject().restore(snapshot);
                record(transactionId, now, actionContext.subject().id(), rule, false, "Stage state transition was rejected");
                return result(false, true, "transition_rejected", applied, transitions,
                    "A stage state transition was rejected");
            }
            ActionExecution actions = actionExecutor.execute(rule.successActions(), actionContext);
            if (!actions.success()) {
                actionContext.subject().restore(snapshot);
                actionExecutor.execute(rule.failureActions(), actionContext);
                record(transactionId, now, actionContext.subject().id(), rule, false, actions.explanation());
                return result(false, true, "action_failed", applied, transitions, actions.explanation());
            }
            applied.add(rule.id());
            transitions.put(rule.targetStage(), rule.direction());
            StateKey key = new StateKey(actionContext.subject().id(), rule.id());
            RuleState state = states.computeIfAbsent(key, ignored -> new RuleState());
            state.committedOnce = true;
            state.lastCommittedAt = now;
            state.cooldownUntil = safeAdd(now, rule.cooldownMillis());
            armed.remove(key);
            record(transactionId, now, actionContext.subject().id(), rule, true, "Lifecycle transition committed");
        }
        return result(true, false, "ok", applied, transitions,
            applied.isEmpty() ? "No lifecycle transition matched" : "Lifecycle transaction committed");
    }

    private static boolean eligibleOwnership(CompiledLifecycleRule rule, ActionContext context) {
        boolean owned = context.subject().hasStage(rule.targetStage());
        return switch (rule.direction()) {
            case GRANT -> !owned || rule.evaluateWhileOwned();
            case REVOKE -> owned || rule.evaluateWhileMissing();
            case SET_STATE -> true;
        };
    }

    private static Map<StageId, Candidate> winners(List<Candidate> candidates) {
        Map<StageId, Candidate> winners = new LinkedHashMap<>();
        for (Candidate candidate : candidates) {
            winners.merge(candidate.rule().targetStage(), candidate, (left, right) -> {
                if (right.rule().priority() > left.rule().priority()) return right;
                if (right.rule().priority() < left.rule().priority()) return left;
                if (right.rule().direction() == LifecycleDirection.REVOKE
                        && left.rule().direction() != LifecycleDirection.REVOKE) return right;
                return right.rule().id().toString().compareTo(left.rule().id().toString()) < 0 ? right : left;
            });
        }
        return winners;
    }

    private static String validateDependencies(Map<StageId, Candidate> winners,
                                               Map<StageId, Set<StageId>> dependencies,
                                               ActionContext context) {
        Map<StageId, Set<StageId>> graph = dependencies == null ? Map.of() : dependencies;
        for (StageId stage : winners.keySet()) {
            if (hasCycle(stage, graph, new LinkedHashSet<>(), new LinkedHashSet<>())) {
                return "A stage dependency cycle includes " + stage;
            }
            Candidate candidate = winners.get(stage);
            if (candidate.rule().direction() != LifecycleDirection.GRANT) continue;
            for (StageId dependency : graph.getOrDefault(stage, Set.of())) {
                Candidate planned = winners.get(dependency);
                boolean willOwn = context.subject().hasStage(dependency)
                    || planned != null && planned.rule().direction() == LifecycleDirection.GRANT;
                if (!willOwn) return "A required stage is missing. " + dependency;
            }
        }
        return null;
    }

    private static boolean hasCycle(StageId stage, Map<StageId, Set<StageId>> graph,
                                    Set<StageId> visiting, Set<StageId> visited) {
        if (visiting.contains(stage)) return true;
        if (!visited.add(stage)) return false;
        visiting.add(stage);
        for (StageId dependency : graph.getOrDefault(stage, Set.of())) {
            if (hasCycle(dependency, graph, visiting, visited)) return true;
        }
        visiting.remove(stage);
        return false;
    }

    private static boolean applyTransition(CompiledLifecycleRule rule, ActionContext context) {
        return switch (rule.direction()) {
            case GRANT -> context.subject().grantStage(rule.targetStage());
            case REVOKE -> context.subject().revokeStage(rule.targetStage());
            case SET_STATE -> context.subject().setStageState(rule.targetStage(), rule.targetState());
        };
    }

    private void record(long transaction, long now, String subject, CompiledLifecycleRule rule,
                        boolean committed, String explanation) {
        history.add(new TransitionHistoryEntry(transaction, now, subject, rule.id(), rule.targetStage(),
            rule.direction(), committed, explanation));
    }

    private static LifecycleTransactionResult result(boolean committed, boolean rolledBack, String code,
                                                     List<ResourceLocation> applied,
                                                     Map<StageId, LifecycleDirection> transitions,
                                                     String explanation) {
        return new LifecycleTransactionResult(committed, rolledBack, code, applied, transitions, explanation);
    }

    private static long safeAdd(long left, long right) {
        return right >= Long.MAX_VALUE - left ? Long.MAX_VALUE : left + right;
    }

    private record Candidate(CompiledLifecycleRule rule, ConditionTrace trace) {}
    private record StateKey(String subject, ResourceLocation rule) {}

    private static final class RuleState {
        boolean lastMatched;
        boolean committedOnce;
        long matchSince;
        long lastCommittedAt;
        long cooldownUntil;
    }

    public record LifecycleStateSnapshot(String subject, ResourceLocation rule,
                                         boolean lastMatched, boolean committedOnce,
                                         long matchSince, long lastCommittedAt,
                                         long cooldownUntil, boolean armed) {}
}
