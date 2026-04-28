package com.enviouse.progressivestages.server.enforcement;

import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.config.StageConfig;
import com.enviouse.progressivestages.common.lock.LockRegistry;
import com.enviouse.progressivestages.common.stage.StageManager;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles dimension travel enforcement.
 *
 * <p>Uses two layers of protection:
 * <ol>
 *   <li><b>Pre-travel</b> ({@link net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent}):
 *       Cancels the dimension change before it happens. Works for all teleportation that goes
 *       through {@code Entity.changeDimension()}.</li>
 *   <li><b>Post-travel safety net</b> ({@link net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent}):
 *       Catches dimension changes that bypassed the pre-travel event (e.g., modded portals like
 *       Twilight Forest that use custom teleportation). Teleports the player back to their
 *       previous position in the source dimension.</li>
 * </ol>
 */
public class DimensionEnforcer {

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Stores each player's position/dimension just before a dimension change attempt,
     * so we can teleport them back if the safety net triggers.
     */
    private static final Map<UUID, SavedPosition> SAVED_POSITIONS = new ConcurrentHashMap<>();

    /**
     * Tracks players currently being bounced back to prevent infinite loops.
     * If a bounce-back teleport triggers another dimension change event, we skip enforcement.
     */
    private static final Set<UUID> BOUNCING_BACK = ConcurrentHashMap.newKeySet();

    /**
     * Immutable record of a player's position before dimension travel.
     */
    public record SavedPosition(ResourceKey<Level> dimension, double x, double y, double z, float yRot, float xRot) {}

    // ==================== Pre-Travel (Primary Gate) ====================

    /**
     * Check if a player can travel to a dimension.
     * @return true if allowed, false if blocked
     */
    public static boolean canTravelToDimension(ServerPlayer player, ResourceKey<Level> dimension) {
        // Always allow bounce-back teleports
        if (BOUNCING_BACK.contains(player.getUUID())) {
            return true;
        }

        if (!StageConfig.isBlockDimensionTravel()) {
            return true;
        }

        // Creative bypass
        if (StageConfig.isAllowCreativeBypass() && player.isCreative()) {
            return true;
        }

        if (dimension == null) {
            return true;
        }

        return !isDimensionLockedForPlayer(player, dimension.location());
    }

    /**
     * Save the player's current position before a dimension change attempt.
     * Called from the pre-travel event handler so the safety net can teleport back.
     */
    public static void savePositionBeforeTravel(ServerPlayer player) {
        SAVED_POSITIONS.put(player.getUUID(), new SavedPosition(
                player.level().dimension(),
                player.getX(), player.getY(), player.getZ(),
                player.getYRot(), player.getXRot()
        ));
    }

    // ==================== Post-Travel Safety Net ====================

    /**
     * Called after a dimension change has already occurred.
     * If the new dimension is locked, teleports the player back to the source dimension.
     *
     * @param player the player who changed dimensions
     * @param from   the dimension the player came from
     * @param to     the dimension the player arrived in
     */
    public static void handlePostTravelSafetyNet(ServerPlayer player, ResourceKey<Level> from, ResourceKey<Level> to) {
        if (!StageConfig.isBlockDimensionTravel()) {
            cleanupPlayer(player.getUUID());
            return;
        }

        if (StageConfig.isAllowCreativeBypass() && player.isCreative()) {
            cleanupPlayer(player.getUUID());
            return;
        }

        if (BOUNCING_BACK.contains(player.getUUID())) {
            return; // Don't check during bounce-back
        }

        // Check if the destination dimension is locked
        if (!isDimensionLockedForPlayer(player, to.location())) {
            cleanupPlayer(player.getUUID());
            return; // Destination is allowed
        }

        // Player ended up in a locked dimension — bounce them back
        LOGGER.warn("[ProgressiveStages] Player {} arrived in locked dimension {} (bypassed pre-travel event). Bouncing back to {}.",
                player.getName().getString(), to.location(), from.location());

        bounceBack(player, from);
    }

    /**
     * Teleport the player back to the source dimension.
     * Uses the saved position if available, otherwise falls back to the world spawn.
     */
    private static void bounceBack(ServerPlayer player, ResourceKey<Level> fromDimension) {
        UUID playerId = player.getUUID();
        BOUNCING_BACK.add(playerId);

        try {
            SavedPosition saved = SAVED_POSITIONS.get(playerId);
            ServerLevel targetLevel = player.server.getLevel(fromDimension);

            if (targetLevel == null) {
                // Source dimension no longer available — use overworld as fallback
                targetLevel = player.server.overworld();
                LOGGER.warn("[ProgressiveStages] Source dimension {} not available, falling back to overworld.", fromDimension.location());
            }

            double x, y, z;
            float yRot, xRot;

            if (saved != null && saved.dimension().equals(fromDimension)) {
                // Use the exact position the player was at before the dimension change
                x = saved.x();
                y = saved.y();
                z = saved.z();
                yRot = saved.yRot();
                xRot = saved.xRot();
            } else {
                // No saved position — use the world spawn
                BlockPos spawn = targetLevel.getSharedSpawnPos();
                x = spawn.getX() + 0.5;
                y = spawn.getY() + 1.0;
                z = spawn.getZ() + 0.5;
                yRot = player.getYRot();
                xRot = player.getXRot();
            }

            player.teleportTo(targetLevel, x, y, z, Set.of(), yRot, xRot);
            notifyLocked(player, player.level().dimension().location());
        } catch (Exception e) {
            LOGGER.error("[ProgressiveStages] Failed to bounce player {} back from locked dimension: {}",
                    player.getName().getString(), e.getMessage());
        } finally {
            BOUNCING_BACK.remove(playerId);
            SAVED_POSITIONS.remove(playerId);
        }
    }

    // ==================== Query Methods ====================

    /**
     * Check if a dimension is locked for a player.
     * v2.0: multi-stage aware.
     */
    public static boolean isDimensionLockedForPlayer(ServerPlayer player, ResourceLocation dimensionId) {
        return LockRegistry.getInstance().isDimensionBlockedFor(player, dimensionId);
    }

    /**
     * Notify player that dimension is locked.
     * v2.0: shows the primary missing stage.
     */
    public static void notifyLocked(ServerPlayer player, ResourceLocation dimensionId) {
        java.util.Set<StageId> gating = LockRegistry.getInstance().getRequiredStagesForDimension(dimensionId);
        if (gating.isEmpty()) return;
        for (StageId s : gating) {
            if (!StageManager.getInstance().hasStage(player, s)) {
                ItemEnforcer.notifyLocked(player, s, com.enviouse.progressivestages.common.config.StageConfig.getMsgTypeLabelDimension());
                return;
            }
        }
    }

    // ==================== Cleanup ====================

    /**
     * Remove tracking data for a player (call on logout).
     */
    public static void cleanupPlayer(UUID playerId) {
        SAVED_POSITIONS.remove(playerId);
        BOUNCING_BACK.remove(playerId);
    }
}
