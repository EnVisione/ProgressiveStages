package com.enviouse.progressivestages.compat.ftbquests;

import com.enviouse.progressivestages.common.api.ProgressiveStagesAPI;
import com.enviouse.progressivestages.common.api.StageId;
import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

/**
 * Helper class for checking stage requirements in FTB Quests.
 *
 * This bridges FTB Quests to ProgressiveStages' stage system.
 * Uses reflection to avoid compile-time dependencies on FTB.
 */
public final class StageRequirementHelper {

    private static final Logger LOGGER = LogUtils.getLogger();

    private StageRequirementHelper() {}

    /**
     * Check if a player has the specified stage (for use in FTB Quests context).
     *
     * @param player The server player
     * @param stageIdStr The stage ID string (will be normalized)
     * @return true if the stage is unlocked, false otherwise
     */
    public static boolean hasStage(ServerPlayer player, String stageIdStr) {
        if (stageIdStr == null || stageIdStr.isBlank()) {
            // No stage requirement = visible
            return true;
        }

        try {
            // Normalize the stage ID
            StageId stageId = StageId.parse(stageIdStr.trim());
            return ProgressiveStagesAPI.hasStage(player, stageId);
        } catch (Exception e) {
            LOGGER.warn("[ProgressiveStages] Error checking stage requirement '{}': {}", stageIdStr, e.getMessage());
            // On error, default to visible (don't block content due to errors)
            return true;
        }
    }

    /**
     * Check if a player has the specified stage using client-side cache.
     * For use in client-side FTB Quests visibility checks.
     *
     * @param stageIdStr The stage ID string (will be normalized)
     * @return true if the stage is unlocked, false otherwise
     */
    public static boolean hasStageClient(String stageIdStr) {
        if (stageIdStr == null || stageIdStr.isBlank()) {
            return true;
        }

        try {
            StageId stageId = StageId.parse(stageIdStr.trim());
            return com.enviouse.progressivestages.client.ClientStageCache.hasStage(stageId);
        } catch (Exception e) {
            LOGGER.warn("[ProgressiveStages] Error checking client stage requirement '{}': {}", stageIdStr, e.getMessage());
            return true;
        }
    }
}

