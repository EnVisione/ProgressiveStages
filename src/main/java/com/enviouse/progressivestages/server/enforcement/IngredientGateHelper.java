package com.enviouse.progressivestages.server.enforcement;

import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.config.StageConfig;
import com.enviouse.progressivestages.common.config.StageDefinition;
import com.enviouse.progressivestages.common.lock.LockRegistry;
import com.enviouse.progressivestages.common.stage.StageOrder;
import com.enviouse.progressivestages.common.util.TextUtil;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Helper for transitive crafting / automated-craft enforcement.
 *
 * <p>Per-stage opt-in toggles ({@code enforcement.block_crafting_with_locked_ingredients}
 * and {@code enforcement.block_automated_crafting}) extend gating to crafting recipes
 * whose ingredients include a locked item, even when the output itself is not locked.
 *
 * <p>Every method here short-circuits to a no-op when no stage has opted in,
 * keeping TPS impact near zero for packs that don't use the feature.
 */
public final class IngredientGateHelper {

    private IngredientGateHelper() {}

    // Cooldown for ingredient-blocked notifications (separate from item lock cooldown)
    private static final java.util.Map<java.util.UUID, java.util.Map<String, Long>> NOTIFY_COOLDOWNS =
        new java.util.HashMap<>();

    /**
     * Test the player against the given container's slots (range [from, toExclusive)) for
     * ingredient-transitive blocking.
     *
     * @return the (stage, item) pair the player is missing, or empty if not blocked
     */
    public static Optional<LockRegistry.IngredientBlockResult> checkContainer(
            ServerPlayer player, Container container, int fromSlot, int toExclusive) {
        if (player == null || container == null) return Optional.empty();
        if (!LockRegistry.getInstance().isIngredientGatingActive()) return Optional.empty();
        if (StageConfig.isAllowCreativeBypass() && player.isCreative()) return Optional.empty();
        if (player.isSpectator()) return Optional.empty();

        Set<Item> distinct = collectDistinct(container, fromSlot, toExclusive);
        if (distinct.isEmpty()) return Optional.empty();
        return LockRegistry.getInstance().firstBlockingIngredientStage(player, distinct);
    }

    /**
     * Convenience overload — whole container.
     */
    public static Optional<LockRegistry.IngredientBlockResult> checkContainer(
            ServerPlayer player, Container container) {
        if (container == null) return Optional.empty();
        return checkContainer(player, container, 0, container.getContainerSize());
    }

    /**
     * Variant for {@link NonNullList} of ItemStacks (used by recipe.getIngredients()
     * paths or by mod-supplied ingredient lists). Skips empty stacks and dedupes.
     */
    public static Optional<LockRegistry.IngredientBlockResult> checkStacks(
            ServerPlayer player, Iterable<ItemStack> stacks) {
        if (player == null || stacks == null) return Optional.empty();
        if (!LockRegistry.getInstance().isIngredientGatingActive()) return Optional.empty();
        if (StageConfig.isAllowCreativeBypass() && player.isCreative()) return Optional.empty();
        if (player.isSpectator()) return Optional.empty();

        Set<Item> distinct = new HashSet<>();
        for (ItemStack s : stacks) {
            if (s != null && !s.isEmpty()) distinct.add(s.getItem());
        }
        if (distinct.isEmpty()) return Optional.empty();
        return LockRegistry.getInstance().firstBlockingIngredientStage(player, distinct);
    }

    /**
     * Notify the player that their craft was blocked by a locked ingredient.
     * Respects show_lock_message and uses a per-item-per-stage cooldown to avoid spam.
     */
    public static void notifyIngredientBlocked(ServerPlayer player,
                                               LockRegistry.IngredientBlockResult res) {
        if (player == null || res == null) return;
        if (!StageConfig.isShowLockMessage()) {
            if (StageConfig.isPlayLockSound()) ItemEnforcer.playLockSound(player);
            return;
        }

        // Cooldown
        long now = System.currentTimeMillis();
        int cd = StageConfig.getNotificationCooldown();
        String itemKey = BuiltInRegistries.ITEM.getKey(res.offendingItem).toString();
        String key = "ingredient:" + res.stage + ":" + itemKey;
        java.util.Map<String, Long> per = NOTIFY_COOLDOWNS.computeIfAbsent(player.getUUID(),
            k -> new java.util.HashMap<>());
        Long last = per.get(key);
        if (last != null && now - last < cd) return;
        per.put(key, now);

        // Build message — reuse existing recipe-locked message frame with the
        // offending-ingredient context appended.
        String stageDisplay = StageOrder.getInstance().getStageDefinition(res.stage)
            .map(StageDefinition::getDisplayName)
            .orElse(res.stage.getPath());

        String itemName;
        try {
            Item it = res.offendingItem;
            itemName = new ItemStack(it).getHoverName().getString();
        } catch (Exception e) {
            itemName = itemKey;
        }

        // Use the generic recipe-locked message and append "(needs X for ingredient Y)"
        String template = StageConfig.getMsgTypeLocked()
            .replace("{type}", "This recipe (uses locked: " + itemName + ")")
            .replace("{stage}", stageDisplay);
        Component msg = TextUtil.parseColorCodes(template);
        player.sendSystemMessage(msg);

        if (StageConfig.isPlayLockSound()) ItemEnforcer.playLockSound(player);
    }

    /**
     * Notify the player a craft was blocked (alias used by ResultSlotMixin); shows
     * both the stage and which ingredient triggered it.
     */
    public static void notifyAutoBlocked(ServerPlayer nearest,
                                         LockRegistry.IngredientBlockResult res) {
        notifyIngredientBlocked(nearest, res);
    }

    public static void clearCooldowns(java.util.UUID playerId) {
        NOTIFY_COOLDOWNS.remove(playerId);
    }

    // -------------------- internal --------------------

    private static Set<Item> collectDistinct(Container c, int from, int toExclusive) {
        Set<Item> out = new HashSet<>();
        int size = c.getContainerSize();
        int lo = Math.max(0, from);
        int hi = Math.min(size, toExclusive);
        for (int i = lo; i < hi; i++) {
            ItemStack s = c.getItem(i);
            if (s != null && !s.isEmpty()) out.add(s.getItem());
        }
        return out;
    }
}
