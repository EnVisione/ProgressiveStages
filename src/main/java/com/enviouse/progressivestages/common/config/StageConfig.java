package com.enviouse.progressivestages.common.config;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

/**
 * Main configuration for ProgressiveStages
 */
@EventBusSubscriber(modid = "progressivestages", bus = EventBusSubscriber.Bus.MOD)
public class StageConfig {

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // ============ General Settings ============

    private static final ModConfigSpec.ConfigValue<String> STARTING_STAGE = BUILDER
        .comment("Starting stage for new players",
                 "Set to a stage ID (e.g., \"stone_age\") to auto-grant on first join",
                 "Set to \"\" (empty string) for no starting stage")
        .define("general.starting_stage", "stone_age");

    private static final ModConfigSpec.ConfigValue<String> TEAM_MODE = BUILDER
        .comment("Team mode: \"ftb_teams\" (requires FTB Teams mod) or \"solo\" (each player is their own team)",
                 "If \"ftb_teams\", stages are shared across team members",
                 "If \"solo\", each player has independent progression")
        .define("general.team_mode", "ftb_teams");

    private static final ModConfigSpec.BooleanValue DEBUG_LOGGING = BUILDER
        .comment("Enable debug logging for stage checks, lock queries, and team operations")
        .define("general.debug_logging", false);

    // ============ Enforcement Settings ============

    private static final ModConfigSpec.BooleanValue BLOCK_ITEM_USE = BUILDER
        .comment("Block item use (right-click with item in hand)")
        .define("enforcement.block_item_use", true);

    private static final ModConfigSpec.BooleanValue BLOCK_ITEM_PICKUP = BUILDER
        .comment("Block item pickup (prevent picking up locked items from ground)")
        .define("enforcement.block_item_pickup", true);

    private static final ModConfigSpec.BooleanValue BLOCK_ITEM_INVENTORY = BUILDER
        .comment("Block item holding in inventory (auto-drop locked items)")
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

    public static final ModConfigSpec SPEC = BUILDER.build();

    // ============ Cached Values ============

    private static String startingStage;
    private static String teamMode;
    private static boolean debugLogging;
    private static boolean blockItemUse;
    private static boolean blockItemPickup;
    private static boolean blockItemInventory;
    private static int inventoryScanFrequency;
    private static boolean blockCrafting;
    private static boolean hideLockRecipeOutput;
    private static boolean blockBlockPlacement;
    private static boolean blockBlockInteraction;
    private static boolean blockDimensionTravel;
    private static boolean blockLockedMods;
    private static boolean blockInteractions;
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

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        startingStage = STARTING_STAGE.get();
        teamMode = TEAM_MODE.get();
        debugLogging = DEBUG_LOGGING.get();
        blockItemUse = BLOCK_ITEM_USE.get();
        blockItemPickup = BLOCK_ITEM_PICKUP.get();
        blockItemInventory = BLOCK_ITEM_INVENTORY.get();
        inventoryScanFrequency = INVENTORY_SCAN_FREQUENCY.get();
        blockCrafting = BLOCK_CRAFTING.get();
        hideLockRecipeOutput = HIDE_LOCKED_RECIPE_OUTPUT.get();
        blockBlockPlacement = BLOCK_BLOCK_PLACEMENT.get();
        blockBlockInteraction = BLOCK_BLOCK_INTERACTION.get();
        blockDimensionTravel = BLOCK_DIMENSION_TRAVEL.get();
        blockLockedMods = BLOCK_LOCKED_MODS.get();
        blockInteractions = BLOCK_INTERACTIONS.get();
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

    public static String getStartingStage() { return startingStage; }
    public static String getTeamMode() { return teamMode; }
    public static boolean isDebugLogging() { return debugLogging; }
    public static boolean isBlockItemUse() { return blockItemUse; }
    public static boolean isBlockItemPickup() { return blockItemPickup; }
    public static boolean isBlockItemInventory() { return blockItemInventory; }
    public static int getInventoryScanFrequency() { return inventoryScanFrequency; }
    public static boolean isBlockCrafting() { return blockCrafting; }
    public static boolean isHideLockRecipeOutput() { return hideLockRecipeOutput; }
    public static boolean isBlockBlockPlacement() { return blockBlockPlacement; }
    public static boolean isBlockBlockInteraction() { return blockBlockInteraction; }
    public static boolean isBlockDimensionTravel() { return blockDimensionTravel; }
    public static boolean isBlockLockedMods() { return blockLockedMods; }
    public static boolean isBlockInteractions() { return blockInteractions; }
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

    public static boolean isFtbTeamsMode() {
        return "ftb_teams".equalsIgnoreCase(teamMode);
    }

    public static boolean isSoloMode() {
        return "solo".equalsIgnoreCase(teamMode);
    }
}
