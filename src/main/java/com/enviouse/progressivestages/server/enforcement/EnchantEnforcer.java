package com.enviouse.progressivestages.server.enforcement;

import com.enviouse.progressivestages.common.config.StageConfig;
import com.enviouse.progressivestages.common.lock.LockRegistry;
import com.mojang.logging.LogUtils;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.slf4j.Logger;

/**
 * Enforces {@code [enchants] locked = [...]}.
 *
 * <p>Two entry points:
 * <ul>
 *   <li>{@link #stripLockedEnchants(ServerPlayer, ItemStack)} — called from
 *       InventoryScanner during the periodic inventory scan. Rewrites the
 *       {@link DataComponents#ENCHANTMENTS} component, dropping any enchantment
 *       the player's team hasn't unlocked.</li>
 *   <li>{@link #anyEnchantLocked(ServerPlayer, ItemStack)} — called from the
 *       AnvilUpdateEvent handler to short-circuit locked-enchantment applications.</li>
 * </ul>
 */
public final class EnchantEnforcer {

    private static final Logger LOGGER = LogUtils.getLogger();

    private EnchantEnforcer() {}

    /**
     * Strip any locked enchantments from {@code stack} in place. Returns {@code true}
     * if the stack was modified (so the caller can notify / resync).
     */
    public static boolean stripLockedEnchants(ServerPlayer player, ItemStack stack) {
        if (!StageConfig.isBlockEnchants()) return false;
        if (StageConfig.isAllowCreativeBypass() && player.isCreative()) return false;
        if (stack.isEmpty()) return false;

        ItemEnchantments current = stack.get(DataComponents.ENCHANTMENTS);
        if (current == null || current.isEmpty()) {
            // Stored enchantments (enchanted books) live on a different component.
            return stripFromBook(player, stack);
        }

        ItemEnchantments.Mutable mutable = applyEnchantPolicy(player, current);
        if (mutable == null) return false;
        stack.set(DataComponents.ENCHANTMENTS, mutable.toImmutable());
        return true;
    }

    private static boolean stripFromBook(ServerPlayer player, ItemStack stack) {
        ItemEnchantments stored = stack.get(DataComponents.STORED_ENCHANTMENTS);
        if (stored == null || stored.isEmpty()) return false;

        ItemEnchantments.Mutable mutable = applyEnchantPolicy(player, stored);
        if (mutable == null) return false;
        stack.set(DataComponents.STORED_ENCHANTMENTS, mutable.toImmutable());
        return true;
    }

    /**
     * Remove locked enchants and CAP the rest to their effective {@code max_levels} (v3.0). Returns a
     * mutated copy, or {@code null} if nothing changed.
     */
    private static ItemEnchantments.Mutable applyEnchantPolicy(ServerPlayer player, ItemEnchantments current) {
        boolean capsActive = LockRegistry.getInstance().hasEnchantCaps();
        ItemEnchantments.Mutable mutable = null;
        for (Holder<Enchantment> holder : current.keySet()) {
            if (isHolderLockedForPlayer(player, holder)) {
                if (mutable == null) mutable = new ItemEnchantments.Mutable(current);
                mutable.removeIf(h -> h.equals(holder));
            } else if (capsActive) {
                ResourceLocation eid = holder.unwrapKey().map(k -> k.location()).orElse(null);
                int cap = LockRegistry.getInstance().effectiveEnchantCap(player, eid);
                if (cap != Integer.MAX_VALUE && current.getLevel(holder) > cap) {
                    if (mutable == null) mutable = new ItemEnchantments.Mutable(current);
                    mutable.set(holder, Math.max(0, cap)); // level 0 removes the enchant
                }
            }
        }
        return mutable;
    }

    /**
     * Returns {@code true} if the stack carries at least one enchantment the player
     * has not unlocked. Used by the anvil hook to block locked-book applications.
     */
    public static boolean anyEnchantLocked(ServerPlayer player, ItemStack stack) {
        if (!StageConfig.isBlockEnchants()) return false;
        if (StageConfig.isAllowCreativeBypass() && player.isCreative()) return false;
        if (stack.isEmpty()) return false;

        ItemEnchantments active = stack.get(DataComponents.ENCHANTMENTS);
        if (active != null && !active.isEmpty()) {
            for (Holder<Enchantment> holder : active.keySet()) {
                if (isHolderLockedForPlayer(player, holder)) return true;
            }
        }
        ItemEnchantments stored = stack.get(DataComponents.STORED_ENCHANTMENTS);
        if (stored != null && !stored.isEmpty()) {
            for (Holder<Enchantment> holder : stored.keySet()) {
                if (isHolderLockedForPlayer(player, holder)) return true;
            }
        }
        return false;
    }

    private static boolean isHolderLockedForPlayer(ServerPlayer player, Holder<Enchantment> holder) {
        ResourceLocation enchantId = holder.unwrapKey().map(k -> k.location()).orElse(null);
        if (enchantId == null) return false;
        // v2.0: multi-stage — locked when player is missing ANY gating stage for this enchant.
        return LockRegistry.getInstance().isEnchantmentBlockedFor(player, enchantId, holder);
    }
}
