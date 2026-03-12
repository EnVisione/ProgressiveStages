package com.enviouse.progressivestages.server.enforcement;

import com.enviouse.progressivestages.common.config.StageConfig;
import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Scans player inventory and drops locked items
 */
public class InventoryScanner {

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Scan a player's inventory and drop any locked items
     * @return number of items dropped
     */
    public static int scanAndDropLockedItems(ServerPlayer player) {
        if (!StageConfig.isBlockItemInventory()) {
            return 0;
        }

        Inventory inventory = player.getInventory();
        List<ItemStack> toDrop = new ArrayList<>();

        // Check main inventory
        for (int i = 0; i < inventory.items.size(); i++) {
            ItemStack stack = inventory.items.get(i);
            if (!stack.isEmpty() && !ItemEnforcer.canHoldItem(player, stack)) {
                toDrop.add(stack.copy());
                inventory.items.set(i, ItemStack.EMPTY);
            }
        }

        // Check armor slots
        for (int i = 0; i < inventory.armor.size(); i++) {
            ItemStack stack = inventory.armor.get(i);
            if (!stack.isEmpty() && !ItemEnforcer.canHoldItem(player, stack)) {
                toDrop.add(stack.copy());
                inventory.armor.set(i, ItemStack.EMPTY);
            }
        }

        // Check offhand
        for (int i = 0; i < inventory.offhand.size(); i++) {
            ItemStack stack = inventory.offhand.get(i);
            if (!stack.isEmpty() && !ItemEnforcer.canHoldItem(player, stack)) {
                toDrop.add(stack.copy());
                inventory.offhand.set(i, ItemStack.EMPTY);
            }
        }

        // Drop all locked items
        if (!toDrop.isEmpty()) {
            for (ItemStack stack : toDrop) {
                dropItem(player, stack);
            }

            // Notify once for all dropped items
            if (toDrop.size() == 1) {
                ItemEnforcer.notifyLocked(player, toDrop.get(0).getItem());
            } else {
                // Generic message for multiple items
                ItemEnforcer.playLockSound(player);
                if (StageConfig.isShowLockMessage()) {
                    player.sendSystemMessage(
                        com.enviouse.progressivestages.common.util.TextUtil.parseColorCodes(
                            "&c🔒 Dropped " + toDrop.size() + " locked items from your inventory!"
                        )
                    );
                }
            }

            LOGGER.debug("Dropped {} locked items from player {}", toDrop.size(), player.getName().getString());
        }

        return toDrop.size();
    }

    /**
     * Scan player's hotbar and move any locked items to main inventory.
     * Called when block_item_hotbar = true but block_item_inventory = false.
     * This is a softer alternative that lets players keep items in storage but not in the hotbar.
     *
     * @return number of items moved out of the hotbar
     */
    public static int scanAndMoveLockedItemsFromHotbar(ServerPlayer player) {
        if (!StageConfig.isBlockItemHotbar()) {
            return 0;
        }

        // If block_item_inventory is true, scanAndDropLockedItems handles everything already
        if (StageConfig.isBlockItemInventory()) {
            return 0;
        }

        Inventory inventory = player.getInventory();
        int movedCount = 0;

        // Hotbar slots are indices 0-8 in inventory.items
        for (int hotbarSlot = 0; hotbarSlot < 9; hotbarSlot++) {
            ItemStack stack = inventory.items.get(hotbarSlot);
            if (stack.isEmpty() || ItemEnforcer.canHoldItem(player, stack)) {
                continue;
            }

            // Try to move to main inventory (slots 9-35)
            boolean moved = false;
            for (int invSlot = 9; invSlot < 36; invSlot++) {
                ItemStack existing = inventory.items.get(invSlot);
                if (existing.isEmpty()) {
                    // Move the locked item to this empty main inventory slot
                    inventory.items.set(invSlot, stack.copy());
                    inventory.items.set(hotbarSlot, ItemStack.EMPTY);
                    moved = true;
                    movedCount++;
                    break;
                }
            }

            // If main inventory is full, drop as fallback
            if (!moved) {
                dropItem(player, stack.copy());
                inventory.items.set(hotbarSlot, ItemStack.EMPTY);
                movedCount++;
            }
        }

        if (movedCount > 0) {
            ItemEnforcer.playLockSound(player);
            if (StageConfig.isShowLockMessage()) {
                player.sendSystemMessage(
                    com.enviouse.progressivestages.common.util.TextUtil.parseColorCodes(
                        "&c🔒 Moved " + movedCount + " locked item" + (movedCount > 1 ? "s" : "") + " out of your hotbar!"
                    )
                );
            }
            LOGGER.debug("Moved {} locked items from hotbar for player {}", movedCount, player.getName().getString());
        }

        return movedCount;
    }

    /**
     * Drop an item at the player's feet
     */
    private static void dropItem(ServerPlayer player, ItemStack stack) {
        ItemEntity itemEntity = new ItemEntity(
            player.level(),
            player.getX(),
            player.getY(),
            player.getZ(),
            stack
        );

        // Set pickup delay to prevent immediate re-pickup
        itemEntity.setPickUpDelay(40); // 2 seconds

        player.level().addFreshEntity(itemEntity);
    }
}
