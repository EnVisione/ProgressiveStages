package com.enviouse.progressivestages.common.rehaul.lifecycle;

import com.enviouse.progressivestages.common.rehaul.RuleLifetime;

public record ActivationPolicy(RuleLifetime lifetime, long durationMillis, long cooldownMillis,
                               long debounceMillis, long graceMillis, long minimumActiveMillis,
                               long minimumInactiveMillis, boolean refreshDuration,
                               boolean pauseOffline, String sessionKey) {

    public ActivationPolicy {
        lifetime = lifetime == null ? RuleLifetime.PERMANENT : lifetime;
        if (durationMillis < 0 || cooldownMillis < 0 || debounceMillis < 0 || graceMillis < 0
                || minimumActiveMillis < 0 || minimumInactiveMillis < 0) {
            throw new IllegalArgumentException("Activation timing cannot be negative");
        }
        sessionKey = sessionKey == null ? "" : sessionKey;
    }

    public static ActivationPolicy live() {
        return new ActivationPolicy(RuleLifetime.LIVE, 0, 0, 0, 0, 0, 0, true, false, "");
    }
}
