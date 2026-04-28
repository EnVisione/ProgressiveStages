package com.enviouse.progressivestages.client.jei;

import com.enviouse.progressivestages.client.ClientLockCache;
import com.enviouse.progressivestages.client.ClientStageCache;
import com.enviouse.progressivestages.common.config.StageConfig;
import com.enviouse.progressivestages.common.lock.LockRegistry;
import com.enviouse.progressivestages.common.util.Constants;
import com.enviouse.progressivestages.compat.recipeviewer.RecipeViewerModHints;
import com.mojang.logging.LogUtils;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * JEI plugin for ProgressiveStages
 *
 * Handles hiding locked items from JEI's ingredient list when configured.
 */
@JeiPlugin
public class ProgressiveStagesJEIPlugin implements IModPlugin {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation PLUGIN_ID = ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "jei_plugin");
    // Matches namespace:path pairs embedded in a JEI uid string (e.g. "fluid:create:potion/awkward")
    private static final Pattern UID_NAMESPACE_RE = Pattern.compile("([a-z0-9_.-]+):([a-z0-9_./-]+)");

    private static IJeiRuntime jeiRuntime = null;
    private static IIngredientManager ingredientManager = null;
    private static final AtomicBoolean refreshQueued = new AtomicBoolean(false);
    private static boolean listenerRegistered = false;

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

        // Register an ingredient listener so we react to live add/remove notifications
        // (e.g., reloads, dynamic recipes). Some JEI builds don't expose this — be defensive.
        if (!listenerRegistered) {
            try {
                ingredientManager.registerIngredientListener(new IIngredientManager.IIngredientListener() {
                    @Override public <V> void onIngredientsAdded(IIngredientHelper<V> helper, Collection<ITypedIngredient<V>> ingredients) {
                        scheduleRefresh();
                    }
                    @Override public <V> void onIngredientsRemoved(IIngredientHelper<V> helper, Collection<ITypedIngredient<V>> ingredients) {
                        scheduleRefresh();
                    }
                });
                listenerRegistered = true;
                LOGGER.debug("[ProgressiveStages] Registered JEI ingredient listener");
            } catch (Throwable t) {
                LOGGER.debug("[ProgressiveStages] Could not register JEI ingredient listener (API surface differs): {}", t.getMessage());
            }
        }

        // After registration, do a coalesced two-pass refresh so the second pass reapplies
        // hides that JEI cleared during onIngredientsAdded.
        scheduleRefresh();
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
     *
     * v2.0: multi-stage aware — an item is hidden if the player is missing ANY required stage.
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

            // v2.0 multi-stage: hidden if missing ANY gating stage (uses multi-stage view if synced)
            if (!ClientLockCache.playerOwnsAllStagesFor(itemId)) {
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

        // Multi-FluidStack-type pass (NeoForgeTypes.FLUID_STACK above only covers one
        // registered type — JEI may have additional fluid-typed ingredient types).
        hideLockedFluidsAllTypes(directFluidLocks, lockedFluidMods, lockedMods, namePatterns, unlockedFluids);

        // Generic ingredient types (Mekanism gases/pigments, library types) — gated by display-mod.
        hideLockedGenericIngredientType_allModBlocked(lockedMods);
    }

    private void hideLockedGenericIngredientType_allModBlocked(Set<String> blockedMods) {
        if (ingredientManager == null || blockedMods.isEmpty()) return;
        try {
            for (IIngredientType<?> type : ingredientManager.getRegisteredIngredientTypes()) {
                hideLockedGenericIngredientType(type, blockedMods);
            }
        } catch (Throwable t) {
            LOGGER.debug("[ProgressiveStages] generic-type sweep failed: {}", t.getMessage());
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
            // v2.0: multi-stage — locked iff player is missing ANY gating stage.
            var lockedItems = ClientLockCache.getAllItemLocks();
            Set<ResourceLocation> lockedItemIds = new java.util.HashSet<>();
            Set<ResourceLocation> unlockedItemIds = new java.util.HashSet<>();

            for (var entry : lockedItems.entrySet()) {
                ResourceLocation itemId = entry.getKey();

                if (ClientLockCache.playerOwnsAllStagesFor(itemId)) {
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

            // Also refresh fluids from locked mods (single-type quick path)
            refreshLockedFluids();

            // Multi-FluidStack-type + generic-ingredient sweep using the same data the
            // initial-pass uses. Wrapped to never break the refresh.
            try {
                LockRegistry _reg = LockRegistry.getInstance();
                Set<ResourceLocation> directFluidLocks = new java.util.HashSet<>();
                Set<String> lockedFluidMods = new java.util.HashSet<>();
                Set<String> lockedMods = new java.util.HashSet<>();
                Set<String> namePatterns = new java.util.HashSet<>();
                Set<ResourceLocation> unlockedFluids = _reg.getUnlockedFluids();

                for (var e : _reg.getAllFluidLocks().entrySet()) {
                    if (!ClientStageCache.hasStage(e.getValue())) directFluidLocks.add(e.getKey());
                }
                for (String modId : _reg.getAllLockedFluidMods()) {
                    var rs = _reg.getFluidModLockStage(modId);
                    if (rs.isPresent() && !ClientStageCache.hasStage(rs.get())) lockedFluidMods.add(modId.toLowerCase());
                }
                for (String modId : _reg.getAllLockedMods()) {
                    var rs = _reg.getModLockStage(modId);
                    if (rs.isPresent() && !ClientStageCache.hasStage(rs.get())) lockedMods.add(modId.toLowerCase());
                }
                for (String pattern : _reg.getAllNamePatterns()) {
                    var rs = _reg.getNamePatternStage(pattern);
                    if (rs.isPresent() && !ClientStageCache.hasStage(rs.get())) namePatterns.add(pattern.toLowerCase());
                }
                hideLockedFluidsAllTypes(directFluidLocks, lockedFluidMods, lockedMods, namePatterns, unlockedFluids);
                if (!lockedMods.isEmpty()) {
                    for (IIngredientType<?> type : ingredientManager.getRegisteredIngredientTypes()) {
                        hideLockedGenericIngredientType(type, lockedMods);
                    }
                }
            } catch (Throwable t) {
                LOGGER.debug("[ProgressiveStages] refreshJei extended sweep failed: {}", t.getMessage());
            }

            // Force JEI to rebuild its filter so removed entries actually disappear.
            notifyJeiIngredientFilter(jeiRuntime);
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

    /**
     * Coalesce simultaneous refresh calls and run two passes — the second exists because
     * JEI's IngredientBlacklistInternal.onIngredientsAdded clears blacklist for newly-added
     * stacks, which would un-hide locked items added during the first pass.
     */
    public static void scheduleRefresh() {
        if (!refreshQueued.compareAndSet(false, true)) return;
        try {
            Minecraft mc = Minecraft.getInstance();
            mc.execute(() -> {
                refreshQueued.set(false);
                try { refreshJei(); } catch (Throwable t) { LOGGER.debug("[ProgressiveStages] JEI first-pass refresh failed: {}", t.getMessage()); }
                // Second pass — JEI clears blacklist on add, so reapply.
                mc.execute(() -> {
                    try { refreshJei(); } catch (Throwable t) { LOGGER.debug("[ProgressiveStages] JEI second-pass refresh failed: {}", t.getMessage()); }
                    notifyJeiIngredientFilter(jeiRuntime);
                });
            });
        } catch (Throwable t) {
            refreshQueued.set(false);
            LOGGER.debug("[ProgressiveStages] Failed to schedule JEI refresh: {}", t.getMessage());
        }
    }

    /**
     * Force JEI to rebuild its internal IngredientFilter. Reflective so we don't compile-couple
     * to mezz.jei.gui.ingredients.IngredientFilter (impl module, may shift between versions).
     */
    private static void notifyJeiIngredientFilter(IJeiRuntime r) {
        if (r == null) return;
        try {
            Object filter = r.getIngredientFilter();
            if (filter == null) return;
            try { filter.getClass().getMethod("rebuildItemFilter").invoke(filter); } catch (Throwable ignored) {}
            try { filter.getClass().getMethod("updateHidden").invoke(filter); } catch (Throwable ignored) {}
        } catch (Throwable ignored) {}
    }

    @SuppressWarnings("unchecked")
    private static boolean isFluidStackIngredientType(IIngredientType<?> t) {
        try { return FluidStack.class.isAssignableFrom(t.getIngredientClass()); } catch (Throwable ignored) { return false; }
    }

    /**
     * For ingredient types that aren't ITEM_STACK or FluidStack (Mekanism gases/pigments,
     * library/pseudo types), gate by display-mod and class-owning-mod.
     */
    private static <V> void hideLockedGenericIngredientType(IIngredientType<V> type, Set<String> blockedMods) {
        if (ingredientManager == null || blockedMods.isEmpty()) return;
        try {
            if (type == VanillaTypes.ITEM_STACK) return;
            if (isFluidStackIngredientType(type)) return;
            Collection<V> all = ingredientManager.getAllIngredients(type);
            if (all == null || all.isEmpty()) return;
            IIngredientHelper<V> helper = ingredientManager.getIngredientHelper(type);
            List<V> toHide = new ArrayList<>();
            for (V ing : all) {
                if (ing == null) continue;
                String displayMod = null;
                try { displayMod = helper.getDisplayModId(ing); } catch (Throwable ignored) {}
                if (displayMod != null && blockedMods.contains(displayMod.toLowerCase())) {
                    toHide.add(ing);
                    continue;
                }
                var owner = RecipeViewerModHints.owningModIdForClass(ing.getClass());
                if (owner.isPresent() && blockedMods.contains(owner.get().toLowerCase())) {
                    toHide.add(ing);
                }
            }
            if (!toHide.isEmpty()) {
                ingredientManager.removeIngredientsAtRuntime(type, toHide);
                LOGGER.debug("[ProgressiveStages] JEI: hid {} generic ingredient(s) of type {}", toHide.size(), type.getIngredientClass().getSimpleName());
            }
        } catch (Throwable t) {
            LOGGER.debug("[ProgressiveStages] hideLockedGenericIngredientType failed: {}", t.getMessage());
        }
    }

    /**
     * Iterate every registered FluidStack-typed IIngredientType and apply the same hide logic
     * (so we don't miss NeoForgeTypes vs vanilla fluids vs other registered fluid types).
     */
    @SuppressWarnings("unchecked")
    private static void hideLockedFluidsAllTypes(Set<ResourceLocation> directFluidLocks,
                                                 Set<String> lockedFluidMods,
                                                 Set<String> lockedMods,
                                                 Set<String> namePatterns,
                                                 Set<ResourceLocation> unlockedFluids) {
        if (ingredientManager == null) return;
        try {
            for (IIngredientType<?> raw : ingredientManager.getRegisteredIngredientTypes()) {
                if (!isFluidStackIngredientType(raw)) continue;
                IIngredientType<FluidStack> type = (IIngredientType<FluidStack>) raw;
                try {
                    Collection<FluidStack> all = ingredientManager.getAllIngredients(type);
                    if (all == null || all.isEmpty()) continue;
                    IIngredientHelper<FluidStack> helper = ingredientManager.getIngredientHelper(type);
                    List<FluidStack> toHide = new ArrayList<>();
                    for (FluidStack fs : all) {
                        if (fs == null || fs.isEmpty()) continue;
                        ResourceLocation rl = fluidIngredientId(helper, fs);
                        if (rl != null && unlockedFluids.contains(rl)) continue;

                        boolean blocked = false;
                        if (rl != null && directFluidLocks.contains(rl)) blocked = true;
                        if (!blocked && rl != null) {
                            String ns = rl.getNamespace().toLowerCase();
                            if (lockedFluidMods.contains(ns) || lockedMods.contains(ns)) blocked = true;
                        }
                        if (!blocked && rl != null) {
                            String s = rl.toString().toLowerCase();
                            for (String p : namePatterns) {
                                if (s.contains(p)) { blocked = true; break; }
                            }
                        }
                        // UID-embedded namespace scan — catches Create potions whose Fluid is minecraft:water
                        if (!blocked) {
                            String uid = null;
                            try { uid = helper.getUniqueId(fs, UidContext.Ingredient); } catch (Throwable ignored) {}
                            if (uid != null && uidDeclaresBlocked(uid, lockedMods, directFluidLocks)) {
                                blocked = true;
                            }
                        }
                        if (blocked) toHide.add(fs);
                    }
                    if (!toHide.isEmpty()) {
                        ingredientManager.removeIngredientsAtRuntime(type, toHide);
                        LOGGER.debug("[ProgressiveStages] JEI: hid {} fluid stack(s) of type {}", toHide.size(), type.getIngredientClass().getSimpleName());
                    }
                } catch (Throwable t) {
                    LOGGER.debug("[ProgressiveStages] Failed processing fluid type {}: {}", raw, t.getMessage());
                }
            }
        } catch (Throwable t) {
            LOGGER.debug("[ProgressiveStages] hideLockedFluidsAllTypes failed: {}", t.getMessage());
        }
    }

    private static ResourceLocation fluidIngredientId(IIngredientHelper<FluidStack> helper, FluidStack fs) {
        if (fs == null || fs.isEmpty()) return null;
        try {
            ResourceLocation rl = helper.getResourceLocation(fs);
            if (rl != null) return rl;
        } catch (Throwable ignored) {}
        Fluid fluid = fs.getFluid();
        if (fluid == null) return null;
        return BuiltInRegistries.FLUID.getKey(fluid);
    }

    /**
     * Strip a leading "fluid:" if present and walk every namespace:path substring through
     * the regex. If any matches a blocked mod or full id, treat the parent as blocked.
     */
    private static boolean uidDeclaresBlocked(String uid, Set<String> blockedMods, Set<ResourceLocation> blockedIds) {
        if (uid == null || uid.isEmpty()) return false;
        String s = uid.startsWith("fluid:") ? uid.substring("fluid:".length()) : uid;
        Matcher m = UID_NAMESPACE_RE.matcher(s);
        while (m.find()) {
            String ns = m.group(1);
            String path = m.group(2);
            if (ns != null && blockedMods.contains(ns.toLowerCase())) return true;
            try {
                ResourceLocation rl = ResourceLocation.fromNamespaceAndPath(ns, path);
                if (blockedIds.contains(rl)) return true;
            } catch (Throwable ignored) {}
        }
        return false;
    }
}
