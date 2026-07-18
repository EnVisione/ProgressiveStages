package com.enviouse.progressivestages.common.api.structure;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;

import java.util.Objects;
import java.util.UUID;

public abstract class StructureSessionEvent extends Event {
    private final ServerPlayer player;
    private final ResourceLocation providerId;
    private final StructureSessionView session;
    private final UUID effectiveOwner;
    private final long visitSequence;
    private final boolean stageGranted;
    private final boolean stageRevoked;

    protected StructureSessionEvent(ServerPlayer player, ResourceLocation providerId,
                                    StructureSessionView session, UUID effectiveOwner,
                                    long visitSequence, boolean stageGranted, boolean stageRevoked) {
        this.player = Objects.requireNonNull(player, "player");
        this.providerId = Objects.requireNonNull(providerId, "providerId");
        this.session = Objects.requireNonNull(session, "session");
        this.effectiveOwner = Objects.requireNonNull(effectiveOwner, "effectiveOwner");
        this.visitSequence = visitSequence;
        this.stageGranted = stageGranted;
        this.stageRevoked = stageRevoked;
    }

    public ServerPlayer getPlayer() {
        return player;
    }

    public UUID getPlayerId() {
        return player.getUUID();
    }

    public ResourceLocation getProviderId() {
        return providerId;
    }

    public StructureSessionView getSession() {
        return session;
    }

    public UUID getEffectiveOwner() {
        return effectiveOwner;
    }

    public long getVisitSequence() {
        return visitSequence;
    }

    public boolean wasStageGranted() {
        return stageGranted;
    }

    public boolean wasStageRevoked() {
        return stageRevoked;
    }
}
