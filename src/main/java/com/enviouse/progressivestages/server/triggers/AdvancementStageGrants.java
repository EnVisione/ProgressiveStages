package com.enviouse.progressivestages.server.triggers;

import com.enviouse.progressivestages.common.api.ProgressiveStagesAPI;
import com.enviouse.progressivestages.common.api.StageCause;
import com.enviouse.progressivestages.common.api.StageId;
import com.mojang.logging.LogUtils;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;

/**
 * Triggers stage grants when players complete specific advancements.
 *
 * <p>This is an event-driven system - no polling required.
 * When an advancement completes, this handler checks if there's a mapped stage
 * and grants it if so.
 *
 * <p>Mappings can be configured via:
 * <ul>
 *   <li>TOML config files</li>
 *   <li>Datapack JSON files</li>
 *   <li>API calls</li>
 * </ul>
 */
public class AdvancementStageGrants {

    private static final Logger LOGGER = LogUtils.getLogger();

    // Advancement ID -> Stage ID mapping
    private static final Map<ResourceLocation, StageId> ADVANCEMENT_STAGES = new HashMap<>();

    /**
     * Register an advancement -> stage mapping.
     * When the advancement is completed, the stage will be granted.
     *
     * @param advancementId The advancement resource location
     * @param stageId The stage to grant
     */
    public static void registerMapping(ResourceLocation advancementId, StageId stageId) {
        ADVANCEMENT_STAGES.put(advancementId, stageId);
        LOGGER.debug("[ProgressiveStages] Registered advancement stage mapping: {} -> {}",
            advancementId, stageId);
    }

    /**
     * Register an advancement -> stage mapping using strings.
     *
     * @param advancementId The advancement ID (e.g., "minecraft:adventure/kill_a_mob")
     * @param stageId The stage ID (e.g., "hunter_gatherer")
     */
    public static void registerMapping(String advancementId, String stageId) {
        registerMapping(ResourceLocation.parse(advancementId), StageId.parse(stageId));
    }

    /**
     * Remove an advancement -> stage mapping.
     */
    public static void removeMapping(ResourceLocation advancementId) {
        ADVANCEMENT_STAGES.remove(advancementId);
    }

    /**
     * Clear all advancement -> stage mappings.
     */
    public static void clearMappings() {
        ADVANCEMENT_STAGES.clear();
    }

    /**
     * Get the stage associated with an advancement, if any.
     */
    public static StageId getStageForAdvancement(ResourceLocation advancementId) {
        return ADVANCEMENT_STAGES.get(advancementId);
    }

    /**
     * Load default mappings (can be overridden by config).
     */
    public static void loadDefaultMappings() {
        // Example mappings - these can be overridden by config
        // registerMapping("minecraft:story/mine_stone", "stone_age");
        // registerMapping("minecraft:story/smelt_iron", "iron_age");
        // registerMapping("minecraft:story/mine_diamond", "diamond_age");

        LOGGER.info("[ProgressiveStages] Advancement stage mappings loaded: {} mappings",
            ADVANCEMENT_STAGES.size());
    }

    /**
     * Event handler for advancement completion.
     */
    @SubscribeEvent
    public static void onAdvancementEarn(AdvancementEvent.AdvancementEarnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        AdvancementHolder holder = event.getAdvancement();
        ResourceLocation advancementId = holder.id();

        StageId stageId = ADVANCEMENT_STAGES.get(advancementId);
        if (stageId != null) {
            // Check if player already has the stage
            if (!ProgressiveStagesAPI.hasStage(player, stageId)) {
                ProgressiveStagesAPI.grantStage(player, stageId, StageCause.ADVANCEMENT);
                LOGGER.info("[ProgressiveStages] Granted stage '{}' to {} for advancement '{}'",
                    stageId, player.getName().getString(), advancementId);
            }
        }
    }
}

