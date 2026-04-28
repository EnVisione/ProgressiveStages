package com.enviouse.progressivestages.common.lock;

import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.config.StageDefinition;
import com.mojang.logging.LogUtils;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * The 2.0 lock registry.
 *
 * <p>Internally stores a {@link ResolvedCategory} per category. Each category owns a list of
 * ({@link PrefixEntry}, {@link StageId}) pairs plus a set of always-unlocked IDs that short-circuit
 * the category check. Query methods iterate the category's entry list, consulting the element's
 * {@link Holder} for tag membership.
 *
 * <p>Public query signatures are deliberately preserved from the 1.x registry so enforcers,
 * mixins, network sync, and integrations don't ripple — only the internals have changed.
 */
public final class LockRegistry {

    private static final Logger LOGGER = LogUtils.getLogger();

    // ------- categories (one per registry-like lockable kind) -------
    private final ResolvedCategory<Item>              itemCat         = new ResolvedCategory<>(Registries.ITEM);
    private final ResolvedCategory<Block>             blockCat        = new ResolvedCategory<>(Registries.BLOCK);
    private final ResolvedCategory<Fluid>             fluidCat        = new ResolvedCategory<>(Registries.FLUID);
    private final ResolvedCategory<EntityType<?>>     entityCat       = new ResolvedCategory<>(Registries.ENTITY_TYPE);
    private final ResolvedCategory<EntityType<?>>     spawnCat        = new ResolvedCategory<>(Registries.ENTITY_TYPE);
    private final ResolvedCategory<net.minecraft.world.item.enchantment.Enchantment>
                                                      enchantCat      = new ResolvedCategory<>(Registries.ENCHANTMENT);
    private final ResolvedCategory<Block>             cropCat         = new ResolvedCategory<>(Registries.BLOCK);
    private final ResolvedCategory<Block>             screenCat       = new ResolvedCategory<>(Registries.BLOCK);
    /** Mirror of {@link #screenCat} typed to Item, so item-opened GUIs (backpacks, portable crafting) also gate. */
    private final ResolvedCategory<Item>              screenItemCat   = new ResolvedCategory<>(Registries.ITEM);
    private final ResolvedCategory<Item>              lootCat         = new ResolvedCategory<>(Registries.ITEM);
    private final ResolvedCategory<EntityType<?>>     petTamingCat      = new ResolvedCategory<>(Registries.ENTITY_TYPE);
    private final ResolvedCategory<EntityType<?>>     petBreedingCat    = new ResolvedCategory<>(Registries.ENTITY_TYPE);
    private final ResolvedCategory<EntityType<?>>     petCommandingCat  = new ResolvedCategory<>(Registries.ENTITY_TYPE);
    /** Used via the id-only path (no holder); tag checks fall back to false. */
    private final ResolvedCategory<Object>            recipeIdCat     = new ResolvedCategory<>(null);
    private final ResolvedCategory<Item>              recipeOutputCat = new ResolvedCategory<>(Registries.ITEM);

    // ------- other lockable structures -------
    private final Map<ResourceLocation, StageId>            dimensionLocks   = new ConcurrentHashMap<>();
    private final Map<String, InteractionLockEntry>         interactionLocks = new ConcurrentHashMap<>();
    private final List<MobReplacementEntry>                 mobReplacements  = Collections.synchronizedList(new ArrayList<>());
    private final List<RegionLockEntry>                     regions          = Collections.synchronizedList(new ArrayList<>());
    private final List<OreOverrideEntry>                    oreOverrides     = Collections.synchronizedList(new ArrayList<>());
    private StructureRulesAggregate                         structures       = StructureRulesAggregate.EMPTY;
    /** Curios slot identifier → required stage. Populated if the compat module is active. */
    private final Map<String, StageId>                      curioSlotLocks   = new ConcurrentHashMap<>();

    // ------- enforcement exceptions -------
    private final List<String> useExemptions          = Collections.synchronizedList(new ArrayList<>());
    private final List<String> pickupExemptions       = Collections.synchronizedList(new ArrayList<>());
    private final List<String> hotbarExemptions       = Collections.synchronizedList(new ArrayList<>());
    private final List<String> mousePickupExemptions  = Collections.synchronizedList(new ArrayList<>());
    private final List<String> inventoryExemptions    = Collections.synchronizedList(new ArrayList<>());

    // ------- v2.0: stages that gate the minecraft: namespace via shorthand -------
    private final Set<StageId> vanillaNamespaceGatingStages = ConcurrentHashMap.newKeySet();

    // ------- v2.0: per-stage [unlocks] carve-out lists -------
    private final Map<StageId, LockDefinition.UnlockGateLists> stageUnlocks = new ConcurrentHashMap<>();

    // ------- caches -------
    private final Map<Item, Optional<StageId>> itemStageCache = new ConcurrentHashMap<>();
    private Map<ResourceLocation, StageId> resolvedItemLocksCache;

    private static LockRegistry INSTANCE;
    public static LockRegistry getInstance() {
        if (INSTANCE == null) INSTANCE = new LockRegistry();
        return INSTANCE;
    }
    private LockRegistry() {}

    // ================================================================
    // Mutation
    // ================================================================

    public void clear() {
        itemCat.clear(); blockCat.clear(); fluidCat.clear(); entityCat.clear(); spawnCat.clear();
        enchantCat.clear(); cropCat.clear(); screenCat.clear(); screenItemCat.clear(); lootCat.clear();
        petTamingCat.clear(); petBreedingCat.clear(); petCommandingCat.clear();
        recipeIdCat.clear(); recipeOutputCat.clear();
        dimensionLocks.clear();
        interactionLocks.clear();
        mobReplacements.clear();
        regions.clear();
        oreOverrides.clear();
        structures = StructureRulesAggregate.EMPTY;
        curioSlotLocks.clear();
        useExemptions.clear(); pickupExemptions.clear(); hotbarExemptions.clear();
        mousePickupExemptions.clear(); inventoryExemptions.clear();
        vanillaNamespaceGatingStages.clear();
        stageUnlocks.clear();
        clearCache();
    }

    public void clearCache() {
        itemStageCache.clear();
        resolvedItemLocksCache = null;
    }

    public void invalidateResolvedCache() {
        resolvedItemLocksCache = null;
    }

    public void registerStage(StageDefinition stage) {
        StageId id = stage.getId();
        LockDefinition locks = stage.getLocks();

        itemCat.register(locks.items(), id);
        blockCat.register(locks.blocks(), id);
        fluidCat.register(locks.fluids(), id);
        entityCat.register(locks.entities(), id);
        spawnCat.register(locks.mobSpawns(), id);
        enchantCat.register(locks.enchants(), id);
        cropCat.register(locks.crops(), id);
        screenCat.register(locks.screens(), id);
        screenItemCat.register(locks.screens(), id);
        lootCat.register(locks.loot(), id);
        petTamingCat.register(locks.petsTaming(), id);
        petBreedingCat.register(locks.petsBreeding(), id);
        petCommandingCat.register(locks.petsCommanding(), id);
        recipeIdCat.register(locks.recipeIds(), id);
        recipeOutputCat.register(locks.recipeOutputs(), id);

        for (ResourceLocation dim : locks.lockedDimensions()) {
            dimensionLocks.put(dim, id);
        }

        for (LockDefinition.InteractionLock i : locks.interactions()) {
            String key = interactionKey(i.type(), i.heldItem(), i.targetBlock());
            interactionLocks.put(key, new InteractionLockEntry(
                i.type(), i.heldItem(), i.targetBlock(), i.description(), id));
        }

        for (LockDefinition.MobReplacement m : locks.mobReplacements()) {
            mobReplacements.add(new MobReplacementEntry(m.target(), m.replaceWith(), id));
        }

        for (LockDefinition.RegionLock r : locks.regions()) {
            regions.add(new RegionLockEntry(r, id));
        }

        for (LockDefinition.OreOverride o : locks.oreOverrides()) {
            oreOverrides.add(new OreOverrideEntry(o.target(), o.displayAs(), o.dropAs(), id));
        }

        structures = structures.merge(locks.structures(), id);

        for (String slot : locks.curioLockedSlots()) {
            if (slot != null && !slot.isEmpty()) curioSlotLocks.putIfAbsent(slot, id);
        }

        useExemptions.addAll(locks.allowedUse());
        pickupExemptions.addAll(locks.allowedPickup());
        hotbarExemptions.addAll(locks.allowedHotbar());
        mousePickupExemptions.addAll(locks.allowedMousePickup());
        inventoryExemptions.addAll(locks.allowedInventory());

        if (locks.minecraftNamespace()) {
            vanillaNamespaceGatingStages.add(id);
            // v2.0 fix: shorthand `minecraft = true` is equivalent to `mods = ["minecraft"]`
            // across the primary lockable categories. Without this, the flag would be
            // recorded but never consulted by `getRequiredStages*` (the gating set would
            // be empty for `minecraft:` resources unless the user also added `mod:minecraft`
            // explicitly). Register a synthetic MOD entry into the items, blocks, fluids,
            // and entities categories so a stage's vanilla gating is non-empty.
            PrefixEntry mc = PrefixEntry.fromMod("minecraft");
            if (mc != null) {
                itemCat.registerSingle(mc, id);
                blockCat.registerSingle(mc, id);
                fluidCat.registerSingle(mc, id);
                entityCat.registerSingle(mc, id);
            }
        }

        LockDefinition.UnlockGateLists u = locks.unlocks();
        if (u != null && !u.isEmpty()) {
            stageUnlocks.put(id, u);
        }

        LOGGER.debug("Registered locks for stage: {}", id);
    }

    // ================================================================
    // Query — items
    // ================================================================

    public Optional<StageId> getRequiredStage(Item item) {
        // Route through getRequiredStages so per-stage [unlocks] carve-outs
        // are applied uniformly between single-stage and multi-stage paths.
        Set<StageId> gating = getRequiredStages(item);
        return gating.isEmpty() ? Optional.empty() : gating.stream().findFirst();
    }

    public boolean isItemLocked(Item item) {
        return getRequiredStage(item).isPresent();
    }

    public Set<ResourceLocation> getAllLockedItems() {
        return itemCat.directIds();
    }

    public Map<ResourceLocation, StageId> getAllItemLocks() {
        return itemCat.directIdMap();
    }

    /**
     * Resolve every Item in the registry against the item category, yielding a flat
     * ID → stage map. Cached until invalidated. Used by network sync and EMI.
     */
    public Map<ResourceLocation, StageId> getAllResolvedItemLocks() {
        if (resolvedItemLocksCache != null) return resolvedItemLocksCache;

        long t0 = System.currentTimeMillis();
        Map<ResourceLocation, StageId> resolved = new HashMap<>();
        for (Item item : BuiltInRegistries.ITEM) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            if (id == null) continue;
            itemCat.findStage(id, item.builtInRegistryHolder())
                .ifPresent(stage -> resolved.put(id, stage));
        }

        long elapsed = System.currentTimeMillis() - t0;
        if (elapsed > 100) {
            LOGGER.info("[ProgressiveStages] Resolved {} item locks in {}ms", resolved.size(), elapsed);
        }
        resolvedItemLocksCache = Collections.unmodifiableMap(resolved);
        return resolvedItemLocksCache;
    }

    // ================================================================
    // Query — blocks, fluids, entities, dimensions
    // ================================================================

    public Optional<StageId> getRequiredStageForBlock(Block block) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
        // Preserve v1 behavior: a block counts as whitelisted if its item form is whitelisted.
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(block.asItem());
        if (itemId != null && itemCat.isWhitelisted(itemId)) return Optional.empty();
        return blockCat.findStage(id, block.builtInRegistryHolder());
    }

    public boolean isBlockUnlocked(ResourceLocation blockId) {
        return blockCat.isWhitelisted(blockId);
    }

    public Set<ResourceLocation> getUnlockedBlocks() {
        return blockCat.whitelistView();
    }

    public Optional<StageId> getRequiredStageForFluid(ResourceLocation fluidId) {
        if (fluidId == null) return Optional.empty();
        Fluid fluid = BuiltInRegistries.FLUID.get(fluidId);
        if (fluid == null) {
            return fluidCat.findStageIdOnly(fluidId);
        }
        return fluidCat.findStage(fluidId, fluid.builtInRegistryHolder());
    }

    public boolean isFluidUnlocked(ResourceLocation fluidId) {
        return fluidCat.isWhitelisted(fluidId);
    }

    public Set<ResourceLocation> getUnlockedFluids() {
        return fluidCat.whitelistView();
    }

    public Map<ResourceLocation, StageId> getAllFluidLocks() {
        return fluidCat.directIdMap();
    }

    public Map<ResourceLocation, StageId> getAllFluidTagLocks() {
        return fluidCat.tagIdMap();
    }

    public Set<String> getAllLockedFluidMods() {
        return fluidCat.modNames();
    }

    public Optional<StageId> getFluidModLockStage(String modId) {
        return fluidCat.modStage(modId);
    }

    public Optional<StageId> getRequiredStageForEntity(EntityType<?> type) {
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        return entityCat.findStage(id, type.builtInRegistryHolder());
    }

    public boolean isEntityLocked(EntityType<?> type) {
        return getRequiredStageForEntity(type).isPresent();
    }

    public Optional<StageId> getRequiredStageForSpawn(EntityType<?> type) {
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        return spawnCat.findStage(id, type.builtInRegistryHolder());
    }

    public Optional<StageId> getRequiredStageForDimension(ResourceLocation dimId) {
        if (dimId == null) return Optional.empty();
        return Optional.ofNullable(dimensionLocks.get(dimId));
    }

    // ================================================================
    // Query — recipes
    // ================================================================

    public Optional<StageId> getRequiredStageForRecipe(ResourceLocation recipeId) {
        if (recipeId == null) return Optional.empty();
        return recipeIdCat.findStageIdOnly(recipeId);
    }

    public Optional<StageId> getRequiredStageForRecipeByOutput(Item outputItem) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(outputItem);
        if (id == null) return Optional.empty();
        return recipeOutputCat.findStage(id, outputItem.builtInRegistryHolder());
    }

    public boolean hasRecipeOnlyLock(Item item) {
        return getRequiredStageForRecipeByOutput(item).isPresent();
    }

    public Set<ResourceLocation> getAllLockedRecipes() {
        return recipeIdCat.directIds();
    }

    public Map<ResourceLocation, StageId> getAllRecipeLocks() {
        return recipeIdCat.directIdMap();
    }

    public Map<ResourceLocation, StageId> getAllRecipeItemLocks() {
        // Resolve recipeOutputCat across every Item so the client receives a flat map.
        Map<ResourceLocation, StageId> out = new HashMap<>();
        for (Item item : BuiltInRegistries.ITEM) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            if (id == null) continue;
            recipeOutputCat.findStage(id, item.builtInRegistryHolder())
                .ifPresent(stage -> out.put(id, stage));
        }
        return Collections.unmodifiableMap(out);
    }

    // ================================================================
    // Query — mod/name surface (legacy compat for JEI / commands)
    // ================================================================

    /**
     * Every mod namespace that locks at least one item in the item category.
     * In 1.x this drew from a separate {@code mods} list; in 2.0 we derive it from
     * {@code mod:} entries in {@code [items].locked}.
     */
    public Set<String> getAllLockedMods() {
        return itemCat.modNames();
    }

    public Optional<StageId> getModLockStage(String modId) {
        if (modId == null) return Optional.empty();
        return itemCat.modStage(modId);
    }

    public Set<String> getAllNamePatterns() {
        return itemCat.nameValues();
    }

    public Optional<StageId> getNamePatternStage(String pattern) {
        if (pattern == null) return Optional.empty();
        return itemCat.nameStage(pattern);
    }

    // ================================================================
    // Query — interactions
    // ================================================================

    public Optional<StageId> getRequiredStageForInteraction(String type, String heldItem, String target) {
        InteractionLockEntry exact = interactionLocks.get(interactionKey(type, heldItem, target));
        if (exact != null) return Optional.of(exact.requiredStage);
        for (InteractionLockEntry e : interactionLocks.values()) {
            if (e.matches(type, heldItem, target)) return Optional.of(e.requiredStage);
        }
        return Optional.empty();
    }

    public java.util.Collection<InteractionLockEntry> getAllInteractionLocksOfType(String type) {
        List<InteractionLockEntry> out = new ArrayList<>();
        for (InteractionLockEntry e : interactionLocks.values()) if (type.equals(e.type)) out.add(e);
        return out;
    }

    private static String interactionKey(String type, String heldItem, String target) {
        return type + ":" + (heldItem != null ? heldItem : "*") + ":" + (target != null ? target : "*");
    }

    // ================================================================
    // Enforcement exemption checks
    // ================================================================

    public boolean isExemptFromUse(Item item)          { return matchesExemption(item, useExemptions); }
    public boolean isExemptFromPickup(Item item)       { return matchesExemption(item, pickupExemptions); }
    public boolean isExemptFromHotbar(Item item)       { return matchesExemption(item, hotbarExemptions); }
    public boolean isExemptFromMousePickup(Item item)  { return matchesExemption(item, mousePickupExemptions); }
    public boolean isExemptFromInventory(Item item)    { return matchesExemption(item, inventoryExemptions); }

    private boolean matchesExemption(Item item, List<String> exemptions) {
        if (exemptions.isEmpty()) return false;
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
        if (itemId == null) return false;
        String itemIdStr = itemId.toString();
        String modId = itemId.getNamespace();

        for (String entry : exemptions) {
            if (entry.startsWith("#")) {
                try {
                    TagKey<Item> tagKey = TagKey.create(Registries.ITEM, ResourceLocation.parse(entry.substring(1)));
                    if (item.builtInRegistryHolder().is(tagKey)) return true;
                } catch (Exception ignored) {}
            } else if (entry.contains(":")) {
                if (itemIdStr.equals(entry)) return true;
            } else {
                if (modId.equals(entry)) return true;
            }
        }
        return false;
    }

    // ================================================================
    // Accessors for the 2.0-only categories (used by future enforcers)
    // ================================================================

    public Optional<StageId> getRequiredStageForEnchantment(ResourceLocation enchantId, Holder<net.minecraft.world.item.enchantment.Enchantment> holder) {
        return enchantCat.findStage(enchantId, holder);
    }

    public Optional<StageId> getRequiredStageForCrop(Block block) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
        return cropCat.findStage(id, block.builtInRegistryHolder());
    }

    public Optional<StageId> getRequiredStageForScreen(Block block) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
        return screenCat.findStage(id, block.builtInRegistryHolder());
    }

    /**
     * Item-side of the screens category: matches the {@code [screens] locked} list against
     * an item ID so item-opened GUIs (backpacks, portable crafting tables, shulker-in-hand
     * via mods) gate on the same config as block-opened GUIs.
     */
    public Optional<StageId> getRequiredStageForScreenItem(Item item) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        if (id == null) return Optional.empty();
        return screenItemCat.findStage(id, item.builtInRegistryHolder());
    }

    public Optional<StageId> getRequiredStageForLoot(Item item) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        if (id == null) return Optional.empty();
        return lootCat.findStage(id, item.builtInRegistryHolder());
    }

    public Optional<StageId> getRequiredStageForPetTaming(EntityType<?> type) {
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        return petTamingCat.findStage(id, type.builtInRegistryHolder());
    }

    public Optional<StageId> getRequiredStageForPetBreeding(EntityType<?> type) {
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        return petBreedingCat.findStage(id, type.builtInRegistryHolder());
    }

    public Optional<StageId> getRequiredStageForPetCommanding(EntityType<?> type) {
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        return petCommandingCat.findStage(id, type.builtInRegistryHolder());
    }

    public List<MobReplacementEntry> getMobReplacements() {
        return Collections.unmodifiableList(mobReplacements);
    }

    public List<RegionLockEntry> getRegions() {
        return Collections.unmodifiableList(regions);
    }

    public StructureRulesAggregate getStructures() {
        return structures;
    }

    public List<OreOverrideEntry> getOreOverrides() {
        return Collections.unmodifiableList(oreOverrides);
    }

    /** Unmodifiable view of locked Curios slot identifiers → required stage. */
    public Map<String, StageId> getCurioSlotLocks() {
        return Collections.unmodifiableMap(curioSlotLocks);
    }

    public Optional<StageId> getRequiredStageForCurioSlot(String slotIdentifier) {
        if (slotIdentifier == null) return Optional.empty();
        return Optional.ofNullable(curioSlotLocks.get(slotIdentifier));
    }

    // ================================================================
    // v2.0: minecraft=true shorthand + child-stage inheritance
    // ================================================================

    /** True if {@code stage} declares {@code minecraft=true} (gates the minecraft: namespace). */
    public boolean isVanillaNamespaceGatedByStage(StageId stage) {
        return stage != null && vanillaNamespaceGatingStages.contains(stage);
    }

    /** All stages with {@code minecraft=true} declared. */
    public Set<StageId> getStagesGatingVanillaNamespace() {
        return Collections.unmodifiableSet(vanillaNamespaceGatingStages);
    }

    /**
     * Compute the effective access set for the {@code minecraft:} namespace.
     * Starting from the player's owned stages, this method ALSO pulls in transitively-owned
     * prerequisite stages with {@code minecraft=true} so that granting a child stage
     * doesn't accidentally drop a parent's vanilla gating.
     *
     * <p>Returns a Set including all of the player's owned stages plus any transitive
     * prerequisite that is still in the player's owned set AND has {@code minecraft=true}.
     */
    public Set<StageId> effectiveLockStagesForVanillaNamespace(Set<StageId> playerOwnedStages) {
        if (playerOwnedStages == null || playerOwnedStages.isEmpty()) return Collections.emptySet();
        if (vanillaNamespaceGatingStages.isEmpty()) return Collections.unmodifiableSet(new HashSet<>(playerOwnedStages));
        Set<StageId> active = new HashSet<>(playerOwnedStages);
        com.enviouse.progressivestages.common.stage.StageOrder order =
            com.enviouse.progressivestages.common.stage.StageOrder.getInstance();
        for (StageId t : new ArrayList<>(playerOwnedStages)) {
            for (StageId s : order.getAllDependencies(t)) {
                if (!playerOwnedStages.contains(s)) continue;
                if (vanillaNamespaceGatingStages.contains(s)) active.add(s);
            }
        }
        return Collections.unmodifiableSet(active);
    }

    // ================================================================
    // v2.0: per-stage [unlocks] carve-outs
    // ================================================================

    /**
     * Apply per-stage unlocks: from {@code gating}, drop any stage S whose
     * own {@code [unlocks]} carves out the given resource (item or its mod namespace).
     */
    public Set<StageId> applyPerStageUnlocks(Set<StageId> gating, ResourceLocation itemId, String modNs) {
        if (gating == null || gating.isEmpty() || stageUnlocks.isEmpty()) return gating == null ? Set.of() : gating;
        Set<StageId> filtered = new HashSet<>(gating);
        boolean changed = filtered.removeIf(sid -> {
            LockDefinition.UnlockGateLists u = stageUnlocks.get(sid);
            if (u == null) return false;
            if (itemId != null && u.items().contains(itemId)) {
                if (com.enviouse.progressivestages.common.config.StageConfig.isDebugLogging()) {
                    LOGGER.debug("[ProgressiveStages] Stage {} carves out {} via [unlocks].items", sid, itemId);
                }
                return true;
            }
            if (modNs != null && u.mods().contains(modNs)) {
                if (com.enviouse.progressivestages.common.config.StageConfig.isDebugLogging()) {
                    LOGGER.debug("[ProgressiveStages] Stage {} carves out namespace {} via [unlocks].mods", sid, modNs);
                }
                return true;
            }
            return false;
        });
        if (!changed) return gating;
        return Set.copyOf(filtered);
    }

    public Set<StageId> applyPerStageUnlocksFluid(Set<StageId> gating, ResourceLocation fluidId, String modNs) {
        if (gating == null || gating.isEmpty() || stageUnlocks.isEmpty()) return gating == null ? Set.of() : gating;
        Set<StageId> filtered = new HashSet<>(gating);
        filtered.removeIf(sid -> {
            LockDefinition.UnlockGateLists u = stageUnlocks.get(sid);
            if (u == null) return false;
            return (fluidId != null && u.fluids().contains(fluidId))
                || (modNs != null && u.mods().contains(modNs));
        });
        return Set.copyOf(filtered);
    }

    public Set<StageId> applyPerStageUnlocksDimension(Set<StageId> gating, ResourceLocation dimId) {
        if (gating == null || gating.isEmpty() || stageUnlocks.isEmpty()) return gating == null ? Set.of() : gating;
        Set<StageId> filtered = new HashSet<>(gating);
        filtered.removeIf(sid -> {
            LockDefinition.UnlockGateLists u = stageUnlocks.get(sid);
            return u != null && dimId != null && u.dimensions().contains(dimId);
        });
        return Set.copyOf(filtered);
    }

    public Set<StageId> applyPerStageUnlocksEntity(Set<StageId> gating, ResourceLocation entityId, String modNs) {
        if (gating == null || gating.isEmpty() || stageUnlocks.isEmpty()) return gating == null ? Set.of() : gating;
        Set<StageId> filtered = new HashSet<>(gating);
        filtered.removeIf(sid -> {
            LockDefinition.UnlockGateLists u = stageUnlocks.get(sid);
            if (u == null) return false;
            return (entityId != null && u.entities().contains(entityId))
                || (modNs != null && u.mods().contains(modNs));
        });
        return Set.copyOf(filtered);
    }

    // ================================================================
    // v2.0: Multi-stage gating API (Set<StageId> returns)
    // ================================================================

    public Set<StageId> getRequiredStages(Item item) {
        if (item == null) return Set.of();
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        Set<StageId> raw = itemCat.findStages(id, item.builtInRegistryHolder());
        return applyPerStageUnlocks(raw, id, id != null ? id.getNamespace() : null);
    }

    public Set<StageId> getRequiredStagesForBlock(Block block) {
        if (block == null) return Set.of();
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(block.asItem());
        if (itemId != null && itemCat.isWhitelisted(itemId)) return Set.of();
        Set<StageId> raw = blockCat.findStages(id, block.builtInRegistryHolder());
        // v2.0: apply per-stage [unlocks] carve-outs (mods is the meaningful filter for blocks).
        return applyPerStageUnlocks(raw, id, id != null ? id.getNamespace() : null);
    }

    public Set<StageId> getRequiredStagesForFluid(ResourceLocation fluidId) {
        if (fluidId == null) return Set.of();
        Fluid fluid = BuiltInRegistries.FLUID.get(fluidId);
        Set<StageId> raw = (fluid == null)
            ? fluidCat.findStagesIdOnly(fluidId)
            : fluidCat.findStages(fluidId, fluid.builtInRegistryHolder());
        return applyPerStageUnlocksFluid(raw, fluidId, fluidId.getNamespace());
    }

    public Set<StageId> getRequiredStagesForEntity(ResourceLocation entityId) {
        if (entityId == null) return Set.of();
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(entityId);
        Set<StageId> raw = (type == null)
            ? entityCat.findStagesIdOnly(entityId)
            : entityCat.findStages(entityId, type.builtInRegistryHolder());
        return applyPerStageUnlocksEntity(raw, entityId, entityId.getNamespace());
    }

    public Set<StageId> getRequiredStagesForEntity(EntityType<?> type) {
        if (type == null) return Set.of();
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        Set<StageId> raw = entityCat.findStages(id, type.builtInRegistryHolder());
        return applyPerStageUnlocksEntity(raw, id, id != null ? id.getNamespace() : null);
    }

    public Set<StageId> getRequiredStagesForDimension(ResourceLocation dimId) {
        if (dimId == null) return Set.of();
        StageId direct = dimensionLocks.get(dimId);
        Set<StageId> raw = direct != null ? Set.of(direct) : Set.of();
        return applyPerStageUnlocksDimension(raw, dimId);
    }

    public Set<StageId> getRequiredStagesForRecipe(ResourceLocation recipeId) {
        if (recipeId == null) return Set.of();
        Set<StageId> raw = recipeIdCat.findStagesIdOnly(recipeId);
        // v2.0: per-stage [unlocks] carve-out (items and mods both meaningful here).
        return applyPerStageUnlocks(raw, recipeId, recipeId.getNamespace());
    }

    public Set<StageId> getRequiredStagesForRecipeByOutput(Item output) {
        if (output == null) return Set.of();
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(output);
        Set<StageId> raw = recipeOutputCat.findStages(id, output.builtInRegistryHolder());
        // v2.0: carve-outs apply on the output item and its mod namespace.
        return applyPerStageUnlocks(raw, id, id != null ? id.getNamespace() : null);
    }

    public Set<StageId> getRequiredStagesForEnchantment(ResourceLocation id, Holder<net.minecraft.world.item.enchantment.Enchantment> holder) {
        return enchantCat.findStages(id, holder);
    }

    public Set<StageId> getRequiredStagesForMod(String modId) {
        if (modId == null) return Set.of();
        return itemCat.modStages(modId);
    }

    public Set<StageId> getRequiredStagesForName(String pattern) {
        if (pattern == null) return Set.of();
        String needle = pattern.toLowerCase();
        Set<StageId> out = null;
        // we can't access the inner entries here; use existing single-stage as fallback
        Optional<StageId> single = itemCat.nameStage(pattern);
        if (single.isPresent()) {
            out = new java.util.LinkedHashSet<>();
            out.add(single.get());
        }
        return out == null ? Set.of() : Set.copyOf(out);
    }

    /**
     * The canonical multi-stage gate predicate for items.
     * Considers vanilla namespace inheritance for {@code minecraft:} ids.
     * Returns true if blocked (gating non-empty AND not all gating stages are owned).
     */
    public boolean isItemBlockedFor(net.minecraft.server.level.ServerPlayer player, Item item) {
        if (player == null || item == null) return false;
        Set<StageId> gating = getRequiredStages(item);
        if (gating.isEmpty()) return false;
        Set<StageId> access = computeAccessStagesForItem(player, item);
        return blockedByMissing(gating, access);
    }

    public Optional<StageId> primaryRestrictingStage(net.minecraft.server.level.ServerPlayer player, Item item) {
        if (player == null || item == null) return Optional.empty();
        Set<StageId> gating = getRequiredStages(item);
        if (gating.isEmpty()) return Optional.empty();
        Set<StageId> access = computeAccessStagesForItem(player, item);
        for (StageId s : gating) {
            if (!access.contains(s)) return Optional.of(s);
        }
        return Optional.empty();
    }

    /** Lists all gating stages the player is missing for the given item. */
    public Set<StageId> missingStagesForItem(net.minecraft.server.level.ServerPlayer player, Item item) {
        if (player == null || item == null) return Set.of();
        Set<StageId> gating = getRequiredStages(item);
        if (gating.isEmpty()) return Set.of();
        Set<StageId> access = computeAccessStagesForItem(player, item);
        Set<StageId> missing = new java.util.LinkedHashSet<>();
        for (StageId s : gating) if (!access.contains(s)) missing.add(s);
        return Collections.unmodifiableSet(missing);
    }

    private Set<StageId> computeAccessStagesForItem(net.minecraft.server.level.ServerPlayer player, Item item) {
        Set<StageId> owned = com.enviouse.progressivestages.common.stage.StageManager.getInstance().getStages(player);
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        if (id != null && "minecraft".equals(id.getNamespace())) {
            return effectiveLockStagesForVanillaNamespace(owned);
        }
        return owned;
    }

    private boolean blockedByMissing(Set<StageId> gating, Set<StageId> access) {
        return !gating.isEmpty() && !access.containsAll(gating);
    }

    /** True if {@code player} owns every stage in {@code set}. */
    public boolean playerHasAllStages(net.minecraft.server.level.ServerPlayer player, Set<StageId> set) {
        if (set == null || set.isEmpty()) return true;
        com.enviouse.progressivestages.common.stage.StageManager sm =
            com.enviouse.progressivestages.common.stage.StageManager.getInstance();
        for (StageId s : set) if (!sm.hasStage(player, s)) return false;
        return true;
    }

    public boolean isFluidBlockedFor(net.minecraft.server.level.ServerPlayer player, ResourceLocation fluidId) {
        if (player == null || fluidId == null) return false;
        Set<StageId> gating = getRequiredStagesForFluid(fluidId);
        if (gating.isEmpty()) return false;
        Set<StageId> access = "minecraft".equals(fluidId.getNamespace())
            ? effectiveLockStagesForVanillaNamespace(com.enviouse.progressivestages.common.stage.StageManager.getInstance().getStages(player))
            : com.enviouse.progressivestages.common.stage.StageManager.getInstance().getStages(player);
        return blockedByMissing(gating, access);
    }

    public boolean isDimensionBlockedFor(net.minecraft.server.level.ServerPlayer player, ResourceLocation dimId) {
        if (player == null || dimId == null) return false;
        Set<StageId> gating = getRequiredStagesForDimension(dimId);
        if (gating.isEmpty()) return false;
        return !playerHasAllStages(player, gating);
    }

    public boolean isEntityBlockedFor(net.minecraft.server.level.ServerPlayer player, EntityType<?> type) {
        if (player == null || type == null) return false;
        Set<StageId> gating = getRequiredStagesForEntity(type);
        if (gating.isEmpty()) return false;
        return !playerHasAllStages(player, gating);
    }

    public boolean isBlockBlockedFor(net.minecraft.server.level.ServerPlayer player, Block block) {
        if (player == null || block == null) return false;
        Set<StageId> gating = getRequiredStagesForBlock(block);
        if (gating.isEmpty()) return false;
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
        Set<StageId> access = (id != null && "minecraft".equals(id.getNamespace()))
            ? effectiveLockStagesForVanillaNamespace(com.enviouse.progressivestages.common.stage.StageManager.getInstance().getStages(player))
            : com.enviouse.progressivestages.common.stage.StageManager.getInstance().getStages(player);
        return blockedByMissing(gating, access);
    }

    public boolean isRecipeBlockedFor(net.minecraft.server.level.ServerPlayer player, ResourceLocation recipeId) {
        if (player == null || recipeId == null) return false;
        Set<StageId> gating = getRequiredStagesForRecipe(recipeId);
        if (gating.isEmpty()) return false;
        return !playerHasAllStages(player, gating);
    }

    public boolean isRecipeOutputBlockedFor(net.minecraft.server.level.ServerPlayer player, Item outputItem) {
        if (player == null || outputItem == null) return false;
        Set<StageId> gating = getRequiredStagesForRecipeByOutput(outputItem);
        if (gating.isEmpty()) return false;
        return !playerHasAllStages(player, gating);
    }

    public Optional<StageId> primaryRestrictingStageForRecipe(net.minecraft.server.level.ServerPlayer player, ResourceLocation recipeId) {
        if (player == null || recipeId == null) return Optional.empty();
        return firstMissing(player, getRequiredStagesForRecipe(recipeId));
    }

    public Optional<StageId> primaryRestrictingStageForRecipeOutput(net.minecraft.server.level.ServerPlayer player, Item outputItem) {
        if (player == null || outputItem == null) return Optional.empty();
        return firstMissing(player, getRequiredStagesForRecipeByOutput(outputItem));
    }

    public Optional<StageId> primaryRestrictingStageForEnchantment(net.minecraft.server.level.ServerPlayer player, ResourceLocation enchantId, Holder<net.minecraft.world.item.enchantment.Enchantment> holder) {
        if (player == null || enchantId == null) return Optional.empty();
        return firstMissing(player, getRequiredStagesForEnchantment(enchantId, holder));
    }

    public boolean isEnchantmentBlockedFor(net.minecraft.server.level.ServerPlayer player, ResourceLocation enchantId, Holder<net.minecraft.world.item.enchantment.Enchantment> holder) {
        if (player == null || enchantId == null) return false;
        Set<StageId> gating = getRequiredStagesForEnchantment(enchantId, holder);
        if (gating.isEmpty()) return false;
        return !playerHasAllStages(player, gating);
    }

    /** Multi-stage variant of getModLockStage — returns ALL stages locking this mod. */
    public Set<StageId> getModLockStages(String modId) {
        return getRequiredStagesForMod(modId);
    }

    // ----- Multi-stage variants for secondary categories (v2.0 cleanup pass) -----

    public Set<StageId> getRequiredStagesForSpawn(EntityType<?> type) {
        if (type == null) return Set.of();
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        Set<StageId> raw = spawnCat.findStages(id, type.builtInRegistryHolder());
        return applyPerStageUnlocksEntity(raw, id, id != null ? id.getNamespace() : null);
    }

    public Set<StageId> getRequiredStagesForLoot(Item item) {
        if (item == null) return Set.of();
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        Set<StageId> raw = lootCat.findStages(id, item.builtInRegistryHolder());
        return applyPerStageUnlocks(raw, id, id != null ? id.getNamespace() : null);
    }

    public Set<StageId> getRequiredStagesForCrop(Block block) {
        if (block == null) return Set.of();
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
        Set<StageId> raw = cropCat.findStages(id, block.builtInRegistryHolder());
        // v2.0: per-stage [unlocks] carve-outs (mods filter is meaningful for crop blocks).
        return applyPerStageUnlocks(raw, id, id != null ? id.getNamespace() : null);
    }

    public Set<StageId> getRequiredStagesForScreen(Block block) {
        if (block == null) return Set.of();
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
        Set<StageId> raw = screenCat.findStages(id, block.builtInRegistryHolder());
        // v2.0: per-stage [unlocks] carve-outs.
        return applyPerStageUnlocks(raw, id, id != null ? id.getNamespace() : null);
    }

    public Set<StageId> getRequiredStagesForScreenItem(Item item) {
        if (item == null) return Set.of();
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        Set<StageId> raw = screenItemCat.findStages(id, item.builtInRegistryHolder());
        // v2.0: per-stage [unlocks] carve-outs (item/mod filter applies directly here).
        return applyPerStageUnlocks(raw, id, id != null ? id.getNamespace() : null);
    }

    public Set<StageId> getRequiredStagesForPetTaming(EntityType<?> type) {
        if (type == null) return Set.of();
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        Set<StageId> raw = petTamingCat.findStages(id, type.builtInRegistryHolder());
        // v2.0: per-stage [unlocks] entity carve-outs.
        return applyPerStageUnlocksEntity(raw, id, id != null ? id.getNamespace() : null);
    }

    public Set<StageId> getRequiredStagesForPetBreeding(EntityType<?> type) {
        if (type == null) return Set.of();
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        Set<StageId> raw = petBreedingCat.findStages(id, type.builtInRegistryHolder());
        return applyPerStageUnlocksEntity(raw, id, id != null ? id.getNamespace() : null);
    }

    public Set<StageId> getRequiredStagesForPetCommanding(EntityType<?> type) {
        if (type == null) return Set.of();
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        Set<StageId> raw = petCommandingCat.findStages(id, type.builtInRegistryHolder());
        return applyPerStageUnlocksEntity(raw, id, id != null ? id.getNamespace() : null);
    }

    public Set<StageId> getRequiredStagesForCurioSlot(String slotIdentifier) {
        if (slotIdentifier == null) return Set.of();
        StageId direct = curioSlotLocks.get(slotIdentifier);
        return direct != null ? Set.of(direct) : Set.of();
    }

    // ----- isXxxBlockedFor + primary helpers for secondary categories -----

    public boolean isEntitySpawnBlockedFor(net.minecraft.server.level.ServerPlayer player, EntityType<?> type) {
        if (player == null || type == null) return false;
        Set<StageId> gating = getRequiredStagesForSpawn(type);
        if (gating.isEmpty()) return false;
        return !playerHasAllStages(player, gating);
    }

    public Optional<StageId> primaryRestrictingStageForSpawn(net.minecraft.server.level.ServerPlayer player, EntityType<?> type) {
        if (player == null || type == null) return Optional.empty();
        Set<StageId> gating = getRequiredStagesForSpawn(type);
        return firstMissing(player, gating);
    }

    public boolean isLootBlockedFor(net.minecraft.server.level.ServerPlayer player, Item item) {
        if (player == null || item == null) return false;
        Set<StageId> gating = getRequiredStagesForLoot(item);
        if (gating.isEmpty()) return false;
        return !playerHasAllStages(player, gating);
    }

    public Optional<StageId> primaryRestrictingStageForLoot(net.minecraft.server.level.ServerPlayer player, Item item) {
        if (player == null || item == null) return Optional.empty();
        return firstMissing(player, getRequiredStagesForLoot(item));
    }

    public boolean isCropBlockedFor(net.minecraft.server.level.ServerPlayer player, Block block) {
        if (player == null || block == null) return false;
        Set<StageId> gating = getRequiredStagesForCrop(block);
        if (gating.isEmpty()) return false;
        return !playerHasAllStages(player, gating);
    }

    public Optional<StageId> primaryRestrictingStageForCrop(net.minecraft.server.level.ServerPlayer player, Block block) {
        if (player == null || block == null) return Optional.empty();
        return firstMissing(player, getRequiredStagesForCrop(block));
    }

    public boolean isScreenBlockedFor(net.minecraft.server.level.ServerPlayer player, Block block) {
        if (player == null || block == null) return false;
        Set<StageId> gating = getRequiredStagesForScreen(block);
        if (gating.isEmpty()) return false;
        return !playerHasAllStages(player, gating);
    }

    public Optional<StageId> primaryRestrictingStageForScreen(net.minecraft.server.level.ServerPlayer player, Block block) {
        if (player == null || block == null) return Optional.empty();
        return firstMissing(player, getRequiredStagesForScreen(block));
    }

    public boolean isScreenItemBlockedFor(net.minecraft.server.level.ServerPlayer player, Item item) {
        if (player == null || item == null) return false;
        Set<StageId> gating = getRequiredStagesForScreenItem(item);
        if (gating.isEmpty()) return false;
        return !playerHasAllStages(player, gating);
    }

    public Optional<StageId> primaryRestrictingStageForScreenItem(net.minecraft.server.level.ServerPlayer player, Item item) {
        if (player == null || item == null) return Optional.empty();
        return firstMissing(player, getRequiredStagesForScreenItem(item));
    }

    public boolean isPetTamingBlockedFor(net.minecraft.server.level.ServerPlayer player, EntityType<?> type) {
        return !playerHasAllStages(player, getRequiredStagesForPetTaming(type));
    }

    public boolean isPetBreedingBlockedFor(net.minecraft.server.level.ServerPlayer player, EntityType<?> type) {
        return !playerHasAllStages(player, getRequiredStagesForPetBreeding(type));
    }

    public boolean isPetCommandingBlockedFor(net.minecraft.server.level.ServerPlayer player, EntityType<?> type) {
        return !playerHasAllStages(player, getRequiredStagesForPetCommanding(type));
    }

    public Optional<StageId> primaryRestrictingStageForPetTaming(net.minecraft.server.level.ServerPlayer player, EntityType<?> type) {
        if (player == null || type == null) return Optional.empty();
        return firstMissing(player, getRequiredStagesForPetTaming(type));
    }

    public Optional<StageId> primaryRestrictingStageForPetBreeding(net.minecraft.server.level.ServerPlayer player, EntityType<?> type) {
        if (player == null || type == null) return Optional.empty();
        return firstMissing(player, getRequiredStagesForPetBreeding(type));
    }

    public Optional<StageId> primaryRestrictingStageForPetCommanding(net.minecraft.server.level.ServerPlayer player, EntityType<?> type) {
        if (player == null || type == null) return Optional.empty();
        return firstMissing(player, getRequiredStagesForPetCommanding(type));
    }

    public boolean isCurioSlotBlockedFor(net.minecraft.server.level.ServerPlayer player, String slotIdentifier) {
        if (player == null || slotIdentifier == null) return false;
        Set<StageId> gating = getRequiredStagesForCurioSlot(slotIdentifier);
        if (gating.isEmpty()) return false;
        return !playerHasAllStages(player, gating);
    }

    public Optional<StageId> primaryRestrictingStageForCurioSlot(net.minecraft.server.level.ServerPlayer player, String slotIdentifier) {
        if (player == null || slotIdentifier == null) return Optional.empty();
        return firstMissing(player, getRequiredStagesForCurioSlot(slotIdentifier));
    }

    public Optional<StageId> primaryRestrictingStageForBlock(net.minecraft.server.level.ServerPlayer player, Block block) {
        if (player == null || block == null) return Optional.empty();
        Set<StageId> gating = getRequiredStagesForBlock(block);
        if (gating.isEmpty()) return Optional.empty();
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
        Set<StageId> access = (id != null && "minecraft".equals(id.getNamespace()))
            ? effectiveLockStagesForVanillaNamespace(com.enviouse.progressivestages.common.stage.StageManager.getInstance().getStages(player))
            : com.enviouse.progressivestages.common.stage.StageManager.getInstance().getStages(player);
        for (StageId s : gating) if (!access.contains(s)) return Optional.of(s);
        return Optional.empty();
    }

    public Optional<StageId> primaryRestrictingStageForFluid(net.minecraft.server.level.ServerPlayer player, ResourceLocation fluidId) {
        if (player == null || fluidId == null) return Optional.empty();
        Set<StageId> gating = getRequiredStagesForFluid(fluidId);
        if (gating.isEmpty()) return Optional.empty();
        Set<StageId> access = "minecraft".equals(fluidId.getNamespace())
            ? effectiveLockStagesForVanillaNamespace(com.enviouse.progressivestages.common.stage.StageManager.getInstance().getStages(player))
            : com.enviouse.progressivestages.common.stage.StageManager.getInstance().getStages(player);
        for (StageId s : gating) if (!access.contains(s)) return Optional.of(s);
        return Optional.empty();
    }

    public Optional<StageId> primaryRestrictingStageForDimension(net.minecraft.server.level.ServerPlayer player, ResourceLocation dimId) {
        return firstMissing(player, getRequiredStagesForDimension(dimId));
    }

    public Optional<StageId> primaryRestrictingStageForEntity(net.minecraft.server.level.ServerPlayer player, EntityType<?> type) {
        return firstMissing(player, getRequiredStagesForEntity(type));
    }

    /** Returns the first stage in {@code gating} the player is missing, or empty if none. */
    private Optional<StageId> firstMissing(net.minecraft.server.level.ServerPlayer player, Set<StageId> gating) {
        if (player == null || gating == null || gating.isEmpty()) return Optional.empty();
        com.enviouse.progressivestages.common.stage.StageManager sm =
            com.enviouse.progressivestages.common.stage.StageManager.getInstance();
        for (StageId s : gating) if (!sm.hasStage(player, s)) return Optional.of(s);
        return Optional.empty();
    }

    // ================================================================
    // Nested types
    // ================================================================

    /**
     * Generic bag of {@link PrefixEntry} locks + whitelist. The registry parameter
     * identifies which registry the holder in {@link #findStage(ResourceLocation, Holder)}
     * must come from; it's used to build tag keys.
     */
    private static final class ResolvedCategory<T> {
        private final ResourceKey<? extends Registry<T>> registryKey;
        private final List<Entry<T>> entries = Collections.synchronizedList(new ArrayList<>());
        private final Set<ResourceLocation> whitelist = ConcurrentHashMap.newKeySet();

        ResolvedCategory(ResourceKey<? extends Registry<T>> registryKey) {
            this.registryKey = registryKey;
        }

        void clear() { entries.clear(); whitelist.clear(); }

        void register(CategoryLocks locks, StageId stage) {
            if (locks == null) return;
            for (PrefixEntry e : locks.locked()) entries.add(new Entry<>(e, stage));
            whitelist.addAll(locks.alwaysUnlocked());
        }

        /** Register a single {@link PrefixEntry} → stage pair (used for synthetic entries). */
        void registerSingle(PrefixEntry entry, StageId stage) {
            if (entry == null || stage == null) return;
            entries.add(new Entry<>(entry, stage));
        }

        Optional<StageId> findStage(ResourceLocation id, Holder<T> holder) {
            if (id == null) return Optional.empty();
            if (whitelist.contains(id)) return Optional.empty();
            for (Entry<T> e : entries) {
                if (e.prefix.matches(id, holder, registryKey)) return Optional.of(e.stage);
            }
            return Optional.empty();
        }

        /** v2.0 multi-stage: every gating stage for this id (deduplicated, insertion order). */
        Set<StageId> findStages(ResourceLocation id, Holder<T> holder) {
            if (id == null) return Set.of();
            if (whitelist.contains(id)) return Set.of();
            Set<StageId> out = null;
            for (Entry<T> e : entries) {
                if (e.prefix.matches(id, holder, registryKey)) {
                    if (out == null) out = new java.util.LinkedHashSet<>();
                    out.add(e.stage);
                }
            }
            return out == null ? Set.of() : Set.copyOf(out);
        }

        /** ID-only variant for contexts with no holder (e.g. dimensions, recipe IDs). */
        Optional<StageId> findStageIdOnly(ResourceLocation id) {
            if (id == null) return Optional.empty();
            if (whitelist.contains(id)) return Optional.empty();
            for (Entry<T> e : entries) {
                if (e.prefix.matchesIdOnly(id)) return Optional.of(e.stage);
            }
            return Optional.empty();
        }

        Set<StageId> findStagesIdOnly(ResourceLocation id) {
            if (id == null) return Set.of();
            if (whitelist.contains(id)) return Set.of();
            Set<StageId> out = null;
            for (Entry<T> e : entries) {
                if (e.prefix.matchesIdOnly(id)) {
                    if (out == null) out = new java.util.LinkedHashSet<>();
                    out.add(e.stage);
                }
            }
            return out == null ? Set.of() : Set.copyOf(out);
        }

        boolean isWhitelisted(ResourceLocation id) {
            return id != null && whitelist.contains(id);
        }

        Set<ResourceLocation> whitelistView() {
            return Collections.unmodifiableSet(whitelist);
        }

        /** Direct ID-kind locks in this category, as a map. */
        Map<ResourceLocation, StageId> directIdMap() {
            Map<ResourceLocation, StageId> out = new LinkedHashMap<>();
            for (Entry<T> e : entries) {
                if (e.prefix.kind() == PrefixEntry.Kind.ID && e.prefix.id() != null) {
                    out.putIfAbsent(e.prefix.id(), e.stage);
                }
            }
            return Collections.unmodifiableMap(out);
        }

        Set<ResourceLocation> directIds() {
            return directIdMap().keySet();
        }

        /** Tag-kind locks (tag id → stage). */
        Map<ResourceLocation, StageId> tagIdMap() {
            Map<ResourceLocation, StageId> out = new LinkedHashMap<>();
            for (Entry<T> e : entries) {
                if (e.prefix.kind() == PrefixEntry.Kind.TAG && e.prefix.id() != null) {
                    out.putIfAbsent(e.prefix.id(), e.stage);
                }
            }
            return Collections.unmodifiableMap(out);
        }

        Set<String> modNames() {
            Set<String> out = new HashSet<>();
            for (Entry<T> e : entries) {
                if (e.prefix.kind() == PrefixEntry.Kind.MOD) out.add(e.prefix.value());
            }
            return Collections.unmodifiableSet(out);
        }

        Optional<StageId> modStage(String modId) {
            String needle = modId.toLowerCase();
            for (Entry<T> e : entries) {
                if (e.prefix.kind() == PrefixEntry.Kind.MOD && e.prefix.value().equals(needle)) {
                    return Optional.of(e.stage);
                }
            }
            return Optional.empty();
        }

        Set<StageId> modStages(String modId) {
            String needle = modId.toLowerCase();
            Set<StageId> out = null;
            for (Entry<T> e : entries) {
                if (e.prefix.kind() == PrefixEntry.Kind.MOD && e.prefix.value().equals(needle)) {
                    if (out == null) out = new java.util.LinkedHashSet<>();
                    out.add(e.stage);
                }
            }
            return out == null ? Set.of() : Set.copyOf(out);
        }

        Set<String> nameValues() {
            Set<String> out = new HashSet<>();
            for (Entry<T> e : entries) {
                if (e.prefix.kind() == PrefixEntry.Kind.NAME) out.add(e.prefix.value());
            }
            return Collections.unmodifiableSet(out);
        }

        Optional<StageId> nameStage(String pattern) {
            String needle = pattern.toLowerCase();
            for (Entry<T> e : entries) {
                if (e.prefix.kind() == PrefixEntry.Kind.NAME && e.prefix.value().equals(needle)) {
                    return Optional.of(e.stage);
                }
            }
            return Optional.empty();
        }

        private record Entry<T>(PrefixEntry prefix, StageId stage) {}
    }

    /** An interaction lock entry. */
    public static final class InteractionLockEntry {
        public final String type;
        public final String heldItem;
        public final String targetBlock;
        public final String description;
        public final StageId requiredStage;

        public InteractionLockEntry(String type, String heldItem, String targetBlock,
                                    String description, StageId requiredStage) {
            this.type = type;
            this.heldItem = heldItem;
            this.targetBlock = targetBlock;
            this.description = description;
            this.requiredStage = requiredStage;
        }

        public boolean matches(String checkType, String checkHeldItem, String checkTargetBlock) {
            if (!Objects.equals(type, checkType)) return false;
            if (heldItem != null && !"*".equals(heldItem)) {
                if (heldItem.startsWith("#")) {
                    if (checkHeldItem == null || !checkHeldItem.contains(heldItem.substring(1))) return false;
                } else if (!heldItem.equals(checkHeldItem)) return false;
            }
            if (targetBlock != null && !"*".equals(targetBlock)) {
                if (targetBlock.startsWith("#")) {
                    if (checkTargetBlock == null || !checkTargetBlock.contains(targetBlock.substring(1))) return false;
                } else if (!targetBlock.equals(checkTargetBlock)) return false;
            }
            return true;
        }
    }

    /** A mob replacement entry as registered with its owning stage. */
    public static final class MobReplacementEntry {
        public final PrefixEntry target;
        public final ResourceLocation replaceWith;
        public final StageId requiredStage;

        public MobReplacementEntry(PrefixEntry target, ResourceLocation replaceWith, StageId requiredStage) {
            this.target = target;
            this.replaceWith = replaceWith;
            this.requiredStage = requiredStage;
        }
    }

    /** A region lock entry as registered with its owning stage. */
    public static final class RegionLockEntry {
        public final LockDefinition.RegionLock def;
        public final StageId requiredStage;

        public RegionLockEntry(LockDefinition.RegionLock def, StageId requiredStage) {
            this.def = def;
            this.requiredStage = requiredStage;
        }
    }

    public static final class OreOverrideEntry {
        public final ResourceLocation target;
        public final ResourceLocation displayAs;
        public final ResourceLocation dropAs;
        public final StageId requiredStage;

        public OreOverrideEntry(ResourceLocation target, ResourceLocation displayAs,
                                ResourceLocation dropAs, StageId requiredStage) {
            this.target = target;
            this.displayAs = displayAs;
            this.dropAs = dropAs;
            this.requiredStage = requiredStage;
        }
    }

    /**
     * Accumulated structure rules across all stages. Merging is union-style: if any
     * stage sets a boolean, it applies. Entry-lock IDs carry their own stage.
     */
    public static final class StructureRulesAggregate {
        public static final StructureRulesAggregate EMPTY =
            new StructureRulesAggregate(Map.of(), false, false, false, false);

        /** Structure ID → required stage. Only exact IDs are used for entry locks. */
        public final Map<ResourceLocation, StageId> lockedEntry;
        public final boolean preventBlockBreak;
        public final boolean preventBlockPlace;
        public final boolean preventExplosions;
        public final boolean disableMobSpawning;

        public StructureRulesAggregate(Map<ResourceLocation, StageId> lockedEntry,
                                       boolean pbb, boolean pbp, boolean pex, boolean dms) {
            this.lockedEntry = Collections.unmodifiableMap(lockedEntry);
            this.preventBlockBreak = pbb;
            this.preventBlockPlace = pbp;
            this.preventExplosions = pex;
            this.disableMobSpawning = dms;
        }

        StructureRulesAggregate merge(LockDefinition.StructureRules other, StageId stage) {
            if (other == null || other.isEmpty()) return this;
            Map<ResourceLocation, StageId> merged = new HashMap<>(this.lockedEntry);
            for (PrefixEntry e : other.lockedEntry().locked()) {
                if (e.kind() == PrefixEntry.Kind.ID && e.id() != null) {
                    merged.putIfAbsent(e.id(), stage);
                }
            }
            return new StructureRulesAggregate(
                merged,
                this.preventBlockBreak || other.preventBlockBreak(),
                this.preventBlockPlace || other.preventBlockPlace(),
                this.preventExplosions || other.preventExplosions(),
                this.disableMobSpawning || other.disableMobSpawning()
            );
        }
    }
}
