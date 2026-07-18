package com.enviouse.progressivestages.common.rehaul.decision;

public final class PriorityCascade {

    public static final int ABSOLUTE_MINIMUM = -1_000_000;
    public static final int ABSOLUTE_MAXIMUM = 1_000_000;

    private PriorityCascade() {}

    public static ResolvedPriority resolve(Integer entry, Integer rule, Integer category,
                                           Integer stage, int global) {
        if (entry != null) return checked(entry, PrioritySource.ENTRY);
        if (rule != null) return checked(rule, PrioritySource.RULE);
        if (category != null) return checked(category, PrioritySource.CATEGORY);
        if (stage != null) return checked(stage, PrioritySource.STAGE);
        return checked(global, PrioritySource.GLOBAL);
    }

    private static ResolvedPriority checked(int value, PrioritySource source) {
        if (value < ABSOLUTE_MINIMUM || value > ABSOLUTE_MAXIMUM) {
            throw new IllegalArgumentException("Priority is outside the absolute safety range. " + value);
        }
        return new ResolvedPriority(value, source);
    }
}
