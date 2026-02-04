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
