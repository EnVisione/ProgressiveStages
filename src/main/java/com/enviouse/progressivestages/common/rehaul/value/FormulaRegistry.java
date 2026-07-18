package com.enviouse.progressivestages.common.rehaul.value;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class FormulaRegistry {

    private volatile Map<String, CompiledFormula> formulas = Map.of();

    public synchronized void rebuild(Map<String, String> sources) {
        FormulaCompiler compiler = new FormulaCompiler();
        Map<String, CompiledFormula> compiled = new LinkedHashMap<>();
        sources.forEach((id, expression) -> {
            if (compiled.putIfAbsent(id, compiler.compile(expression)) != null) {
                throw new IllegalArgumentException("Duplicate formula. " + id);
            }
        });
        for (String id : compiled.keySet()) detectCycle(id, compiled, new LinkedHashSet<>(), new LinkedHashSet<>());
        formulas = Map.copyOf(compiled);
    }

    public double evaluate(String id, Map<String, Double> variables) {
        return evaluate(id, variables == null ? Map.of() : variables, new LinkedHashSet<>());
    }

    public Map<String, CompiledFormula> formulas() { return formulas; }

    private double evaluate(String id, Map<String, Double> values, Set<String> visiting) {
        CompiledFormula formula = formulas.get(id);
        if (formula == null) return values.getOrDefault(id, 0.0);
        if (!visiting.add(id)) throw new IllegalStateException("Formula cycle reached during evaluation. " + id);
        Map<String, Double> resolved = new LinkedHashMap<>(values);
        for (String dependency : formula.dependencies()) {
            if (formulas.containsKey(dependency)) resolved.put(dependency, evaluate(dependency, values, visiting));
        }
        visiting.remove(id);
        return formula.evaluate(resolved);
    }

    private static void detectCycle(String id, Map<String, CompiledFormula> formulas,
                                    Set<String> visiting, Set<String> visited) {
        if (visiting.contains(id)) throw new IllegalArgumentException("Formula dependency cycle. " + visiting + ". " + id);
        if (!visited.add(id)) return;
        visiting.add(id);
        for (String dependency : formulas.get(id).dependencies()) {
            if (formulas.containsKey(dependency)) detectCycle(dependency, formulas, visiting, visited);
        }
        visiting.remove(id);
    }
}
