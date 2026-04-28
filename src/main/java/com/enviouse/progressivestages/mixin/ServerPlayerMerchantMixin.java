package com.enviouse.progressivestages.mixin;

import com.enviouse.progressivestages.common.config.StageConfig;
import com.enviouse.progressivestages.common.lock.LockRegistry;
import com.enviouse.progressivestages.server.enforcement.EnchantEnforcer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Server-side filter for villager / wandering-trader offers.
 *
 * <p>Targets the {@code ClientboundMerchantOffersPacket} constructor call inside
 * {@link ServerPlayer#sendMerchantOffers}. By the time this mixin fires, the vanilla
 * villager (or wandering trader) has already assembled its full offer list and the
 * server is about to packetize it for the client. We substitute a filtered copy so
 * the player never sees — and therefore can't select — trades whose result is a
 * locked item or carries a locked enchantment.
 *
 * <p>Filtering here instead of on the client means a tampered client can't bypass
 * the gate, and filtering at the packet boundary (rather than mutating the
 * villager's {@code offers} field) preserves the villager's full trade list for
 * the next player or the next stage unlock.
 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMerchantMixin {

    @ModifyArg(
        method = "sendMerchantOffers",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/network/protocol/game/ClientboundMerchantOffersPacket;<init>(ILnet/minecraft/world/item/trading/MerchantOffers;IIZZ)V"
        ),
        index = 1,
        require = 1
    )
    private MerchantOffers progressivestages$filterOffers(MerchantOffers original) {
        if (original == null || original.isEmpty()) return original;

        ServerPlayer self = (ServerPlayer) (Object) this;
        if (StageConfig.isAllowCreativeBypass() && self.isCreative()) return original;

        MerchantOffers filtered = null;
        for (int i = 0; i < original.size(); i++) {
            MerchantOffer offer = original.get(i);
            if (isOfferLocked(self, offer)) {
                if (filtered == null) {
                    filtered = new MerchantOffers();
                    for (int j = 0; j < i; j++) filtered.add(original.get(j));
                }
            } else if (filtered != null) {
                filtered.add(offer);
            }
        }
        return filtered == null ? original : filtered;
    }

    private static boolean isOfferLocked(ServerPlayer player, MerchantOffer offer) {
        if (offer == null) return false;
        ItemStack result = offer.getResult();
        if (result.isEmpty()) return false;

        // v2.0: multi-stage — blocked when ANY gating stage is missing for the result item.
        if (LockRegistry.getInstance().isItemBlockedFor(player, result.getItem())) {
            return true;
        }
        return EnchantEnforcer.anyEnchantLocked(player, result);
    }
}
