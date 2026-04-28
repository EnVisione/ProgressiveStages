package com.enviouse.progressivestages.server.enforcement;

import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.config.StageConfig;
import com.enviouse.progressivestages.common.lock.LockRegistry;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;

import java.util.Optional;

/**
 * Enforces {@code [crops] locked = [...]}.
 *
 * <p>Three hooks feed into this:
 * <ul>
 *   <li><b>Planting</b> — {@link #canPlace(ServerPlayer, Block)} from {@code BlockEvent.EntityPlaceEvent}.</li>
 *   <li><b>Growth</b> — {@link #shouldCancelGrowth(ServerLevel, Block, double, double, double)} from {@code BlockEvent.CropGrowEvent.Pre}.
 *       No player is available on growth ticks, so we use the same "nearest player" lookup as mob spawning.</li>
 *   <li><b>Bonemeal</b> — {@link #canBonemeal(ServerPlayer, Block)} from {@code BonemealEvent}.</li>
 * </ul>
 */
public final class CropEnforcer {

    private CropEnforcer() {}

    public static boolean canPlace(ServerPlayer player, Block block) {
        if (!StageConfig.isBlockCropGrowth()) return true;
        if (StageConfig.isAllowCreativeBypass() && player.isCreative()) return true;
        return !LockRegistry.getInstance().isCropBlockedFor(player, block);
    }

    public static boolean canBonemeal(ServerPlayer player, Block block) {
        return canPlace(player, block); // Same gate — if you can't plant it, you can't grow it.
    }

    /**
     * Called on random crop-grow ticks. Returns {@code true} to cancel the growth tick.
     * Team-aware via nearest-player lookup (same pattern as MobSpawnEnforcer).
     * v2.0: multi-stage — cancels if the nearest player is missing ANY of the gating stages.
     */
    public static boolean shouldCancelGrowth(ServerLevel level, Block block, double x, double y, double z) {
        if (!StageConfig.isBlockCropGrowth()) return false;
        java.util.Set<StageId> gating = LockRegistry.getInstance().getRequiredStagesForCrop(block);
        if (gating.isEmpty()) return false;

        double radius = StageConfig.getMobSpawnCheckRadius();
        return NearestPlayerCheck.nearestPlayerLacksAll(level, x, y, z, radius, gating);
    }

    public static void notifyLocked(ServerPlayer player, Block block) {
        Optional<StageId> required = LockRegistry.getInstance().primaryRestrictingStageForCrop(player, block);
        required.ifPresent(stage ->
            ItemEnforcer.notifyLockedWithCooldown(player, stage, "This crop"));
    }
}
