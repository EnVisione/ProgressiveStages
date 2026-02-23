package com.enviouse.progressivestages.common.lock;

import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.config.StageDefinition;
import com.enviouse.progressivestages.common.util.StageUtil;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import org.slf4j.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central registry for all locks. Maps items, recipes, blocks, dimensions, mods to required stages.
 */
public class LockRegistry {

    private static final Logger LOGGER = LogUtils.getLogger();

    // Item ID -> Required Stage
    private final Map<ResourceLocation, StageId> itemLocks = new ConcurrentHashMap<>();

    // Item Tag -> Required Stage
    private final Map<ResourceLocation, StageId> itemTagLocks = new ConcurrentHashMap<>();

    // Item Mod ID -> Required Stage (lock all items from a mod, but not blocks/entities)
    private final Map<String, StageId> itemModLocks = new ConcurrentHashMap<>();

    // Recipe ID -> Required Stage
    private final Map<ResourceLocation, StageId> recipeLocks = new ConcurrentHashMap<>();

    // Recipe Tag -> Required Stage
    private final Map<ResourceLocation, StageId> recipeTagLocks = new ConcurrentHashMap<>();

    // Block ID -> Required Stage
    private final Map<ResourceLocation, StageId> blockLocks = new ConcurrentHashMap<>();

    // Block Tag -> Required Stage
    private final Map<ResourceLocation, StageId> blockTagLocks = new ConcurrentHashMap<>();

    // Block Mod ID -> Required Stage (lock all blocks from a mod, but not items/entities)
    private final Map<String, StageId> blockModLocks = new ConcurrentHashMap<>();

    // v1.4: Fluid ID -> Required Stage (hide from EMI/JEI)
    private final Map<ResourceLocation, StageId> fluidLocks = new ConcurrentHashMap<>();

    // v1.4: Fluid Tag -> Required Stage (hide from EMI/JEI)
    private final Map<ResourceLocation, StageId> fluidTagLocks = new ConcurrentHashMap<>();

    // v1.4: Fluid Mod ID -> Required Stage (hide all fluids from a mod in EMI/JEI)
    private final Map<String, StageId> fluidModLocks = new ConcurrentHashMap<>();

    // Dimension ID -> Required Stage
    private final Map<ResourceLocation, StageId> dimensionLocks = new ConcurrentHashMap<>();

    // Mod ID -> Required Stage (locks items, blocks, AND entities)
    private final Map<String, StageId> modLocks = new ConcurrentHashMap<>();

    // Name patterns -> Required Stage (case-insensitive substring matching)
    private final Map<String, StageId> nameLocks = new ConcurrentHashMap<>();

    // Interaction locks: key = type:heldItem:targetBlock
    private final Map<String, InteractionLockEntry> interactionLocks = new ConcurrentHashMap<>();

    // Entity type ID -> Required Stage
    private final Map<ResourceLocation, StageId> entityLocks = new ConcurrentHashMap<>();

    // Entity Tag -> Required Stage
    private final Map<ResourceLocation, StageId> entityTagLocks = new ConcurrentHashMap<>();

    // Entity Mod ID -> Required Stage (lock all entities from a mod)
    private final Map<String, StageId> entityModLocks = new ConcurrentHashMap<>();

    // v1.3: Global whitelist of items that are ALWAYS unlocked (bypass all lock checks)
    private final Set<ResourceLocation> unlockedItems = ConcurrentHashMap.newKeySet();

    // v1.4: Global whitelist of blocks that are ALWAYS unlocked (bypass all block lock checks)
    private final Set<ResourceLocation> unlockedBlocks = ConcurrentHashMap.newKeySet();

    // v1.3: Global whitelist of entities that are ALWAYS unlocked (bypass all entity lock checks)
    private final Set<ResourceLocation> unlockedEntities = ConcurrentHashMap.newKeySet();

    // v1.4: Global whitelist of fluids that are ALWAYS unlocked (bypass mod locks in EMI/JEI)
    private final Set<ResourceLocation> unlockedFluids = ConcurrentHashMap.newKeySet();

    // Cache for item -> stage lookups (rebuilt when registry changes)
    private final Map<Item, Optional<StageId>> itemStageCache = new ConcurrentHashMap<>();

    private static LockRegistry INSTANCE;

    public static LockRegistry getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new LockRegistry();
        }
        return INSTANCE;
    }

    private LockRegistry() {}

    /**
     * Clear all locks and cache
     */
    public void clear() {
        itemLocks.clear();
        itemTagLocks.clear();
        itemModLocks.clear();
        recipeLocks.clear();
        recipeTagLocks.clear();
        blockLocks.clear();
        blockTagLocks.clear();
        blockModLocks.clear();
        fluidLocks.clear();
        fluidTagLocks.clear();
        fluidModLocks.clear();
        dimensionLocks.clear();
        modLocks.clear();
        nameLocks.clear();
        interactionLocks.clear();
        entityLocks.clear();
        entityTagLocks.clear();
        entityModLocks.clear();
        unlockedItems.clear();
        unlockedBlocks.clear();
        unlockedEntities.clear();
        unlockedFluids.clear();
        clearCache();
    }

    /**
     * Clear the cache (call after lock changes)
     */
    public void clearCache() {
        itemStageCache.clear();
        resolvedItemLocksCache = null;
    }

    /**
     * Register all locks from a stage definition
     */
    public void registerStage(StageDefinition stage) {
        StageId stageId = stage.getId();
        LockDefinition locks = stage.getLocks();

        // Register item locks
        for (String itemId : locks.getItems()) {
            registerItemLock(itemId, stageId);
        }

        // Register item tag locks
        for (String tagId : locks.getItemTags()) {
            registerItemTagLock(tagId, stageId);
        }

        // Register item mod locks (locks only items from a mod, not blocks/entities)
        for (String modId : locks.getItemMods()) {
            registerItemModLock(modId, stageId);
        }

        // Register recipe locks
        for (String recipeId : locks.getRecipes()) {
            registerRecipeLock(recipeId, stageId);
        }

        // Register recipe tag locks
        for (String tagId : locks.getRecipeTags()) {
            registerRecipeTagLock(tagId, stageId);
        }

        // Register block locks
        for (String blockId : locks.getBlocks()) {
            registerBlockLock(blockId, stageId);
        }

        // Register block tag locks
        for (String tagId : locks.getBlockTags()) {
            registerBlockTagLock(tagId, stageId);
        }

        // Register block mod locks (locks only blocks from a mod, not items/entities)
        for (String modId : locks.getBlockMods()) {
            registerBlockModLock(modId, stageId);
        }

        // Register fluid locks (v1.4 - hide from EMI/JEI)
        for (String fluidId : locks.getFluids()) {
            registerFluidLock(fluidId, stageId);
        }

        // Register fluid tag locks (v1.4)
        for (String tagId : locks.getFluidTags()) {
            registerFluidTagLock(tagId, stageId);
        }

        // Register fluid mod locks (v1.4 - hide all fluids from a mod in EMI/JEI)
        for (String modId : locks.getFluidMods()) {
            registerFluidModLock(modId, stageId);
        }

        // Register dimension locks
        for (String dimId : locks.getDimensions()) {
            registerDimensionLock(dimId, stageId);
        }

        // Register mod locks (locks items, blocks, AND entities from a mod)
        for (String modId : locks.getMods()) {
            registerModLock(modId, stageId);
        }

        // Register name locks
        for (String name : locks.getNames()) {
            registerNameLock(name, stageId);
        }

        // Register interaction locks
        for (LockDefinition.InteractionLock interaction : locks.getInteractions()) {
            registerInteractionLock(interaction, stageId);
        }

        // Register entity locks
        for (String entityId : locks.getEntities()) {
            registerEntityLock(entityId, stageId);
        }

        // Register entity tag locks
        for (String tagId : locks.getEntityTags()) {
            registerEntityTagLock(tagId, stageId);
        }

        // Register entity mod locks (lock all entities from a mod)
        for (String modId : locks.getEntityMods()) {
            registerEntityModLock(modId, stageId);
        }

        // Register unlocked items (v1.3 whitelist exceptions)
        for (String itemId : locks.getUnlockedItems()) {
            ResourceLocation rl = parseResourceLocation(itemId);
            if (rl != null) {
                unlockedItems.add(rl);
                LOGGER.debug("Registered whitelist item for stage {}: {}", stageId, itemId);
            } else {
                LOGGER.warn("Invalid unlocked item ID in stage {}: {}", stageId, itemId);
            }
        }

        // Register unlocked blocks (v1.4 whitelist exceptions for blocks)
        for (String blockId : locks.getUnlockedBlocks()) {
            ResourceLocation rl = parseResourceLocation(blockId);
            if (rl != null) {
                unlockedBlocks.add(rl);
                LOGGER.debug("Registered whitelist block for stage {}: {}", stageId, blockId);
            } else {
                LOGGER.warn("Invalid unlocked block ID in stage {}: {}", stageId, blockId);
            }
        }

        // Register unlocked entities (v1.3 whitelist exceptions for entities)
        for (String entityId : locks.getUnlockedEntities()) {
            ResourceLocation rl = parseResourceLocation(entityId);
            if (rl != null) {
                unlockedEntities.add(rl);
                LOGGER.debug("Registered whitelist entity for stage {}: {}", stageId, entityId);
            } else {
                LOGGER.warn("Invalid unlocked entity ID in stage {}: {}", stageId, entityId);
            }
        }

        // Register unlocked fluids (v1.4 whitelist exceptions for fluids)
        for (String fluidId : locks.getUnlockedFluids()) {
            ResourceLocation rl = parseResourceLocation(fluidId);
            if (rl != null) {
                unlockedFluids.add(rl);
                LOGGER.debug("Registered whitelist fluid for stage {}: {}", stageId, fluidId);
            } else {
                LOGGER.warn("Invalid unlocked fluid ID in stage {}: {}", stageId, fluidId);
            }
        }

        LOGGER.debug("Registered locks for stage: {}", stageId);
    }

    private void registerItemLock(String itemId, StageId stageId) {
        ResourceLocation rl = parseResourceLocation(itemId);
        if (rl != null) {
            itemLocks.put(rl, stageId);
        } else {
            LOGGER.warn("Invalid item ID in stage {}: {}", stageId, itemId);
        }
    }

    private void registerItemTagLock(String tagId, StageId stageId) {
        String cleanTag = tagId.startsWith("#") ? tagId.substring(1) : tagId;
        ResourceLocation rl = parseResourceLocation(cleanTag);
        if (rl != null) {
            itemTagLocks.put(rl, stageId);
        } else {
            LOGGER.warn("Invalid item tag in stage {}: {}", stageId, tagId);
        }
    }

    private void registerRecipeLock(String recipeId, StageId stageId) {
        ResourceLocation rl = parseResourceLocation(recipeId);
        if (rl != null) {
            recipeLocks.put(rl, stageId);
        } else {
            LOGGER.warn("Invalid recipe ID in stage {}: {}", stageId, recipeId);
        }
    }

    private void registerRecipeTagLock(String tagId, StageId stageId) {
        String cleanTag = tagId.startsWith("#") ? tagId.substring(1) : tagId;
        ResourceLocation rl = parseResourceLocation(cleanTag);
        if (rl != null) {
            recipeTagLocks.put(rl, stageId);
        } else {
            LOGGER.warn("Invalid recipe tag in stage {}: {}", stageId, tagId);
        }
    }

    private void registerBlockLock(String blockId, StageId stageId) {
        ResourceLocation rl = parseResourceLocation(blockId);
        if (rl != null) {
            blockLocks.put(rl, stageId);
        } else {
            LOGGER.warn("Invalid block ID in stage {}: {}", stageId, blockId);
        }
    }

    private void registerBlockTagLock(String tagId, StageId stageId) {
        String cleanTag = tagId.startsWith("#") ? tagId.substring(1) : tagId;
        ResourceLocation rl = parseResourceLocation(cleanTag);
        if (rl != null) {
            blockTagLocks.put(rl, stageId);
        } else {
            LOGGER.warn("Invalid block tag in stage {}: {}", stageId, tagId);
        }
    }

    private void registerDimensionLock(String dimId, StageId stageId) {
        ResourceLocation rl = parseResourceLocation(dimId);
        if (rl != null) {
            dimensionLocks.put(rl, stageId);
        } else {
            LOGGER.warn("Invalid dimension ID in stage {}: {}", stageId, dimId);
        }
    }

    private void registerModLock(String modId, StageId stageId) {
        modLocks.put(modId.toLowerCase(), stageId);
    }

    private void registerItemModLock(String modId, StageId stageId) {
        itemModLocks.put(modId.toLowerCase(), stageId);
        LOGGER.debug("Registered item mod lock for stage {}: {}", stageId, modId);
    }

    private void registerBlockModLock(String modId, StageId stageId) {
        blockModLocks.put(modId.toLowerCase(), stageId);
        LOGGER.debug("Registered block mod lock for stage {}: {}", stageId, modId);
    }

    private void registerFluidLock(String fluidId, StageId stageId) {
        ResourceLocation rl = parseResourceLocation(fluidId);
        if (rl != null) {
            fluidLocks.put(rl, stageId);
            LOGGER.debug("Registered fluid lock for stage {}: {}", stageId, fluidId);
        } else {
            LOGGER.warn("Invalid fluid ID in stage {}: {}", stageId, fluidId);
        }
    }

    private void registerFluidTagLock(String tagId, StageId stageId) {
        String cleanTag = tagId.startsWith("#") ? tagId.substring(1) : tagId;
        ResourceLocation rl = parseResourceLocation(cleanTag);
        if (rl != null) {
            fluidTagLocks.put(rl, stageId);
            LOGGER.debug("Registered fluid tag lock for stage {}: {}", stageId, tagId);
        } else {
            LOGGER.warn("Invalid fluid tag in stage {}: {}", stageId, tagId);
        }
    }

    private void registerFluidModLock(String modId, StageId stageId) {
        fluidModLocks.put(modId.toLowerCase(), stageId);
        LOGGER.debug("Registered fluid mod lock for stage {}: {}", stageId, modId);
    }

    private void registerNameLock(String name, StageId stageId) {
        nameLocks.put(name.toLowerCase(), stageId);
    }

    private void registerInteractionLock(LockDefinition.InteractionLock interaction, StageId stageId) {
        String key = buildInteractionKey(interaction.getType(), interaction.getHeldItem(), interaction.getTargetBlock());
        interactionLocks.put(key, new InteractionLockEntry(
            interaction.getType(),
            interaction.getHeldItem(),
            interaction.getTargetBlock(),
            interaction.getDescription(),
            stageId
        ));
    }

    private void registerEntityLock(String entityId, StageId stageId) {
        ResourceLocation rl = parseResourceLocation(entityId);
        if (rl != null) {
            entityLocks.put(rl, stageId);
        } else {
            LOGGER.warn("Invalid entity ID in stage {}: {}", stageId, entityId);
        }
    }

    private void registerEntityTagLock(String tagId, StageId stageId) {
        String cleanTag = tagId.startsWith("#") ? tagId.substring(1) : tagId;
        ResourceLocation rl = parseResourceLocation(cleanTag);
        if (rl != null) {
            entityTagLocks.put(rl, stageId);
        } else {
            LOGGER.warn("Invalid entity tag in stage {}: {}", stageId, tagId);
        }
    }

    private void registerEntityModLock(String modId, StageId stageId) {
        entityModLocks.put(modId.toLowerCase(), stageId);
        LOGGER.debug("Registered entity mod lock for stage {}: {}", stageId, modId);
    }

    private String buildInteractionKey(String type, String heldItem, String targetBlock) {
        return type + ":" + (heldItem != null ? heldItem : "*") + ":" + (targetBlock != null ? targetBlock : "*");
    }

    private ResourceLocation parseResourceLocation(String str) {
        try {
            return ResourceLocation.parse(str);
        } catch (Exception e) {
            return null;
        }
    }

    // ==================== Query Methods ====================

    /**
     * Get the required stage for an item (checks all lock types)
     */
    public Optional<StageId> getRequiredStage(Item item) {
        return itemStageCache.computeIfAbsent(item, this::computeRequiredStageForItem);
    }

    private Optional<StageId> computeRequiredStageForItem(Item item) {
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
        if (itemId == null) {
            return Optional.empty();
        }

        // v1.3: Check whitelist first - if item is whitelisted, it's never locked
        if (unlockedItems.contains(itemId)) {
            return Optional.empty();
        }

        // Check direct item lock
        StageId directLock = itemLocks.get(itemId);
        if (directLock != null) {
            return Optional.of(directLock);
        }

        // Check item tags
        for (Map.Entry<ResourceLocation, StageId> entry : itemTagLocks.entrySet()) {
            TagKey<Item> tagKey = TagKey.create(Registries.ITEM, entry.getKey());
            if (item.builtInRegistryHolder().is(tagKey)) {
                return Optional.of(entry.getValue());
            }
        }

        // Check item mod lock (item_mods = ["mekanism"] - locks only items from that mod)
        String modId = itemId.getNamespace().toLowerCase();
        StageId itemModLock = itemModLocks.get(modId);
        if (itemModLock != null) {
            return Optional.of(itemModLock);
        }

        // Check general mod lock (mods = ["mekanism"] - locks items, blocks, AND entities)
        StageId modLock = modLocks.get(modId);
        if (modLock != null) {
            return Optional.of(modLock);
        }

        // Check name locks
        String itemIdStr = itemId.toString().toLowerCase();
        for (Map.Entry<String, StageId> entry : nameLocks.entrySet()) {
            if (itemIdStr.contains(entry.getKey())) {
                return Optional.of(entry.getValue());
            }
        }

        return Optional.empty();
    }

    /**
     * Get the required stage for a recipe
     */
    public Optional<StageId> getRequiredStageForRecipe(ResourceLocation recipeId) {
        if (recipeId == null) {
            return Optional.empty();
        }

        // Check direct recipe lock
        StageId directLock = recipeLocks.get(recipeId);
        if (directLock != null) {
            return Optional.of(directLock);
        }

        // Check mod lock for recipe
        String modId = recipeId.getNamespace().toLowerCase();
        StageId modLock = modLocks.get(modId);
        if (modLock != null) {
            return Optional.of(modLock);
        }

        return Optional.empty();
    }

    /**
     * Get the required stage for a block
     */
    public Optional<StageId> getRequiredStageForBlock(Block block) {
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(block);
        if (blockId == null) {
            return Optional.empty();
        }

        // v1.4: Check block whitelist first
        if (unlockedBlocks.contains(blockId)) {
            return Optional.empty();
        }
        // v1.1: Also check item whitelist - if block (or its item form) is whitelisted, it's never locked
        if (unlockedItems.contains(blockId)) {
            return Optional.empty();
        }
        // Also check the item form of the block
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(block.asItem());
        if (itemId != null && unlockedItems.contains(itemId)) {
            return Optional.empty();
        }

        // Check direct block lock
        StageId directLock = blockLocks.get(blockId);
        if (directLock != null) {
            return Optional.of(directLock);
        }

        // Check block tags
        for (Map.Entry<ResourceLocation, StageId> entry : blockTagLocks.entrySet()) {
            TagKey<Block> tagKey = TagKey.create(Registries.BLOCK, entry.getKey());
            if (block.builtInRegistryHolder().is(tagKey)) {
                return Optional.of(entry.getValue());
            }
        }

        // Check block mod lock (block_mods = ["mekanism"] - locks only blocks from that mod)
        String modId = blockId.getNamespace().toLowerCase();
        StageId blockModLock = blockModLocks.get(modId);
        if (blockModLock != null) {
            return Optional.of(blockModLock);
        }

        // Check general mod lock (mods = ["mekanism"] - locks items, blocks, AND entities)
        StageId modLock = modLocks.get(modId);
        if (modLock != null) {
            return Optional.of(modLock);
        }

        // Check name locks
        String blockIdStr = blockId.toString().toLowerCase();
        for (Map.Entry<String, StageId> entry : nameLocks.entrySet()) {
            if (blockIdStr.contains(entry.getKey())) {
                return Optional.of(entry.getValue());
            }
        }

        return Optional.empty();
    }

    /**
     * Get the required stage for a dimension
     */
    public Optional<StageId> getRequiredStageForDimension(ResourceLocation dimensionId) {
        if (dimensionId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(dimensionLocks.get(dimensionId));
    }

    /**
     * Get the required stage for an interaction
     */
    public Optional<StageId> getRequiredStageForInteraction(String type, String heldItem, String targetBlock) {
        // Check exact match
        String exactKey = buildInteractionKey(type, heldItem, targetBlock);
        InteractionLockEntry exactMatch = interactionLocks.get(exactKey);
        if (exactMatch != null) {
            return Optional.of(exactMatch.requiredStage);
        }

        // Check with wildcards
        for (InteractionLockEntry entry : interactionLocks.values()) {
            if (entry.matches(type, heldItem, targetBlock)) {
                return Optional.of(entry.requiredStage);
            }
        }

        return Optional.empty();
    }

    /**
     * Check if an item is locked (regardless of which stage)
     */
    public boolean isItemLocked(Item item) {
        return getRequiredStage(item).isPresent();
    }

    /**
     * Get the required stage for an entity type.
     * Checks: whitelist → direct entity lock → entity tags → entity mod locks → general mod locks → name locks.
     */
    public Optional<StageId> getRequiredStageForEntity(net.minecraft.world.entity.EntityType<?> entityType) {
        ResourceLocation entityId = BuiltInRegistries.ENTITY_TYPE.getKey(entityType);
        if (entityId == null) {
            return Optional.empty();
        }

        // v1.3: Check whitelist first - if entity is whitelisted, it's never locked
        if (unlockedEntities.contains(entityId)) {
            return Optional.empty();
        }

        // Check direct entity lock
        StageId directLock = entityLocks.get(entityId);
        if (directLock != null) {
            return Optional.of(directLock);
        }

        // Check entity tags
        for (Map.Entry<ResourceLocation, StageId> entry : entityTagLocks.entrySet()) {
            TagKey<net.minecraft.world.entity.EntityType<?>> tagKey = TagKey.create(Registries.ENTITY_TYPE, entry.getKey());
            if (entityType.builtInRegistryHolder().is(tagKey)) {
                return Optional.of(entry.getValue());
            }
        }

        // Check entity mod locks (entity_mods = ["mekanism"])
        String modId = entityId.getNamespace().toLowerCase();
        StageId entityModLock = entityModLocks.get(modId);
        if (entityModLock != null) {
            return Optional.of(entityModLock);
        }

        // Also check general mod locks (mods = ["mekanism"] also locks entities from that mod)
        StageId modLock = modLocks.get(modId);
        if (modLock != null) {
            return Optional.of(modLock);
        }

        // Check name locks
        String entityIdStr = entityId.toString().toLowerCase();
        for (Map.Entry<String, StageId> entry : nameLocks.entrySet()) {
            if (entityIdStr.contains(entry.getKey())) {
                return Optional.of(entry.getValue());
            }
        }

        return Optional.empty();
    }

    /**
     * Check if an entity type is locked (regardless of which stage)
     */
    public boolean isEntityLocked(net.minecraft.world.entity.EntityType<?> entityType) {
        return getRequiredStageForEntity(entityType).isPresent();
    }

    /**
     * Get all locked items (for EMI integration)
     */
    public Set<ResourceLocation> getAllLockedItems() {
        return Collections.unmodifiableSet(itemLocks.keySet());
    }

    /**
     * Get all item locks with their required stages (for network sync)
     */
    public Map<ResourceLocation, StageId> getAllItemLocks() {
        return Collections.unmodifiableMap(itemLocks);
    }

    // Cache for resolved item locks (includes name patterns, tags, mod locks)
    // Cleared when lock registry changes (via clear() or clearCache())
    private Map<ResourceLocation, StageId> resolvedItemLocksCache = null;

    /**
     * Get ALL resolved item locks including name patterns, tags, and mod locks.
     * This iterates all registered items and checks each one against all lock types.
     * Used for syncing complete lock data to clients for EMI integration.
     *
     * <p>Results are cached for performance. Cache is cleared when registry changes.
     */
    public Map<ResourceLocation, StageId> getAllResolvedItemLocks() {
        // Return cached result if available
        if (resolvedItemLocksCache != null) {
            return resolvedItemLocksCache;
        }

        long startTime = System.currentTimeMillis();
        Map<ResourceLocation, StageId> resolved = new HashMap<>();

        // Start with direct item locks
        resolved.putAll(itemLocks);

        // Only iterate all items if we have pattern-based locks
        boolean hasPatternLocks = !itemTagLocks.isEmpty() || !itemModLocks.isEmpty() || !modLocks.isEmpty() || !nameLocks.isEmpty();

        if (hasPatternLocks) {
            // Debug: log which mod locks are active
            if (!modLocks.isEmpty()) {
                LOGGER.debug("[ProgressiveStages] Active mod locks: {}", modLocks.keySet());
            }
            if (!itemModLocks.isEmpty()) {
                LOGGER.debug("[ProgressiveStages] Active item mod locks: {}", itemModLocks.keySet());
            }

            int modLockCount = 0;
            int itemModLockCount = 0;
            int tagLockCount = 0;
            int nameLockCount = 0;

            // Iterate all registered items and check name patterns, tags, and mod locks
            for (Item item : BuiltInRegistries.ITEM) {
                ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
                if (itemId == null) continue;

                // Skip if already has direct lock
                if (resolved.containsKey(itemId)) continue;

                // Check item tags
                boolean lockedByTag = false;
                for (Map.Entry<ResourceLocation, StageId> entry : itemTagLocks.entrySet()) {
                    TagKey<Item> tagKey = TagKey.create(Registries.ITEM, entry.getKey());
                    if (item.builtInRegistryHolder().is(tagKey)) {
                        resolved.put(itemId, entry.getValue());
                        tagLockCount++;
                        lockedByTag = true;
                        break;
                    }
                }

                // Skip if already locked by tag
                if (lockedByTag) continue;

                // Check item mod locks (item_mods = ["mekanism"])
                String modId = itemId.getNamespace().toLowerCase();
                StageId itemModLock = itemModLocks.get(modId);
                if (itemModLock != null) {
                    resolved.put(itemId, itemModLock);
                    itemModLockCount++;
                    continue;
                }

                // Check general mod locks (mods = ["mekanism"])
                StageId modLock = modLocks.get(modId);
                if (modLock != null) {
                    resolved.put(itemId, modLock);
                    modLockCount++;
                    continue;
                }

                // Check name pattern locks
                String itemIdStr = itemId.toString().toLowerCase();
                for (Map.Entry<String, StageId> entry : nameLocks.entrySet()) {
                    if (itemIdStr.contains(entry.getKey())) {
                        resolved.put(itemId, entry.getValue());
                        nameLockCount++;
                        break;
                    }
                }
            }

            LOGGER.debug("[ProgressiveStages] Lock resolution: {} by item_mods, {} by mods, {} by tag, {} by name pattern",
                itemModLockCount, modLockCount, tagLockCount, nameLockCount);
        }

        // Remove whitelisted items from the resolved set
        // This ensures ClientLockCache (and thus EMI/tooltips) won't show whitelisted items as locked
        if (!unlockedItems.isEmpty()) {
            int beforeSize = resolved.size();
            for (ResourceLocation unlocked : unlockedItems) {
                resolved.remove(unlocked);
            }
            int removedCount = beforeSize - resolved.size();
            if (removedCount > 0) {
                LOGGER.debug("[ProgressiveStages] Removed {} whitelisted items from resolved lock set", removedCount);
            }
        }

        long elapsed = System.currentTimeMillis() - startTime;
        if (elapsed > 100) {
            LOGGER.info("[ProgressiveStages] Resolved {} item locks in {}ms (caching result)", resolved.size(), elapsed);
        } else {
            LOGGER.debug("[ProgressiveStages] Resolved {} item locks in {}ms", resolved.size(), elapsed);
        }

        // Cache the result
        resolvedItemLocksCache = Collections.unmodifiableMap(resolved);
        return resolvedItemLocksCache;
    }

    /**
     * Invalidate the resolved item locks cache.
     * Call this when lock definitions change.
     */
    public void invalidateResolvedCache() {
        resolvedItemLocksCache = null;
    }

    /**
     * Get all locked recipes
     */
    public Set<ResourceLocation> getAllLockedRecipes() {
        return Collections.unmodifiableSet(recipeLocks.keySet());
    }

    /**
     * Get all locked mods
     */
    public Set<String> getAllLockedMods() {
        return Collections.unmodifiableSet(modLocks.keySet());
    }

    /**
     * Get the stage required for a mod lock.
     * Checks general mod locks (mods = ["modid"]).
     */
    public Optional<StageId> getModLockStage(String modId) {
        StageId stage = modLocks.get(modId.toLowerCase());
        return Optional.ofNullable(stage);
    }

    /**
     * Get all unlocked (whitelisted) fluids.
     * These fluids should be visible in EMI/JEI even if their mod is locked.
     */
    public Set<ResourceLocation> getUnlockedFluids() {
        return Collections.unmodifiableSet(unlockedFluids);
    }

    /**
     * Check if a fluid is whitelisted (should bypass mod locks).
     */
    public boolean isFluidUnlocked(ResourceLocation fluidId) {
        return fluidId != null && unlockedFluids.contains(fluidId);
    }

    /**
     * Get all unlocked (whitelisted) blocks.
     */
    public Set<ResourceLocation> getUnlockedBlocks() {
        return Collections.unmodifiableSet(unlockedBlocks);
    }

    /**
     * Check if a block is whitelisted (should bypass mod/tag locks).
     */
    public boolean isBlockUnlocked(ResourceLocation blockId) {
        return blockId != null && unlockedBlocks.contains(blockId);
    }

    /**
     * Get all directly locked fluids (fluids = ["..."])
     */
    public Map<ResourceLocation, StageId> getAllFluidLocks() {
        return Collections.unmodifiableMap(fluidLocks);
    }

    /**
     * Get all fluid tag locks
     */
    public Map<ResourceLocation, StageId> getAllFluidTagLocks() {
        return Collections.unmodifiableMap(fluidTagLocks);
    }

    /**
     * Get all fluid mod locks (fluid_mods = ["..."])
     */
    public Set<String> getAllLockedFluidMods() {
        return Collections.unmodifiableSet(fluidModLocks.keySet());
    }

    /**
     * Get the stage required for a fluid mod lock.
     */
    public Optional<StageId> getFluidModLockStage(String modId) {
        StageId stage = fluidModLocks.get(modId.toLowerCase());
        return Optional.ofNullable(stage);
    }

    /**
     * Check if a fluid is locked (direct lock, tag lock, fluid_mods lock, general mods lock, or name lock).
     * Returns the required stage, or empty if not locked.
     *
     * Check order: whitelist → direct fluid lock → fluid mod lock → general mod lock → name patterns
     */
    public Optional<StageId> getRequiredStageForFluid(ResourceLocation fluidId) {
        if (fluidId == null) {
            return Optional.empty();
        }

        // Check whitelist first
        if (unlockedFluids.contains(fluidId)) {
            return Optional.empty();
        }

        // Check direct fluid lock
        StageId directLock = fluidLocks.get(fluidId);
        if (directLock != null) {
            return Optional.of(directLock);
        }

        // Check fluid mod lock
        String modId = fluidId.getNamespace().toLowerCase();
        StageId fluidModLock = fluidModLocks.get(modId);
        if (fluidModLock != null) {
            return Optional.of(fluidModLock);
        }

        // Check general mod lock (mods = ["modid"] also locks fluids)
        StageId modLock = modLocks.get(modId);
        if (modLock != null) {
            return Optional.of(modLock);
        }

        // Check name locks (names = ["diamond"] also locks fluids containing "diamond")
        String fluidIdStr = fluidId.toString().toLowerCase();
        for (Map.Entry<String, StageId> entry : nameLocks.entrySet()) {
            if (fluidIdStr.contains(entry.getKey())) {
                return Optional.of(entry.getValue());
            }
        }

        return Optional.empty();
    }

    /**
     * Get all name patterns
     */
    public Set<String> getAllNamePatterns() {
        return Collections.unmodifiableSet(nameLocks.keySet());
    }

    /**
     * Get the stage required for a name pattern lock.
     */
    public Optional<StageId> getNamePatternStage(String pattern) {
        StageId stage = nameLocks.get(pattern.toLowerCase());
        return Optional.ofNullable(stage);
    }

    /**
     * Data class for interaction lock entries
     */
    public static class InteractionLockEntry {
        public final String type;
        public final String heldItem;
        public final String targetBlock;
        public final String description;
        public final StageId requiredStage;

        public InteractionLockEntry(String type, String heldItem, String targetBlock, String description, StageId requiredStage) {
            this.type = type;
            this.heldItem = heldItem;
            this.targetBlock = targetBlock;
            this.description = description;
            this.requiredStage = requiredStage;
        }

        public boolean matches(String checkType, String checkHeldItem, String checkTargetBlock) {
            if (!type.equals(checkType)) {
                return false;
            }

            // Check held item (supports tags with #)
            if (heldItem != null && !heldItem.equals("*")) {
                if (heldItem.startsWith("#")) {
                    // Tag matching would require more complex logic
                    // For now, just check if the item string contains the tag name
                    String tagName = heldItem.substring(1);
                    if (!checkHeldItem.contains(tagName)) {
                        return false;
                    }
                } else if (!heldItem.equals(checkHeldItem)) {
                    return false;
                }
            }

            // Check target block (supports tags with #)
            if (targetBlock != null && !targetBlock.equals("*")) {
                if (targetBlock.startsWith("#")) {
                    String tagName = targetBlock.substring(1);
                    if (!checkTargetBlock.contains(tagName)) {
                        return false;
                    }
                } else if (!targetBlock.equals(checkTargetBlock)) {
                    return false;
                }
            }

            return true;
        }
    }
}
