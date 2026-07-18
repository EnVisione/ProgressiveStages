package com.enviouse.progressivestages.common.api.structure;

import com.enviouse.progressivestages.common.api.StageId;

import java.util.Optional;

public record StructureAccessDecision(Result result, Reason reason, Optional<StageId> displayStage,
                                      Optional<StructureSessionId> sessionId,
                                      Optional<StructureBounds> bounds) {
    public enum Result {
        PASS,
        PERMIT,
        DENY
    }

    public enum Reason {
        NONE,
        NOT_CLAIMED,
        ASSIGNMENT_REQUIRED,
        WRONG_OWNER,
        WRONG_INSTANCE,
        UNAVAILABLE,
        MISSING_ACCESS_STAGE,
        PROVIDER_DENIED,
        PROVIDER_ERROR,
        STATIC_STAGE_REQUIRED,
        SESSION_CLOSED
    }

    public StructureAccessDecision {
        result = result == null ? Result.PASS : result;
        reason = reason == null ? Reason.NONE : reason;
        displayStage = displayStage == null ? Optional.empty() : displayStage;
        sessionId = sessionId == null ? Optional.empty() : sessionId;
        bounds = bounds == null ? Optional.empty() : bounds;
    }

    public static StructureAccessDecision pass() {
        return new StructureAccessDecision(Result.PASS, Reason.NONE, Optional.empty(),
            Optional.empty(), Optional.empty());
    }

    public static StructureAccessDecision permit(StructureSessionId sessionId, StructureBounds bounds) {
        return new StructureAccessDecision(Result.PERMIT, Reason.NONE, Optional.empty(),
            Optional.ofNullable(sessionId), Optional.ofNullable(bounds));
    }

    public static StructureAccessDecision deny(Reason reason, StageId stage,
                                               StructureSessionId sessionId, StructureBounds bounds) {
        return new StructureAccessDecision(Result.DENY, reason, Optional.ofNullable(stage),
            Optional.ofNullable(sessionId), Optional.ofNullable(bounds));
    }
}
