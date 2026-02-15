package com.enviouse.progressivestages.server.triggers;

import com.enviouse.progressivestages.common.api.ProgressiveStagesAPI;
import com.enviouse.progressivestages.common.api.StageCause;
import com.enviouse.progressivestages.common.api.StageId;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Triggers stage grants when players pick up specific items.
 *
 * <p>This is an event-driven system - no per-tick polling.
 * When an item is picked up, this handler checks if there's a mapped stage
 * and grants it if so.
 *
 * <p>Also performs a one-time inventory scan on player login to grant
 * stages for items they already possess.
 */
public class ItemPickupStageGrants {

    private static final Logger LOGGER = LogUtils.getLogger();

    // Item ID -> Stage ID mapping
    private static final Map<ResourceLocation, StageId> ITEM_STAGES = new HashMap<>();

    /**
     * Register an item -> stage mapping.
     * When the item is picked up, the stage will be granted.
     *
     * @param itemId The item resource location
     * @param stageId The stage to grant
     */
    public static void registerMapping(ResourceLocation itemId, StageId stageId) {
        ITEM_STAGES.put(itemId, stageId);
        LOGGER.debug("[ProgressiveStages] Registered item pickup stage mapping: {} -> {}",
            itemId, stageId);
    }

    /**
     * Register an item -> stage mapping using strings.
     *
     * @param itemId The item ID (e.g., "minecraft:diamond")
     * @param stageId The stage ID (e.g., "diamond_age")
     */
    public static void registerMapping(String itemId, String stageId) {
        registerMapping(ResourceLocation.parse(itemId), StageId.parse(stageId));
    }

    /**
     * Remove an item -> stage mapping.
     */
    public static void removeMapping(ResourceLocation itemId) {
        ITEM_STAGES.remove(itemId);
    }

    /**
     * Clear all item -> stage mappings.
     */
    public static void clearMappings() {
        ITEM_STAGES.clear();
    }

    /**
     * Get the stage associated with an item, if any.
     */
    public static StageId getStageForItem(ResourceLocation itemId) {
        return ITEM_STAGES.get(itemId);
    }

    /**
     * Get the stage associated with an item.
     */
    public static StageId getStageForItem(Item item) {
        ResourceLocation itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item);
        return ITEM_STAGES.get(itemId);
    }

    /**
     * Load default mappings (can be overridden by config).
     */
    public static void loadDefaultMappings() {
        // Example mappings - these can be overridden by config
        // registerMapping("minecraft:iron_ingot", "iron_age");
        // registerMapping("minecraft:diamond", "diamond_age");

        LOGGER.info("[ProgressiveStages] Item pickup stage mappings loaded: {} mappings",
            ITEM_STAGES.size());
    }

    /**
     * Event handler for item pickup.
     */
    @SubscribeEvent
    public static void onItemPickup(ItemEntityPickupEvent.Pre event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }

        ItemEntity itemEntity = event.getItemEntity();
        ItemStack stack = itemEntity.getItem();
        Item item = stack.getItem();

        ResourceLocation itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item);
        StageId stageId = ITEM_STAGES.get(itemId);

        if (stageId != null) {
            // Check if player already has the stage
            if (!ProgressiveStagesAPI.hasStage(player, stageId)) {
                ProgressiveStagesAPI.grantStage(player, stageId, StageCause.ITEM_PICKUP);
                LOGGER.info("[ProgressiveStages] Granted stage '{}' to {} for picking up '{}'",
                    stageId, player.getName().getString(), itemId);
            }
        }
    }

    /**
     * One-time inventory scan on player login.
     * Grants stages for items the player already has.
     */
    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        // Scan inventory for trigger items
        scanInventoryForStages(player);
    }

    /**
     * Scan player's inventory and grant stages for any trigger items found.
     */
    public static void scanInventoryForStages(ServerPlayer player) {
        if (ITEM_STAGES.isEmpty()) {
            return;
        }

        Set<StageId> currentStages = ProgressiveStagesAPI.getStages(player);
        int granted = 0;

        // Check main inventory
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty()) {
                ResourceLocation itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
                StageId stageId = ITEM_STAGES.get(itemId);

                if (stageId != null && !currentStages.contains(stageId)) {
                    ProgressiveStagesAPI.grantStage(player, stageId, StageCause.INVENTORY_CHECK);
                    currentStages.add(stageId);
                    granted++;
                }
            }
        }

        if (granted > 0) {
            LOGGER.info("[ProgressiveStages] Granted {} stages to {} from inventory scan",
                granted, player.getName().getString());
        }
    }
}

