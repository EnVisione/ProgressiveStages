package com.enviouse.progressivestages.common.api.structure;

import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public final class StructureSessionEnterEvent extends StructureSessionEvent {
    public StructureSessionEnterEvent(ServerPlayer player, StructureSessionView session,
                                      UUID effectiveOwner, boolean stageGranted) {
        super(player, session.providerId(), session, effectiveOwner, session.visitSequence(),
            stageGranted, false);
    }
}
