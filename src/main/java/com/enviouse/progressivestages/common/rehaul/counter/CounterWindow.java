package com.enviouse.progressivestages.common.rehaul.counter;

public record CounterWindow(WindowKind kind, long durationMillis, String sessionId,
                            ResetPolicy resetPolicy, boolean pauseOffline) {

    public CounterWindow {
        kind = kind == null ? WindowKind.LIFETIME : kind;
        if (durationMillis < 0) throw new IllegalArgumentException("Counter window duration cannot be negative");
        sessionId = sessionId == null ? "" : sessionId;
        resetPolicy = resetPolicy == null ? ResetPolicy.NEVER : resetPolicy;
        if (kind == WindowKind.ROLLING_DURATION && durationMillis < 1) {
            throw new IllegalArgumentException("A rolling counter window requires a duration");
        }
    }

    public static CounterWindow lifetime() {
        return new CounterWindow(WindowKind.LIFETIME, 0, "", ResetPolicy.NEVER, false);
    }
}
