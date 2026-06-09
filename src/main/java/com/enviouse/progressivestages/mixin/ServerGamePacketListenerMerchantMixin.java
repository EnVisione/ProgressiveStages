package com.enviouse.progressivestages.mixin;

import com.enviouse.progressivestages.server.enforcement.TradeEnforcer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.trading.MerchantOffers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Reconciles the client-side trade index with the server's unfiltered offer list.
 *
 * <p>{@code ServerPlayerMerchantMixin} hides locked offers by sending the client a re-indexed
 * copy of the offer list, but the server's {@link MerchantMenu} keeps the villager's FULL,
 * unfiltered list. {@code handleSelectTrade} then feeds the CLIENT index straight into
 * {@code setSelectionHint}/{@code tryMoveItems}, which index into the unfiltered list — so once
 * a locked offer is hidden, every visible offer after it selects the wrong server-side offer
 * (often the locked one, or an off-by-one neighbour).
 *
 * <p>This translates the incoming client (filtered) index back to the real index in the
 * villager's full list using the SAME {@link TradeEnforcer} filter, so the server selects
 * exactly the offer the player clicked. {@code MerchantContainerMixin} remains the authoritative
 * block for anything that still resolves to a locked offer (e.g. a tampered client sending an
 * out-of-range index).
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerMerchantMixin {

    @Shadow public ServerPlayer player;

    @ModifyVariable(method = "handleSelectTrade", at = @At("STORE"), ordinal = 0, require = 1)
    private int progressivestages$translateTradeIndex(int clientIndex) {
        ServerPlayer p = this.player;
        if (p == null || clientIndex < 0) return clientIndex;
        if (!(p.containerMenu instanceof MerchantMenu menu)) return clientIndex;

        MerchantOffers offers = menu.getOffers();
        if (offers == null || offers.isEmpty()) return clientIndex;

        // Walk the full list, skipping the offers the client never saw (same filter as the hide
        // mixin); the clientIndex-th remaining offer is the one actually clicked.
        int visible = -1;
        for (int idx = 0; idx < offers.size(); idx++) {
            if (TradeEnforcer.isOfferLocked(p, offers.get(idx))) continue;
            visible++;
            if (visible == clientIndex) return idx;
        }
        // Index out of visible range (nothing filtered, or a tampered client): leave untouched —
        // MerchantContainerMixin will reject it if it lands on a locked offer.
        return clientIndex;
    }
}
