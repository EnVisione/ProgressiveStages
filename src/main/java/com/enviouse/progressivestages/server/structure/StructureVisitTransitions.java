package com.enviouse.progressivestages.server.structure;

public final class StructureVisitTransitions {
    public enum Type {
        NONE,
        ENTER,
        CANCEL_EXIT,
        BEGIN_EXIT,
        LEAVE
    }

    public record Decision(Type type, long exitDeadline) {}

    private StructureVisitTransitions() {}

    public static Decision decide(boolean participating, boolean inside, boolean withinExitBounds,
                                  long exitDeadline, long now, long debounceTicks) {
        if (inside) {
            return new Decision(!participating ? Type.ENTER
                : exitDeadline >= 0L ? Type.CANCEL_EXIT : Type.NONE, -1L);
        }
        if (!participating) return new Decision(Type.NONE, -1L);
        if (withinExitBounds) {
            return new Decision(exitDeadline >= 0L ? Type.CANCEL_EXIT : Type.NONE, -1L);
        }
        if (exitDeadline < 0L) {
            return new Decision(Type.BEGIN_EXIT, now + Math.max(0L, debounceTicks));
        }
        if (now >= exitDeadline) return new Decision(Type.LEAVE, exitDeadline);
        return new Decision(Type.NONE, exitDeadline);
    }
}
