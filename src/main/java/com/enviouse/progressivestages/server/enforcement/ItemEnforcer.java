package com.enviouse.progressivestages.server.enforcement;

import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.api.ItemUseDecision;
import com.enviouse.progressivestages.common.config.StageConfig;
import com.enviouse.progressivestages.common.config.StageDefinition;
import com.enviouse.progressivestages.common.lock.ActiveLockDefinition;
import com.enviouse.progressivestages.common.lock.EnforcementCategory;
import com.enviouse.progressivestages.common.lock.ConditionalRule;
import com.enviouse.progressivestages.common.lock.LockRegistry;
import com.enviouse.progressivestages.common.lock.PrefixEntry;
import com.enviouse.progressivestages.common.stage.StageManager;
import com.enviouse.progressivestages.common.stage.StageOrder;
import com.enviouse.progressivestages.server.structure.StructureSessionManager;
import com.enviouse.progressivestages.server.structure.StructureContextRegistry;
import com.enviouse.progressivestages.common.api.structure.StructureAccessDecision;
import com.enviouse.progressivestages.common.api.structure.StructureAction;
import com.enviouse.progressivestages.common.util.StageDisclosure;
import com.enviouse.progressivestages.common.util.TextUtil;
import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.core.Holder;
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
        return evaluateItemUse(player, stack).allowed();
    }

    public static ItemUseDecision evaluateItemUse(ServerPlayer player, ItemStack stack) {
        LockRegistry reg = LockRegistry.getInstance();
        if (!StageConfig.isBlockItemUse() && !reg.hasEnforcementOverrides()
                && !ConditionalLockEngine.hasRules(ConditionalRule.TargetType.ITEM)
                && !hasActiveItemLocks()
                && !StructureContextRegistry.getInstance().hasProviders()) {
            return ItemUseDecision.allowed(ItemUseDecision.Reason.CATEGORY_DISABLED);
        }

        if (player.isSpectator()) {
            return ItemUseDecision.allowed(ItemUseDecision.Reason.SPECTATOR);
        }

        if (StageConfig.isAllowCreativeBypass() && player.isCreative()) {
            return ItemUseDecision.allowed(ItemUseDecision.Reason.CREATIVE_BYPASS);
        }

        if (stack.isEmpty()) {
            return ItemUseDecision.allowed(ItemUseDecision.Reason.EMPTY_STACK);
        }

        StructureEnforcer.EvaluationResult structure = StructureEnforcer.evaluate(
            player, player.blockPosition(), StructureAction.ITEM_USE);
        if (!structure.allowed()) {
            ItemUseDecision.Polarity polarity = structure.reason()
                == StructureAccessDecision.Reason.MISSING_ACCESS_STAGE
                ? ItemUseDecision.Polarity.MISSING_STAGE
                : ItemUseDecision.Polarity.PRESENT_IN_CONTEXT;
            return new ItemUseDecision(false, Optional.ofNullable(structure.displayStage()),
                ItemUseDecision.Category.ITEM_USE, polarity,
                Optional.ofNullable(structure.providerId()), Optional.ofNullable(structure.sessionId()),
                ItemUseDecision.Reason.STRUCTURE_CONTEXT_DENIED,
                "Item use is disabled by the active structure context");
        }

        java.util.Set<StageId> missing = reg.missingStagesForItem(player, stack.getItem());
        if (!missing.isEmpty() && reg.isCategoryEnforced(missing, EnforcementCategory.ITEM_USE)
                && !reg.isExemptFromUse(stack.getItem(), missing)) {
            StageId stage = missing.iterator().next();
            return new ItemUseDecision(false, Optional.of(stage), ItemUseDecision.Category.ITEM_USE,
                ItemUseDecision.Polarity.MISSING_STAGE, Optional.empty(), Optional.empty(),
                ItemUseDecision.Reason.MISSING_STAGE, "Item use requires " + stage);
        }

        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        Holder<Item> holder = BuiltInRegistries.ITEM.wrapAsHolder(stack.getItem());
        for (StageId stage : StageOrder.getInstance().getOrderedStages()) {
            StageDefinition definition = StageOrder.getInstance().getStageDefinition(stage).orElse(null);
            if (definition == null || definition.getActiveLocks().isEmpty()
                    || !StageManager.getInstance().hasStage(player, stage)) continue;
            ActiveLockDefinition active = definition.getActiveLocks();
            var session = StructureSessionManager.getInstance().activeSessionForStage(player, stage);
            if (session.isEmpty()) continue;
            if (active.items().isAlwaysUnlocked(itemId)) continue;
            boolean matches = active.items().locked().stream()
                .anyMatch(selector -> selector.matches(itemId, holder, PrefixEntry.Keys.ITEM));
            if (!matches) continue;
            var view = session.get();
            return new ItemUseDecision(false, Optional.of(stage), ItemUseDecision.Category.ITEM_USE,
                ItemUseDecision.Polarity.PRESENT_IN_CONTEXT, Optional.of(view.providerId()),
                Optional.of(view.sessionId()), ItemUseDecision.Reason.ACTIVE_STRUCTURE_LOCK,
                "Item use is disabled during this structure session");
        }
        return ItemUseDecision.allowed(ItemUseDecision.Reason.ALLOWED);
    }

    private static boolean hasActiveItemLocks() {
        for (StageId stage : StageOrder.getInstance().getOrderedStages()) {
            if (StageOrder.getInstance().getStageDefinition(stage)
                    .map(definition -> !definition.getActiveLocks().isEmpty()).orElse(false)) return true;
        }
        return false;
    }

    /**
     * Check if a player can pick up an item
     * @return true if allowed, false if blocked
     */
    public static boolean canPickupItem(ServerPlayer player, ItemStack stack) {
        LockRegistry reg = LockRegistry.getInstance();
        if (!StageConfig.isBlockItemPickup() && !reg.hasEnforcementOverrides()
                && !ConditionalLockEngine.hasRules(ConditionalRule.TargetType.ITEM)) {
            return true;
        }

        // Spectators always bypass.
        if (player.isSpectator()) {
            return true;
        }

        // Creative bypass
        if (StageConfig.isAllowCreativeBypass() && player.isCreative()) {
            return true;
        }

        if (stack.isEmpty()) {
            return true;
        }

        java.util.Set<StageId> missing = reg.missingStagesForItem(player, stack.getItem());
        if (missing.isEmpty()) return true;
        if (!reg.isCategoryEnforced(missing, EnforcementCategory.ITEM_PICKUP)) return true;
        return reg.isExemptFromPickup(stack.getItem(), missing);
    }

    /**
     * Check if a player can hold an item in their inventory
     * @return true if allowed, false if should be dropped
     */
    public static boolean canHoldItem(ServerPlayer player, ItemStack stack) {
        LockRegistry reg = LockRegistry.getInstance();
        if (!StageConfig.isBlockItemInventory() && !reg.hasEnforcementOverrides()
                && !ConditionalLockEngine.hasRules(ConditionalRule.TargetType.ITEM)) {
            return true;
        }

        // Spectators always bypass.
        if (player.isSpectator()) {
            return true;
        }

        // Creative bypass
        if (StageConfig.isAllowCreativeBypass() && player.isCreative()) {
            return true;
        }

        if (stack.isEmpty()) {
            return true;
        }

        java.util.Set<StageId> missing = reg.missingStagesForItem(player, stack.getItem());
        if (missing.isEmpty()) return true;
        if (!reg.isCategoryEnforced(missing, EnforcementCategory.ITEM_INVENTORY)) return true;
        return reg.isExemptFromInventory(stack.getItem(), missing);
    }

    /**
     * Check if an item is locked for a specific player.
     * v2.0: multi-stage aware — blocked when ANY gating stage is missing.
     */
    public static boolean isItemLockedForPlayer(ServerPlayer player, Item item) {
        return LockRegistry.getInstance().isItemBlockedFor(player, item);
    }

    /**
     * Send lock message and play sound to player.
     * v2.0: shows the primary missing stage (first gating stage the player doesn't own).
     */
    public static void notifyLocked(ServerPlayer player, Item item) {
        Optional<StageId> requiredStage = LockRegistry.getInstance().primaryRestrictingStage(player, item);
        if (requiredStage.isEmpty()) {
            return;
        }

        StageId stageId = requiredStage.get();

        // Send message
        if (StageConfig.isShowLockMessage()) {
            Component message;
            if (StageManager.getInstance().hasStage(player, stageId)) {
                message = TextUtil.parseColorCodes(StageConfig.getMsgItemLockedGeneric());
            } else if (StageDisclosure.mayShowRestrictingStageName(player)) {
                String displayName = StageOrder.getInstance().getStageDefinition(stageId)
                    .map(StageDefinition::getDisplayName)
                    .orElse(stageId.getPath());

                String template = StageConfig.getMsgItemLocked();
                message = TextUtil.parseColorCodes(template.replace("{stage}", displayName));
            } else {
                message = TextUtil.parseColorCodes(StageConfig.getMsgItemLockedGeneric());
            }
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
            Component message;
            if (StageManager.getInstance().hasStage(player, requiredStage)) {
                message = TextUtil.parseColorCodes(
                    StageConfig.getMsgTypeLockedGeneric().replace("{type}", type)
                );
            } else if (StageDisclosure.mayShowRestrictingStageName(player)) {
                String displayName = StageOrder.getInstance().getStageDefinition(requiredStage)
                    .map(StageDefinition::getDisplayName)
                    .orElse(requiredStage.getPath());

                String template = StageConfig.getMsgTypeLocked();
                message = TextUtil.parseColorCodes(
                    template.replace("{type}", type).replace("{stage}", displayName)
                );
            } else {
                message = TextUtil.parseColorCodes(
                    StageConfig.getMsgTypeLockedGeneric().replace("{type}", type)
                );
            }
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

    public static void notifyLockedWithCooldown(ServerPlayer player, ItemUseDecision decision, Item item) {
        if (decision == null || decision.allowed()) return;
        UUID playerId = player.getUUID();
        String itemId = BuiltInRegistries.ITEM.getKey(item).toString();
        String key = decision.reason().name() + "." + itemId;
        long currentTime = System.currentTimeMillis();
        int cooldownMs = StageConfig.getNotificationCooldown();
        Map<String, Long> playerCooldowns = messageCooldowns.computeIfAbsent(playerId, ignored -> new HashMap<>());
        Long lastMessageTime = playerCooldowns.get(key);
        if (lastMessageTime == null || currentTime - lastMessageTime >= cooldownMs) {
            if (decision.stage().isPresent()) notifyLocked(player, decision.stage().get(), "This item");
            else notifyLocked(player, item);
            playerCooldowns.put(key, currentTime);
        }
    }

    /**
     * Send lock notification with cooldown for a generic locked thing (entity, block, etc.)
     */
    public static void notifyLockedWithCooldown(ServerPlayer player, StageId requiredStage, String type) {
        UUID playerId = player.getUUID();
        String key = type + ":" + requiredStage.toString();
        long currentTime = System.currentTimeMillis();
        int cooldownMs = StageConfig.getNotificationCooldown();

        Map<String, Long> playerCooldowns = messageCooldowns.computeIfAbsent(playerId, k -> new HashMap<>());

        Long lastMessageTime = playerCooldowns.get(key);
        if (lastMessageTime == null || currentTime - lastMessageTime >= cooldownMs) {
            notifyLocked(player, requiredStage, type);
            playerCooldowns.put(key, currentTime);
        }
    }

    /**
     * Clear cooldowns for a player (call on logout)
     */
    public static void clearCooldowns(UUID playerId) {
        messageCooldowns.remove(playerId);
    }

    public static void clearAllCooldowns() {
        messageCooldowns.clear();
    }
}
