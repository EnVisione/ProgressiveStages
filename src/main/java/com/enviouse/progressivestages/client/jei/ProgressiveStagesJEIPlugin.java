package com.enviouse.progressivestages.client.jei;

import com.enviouse.progressivestages.client.ClientLockCache;
import com.enviouse.progressivestages.client.ClientStageCache;
import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.config.StageConfig;
import com.enviouse.progressivestages.common.lock.LockRegistry;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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
     * Hide all locked items from JEI.
     *
     * IMPORTANT: We query JEI's IIngredientManager.getAllIngredients() to get ALL registered
     * ItemStacks including NBT variants, then filter by Item registry ID.
     * This catches mods like Mekanism that register multiple stacks per item type.
     */
    private void hideLockedItems() {
        if (ingredientManager == null) {
            return;
        }

        // Build a set of locked item IDs for fast lookup
        var lockedItems = ClientLockCache.getAllItemLocks();
        Set<ResourceLocation> lockedItemIds = new java.util.HashSet<>();

        for (var entry : lockedItems.entrySet()) {
            ResourceLocation itemId = entry.getKey();
            StageId requiredStage = entry.getValue();

            // Only add to locked set if player doesn't have the required stage
            if (!ClientStageCache.hasStage(requiredStage)) {
                lockedItemIds.add(itemId);
            }
        }

        if (lockedItemIds.isEmpty()) {
            LOGGER.info("[ProgressiveStages] JEI: No locked items to hide");
            return;
        }

        // Get ALL registered ItemStacks from JEI (including NBT variants)
        // and filter by Item registry ID
        List<ItemStack> toHide = ingredientManager.getAllIngredients(VanillaTypes.ITEM_STACK)
            .stream()
            .filter(stack -> {
                ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
                return lockedItemIds.contains(itemId);
            })
            .collect(java.util.stream.Collectors.toList());

        if (!toHide.isEmpty()) {
            ingredientManager.removeIngredientsAtRuntime(VanillaTypes.ITEM_STACK, toHide);
            LOGGER.info("[ProgressiveStages] JEI: Hidden {} locked item stacks ({} unique item types)",
                toHide.size(), lockedItemIds.size());
        }

        // Also hide fluids from locked mods
        hideLockedFluids();
    }

    /**
     * Hide fluids that are locked from JEI.
     * Checks: direct fluid locks, fluid mod locks, general mod locks, and name patterns.
     */
    private void hideLockedFluids() {
        if (ingredientManager == null) {
            return;
        }

        LockRegistry lockRegistry = LockRegistry.getInstance();

        // Get all types of fluid locks
        Set<ResourceLocation> directFluidLocks = new java.util.HashSet<>();
        Set<String> lockedFluidMods = new java.util.HashSet<>();
        Set<String> lockedMods = new java.util.HashSet<>();
        Set<String> namePatterns = new java.util.HashSet<>();

        // Direct fluid locks (fluids = ["..."])
        for (var entry : lockRegistry.getAllFluidLocks().entrySet()) {
            if (!ClientStageCache.hasStage(entry.getValue())) {
                directFluidLocks.add(entry.getKey());
            }
        }

        // Fluid mod locks (fluid_mods = ["..."])
        for (String modId : lockRegistry.getAllLockedFluidMods()) {
            var requiredStage = lockRegistry.getFluidModLockStage(modId);
            if (requiredStage.isPresent() && !ClientStageCache.hasStage(requiredStage.get())) {
                lockedFluidMods.add(modId.toLowerCase());
            }
        }

        // General mod locks also lock fluids (mods = ["..."])
        for (String modId : lockRegistry.getAllLockedMods()) {
            var requiredStage = lockRegistry.getModLockStage(modId);
            if (requiredStage.isPresent() && !ClientStageCache.hasStage(requiredStage.get())) {
                lockedMods.add(modId.toLowerCase());
            }
        }

        // Name patterns also lock fluids (names = ["diamond"])
        for (String pattern : lockRegistry.getAllNamePatterns()) {
            var requiredStage = lockRegistry.getNamePatternStage(pattern);
            if (requiredStage.isPresent() && !ClientStageCache.hasStage(requiredStage.get())) {
                namePatterns.add(pattern.toLowerCase());
            }
        }

        if (directFluidLocks.isEmpty() && lockedFluidMods.isEmpty() && lockedMods.isEmpty() && namePatterns.isEmpty()) {
            return;
        }

        // Get unlocked fluids whitelist
        Set<ResourceLocation> unlockedFluids = lockRegistry.getUnlockedFluids();

        List<FluidStack> toHide = new ArrayList<>();

        for (Fluid fluid : BuiltInRegistries.FLUID) {
            ResourceLocation fluidId = BuiltInRegistries.FLUID.getKey(fluid);
            if (fluidId == null) continue;

            // Check fluid whitelist first - don't hide whitelisted fluids
            if (unlockedFluids.contains(fluidId)) {
                continue;
            }

            // Check direct fluid lock
            if (directFluidLocks.contains(fluidId)) {
                toHide.add(new FluidStack(fluid, 1000));
                continue;
            }

            // Check fluid mod lock and general mod lock
            String modId = fluidId.getNamespace().toLowerCase();
            if (lockedFluidMods.contains(modId) || lockedMods.contains(modId)) {
                toHide.add(new FluidStack(fluid, 1000));
                continue;
            }

            // Check name patterns (names = ["diamond"] locks fluids containing "diamond")
            String fluidIdStr = fluidId.toString().toLowerCase();
            for (String pattern : namePatterns) {
                if (fluidIdStr.contains(pattern)) {
                    toHide.add(new FluidStack(fluid, 1000));
                    break;
                }
            }
        }

        if (!toHide.isEmpty()) {
            try {
                ingredientManager.removeIngredientsAtRuntime(mezz.jei.api.neoforge.NeoForgeTypes.FLUID_STACK, toHide);
                LOGGER.debug("[ProgressiveStages] JEI: Hidden {} locked fluids", toHide.size());
            } catch (Exception e) {
                LOGGER.debug("[ProgressiveStages] Failed to hide fluids from JEI: {}", e.getMessage());
            }
        }
    }

    /**
     * Refresh JEI by re-evaluating hidden items based on current stages.
     * Called when stages change.
     *
     * NOTE: For JEI refresh, we need to query ALL ingredients from JEI
     * to properly handle NBT variants.
     */
    public static void refreshJei() {
        if (ingredientManager == null) {
            return;
        }

        try {
            // Build sets of locked and unlocked item IDs
            var lockedItems = ClientLockCache.getAllItemLocks();
            Set<ResourceLocation> lockedItemIds = new java.util.HashSet<>();
            Set<ResourceLocation> unlockedItemIds = new java.util.HashSet<>();

            for (var entry : lockedItems.entrySet()) {
                ResourceLocation itemId = entry.getKey();
                StageId requiredStage = entry.getValue();

                if (ClientStageCache.hasStage(requiredStage)) {
                    unlockedItemIds.add(itemId);
                } else if (!StageConfig.isShowLockedRecipes()) {
                    lockedItemIds.add(itemId);
                }
            }

            // Get ALL registered ItemStacks from JEI and filter
            var allStacks = ingredientManager.getAllIngredients(VanillaTypes.ITEM_STACK);

            List<ItemStack> toHide = allStacks.stream()
                .filter(stack -> {
                    ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
                    return lockedItemIds.contains(itemId);
                })
                .collect(java.util.stream.Collectors.toList());

            // For showing items, we need the stacks that JEI had previously hidden
            // Since we can't easily get those back, we create base stacks
            // (JEI will re-register the full variants on next reload)
            List<ItemStack> toShow = new ArrayList<>();
            for (ResourceLocation itemId : unlockedItemIds) {
                var itemOpt = BuiltInRegistries.ITEM.getOptional(itemId);
                if (itemOpt.isPresent()) {
                    toShow.add(new ItemStack(itemOpt.get()));
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
                LOGGER.debug("[ProgressiveStages] JEI refresh: hidden {} stacks, shown {} items", toHide.size(), toShow.size());
            }

            // Also refresh fluids from locked mods
            refreshLockedFluids();
        } catch (Exception e) {
            LOGGER.debug("[ProgressiveStages] Error refreshing JEI: {}", e.getMessage());
        }
    }

    /**
     * Refresh fluid visibility in JEI based on mod locks
     */
    private static void refreshLockedFluids() {
        if (ingredientManager == null) {
            return;
        }

        var lockedMods = LockRegistry.getInstance().getAllLockedMods();
        if (lockedMods.isEmpty()) {
            return;
        }

        List<FluidStack> toHide = new ArrayList<>();
        List<FluidStack> toShow = new ArrayList<>();

        for (Fluid fluid : BuiltInRegistries.FLUID) {
            ResourceLocation fluidId = BuiltInRegistries.FLUID.getKey(fluid);
            if (fluidId == null) continue;

            String modId = fluidId.getNamespace().toLowerCase();
            var requiredStage = LockRegistry.getInstance().getModLockStage(modId);

            if (requiredStage.isPresent()) {
                FluidStack stack = new FluidStack(fluid, 1000);
                if (ClientStageCache.hasStage(requiredStage.get())) {
                    toShow.add(stack);
                } else if (!StageConfig.isShowLockedRecipes()) {
                    toHide.add(stack);
                }
            }
        }

        try {
            if (!toHide.isEmpty()) {
                ingredientManager.removeIngredientsAtRuntime(mezz.jei.api.neoforge.NeoForgeTypes.FLUID_STACK, toHide);
            }
            if (!toShow.isEmpty()) {
                ingredientManager.addIngredientsAtRuntime(mezz.jei.api.neoforge.NeoForgeTypes.FLUID_STACK, toShow);
            }
        } catch (Exception e) {
            LOGGER.debug("[ProgressiveStages] Error refreshing JEI fluids: {}", e.getMessage());
        }
    }

    /**
     * Check if JEI is available
     */
    public static boolean isJeiAvailable() {
        return jeiRuntime != null;
    }
}
