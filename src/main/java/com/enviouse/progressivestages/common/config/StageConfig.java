package com.enviouse.progressivestages.common.config;

import com.enviouse.progressivestages.common.util.Constants;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Main configuration for ProgressiveStages
 */
@SuppressWarnings("removal")
@EventBusSubscriber(modid = Constants.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public class StageConfig {

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // ============ General Settings ============

    private static final ModConfigSpec.ConfigValue<List<? extends String>> STARTING_STAGES = BUILDER
        .comment("Starting stages for new players (v1.3)",
                 "List of stage IDs to auto-grant on first join",
                 "Example: [\"stone_age\", \"tutorial_complete\"]",
                 "Set to empty list [] for no starting stages")
        .defineListAllowEmpty("general.starting_stages", List.of("stone_age"), obj -> obj instanceof String);

    private static final ModConfigSpec.ConfigValue<String> TEAM_MODE = BUILDER
        .comment("Team mode: \"ftb_teams\" (requires FTB Teams mod) or \"solo\" (each player is their own team)",
                 "If \"ftb_teams\", stages are shared across team members",
                 "If \"solo\", each player has independent progression")
        .define("general.team_mode", "ftb_teams");

    private static final ModConfigSpec.BooleanValue DEBUG_LOGGING = BUILDER
        .comment("Enable debug logging for stage checks, lock queries, and team operations")
        .define("general.debug_logging", false);

    private static final ModConfigSpec.BooleanValue LINEAR_PROGRESSION = BUILDER
        .comment("Enable linear progression (auto-grant dependency stages)",
                 "If true, granting a stage also auto-grants all missing dependencies recursively",
                 "If false, stages require explicit dependency satisfaction (admin can bypass with double-confirm)")
        .define("general.linear_progression", false);

    // ============ Enforcement Settings ============

    private static final ModConfigSpec.BooleanValue BLOCK_ITEM_USE = BUILDER
        .comment("Block item use (right-click with item in hand)")
        .define("enforcement.block_item_use", true);

    private static final ModConfigSpec.BooleanValue BLOCK_ITEM_PICKUP = BUILDER
        .comment("Block item pickup (prevent picking up locked items from ground)")
        .define("enforcement.block_item_pickup", true);

    private static final ModConfigSpec.BooleanValue BLOCK_ITEM_HOTBAR = BUILDER
        .comment("Prevent locked items from being in the hotbar",
                 "When true, locked items in hotbar slots are moved to main inventory (not dropped)",
                 "When false, locked items can remain in the hotbar but still cannot be used",
                 "This is a softer alternative to block_item_inventory — lets players store items for later",
                 "Ignored if block_item_inventory is true (which drops items from everywhere)")
        .define("enforcement.block_item_hotbar", true);

    private static final ModConfigSpec.BooleanValue BLOCK_ITEM_MOUSE_PICKUP = BUILDER
        .comment("Prevent picking up locked items with the mouse cursor in GUIs",
                 "When true, players cannot click on locked items in inventories/containers",
                 "When false, players can freely move locked items between inventory and chests",
                 "This allows players to store locked items for later use once they unlock the stage",
                 "Ignored if block_item_inventory is true (which blocks all interaction)")
        .define("enforcement.block_item_mouse_pickup", true);

    private static final ModConfigSpec.BooleanValue BLOCK_ITEM_INVENTORY = BUILDER
        .comment("Block item holding in inventory (auto-drop locked items)",
                 "This is the strictest option — locked items are dropped on the ground",
                 "If false, see block_item_hotbar and block_item_mouse_pickup for softer alternatives")
        .define("enforcement.block_item_inventory", true);

    private static final ModConfigSpec.IntValue INVENTORY_SCAN_FREQUENCY = BUILDER
        .comment("Frequency to scan inventory for locked items (in ticks, 20 ticks = 1 second)",
                 "Set to 0 to disable periodic scanning")
        .defineInRange("enforcement.inventory_scan_frequency", 20, 0, 200);

    private static final ModConfigSpec.BooleanValue BLOCK_CRAFTING = BUILDER
        .comment("Block crafting locked recipes")
        .define("enforcement.block_crafting", true);

    private static final ModConfigSpec.BooleanValue HIDE_LOCKED_RECIPE_OUTPUT = BUILDER
        .comment("Hide locked recipes from crafting table output")
        .define("enforcement.hide_locked_recipe_output", true);

    private static final ModConfigSpec.BooleanValue BLOCK_BLOCK_PLACEMENT = BUILDER
        .comment("Block placement of locked blocks")
        .define("enforcement.block_block_placement", true);

    private static final ModConfigSpec.BooleanValue BLOCK_BLOCK_INTERACTION = BUILDER
        .comment("Block right-clicking locked blocks")
        .define("enforcement.block_block_interaction", true);

    private static final ModConfigSpec.BooleanValue BLOCK_DIMENSION_TRAVEL = BUILDER
        .comment("Block travel to locked dimensions")
        .define("enforcement.block_dimension_travel", true);

    private static final ModConfigSpec.BooleanValue BLOCK_LOCKED_MODS = BUILDER
        .comment("Block items from locked mods")
        .define("enforcement.block_locked_mods", true);

    private static final ModConfigSpec.BooleanValue BLOCK_INTERACTIONS = BUILDER
        .comment("Block Create-style item-on-block interactions")
        .define("enforcement.block_interactions", true);

    private static final ModConfigSpec.BooleanValue BLOCK_ENTITY_ATTACK = BUILDER
        .comment("Block attacking locked entities",
                 "If true, players cannot attack entity types locked behind a stage")
        .define("enforcement.block_entity_attack", true);

    private static final ModConfigSpec.BooleanValue ALLOW_CREATIVE_BYPASS = BUILDER
        .comment("Allow creative mode players to bypass stage locks",
                 "If true, players in creative mode can use/place locked items",
                 "They will still be locked when switching to survival")
        .define("enforcement.allow_creative_bypass", true);

    private static final ModConfigSpec.BooleanValue MASK_LOCKED_ITEM_NAMES = BUILDER
        .comment("Rename locked items to hide their identity",
                 "If true, locked items show as 'Unknown Item' instead of real name",
                 "Players must unlock the stage to see the real name")
        .define("enforcement.mask_locked_item_names", true);

    private static final ModConfigSpec.IntValue NOTIFICATION_COOLDOWN = BUILDER
        .comment("Cooldown in milliseconds between lock notification messages",
                 "Prevents chat spam when standing on locked items")
        .defineInRange("enforcement.notification_cooldown", 3000, 0, 30000);

    private static final ModConfigSpec.BooleanValue SHOW_LOCK_MESSAGE = BUILDER
        .comment("Show chat message when player is blocked by a lock")
        .define("enforcement.show_lock_message", true);

    private static final ModConfigSpec.BooleanValue PLAY_LOCK_SOUND = BUILDER
        .comment("Play sound when player is blocked by a lock")
        .define("enforcement.play_lock_sound", true);

    private static final ModConfigSpec.ConfigValue<String> LOCK_SOUND = BUILDER
        .comment("Sound to play when blocked")
        .define("enforcement.lock_sound", "minecraft:block.note_block.pling");

    private static final ModConfigSpec.DoubleValue LOCK_SOUND_VOLUME = BUILDER
        .comment("Sound volume (0.0 to 1.0)")
        .defineInRange("enforcement.lock_sound_volume", 1.0, 0.0, 1.0);

    private static final ModConfigSpec.DoubleValue LOCK_SOUND_PITCH = BUILDER
        .comment("Sound pitch (0.5 to 2.0)")
        .defineInRange("enforcement.lock_sound_pitch", 1.0, 0.5, 2.0);

    // ============ Team Settings ============

    private static final ModConfigSpec.BooleanValue PERSIST_STAGES_ON_LEAVE = BUILDER
        .comment("Stages persist when leaving team")
        .define("team.persist_stages_on_leave", true);

    // ============ EMI Settings ============

    private static final ModConfigSpec.BooleanValue EMI_ENABLED = BUILDER
        .comment("Enable EMI integration")
        .define("emi.enabled", true);

    private static final ModConfigSpec.BooleanValue SHOW_LOCK_ICON = BUILDER
        .comment("Show lock icon overlay on locked items/recipes in EMI")
        .define("emi.show_lock_icon", true);

    private static final ModConfigSpec.ConfigValue<String> LOCK_ICON_POSITION = BUILDER
        .comment("Lock icon position: top_left, top_right, bottom_left, bottom_right, center")
        .define("emi.lock_icon_position", "top_left");

    private static final ModConfigSpec.IntValue LOCK_ICON_SIZE = BUILDER
        .comment("Lock icon size in pixels")
        .defineInRange("emi.lock_icon_size", 8, 4, 32);

    private static final ModConfigSpec.BooleanValue SHOW_HIGHLIGHT = BUILDER
        .comment("Show semi-transparent highlight on locked recipe outputs")
        .define("emi.show_highlight", true);

    private static final ModConfigSpec.ConfigValue<String> HIGHLIGHT_COLOR = BUILDER
        .comment("Highlight color (ARGB hex format: 0xAARRGGBB)")
        .define("emi.highlight_color", "0x50FFAA40");

    private static final ModConfigSpec.BooleanValue SHOW_TOOLTIP = BUILDER
        .comment("Show lock info in item tooltips")
        .define("emi.show_tooltip", true);

    private static final ModConfigSpec.BooleanValue SHOW_LOCKED_RECIPES = BUILDER
        .comment("Show locked recipes in EMI",
                 "If false, locked items and recipes will be hidden from EMI entirely",
                 "If true, locked items will show with lock overlays")
        .define("emi.show_locked_recipes", false);

    // ============ Performance Settings ============

    private static final ModConfigSpec.BooleanValue ENABLE_LOCK_CACHE = BUILDER
        .comment("Cache lock queries for better performance")
        .define("performance.enable_lock_cache", true);

    private static final ModConfigSpec.IntValue LOCK_CACHE_SIZE = BUILDER
        .comment("Cache size per player")
        .defineInRange("performance.lock_cache_size", 1024, 128, 8192);

    // ============ Messages Settings ============
    // All player-facing text is configurable here. Supports & color codes (e.g., &c = red, &l = bold).
    // Placeholders: {stage} = stage display name, {type} = lock type, {count} = number, {progress} = progress string, {dependencies} = missing deps

    private static final ModConfigSpec.ConfigValue<String> MSG_TOOLTIP_MASKED_NAME = BUILDER
        .comment("Text shown in place of item name when mask_locked_item_names is true")
        .define("messages.tooltip_masked_name", "Unknown Item");

    private static final ModConfigSpec.ConfigValue<String> MSG_TOOLTIP_ITEM_AND_RECIPE_LOCKED = BUILDER
        .comment("Tooltip header when both the item and its recipe are locked")
        .define("messages.tooltip_item_and_recipe_locked", "\uD83D\uDD12 Item and Recipe Locked");

    private static final ModConfigSpec.ConfigValue<String> MSG_TOOLTIP_ITEM_LOCKED = BUILDER
        .comment("Tooltip header when only the item is locked")
        .define("messages.tooltip_item_locked", "\uD83D\uDD12 Item Locked");

    private static final ModConfigSpec.ConfigValue<String> MSG_TOOLTIP_RECIPE_LOCKED = BUILDER
        .comment("Tooltip header when only the recipe is locked")
        .define("messages.tooltip_recipe_locked", "\uD83D\uDD12 Recipe Locked");

    private static final ModConfigSpec.ConfigValue<String> MSG_TOOLTIP_STAGE_REQUIRED = BUILDER
        .comment("Tooltip line showing required stage. {stage} = stage display name")
        .define("messages.tooltip_stage_required", "Stage required: {stage}");

    private static final ModConfigSpec.ConfigValue<String> MSG_TOOLTIP_CURRENT_STAGE = BUILDER
        .comment("Tooltip line showing current stage. {stage} = current stage name, {progress} = progress string")
        .define("messages.tooltip_current_stage", "Current stage: {stage} ({progress})");

    private static final ModConfigSpec.ConfigValue<String> MSG_ITEM_LOCKED = BUILDER
        .comment("Chat message when a player tries to use a locked item. {stage} = required stage display name",
                 "Supports & color codes (e.g., &c = red, &7 = gray, &f = white, &l = bold)")
        .define("messages.item_locked", "&c\uD83D\uDD12 You haven't unlocked this item yet! &7Required: &f{stage}");

    private static final ModConfigSpec.ConfigValue<String> MSG_TYPE_LOCKED = BUILDER
        .comment("Chat message for generic locked things (block, dimension, entity, recipe, interaction).",
                 "{type} = the type description (e.g., 'This block'), {stage} = required stage display name",
                 "Supports & color codes")
        .define("messages.type_locked", "&c\uD83D\uDD12 {type} is locked! &7Required: &f{stage}");

    private static final ModConfigSpec.ConfigValue<String> MSG_ITEMS_DROPPED = BUILDER
        .comment("Chat message when locked items are dropped from inventory. {count} = number of items dropped",
                 "Supports & color codes")
        .define("messages.items_dropped", "&c\uD83D\uDD12 Dropped {count} locked items from your inventory!");

    private static final ModConfigSpec.ConfigValue<String> MSG_ITEMS_MOVED_HOTBAR = BUILDER
        .comment("Chat message when locked items are moved out of the hotbar. {count} = number of items moved",
                 "Supports & color codes")
        .define("messages.items_moved_hotbar", "&c\uD83D\uDD12 Moved {count} locked item(s) out of your hotbar!");

    private static final ModConfigSpec.ConfigValue<String> MSG_MISSING_DEPENDENCIES = BUILDER
        .comment("Chat message when a stage cannot be granted due to missing dependencies.",
                 "{stage} = stage path, {dependencies} = comma-separated list of missing dependency paths")
        .define("messages.missing_dependencies", "[ProgressiveStages] Stage '{stage}' could not be granted: missing required stage(s): {dependencies}. Complete the prerequisites first.");

    // ============ Integration Settings ============

    private static final ModConfigSpec.BooleanValue FTB_QUESTS_INTEGRATION = BUILDER
        .comment("Enable FTB Quests integration",
                 "If true, ProgressiveStages registers as the stage provider for FTB Quests",
                 "Stage tasks will update instantly when stages change",
                 "Set to false if you experience compatibility issues")
        .define("integration.ftbquests.enabled", true);

    private static final ModConfigSpec.IntValue FTB_RECHECK_BUDGET = BUILDER
        .comment("Maximum FTB Quests stage rechecks per tick",
                 "Limits processing to prevent lag spikes on bulk operations",
                 "Remaining players are processed on subsequent ticks")
        .defineInRange("integration.ftbquests.recheck_budget_per_tick", 10, 1, 100);

    public static final ModConfigSpec SPEC = BUILDER.build();

    // ============ Cached Values ============

    private static List<String> startingStages;
    private static String teamMode;
    private static boolean debugLogging;
    private static boolean linearProgression;
    private static boolean blockItemUse;
    private static boolean blockItemPickup;
    private static boolean blockItemHotbar;
    private static boolean blockItemMousePickup;
    private static boolean blockItemInventory;
    private static int inventoryScanFrequency;
    private static boolean blockCrafting;
    private static boolean hideLockRecipeOutput;
    private static boolean blockBlockPlacement;
    private static boolean blockBlockInteraction;
    private static boolean blockDimensionTravel;
    private static boolean blockLockedMods;
    private static boolean blockInteractions;
    private static boolean blockEntityAttack;
    private static boolean allowCreativeBypass;
    private static boolean maskLockedItemNames;
    private static int notificationCooldown;
    private static boolean showLockMessage;
    private static boolean playLockSound;
    private static String lockSound;
    private static float lockSoundVolume;
    private static float lockSoundPitch;
    private static boolean persistStagesOnLeave;
    private static boolean emiEnabled;
    private static boolean showLockIcon;
    private static String lockIconPosition;
    private static int lockIconSize;
    private static boolean showHighlight;
    private static int highlightColor;
    private static boolean showTooltip;
    private static boolean showLockedRecipes;
    private static boolean enableLockCache;
    private static int lockCacheSize;
    private static boolean ftbQuestsIntegration;
    private static int ftbRecheckBudget;

    // Messages
    private static String msgTooltipMaskedName;
    private static String msgTooltipItemAndRecipeLocked;
    private static String msgTooltipItemLocked;
    private static String msgTooltipRecipeLocked;
    private static String msgTooltipStageRequired;
    private static String msgTooltipCurrentStage;
    private static String msgItemLocked;
    private static String msgTypeLocked;
    private static String msgItemsDropped;
    private static String msgItemsMovedHotbar;
    private static String msgMissingDependencies;

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        // Starting stages (v1.3 - supports list)
        List<? extends String> stagesList = STARTING_STAGES.get();
        startingStages = new ArrayList<>();
        if (stagesList != null) {
            for (String s : stagesList) {
                startingStages.add(s);
            }
        }

        teamMode = TEAM_MODE.get();
        debugLogging = DEBUG_LOGGING.get();
        linearProgression = LINEAR_PROGRESSION.get();
        blockItemUse = BLOCK_ITEM_USE.get();
        blockItemPickup = BLOCK_ITEM_PICKUP.get();
        blockItemHotbar = BLOCK_ITEM_HOTBAR.get();
        blockItemMousePickup = BLOCK_ITEM_MOUSE_PICKUP.get();
        blockItemInventory = BLOCK_ITEM_INVENTORY.get();
        inventoryScanFrequency = INVENTORY_SCAN_FREQUENCY.get();
        blockCrafting = BLOCK_CRAFTING.get();
        hideLockRecipeOutput = HIDE_LOCKED_RECIPE_OUTPUT.get();
        blockBlockPlacement = BLOCK_BLOCK_PLACEMENT.get();
        blockBlockInteraction = BLOCK_BLOCK_INTERACTION.get();
        blockDimensionTravel = BLOCK_DIMENSION_TRAVEL.get();
        blockLockedMods = BLOCK_LOCKED_MODS.get();
        blockInteractions = BLOCK_INTERACTIONS.get();
        blockEntityAttack = BLOCK_ENTITY_ATTACK.get();
        allowCreativeBypass = ALLOW_CREATIVE_BYPASS.get();
        maskLockedItemNames = MASK_LOCKED_ITEM_NAMES.get();
        notificationCooldown = NOTIFICATION_COOLDOWN.get();
        showLockMessage = SHOW_LOCK_MESSAGE.get();
        playLockSound = PLAY_LOCK_SOUND.get();
        lockSound = LOCK_SOUND.get();
        lockSoundVolume = LOCK_SOUND_VOLUME.get().floatValue();
        lockSoundPitch = LOCK_SOUND_PITCH.get().floatValue();
        persistStagesOnLeave = PERSIST_STAGES_ON_LEAVE.get();
        emiEnabled = EMI_ENABLED.get();
        showLockIcon = SHOW_LOCK_ICON.get();
        lockIconPosition = LOCK_ICON_POSITION.get();
        lockIconSize = LOCK_ICON_SIZE.get();
        showHighlight = SHOW_HIGHLIGHT.get();
        showTooltip = SHOW_TOOLTIP.get();
        showLockedRecipes = SHOW_LOCKED_RECIPES.get();
        enableLockCache = ENABLE_LOCK_CACHE.get();
        lockCacheSize = LOCK_CACHE_SIZE.get();
        ftbQuestsIntegration = FTB_QUESTS_INTEGRATION.get();
        ftbRecheckBudget = FTB_RECHECK_BUDGET.get();

        // Messages
        msgTooltipMaskedName = MSG_TOOLTIP_MASKED_NAME.get();
        msgTooltipItemAndRecipeLocked = MSG_TOOLTIP_ITEM_AND_RECIPE_LOCKED.get();
        msgTooltipItemLocked = MSG_TOOLTIP_ITEM_LOCKED.get();
        msgTooltipRecipeLocked = MSG_TOOLTIP_RECIPE_LOCKED.get();
        msgTooltipStageRequired = MSG_TOOLTIP_STAGE_REQUIRED.get();
        msgTooltipCurrentStage = MSG_TOOLTIP_CURRENT_STAGE.get();
        msgItemLocked = MSG_ITEM_LOCKED.get();
        msgTypeLocked = MSG_TYPE_LOCKED.get();
        msgItemsDropped = MSG_ITEMS_DROPPED.get();
        msgItemsMovedHotbar = MSG_ITEMS_MOVED_HOTBAR.get();
        msgMissingDependencies = MSG_MISSING_DEPENDENCIES.get();

        // Parse highlight color
        try {
            String colorStr = HIGHLIGHT_COLOR.get();
            if (colorStr.startsWith("0x") || colorStr.startsWith("0X")) {
                highlightColor = (int) Long.parseLong(colorStr.substring(2), 16);
            } else {
                highlightColor = (int) Long.parseLong(colorStr, 16);
            }
        } catch (NumberFormatException e) {
            highlightColor = 0x50FFAA40; // Default
        }
    }

    // ============ Getters ============

    /**
     * @deprecated Use {@link #getStartingStages()} instead. v1.3 supports multiple starting stages.
     */
    @Deprecated(forRemoval = true)
    public static String getStartingStage() {
        return startingStages == null || startingStages.isEmpty() ? "" : startingStages.get(0);
    }

    /**
     * Get all starting stages to auto-grant to new players.
     */
    public static List<String> getStartingStages() {
        return startingStages != null ? Collections.unmodifiableList(startingStages) : Collections.emptyList();
    }

    public static String getTeamMode() { return teamMode; }
    public static boolean isDebugLogging() { return debugLogging; }
    public static boolean isLinearProgression() { return linearProgression; }
    public static boolean isBlockItemUse() { return blockItemUse; }
    public static boolean isBlockItemPickup() { return blockItemPickup; }
    public static boolean isBlockItemHotbar() { return blockItemHotbar; }
    public static boolean isBlockItemMousePickup() { return blockItemMousePickup; }
    public static boolean isBlockItemInventory() { return blockItemInventory; }
    public static int getInventoryScanFrequency() { return inventoryScanFrequency; }
    public static boolean isBlockCrafting() { return blockCrafting; }
    public static boolean isHideLockRecipeOutput() { return hideLockRecipeOutput; }
    public static boolean isBlockBlockPlacement() { return blockBlockPlacement; }
    public static boolean isBlockBlockInteraction() { return blockBlockInteraction; }
    public static boolean isBlockDimensionTravel() { return blockDimensionTravel; }
    public static boolean isBlockLockedMods() { return blockLockedMods; }
    public static boolean isBlockInteractions() { return blockInteractions; }
    public static boolean isBlockEntityAttack() { return blockEntityAttack; }
    public static boolean isAllowCreativeBypass() { return allowCreativeBypass; }
    public static boolean isMaskLockedItemNames() { return maskLockedItemNames; }
    public static int getNotificationCooldown() { return notificationCooldown; }
    public static boolean isShowLockMessage() { return showLockMessage; }
    public static boolean isPlayLockSound() { return playLockSound; }
    public static String getLockSound() { return lockSound; }
    public static float getLockSoundVolume() { return lockSoundVolume; }
    public static float getLockSoundPitch() { return lockSoundPitch; }
    public static boolean isPersistStagesOnLeave() { return persistStagesOnLeave; }
    public static boolean isEmiEnabled() { return emiEnabled; }
    public static boolean isShowLockIcon() { return showLockIcon; }
    public static String getLockIconPosition() { return lockIconPosition; }
    public static int getLockIconSize() { return lockIconSize; }
    public static boolean isShowHighlight() { return showHighlight; }
    public static int getHighlightColor() { return highlightColor; }
    public static boolean isShowTooltip() { return showTooltip; }
    public static boolean isShowLockedRecipes() { return showLockedRecipes; }
    public static boolean isEnableLockCache() { return enableLockCache; }
    public static int getLockCacheSize() { return lockCacheSize; }
    public static boolean isFtbQuestsIntegrationEnabled() { return ftbQuestsIntegration; }
    public static int getFtbRecheckBudget() { return ftbRecheckBudget; }

    public static boolean isFtbTeamsMode() {
        return "ftb_teams".equalsIgnoreCase(teamMode);
    }

    public static boolean isSoloMode() {
        return "solo".equalsIgnoreCase(teamMode);
    }

    // ============ Message Getters ============

    public static String getMsgTooltipMaskedName() { return msgTooltipMaskedName != null ? msgTooltipMaskedName : "Unknown Item"; }
    public static String getMsgTooltipItemAndRecipeLocked() { return msgTooltipItemAndRecipeLocked != null ? msgTooltipItemAndRecipeLocked : "\uD83D\uDD12 Item and Recipe Locked"; }
    public static String getMsgTooltipItemLocked() { return msgTooltipItemLocked != null ? msgTooltipItemLocked : "\uD83D\uDD12 Item Locked"; }
    public static String getMsgTooltipRecipeLocked() { return msgTooltipRecipeLocked != null ? msgTooltipRecipeLocked : "\uD83D\uDD12 Recipe Locked"; }
    public static String getMsgTooltipStageRequired() { return msgTooltipStageRequired != null ? msgTooltipStageRequired : "Stage required: {stage}"; }
    public static String getMsgTooltipCurrentStage() { return msgTooltipCurrentStage != null ? msgTooltipCurrentStage : "Current stage: {stage} ({progress})"; }
    public static String getMsgItemLocked() { return msgItemLocked != null ? msgItemLocked : "&c\uD83D\uDD12 You haven't unlocked this item yet! &7Required: &f{stage}"; }
    public static String getMsgTypeLocked() { return msgTypeLocked != null ? msgTypeLocked : "&c\uD83D\uDD12 {type} is locked! &7Required: &f{stage}"; }
    public static String getMsgItemsDropped() { return msgItemsDropped != null ? msgItemsDropped : "&c\uD83D\uDD12 Dropped {count} locked items from your inventory!"; }
    public static String getMsgItemsMovedHotbar() { return msgItemsMovedHotbar != null ? msgItemsMovedHotbar : "&c\uD83D\uDD12 Moved {count} locked item(s) out of your hotbar!"; }
    public static String getMsgMissingDependencies() { return msgMissingDependencies != null ? msgMissingDependencies : "[ProgressiveStages] Stage '{stage}' could not be granted: missing required stage(s): {dependencies}. Complete the prerequisites first."; }
}
