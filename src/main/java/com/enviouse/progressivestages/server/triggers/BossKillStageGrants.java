package com.enviouse.progressivestages.server.triggers;

import com.enviouse.progressivestages.common.api.ProgressiveStagesAPI;
import com.enviouse.progressivestages.common.api.StageCause;
import com.enviouse.progressivestages.common.api.StageId;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;

/**
 * Triggers stage grants when players kill specific entities (bosses).
 *
 * <p>This is an event-driven system - no polling required.
 * When an entity dies and was killed by a player, this handler checks
 * if there's a mapped stage and grants it if so.
 */
public class BossKillStageGrants {

    private static final Logger LOGGER = LogUtils.getLogger();

    // Entity type ID -> Stage ID mapping
    private static final Map<ResourceLocation, StageId> BOSS_STAGES = new HashMap<>();

    /**
     * Register an entity type -> stage mapping.
     * When the entity is killed by a player, the stage will be granted.
     *
     * @param entityTypeId The entity type resource location
     * @param stageId The stage to grant
     */
    public static void registerMapping(ResourceLocation entityTypeId, StageId stageId) {
        BOSS_STAGES.put(entityTypeId, stageId);
        LOGGER.debug("[ProgressiveStages] Registered boss kill stage mapping: {} -> {}",
            entityTypeId, stageId);
    }

    /**
     * Register an entity type -> stage mapping using strings.
     *
     * @param entityTypeId The entity type ID (e.g., "minecraft:ender_dragon")
     * @param stageId The stage ID (e.g., "dragon_slayer")
     */
    public static void registerMapping(String entityTypeId, String stageId) {
        registerMapping(ResourceLocation.parse(entityTypeId), StageId.parse(stageId));
    }

    /**
     * Remove an entity type -> stage mapping.
     */
    public static void removeMapping(ResourceLocation entityTypeId) {
        BOSS_STAGES.remove(entityTypeId);
    }

    /**
     * Clear all entity type -> stage mappings.
     */
    public static void clearMappings() {
        BOSS_STAGES.clear();
    }

    /**
     * Get the stage associated with an entity type, if any.
     */
    public static StageId getStageForEntity(ResourceLocation entityTypeId) {
        return BOSS_STAGES.get(entityTypeId);
    }

    /**
     * Load default mappings (can be overridden by config).
     */
    public static void loadDefaultMappings() {
        // Example mappings - these can be overridden by config
        // registerMapping("minecraft:ender_dragon", "dragon_slayer");
        // registerMapping("minecraft:wither", "wither_slayer");
        // registerMapping("minecraft:warden", "warden_slayer");

        LOGGER.info("[ProgressiveStages] Boss kill stage mappings loaded: {} mappings",
            BOSS_STAGES.size());
    }

    /**
     * Event handler for entity death.
     * Uses TriggerPersistence to ensure boss kills only trigger once ever per player.
     */
    @SubscribeEvent
    public static void onEntityDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();

        // Get the entity type ID
        EntityType<?> entityType = entity.getType();
        ResourceLocation entityTypeId = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entityType);

        // Check if this entity type has a stage mapping
        StageId stageId = BOSS_STAGES.get(entityTypeId);
        if (stageId == null) {
            return;
        }

        // Find the player who killed this entity
        ServerPlayer killer = getKillingPlayer(event);
        if (killer == null) {
            return;
        }

        // Check persistence - has this boss kill trigger already fired for this player?
        TriggerPersistence persistence = TriggerPersistence.get(killer.server);
        String triggerKey = entityTypeId.toString();

        if (persistence.hasTriggered("boss", triggerKey, killer.getUUID())) {
            // Already triggered before, skip (even if player lost the stage somehow)
            return;
        }

        // Grant the stage if the player doesn't have it (redundant safety check)
        if (!ProgressiveStagesAPI.hasStage(killer, stageId)) {
            ProgressiveStagesAPI.grantStage(killer, stageId, StageCause.BOSS_KILL);
            LOGGER.info("[ProgressiveStages] Granted stage '{}' to {} for killing '{}'",
                stageId, killer.getName().getString(), entityTypeId);
        }

        // Mark as triggered (persist across restarts)
        persistence.markTriggered("boss", triggerKey, killer.getUUID());
    }

    /**
     * Get the player who killed the entity, if any.
     * Handles direct kills, projectile kills, and "last hurt by" attribution.
     */
    private static ServerPlayer getKillingPlayer(LivingDeathEvent event) {
        // First check: direct killer (entity that dealt the killing blow)
        Entity directEntity = event.getSource().getDirectEntity();
        if (directEntity instanceof ServerPlayer player) {
            return player;
        }

        // Second check: source entity (e.g., player who shot an arrow)
        Entity sourceEntity = event.getSource().getEntity();
        if (sourceEntity instanceof ServerPlayer player) {
            return player;
        }

        // Third check: last player who hurt this entity (fallback for DoT, fall damage after hit, etc.)
        LivingEntity victim = event.getEntity();
        if (victim.getLastHurtByMob() instanceof ServerPlayer player) {
            // Only attribute if the player damaged recently (within 5 seconds / 100 ticks)
            if (victim.getLastHurtByMobTimestamp() > victim.tickCount - 100) {
                return player;
            }
        }

        // No player attribution found
        return null;
    }
}

