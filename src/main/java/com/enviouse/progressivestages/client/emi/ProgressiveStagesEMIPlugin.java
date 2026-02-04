package com.enviouse.progressivestages.client.emi;

import com.enviouse.progressivestages.client.ClientLockCache;
import com.enviouse.progressivestages.client.ClientStageCache;
import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.config.StageConfig;
import com.enviouse.progressivestages.common.config.StageDefinition;
import com.enviouse.progressivestages.common.lock.LockRegistry;
import com.enviouse.progressivestages.common.stage.StageOrder;
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

/**
 * EMI plugin entrypoint for ProgressiveStages
 *
 * This plugin handles:
 * - Hiding locked items/recipes from EMI when show_locked_recipes = false
 * - Triggering EMI reload when stages change
 *
 * Stage browsing uses dynamic tags (#progressivestages:iron_age) built from
 * the TOML stage files - no static tag JSONs needed.
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

        LOGGER.info("[ProgressiveStages] Processing {} locked item definitions", lockedItems.size());
        LOGGER.info("[ProgressiveStages] Player has stages: {}", ClientStageCache.getStages());

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
     * This will cause EMI to re-run all plugins including ours,
     * which will re-evaluate what should be hidden based on current stages.
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
            new Thread(() -> {
                try {
                    Thread.sleep(250); // Wait 250ms for stage data to be fully processed
                    minecraft.execute(() -> {
                        try {
                            LOGGER.info("[ProgressiveStages] Current stages: {}", ClientStageCache.getStages());
                            EmiReloadManager.reloadRecipes();
                            LOGGER.info("[ProgressiveStages] EMI reload completed");
                        } catch (Exception e) {
                            LOGGER.error("[ProgressiveStages] Failed to trigger EMI reload: {}", e.getMessage());
                        }
                    });
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }).start();
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
