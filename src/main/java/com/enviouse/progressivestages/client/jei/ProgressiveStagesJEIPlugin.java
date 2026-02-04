package com.enviouse.progressivestages.client.jei;

import com.enviouse.progressivestages.client.ClientLockCache;
import com.enviouse.progressivestages.client.ClientStageCache;
import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.config.StageConfig;
import com.enviouse.progressivestages.common.util.Constants;
import com.mojang.logging.LogUtils;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * JEI plugin for ProgressiveStages
 *
 * Handles hiding locked items from JEI's ingredient list when configured.
 */
@JeiPlugin
public class ProgressiveStagesJEIPlugin implements IModPlugin {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation PLUGIN_ID = ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "jei_plugin");

    private static IJeiRuntime jeiRuntime = null;
    private static IIngredientManager ingredientManager = null;

    @Override
    public ResourceLocation getPluginUid() {
        return PLUGIN_ID;
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        LOGGER.info("[ProgressiveStages] JEI runtime available");
        jeiRuntime = runtime;
        ingredientManager = runtime.getIngredientManager();

        // Hide locked items if configured
        if (!StageConfig.isShowLockedRecipes()) {
            hideLockedItems();
        }
    }

    @Override
    public void onRuntimeUnavailable() {
        jeiRuntime = null;
        ingredientManager = null;
    }

    /**
     * Hide all locked items from JEI
     */
    private void hideLockedItems() {
        if (ingredientManager == null) {
            return;
        }

        List<ItemStack> toHide = new ArrayList<>();
        var lockedItems = ClientLockCache.getAllItemLocks();

        for (var entry : lockedItems.entrySet()) {
            ResourceLocation itemId = entry.getKey();
            StageId requiredStage = entry.getValue();

            // Check if player has this stage
            if (!ClientStageCache.hasStage(requiredStage)) {
                Item item = BuiltInRegistries.ITEM.get(itemId);
                if (item != null) {
                    toHide.add(new ItemStack(item));
                }
            }
        }

        if (!toHide.isEmpty()) {
            ingredientManager.removeIngredientsAtRuntime(VanillaTypes.ITEM_STACK, toHide);
            LOGGER.info("[ProgressiveStages] Hidden {} locked items from JEI", toHide.size());
        }
    }

    /**
     * Refresh JEI by re-evaluating hidden items based on current stages
     * Called when stages change
     */
    public static void refreshJei() {
        if (ingredientManager == null) {
            return;
        }

        try {
            // Get all locked items
            var lockedItems = ClientLockCache.getAllItemLocks();
            List<ItemStack> toHide = new ArrayList<>();
            List<ItemStack> toShow = new ArrayList<>();

            for (var entry : lockedItems.entrySet()) {
                ResourceLocation itemId = entry.getKey();
                StageId requiredStage = entry.getValue();
                Item item = BuiltInRegistries.ITEM.get(itemId);

                if (item != null) {
                    ItemStack stack = new ItemStack(item);
                    if (ClientStageCache.hasStage(requiredStage)) {
                        // Player has stage - show item
                        toShow.add(stack);
                    } else if (!StageConfig.isShowLockedRecipes()) {
                        // Player doesn't have stage and config says hide - hide item
                        toHide.add(stack);
                    }
                }
            }

            // Apply changes
            if (!toHide.isEmpty()) {
                ingredientManager.removeIngredientsAtRuntime(VanillaTypes.ITEM_STACK, toHide);
            }
            if (!toShow.isEmpty()) {
                ingredientManager.addIngredientsAtRuntime(VanillaTypes.ITEM_STACK, toShow);
            }

            if (!toHide.isEmpty() || !toShow.isEmpty()) {
                LOGGER.debug("[ProgressiveStages] JEI refresh: hidden {}, shown {}", toHide.size(), toShow.size());
            }
        } catch (Exception e) {
            LOGGER.debug("[ProgressiveStages] Error refreshing JEI: {}", e.getMessage());
        }
    }

    /**
     * Check if JEI is available
     */
    public static boolean isJeiAvailable() {
        return jeiRuntime != null;
    }
}
