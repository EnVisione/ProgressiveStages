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

import java.util.Optional;

/**
 * Handles interaction locking (item-on-block, block right-click, etc.)
 * Useful for Create mod style interactions
 */
public class InteractionEnforcer {

    public static final String TYPE_ITEM_ON_BLOCK = "item_on_block";
    public static final String TYPE_BLOCK_RIGHT_CLICK = "block_right_click";

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
     * Check if an interaction is locked due to tag matching
     */
    private static boolean isInteractionLockedByTag(ServerPlayer player, String type, ItemStack heldItem, Block targetBlock) {
        // This would require iterating through all interaction locks and checking tag matches
        // For performance, we rely on the pre-computed matches in LockRegistry
        // Tag matching is handled when registering the lock
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
     * Notify player that an interaction is locked
     */
    public static void notifyLocked(ServerPlayer player, ItemStack heldItem, Block targetBlock) {
        Optional<StageId> required = getRequiredStage(heldItem, targetBlock);
        if (required.isPresent()) {
            ItemEnforcer.notifyLocked(player, required.get(), "This interaction");
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
