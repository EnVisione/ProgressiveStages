package com.enviouse.progressivestages.common.rehaul.value;

import java.util.Map;
import java.util.Set;

public record CompiledFormula(String expression, Set<String> dependencies, FormulaNode root) {

    public CompiledFormula {
        expression = expression == null ? "" : expression;
        dependencies = dependencies == null ? Set.of() : Set.copyOf(dependencies);
    }

    public double evaluate(Map<String, Double> values) {
        double result = root.evaluate(values == null ? Map.of() : values);
        if (!Double.isFinite(result)) throw new IllegalArgumentException("Formula produced a non finite value");
        return result;
    }

    public interface FormulaNode {
        double evaluate(Map<String, Double> values);
    }
}
