package com.enviouse.progressivestages.mixin;

import com.enviouse.progressivestages.common.lock.LockRegistry;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundUpdateAdvancementsPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * v2.5: advancement HIDING. Strips advancements gated by {@code [advancements].locked} (whose stage
 * the receiving player doesn't own) out of every outgoing {@link ClientboundUpdateAdvancementsPacket},
 * so locked advancements never appear in the client's advancements screen.
 *
 * <p>Server-authoritative: the client is never told the advancement exists, so there's nothing to
 * reveal client-side. When the player gains the gating stage, {@code AdvancementHider.resyncIfNeeded}
 * triggers a fresh full re-send and the now-unlocked advancements appear. Mirrors the send-site
 * {@code @ModifyVariable} technique used by {@code ServerCommonPacketListenerImplMixin} for ore-spoof.
 */
@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class ServerAdvancementHidingMixin {

    @ModifyVariable(
        method = "send(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketSendListener;)V",
        at = @At("HEAD"),
        argsOnly = true
    )
    private Packet<?> progressivestages$filterAdvancements(Packet<?> packet) {
        try {
            if (!(packet instanceof ClientboundUpdateAdvancementsPacket adv)) return packet;
            LockRegistry reg = LockRegistry.getInstance();
            if (!reg.hasAdvancementLocks()) return packet;
            ServerPlayer player = progressivestages$advPlayer();
            if (player == null) return packet;

            boolean changed = false;

            List<AdvancementHolder> keptAdded = new ArrayList<>(adv.getAdded().size());
            for (AdvancementHolder h : adv.getAdded()) {
                if (reg.isAdvancementHiddenFor(player, h.id())) { changed = true; continue; }
                keptAdded.add(h);
            }

            Map<ResourceLocation, AdvancementProgress> keptProgress = new LinkedHashMap<>();
            for (Map.Entry<ResourceLocation, AdvancementProgress> e : adv.getProgress().entrySet()) {
                if (reg.isAdvancementHiddenFor(player, e.getKey())) { changed = true; continue; }
                keptProgress.put(e.getKey(), e.getValue());
            }

            if (!changed) return packet;
            return new ClientboundUpdateAdvancementsPacket(
                adv.shouldReset(), keptAdded, adv.getRemoved(), keptProgress);
        } catch (Throwable t) {
            org.slf4j.LoggerFactory.getLogger("ProgressiveStages")
                .error("[AdvancementHide] packet filter failed; sending original", t);
            return packet;
        }
    }

    /** The ServerPlayer this listener belongs to, or null for non-game (config) listeners. */
    private ServerPlayer progressivestages$advPlayer() {
        Object self = this;
        if (self instanceof ServerGamePacketListenerImpl gp) {
            return gp.player;
        }
        return null;
    }
}
