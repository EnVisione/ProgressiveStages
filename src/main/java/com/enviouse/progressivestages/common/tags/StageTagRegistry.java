package com.enviouse.progressivestages.common.tags;

import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.config.StageDefinition;
import com.enviouse.progressivestages.common.lock.LockDefinition;
import com.enviouse.progressivestages.common.stage.StageOrder;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.core.registries.Registries;
import org.slf4j.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dynamic stage tag registry - builds item groups from loaded stage definitions.
 *
 * This replaces static tag JSON files with runtime-generated groups based on
 * the TOML stage files in the world's ProgressiveStages directory.
 *
 * Usage in EMI: search #progressivestages:iron_age to find all iron age items.
 */
public final class StageTagRegistry {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String TAG_NAMESPACE = "progressivestages";

    // StageId path -> Set of items in that stage
    private static final Map<String, Set<Item>> stageItems = new ConcurrentHashMap<>();

    // Item -> StageId (reverse lookup for checking which stage an item belongs to)
    private static final Map<Item, StageId> itemToStage = new ConcurrentHashMap<>();

    private StageTagRegistry() {
    }

    /**
     * Clear all cached tag data
     */
    public static void clear() {
        stageItems.clear();
        itemToStage.clear();
    }

    /**
     * Rebuild tags from the currently loaded stage definitions.
     * Call this after stages are loaded/reloaded.
     */
    public static void rebuildFromStages() {
        clear();

        for (StageId stageId : StageOrder.getInstance().getOrderedStages()) {
            Optional<StageDefinition> defOpt = StageOrder.getInstance().getStageDefinition(stageId);
            if (defOpt.isEmpty()) continue;

            StageDefinition def = defOpt.get();
            LockDefinition locks = def.getLocks();
            String stagePath = stageId.getPath();

            Set<Item> items = stageItems.computeIfAbsent(stagePath, k -> ConcurrentHashMap.newKeySet());

            // Add directly locked items
            for (String itemIdStr : locks.getItems()) {
                ResourceLocation itemId = ResourceLocation.tryParse(itemIdStr);
                if (itemId != null) {
                    Item item = BuiltInRegistries.ITEM.get(itemId);
                    if (item != null && item != BuiltInRegistries.ITEM.get(BuiltInRegistries.ITEM.getDefaultKey())) {
                        items.add(item);
                        itemToStage.put(item, stageId);
                    }
                }
            }

            // Add items from item tags defined in the stage
            for (String tagIdStr : locks.getItemTags()) {
                String cleanTag = tagIdStr.startsWith("#") ? tagIdStr.substring(1) : tagIdStr;
                ResourceLocation tagId = ResourceLocation.tryParse(cleanTag);
                if (tagId != null) {
                    TagKey<Item> tagKey = TagKey.create(Registries.ITEM, tagId);
                    // Iterate all items and check if they're in this tag
                    for (Item item : BuiltInRegistries.ITEM) {
                        if (item.builtInRegistryHolder().is(tagKey)) {
                            items.add(item);
                            itemToStage.putIfAbsent(item, stageId); // First stage wins
                        }
                    }
                }
            }
        }

        int totalItems = stageItems.values().stream().mapToInt(Set::size).sum();
        LOGGER.info("[ProgressiveStages] Built dynamic stage tags: {} stages, {} total items",
            stageItems.size(), totalItems);
    }

    /**
     * Get all items belonging to a stage (by stage path, e.g. "iron_age")
     */
    public static Set<Item> getItemsForStage(String stagePath) {
        return Collections.unmodifiableSet(stageItems.getOrDefault(stagePath, Collections.emptySet()));
    }

    /**
     * Get all items belonging to a stage
     */
    public static Set<Item> getItemsForStage(StageId stageId) {
        return getItemsForStage(stageId.getPath());
    }

    /**
     * Get the stage an item belongs to (for display/grouping)
     */
    public static Optional<StageId> getStageForItem(Item item) {
        return Optional.ofNullable(itemToStage.get(item));
    }

    /**
     * Get all stage paths that have items
     */
    public static Set<String> getAllStagePaths() {
        return Collections.unmodifiableSet(stageItems.keySet());
    }

    /**
     * Check if an item is in a specific stage tag
     */
    public static boolean isItemInStage(Item item, String stagePath) {
        Set<Item> items = stageItems.get(stagePath);
        return items != null && items.contains(item);
    }

    /**
     * Check if an item is in a specific stage tag
     */
    public static boolean isItemInStage(Item item, StageId stageId) {
        return isItemInStage(item, stageId.getPath());
    }

    /**
     * Get the tag ResourceLocation for a stage (for EMI tag search compatibility)
     * Returns progressivestages:<stage_path>
     */
    public static ResourceLocation getTagId(String stagePath) {
        return ResourceLocation.fromNamespaceAndPath(TAG_NAMESPACE, stagePath);
    }

    /**
     * Get the tag ResourceLocation for a stage
     */
    public static ResourceLocation getTagId(StageId stageId) {
        return getTagId(stageId.getPath());
    }
}
