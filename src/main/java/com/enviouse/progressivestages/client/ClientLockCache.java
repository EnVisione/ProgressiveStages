package com.enviouse.progressivestages.client;

import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.config.StageConfig;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side cache for lock registry data synced from server.
 * This stores the mapping of item IDs to their required stages.
 */
public class ClientLockCache {

    private static final Logger LOGGER = LogUtils.getLogger();

    // Item ID -> Required Stage
    private static final Map<ResourceLocation, StageId> itemLocks = new ConcurrentHashMap<>();

    // Block ID -> Required Stage
    private static final Map<ResourceLocation, StageId> blockLocks = new ConcurrentHashMap<>();

    // Recipe ID -> Required Stage
    private static final Map<ResourceLocation, StageId> recipeLocks = new ConcurrentHashMap<>();

    /**
     * Set all item locks (replaces existing)
     */
    public static void setItemLocks(Map<ResourceLocation, StageId> locks) {
        boolean changed = !itemLocks.equals(locks);
        itemLocks.clear();
        itemLocks.putAll(locks);

        if (StageConfig.isDebugLogging()) {
            LOGGER.info("[ProgressiveStages] Client received {} item locks", locks.size());
        }

        // Trigger EMI reload when lock data changes
        if (changed && !locks.isEmpty()) {
            triggerEmiReload();
        }
    }

    /**
     * Trigger EMI and JEI to reload
     */
    private static void triggerEmiReload() {
        try {
            com.enviouse.progressivestages.client.emi.ProgressiveStagesEMIPlugin.triggerEmiReload();
        } catch (Exception e) {
            // Ignore - EMI may not be loaded
        }
        try {
            com.enviouse.progressivestages.client.jei.ProgressiveStagesJEIPlugin.refreshJei();
        } catch (NoClassDefFoundError e) {
            // JEI not installed - ignore
        } catch (Exception e) {
            // Ignore
        }
    }

    /**
     * Set all block locks (replaces existing)
     */
    public static void setBlockLocks(Map<ResourceLocation, StageId> locks) {
        blockLocks.clear();
        blockLocks.putAll(locks);

        if (StageConfig.isDebugLogging()) {
            LOGGER.info("[ProgressiveStages] Client received {} block locks", locks.size());
        }
    }

    /**
     * Set all recipe locks (replaces existing)
     */
    public static void setRecipeLocks(Map<ResourceLocation, StageId> locks) {
        recipeLocks.clear();
        recipeLocks.putAll(locks);

        if (StageConfig.isDebugLogging()) {
            LOGGER.info("[ProgressiveStages] Client received {} recipe locks", locks.size());
        }
    }

    /**
     * Get the required stage for an item
     */
    public static Optional<StageId> getRequiredStageForItem(ResourceLocation itemId) {
        return Optional.ofNullable(itemLocks.get(itemId));
    }

    /**
     * Get the required stage for a block
     */
    public static Optional<StageId> getRequiredStageForBlock(ResourceLocation blockId) {
        return Optional.ofNullable(blockLocks.get(blockId));
    }

    /**
     * Get the required stage for a recipe
     */
    public static Optional<StageId> getRequiredStageForRecipe(ResourceLocation recipeId) {
        return Optional.ofNullable(recipeLocks.get(recipeId));
    }

    /**
     * Check if an item is locked (has a required stage)
     */
    public static boolean hasItemLock(ResourceLocation itemId) {
        return itemLocks.containsKey(itemId);
    }

    /**
     * Get all item locks
     */
    public static Map<ResourceLocation, StageId> getAllItemLocks() {
        return Collections.unmodifiableMap(itemLocks);
    }

    /**
     * Clear all cached data (on disconnect)
     */
    public static void clear() {
        itemLocks.clear();
        blockLocks.clear();
        recipeLocks.clear();

        if (StageConfig.isDebugLogging()) {
            LOGGER.debug("[ProgressiveStages] Cleared client lock cache");
        }
    }

    /**
     * Get total number of locks
     */
    public static int getTotalLockCount() {
        return itemLocks.size() + blockLocks.size() + recipeLocks.size();
    }
}
