package com.enviouse.progressivestages.common.rehaul.lifecycle;

import com.enviouse.progressivestages.common.rehaul.condition.ConditionTrace;

public record ActivationDecision(boolean active, long activeSince, long expiresAt,
                                 long cooldownUntil, ConditionTrace conditionTrace,
                                 String explanation) {

    public ActivationDecision {
        explanation = explanation == null ? "" : explanation;
    }
}
