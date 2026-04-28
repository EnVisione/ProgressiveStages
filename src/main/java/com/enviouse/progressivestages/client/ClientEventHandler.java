package com.enviouse.progressivestages.client;

import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.config.StageConfig;
import com.enviouse.progressivestages.common.config.StageDefinition;
import com.enviouse.progressivestages.common.lock.LockRegistry;
import com.enviouse.progressivestages.common.stage.StageOrder;
import com.enviouse.progressivestages.common.util.Constants;
import com.enviouse.progressivestages.common.util.TextUtil;
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

        // v2.0: gather ALL gating stages (multi-stage). Use cache first, fall back to LockRegistry for integrated.
        var itemId = BuiltInRegistries.ITEM.getKey(item);
        java.util.Set<StageId> itemGating = ClientLockCache.getRequiredStagesForItem(itemId);
        if (itemGating.isEmpty()) {
            itemGating = LockRegistry.getInstance().getRequiredStages(item);
        }
        java.util.LinkedHashSet<StageId> missingItemStages = new java.util.LinkedHashSet<>();
        for (StageId s : itemGating) if (!ClientStageCache.hasStage(s)) missingItemStages.add(s);
        boolean itemLocked = !missingItemStages.isEmpty();

        java.util.Set<StageId> recipeGating = ClientLockCache.getRequiredStagesForRecipeByOutput(itemId);
        if (recipeGating.isEmpty()) {
            recipeGating = LockRegistry.getInstance().getRequiredStagesForRecipeByOutput(item);
        }
        java.util.LinkedHashSet<StageId> missingRecipeStages = new java.util.LinkedHashSet<>();
        for (StageId s : recipeGating) if (!ClientStageCache.hasStage(s)) missingRecipeStages.add(s);
        boolean recipeOnlyLocked = !missingRecipeStages.isEmpty();

        if (!itemLocked && !recipeOnlyLocked) {
            return; // Nothing to show
        }

        // Determine the primary stage to display (item lock takes priority); kept for masked-name usage.
        StageId displayStage = itemLocked ? missingItemStages.iterator().next() : missingRecipeStages.iterator().next();

        List<Component> tooltip = event.getToolTip();

        // Mask item name if configured (only for full item lock)
        if (itemLocked && StageConfig.isMaskLockedItemNames() && !tooltip.isEmpty()) {
            // Replace the first line (item name) with configurable masked name (supports & color codes)
            tooltip.set(0, TextUtil.parseColorCodes(StageConfig.getMsgTooltipMaskedName()));
        }

        if (!StageConfig.isShowTooltip()) {
            return;
        }

        // Aggregate all missing stage names for the multi-stage tooltip line.
        java.util.LinkedHashSet<StageId> allMissing = new java.util.LinkedHashSet<>();
        allMissing.addAll(missingItemStages);
        allMissing.addAll(missingRecipeStages);
        String stageDisplayName = allMissing.stream()
            .map(s -> StageOrder.getInstance().getStageDefinition(s)
                .map(StageDefinition::getDisplayName).orElse(s.getPath()))
            .collect(java.util.stream.Collectors.joining(", "));

        String currentStageName = ClientStageCache.getCurrentStage()
            .flatMap(id -> StageOrder.getInstance().getStageDefinition(id))
            .map(StageDefinition::getDisplayName)
            .orElse(StageConfig.getMsgTooltipCurrentStageNone());

        String progress = ClientStageCache.getProgressString();

        // Add blank line before our tooltip
        tooltip.add(Component.empty());

        // Determine lock label based on what's locked
        String lockLabel;
        if (itemLocked && recipeOnlyLocked) {
            lockLabel = StageConfig.getMsgTooltipItemAndRecipeLocked();
        } else if (itemLocked) {
            lockLabel = StageConfig.getMsgTooltipItemLocked();
        } else {
            lockLabel = StageConfig.getMsgTooltipRecipeLocked();
        }

        // Add lock indicator (supports & color codes)
        tooltip.add(TextUtil.parseColorCodes(lockLabel));

        // Required stage (supports & color codes). Hide stage name from non-ops if configured.
        String stageRequiredText;
        if (com.enviouse.progressivestages.common.util.StageDisclosure.mayShowRestrictingStageNameClient()) {
            stageRequiredText = StageConfig.getMsgTooltipStageRequired().replace("{stage}", stageDisplayName);
        } else {
            stageRequiredText = StageConfig.getMsgTooltipStageRequiredGeneric();
        }
        tooltip.add(TextUtil.parseColorCodes(stageRequiredText));

        // Current stage (supports & color codes)
        String currentStageText = StageConfig.getMsgTooltipCurrentStage()
            .replace("{stage}", currentStageName)
            .replace("{progress}", progress);
        tooltip.add(TextUtil.parseColorCodes(currentStageText));
    }
}
