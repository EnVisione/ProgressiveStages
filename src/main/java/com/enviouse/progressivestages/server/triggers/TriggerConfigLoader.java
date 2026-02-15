package com.enviouse.progressivestages.server.triggers;

import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.enviouse.progressivestages.common.util.Constants;
import com.mojang.logging.LogUtils;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Loads trigger mappings from TOML configuration files.
 *
 * <p>Trigger config file: config/ProgressiveStages/triggers.toml
 *
 * <p>Format:
 * <pre>
 * [advancements]
 * "minecraft:story/mine_stone" = "stone_age"
 * "minecraft:story/smelt_iron" = "iron_age"
 *
 * [items]
 * "minecraft:diamond" = "diamond_age"
 * "minecraft:iron_ingot" = "iron_age"
 *
 * [dimensions]
 * "minecraft:the_nether" = "nether_explorer"
 * "minecraft:the_end" = "end_explorer"
 *
 * [bosses]
 * "minecraft:ender_dragon" = "dragon_slayer"
 * "minecraft:wither" = "wither_slayer"
 * </pre>
 */
public class TriggerConfigLoader {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String TRIGGERS_FILE = "triggers.toml";

    /**
     * Load all trigger mappings from config.
     */
    public static void loadTriggerConfig() {
        Path configDir = FMLPaths.CONFIGDIR.get().resolve(Constants.STAGE_FILES_DIRECTORY);
        Path triggersFile = configDir.resolve(TRIGGERS_FILE);

        // Create default file if it doesn't exist
        if (!Files.exists(triggersFile)) {
            createDefaultTriggersFile(triggersFile);
        }

        // Clear existing mappings
        AdvancementStageGrants.clearMappings();
        ItemPickupStageGrants.clearMappings();
        DimensionStageGrants.clearMappings();
        BossKillStageGrants.clearMappings();

        // Load the file using NightConfig
        try (CommentedFileConfig config = CommentedFileConfig.builder(triggersFile).build()) {
            config.load();

            // Load advancement triggers
            if (config.contains("advancements")) {
                Object advObj = config.get("advancements");
                Map<String, Object> advancements = convertToMap(advObj);
                if (advancements != null) {
                    loadMappings(advancements, AdvancementStageGrants::registerMapping, "advancement");
                }
            }

            // Load item pickup triggers
            if (config.contains("items")) {
                Object itemsObj = config.get("items");
                Map<String, Object> items = convertToMap(itemsObj);
                if (items != null) {
                    loadMappings(items, ItemPickupStageGrants::registerMapping, "item");
                }
            }

            // Load dimension triggers
            if (config.contains("dimensions")) {
                Object dimObj = config.get("dimensions");
                Map<String, Object> dimensions = convertToMap(dimObj);
                if (dimensions != null) {
                    loadMappings(dimensions, DimensionStageGrants::registerMapping, "dimension");
                }
            }

            // Load boss kill triggers
            if (config.contains("bosses")) {
                Object bossObj = config.get("bosses");
                Map<String, Object> bosses = convertToMap(bossObj);
                if (bosses != null) {
                    loadMappings(bosses, BossKillStageGrants::registerMapping, "boss");
                }
            }

            LOGGER.info("[ProgressiveStages] Loaded trigger config from {}", triggersFile);

        } catch (Exception e) {
            LOGGER.error("[ProgressiveStages] Failed to load trigger config from {}", triggersFile, e);
        }
    }

    /**
     * Convert NightConfig value to Map.
     * NightConfig can return either a Config object or a raw Map depending on version/context.
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> convertToMap(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Config configObj) {
            return configObj.valueMap();
        }
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        LOGGER.warn("[ProgressiveStages] Unexpected config value type: {}", value.getClass().getName());
        return null;
    }

    @FunctionalInterface
    private interface MappingRegistrar {
        void register(String key, String value);
    }

    private static void loadMappings(Map<String, Object> entries, MappingRegistrar registrar, String type) {
        // Handle null or empty maps gracefully
        if (entries == null || entries.isEmpty()) {
            LOGGER.debug("[ProgressiveStages] No {} triggers defined (section empty or null)", type);
            return;
        }

        int count = 0;
        int invalid = 0;

        for (Map.Entry<String, Object> entry : entries.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if (value instanceof String stageIdStr) {
                // Normalize the stage ID (handles case differences, whitespace, etc.)
                com.enviouse.progressivestages.common.api.StageId stageId =
                    com.enviouse.progressivestages.common.api.StageId.parse(stageIdStr);

                // Use the normalized string representation for registration
                String normalizedStageId = stageId.toString();

                if (!com.enviouse.progressivestages.common.api.ProgressiveStagesAPI.stageExists(stageId)) {
                    LOGGER.warn("[ProgressiveStages] {} trigger '{}' targets non-existent stage '{}' (normalized: '{}') - trigger will not work",
                        type, key, stageIdStr, normalizedStageId);
                    invalid++;
                    // Still register it in case the stage is added later
                }

                try {
                    // Register with normalized stage ID to ensure consistency
                    registrar.register(key, normalizedStageId);
                    count++;
                } catch (Exception e) {
                    LOGGER.warn("[ProgressiveStages] Invalid {} trigger mapping: {} -> {}: {}",
                        type, key, normalizedStageId, e.getMessage());
                }
            } else {
                LOGGER.warn("[ProgressiveStages] Invalid {} trigger value type for {}: expected String, got {}",
                    type, key, value.getClass().getSimpleName());
            }
        }

        if (invalid > 0) {
            LOGGER.warn("[ProgressiveStages] {} {} trigger(s) target non-existent stages", invalid, type);
        }
        LOGGER.debug("[ProgressiveStages] Loaded {} {} trigger mappings", count, type);
    }

    /**
     * Create the default triggers.toml file with examples.
     */
    private static void createDefaultTriggersFile(Path file) {
        String content = """
            # ============================================================================
            # ProgressiveStages v1.1 - Trigger Configuration
            # ============================================================================
            # This file defines automatic stage grants based on player actions.
            # All triggers are EVENT-DRIVEN (no polling/tick scanning).
            #
            # Format: "resource_id" = "stage_id"
            # Stage IDs default to progressivestages namespace if not specified.
            #
            # IMPORTANT: 
            # - Trigger persistence is stored per-world (dimension/boss triggers only fire once)
            # - Item triggers also scan inventory on player login
            # - Use /progressivestages trigger reset to clear trigger history
            # ============================================================================
            
            # ============================================================================
            # ADVANCEMENT TRIGGERS
            # When a player earns an advancement, they automatically receive the stage.
            # Format: "namespace:advancement_path" = "stage_id"
            # ============================================================================
            [advancements]
            # Examples:
            # "minecraft:story/mine_stone" = "stone_age"
            # "minecraft:story/smelt_iron" = "iron_age"
            # "minecraft:story/mine_diamond" = "diamond_age"
            # "minecraft:adventure/kill_a_mob" = "hunter_gatherer"
            # "minecraft:nether/return_to_sender" = "nether_warrior"
            # "minecraft:end/kill_dragon" = "dragon_slayer"
            
            # ============================================================================
            # ITEM PICKUP TRIGGERS
            # When a player picks up an item (or has it in inventory on login), they get the stage.
            # This includes items received from any source: chests, crafting, drops, etc.
            # Format: "namespace:item_id" = "stage_id"
            # ============================================================================
            [items]
            # Examples:
            # "minecraft:iron_ingot" = "iron_age"
            # "minecraft:diamond" = "diamond_age"
            # "minecraft:netherite_ingot" = "netherite_age"
            # "minecraft:ender_pearl" = "end_explorer"
            # "minecraft:blaze_rod" = "nether_explorer"
            
            # ============================================================================
            # DIMENSION ENTRY TRIGGERS
            # When a player FIRST enters a dimension, they get the stage (one-time only).
            # Persisted per-world - use /progressivestages trigger reset to clear.
            # Format: "namespace:dimension_id" = "stage_id"
            # ============================================================================
            [dimensions]
            # Examples:
            # "minecraft:the_nether" = "nether_explorer"
            # "minecraft:the_end" = "end_explorer"
            # "aether:the_aether" = "aether_explorer"
            # "twilightforest:twilight_forest" = "twilight_explorer"
            
            # ============================================================================
            # BOSS/ENTITY KILL TRIGGERS
            # When a player kills a specific entity, they get the stage (one-time only).
            # Persisted per-world - use /progressivestages trigger reset to clear.
            # Format: "namespace:entity_id" = "stage_id"
            # ============================================================================
            [bosses]
            # Examples:
            # "minecraft:ender_dragon" = "dragon_slayer"
            # "minecraft:wither" = "wither_slayer"
            # "minecraft:warden" = "warden_slayer"
            # "minecraft:elder_guardian" = "ocean_conqueror"
            # "alexscaves:tremorzilla" = "tremorzilla_slayer"
            # "irons_spellbooks:dead_king" = "dead_king_slayer"
            """;

        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, content);
            LOGGER.info("[ProgressiveStages] Created default triggers config: {}", file);
        } catch (IOException e) {
            LOGGER.error("[ProgressiveStages] Failed to create default triggers config", e);
        }
    }

    /**
     * Reload trigger config from disk.
     */
    public static void reload() {
        loadTriggerConfig();
    }
}

