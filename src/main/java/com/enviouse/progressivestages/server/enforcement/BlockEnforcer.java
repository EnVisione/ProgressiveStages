package com.enviouse.progressivestages.server.enforcement;

import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.config.StageConfig;
import com.enviouse.progressivestages.common.lock.LockRegistry;
import com.enviouse.progressivestages.common.stage.StageManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;

import java.util.Optional;

/**
 * Handles block placement and interaction enforcement
 */
public class BlockEnforcer {

    /**
     * Check if a player can place a block
     * @return true if allowed, false if blocked
     */
    public static boolean canPlaceBlock(ServerPlayer player, Block block) {
        if (!StageConfig.isBlockBlockPlacement()) {
            return true;
        }

        // Creative bypass
        if (StageConfig.isAllowCreativeBypass() && player.isCreative()) {
            return true;
        }

        if (block == null) {
            return true;
        }

        return !isBlockLockedForPlayer(player, block);
    }

    /**
     * Check if a player can interact with (right-click) a block
     * @return true if allowed, false if blocked
     */
    public static boolean canInteractWithBlock(ServerPlayer player, Block block) {
        if (!StageConfig.isBlockBlockInteraction()) {
            return true;
        }

        // Creative bypass
        if (StageConfig.isAllowCreativeBypass() && player.isCreative()) {
            return true;
        }

        if (block == null) {
            return true;
        }

        return !isBlockLockedForPlayer(player, block);
    }

    /**
     * Check if a block is locked for a player
     */
    public static boolean isBlockLockedForPlayer(ServerPlayer player, Block block) {
        Optional<StageId> requiredStage = LockRegistry.getInstance().getRequiredStageForBlock(block);
        if (requiredStage.isEmpty()) {
            return false;
        }

        return !StageManager.getInstance().hasStage(player, requiredStage.get());
    }

    /**
     * Notify player that block is locked
     */
    public static void notifyPlacementLocked(ServerPlayer player, Block block) {
        Optional<StageId> requiredStage = LockRegistry.getInstance().getRequiredStageForBlock(block);
        if (requiredStage.isPresent()) {
            ItemEnforcer.notifyLocked(player, requiredStage.get(), "This block");
        }
    }

    /**
     * Notify player that block interaction is locked
     */
    public static void notifyInteractionLocked(ServerPlayer player, Block block) {
        Optional<StageId> requiredStage = LockRegistry.getInstance().getRequiredStageForBlock(block);
        if (requiredStage.isPresent()) {
            ItemEnforcer.notifyLocked(player, requiredStage.get(), "This block");
        }
    }
}
