package com.enviouse.progressivestages.server.enforcement;

import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.config.StageConfig;
import com.enviouse.progressivestages.common.lock.LockRegistry;
import com.enviouse.progressivestages.common.stage.StageManager;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.Optional;

/**
 * Handles dimension travel enforcement
 */
public class DimensionEnforcer {

    /**
     * Check if a player can travel to a dimension
     * @return true if allowed, false if blocked
     */
    public static boolean canTravelToDimension(ServerPlayer player, ResourceKey<Level> dimension) {
        if (!StageConfig.isBlockDimensionTravel()) {
            return true;
        }

        if (dimension == null) {
            return true;
        }

        return !isDimensionLockedForPlayer(player, dimension.location());
    }

    /**
     * Check if a dimension is locked for a player
     */
    public static boolean isDimensionLockedForPlayer(ServerPlayer player, ResourceLocation dimensionId) {
        Optional<StageId> requiredStage = LockRegistry.getInstance().getRequiredStageForDimension(dimensionId);
        if (requiredStage.isEmpty()) {
            return false;
        }

        return !StageManager.getInstance().hasStage(player, requiredStage.get());
    }

    /**
     * Notify player that dimension is locked
     */
    public static void notifyLocked(ServerPlayer player, ResourceLocation dimensionId) {
        Optional<StageId> requiredStage = LockRegistry.getInstance().getRequiredStageForDimension(dimensionId);
        if (requiredStage.isPresent()) {
            ItemEnforcer.notifyLocked(player, requiredStage.get(), "This dimension");
        }
    }
}
