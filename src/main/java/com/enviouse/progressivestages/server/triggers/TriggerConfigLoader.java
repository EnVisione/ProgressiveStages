package com.enviouse.progressivestages.server.triggers;

import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.enviouse.progressivestages.common.api.ProgressiveStagesAPI;
import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.util.Constants;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Loads trigger mappings from {@code config/ProgressiveStages/triggers.toml}.
 *
 * <p>The file contains five sections:
 *
 * <pre>
 * [advancements]               # one-to-one: advancement -> stage
 * [items]                      # one-to-one: item pickup -> stage
 * [dimensions]                 # one-to-one: first dimension entry -> stage
 * [bosses]                     # one-to-one: boss kill -> stage
 * [[multi]] / [[multi.all_of]] # many-to-one: all (or any) sub-triggers -> stage
 * </pre>
 *
 * <p>The first four are simple {@code "key" = "stage"} maps registered into the
 * matching {@code *StageGrants} handler. The fifth — {@code [[multi]]} — is a
 * table-array of MULTI-trigger requirements, each one specifying a stage plus a
 * list of sub-triggers that must ALL fire (or ANY fire) before the stage is
 * granted. Each sub-trigger uses a prefix to pick its surface:
 *
 * <ul>
 *   <li>{@code item:minecraft:diamond}</li>
 *   <li>{@code advancement:minecraft:story/mine_iron}</li>
 *   <li>{@code dimension:minecraft:the_nether}</li>
 *   <li>{@code boss:minecraft:wither}</li>
 * </ul>
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
        MultiTriggerManager.clear();

        // Load the file using NightConfig
        try (CommentedFileConfig config = CommentedFileConfig.builder(triggersFile).build()) {
            config.load();

            if (config.contains("advancements")) {
                Map<String, Object> advancements = convertToMap(config.get("advancements"));
                if (advancements != null) {
                    loadMappings(advancements, AdvancementStageGrants::registerMapping, "advancement");
                }
            }

            if (config.contains("items")) {
                Map<String, Object> items = convertToMap(config.get("items"));
                if (items != null) {
                    loadMappings(items, ItemPickupStageGrants::registerMapping, "item");
                }
            }

            if (config.contains("dimensions")) {
                Map<String, Object> dimensions = convertToMap(config.get("dimensions"));
                if (dimensions != null) {
                    loadMappings(dimensions, DimensionStageGrants::registerMapping, "dimension");
                }
            }

            if (config.contains("bosses")) {
                Map<String, Object> bosses = convertToMap(config.get("bosses"));
                if (bosses != null) {
                    loadMappings(bosses, BossKillStageGrants::registerMapping, "boss");
                }
            }

            // 2.0: multi-trigger requirements
            loadMultiRequirements(config);

            LOGGER.info("[ProgressiveStages] Loaded trigger config from {} ({} multi-requirement(s))",
                triggersFile, MultiTriggerManager.getAll().size());

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
                StageId stageId = StageId.parse(stageIdStr);
                String normalizedStageId = stageId.toString();

                if (!ProgressiveStagesAPI.stageExists(stageId)) {
                    LOGGER.warn("[ProgressiveStages] {} trigger '{}' targets non-existent stage '{}' (normalized: '{}') - trigger will not work",
                        type, key, stageIdStr, normalizedStageId);
                    invalid++;
                }

                try {
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

    // -------------------- [[multi]] requirements --------------------

    /**
     * Parse the {@code [[multi]]} table-array. Each entry needs a {@code stage} field
     * plus at least one non-empty {@code all_of}/{@code any_of} list. The optional
     * {@code id} field stabilizes the persistence key across edits — without it, the
     * id is auto-derived from a hash of (stage + mode + sorted sub-keys).
     */
    @SuppressWarnings("unchecked")
    private static void loadMultiRequirements(Config config) {
        Object raw = config.get("multi");
        if (raw == null) return;
        if (!(raw instanceof List<?> list)) {
            LOGGER.warn("[ProgressiveStages] [[multi]] section is not a table-array — expected [[multi]] entries.");
            return;
        }

        int index = 0;
        for (Object obj : list) {
            if (!(obj instanceof Config entry)) {
                LOGGER.warn("[ProgressiveStages] [[multi]] entry #{} is not a table — skipped.", index);
                index++;
                continue;
            }

            String stageRaw = entry.get("stage");
            if (stageRaw == null || stageRaw.isEmpty()) {
                LOGGER.warn("[ProgressiveStages] [[multi]] entry #{} is missing 'stage' — skipped.", index);
                index++;
                continue;
            }
            StageId stageId;
            try {
                stageId = StageId.parse(stageRaw);
            } catch (Exception e) {
                LOGGER.warn("[ProgressiveStages] [[multi]] entry #{} has invalid stage '{}' — skipped.", index, stageRaw);
                index++;
                continue;
            }

            // Mode + sub-triggers. all_of takes precedence; any_of fires on first match.
            List<String> allOfRaw = stringList(entry, "all_of");
            List<String> anyOfRaw = stringList(entry, "any_of");
            MultiTrigger.Mode mode;
            List<String> subRaw;
            if (!allOfRaw.isEmpty()) {
                mode = MultiTrigger.Mode.ALL_OF;
                subRaw = allOfRaw;
            } else if (!anyOfRaw.isEmpty()) {
                mode = MultiTrigger.Mode.ANY_OF;
                subRaw = anyOfRaw;
            } else {
                LOGGER.warn("[ProgressiveStages] [[multi]] entry #{} (stage='{}') has no all_of/any_of list — skipped.",
                    index, stageRaw);
                index++;
                continue;
            }

            List<MultiTrigger.SubTrigger> subs = new ArrayList<>();
            for (String s : subRaw) {
                MultiTrigger.SubTrigger sub = parseSubTrigger(s);
                if (sub != null) {
                    subs.add(sub);
                } else {
                    LOGGER.warn("[ProgressiveStages] [[multi]] entry #{} (stage='{}') has invalid sub-trigger '{}' — skipped.",
                        index, stageRaw, s);
                }
            }
            if (subs.isEmpty()) {
                LOGGER.warn("[ProgressiveStages] [[multi]] entry #{} (stage='{}') ended up with zero valid sub-triggers — skipped.",
                    index, stageRaw);
                index++;
                continue;
            }

            String userId = entry.get("id");
            String description = entry.get("description");
            String requirementId = userId != null && !userId.isEmpty()
                ? userId.trim()
                : autoId(stageId, mode, subs);

            if (!ProgressiveStagesAPI.stageExists(stageId)) {
                LOGGER.warn("[ProgressiveStages] [[multi]] requirement '{}' targets non-existent stage '{}' — registered anyway in case the stage is added later.",
                    requirementId, stageId);
            }

            MultiTrigger req = new MultiTrigger(requirementId, stageId, mode, subs, description);
            MultiTriggerManager.register(req);
            LOGGER.info("[ProgressiveStages] Loaded multi-requirement '{}' -> {} ({}, {} sub-trigger(s))",
                requirementId, stageId, mode, subs.size());
            index++;
        }
    }

    /** Parse one sub-trigger string like {@code "item:minecraft:diamond"}. */
    private static MultiTrigger.SubTrigger parseSubTrigger(String raw) {
        if (raw == null) return null;
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) return null;
        int colon = trimmed.indexOf(':');
        if (colon <= 0) return null;
        String prefix = trimmed.substring(0, colon);
        String rest = trimmed.substring(colon + 1).trim();
        MultiTrigger.SubType type = MultiTrigger.SubType.parse(prefix);
        if (type == null || rest.isEmpty()) return null;
        ResourceLocation key;
        try {
            key = ResourceLocation.parse(rest);
        } catch (Exception e) {
            return null;
        }
        return new MultiTrigger.SubTrigger(type, key, trimmed);
    }

    private static String autoId(StageId stageId, MultiTrigger.Mode mode, List<MultiTrigger.SubTrigger> subs) {
        List<String> keys = new ArrayList<>();
        for (MultiTrigger.SubTrigger s : subs) keys.add(s.canonicalKey());
        Collections.sort(keys);
        String canonical = stageId.toString() + "|" + mode.name() + "|" + String.join(",", keys);
        return stageId.getPath() + "_" + Integer.toHexString(canonical.hashCode() & 0x7FFFFFFF);
    }

    @SuppressWarnings("unchecked")
    private static List<String> stringList(Config c, String key) {
        Object v = c.get(key);
        if (v == null) return Collections.emptyList();
        if (v instanceof List<?> list) {
            List<String> out = new ArrayList<>(list.size());
            for (Object o : list) if (o instanceof String s) out.add(s);
            return out;
        }
        return Collections.emptyList();
    }

    // -------------------- default file --------------------

    /**
     * Create the default triggers.toml file with examples.
     */
    private static void createDefaultTriggersFile(Path file) {
        String content = com.enviouse.progressivestages.server.loader.DefaultStageTemplates.triggers();
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
