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
import net.minecraft.world.level.material.Fluid;
import org.slf4j.Logger;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

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
    // Prevent rapid-fire reloads; only allow one pending reload at a time
    private static final AtomicBoolean reloadPending = new AtomicBoolean(false);

    @Override
    public void register(EmiRegistry registry) {
        LOGGER.info("[ProgressiveStages] EMI plugin registering...");
        initialized = true;

        if (!StageConfig.isEmiEnabled()) {
            LOGGER.info("[ProgressiveStages] EMI integration is disabled in config");
            return;
        }

        // If show_locked_recipes is false AND creative bypass is not active, hide locked items
        if (!StageConfig.isShowLockedRecipes() && !ClientLockCache.isCreativeBypass()) {
            hideLockedStacks(registry);
        }

        LOGGER.info("[ProgressiveStages] EMI integration enabled");
    }


    /**
     * Hide all locked stacks from EMI's index.
     * Uses ClientLockCache (synced from server) with LockRegistry fallback for integrated server.
     *
     * IMPORTANT: We use removeEmiStacks with a predicate to catch ALL NBT variants of locked items.
     * Mods like Mekanism register multiple stacks per item (different stored fluids, chemicals, etc.)
     * and we need to hide all of them, not just the base item.
     */
    private void hideLockedStacks(EmiRegistry registry) {
        // Get all locked items from the client cache (synced from server)
        Map<ResourceLocation, StageId> lockedItems = ClientLockCache.getAllItemLocks();

        // If ClientLockCache is empty, try LockRegistry directly (singleplayer/integrated server)
        if (lockedItems.isEmpty()) {
            LockRegistry reg = LockRegistry.getInstance();
            lockedItems = reg.getAllResolvedItemLocks();
            if (!lockedItems.isEmpty()) {
                LOGGER.info("[ProgressiveStages] ClientLockCache empty, using LockRegistry directly ({} items)", lockedItems.size());
            }
        }

        if (lockedItems.isEmpty()) {
            LOGGER.info("[ProgressiveStages] No lock data available yet, EMI will reload when lock data arrives");
            return;
        }

        Set<StageId> playerStages = ClientStageCache.getStages();

        // Build a set of locked item IDs for fast lookup
        Set<ResourceLocation> lockedItemIds = new java.util.HashSet<>();
        for (var entry : lockedItems.entrySet()) {
            ResourceLocation itemId = entry.getKey();
            StageId requiredStage = entry.getValue();

            // Only add to locked set if player doesn't have the required stage
            if (!playerStages.contains(requiredStage)) {
                lockedItemIds.add(itemId);
            }
        }

        if (lockedItemIds.isEmpty()) {
            LOGGER.info("[ProgressiveStages] EMI: No locked items to hide (player has all required stages)");
            return;
        }

        LOGGER.debug("[ProgressiveStages] Hiding {} locked item types from EMI", lockedItemIds.size());

        // Use predicate-based removal to catch ALL NBT variants of each locked item
        // This is crucial for mods like Mekanism that register multiple stacks per item type
        final Set<ResourceLocation> finalLockedItemIds = lockedItemIds;
        registry.removeEmiStacks(stack -> {
            // Get the underlying ItemStack from the EmiStack
            var itemStack = stack.getItemStack();
            if (itemStack.isEmpty()) {
                return false; // Don't remove empty stacks
            }

            // Check by Item registry ID, ignoring NBT
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(itemStack.getItem());
            return finalLockedItemIds.contains(itemId);
        });

        LOGGER.info("[ProgressiveStages] EMI: Set up removal predicate for {} locked item types (catches all NBT variants)", lockedItemIds.size());

        // Also hide fluids from locked mods
        hideLockedFluids(registry, playerStages);
    }

    /**
     * Hide fluids that are locked.
     * Checks: direct fluid locks, fluid mod locks, general mod locks, and name patterns.
     * Uses predicate-based removal to catch all fluid variants.
     * Respects unlocked_fluids whitelist.
     */
    private void hideLockedFluids(EmiRegistry registry, Set<StageId> playerStages) {
        LockRegistry lockRegistry = LockRegistry.getInstance();

        // Get all types of fluid locks
        Set<ResourceLocation> directFluidLocks = new java.util.HashSet<>();
        Set<String> lockedFluidMods = new java.util.HashSet<>();
        Set<String> lockedMods = new java.util.HashSet<>();
        Map<String, StageId> namePatterns = new java.util.HashMap<>();

        // Direct fluid locks (fluids = ["..."])
        for (var entry : lockRegistry.getAllFluidLocks().entrySet()) {
            if (!playerStages.contains(entry.getValue())) {
                directFluidLocks.add(entry.getKey());
            }
        }

        // Fluid mod locks (fluid_mods = ["..."])
        for (String modId : lockRegistry.getAllLockedFluidMods()) {
            var requiredStage = lockRegistry.getFluidModLockStage(modId);
            if (requiredStage.isPresent() && !playerStages.contains(requiredStage.get())) {
                lockedFluidMods.add(modId.toLowerCase());
            }
        }

        // General mod locks also lock fluids (mods = ["..."])
        for (String modId : lockRegistry.getAllLockedMods()) {
            var requiredStage = lockRegistry.getModLockStage(modId);
            if (requiredStage.isPresent() && !playerStages.contains(requiredStage.get())) {
                lockedMods.add(modId.toLowerCase());
            }
        }

        // Name patterns also lock fluids (names = ["diamond"])
        for (String pattern : lockRegistry.getAllNamePatterns()) {
            var requiredStage = lockRegistry.getNamePatternStage(pattern);
            if (requiredStage.isPresent() && !playerStages.contains(requiredStage.get())) {
                namePatterns.put(pattern.toLowerCase(), requiredStage.get());
            }
        }

        if (directFluidLocks.isEmpty() && lockedFluidMods.isEmpty() && lockedMods.isEmpty() && namePatterns.isEmpty()) {
            return;
        }

        // Get unlocked fluids whitelist
        Set<ResourceLocation> unlockedFluids = lockRegistry.getUnlockedFluids();

        // Use predicate-based removal for fluids
        registry.removeEmiStacks(stack -> {
            // Check if this is a fluid stack by checking if getItemStack is empty
            var itemStack = stack.getItemStack();
            if (!itemStack.isEmpty()) {
                return false; // This is an item, not a fluid
            }

            // Get the fluid ID from the stack
            ResourceLocation id = stack.getId();
            if (id == null) {
                return false;
            }

            // Check fluid whitelist first - don't hide whitelisted fluids
            if (unlockedFluids.contains(id)) {
                return false;
            }

            // Check direct fluid lock
            if (directFluidLocks.contains(id)) {
                return true;
            }

            // Check fluid mod lock and general mod lock
            String modId = id.getNamespace().toLowerCase();
            if (lockedFluidMods.contains(modId) || lockedMods.contains(modId)) {
                return true;
            }

            // Check name patterns (names = ["diamond"] locks fluids containing "diamond")
            String fluidIdStr = id.toString().toLowerCase();
            for (String pattern : namePatterns.keySet()) {
                if (fluidIdStr.contains(pattern)) {
                    return true;
                }
            }

            return false;
        });

        LOGGER.debug("[ProgressiveStages] EMI: Set up fluid removal predicate for {} direct locks, {} fluid mods, {} general mods, {} name patterns",
            directFluidLocks.size(), lockedFluidMods.size(), lockedMods.size(), namePatterns.size());
    }

    /**
     * Trigger EMI to fully reload recipes and stacks.
     * This forces EMI to re-run all plugins including ours,
     * which will re-evaluate what should be hidden based on current stages.
     *
     * Uses EmiReloadManager.reloadRecipes() which is the same path EMI uses
     * when RecipesUpdatedEvent fires — it re-runs all plugin register() calls.
     */
    public static void triggerEmiReload() {
        if (!initialized) {
            LOGGER.debug("[ProgressiveStages] EMI not initialized yet, skipping reload");
            return;
        }

        // Debounce: if a reload is already pending, don't queue another
        if (!reloadPending.compareAndSet(false, true)) {
            LOGGER.debug("[ProgressiveStages] EMI reload already pending, skipping duplicate");
            return;
        }

        try {
            LOGGER.info("[ProgressiveStages] Scheduling EMI reload due to stage/lock change...");

            var minecraft = net.minecraft.client.Minecraft.getInstance();

            // Schedule on the main render thread with a small delay to let data settle
            minecraft.execute(() -> {
                try {
                    reloadPending.set(false);
                    LOGGER.info("[ProgressiveStages] Triggering EMI reload. Current stages: {}", ClientStageCache.getStages());

                    // EmiReloadManager.reloadRecipes() triggers a full EMI reload cycle:
                    // - Clears all data
                    // - Re-runs all plugin register() methods (including ours)
                    // - Rebuilds search index
                    // This is the same codepath as when Minecraft syncs recipes on world join.
                    EmiReloadManager.reloadRecipes();

                    LOGGER.info("[ProgressiveStages] EMI reload triggered successfully");
                } catch (Exception e) {
                    reloadPending.set(false);
                    LOGGER.error("[ProgressiveStages] Failed to trigger EMI reload: {}", e.getMessage());
                }
            });
        } catch (Exception e) {
            reloadPending.set(false);
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
