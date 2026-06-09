package com.enviouse.progressivestages.server.enforcement;

import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.config.StageConfig;
import com.enviouse.progressivestages.common.lock.LockRegistry;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.trading.MerchantOffer;

import java.util.List;
import java.util.Optional;

/**
 * Enforces {@code [trades] locked = [...]} — villager / wandering-trader trade gating.
 *
 * <p>A trade is blocked when its <em>result</em> item is gated. This shares one predicate
 * between the two enforcement hooks so the visible offer list and the server-side
 * transaction can never diverge:
 * <ul>
 *   <li>{@code ServerPlayerMerchantMixin} filters locked offers out of the
 *       {@code ClientboundMerchantOffersPacket} so legitimate players never see them.</li>
 *   <li>{@code MerchantContainerMixin} clears the result slot server-side when a locked
 *       offer is selected, so a tampered/desynced client still can't complete it.</li>
 * </ul>
 *
 * <p>A result is considered locked if its item is in this stage's {@code [trades]} list,
 * OR (preserving prior behavior) the item is {@code [items]}-locked, OR the result carries
 * an {@code [enchants]}-locked enchantment (NBT-aware — covers enchanted-book trades via
 * {@link EnchantEnforcer#anyEnchantLocked}). Creative and spectator players bypass.
 */
public final class TradeEnforcer {

    private TradeEnforcer() {}

    /**
     * @return {@code true} if {@code player} must not see or complete this offer.
     */
    public static boolean isOfferLocked(ServerPlayer player, MerchantOffer offer) {
        if (player == null || offer == null) return false;
        if (!StageConfig.isBlockTrades()) return false;
        // Bypass parity with every other enforcer: creative (when allowed) + spectators.
        if (StageConfig.isAllowCreativeBypass() && player.isCreative()) return false;
        if (player.isSpectator()) return false;

        ItemStack result = offer.getResult();
        if (result.isEmpty()) return false;

        LockRegistry reg = LockRegistry.getInstance();
        Item item = result.getItem();
        // [trades] — the dedicated category for "block the trade, not the item".
        if (reg.isTradeBlockedFor(player, item)) return true;
        // Preserved behavior: a result you can't even hold shouldn't be tradeable.
        if (reg.isItemBlockedFor(player, item)) return true;
        // NBT/enchant-aware (enchanted books, enchanted gear) via the [enchants] category.
        return EnchantEnforcer.anyEnchantLocked(player, result);
    }

    /**
     * Send a cooldown-throttled lock message for a blocked trade. Resolves the gating stage from
     * the [trades] category, then the [items] category, then (for enchanted-book / enchanted-gear
     * trades) the first [enchants]-locked enchantment on the result — so an enchant-only locked
     * trade still tells the player why it was blocked.
     */
    public static void notifyBlocked(ServerPlayer player, MerchantOffer offer) {
        if (player == null || offer == null) return;
        ItemStack result = offer.getResult();
        if (result.isEmpty()) return;

        LockRegistry reg = LockRegistry.getInstance();
        Item item = result.getItem();
        Optional<StageId> stage = reg.primaryRestrictingStageForTrade(player, item);
        if (stage.isEmpty()) stage = reg.primaryRestrictingStage(player, item);
        if (stage.isEmpty()) stage = firstLockedEnchantStage(player, result);
        stage.ifPresent(s ->
            ItemEnforcer.notifyLockedWithCooldown(player, s, StageConfig.getMsgTypeLabelTrade()));
    }

    /** The gating stage of the first [enchants]-locked enchantment on the stack (active or stored). */
    private static Optional<StageId> firstLockedEnchantStage(ServerPlayer player, ItemStack stack) {
        if (!StageConfig.isBlockEnchants()) return Optional.empty();
        LockRegistry reg = LockRegistry.getInstance();
        for (var component : List.of(DataComponents.ENCHANTMENTS, DataComponents.STORED_ENCHANTMENTS)) {
            ItemEnchantments enchants = stack.get(component);
            if (enchants == null || enchants.isEmpty()) continue;
            for (Holder<Enchantment> holder : enchants.keySet()) {
                ResourceLocation id = holder.unwrapKey().map(k -> k.location()).orElse(null);
                if (id == null) continue;
                Optional<StageId> s = reg.primaryRestrictingStageForEnchantment(player, id, holder);
                if (s.isPresent()) return s;
            }
        }
        return Optional.empty();
    }
}
