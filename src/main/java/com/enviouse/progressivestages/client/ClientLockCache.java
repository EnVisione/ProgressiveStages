package com.enviouse.progressivestages.client;

import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.config.StageConfig;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

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
    private static final Map<ResourceLocation, java.util.Set<StageId>> blockMultiLocks = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, java.util.Set<StageId>> fluidMultiLocks = new ConcurrentHashMap<>();
    private static final Map<String, java.util.Set<StageId>> modMultiLocks = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, java.util.Set<StageId>> recipeMultiLocks = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, java.util.Set<StageId>> recipeItemMultiLocks = new ConcurrentHashMap<>();
    /** v3.0: entity id → gating stages (for the Jade/WTHIT overlay on locked mobs). */
    private static final Map<ResourceLocation, java.util.Set<StageId>> entityMultiLocks = new ConcurrentHashMap<>();

    private static <K> Map<K, java.util.Set<StageId>> copyMultiLocks(
            Map<K, java.util.Set<StageId>> locks) {
        if (locks == null || locks.isEmpty()) return Map.of();
        Map<K, java.util.Set<StageId>> copy = new java.util.LinkedHashMap<>();
        locks.forEach((key, stages) -> {
            if (key != null && stages != null && !stages.isEmpty()) {
                copy.put(key, java.util.Set.copyOf(stages));
            }
        });
        return copy;
    }

    public static void setEntityMultiLocks(Map<ResourceLocation, java.util.Set<StageId>> locks) {
        Map<ResourceLocation, java.util.Set<StageId>> copy = copyMultiLocks(locks);
        entityMultiLocks.clear();
        entityMultiLocks.putAll(copy);
    }

    /** Gating stages for an entity id ({@link java.util.Set#of()} when not gated or creative-bypassing). */
    public static java.util.Set<StageId> getRequiredStagesForEntity(ResourceLocation entityId) {
        if (creativeBypass || entityId == null) return java.util.Set.of();
        java.util.Set<StageId> set = entityMultiLocks.get(entityId);
        return set != null ? set : java.util.Set.of();
    }

    /** True iff the player owns ALL gating stages for this entity (so it isn't locked for them). */
    public static boolean playerOwnsAllStagesForEntity(ResourceLocation entityId) {
        java.util.Set<StageId> gating = getRequiredStagesForEntity(entityId);
        if (gating.isEmpty()) return true;
        for (StageId s : gating) if (!ClientStageCache.hasStage(s)) return false;
        return true;
    }

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
        if (locks == null) locks = Map.of();
        boolean changed = !itemLocks.equals(locks);
        itemLocks.clear();
        itemLocks.putAll(locks);

        if (StageConfig.isDebugLogging()) {
            LOGGER.info("[ProgressiveStages] Client received {} item locks", locks.size());
        }

        // Trigger EMI reload when lock data changes
        if (changed) {
            triggerEmiReload();
        }
    }

    /** v2.0: replace the multi-stage view of item locks. */
    public static void setItemMultiLocks(Map<ResourceLocation, java.util.Set<StageId>> locks) {
        Map<ResourceLocation, java.util.Set<StageId>> copy = copyMultiLocks(locks);
        itemMultiLocks.clear();
        itemMultiLocks.putAll(copy);
    }

    public static void setBlockMultiLocks(Map<ResourceLocation, java.util.Set<StageId>> locks) {
        Map<ResourceLocation, java.util.Set<StageId>> copy = copyMultiLocks(locks);
        blockMultiLocks.clear();
        blockMultiLocks.putAll(copy);
    }

    public static void setFluidMultiLocks(Map<ResourceLocation, java.util.Set<StageId>> locks) {
        Map<ResourceLocation, java.util.Set<StageId>> copy = copyMultiLocks(locks);
        boolean changed = !fluidMultiLocks.equals(copy);
        fluidMultiLocks.clear();
        fluidMultiLocks.putAll(copy);
        if (changed) triggerEmiReload();
    }

    public static void setModMultiLocks(Map<String, java.util.Set<StageId>> locks) {
        Map<String, java.util.Set<StageId>> normalized = new java.util.LinkedHashMap<>();
        copyMultiLocks(locks).forEach((key, stages) ->
            normalized.put(key.toLowerCase(java.util.Locale.ROOT), stages));
        boolean changed = !modMultiLocks.equals(normalized);
        modMultiLocks.clear();
        modMultiLocks.putAll(normalized);
        if (changed) triggerEmiReload();
    }

    public static void setRecipeMultiLocks(Map<ResourceLocation, java.util.Set<StageId>> locks) {
        Map<ResourceLocation, java.util.Set<StageId>> copy = copyMultiLocks(locks);
        recipeMultiLocks.clear();
        recipeMultiLocks.putAll(copy);
    }

    public static void setRecipeItemMultiLocks(Map<ResourceLocation, java.util.Set<StageId>> locks) {
        Map<ResourceLocation, java.util.Set<StageId>> copy = copyMultiLocks(locks);
        recipeItemMultiLocks.clear();
        recipeItemMultiLocks.putAll(copy);
    }

    /** v2.0: every gating stage for the item, or empty set if not gated. */
    public static java.util.Set<StageId> getRequiredStagesForItem(ResourceLocation itemId) {
        if (creativeBypass || itemId == null) return java.util.Set.of();
        java.util.Set<StageId> set = itemMultiLocks.get(itemId);
        if (set != null) return set;
        StageId single = itemLocks.get(itemId);
        return single != null ? java.util.Set.of(single) : java.util.Set.of();
    }

    public static java.util.Set<StageId> getRequiredStagesForBlock(ResourceLocation blockId) {
        if (creativeBypass || blockId == null) return java.util.Set.of();
        java.util.Set<StageId> set = blockMultiLocks.get(blockId);
        if (set != null) return set;
        StageId single = blockLocks.get(blockId);
        return single != null ? java.util.Set.of(single) : java.util.Set.of();
    }

    public static java.util.Set<StageId> getRequiredStagesForFluid(ResourceLocation fluidId) {
        if (creativeBypass || fluidId == null) return java.util.Set.of();
        return fluidMultiLocks.getOrDefault(fluidId, java.util.Set.of());
    }

    public static java.util.Set<StageId> getRequiredStagesForMod(String modId) {
        if (creativeBypass || modId == null) return java.util.Set.of();
        return modMultiLocks.getOrDefault(modId.toLowerCase(java.util.Locale.ROOT), java.util.Set.of());
    }

    public static boolean isModLocked(String modId) {
        for (StageId stage : getRequiredStagesForMod(modId)) {
            if (!ClientStageCache.hasStage(stage)) return true;
        }
        return false;
    }

    public static java.util.Set<String> getLockedModIds() {
        if (creativeBypass) return java.util.Set.of();
        java.util.Set<String> out = new java.util.HashSet<>();
        for (String modId : modMultiLocks.keySet()) if (isModLocked(modId)) out.add(modId);
        return java.util.Set.copyOf(out);
    }

    /** Complete server-resolved block lock view. Values are immutable snapshots per entry. */
    public static Map<ResourceLocation, java.util.Set<StageId>> getAllBlockMultiLocks() {
        return Map.copyOf(blockMultiLocks);
    }

    /** Complete server-resolved fluid lock view. Values are immutable snapshots per entry. */
    public static Map<ResourceLocation, java.util.Set<StageId>> getAllFluidMultiLocks() {
        return Map.copyOf(fluidMultiLocks);
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

    /** v2.0: true iff the player owns ALL gating stages for this item's recipe-output lock ([recipes].locked_items). */
    public static boolean playerOwnsAllStagesForRecipeOutput(ResourceLocation itemId) {
        java.util.Set<StageId> gating = getRequiredStagesForRecipeByOutput(itemId);
        if (gating.isEmpty()) return true;
        for (StageId s : gating) if (!ClientStageCache.hasStage(s)) return false;
        return true;
    }

    /**
     * Item IDs whose crafting recipes are locked for the player ([recipes].locked_items) and
     * which should therefore be hidden from EMI/JEI (the item AND its recipes) until unlocked.
     * Multi-stage aware; honors creative bypass. Falls back to LockRegistry on the integrated
     * server before the lock-sync packet has populated the client cache.
     */
    public static java.util.Set<ResourceLocation> getRecipeOutputLockedItemIds() {
        java.util.Set<ResourceLocation> out = new java.util.HashSet<>();
        if (creativeBypass) return out;
        if (!recipeItemLocks.isEmpty()) {
            for (ResourceLocation id : recipeItemLocks.keySet()) {
                if (!playerOwnsAllStagesForRecipeOutput(id)) out.add(id);
            }
            return out;
        }
        // Integrated-server fallback (client cache not yet synced from the server) — multi-stage
        // aware so the pre-sync decision matches the post-sync one (item gated by N stages is
        // hidden unless the player owns ALL N).
        try {
            var reg = com.enviouse.progressivestages.common.lock.LockRegistry.getInstance();
            for (net.minecraft.world.item.Item item : net.minecraft.core.registries.BuiltInRegistries.ITEM) {
                ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item);
                if (id == null) continue;
                java.util.Set<StageId> gating = reg.getRequiredStagesForRecipeByOutput(item);
                if (gating.isEmpty()) continue;
                boolean ownsAll = true;
                for (StageId s : gating) if (!ClientStageCache.hasStage(s)) { ownsAll = false; break; }
                if (!ownsAll) out.add(id);
            }
        } catch (Throwable ignored) {}
        return out;
    }

    /**
     * Trigger EMI and JEI to reload
     */
    private static void triggerEmiReload() {
        try {
            com.enviouse.progressivestages.client.emi.ProgressiveStagesEMIPlugin.triggerEmiReload();
        } catch (Throwable e) {
            // Ignore — EMI may not be loaded. MUST catch Throwable, not just Exception: when EMI is
            // absent, class-loading ProgressiveStagesEMIPlugin (it implements EmiPlugin) throws
            // NoClassDefFoundError (an Error), which would otherwise crash the recipe-viewer reload
            // and make EMI behave like a hard dependency.
        }
        try {
            // Coalesce: scheduleRefresh() dedupes multiple calls fired during a single lock_sync
            // packet (setItemLocks + setRecipeItemLocks + ... each call here) into ONE deferred
            // two-pass refresh on the next client tick, instead of running the heavy synchronous
            // refreshJei() N times back-to-back.
            com.enviouse.progressivestages.client.jei.ProgressiveStagesJEIPlugin.scheduleRefresh();
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
        if (locks == null) locks = Map.of();
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
        if (locks == null) locks = Map.of();
        boolean changed = !recipeLocks.equals(locks);
        recipeLocks.clear();
        recipeLocks.putAll(locks);

        if (StageConfig.isDebugLogging()) {
            LOGGER.info("[ProgressiveStages] Client received {} recipe locks", locks.size());
        }
        if (changed) triggerEmiReload();
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
        java.util.Set<StageId> synced = getRequiredStagesForFluid(fluidId);
        if (!synced.isEmpty()) {
            for (StageId stage : synced) if (!ClientStageCache.hasStage(stage)) return true;
            return false;
        }
        // Integrated-server fallback before the initial sync packet arrives.
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
        return Map.copyOf(itemLocks);
    }

    /**
     * Get all recipe locks (for EMI recipe hiding)
     */
    public static Map<ResourceLocation, StageId> getAllRecipeLocks() {
        return Map.copyOf(recipeLocks);
    }

    /**
     * Set all recipe-item locks (replaces existing).
     * recipe_items = [...] locks ALL recipes whose output matches the item ID.
     */
    public static void setRecipeItemLocks(Map<ResourceLocation, StageId> locks) {
        if (locks == null) locks = Map.of();
        boolean changed = !recipeItemLocks.equals(locks);
        recipeItemLocks.clear();
        recipeItemLocks.putAll(locks);

        if (StageConfig.isDebugLogging()) {
            LOGGER.info("[ProgressiveStages] Client received {} recipe-item locks", locks.size());
        }

        // Trigger an EMI/JEI reload on ANY change (including clear-to-empty, so formerly-hidden
        // recipe-output items reappear) — there may be no plain [items] locks to trigger
        // setItemLocks' reload. triggerEmiReload is cheap and guarded by EMI/JEI presence.
        if (changed) {
            triggerEmiReload();
        }
    }

    /**
     * Get all recipe-item locks (for EMI recipe hiding by output item)
     */
    public static Map<ResourceLocation, StageId> getAllRecipeItemLocks() {
        return Map.copyOf(recipeItemLocks);
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
        boolean changed = creativeBypass || !itemLocks.isEmpty() || !recipeLocks.isEmpty()
            || !recipeItemLocks.isEmpty() || !fluidMultiLocks.isEmpty() || !modMultiLocks.isEmpty();
        itemLocks.clear();
        blockLocks.clear();
        recipeLocks.clear();
        recipeItemLocks.clear();
        itemMultiLocks.clear();
        blockMultiLocks.clear();
        fluidMultiLocks.clear();
        modMultiLocks.clear();
        recipeMultiLocks.clear();
        recipeItemMultiLocks.clear();
        entityMultiLocks.clear();
        creativeBypass = false;

        if (changed) triggerEmiReload();

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
