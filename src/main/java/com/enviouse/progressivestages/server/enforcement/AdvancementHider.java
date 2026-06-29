package com.enviouse.progressivestages.server.enforcement;

import com.enviouse.progressivestages.common.lock.LockRegistry;
import net.minecraft.server.level.ServerPlayer;

/**
 * v2.5: drives advancement HIDING via {@code [advancements].locked}.
 *
 * <p>The actual filtering happens in {@code ServerAdvancementHidingMixin}, which strips gated
 * advancements (whose stage the player lacks) from every {@code ClientboundUpdateAdvancementsPacket}
 * before it reaches the client — so they never appear in the advancements screen. This helper just
 * forces a fresh full re-send when a player's stages change, so advancements that became reachable
 * pop into view (and revoked ones disappear) without a relog. No-op unless some stage gates an
 * advancement.
 */
public final class AdvancementHider {

    private AdvancementHider() {}

    public static void resyncIfNeeded(ServerPlayer player) {
        if (player == null || player.server == null) return;
        if (!LockRegistry.getInstance().hasAdvancementLocks()) return;
        // reload() clears in-memory progress and re-reads it from disk, so we save() FIRST to flush
        // any progress earned since the last periodic save — otherwise the reload would discard it.
        // The reload then re-sends the player's whole advancement state, which the mixin re-filters
        // against the now-current stages. Stage changes are infrequent, so this is fine.
        var advancements = player.getAdvancements();
        advancements.save();
        advancements.reload(player.server.getAdvancements());
    }
}
