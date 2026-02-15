package com.enviouse.progressivestages.common.tags;

import com.enviouse.progressivestages.common.util.Constants;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import org.slf4j.Logger;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Provides dynamic item tags for stages.
 *
 * Since Minecraft tags are loaded before the world, we can't add actual
 * Forge tags dynamically from world-specific TOML files.
 *
 * Instead, this class provides a lookup that EMI can use to filter by stage.
 * The StageTagRegistry builds the mapping, and this class provides tag-like
 * functionality for searching.
 *
 * Usage in EMI search: #progressivestages:iron_age
 * This is handled by having items match the tag pattern via our integration.
 */
@EventBusSubscriber(modid = Constants.MOD_ID)
public final class DynamicTagProvider {

    private static final Logger LOGGER = LogUtils.getLogger();

    // Cache of created TagKeys for stages
    private static final Map<String, TagKey<Item>> stageTagKeys = new ConcurrentHashMap<>();

    private DynamicTagProvider() {}

    /**
     * Get or create a TagKey for a stage.
     * These are "virtual" tags - they exist as TagKeys but won't be in Minecraft's registry
     * unless we inject them.
     */
    public static TagKey<Item> getStageTagKey(String stagePath) {
        return stageTagKeys.computeIfAbsent(stagePath, path ->
            TagKey.create(Registries.ITEM,
                ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, path)));
    }

    /**
     * Check if an item is in a stage "tag" (via our dynamic registry)
     */
    public static boolean isItemInStageTag(Item item, String stagePath) {
        return StageTagRegistry.isItemInStage(item, stagePath);
    }

    /**
     * Get all stage tag paths that are currently defined
     */
    public static Set<String> getDefinedStagePaths() {
        return StageTagRegistry.getAllStagePaths();
    }

    /**
     * Get all items for a stage tag
     */
    public static Set<Item> getItemsForStageTag(String stagePath) {
        return StageTagRegistry.getItemsForStage(stagePath);
    }

    /**
     * Called when tags are updated - we log for debugging but our tags
     * are built from TOML files, not from the tag registry.
     */
    @SubscribeEvent
    public static void onTagsUpdated(TagsUpdatedEvent event) {
        LOGGER.debug("[ProgressiveStages] Tags updated event - dynamic stage tags are built from TOML files");
    }
}
