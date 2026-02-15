package com.enviouse.progressivestages.client.emi;

import com.enviouse.progressivestages.client.ClientLockCache;
import com.enviouse.progressivestages.client.ClientStageCache;
import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.config.StageConfig;
import com.enviouse.progressivestages.common.config.StageDefinition;
import com.enviouse.progressivestages.common.lock.LockRegistry;
import com.enviouse.progressivestages.common.stage.StageOrder;
import com.enviouse.progressivestages.common.util.Constants;
import com.mojang.logging.LogUtils;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.runtime.EmiReloadManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import org.slf4j.Logger;

import java.util.Optional;
import java.util.Set;

/**
 * EMI plugin entrypoint for ProgressiveStages
 *
 * This plugin handles:
 * - Hiding locked items/recipes from EMI when show_locked_recipes = false
 * - Triggering EMI reload when stages change
 *
 * Note: Stage tags (e.g., #progressivestages:iron_age) are provided through
 * NeoForge's dynamic tag system, not through EMI's registry.
 */
@EmiEntrypoint
public class ProgressiveStagesEMIPlugin implements EmiPlugin {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static boolean initialized = false;

    @Override
    public void register(EmiRegistry registry) {
        LOGGER.info("[ProgressiveStages] EMI plugin registering...");
        initialized = true;

        if (!StageConfig.isEmiEnabled()) {
            LOGGER.info("[ProgressiveStages] EMI integration is disabled in config");
            return;
        }

        // Note: Dynamic stage tags (like #progressivestages:iron_age) are handled through
        // NeoForge's tag system, not EMI's registry. EMI automatically picks up item tags.
        // See StageTagProvider for datapack-based tag generation.

        // If show_locked_recipes is false, hide all locked items from EMI index
        if (!StageConfig.isShowLockedRecipes()) {
            hideLockedStacks(registry);
        }

        LOGGER.info("[ProgressiveStages] EMI integration enabled");
    }


    /**
     * Hide all locked stacks from EMI's index
     */
    private void hideLockedStacks(EmiRegistry registry) {
        int hiddenCount = 0;
        int unlockedCount = 0;

        // Get all locked items from the client cache
        var lockedItems = ClientLockCache.getAllItemLocks();

        LOGGER.debug("[ProgressiveStages] Processing {} locked item definitions", lockedItems.size());
        LOGGER.debug("[ProgressiveStages] Player has stages: {}", ClientStageCache.getStages());

        for (var entry : lockedItems.entrySet()) {
            ResourceLocation itemId = entry.getKey();
            StageId requiredStage = entry.getValue();

            // Check if player has this stage
            boolean hasStage = ClientStageCache.hasStage(requiredStage);

            if (!hasStage) {
                // Get the item and hide it
                Item item = BuiltInRegistries.ITEM.get(itemId);
                if (item != null) {
                    EmiStack stack = EmiStack.of(item);
                    if (!stack.isEmpty()) {
                        registry.removeEmiStacks(stack);
                        hiddenCount++;
                    }
                }
            } else {
                unlockedCount++;
            }
        }

        LOGGER.info("[ProgressiveStages] Hidden {} locked items, {} items unlocked", hiddenCount, unlockedCount);
    }

    /**
     * Trigger EMI to fully reload recipes and stacks.
     * This forces EMI to re-run all plugins including ours,
     * which will re-evaluate what should be hidden based on current stages.
     *
     * Multiple approaches are tried:
     * 1. EmiReloadManager.reload() - full reload
     * 2. Clear EmiSearch cache to force rebuild
     * 3. Trigger reloadRecipes() + reloadTags()
     */
    public static void triggerEmiReload() {
        if (!initialized) {
            LOGGER.debug("[ProgressiveStages] EMI not initialized yet, skipping reload");
            return;
        }

        try {
            LOGGER.info("[ProgressiveStages] Scheduling EMI reload due to stage change...");

            var minecraft = net.minecraft.client.Minecraft.getInstance();

            // Use a delayed task to ensure stage data is fully processed
            minecraft.execute(() -> {
                try {
                    LOGGER.info("[ProgressiveStages] Current stages: {}", ClientStageCache.getStages());

                    // Approach 1: Try to clear EmiSearch to force index rebuild
                    try {
                        Class<?> emiSearchClass = Class.forName("dev.emi.emi.search.EmiSearch");
                        // Try to clear the search index
                        try {
                            var clearMethod = emiSearchClass.getMethod("clear");
                            clearMethod.invoke(null);
                            LOGGER.info("[ProgressiveStages] Cleared EmiSearch index");
                        } catch (NoSuchMethodException e) {
                            // Try alternative - bake() to rebuild
                            try {
                                var bakeMethod = emiSearchClass.getMethod("bake");
                                bakeMethod.invoke(null);
                                LOGGER.info("[ProgressiveStages] Called EmiSearch.bake()");
                            } catch (NoSuchMethodException e2) {
                                // Neither method exists
                            }
                        }
                    } catch (ClassNotFoundException e) {
                        LOGGER.debug("[ProgressiveStages] EmiSearch class not found");
                    }

                    // Approach 2: Try EmiReloadManager.reload()
                    try {
                        Class<?> reloadManagerClass = Class.forName("dev.emi.emi.runtime.EmiReloadManager");

                        // Try reload() method first (full reload)
                        try {
                            var reloadMethod = reloadManagerClass.getMethod("reload");
                            reloadMethod.invoke(null);
                            LOGGER.info("[ProgressiveStages] EMI full reload triggered via reload()");
                        } catch (NoSuchMethodException e) {
                            // reload() doesn't exist
                        }

                        // Fallback: trigger both recipes and tags reload
                        EmiReloadManager.reloadRecipes();
                        try {
                            var reloadTagsMethod = reloadManagerClass.getMethod("reloadTags");
                            reloadTagsMethod.invoke(null);
                            LOGGER.info("[ProgressiveStages] Called reloadTags()");
                        } catch (NoSuchMethodException e) {
                            // reloadTags doesn't exist
                        }

                    } catch (ClassNotFoundException e) {
                        LOGGER.warn("[ProgressiveStages] EmiReloadManager class not found");
                    }

                    // Approach 3: Try to invalidate EmiScreenManager's cached index
                    try {
                        Class<?> screenManagerClass = Class.forName("dev.emi.emi.screen.EmiScreenManager");
                        // Look for any methods that might refresh the view
                        for (var method : screenManagerClass.getMethods()) {
                            if (method.getName().contains("refresh") || method.getName().contains("invalidate") || method.getName().contains("clear")) {
                                if (method.getParameterCount() == 0) {
                                    try {
                                        method.invoke(null);
                                        LOGGER.info("[ProgressiveStages] Called EmiScreenManager.{}()", method.getName());
                                    } catch (Exception ex) {
                                        // Ignore
                                    }
                                }
                            }
                        }
                    } catch (ClassNotFoundException e) {
                        LOGGER.debug("[ProgressiveStages] EmiScreenManager class not found");
                    }

                    LOGGER.info("[ProgressiveStages] EMI reload sequence completed");
                } catch (Exception e) {
                    LOGGER.error("[ProgressiveStages] Failed to trigger EMI reload: {}", e.getMessage());
                    e.printStackTrace();
                }
            });
        } catch (Exception e) {
            LOGGER.error("[ProgressiveStages] Failed to schedule EMI reload: {}", e.getMessage());
        }
    }

    /**
     * Check if an EmiStack is locked for the current player
     */
    public static boolean isStackLocked(EmiStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        Item item = stack.getItemStack().getItem();
        return ClientStageCache.isItemLocked(item);
    }

    /**
     * Get the required stage for an item (for display purposes)
     */
    public static Optional<StageId> getRequiredStage(Item item) {
        return LockRegistry.getInstance().getRequiredStage(item);
    }

    /**
     * Get the display name for a stage
     */
    public static String getStageDisplayName(StageId stageId) {
        return StageOrder.getInstance().getStageDefinition(stageId)
            .map(StageDefinition::getDisplayName)
            .orElse(stageId.getPath());
    }

    /**
     * Check if EMI is initialized
     */
    public static boolean isInitialized() {
        return initialized;
    }
}
