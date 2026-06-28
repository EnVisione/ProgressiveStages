package com.enviouse.progressivestages.client;

import com.enviouse.progressivestages.client.emi.ProgressiveStagesEMIPlugin;
import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.config.StageConfig;
import com.enviouse.progressivestages.common.lock.LockRegistry;
import com.enviouse.progressivestages.common.stage.StageOrder;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
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
    private static volatile boolean hideStageNamesFromNonOps = false;

    public static boolean isHideStageNamesFromNonOps() { return hideStageNamesFromNonOps; }
    public static void setHideStageNamesFromNonOps(boolean v) { hideStageNamesFromNonOps = v; }

    // v1.3: Stage definitions with dependencies
    private static final Map<StageId, StageDefinitionData> stageDefinitions = new HashMap<>();

    /**
     * Stage definition data for client-side use.
     *
     * <p>v2.3: extended with description, icon, the resolved per-stage [display] flags (server
     * already folds in the global progressivestages.toml defaults), and whether the stage has
     * auto-grant triggers — everything the tooltip handler and GUI tree viewer need.
     */
    public record StageDefinitionData(StageId id, String displayName, List<StageId> dependencies,
                                      String description, Optional<ResourceLocation> icon,
                                      boolean displayAsUnknownItem, boolean obscureIcon,
                                      boolean showTooltip, boolean showDescriptionOnTooltip,
                                      boolean hasTriggers) {
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

    /** v2.3: every stage id the client knows a definition for (for the GUI tree viewer). */
    public static Set<StageId> getAllStageDefinitionIds() {
        return Collections.unmodifiableSet(stageDefinitions.keySet());
    }

    /**
     * Get display name for a stage (v1.3)
     */
    public static String getDisplayName(StageId stageId) {
        StageDefinitionData def = stageDefinitions.get(stageId);
        return def != null ? def.displayName() : stageId.getPath();
    }

    // ---- v2.3 per-stage display metadata (falls back to the global config when unsynced) ----

    public static String getDescription(StageId stageId) {
        StageDefinitionData def = stageDefinitions.get(stageId);
        return def != null ? def.description() : "";
    }

    public static Optional<ResourceLocation> getIcon(StageId stageId) {
        StageDefinitionData def = stageDefinitions.get(stageId);
        return def != null ? def.icon() : Optional.empty();
    }

    public static boolean isDisplayAsUnknownItem(StageId stageId) {
        StageDefinitionData def = stageDefinitions.get(stageId);
        return def != null ? def.displayAsUnknownItem() : StageConfig.isMaskLockedItemNames();
    }

    public static boolean isObscureIcon(StageId stageId) {
        StageDefinitionData def = stageDefinitions.get(stageId);
        return def != null ? def.obscureIcon() : StageConfig.isObscureLockedItemIcons();
    }

    public static boolean isShowTooltip(StageId stageId) {
        StageDefinitionData def = stageDefinitions.get(stageId);
        return def != null ? def.showTooltip() : StageConfig.isShowTooltip();
    }

    public static boolean isShowDescriptionOnTooltip(StageId stageId) {
        StageDefinitionData def = stageDefinitions.get(stageId);
        return def != null ? def.showDescriptionOnTooltip() : StageConfig.isShowStageDescriptionOnTooltip();
    }

    public static boolean stageHasTriggers(StageId stageId) {
        StageDefinitionData def = stageDefinitions.get(stageId);
        return def != null && def.hasTriggers();
    }

    /**
     * v2.3: true if {@code item} is locked for the player AND at least one of its missing gating
     * stages requests icon obscuring. Used by {@link com.enviouse.progressivestages.client.renderer.LockedItemDecorator}.
     */
    public static boolean shouldObscureItemIcon(Item item) {
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
        if (itemId == null) return false;
        Set<StageId> gating = ClientLockCache.getRequiredStagesForItem(itemId);
        if (gating.isEmpty()) gating = LockRegistry.getInstance().getRequiredStages(item);
        for (StageId s : gating) {
            if (!hasStage(s) && isObscureIcon(s)) return true;
        }
        return false;
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
            triggerFtbQuestsRefresh();
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
            triggerFtbQuestsRefresh();
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
            triggerFtbQuestsRefresh();
        }
    }

    /**
     * Trigger EMI and JEI to reload their recipe/item index.
     * Only triggers if show_locked_recipes is false (meaning items are hidden based on stages).
     */
    private static void triggerEmiReload() {
        // Only refresh if we're hiding locked items - otherwise there's nothing to update
        if (StageConfig.isShowLockedRecipes()) {
            LOGGER.debug("[ProgressiveStages] Stage changed but show_locked_recipes=true, skipping EMI/JEI reload");
            return;
        }

        LOGGER.info("[ProgressiveStages] Stage change detected, scheduling EMI/JEI reload...");

        // Trigger EMI reload (it will handle its own thread scheduling)
        try {
            ProgressiveStagesEMIPlugin.triggerEmiReload();
        } catch (NoClassDefFoundError e) {
            // EMI not installed - ignore
        } catch (Exception e) {
            // Ignore other errors
        }

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
     * Trigger FTB Quests QuestScreen to refresh its chapter and quest panels.
     * This is required because FTB Quests does not automatically re-evaluate
     * chapter/quest visibility when stages change — the ChapterPanel only
     * rebuilds its widget list when explicitly told to.
     *
     * Without this, chapters gated behind stages will hide correctly on first
     * evaluation, but will NOT unhide when the player gains the required stage.
     *
     * Uses reflection because FTB Quests is a compileOnly dependency.
     */
    private static void triggerFtbQuestsRefresh() {
        try {
            var minecraft = net.minecraft.client.Minecraft.getInstance();
            var screen = minecraft.screen;
            if (screen == null) return;

            Class<?> questScreenClass = Class.forName("dev.ftb.mods.ftbquests.client.gui.quests.QuestScreen");
            if (questScreenClass.isInstance(screen)) {
                LOGGER.debug("[ProgressiveStages] Refreshing FTB Quests screen after stage change");
                questScreenClass.getMethod("refreshChapterPanel").invoke(screen);
                questScreenClass.getMethod("refreshQuestPanel").invoke(screen);
            }
        } catch (ClassNotFoundException e) {
            // FTB Quests not installed - expected, ignore silently
        } catch (Exception e) {
            // Ignore errors (screen might be closing, reflection issues, etc.)
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
     * v2.0: multi-stage aware — locked if the player is missing ANY required stage.
     * Falls back to single-stage view if multi-stage data isn't available.
     */
    public static boolean isItemLocked(Item item) {
        var itemId = BuiltInRegistries.ITEM.getKey(item);
        if (itemId == null) return false;

        // v2.0: check multi-stage data first (populated by network sync)
        Set<StageId> gating = ClientLockCache.getRequiredStagesForItem(itemId);
        if (!gating.isEmpty()) {
            for (StageId s : gating) if (!hasStage(s)) return true;
            return false;
        }

        // Fallback to LockRegistry (integrated server / singleplayer)
        Set<StageId> registryGating = LockRegistry.getInstance().getRequiredStages(item);
        if (registryGating.isEmpty()) return false;
        for (StageId s : registryGating) if (!hasStage(s)) return true;
        return false;
    }

    /**
     * Check if an item is locked for the client by ResourceLocation.
     * v2.0: multi-stage aware.
     */
    public static boolean isItemLocked(net.minecraft.resources.ResourceLocation itemId) {
        if (itemId == null) {
            return false;
        }

        // v2.0: check multi-stage data first (populated by network sync)
        Set<StageId> gating = ClientLockCache.getRequiredStagesForItem(itemId);
        if (!gating.isEmpty()) {
            for (StageId s : gating) if (!hasStage(s)) return true;
            return false;
        }

        // Fallback to LockRegistry (integrated server / singleplayer)
        var itemOpt = BuiltInRegistries.ITEM.getOptional(itemId);
        if (itemOpt.isPresent()) {
            Set<StageId> registryGating = LockRegistry.getInstance().getRequiredStages(itemOpt.get());
            if (registryGating.isEmpty()) return false;
            for (StageId s : registryGating) if (!hasStage(s)) return true;
            return false;
        }
        return false;
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
        // v2.3 fix: use the client-synced definition count, not StageOrder. StageOrder is only
        // populated on the server JVM; on a dedicated server the client's StageOrder is empty so
        // this previously rendered "X/0". stageDefinitions is synced via StageDefinitionsSyncPayload.
        // Fall back to StageOrder for the integrated-server/singleplayer case where it's populated.
        int total = stageDefinitions.isEmpty()
            ? StageOrder.getInstance().getStageCount()
            : stageDefinitions.size();
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
