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
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
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

        // Determine the primary stage to display (item lock takes priority); used for the
        // masked-name decision and the description text.
        StageId displayStage = itemLocked ? missingItemStages.iterator().next() : missingRecipeStages.iterator().next();

        // Aggregate every missing gating stage. v2.3: per-stage [display] flags are OR-ed across
        // all of them — a multi-stage-locked item is masked/hidden if ANY gating stage asks for it.
        java.util.LinkedHashSet<StageId> allMissing = new java.util.LinkedHashSet<>();
        allMissing.addAll(missingItemStages);
        allMissing.addAll(missingRecipeStages);

        List<Component> tooltip = event.getToolTip();

        // Mask item name if ANY gating stage requests display_as_unknown_item (overriding the
        // global mask_locked_item_names default). Only for full item locks, never recipe-only.
        if (itemLocked && anyStageFlag(missingItemStages, ClientStageCache::isDisplayAsUnknownItem)
                && !tooltip.isEmpty()) {
            tooltip.set(0, TextUtil.parseColorCodes(StageConfig.getMsgTooltipMaskedName()));
        }

        // Show the lock tooltip unless EVERY gating stage suppresses it via show_tooltip = false
        // (per-stage override of the global emi.show_tooltip default).
        if (!anyStageFlag(allMissing, ClientStageCache::isShowTooltip)) {
            return;
        }
        // v2.3 fix: read display names from ClientStageCache (synced from server via
        // StageDefinitionsSyncPayload), NOT StageOrder. StageOrder is only populated on the
        // server JVM by loading the TOMLs; on a dedicated server the client's StageOrder is
        // empty, so this previously fell back to the raw stage path. ClientStageCache.getDisplayName
        // already falls back to stageId.getPath() when a definition is missing.
        String stageDisplayName = allMissing.stream()
            .map(ClientStageCache::getDisplayName)
            .collect(java.util.stream.Collectors.joining(", "));

        String currentStageName = ClientStageCache.getCurrentStage()
            .map(ClientStageCache::getDisplayName)
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
        if (com.enviouse.progressivestages.client.util.ClientStageDisclosure.mayShowRestrictingStageName()) {
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

        // v2.3: optionally append the gating stage's description (per-stage override of the
        // global show_stage_description_on_tooltip default). Only shown when non-empty AND the
        // stage name is allowed to be revealed to this viewer.
        if (anyStageFlag(allMissing, ClientStageCache::isShowDescriptionOnTooltip)
                && com.enviouse.progressivestages.client.util.ClientStageDisclosure.mayShowRestrictingStageName()) {
            String desc = ClientStageCache.getDescription(displayStage);
            if (desc != null && !desc.isEmpty()) {
                tooltip.add(TextUtil.parseColorCodes(
                    StageConfig.getMsgTooltipStageDescription().replace("{description}", desc)));
            }
        }
    }

    /** v2.0.1: clear client-side ore-spoof cache when leaving a level. */
    @SubscribeEvent
    public static void onLevelUnload(net.neoforged.neoforge.event.level.LevelEvent.Unload event) {
        if (event.getLevel() instanceof net.minecraft.client.multiplayer.ClientLevel) {
            OreSpoofClientState.clear();
        }
    }

    /** Prevent lock and progression snapshots from leaking between multiplayer servers/worlds. */
    @SubscribeEvent
    public static void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientStageCache.clear();
        ClientTriggerProgress.clear();
        ClientUnlockJuice.clear();
        OreSpoofClientState.clear();
    }

    /** v2.3: poll the stage-tree keybind; request a progress snapshot, which opens the GUI. */
    @SubscribeEvent
    public static void onClientTick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        // Drain all queued presses (so the keymapping counter is reset) but send at most one
        // request per tick to avoid flooding the server on a double-tap.
        boolean pressed = false;
        while (ClientModBusEvents.OPEN_TREE.consumeClick()) {
            pressed = true;
        }
        if (pressed) {
            ClientTriggerProgress.requestFromServer();
        }
    }

    /** True if any of the given stages reports the flag true (per-stage display flags are OR-ed). */
    private static boolean anyStageFlag(java.util.Collection<StageId> stages,
                                        java.util.function.Predicate<StageId> flag) {
        for (StageId s : stages) {
            if (flag.test(s)) return true;
        }
        return false;
    }
}
