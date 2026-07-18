package com.enviouse.progressivestages.common.rehaul.challenge;

import com.enviouse.progressivestages.common.rehaul.action.ActionContext;
import com.enviouse.progressivestages.common.rehaul.action.ActionExecution;
import com.enviouse.progressivestages.common.rehaul.action.ActionExecutor;
import com.enviouse.progressivestages.common.rehaul.condition.ConditionContext;
import com.enviouse.progressivestages.common.rehaul.condition.ConditionEvaluator;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class ChallengeEngine {

    private final ConditionEvaluator conditions;
    private final ActionExecutor actions;
    private final ChallengeMeasureRegistry measures;
    private volatile Map<ResourceLocation, CompiledChallenge> definitions = Map.of();
    private final Map<Key, Session> sessions = new ConcurrentHashMap<>();

    public ChallengeEngine(ConditionEvaluator conditions, ActionExecutor actions,
                           ChallengeMeasureRegistry measures) {
        this.conditions = conditions;
        this.actions = actions;
        this.measures = measures;
    }

    public synchronized void rebuild(List<CompiledChallenge> challenges) {
        Map<ResourceLocation, CompiledChallenge> next = new LinkedHashMap<>();
        for (CompiledChallenge challenge : challenges == null ? List.<CompiledChallenge>of() : challenges) {
            if (next.putIfAbsent(challenge.id(), challenge) != null) {
                throw new IllegalArgumentException("Duplicate challenge id. " + challenge.id());
            }
            for (ChallengeBudget budget : challenge.budgets()) {
                ChallengeMeasureProvider provider = measures.find(budget.measure()).orElseThrow(() ->
                    new IllegalArgumentException("Unknown challenge measure. " + budget.measure()));
                provider.validate(budget.filters());
            }
        }
        definitions = Map.copyOf(next);
        sessions.keySet().removeIf(key -> !definitions.containsKey(key.challenge()));
    }

    public ChallengeSessionView reconcile(ResourceLocation challengeId, ConditionContext conditionContext,
                                          ActionContext actionContext) {
        CompiledChallenge challenge = require(challengeId);
        Key key = new Key(actionContext.subject().id(), challengeId);
        Session session = sessions.computeIfAbsent(key, ignored -> new Session());
        long now = conditionContext.nowMillis();
        if ((session.status == ChallengeStatus.FAILED || session.status == ChallengeStatus.EXPIRED)
                && session.attempts <= challenge.retries()
                && conditions.evaluate(challenge.startCondition(), conditionContext).result().matched()) {
            session.start(now);
        }
        if (session.status == ChallengeStatus.INACTIVE
                && conditions.evaluate(challenge.startCondition(), conditionContext).result().matched()) {
            session.start(now);
        }
        if (session.status != ChallengeStatus.ACTIVE) return session.view(key, "Challenge is not active");
        regenerate(session, challenge, now);
        if (challenge.timeoutMillis() > 0 && now - session.startedAt >= challenge.timeoutMillis()) {
            return finish(key, session, challenge, ChallengeStatus.EXPIRED, actionContext, "Challenge time expired");
        }
        if (conditions.evaluate(challenge.endCondition(), conditionContext).result().matched()) {
            return finish(key, session, challenge, ChallengeStatus.FAILED, actionContext, "Challenge end condition matched");
        }
        if (session.currentStep < challenge.steps().size()) {
            ChallengeStep step = challenge.steps().get(session.currentStep);
            if (step.timeoutMillis() > 0 && now - session.stepStartedAt >= step.timeoutMillis()) {
                if (step.resetOnFailure()) session.currentStep = 0;
                else return finish(key, session, challenge, ChallengeStatus.FAILED, actionContext, "Challenge step time expired");
                session.stepStartedAt = now;
            } else if (conditions.evaluate(step.condition(), conditionContext).result().matched()) {
                session.currentStep++;
                session.stepStartedAt = now;
            }
        }
        boolean stepsComplete = session.currentStep >= challenge.steps().size();
        boolean budgetsReady = challenge.budgets().stream().allMatch(budget ->
            budget.successful(session.values.getOrDefault(budget.id(), 0.0)));
        boolean successCondition = conditions.evaluate(challenge.successCondition(), conditionContext).result().matched();
        if (stepsComplete && budgetsReady && successCondition) {
            return finish(key, session, challenge, ChallengeStatus.SUCCEEDED, actionContext, "Challenge succeeded");
        }
        return session.view(key, "Challenge is active");
    }

    public List<ChallengeSessionView> record(ChallengeEvent event, ActionContext actionContext) {
        List<ChallengeSessionView> changed = new ArrayList<>();
        for (Map.Entry<Key, Session> entry : sessions.entrySet()) {
            Key key = entry.getKey();
            Session session = entry.getValue();
            if (!key.subject().equals(event.subject()) || session.status != ChallengeStatus.ACTIVE) continue;
            CompiledChallenge challenge = definitions.get(key.challenge());
            if (challenge == null) continue;
            regenerate(session, challenge, event.timestamp());
            boolean failed = false;
            for (ChallengeBudget budget : challenge.budgets()) {
                ChallengeMeasureProvider provider = measures.find(budget.measure()).orElse(null);
                if (provider == null) continue;
                double amount = provider.amount(event, budget.filters());
                if (amount == 0) continue;
                ResourceLocation pool = budget.sharedPool().isBlank() ? budget.id()
                    : ResourceLocation.fromNamespaceAndPath(challenge.id().getNamespace(),
                        challenge.id().getPath() + "/pool/" + budget.sharedPool());
                double next = session.values.merge(pool, amount, Double::sum);
                if (!pool.equals(budget.id())) session.values.put(budget.id(), next);
                if (budget.failed(next)) failed = true;
            }
            if (failed) changed.add(finish(key, session, challenge, ChallengeStatus.FAILED,
                actionContext, "A challenge budget was exceeded"));
            else changed.add(session.view(key, "Challenge progress changed"));
        }
        return List.copyOf(changed);
    }

    public Optional<ChallengeSessionView> session(String subject, ResourceLocation challenge) {
        Session session = sessions.get(new Key(subject, challenge));
        return session == null ? Optional.empty() : Optional.of(session.view(new Key(subject, challenge), ""));
    }

    public List<ChallengeSessionView> sessions(String subject) {
        return sessions.entrySet().stream().filter(entry -> entry.getKey().subject().equals(subject))
            .map(entry -> entry.getValue().view(entry.getKey(), "")).toList();
    }

    public boolean reset(String subject, ResourceLocation challenge) {
        return sessions.remove(new Key(subject, challenge)) != null;
    }

    public java.util.Set<ResourceLocation> challengeIds() {
        return definitions.keySet();
    }

    public Optional<CompiledChallenge> definition(ResourceLocation challenge) {
        return Optional.ofNullable(definitions.get(challenge));
    }

    public ChallengeSnapshot snapshot() {
        return new ChallengeSnapshot(sessions.entrySet().stream()
            .map(entry -> entry.getValue().view(entry.getKey(), "")).toList());
    }

    public void restore(ChallengeSnapshot snapshot) {
        sessions.clear();
        for (ChallengeSessionView view : snapshot.sessions()) {
            if (!definitions.containsKey(view.challenge())) continue;
            Session state = new Session();
            state.status = view.status();
            state.startedAt = view.startedAt();
            state.endedAt = view.endedAt();
            state.currentStep = view.currentStep();
            state.attempts = view.attempts();
            state.values.putAll(view.budgetValues());
            state.explanation = view.explanation();
            state.lastUpdatedAt = view.startedAt();
            state.stepStartedAt = view.startedAt();
            sessions.put(new Key(view.subject(), view.challenge()), state);
        }
    }

    private ChallengeSessionView finish(Key key, Session session, CompiledChallenge challenge,
                                        ChallengeStatus status, ActionContext context, String explanation) {
        session.status = status;
        session.endedAt = context.nowMillis();
        ActionExecution execution = actions.execute(status == ChallengeStatus.SUCCEEDED
            ? challenge.successActions() : challenge.failureActions(), context);
        if (!execution.success()) explanation += ". " + execution.explanation();
        return session.view(key, explanation);
    }

    private static void regenerate(Session session, CompiledChallenge challenge, long now) {
        if (session.lastUpdatedAt == 0) session.lastUpdatedAt = now;
        double seconds = Math.max(0, now - session.lastUpdatedAt) / 1_000.0;
        if (seconds > 0) {
            for (ChallengeBudget budget : challenge.budgets()) {
                if (budget.regenerationPerSecond() == 0) continue;
                session.values.compute(budget.id(), (ignored, value) -> Math.max(0,
                    (value == null ? 0 : value) - budget.regenerationPerSecond() * seconds));
            }
        }
        session.lastUpdatedAt = now;
    }

    private CompiledChallenge require(ResourceLocation id) {
        CompiledChallenge challenge = definitions.get(id);
        if (challenge == null) throw new IllegalArgumentException("Unknown challenge. " + id);
        return challenge;
    }

    private record Key(String subject, ResourceLocation challenge) {}

    private static final class Session {
        ChallengeStatus status = ChallengeStatus.INACTIVE;
        long startedAt;
        long endedAt;
        long lastUpdatedAt;
        long stepStartedAt;
        int currentStep;
        int attempts;
        String explanation = "";
        final Map<ResourceLocation, Double> values = new ConcurrentHashMap<>();

        void start(long now) {
            status = ChallengeStatus.ACTIVE;
            startedAt = now;
            endedAt = 0;
            lastUpdatedAt = now;
            stepStartedAt = now;
            currentStep = 0;
            attempts++;
            values.clear();
            explanation = "";
        }

        ChallengeSessionView view(Key key, String explanation) {
            if (explanation != null && !explanation.isBlank()) this.explanation = explanation;
            return new ChallengeSessionView(key.subject(), key.challenge(), status, startedAt, endedAt,
                currentStep, attempts, values, this.explanation);
        }
    }
}
