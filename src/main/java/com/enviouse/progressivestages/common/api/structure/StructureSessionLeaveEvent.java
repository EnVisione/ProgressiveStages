package com.enviouse.progressivestages.common.api.structure;

import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;
import java.util.UUID;

public final class StructureSessionLeaveEvent extends StructureSessionEvent {
    private final StructureLeaveOutcome outcome;

    public StructureSessionLeaveEvent(ServerPlayer player, StructureSessionView session,
                                      UUID effectiveOwner, StructureLeaveOutcome outcome,
                                      boolean stageRevoked) {
        super(player, session.providerId(), session, effectiveOwner, session.visitSequence(),
            false, stageRevoked);
        this.outcome = Objects.requireNonNull(outcome, "outcome");
    }

    public StructureLeaveOutcome getOutcome() {
        return outcome;
    }
}
