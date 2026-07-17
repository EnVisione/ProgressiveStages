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
            hideLockedRecipes(registry);
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

        Set<StageId> playerStages = ClientStageCache.getStages();

        // Build a set of locked item IDs for fast lookup.
        // v2.0: multi-stage — locked iff player is missing ANY gating stage.
        Set<ResourceLocation> lockedItemIds = new java.util.HashSet<>();
        for (var entry : lockedItems.entrySet()) {
            ResourceLocation itemId = entry.getKey();

            if (!ClientLockCache.playerOwnsAllStagesFor(itemId)) {
                lockedItemIds.add(itemId);
            }
        }

        // Bug fix: also hide items whose crafting recipe is gated via [recipes].locked_items.
        // Removing the output item from EMI's index drops the item AND all its recipes, so a
        // recipe-locked item can't be browsed or its recipe seen (it's still server-blocked from
        // crafting). recipe-id locks (locked_ids) are handled separately in hideLockedRecipes().
        lockedItemIds.addAll(ClientLockCache.getRecipeOutputLockedItemIds());

        if (lockedItemIds.isEmpty()) {
            // No items to hide, but fluids may still be locked — fall through to the fluid pass.
            hideLockedFluids(registry, playerStages);
            return;
        }

        LOGGER.debug("[ProgressiveStages] Hiding {} locked item types from EMI", lockedItemIds.size());

        // Build a parallel set of blocked mod ids (mods the player hasn't unlocked).
        // Used as a fallback class-based check for EmiStack subclasses whose registry id
        // is e.g. "minecraft:" but whose Java class lives inside a blocked mod's module.
        Set<String> blockedModIds = new java.util.HashSet<>();
        LockRegistry _reg = LockRegistry.getInstance();
        for (String modId : _reg.getAllLockedMods()) {
            var requiredStage = _reg.getModLockStage(modId);
            if (requiredStage.isPresent() && !playerStages.contains(requiredStage.get())) {
                blockedModIds.add(modId.toLowerCase(java.util.Locale.ROOT));
            }
        }
        // Dedicated servers do not share their LockRegistry with the client. The synced,
        // server-resolved view is authoritative there (and already honors per-stage unlocks).
        blockedModIds.addAll(ClientLockCache.getLockedModIds());

        // Use predicate-based removal to catch ALL NBT variants of each locked item
        // This is crucial for mods like Mekanism that register multiple stacks per item type
        final Set<ResourceLocation> finalLockedItemIds = lockedItemIds;
        final Set<String> finalBlockedModIds = blockedModIds;
        registry.removeEmiStacks(stack -> {
            // Get the underlying ItemStack from the EmiStack
            var itemStack = stack.getItemStack();
            if (!itemStack.isEmpty()) {
                // Real item stack: the resolved item-lock set is AUTHORITATIVE here and already
                // honors always_unlocked / per-stage [unlocks]. We intentionally do NOT fall
                // through to the mod-class heuristic below for item stacks — doing so would hide
                // whitelisted items whose Java Item class happens to live inside a blocked mod's
                // module (e.g. Create's WrenchItem, BeltConnectorItem, large_water_wheel,
                // mechanical_press/mixer), while sibling blocks that use vanilla BlockItem
                // (shaft, cogwheel, gearbox…) slip through — the exact bug this guard fixes.
                // JEI already does this (its mod-class fallback skips VanillaTypes.ITEM_STACK).
                ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(itemStack.getItem());
                return finalLockedItemIds.contains(itemId);
            }

            // Class-based fallback — ONLY for non-item EmiStacks (getItemStack() is empty), e.g.
            // Mekanism gases/chemicals/pigments whose registry id is "minecraft:" but whose class
            // lives inside a blocked mod's module. These have no item id to match against, so we
            // gate them by the owning mod of their Java class instead.
            if (!finalBlockedModIds.isEmpty()) {
                try {
                    var stackOwner = com.enviouse.progressivestages.compat.recipeviewer.RecipeViewerModHints.owningModIdForClass(stack.getClass());
                    if (stackOwner.isPresent() && finalBlockedModIds.contains(stackOwner.get().toLowerCase(java.util.Locale.ROOT))) {
                        return true;
                    }
                    Object key = stack.getKey();
                    if (key != null) {
                        var keyOwner = com.enviouse.progressivestages.compat.recipeviewer.RecipeViewerModHints.owningModIdForClass(key.getClass());
                        if (keyOwner.isPresent() && finalBlockedModIds.contains(keyOwner.get().toLowerCase(java.util.Locale.ROOT))) {
                            return true;
                        }
                    }
                } catch (Throwable ignored) {
                    // EmiStack subclasses vary; never fail the predicate
                }
            }

            return false;
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
        for (var entry : ClientLockCache.getAllFluidMultiLocks().entrySet()) {
            for (StageId required : entry.getValue()) {
                if (!playerStages.contains(required)) {
                    directFluidLocks.add(entry.getKey());
                    break;
                }
            }
        }

        // Fluid mod locks (fluid_mods = ["..."])
        for (String modId : lockRegistry.getAllLockedFluidMods()) {
            var requiredStage = lockRegistry.getFluidModLockStage(modId);
            if (requiredStage.isPresent() && !playerStages.contains(requiredStage.get())) {
                lockedFluidMods.add(modId.toLowerCase(java.util.Locale.ROOT));
            }
        }

        // General mod locks also lock fluids (mods = ["..."])
        for (String modId : lockRegistry.getAllLockedMods()) {
            var requiredStage = lockRegistry.getModLockStage(modId);
            if (requiredStage.isPresent() && !playerStages.contains(requiredStage.get())) {
                lockedMods.add(modId.toLowerCase(java.util.Locale.ROOT));
            }
        }
        lockedMods.addAll(ClientLockCache.getLockedModIds());

        // Name patterns also lock fluids (names = ["diamond"])
        for (String pattern : lockRegistry.getAllNamePatterns()) {
            var requiredStage = lockRegistry.getNamePatternStage(pattern);
            if (requiredStage.isPresent() && !playerStages.contains(requiredStage.get())) {
                namePatterns.put(pattern.toLowerCase(java.util.Locale.ROOT), requiredStage.get());
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
            String modId = id.getNamespace().toLowerCase(java.util.Locale.ROOT);
            if (lockedFluidMods.contains(modId) || lockedMods.contains(modId)) {
                return true;
            }

            // Check name patterns (names = ["diamond"] locks fluids containing "diamond")
            String fluidIdStr = id.toString().toLowerCase(java.util.Locale.ROOT);
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
     * Hide recipes locked by RECIPE ID ([recipes].locked_ids) for the current player.
     *
     * <p>Recipe-OUTPUT locks ([recipes].locked_items) and plain [items] locks are handled in
     * hideLockedStacks(), which removes the output item from EMI's index entirely (taking all
     * its recipes with it). This method covers the remaining case: a specific recipe gated by
     * its ID, where the output item itself stays visible (it may have other, unlocked recipes).
     */
    private void hideLockedRecipes(EmiRegistry registry) {
        LockRegistry lockReg = LockRegistry.getInstance();
        Map<ResourceLocation, StageId> recipeLocks = lockReg.getAllRecipeLocks();
        Map<ResourceLocation, StageId> recipeItemLocks = lockReg.getAllRecipeItemLocks();

        // Also check client cache for locks synced from server (dedicated server support)
        Map<ResourceLocation, StageId> clientRecipeLocks = ClientLockCache.getAllRecipeLocks();
        Map<ResourceLocation, StageId> clientRecipeItemLocks = ClientLockCache.getAllRecipeItemLocks();

        // Merge server-side and client-cached locks
        Map<ResourceLocation, StageId> allRecipeLocks = new java.util.HashMap<>(recipeLocks);
        allRecipeLocks.putAll(clientRecipeLocks);

        Map<ResourceLocation, StageId> allRecipeItemLocks = new java.util.HashMap<>(recipeItemLocks);
        allRecipeItemLocks.putAll(clientRecipeItemLocks);

        if (allRecipeLocks.isEmpty() && allRecipeItemLocks.isEmpty()) {
            return;
        }

        Set<StageId> playerStages = ClientStageCache.getStages();

        // Build set of recipe IDs the player doesn't have the stage for
        Set<ResourceLocation> lockedRecipeIds = new java.util.HashSet<>();
        for (var entry : allRecipeLocks.entrySet()) {
            if (!playerStages.contains(entry.getValue())) {
                lockedRecipeIds.add(entry.getKey());
            }
        }

        // Build set of locked output item IDs ([recipes].locked_items) — multi-stage aware via the
        // shared helper (same source hideLockedStacks uses to hide the output items themselves).
        Set<ResourceLocation> lockedRecipeItemIds = ClientLockCache.getRecipeOutputLockedItemIds();

        if (lockedRecipeIds.isEmpty() && lockedRecipeItemIds.isEmpty()) {
            return;
        }

        final Set<ResourceLocation> finalLockedRecipeIds = lockedRecipeIds;
        final Set<ResourceLocation> finalLockedRecipeItemIds = lockedRecipeItemIds;
        registry.removeRecipes(recipe -> {
            ResourceLocation recipeId = recipe.getId();

            // Check direct recipe ID lock
            if (recipeId != null && finalLockedRecipeIds.contains(recipeId)) {
                return true;
            }

            // Check recipe-item lock: remove recipe if any output item is in the locked set
            if (!finalLockedRecipeItemIds.isEmpty()) {
                for (var output : recipe.getOutputs()) {
                    var outputStack = output.getItemStack();
                    if (!outputStack.isEmpty()) {
                        ResourceLocation outputId = BuiltInRegistries.ITEM.getKey(outputStack.getItem());
                        if (finalLockedRecipeItemIds.contains(outputId)) {
                            return true;
                        }
                    }
                }
            }

            return false;
        });

        LOGGER.debug("[ProgressiveStages] EMI: Removed locked recipe(s) ({} by ID, {} by output item)",
            lockedRecipeIds.size(), lockedRecipeItemIds.size());
    }

    /**
     * Trigger EMI to fully reload recipes and stacks.
     * This forces EMI to re-run all plugins including ours,
     * which will re-evaluate what should be hidden based on current stages.
     *
     * Uses EmiReloadManager.reload() which directly starts the reload worker thread.
     * Note: reloadRecipes() cannot be used here because it uses an internal bitmask
     * that waits for BOTH reloadTags() (bit 1) AND reloadRecipes() (bit 2) to be called
     * before triggering the actual reload. Since we're doing a programmatic refresh
     * (not a server-driven tag+recipe resync), reload() bypasses that gate.
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

                    // Use EmiReloadManager.reload() directly to force a full EMI reload.
                    // IMPORTANT: reloadRecipes() does NOT reload on its own — it sets bit 2
                    // of an internal bitmask and waits for reloadTags() (bit 1) before calling
                    // reload(). Since we only need to re-evaluate lock visibility (not a full
                    // tag+recipe resync from the server), we call reload() directly to bypass
                    // the bitmask gate. This clears all data, re-runs all plugin register()
                    // methods (including ours), and rebuilds the search index.
                    EmiReloadManager.reload();

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
     * Get the required stage for an item (for display purposes).
     *
     * @deprecated v2.0: items can now be locked by multiple stages. This returns only
     *             the FIRST gating stage; use {@link LockRegistry#getRequiredStages(Item)}
     *             for the full set when displaying multi-stage requirements.
     */
    @Deprecated
    public static Optional<StageId> getRequiredStage(Item item) {
        return LockRegistry.getInstance().getRequiredStage(item);
    }

    /**
     * Get the display name for a stage
     */
    public static String getStageDisplayName(StageId stageId) {
        // v2.3 fix: read from ClientStageCache (synced from server), not StageOrder, which is
        // empty on a dedicated-server client. getDisplayName already falls back to getPath().
        return ClientStageCache.getDisplayName(stageId);
    }

    /**
     * Check if EMI is initialized
     */
    public static boolean isInitialized() {
        return initialized;
    }
}
