package com.enviouse.progressivestages.mixin;

import com.enviouse.progressivestages.server.enforcement.TradeEnforcer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MerchantContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Server-side anti-cheat block for locked villager / wandering-trader trades.
 *
 * <p>{@code ServerPlayerMerchantMixin} hides locked offers from the client, but that alone
 * is exploitable: a tampered client can still send a {@code ServerboundSelectTradePacket}
 * for an offer the server never filtered out (the server's {@link MerchantContainer} keeps
 * the villager's full, unfiltered offer list). The authoritative result is computed here in
 * {@link MerchantContainer#updateSellItem()} — it picks the active offer and fills the result
 * slot (index 2). By clearing that result (and the active offer) whenever the selected offer
 * is locked for the trading player, no client — vanilla or modified — can take the item:
 * {@code MerchantResultSlot.onTake} reads the (now null) active offer and the (now empty)
 * result slot, so the trade can never commit.
 *
 * <p>Only enforced server-side ({@code !merchant.isClientSide()}); the client container is
 * already constrained to the filtered offer packet.
 */
@Mixin(MerchantContainer.class)
public abstract class MerchantContainerMixin {

    @Shadow private MerchantOffer activeOffer;
    @Shadow @Final private Merchant merchant;

    @Shadow public abstract void setItem(int index, ItemStack stack);

    @Inject(method = "updateSellItem", at = @At("TAIL"), require = 1)
    private void progressivestages$gateLockedTradeResult(CallbackInfo ci) {
        MerchantOffer offer = this.activeOffer;
        if (offer == null) return;
        if (this.merchant.isClientSide()) return;
        if (!(this.merchant.getTradingPlayer() instanceof ServerPlayer player)) return;

        if (TradeEnforcer.isOfferLocked(player, offer)) {
            this.activeOffer = null;
            // Slot 2 is the result slot; setItem on a non-payment slot does not re-trigger updateSellItem.
            this.setItem(2, ItemStack.EMPTY);
            TradeEnforcer.notifyBlocked(player, offer);
        }
    }
}
