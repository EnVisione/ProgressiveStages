package com.enviouse.progressivestages.client;

import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.config.StageConfig;
import com.enviouse.progressivestages.common.config.StageDefinition;
import com.enviouse.progressivestages.common.lock.LockRegistry;
import com.enviouse.progressivestages.common.stage.StageOrder;
import com.enviouse.progressivestages.common.util.Constants;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.util.List;
import java.util.Optional;

/**
 * Client-side event handler for tooltips and rendering
 */
@EventBusSubscriber(modid = Constants.MOD_ID, value = Dist.CLIENT)
public class ClientEventHandler {

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        Item item = event.getItemStack().getItem();

        // Get required stage from ClientLockCache (synced from server) with LockRegistry fallback
        var itemId = BuiltInRegistries.ITEM.getKey(item);
        Optional<StageId> requiredStageOpt = ClientLockCache.getRequiredStageForItem(itemId);

        // Fallback to LockRegistry for integrated server/singleplayer
        if (requiredStageOpt.isEmpty()) {
            requiredStageOpt = LockRegistry.getInstance().getRequiredStage(item);
        }

        if (requiredStageOpt.isEmpty()) {
            return;
        }

        StageId requiredStage = requiredStageOpt.get();
        boolean hasStage = ClientStageCache.hasStage(requiredStage);

        if (hasStage) {
            return; // Don't modify tooltip if unlocked
        }

        List<Component> tooltip = event.getToolTip();

        // Mask item name if configured
        if (StageConfig.isMaskLockedItemNames() && !tooltip.isEmpty()) {
            // Replace the first line (item name) with "Unknown Item"
            tooltip.set(0, Component.literal("Unknown Item").withStyle(ChatFormatting.RED));
        }

        if (!StageConfig.isShowTooltip()) {
            return;
        }

        // Get stage info
        String stageDisplayName = StageOrder.getInstance().getStageDefinition(requiredStage)
            .map(StageDefinition::getDisplayName)
            .orElse(requiredStage.getPath());

        String currentStageName = ClientStageCache.getCurrentStage()
            .flatMap(id -> StageOrder.getInstance().getStageDefinition(id))
            .map(StageDefinition::getDisplayName)
            .orElse("None");

        String progress = ClientStageCache.getProgressString();

        // Add blank line before our tooltip
        tooltip.add(Component.empty());

        // Add lock indicator
        tooltip.add(Component.literal("🔒 Locked").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));

        // Required stage
        tooltip.add(Component.literal("Stage required: ")
            .withStyle(ChatFormatting.GRAY)
            .append(Component.literal(stageDisplayName).withStyle(ChatFormatting.WHITE)));

        // Current stage
        tooltip.add(Component.literal("Current stage: ")
            .withStyle(ChatFormatting.GRAY)
            .append(Component.literal(currentStageName + " (" + progress + ")").withStyle(ChatFormatting.WHITE)));
    }
}
