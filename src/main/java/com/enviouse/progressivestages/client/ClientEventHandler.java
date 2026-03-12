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
        // Creative bypass - don't show lock tooltips in creative mode
        if (ClientLockCache.isCreativeBypass()) {
            return;
        }

        Item item = event.getItemStack().getItem();

        // Get required stage from ClientLockCache (synced from server) with LockRegistry fallback
        var itemId = BuiltInRegistries.ITEM.getKey(item);
        Optional<StageId> requiredStageOpt = ClientLockCache.getRequiredStageForItem(itemId);

        // Fallback to LockRegistry for integrated server/singleplayer
        if (requiredStageOpt.isEmpty()) {
            requiredStageOpt = LockRegistry.getInstance().getRequiredStage(item);
        }

        // Check recipe-only lock (recipe_items = [...])
        Optional<StageId> recipeOnlyStageOpt = LockRegistry.getInstance().getRequiredStageForRecipeByOutput(item);
        boolean recipeOnlyLocked = recipeOnlyStageOpt.isPresent() &&
            !ClientStageCache.hasStage(recipeOnlyStageOpt.get());

        boolean itemLocked = requiredStageOpt.isPresent() &&
            !ClientStageCache.hasStage(requiredStageOpt.get());

        if (!itemLocked && !recipeOnlyLocked) {
            return; // Nothing to show
        }

        // Determine the stage to display (item lock takes priority for display)
        StageId displayStage = itemLocked ? requiredStageOpt.get() : recipeOnlyStageOpt.get();

        List<Component> tooltip = event.getToolTip();

        // Mask item name if configured (only for full item lock)
        if (itemLocked && StageConfig.isMaskLockedItemNames() && !tooltip.isEmpty()) {
            // Replace the first line (item name) with "Unknown Item"
            tooltip.set(0, Component.literal("Unknown Item").withStyle(ChatFormatting.RED));
        }

        if (!StageConfig.isShowTooltip()) {
            return;
        }

        // Get stage info
        String stageDisplayName = StageOrder.getInstance().getStageDefinition(displayStage)
            .map(StageDefinition::getDisplayName)
            .orElse(displayStage.getPath());

        String currentStageName = ClientStageCache.getCurrentStage()
            .flatMap(id -> StageOrder.getInstance().getStageDefinition(id))
            .map(StageDefinition::getDisplayName)
            .orElse("None");

        String progress = ClientStageCache.getProgressString();

        // Add blank line before our tooltip
        tooltip.add(Component.empty());

        // Determine lock label based on what's locked
        String lockLabel;
        if (itemLocked && recipeOnlyLocked) {
            lockLabel = "🔒 Item and Recipe Locked";
        } else if (itemLocked) {
            lockLabel = "🔒 Item Locked";
        } else {
            lockLabel = "🔒 Recipe Locked";
        }

        // Add lock indicator
        tooltip.add(Component.literal(lockLabel).withStyle(ChatFormatting.RED, ChatFormatting.BOLD));

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
