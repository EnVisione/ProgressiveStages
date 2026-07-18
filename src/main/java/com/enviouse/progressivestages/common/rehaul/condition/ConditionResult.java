package com.enviouse.progressivestages.common.rehaul.condition;

public record ConditionResult(boolean matched, double current, double required, String explanation) {

    public ConditionResult {
        explanation = explanation == null ? "" : explanation;
        if (!Double.isFinite(current) || !Double.isFinite(required)) {
            throw new IllegalArgumentException("Condition progress must be finite");
        }
    }

    public static ConditionResult matched(String explanation) {
        return new ConditionResult(true, 1, 1, explanation);
    }

    public static ConditionResult failed(String explanation) {
        return new ConditionResult(false, 0, 1, explanation);
    }
}
