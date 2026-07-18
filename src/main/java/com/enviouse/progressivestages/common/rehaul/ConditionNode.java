package com.enviouse.progressivestages.common.rehaul;

import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public sealed interface ConditionNode permits ConditionNode.Constant, ConditionNode.Leaf,
        ConditionNode.All, ConditionNode.Any, ConditionNode.Not, ConditionNode.AtLeast,
        ConditionNode.Exactly, ConditionNode.Sequence, ConditionNode.Comparison,
        ConditionNode.Reference {

    record Constant(boolean value) implements ConditionNode {}

    record Leaf(ResourceLocation providerId, Map<String, Object> arguments) implements ConditionNode {
        public Leaf {
            Objects.requireNonNull(providerId, "providerId");
            arguments = arguments == null ? Map.of() : Map.copyOf(arguments);
        }
    }

    record All(List<ConditionNode> children) implements ConditionNode {
        public All {
            children = immutableChildren(children);
        }
    }

    record Any(List<ConditionNode> children) implements ConditionNode {
        public Any {
            children = immutableChildren(children);
        }
    }

    record Not(ConditionNode child) implements ConditionNode {
        public Not {
            Objects.requireNonNull(child, "child");
        }
    }

    record AtLeast(int count, List<ConditionNode> children) implements ConditionNode {
        public AtLeast {
            children = immutableChildren(children);
            if (count < 1 || count > children.size()) throw new IllegalArgumentException("Invalid condition count");
        }
    }

    record Exactly(int count, List<ConditionNode> children) implements ConditionNode {
        public Exactly {
            children = immutableChildren(children);
            if (count < 0 || count > children.size()) throw new IllegalArgumentException("Invalid condition count");
        }
    }

    record Sequence(List<ConditionNode> children, long timeoutMillis, boolean resetOnFailure) implements ConditionNode {
        public Sequence {
            children = immutableChildren(children);
            if (timeoutMillis < 0) throw new IllegalArgumentException("Sequence timeout cannot be negative");
        }
    }

    record Comparison(ResourceLocation valueProviderId, Operator operator, Object expected,
                      Map<String, Object> arguments) implements ConditionNode {
        public enum Operator {
            EQUAL,
            NOT_EQUAL,
            GREATER,
            GREATER_OR_EQUAL,
            LESS,
            LESS_OR_EQUAL,
            CONTAINS,
            MATCHES
        }

        public Comparison {
            Objects.requireNonNull(valueProviderId, "valueProviderId");
            Objects.requireNonNull(operator, "operator");
            arguments = arguments == null ? Map.of() : Map.copyOf(arguments);
        }
    }

    record Reference(ResourceLocation id) implements ConditionNode {
        public Reference {
            Objects.requireNonNull(id, "id");
        }
    }

    private static List<ConditionNode> immutableChildren(List<ConditionNode> children) {
        List<ConditionNode> copy = children == null ? List.of() : List.copyOf(children);
        if (copy.isEmpty()) throw new IllegalArgumentException("Condition group cannot be empty");
        return copy;
    }
}
