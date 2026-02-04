package com.enviouse.progressivestages.server.enforcement;

import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.config.StageConfig;
import com.enviouse.progressivestages.common.config.StageDefinition;
import com.enviouse.progressivestages.common.lock.LockRegistry;
import com.enviouse.progressivestages.common.stage.StageManager;
import com.enviouse.progressivestages.common.stage.StageOrder;
import com.enviouse.progressivestages.common.util.TextUtil;
import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Handles item-related enforcement: use, pickup, inventory
 */
public class ItemEnforcer {

    private static final Logger LOGGER = LogUtils.getLogger();

    // Cooldown tracking: UUID -> (ItemID -> LastMessageTime)
    private static final Map<UUID, Map<String, Long>> messageCooldowns = new HashMap<>();

    /**
     * Check if a player can use an item
     * @return true if allowed, false if blocked
     */
    public static boolean canUseItem(ServerPlayer player, ItemStack stack) {
        if (!StageConfig.isBlockItemUse()) {
            return true;
        }

        // Creative bypass
        if (StageConfig.isAllowCreativeBypass() && player.isCreative()) {
            return true;
        }

        if (stack.isEmpty()) {
            return true;
        }

        return !isItemLockedForPlayer(player, stack.getItem());
    }

    /**
     * Check if a player can pick up an item
     * @return true if allowed, false if blocked
     */
    public static boolean canPickupItem(ServerPlayer player, ItemStack stack) {
        if (!StageConfig.isBlockItemPickup()) {
            return true;
        }

        // Creative bypass
        if (StageConfig.isAllowCreativeBypass() && player.isCreative()) {
            return true;
        }

        if (stack.isEmpty()) {
            return true;
        }

        return !isItemLockedForPlayer(player, stack.getItem());
    }

    /**
     * Check if a player can hold an item in their inventory
     * @return true if allowed, false if should be dropped
     */
    public static boolean canHoldItem(ServerPlayer player, ItemStack stack) {
        if (!StageConfig.isBlockItemInventory()) {
            return true;
        }

        // Creative bypass
        if (StageConfig.isAllowCreativeBypass() && player.isCreative()) {
            return true;
        }

        if (stack.isEmpty()) {
            return true;
        }

        return !isItemLockedForPlayer(player, stack.getItem());
    }

    /**
     * Check if an item is locked for a specific player
     */
    public static boolean isItemLockedForPlayer(ServerPlayer player, Item item) {
        Optional<StageId> requiredStage = LockRegistry.getInstance().getRequiredStage(item);
        if (requiredStage.isEmpty()) {
            return false;
        }

        return !StageManager.getInstance().hasStage(player, requiredStage.get());
    }

    /**
     * Send lock message and play sound to player
     */
    public static void notifyLocked(ServerPlayer player, Item item) {
        Optional<StageId> requiredStage = LockRegistry.getInstance().getRequiredStage(item);
        if (requiredStage.isEmpty()) {
            return;
        }

        StageId stageId = requiredStage.get();

        // Send message
        if (StageConfig.isShowLockMessage()) {
            String displayName = StageOrder.getInstance().getStageDefinition(stageId)
                .map(StageDefinition::getDisplayName)
                .orElse(stageId.getPath());

            Component message = TextUtil.parseColorCodes(
                "&c🔒 You haven't unlocked this item yet! &7Required: &f" + displayName
            );
            player.sendSystemMessage(message);
        }

        // Play sound
        if (StageConfig.isPlayLockSound()) {
            playLockSound(player);
        }
    }

    /**
     * Send lock message for a generic lock (not item-specific)
     */
    public static void notifyLocked(ServerPlayer player, StageId requiredStage, String type) {
        // Send message
        if (StageConfig.isShowLockMessage()) {
            String displayName = StageOrder.getInstance().getStageDefinition(requiredStage)
                .map(StageDefinition::getDisplayName)
                .orElse(requiredStage.getPath());

            Component message = TextUtil.parseColorCodes(
                "&c🔒 " + type + " is locked! &7Required: &f" + displayName
            );
            player.sendSystemMessage(message);
        }

        // Play sound
        if (StageConfig.isPlayLockSound()) {
            playLockSound(player);
        }
    }

    /**
     * Play the lock notification sound
     */
    public static void playLockSound(ServerPlayer player) {
        try {
            String soundStr = StageConfig.getLockSound();
            ResourceLocation soundLoc = ResourceLocation.parse(soundStr);
            SoundEvent sound = BuiltInRegistries.SOUND_EVENT.get(soundLoc);

            if (sound != null) {
                player.playNotifySound(sound, SoundSource.PLAYERS,
                    StageConfig.getLockSoundVolume(), StageConfig.getLockSoundPitch());
            } else {
                // Fallback to pling
                player.playNotifySound(SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.PLAYERS, 1.0f, 1.0f);
            }
        } catch (Exception e) {
            // Fallback
            player.playNotifySound(SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.PLAYERS, 1.0f, 1.0f);
        }
    }

    /**
     * Send lock notification with cooldown to prevent chat spam
     */
    public static void notifyLockedWithCooldown(ServerPlayer player, Item item) {
        UUID playerId = player.getUUID();
        String itemId = BuiltInRegistries.ITEM.getKey(item).toString();
        long currentTime = System.currentTimeMillis();
        int cooldownMs = StageConfig.getNotificationCooldown();

        // Get or create player's cooldown map
        Map<String, Long> playerCooldowns = messageCooldowns.computeIfAbsent(playerId, k -> new HashMap<>());

        Long lastMessageTime = playerCooldowns.get(itemId);
        if (lastMessageTime == null || currentTime - lastMessageTime >= cooldownMs) {
            // Cooldown expired or first time, send message
            notifyLocked(player, item);
            playerCooldowns.put(itemId, currentTime);
        }
        // If within cooldown, silently block without message
    }

    /**
     * Clear cooldowns for a player (call on logout)
     */
    public static void clearCooldowns(UUID playerId) {
        messageCooldowns.remove(playerId);
    }
}
