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

    // Item ID -> Required Stage (single-stage view, kept for back-compat)
    private static final Map<ResourceLocation, StageId> itemLocks = new ConcurrentHashMap<>();

    // Block ID -> Required Stage
    private static final Map<ResourceLocation, StageId> blockLocks = new ConcurrentHashMap<>();

    // Recipe ID -> Required Stage
    private static final Map<ResourceLocation, StageId> recipeLocks = new ConcurrentHashMap<>();

    // Recipe-Item Locks: Output Item ID -> Required Stage (recipe_items = [...])
    private static final Map<ResourceLocation, StageId> recipeItemLocks = new ConcurrentHashMap<>();

    // v2.0 multi-stage variants — populated alongside the single-stage maps; an item with N gating stages
    // has N entries in the per-item Set. Same shape mirrors the recipe families.
    private static final Map<ResourceLocation, java.util.Set<StageId>> itemMultiLocks = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, java.util.Set<StageId>> recipeMultiLocks = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, java.util.Set<StageId>> recipeItemMultiLocks = new ConcurrentHashMap<>();

    // Creative bypass flag - when true, all lock checks return false (not locked)
    private static volatile boolean creativeBypass = false;

    /**
     * Set creative bypass state.
     * When enabled, all lock rendering is suppressed (icons, tooltips, EMI hiding).
     */
    public static void setCreativeBypass(boolean bypassing) {
        boolean changed = creativeBypass != bypassing;
        creativeBypass = bypassing;

        if (StageConfig.isDebugLogging()) {
            LOGGER.info("[ProgressiveStages] Creative bypass: {}", bypassing);
        }

        // Trigger EMI/JEI reload when bypass state changes
        // This is needed because:
        // 1. When entering creative: show all items (currently hidden ones need to appear)
        // 2. When leaving creative: hide locked items again
        if (changed) {
            triggerEmiReload();
        }
    }

    /**
     * Check if creative bypass is active.
     * When active, all items should appear unlocked on the client.
     */
    public static boolean isCreativeBypass() {
        return creativeBypass;
    }

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

    /** v2.0: replace the multi-stage view of item locks. */
    public static void setItemMultiLocks(Map<ResourceLocation, java.util.Set<StageId>> locks) {
        itemMultiLocks.clear();
        if (locks != null) itemMultiLocks.putAll(locks);
    }

    public static void setRecipeMultiLocks(Map<ResourceLocation, java.util.Set<StageId>> locks) {
        recipeMultiLocks.clear();
        if (locks != null) recipeMultiLocks.putAll(locks);
    }

    public static void setRecipeItemMultiLocks(Map<ResourceLocation, java.util.Set<StageId>> locks) {
        recipeItemMultiLocks.clear();
        if (locks != null) recipeItemMultiLocks.putAll(locks);
    }

    /** v2.0: every gating stage for the item, or empty set if not gated. */
    public static java.util.Set<StageId> getRequiredStagesForItem(ResourceLocation itemId) {
        if (creativeBypass || itemId == null) return java.util.Set.of();
        java.util.Set<StageId> set = itemMultiLocks.get(itemId);
        if (set != null) return set;
        StageId single = itemLocks.get(itemId);
        return single != null ? java.util.Set.of(single) : java.util.Set.of();
    }

    public static java.util.Set<StageId> getRequiredStagesForRecipeByOutput(ResourceLocation itemId) {
        if (creativeBypass || itemId == null) return java.util.Set.of();
        java.util.Set<StageId> set = recipeItemMultiLocks.get(itemId);
        if (set != null) return set;
        StageId single = recipeItemLocks.get(itemId);
        return single != null ? java.util.Set.of(single) : java.util.Set.of();
    }

    /** v2.0: true iff the player owns ALL gating stages for the item. */
    public static boolean playerOwnsAllStagesFor(ResourceLocation itemId) {
        java.util.Set<StageId> gating = getRequiredStagesForItem(itemId);
        if (gating.isEmpty()) return true;
        for (StageId s : gating) if (!ClientStageCache.hasStage(s)) return false;
        return true;
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
     * Get the required stage for an item.
     * Returns empty if creative bypass is active.
     */
    public static Optional<StageId> getRequiredStageForItem(ResourceLocation itemId) {
        if (creativeBypass) {
            return Optional.empty();
        }
        return Optional.ofNullable(itemLocks.get(itemId));
    }

    /**
     * Get the required stage for a block.
     * Returns empty if creative bypass is active.
     */
    public static Optional<StageId> getRequiredStageForBlock(ResourceLocation blockId) {
        if (creativeBypass) {
            return Optional.empty();
        }
        return Optional.ofNullable(blockLocks.get(blockId));
    }

    /**
     * Get the required stage for a recipe.
     * Returns empty if creative bypass is active.
     */
    public static Optional<StageId> getRequiredStageForRecipe(ResourceLocation recipeId) {
        if (creativeBypass) {
            return Optional.empty();
        }
        return Optional.ofNullable(recipeLocks.get(recipeId));
    }

    /**
     * Check if an item is locked (has a required stage).
     * Returns false if creative bypass is active.
     */
    public static boolean hasItemLock(ResourceLocation itemId) {
        if (creativeBypass) {
            return false;
        }
        return itemLocks.containsKey(itemId);
    }

    /**
     * Check if a fluid is locked (by checking the LockRegistry for fluid locks).
     * Returns false if creative bypass is active.
     */
    public static boolean isFluidLocked(ResourceLocation fluidId) {
        if (creativeBypass) {
            return false;
        }
        // Check LockRegistry for fluid locks (mods, fluid_mods, direct fluid locks)
        try {
            var requiredStage = com.enviouse.progressivestages.common.lock.LockRegistry.getInstance()
                .getRequiredStageForFluid(fluidId);
            if (requiredStage.isEmpty()) {
                return false;
            }
            // Check if player has the required stage
            return !ClientStageCache.hasStage(requiredStage.get());
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Get all item locks
     */
    public static Map<ResourceLocation, StageId> getAllItemLocks() {
        return Collections.unmodifiableMap(itemLocks);
    }

    /**
     * Get all recipe locks (for EMI recipe hiding)
     */
    public static Map<ResourceLocation, StageId> getAllRecipeLocks() {
        return Collections.unmodifiableMap(recipeLocks);
    }

    /**
     * Set all recipe-item locks (replaces existing).
     * recipe_items = [...] locks ALL recipes whose output matches the item ID.
     */
    public static void setRecipeItemLocks(Map<ResourceLocation, StageId> locks) {
        recipeItemLocks.clear();
        recipeItemLocks.putAll(locks);

        if (StageConfig.isDebugLogging()) {
            LOGGER.info("[ProgressiveStages] Client received {} recipe-item locks", locks.size());
        }
    }

    /**
     * Get all recipe-item locks (for EMI recipe hiding by output item)
     */
    public static Map<ResourceLocation, StageId> getAllRecipeItemLocks() {
        return Collections.unmodifiableMap(recipeItemLocks);
    }

    /**
     * Get the required stage for a recipe-item lock (recipe_items = [...]).
     * Returns empty if creative bypass is active.
     */
    public static Optional<StageId> getRequiredStageForRecipeByOutput(ResourceLocation itemId) {
        if (creativeBypass) {
            return Optional.empty();
        }
        return Optional.ofNullable(recipeItemLocks.get(itemId));
    }

    /**
     * Clear all cached data (on disconnect)
     */
    public static void clear() {
        itemLocks.clear();
        blockLocks.clear();
        recipeLocks.clear();
        recipeItemLocks.clear();
        itemMultiLocks.clear();
        recipeMultiLocks.clear();
        recipeItemMultiLocks.clear();
        creativeBypass = false;

        if (StageConfig.isDebugLogging()) {
            LOGGER.debug("[ProgressiveStages] Cleared client lock cache");
        }
    }

    /**
     * Get total number of locks
     */
    public static int getTotalLockCount() {
        return itemLocks.size() + blockLocks.size() + recipeLocks.size() + recipeItemLocks.size();
    }
}
