package com.enviouse.progressivestages.server.enforcement;

import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.config.StageConfig;
import com.enviouse.progressivestages.common.lock.LockRegistry;
import com.enviouse.progressivestages.common.lock.NameMatcher;
import com.enviouse.progressivestages.common.stage.StageManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import net.minecraft.world.entity.EntityType;

import java.util.Optional;

/**
 * Handles interaction locking (item-on-block, block right-click, item-on-entity, etc.)
 * Useful for Create mod style interactions
 */
public class InteractionEnforcer {

    public static final String TYPE_ITEM_ON_BLOCK = "item_on_block";
    public static final String TYPE_BLOCK_RIGHT_CLICK = "block_right_click";
    public static final String TYPE_ITEM_ON_ENTITY = "item_on_entity";

    /**
     * Check if an interaction is allowed
     * @param player The player performing the interaction
     * @param heldItem The item held by the player (can be empty)
     * @param targetBlock The block being interacted with
     * @return true if allowed, false if blocked
     */
    public static boolean canInteract(ServerPlayer player, ItemStack heldItem, Block targetBlock) {
        if (!StageConfig.isBlockInteractions()) {
            return true;
        }

        // Creative bypass
        if (StageConfig.isAllowCreativeBypass() && player.isCreative()) {
            return true;
        }

        // Get IDs for lookup
        String heldItemId = getItemId(heldItem);
        String targetBlockId = getBlockId(targetBlock);

        // Check item_on_block interactions
        if (!heldItem.isEmpty()) {
            Optional<StageId> required = LockRegistry.getInstance().getRequiredStageForInteraction(
                TYPE_ITEM_ON_BLOCK, heldItemId, targetBlockId
            );

            if (required.isPresent() && !StageManager.getInstance().hasStage(player, required.get())) {
                return false;
            }

            // Also check with tag matching
            if (isInteractionLockedByTag(player, TYPE_ITEM_ON_BLOCK, heldItem, targetBlock)) {
                return false;
            }
        }

        // Check block_right_click interactions
        Optional<StageId> blockClickRequired = LockRegistry.getInstance().getRequiredStageForInteraction(
            TYPE_BLOCK_RIGHT_CLICK, "*", targetBlockId
        );

        if (blockClickRequired.isPresent() && !StageManager.getInstance().hasStage(player, blockClickRequired.get())) {
            return false;
        }

        return true;
    }

    /**
     * Check if an interaction is locked due to tag-pattern matching.
     * Iterates all registered locks of the given type and uses runtime tag resolution
     * via itemMatches() / blockMatches() to handle '#tag' patterns correctly.
     */
    private static boolean isInteractionLockedByTag(ServerPlayer player, String type, ItemStack heldItem, Block targetBlock) {
        for (LockRegistry.InteractionLockEntry entry : LockRegistry.getInstance().getAllInteractionLocksOfType(type)) {
            // Only process entries that actually use a tag pattern — exact IDs are
            // already handled by getRequiredStageForInteraction() above.
            boolean heldIsTag = entry.heldItem != null && entry.heldItem.startsWith("#");
            boolean targetIsTag = entry.targetBlock != null && entry.targetBlock.startsWith("#");
            if (!heldIsTag && !targetIsTag) {
                continue;
            }
            if (itemMatches(heldItem, entry.heldItem) && blockMatches(targetBlock, entry.targetBlock)) {
                if (!StageManager.getInstance().hasStage(player, entry.requiredStage)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Get the required stage for an interaction
     */
    public static Optional<StageId> getRequiredStage(ItemStack heldItem, Block targetBlock) {
        String heldItemId = getItemId(heldItem);
        String targetBlockId = getBlockId(targetBlock);

        // Check item_on_block first
        if (!heldItem.isEmpty()) {
            Optional<StageId> required = LockRegistry.getInstance().getRequiredStageForInteraction(
                TYPE_ITEM_ON_BLOCK, heldItemId, targetBlockId
            );
            if (required.isPresent()) {
                return required;
            }
        }

        // Check block_right_click
        return LockRegistry.getInstance().getRequiredStageForInteraction(
            TYPE_BLOCK_RIGHT_CLICK, "*", targetBlockId
        );
    }

    /**
     * Notify player that an interaction is locked.
     * Checks exact-match locks first, then falls back to tag-pattern locks.
     */
    public static void notifyLocked(ServerPlayer player, ItemStack heldItem, Block targetBlock) {
        Optional<StageId> required = getRequiredStage(heldItem, targetBlock);
        if (required.isPresent()) {
            ItemEnforcer.notifyLocked(player, required.get(), "This interaction");
            return;
        }
        // Fall back to tag-pattern search for notification
        for (LockRegistry.InteractionLockEntry entry : LockRegistry.getInstance().getAllInteractionLocksOfType(TYPE_ITEM_ON_BLOCK)) {
            if (itemMatches(heldItem, entry.heldItem) && blockMatches(targetBlock, entry.targetBlock)) {
                ItemEnforcer.notifyLocked(player, entry.requiredStage, "This interaction");
                return;
            }
        }
    }

    private static String getItemId(ItemStack stack) {
        if (stack.isEmpty()) {
            return "*";
        }
        ResourceLocation rl = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return rl != null ? rl.toString() : "*";
    }

    private static String getBlockId(Block block) {
        ResourceLocation rl = BuiltInRegistries.BLOCK.getKey(block);
        return rl != null ? rl.toString() : "*";
    }

    /**
     * Check if an item matches a pattern (supports tags with #)
     */
    public static boolean itemMatches(ItemStack stack, String pattern) {
        if (pattern == null || pattern.equals("*")) {
            return true;
        }

        if (stack.isEmpty()) {
            return false;
        }

        if (pattern.startsWith("#")) {
            // Tag matching
            String tagName = pattern.substring(1);
            try {
                ResourceLocation tagLoc = ResourceLocation.parse(tagName);
                TagKey<Item> tagKey = TagKey.create(Registries.ITEM, tagLoc);
                return stack.is(tagKey);
            } catch (Exception e) {
                return false;
            }
        } else {
            // Direct ID matching
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
            return itemId != null && itemId.toString().equals(pattern);
        }
    }

    /**
     * Check if a player is allowed to interact with an entity while holding the given item.
     * Handles item_on_entity interaction locks, including wildcard and tag patterns.
     *
     * @param player     The player performing the interaction
     * @param heldItem   The item held by the player
     * @param entityType The entity type being interacted with
     * @return true if allowed, false if blocked
     */
    public static boolean canInteractWithEntity(ServerPlayer player, ItemStack heldItem, EntityType<?> entityType) {
        if (!StageConfig.isBlockInteractions()) {
            return true;
        }

        if (StageConfig.isAllowCreativeBypass() && player.isCreative()) {
            return true;
        }

        String heldItemId = getItemId(heldItem);
        String entityTypeId = getEntityTypeId(entityType);

        // Check exact / wildcard match first
        Optional<StageId> required = LockRegistry.getInstance().getRequiredStageForInteraction(
            TYPE_ITEM_ON_ENTITY, heldItemId, entityTypeId
        );
        if (required.isPresent() && !StageManager.getInstance().hasStage(player, required.get())) {
            return false;
        }

        // Runtime tag-pattern matching
        for (LockRegistry.InteractionLockEntry entry : LockRegistry.getInstance().getAllInteractionLocksOfType(TYPE_ITEM_ON_ENTITY)) {
            boolean heldIsTag = entry.heldItem != null && entry.heldItem.startsWith("#");
            boolean targetIsTag = entry.targetBlock != null && entry.targetBlock.startsWith("#");
            if (!heldIsTag && !targetIsTag) {
                continue;
            }
            if (itemMatches(heldItem, entry.heldItem) && entityTypeMatches(entityType, entry.targetBlock)) {
                if (!StageManager.getInstance().hasStage(player, entry.requiredStage)) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Notify player that an entity interaction is locked
     */
    public static void notifyEntityInteractionLocked(ServerPlayer player, ItemStack heldItem, EntityType<?> entityType) {
        String heldItemId = getItemId(heldItem);
        String entityTypeId = getEntityTypeId(entityType);

        Optional<StageId> required = LockRegistry.getInstance().getRequiredStageForInteraction(
            TYPE_ITEM_ON_ENTITY, heldItemId, entityTypeId
        );
        if (required.isPresent()) {
            ItemEnforcer.notifyLocked(player, required.get(), "This interaction");
            return;
        }
        // Tag-pattern fallback
        for (LockRegistry.InteractionLockEntry entry : LockRegistry.getInstance().getAllInteractionLocksOfType(TYPE_ITEM_ON_ENTITY)) {
            if (itemMatches(heldItem, entry.heldItem) && entityTypeMatches(entityType, entry.targetBlock)) {
                ItemEnforcer.notifyLocked(player, entry.requiredStage, "This interaction");
                return;
            }
        }
    }

    private static String getEntityTypeId(EntityType<?> entityType) {
        ResourceLocation rl = BuiltInRegistries.ENTITY_TYPE.getKey(entityType);
        return rl != null ? rl.toString() : "*";
    }

    /**
     * Check if an entity type matches a pattern (supports tags with #)
     */
    public static boolean entityTypeMatches(EntityType<?> entityType, String pattern) {
        if (pattern == null || pattern.equals("*")) {
            return true;
        }

        if (pattern.startsWith("#")) {
            String tagName = pattern.substring(1);
            try {
                ResourceLocation tagLoc = ResourceLocation.parse(tagName);
                TagKey<EntityType<?>> tagKey = TagKey.create(Registries.ENTITY_TYPE, tagLoc);
                return entityType.builtInRegistryHolder().is(tagKey);
            } catch (Exception e) {
                return false;
            }
        } else {
            ResourceLocation entityId = BuiltInRegistries.ENTITY_TYPE.getKey(entityType);
            return entityId != null && entityId.toString().equals(pattern);
        }
    }

    /**
     * Check if a block matches a pattern (supports tags with #)
     */
    public static boolean blockMatches(Block block, String pattern) {
        if (pattern == null || pattern.equals("*")) {
            return true;
        }

        if (pattern.startsWith("#")) {
            // Tag matching
            String tagName = pattern.substring(1);
            try {
                ResourceLocation tagLoc = ResourceLocation.parse(tagName);
                TagKey<Block> tagKey = TagKey.create(Registries.BLOCK, tagLoc);
                return block.builtInRegistryHolder().is(tagKey);
            } catch (Exception e) {
                return false;
            }
        } else {
            // Direct ID matching
            ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(block);
            return blockId != null && blockId.toString().equals(pattern);
        }
    }
}
