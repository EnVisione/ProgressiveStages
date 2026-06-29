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
        // reload() resets + re-sends the player's whole advancement state; the mixin then re-filters
        // it against the player's now-current stages. Stage changes are infrequent, so this is fine.
        player.getAdvancements().reload(player.server.getAdvancements());
    }
}
