package com.enviouse.progressivestages.common.rehaul.condition;

import com.enviouse.progressivestages.common.rehaul.ConditionNode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public final class ConditionEvaluator {

    private final ConditionRegistry registry;
    private final ConditionStateStore stateStore;

    public ConditionEvaluator(ConditionRegistry registry, ConditionStateStore stateStore) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.stateStore = Objects.requireNonNull(stateStore, "stateStore");
    }

    public ConditionTrace evaluate(ConditionNode node, ConditionContext context) {
        return evaluate(node, context, "root");
    }

    private ConditionTrace evaluate(ConditionNode node, ConditionContext context, String path) {
        if (node instanceof ConditionNode.Constant constant) {
            return trace(path, "constant", constant.value(), List.of(), constant.value() ? 1 : 0, 1);
        }
        if (node instanceof ConditionNode.Leaf leaf) {
            ConditionProvider provider = registry.find(leaf.providerId()).orElse(null);
            ConditionResult result;
            if (provider == null) {
                boolean optional = Boolean.TRUE.equals(leaf.arguments().get("optional"));
                result = new ConditionResult(optional, optional ? 1 : 0, 1,
                    optional ? "Optional condition provider is unavailable" : "Condition provider is unavailable");
            } else if (!provider.supportedScopes().contains(context.scope())) {
                result = ConditionResult.failed("Condition does not support this subject scope");
            } else {
                result = provider.evaluate(leaf, context);
            }
            return new ConditionTrace(path, leaf.providerId().toString(), result, List.of());
        }
        if (node instanceof ConditionNode.Reference reference) {
            ConditionNode resolved = context.namedConditions().get(reference.id().toString());
            if (resolved == null) {
                return new ConditionTrace(path, "reference", ConditionResult.failed("Named condition is unavailable"), List.of());
            }
            ConditionTrace child = evaluate(resolved, context, path + ".reference");
            return new ConditionTrace(path, "reference", child.result(), List.of(child));
        }
        if (node instanceof ConditionNode.Not not) {
            ConditionTrace child = evaluate(not.child(), context, path + ".not");
            boolean matched = !child.result().matched();
            return trace(path, "not", matched, List.of(child), matched ? 1 : 0, 1);
        }
        if (node instanceof ConditionNode.All all) return group(all.children(), context, path, "all", all.children().size());
        if (node instanceof ConditionNode.Any any) return group(any.children(), context, path, "any", 1);
        if (node instanceof ConditionNode.AtLeast atLeast) return group(atLeast.children(), context, path, "at_least", atLeast.count());
        if (node instanceof ConditionNode.Exactly exactly) {
            List<ConditionTrace> children = children(exactly.children(), context, path);
            long count = children.stream().filter(child -> child.result().matched()).count();
            return trace(path, "exactly", count == exactly.count(), children, count, exactly.count());
        }
        if (node instanceof ConditionNode.Sequence sequence) return sequence(sequence, context, path);
        if (node instanceof ConditionNode.Comparison comparison) return comparison(comparison, context, path);
        throw new IllegalArgumentException("Unknown condition node. " + node.getClass().getName());
    }

    private ConditionTrace group(List<ConditionNode> nodes, ConditionContext context, String path,
                                 String type, int required) {
        List<ConditionTrace> children = children(nodes, context, path);
        long count = children.stream().filter(child -> child.result().matched()).count();
        return trace(path, type, count >= required, children, count, required);
    }

    private List<ConditionTrace> children(List<ConditionNode> nodes, ConditionContext context, String path) {
        List<ConditionTrace> output = new ArrayList<>();
        for (int index = 0; index < nodes.size(); index++) {
            output.add(evaluate(nodes.get(index), context, path + "." + index));
        }
        return List.copyOf(output);
    }

    private ConditionTrace sequence(ConditionNode.Sequence sequence, ConditionContext context, String path) {
        ConditionStateStore.SequenceState state = stateStore.sequence(context.subjectId(), path);
        if (state.startedAt > 0 && sequence.timeoutMillis() > 0
                && context.nowMillis() - state.startedAt > sequence.timeoutMillis()) {
            state.reset();
        }
        int index = Math.min(state.index, sequence.children().size() - 1);
        ConditionTrace current = evaluate(sequence.children().get(index), context, path + "." + index);
        if (current.result().matched()) {
            if (state.startedAt == 0) state.startedAt = context.nowMillis();
            state.index++;
        } else if (sequence.resetOnFailure() && Boolean.TRUE.equals(context.values().get("sequence_failure." + path))) {
            state.reset();
        }
        boolean complete = state.index >= sequence.children().size();
        if (complete) state.reset();
        return trace(path, "sequence", complete, List.of(current), complete ? sequence.children().size() : state.index,
            sequence.children().size());
    }

    private ConditionTrace comparison(ConditionNode.Comparison comparison, ConditionContext context, String path) {
        Object actual = context.values().get(comparison.valueProviderId().toString());
        if (actual == null) actual = context.values().get(comparison.valueProviderId().getPath());
        boolean matched = compare(actual, comparison.expected(), comparison.operator());
        return trace(path, "comparison", matched, List.of(), matched ? 1 : 0, 1);
    }

    private static boolean compare(Object actual, Object expected, ConditionNode.Comparison.Operator operator) {
        if (operator == ConditionNode.Comparison.Operator.EQUAL) return Objects.equals(normalize(actual), normalize(expected));
        if (operator == ConditionNode.Comparison.Operator.NOT_EQUAL) return !Objects.equals(normalize(actual), normalize(expected));
        if (operator == ConditionNode.Comparison.Operator.CONTAINS) {
            if (actual instanceof Collection<?> collection) return collection.stream().anyMatch(value -> Objects.equals(normalize(value), normalize(expected)));
            return String.valueOf(actual).toLowerCase(Locale.ROOT).contains(String.valueOf(expected).toLowerCase(Locale.ROOT));
        }
        if (operator == ConditionNode.Comparison.Operator.MATCHES) {
            return actual != null && Pattern.matches(String.valueOf(expected), String.valueOf(actual));
        }
        double left = number(actual);
        double right = number(expected);
        return switch (operator) {
            case GREATER -> left > right;
            case GREATER_OR_EQUAL -> left >= right;
            case LESS -> left < right;
            case LESS_OR_EQUAL -> left <= right;
            default -> false;
        };
    }

    private static Object normalize(Object value) {
        return value instanceof String text ? text.toLowerCase(Locale.ROOT) : value;
    }

    private static double number(Object value) {
        if (value instanceof Number number) return number.doubleValue();
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (RuntimeException error) {
            return Double.NaN;
        }
    }

    private static ConditionTrace trace(String path, String type, boolean matched,
                                        List<ConditionTrace> children, double current, double required) {
        String explanation = matched ? "Condition group matched" : "Condition group did not match";
        return new ConditionTrace(path, type, new ConditionResult(matched, current, required, explanation), children);
    }
}
