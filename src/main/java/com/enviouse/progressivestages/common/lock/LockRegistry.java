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

    // Recipe ID -> Required Stage
    private final Map<ResourceLocation, StageId> recipeLocks = new ConcurrentHashMap<>();

    // Recipe Tag -> Required Stage
    private final Map<ResourceLocation, StageId> recipeTagLocks = new ConcurrentHashMap<>();

    // Block ID -> Required Stage
    private final Map<ResourceLocation, StageId> blockLocks = new ConcurrentHashMap<>();

    // Block Tag -> Required Stage
    private final Map<ResourceLocation, StageId> blockTagLocks = new ConcurrentHashMap<>();

    // Dimension ID -> Required Stage
    private final Map<ResourceLocation, StageId> dimensionLocks = new ConcurrentHashMap<>();

    // Mod ID -> Required Stage
    private final Map<String, StageId> modLocks = new ConcurrentHashMap<>();

    // Name patterns -> Required Stage (case-insensitive substring matching)
    private final Map<String, StageId> nameLocks = new ConcurrentHashMap<>();

    // Interaction locks: key = type:heldItem:targetBlock
    private final Map<String, InteractionLockEntry> interactionLocks = new ConcurrentHashMap<>();

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
        recipeLocks.clear();
        recipeTagLocks.clear();
        blockLocks.clear();
        blockTagLocks.clear();
        dimensionLocks.clear();
        modLocks.clear();
        nameLocks.clear();
        interactionLocks.clear();
        clearCache();
    }

    /**
     * Clear the cache (call after lock changes)
     */
    public void clearCache() {
        itemStageCache.clear();
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

        // Register dimension locks
        for (String dimId : locks.getDimensions()) {
            registerDimensionLock(dimId, stageId);
        }

        // Register mod locks
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

        // Check mod lock
        String modId = itemId.getNamespace().toLowerCase();
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

        // Check mod lock
        String modId = blockId.getNamespace().toLowerCase();
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
     * Get all name patterns
     */
    public Set<String> getAllNamePatterns() {
        return Collections.unmodifiableSet(nameLocks.keySet());
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
