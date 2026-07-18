package com.enviouse.progressivestages.common.rehaul.lifecycle;

import com.enviouse.progressivestages.common.rehaul.ConditionNode;
import com.enviouse.progressivestages.common.rehaul.RuleLifetime;
import com.enviouse.progressivestages.common.rehaul.condition.ConditionContext;
import com.enviouse.progressivestages.common.rehaul.condition.ConditionEvaluator;
import com.enviouse.progressivestages.common.rehaul.condition.ConditionTrace;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public final class TemporaryRuleEngine {

    private final ConditionEvaluator evaluator;
    private final Map<Key, State> states = new ConcurrentHashMap<>();

    public TemporaryRuleEngine(ConditionEvaluator evaluator) {
        this.evaluator = Objects.requireNonNull(evaluator, "evaluator");
    }

    public ActivationDecision evaluate(ResourceLocation ruleId, ActivationPolicy policy,
                                       ConditionNode condition, ConditionNode resetCondition,
                                       ConditionContext context) {
        Key key = new Key(context.subjectId(), ruleId);
        State state = states.computeIfAbsent(key, ignored -> new State());
        long now = context.nowMillis();
        ConditionTrace trace = evaluator.evaluate(condition, context);
        boolean matched = trace.result().matched();
        if (resetCondition != null && evaluator.evaluate(resetCondition, context).result().matched()) {
            state.deactivate(now, policy.cooldownMillis());
            state.latched = false;
        }
        boolean active = switch (policy.lifetime()) {
            case PERMANENT -> true;
            case LIVE -> evaluateLive(state, matched, now, policy);
            case DURATION -> evaluateDuration(state, matched, now, policy);
            case LATCHED -> evaluateLatched(state, matched, now, policy);
            case SESSION -> evaluateSession(state, matched && sessionMatches(policy, context), now, policy);
            case SCHEDULE -> evaluateLive(state, matched, now, policy);
        };
        state.lastMatched = matched;
        return new ActivationDecision(active, state.activeSince, state.expiresAt, state.cooldownUntil,
            trace, active ? "Temporary rule is active" : "Temporary rule is inactive");
    }

    public void clearSubject(String subject) {
        states.keySet().removeIf(key -> key.subject().equals(subject));
    }

    public void clear() {
        states.clear();
    }

    public List<TemporaryStateSnapshot> snapshot() {
        return states.entrySet().stream().map(entry -> {
            State value = entry.getValue();
            return new TemporaryStateSnapshot(entry.getKey().subject(), entry.getKey().rule(),
                value.active, value.latched, value.lastMatched, value.activeSince,
                value.inactiveSince, value.matchSince, value.unmatchedSince,
                value.expiresAt, value.cooldownUntil);
        }).toList();
    }

    public void restore(List<TemporaryStateSnapshot> snapshot) {
        states.clear();
        if (snapshot == null) return;
        for (TemporaryStateSnapshot value : snapshot) {
            State state = new State();
            state.active = value.active();
            state.latched = value.latched();
            state.lastMatched = value.lastMatched();
            state.activeSince = value.activeSince();
            state.inactiveSince = value.inactiveSince();
            state.matchSince = value.matchSince();
            state.unmatchedSince = value.unmatchedSince();
            state.expiresAt = value.expiresAt();
            state.cooldownUntil = value.cooldownUntil();
            states.put(new Key(value.subject(), value.rule()), state);
        }
    }

    public record TemporaryStateSnapshot(String subject, ResourceLocation rule,
                                         boolean active, boolean latched, boolean lastMatched,
                                         long activeSince, long inactiveSince, long matchSince,
                                         long unmatchedSince, long expiresAt, long cooldownUntil) {}

    private static boolean evaluateLive(State state, boolean matched, long now, ActivationPolicy policy) {
        if (matched) {
            if (now < state.cooldownUntil || now - state.inactiveSince < policy.minimumInactiveMillis()) return false;
            if (state.matchSince == 0) state.matchSince = now;
            if (now - state.matchSince < policy.debounceMillis()) return state.active;
            state.activate(now, Long.MAX_VALUE);
            return true;
        }
        state.matchSince = 0;
        if (state.active && now - state.activeSince < policy.minimumActiveMillis()) return true;
        if (state.active && policy.graceMillis() > 0 && state.unmatchedSince == 0) state.unmatchedSince = now;
        if (state.active && state.unmatchedSince > 0 && now - state.unmatchedSince < policy.graceMillis()) return true;
        state.deactivate(now, policy.cooldownMillis());
        return false;
    }

    private static boolean evaluateDuration(State state, boolean matched, long now, ActivationPolicy policy) {
        if (state.active && now >= state.expiresAt) state.deactivate(now, policy.cooldownMillis());
        boolean edge = matched && !state.lastMatched;
        if (matched && (edge || policy.refreshDuration()) && now >= state.cooldownUntil) {
            state.activate(now, safeAdd(now, Math.max(1, policy.durationMillis())));
        }
        return state.active && now < state.expiresAt;
    }

    private static boolean evaluateLatched(State state, boolean matched, long now, ActivationPolicy policy) {
        if (matched && now >= state.cooldownUntil) {
            state.latched = true;
            state.activate(now, Long.MAX_VALUE);
        }
        return state.latched;
    }

    private static boolean evaluateSession(State state, boolean matched, long now, ActivationPolicy policy) {
        return evaluateLive(state, matched, now, policy);
    }

    private static boolean sessionMatches(ActivationPolicy policy, ConditionContext context) {
        if (policy.sessionKey().isBlank()) return true;
        Object value = context.values().get("session." + policy.sessionKey());
        return Boolean.TRUE.equals(value) || value instanceof Number number && number.doubleValue() > 0;
    }

    private static long safeAdd(long value, long amount) {
        return amount >= Long.MAX_VALUE - value ? Long.MAX_VALUE : value + amount;
    }

    private record Key(String subject, ResourceLocation rule) {}

    private static final class State {
        boolean active;
        boolean latched;
        boolean lastMatched;
        long activeSince;
        long inactiveSince;
        long matchSince;
        long unmatchedSince;
        long expiresAt;
        long cooldownUntil;

        void activate(long now, long expiry) {
            if (!active) activeSince = now;
            active = true;
            inactiveSince = 0;
            unmatchedSince = 0;
            expiresAt = expiry;
        }

        void deactivate(long now, long cooldown) {
            if (active) inactiveSince = now;
            active = false;
            activeSince = 0;
            unmatchedSince = 0;
            expiresAt = 0;
            cooldownUntil = safeAdd(now, cooldown);
        }
    }
}
