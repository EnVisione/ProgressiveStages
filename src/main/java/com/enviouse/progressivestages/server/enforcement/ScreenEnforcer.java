package com.enviouse.progressivestages.server.enforcement;

import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.config.StageConfig;
import com.enviouse.progressivestages.common.lock.LockRegistry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;

import java.util.Optional;

/**
 * Enforces {@code [screens] locked = [...]}.
 *
 * <p>Screens are gated at the block-right-click level: when a player right-clicks a
 * block whose registry ID (or mod/tag/name match) is in the screens category, the
 * interaction is cancelled before the block's {@code use} method runs. The screens
 * category is intentionally separate from the items/blocks locks so a modpack can
 * lock <em>opening</em> a container (e.g. an anvil or a backpack) without locking
 * the item itself.
 */
public final class ScreenEnforcer {

    private ScreenEnforcer() {}

    /**
     * @return {@code true} if the player is allowed to open this screen/block;
     *         {@code false} if the screens category would block it.
     */
    public static boolean canOpenScreen(ServerPlayer player, Block block) {
        if (!StageConfig.isBlockScreenOpen()) return true;
        if (StageConfig.isAllowCreativeBypass() && player.isCreative()) return true;
        return !LockRegistry.getInstance().isScreenBlockedFor(player, block);
    }

    public static void notifyLocked(ServerPlayer player, Block block) {
        Optional<StageId> required = LockRegistry.getInstance().primaryRestrictingStageForScreen(player, block);
        required.ifPresent(stage ->
            ItemEnforcer.notifyLockedWithCooldown(player, stage, StageConfig.getMsgTypeLabelScreen()));
    }

    /**
     * Item variant for GUIs opened by right-clicking an item in hand (backpacks,
     * portable crafting tables, some shulker mods). Returns {@code true} when the held
     * stack's item isn't in the {@code [screens] locked} list, or the player has all gating stages.
     */
    public static boolean canOpenFromItem(ServerPlayer player, net.minecraft.world.item.ItemStack stack) {
        if (!StageConfig.isBlockScreenOpen()) return true;
        if (StageConfig.isAllowCreativeBypass() && player.isCreative()) return true;
        if (stack.isEmpty()) return true;
        return !LockRegistry.getInstance().isScreenItemBlockedFor(player, stack.getItem());
    }

    public static void notifyLockedItem(ServerPlayer player, net.minecraft.world.item.ItemStack stack) {
        if (stack.isEmpty()) return;
        Optional<StageId> required = LockRegistry.getInstance().primaryRestrictingStageForScreenItem(player, stack.getItem());
        required.ifPresent(stage ->
            ItemEnforcer.notifyLockedWithCooldown(player, stage, StageConfig.getMsgTypeLabelScreenItem()));
    }
}
