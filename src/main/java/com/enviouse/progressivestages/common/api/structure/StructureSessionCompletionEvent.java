package com.enviouse.progressivestages.common.api.structure;

import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public final class StructureSessionCompletionEvent extends StructureSessionEvent {
    public StructureSessionCompletionEvent(ServerPlayer player, StructureSessionView session,
                                           UUID effectiveOwner) {
        super(player, session.providerId(), session, effectiveOwner, session.visitSequence(),
            false, false);
    }
}
