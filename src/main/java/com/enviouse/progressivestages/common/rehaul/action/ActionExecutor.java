package com.enviouse.progressivestages.common.rehaul.action;

import java.util.ArrayList;
import java.util.List;

public final class ActionExecutor {

    private final ActionRegistry registry;

    public ActionExecutor(ActionRegistry registry) {
        this.registry = registry;
    }

    public ActionExecution execute(ActionChain chain, ActionContext context) {
        if (chain == null || chain.actions().isEmpty()) return new ActionExecution(true, false, List.of(), "No actions were required");
        Object subjectSnapshot = chain.atomic() ? context.subject().snapshot() : null;
        List<Completed> completed = new ArrayList<>();
        List<ActionResult> results = new ArrayList<>();
        for (CompiledAction action : chain.actions()) {
            ActionProvider provider = registry.find(action.providerId()).orElse(null);
            if (provider == null) {
                ActionResult missing = ActionResult.failure("missing_provider", "Action provider is unavailable. " + action.providerId());
                results.add(missing);
                if (action.failurePolicy() == FailurePolicy.CONTINUE) continue;
                return fail(chain, context, subjectSnapshot, completed, results, action, missing);
            }
            provider.validate(action.arguments());
            ActionResult result = executeWithRetries(provider, action, context);
            results.add(result);
            if (result.success()) {
                completed.add(new Completed(provider, action, result.compensationToken()));
                continue;
            }
            if (action.failurePolicy() == FailurePolicy.CONTINUE) continue;
            return fail(chain, context, subjectSnapshot, completed, results, action, result);
        }
        return new ActionExecution(results.stream().allMatch(ActionResult::success), false, results,
            "Action chain completed");
    }

    private static ActionResult executeWithRetries(ActionProvider provider, CompiledAction action,
                                                   ActionContext context) {
        ActionResult result = provider.execute(action, context);
        int attempt = 0;
        while (!result.success() && action.failurePolicy() == FailurePolicy.RETRY && attempt++ < action.retries()) {
            result = provider.execute(action, context);
        }
        return result;
    }

    private static ActionExecution fail(ActionChain chain, ActionContext context, Object snapshot,
                                        List<Completed> completed, List<ActionResult> results,
                                        CompiledAction failedAction, ActionResult failure) {
        boolean rollback = chain.atomic() || failedAction.failurePolicy() == FailurePolicy.ROLLBACK;
        if (rollback && snapshot != null) {
            context.subject().restore(snapshot);
            return new ActionExecution(false, true, results, failure.explanation());
        }
        if (failedAction.failurePolicy() == FailurePolicy.COMPENSATE) {
            for (int index = completed.size() - 1; index >= 0; index--) {
                Completed done = completed.get(index);
                if (done.provider().supportsCompensation()) {
                    results.add(done.provider().compensate(done.action(), context, done.token()));
                }
            }
            return new ActionExecution(false, true, results, failure.explanation());
        }
        return new ActionExecution(false, false, results, failure.explanation());
    }

    private record Completed(ActionProvider provider, CompiledAction action, Object token) {}
}
