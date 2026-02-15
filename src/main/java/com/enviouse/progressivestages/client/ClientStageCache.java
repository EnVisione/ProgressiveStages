package com.enviouse.progressivestages.client;

import com.enviouse.progressivestages.client.emi.ProgressiveStagesEMIPlugin;
import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.config.StageConfig;
import com.enviouse.progressivestages.common.lock.LockRegistry;
import com.enviouse.progressivestages.common.stage.StageOrder;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import org.slf4j.Logger;

import java.util.*;

/**
 * Client-side cache for stage data synced from server
 */
public class ClientStageCache {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Set<StageId> stages = new HashSet<>();
    private static StageId currentStage = null;

    // v1.3: Stage definitions with dependencies
    private static final Map<StageId, StageDefinitionData> stageDefinitions = new HashMap<>();

    /**
     * Stage definition data for client-side use (v1.3)
     */
    public record StageDefinitionData(StageId id, String displayName, List<StageId> dependencies) {
    }

    /**
     * Set stage definitions (v1.3)
     */
    public static void setStageDefinitions(Map<StageId, StageDefinitionData> definitions) {
        stageDefinitions.clear();
        stageDefinitions.putAll(definitions);
        LOGGER.info("[ProgressiveStages] Client cached {} stage definitions", definitions.size());
    }

    /**
     * Get stage definition by ID (v1.3)
     */
    public static Optional<StageDefinitionData> getStageDefinition(StageId stageId) {
        return Optional.ofNullable(stageDefinitions.get(stageId));
    }

    /**
     * Get dependencies for a stage (v1.3)
     */
    public static List<StageId> getDependencies(StageId stageId) {
        StageDefinitionData def = stageDefinitions.get(stageId);
        return def != null ? def.dependencies() : Collections.emptyList();
    }

    /**
     * Get display name for a stage (v1.3)
     */
    public static String getDisplayName(StageId stageId) {
        StageDefinitionData def = stageDefinitions.get(stageId);
        return def != null ? def.displayName() : stageId.getPath();
    }

    /**
     * Set all stages (replaces existing)
     */
    public static void setStages(Set<StageId> newStages) {
        boolean changed = !stages.equals(newStages);
        stages.clear();
        stages.addAll(newStages);
        updateCurrentStage();

        // Debug logging
        if (StageConfig.isDebugLogging()) {
            LOGGER.info("[ProgressiveStages] Client received stage sync: {} stages - {}",
                newStages.size(), newStages);
        }

        // Trigger EMI reload if stages changed
        if (changed) {
            triggerEmiReload();
        }
    }

    /**
     * Add a single stage
     */
    public static void addStage(StageId stageId) {
        boolean changed = stages.add(stageId);
        updateCurrentStage();

        // Debug logging
        if (StageConfig.isDebugLogging()) {
            LOGGER.info("[ProgressiveStages] Client added stage: {}", stageId);
        }

        // Trigger EMI reload if stage was added
        if (changed) {
            triggerEmiReload();
        }
    }

    /**
     * Remove a single stage
     */
    public static void removeStage(StageId stageId) {
        boolean changed = stages.remove(stageId);
        updateCurrentStage();

        // Debug logging
        if (StageConfig.isDebugLogging()) {
            LOGGER.info("[ProgressiveStages] Client removed stage: {}", stageId);
        }

        // Trigger EMI reload if stage was removed
        if (changed) {
            triggerEmiReload();
        }
    }

    /**
     * Trigger EMI and JEI to reload their recipe/item index
     */
    private static void triggerEmiReload() {
        LOGGER.info("[ProgressiveStages] Stage change detected, scheduling EMI/JEI reload...");

        // Trigger EMI reload (it will handle its own thread scheduling)
        ProgressiveStagesEMIPlugin.triggerEmiReload();

        // Trigger JEI refresh
        try {
            com.enviouse.progressivestages.client.jei.ProgressiveStagesJEIPlugin.refreshJei();
        } catch (NoClassDefFoundError e) {
            // JEI not installed - ignore
        } catch (Exception e) {
            // Ignore other errors
        }
    }

    /**
     * Check if client has a specific stage
     */
    public static boolean hasStage(StageId stageId) {
        return stages.contains(stageId);
    }

    /**
     * Get all stages
     */
    public static Set<StageId> getStages() {
        return Collections.unmodifiableSet(stages);
    }

    /**
     * Get the current (highest) stage
     */
    public static Optional<StageId> getCurrentStage() {
        return Optional.ofNullable(currentStage);
    }

    /**
     * Check if an item is locked for the client.
     * Uses ClientLockCache for lock data (synced from server) and local stage cache.
     */
    public static boolean isItemLocked(Item item) {
        // Try ClientLockCache first (synced from server)
        var itemId = BuiltInRegistries.ITEM.getKey(item);
        Optional<StageId> requiredStage = ClientLockCache.getRequiredStageForItem(itemId);

        // Fallback to LockRegistry for integrated server/singleplayer
        if (requiredStage.isEmpty()) {
            requiredStage = LockRegistry.getInstance().getRequiredStage(item);
        }

        if (requiredStage.isEmpty()) {
            return false;
        }
        return !hasStage(requiredStage.get());
    }

    /**
     * Get the required stage for an item (if locked).
     * Uses ClientLockCache for lock data (synced from server) and local stage cache.
     */
    public static Optional<StageId> getRequiredStageForItem(Item item) {
        // Try ClientLockCache first (synced from server)
        var itemId = BuiltInRegistries.ITEM.getKey(item);
        Optional<StageId> requiredStage = ClientLockCache.getRequiredStageForItem(itemId);

        // Fallback to LockRegistry for integrated server/singleplayer
        if (requiredStage.isEmpty()) {
            requiredStage = LockRegistry.getInstance().getRequiredStage(item);
        }

        if (requiredStage.isEmpty()) {
            return Optional.empty();
        }
        if (hasStage(requiredStage.get())) {
            return Optional.empty(); // Not locked for us
        }
        return requiredStage;
    }

    /**
     * Get the progress string (e.g., "2/5")
     */
    public static String getProgressString() {
        int total = StageOrder.getInstance().getStageCount();
        return stages.size() + "/" + total;
    }

    /**
     * Clear all cached data (on disconnect)
     */
    public static void clear() {
        stages.clear();
        currentStage = null;
        stageDefinitions.clear();
        ClientLockCache.clear();
    }

    private static void updateCurrentStage() {
        // v1.3: With dependency-based system, just pick any stage if we have any
        // The concept of "highest" stage doesn't really apply anymore
        currentStage = stages.isEmpty() ? null : stages.iterator().next();
    }

}
