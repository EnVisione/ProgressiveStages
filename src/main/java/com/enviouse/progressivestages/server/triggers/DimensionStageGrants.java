package com.enviouse.progressivestages.server.triggers;

import com.enviouse.progressivestages.common.api.ProgressiveStagesAPI;
import com.enviouse.progressivestages.common.api.StageCause;
import com.enviouse.progressivestages.common.api.StageId;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;

/**
 * Triggers stage grants when players first enter specific dimensions.
 *
 * <p>This is an event-driven system - no polling required.
 * When a player changes dimension, this handler checks if there's a mapped stage
 * and grants it if so.
 */
public class DimensionStageGrants {

    private static final Logger LOGGER = LogUtils.getLogger();

    // Dimension ID -> Stage ID mapping
    private static final Map<ResourceLocation, StageId> DIMENSION_STAGES = new HashMap<>();

    /**
     * Register a dimension -> stage mapping.
     * When the player enters the dimension, the stage will be granted.
     *
     * @param dimensionId The dimension resource location
     * @param stageId The stage to grant
     */
    public static void registerMapping(ResourceLocation dimensionId, StageId stageId) {
        DIMENSION_STAGES.put(dimensionId, stageId);
        LOGGER.debug("[ProgressiveStages] Registered dimension stage mapping: {} -> {}",
            dimensionId, stageId);
    }

    /**
     * Register a dimension -> stage mapping using strings.
     *
     * @param dimensionId The dimension ID (e.g., "minecraft:the_nether")
     * @param stageId The stage ID (e.g., "nether_explorer")
     */
    public static void registerMapping(String dimensionId, String stageId) {
        registerMapping(ResourceLocation.parse(dimensionId), StageId.parse(stageId));
    }

    /**
     * Remove a dimension -> stage mapping.
     */
    public static void removeMapping(ResourceLocation dimensionId) {
        DIMENSION_STAGES.remove(dimensionId);
    }

    /**
     * Clear all dimension -> stage mappings.
     */
    public static void clearMappings() {
        DIMENSION_STAGES.clear();
    }

    /**
     * Get the stage associated with a dimension, if any.
     */
    public static StageId getStageForDimension(ResourceLocation dimensionId) {
        return DIMENSION_STAGES.get(dimensionId);
    }

    /**
     * Load default mappings (can be overridden by config).
     */
    public static void loadDefaultMappings() {
        // Example mappings - these can be overridden by config
        // registerMapping("minecraft:the_nether", "nether_explorer");
        // registerMapping("minecraft:the_end", "end_explorer");

        LOGGER.info("[ProgressiveStages] Dimension stage mappings loaded: {} mappings",
            DIMENSION_STAGES.size());
    }

    /**
     * Event handler for dimension change.
     * Uses TriggerPersistence to ensure dimensions only trigger once ever per player.
     */
    @SubscribeEvent
    public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        ResourceKey<Level> toDimension = event.getTo();
        ResourceLocation dimensionId = toDimension.location();

        StageId stageId = DIMENSION_STAGES.get(dimensionId);
        if (stageId != null) {
            // Check persistence - has this dimension trigger already fired for this player?
            TriggerPersistence persistence = TriggerPersistence.get(player.server);
            String triggerKey = dimensionId.toString();

            if (persistence.hasTriggered("dimension", triggerKey, player.getUUID())) {
                // Already triggered before, skip (even if player lost the stage somehow)
                return;
            }

            // Check if player already has the stage (redundant safety check)
            if (!ProgressiveStagesAPI.hasStage(player, stageId)) {
                ProgressiveStagesAPI.grantStage(player, stageId, StageCause.DIMENSION_ENTRY);
                LOGGER.info("[ProgressiveStages] Granted stage '{}' to {} for entering dimension '{}'",
                    stageId, player.getName().getString(), dimensionId);
            }

            // Mark as triggered (persist across restarts)
            persistence.markTriggered("dimension", triggerKey, player.getUUID());
        }
    }
}

