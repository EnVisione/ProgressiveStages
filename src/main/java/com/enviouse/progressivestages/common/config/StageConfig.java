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

    private static final ModConfigSpec.BooleanValue REAPPLY_STARTING_STAGES_ON_LOGIN = BUILDER
        .comment("If true, starting_stages are checked and applied on EVERY login, not just on first join.",
                 "This lets pack devs add a starting stage to an existing world and have all online players pick it up next login.",
                 "Idempotent — already-granted stages are not re-granted. Default false to preserve original behavior.")
        .define("general.reapply_starting_stages_on_login", false);

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

    private static final ModConfigSpec.BooleanValue BLOCK_MOB_SPAWNS = BUILDER
        .comment("Gate mob spawns behind stages",
                 "If true, mobs listed in spawn_entities / spawn_entity_tags / spawn_entity_mods",
                 "will be prevented from spawning in the world until the nearest player has the required stage.",
                 "Useful for 'world responds to progression' setups (e.g., Born In Chaos mobs only spawn after a quest).")
        .define("enforcement.block_mob_spawns", true);

    private static final ModConfigSpec.IntValue MOB_SPAWN_CHECK_RADIUS = BUILDER
        .comment("Radius (in blocks) to search for the nearest player when gating mob spawns",
                 "If no player is within this radius of the spawn, the spawn is ALLOWED (no one will see it).",
                 "If a player is within range, their stages determine whether the spawn is allowed.",
                 "Default 128 blocks (~8 chunks, standard mob tracking range).")
        .defineInRange("enforcement.mob_spawn_check_radius", 128, 16, 512);

    // ============ 2.0 Enforcement Toggles ============

    private static final ModConfigSpec.BooleanValue BLOCK_ENCHANTS = BUILDER
        .comment("Gate enchantments behind stages",
                 "Strips locked enchantments from items in the player's inventory each scan,",
                 "and blocks anvils from applying locked enchantment books.")
        .define("enforcement.block_enchants", true);

    private static final ModConfigSpec.BooleanValue BLOCK_SCREEN_OPEN = BUILDER
        .comment("Block opening locked containers/GUIs (screens category)")
        .define("enforcement.block_screen_open", true);

    private static final ModConfigSpec.BooleanValue BLOCK_CROP_GROWTH = BUILDER
        .comment("Block planting, bonemealing, and random-tick growth of locked crops.")
        .define("enforcement.block_crop_growth", true);

    private static final ModConfigSpec.BooleanValue BLOCK_PET_INTERACT = BUILDER
        .comment("Block taming and breeding interactions with locked pets.")
        .define("enforcement.block_pet_interact", true);

    private static final ModConfigSpec.BooleanValue BLOCK_LOOT_DROPS = BUILDER
        .comment("Filter locked loot from mob drops and block drops before the nearest player can pick them up.")
        .define("enforcement.block_loot_drops", true);

    private static final ModConfigSpec.BooleanValue BLOCK_MOB_REPLACEMENTS = BUILDER
        .comment("Apply [[mobs.replacements]] - swap locked mobs with a configured substitute on spawn.")
        .define("enforcement.block_mob_replacements", true);

    private static final ModConfigSpec.BooleanValue BLOCK_REGION_ENTRY = BUILDER
        .comment("Enforce [[regions]] gating: push players back from locked regions, cancel locked block breaks/places, and clear explosions in locked regions.")
        .define("enforcement.block_region_entry", true);

    private static final ModConfigSpec.IntValue REGION_TICK_FREQUENCY = BUILDER
        .comment("How often (in ticks) to run region/structure entry checks. 20 = once per second.")
        .defineInRange("enforcement.region_tick_frequency", 20, 1, 200);

    private static final ModConfigSpec.BooleanValue BLOCK_STRUCTURE_ENTRY = BUILDER
        .comment("Enforce [structures].locked_entry gating: push players out of locked structures, apply structures.rules flags.")
        .define("enforcement.block_structure_entry", true);

    private static final ModConfigSpec.BooleanValue ALLOW_CREATIVE_BYPASS = BUILDER
        .comment("Allow creative mode players to bypass stage locks",
                 "If true, players in creative mode can use/place locked items",
                 "They will still be locked when switching to survival")
        .define("enforcement.allow_creative_bypass", true);

    private static final ModConfigSpec.BooleanValue REVEAL_STAGE_NAMES_ONLY_TO_OPERATORS = BUILDER
        .comment("If true, non-operator players see generic lock messages without stage names.",
                 "Operators (permission level >= 2) always see the full stage name.",
                 "Useful for spoiler-free progression in modpacks.")
        .define("enforcement.reveal_stage_names_only_to_operators", true);

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

    // ============ Messages Settings (v1.4) ============
    // Every player-facing and command text is configurable here.
    // ALL messages support & color codes: &0-&f for colors, &k obfuscated, &l bold, &m strikethrough, &n underline, &o italic, &r reset.
    // Placeholders are substituted before color code parsing, so you can color placeholders too.

    // --- Tooltip Messages ---

    private static final ModConfigSpec.ConfigValue<String> MSG_TOOLTIP_MASKED_NAME = BUILDER
        .comment("Text shown in place of item name when mask_locked_item_names is true.",
                 "Supports & color codes. Example: '&4&lUnknown Item'")
        .define("messages.tooltip_masked_name", "&cUnknown Item");

    private static final ModConfigSpec.ConfigValue<String> MSG_TOOLTIP_ITEM_AND_RECIPE_LOCKED = BUILDER
        .comment("Tooltip header when both the item and its recipe are locked.",
                 "Supports & color codes.")
        .define("messages.tooltip_item_and_recipe_locked", "&c&l\uD83D\uDD12 Item and Recipe Locked");

    private static final ModConfigSpec.ConfigValue<String> MSG_TOOLTIP_ITEM_LOCKED = BUILDER
        .comment("Tooltip header when only the item is locked.",
                 "Supports & color codes.")
        .define("messages.tooltip_item_locked", "&c&l\uD83D\uDD12 Item Locked");

    private static final ModConfigSpec.ConfigValue<String> MSG_TOOLTIP_RECIPE_LOCKED = BUILDER
        .comment("Tooltip header when only the recipe is locked.",
                 "Supports & color codes.")
        .define("messages.tooltip_recipe_locked", "&c&l\uD83D\uDD12 Recipe Locked");

    private static final ModConfigSpec.ConfigValue<String> MSG_TOOLTIP_STAGE_REQUIRED = BUILDER
        .comment("Tooltip line showing required stage. {stage} = stage display name.",
                 "Supports & color codes.")
        .define("messages.tooltip_stage_required", "&7Stage required: &f{stage}");

    private static final ModConfigSpec.ConfigValue<String> MSG_TOOLTIP_CURRENT_STAGE = BUILDER
        .comment("Tooltip line showing current stage.",
                 "{stage} = current stage name, {progress} = progress string (e.g. 2/5).",
                 "Supports & color codes.")
        .define("messages.tooltip_current_stage", "&7Current stage: &f{stage} &8({progress})");

    // --- Chat / Enforcement Messages ---

    private static final ModConfigSpec.ConfigValue<String> MSG_ITEM_LOCKED = BUILDER
        .comment("Chat message when a player tries to use/pickup a locked item.",
                 "{stage} = required stage display name. Supports & color codes.")
        .define("messages.item_locked", "&c\uD83D\uDD12 You haven't unlocked this item yet! &7Required: &f{stage}");

    private static final ModConfigSpec.ConfigValue<String> MSG_TYPE_LOCKED = BUILDER
        .comment("Chat message for generic locked things (block, dimension, entity, recipe, interaction).",
                 "{type} = description (e.g. 'This block'), {stage} = required stage display name.",
                 "Supports & color codes.")
        .define("messages.type_locked", "&c\uD83D\uDD12 {type} is locked! &7Required: &f{stage}");

    private static final ModConfigSpec.ConfigValue<String> MSG_ITEM_LOCKED_GENERIC = BUILDER
        .comment("Generic chat message when reveal_stage_names_only_to_operators=true and player is non-op.",
                 "Supports & color codes.")
        .define("messages.item_locked_generic", "&c\uD83D\uDD12 You haven't unlocked this item yet!");

    private static final ModConfigSpec.ConfigValue<String> MSG_TYPE_LOCKED_GENERIC = BUILDER
        .comment("Generic chat message for blocks/dimensions/etc when player is non-op.",
                 "{type} = description. Supports & color codes.")
        .define("messages.type_locked_generic", "&c\uD83D\uDD12 {type} is locked!");

    private static final ModConfigSpec.ConfigValue<String> MSG_TOOLTIP_STAGE_REQUIRED_GENERIC = BUILDER
        .comment("Generic tooltip line when reveal_stage_names_only_to_operators=true and viewer is non-op.",
                 "Supports & color codes.")
        .define("messages.tooltip_stage_required_generic", "&7Progress further to unlock.");

    private static final ModConfigSpec.ConfigValue<String> MSG_ITEMS_DROPPED = BUILDER
        .comment("Chat message when locked items are dropped from inventory.",
                 "{count} = number of items dropped. Supports & color codes.")
        .define("messages.items_dropped", "&c\uD83D\uDD12 Dropped {count} locked items from your inventory!");

    private static final ModConfigSpec.ConfigValue<String> MSG_ITEMS_MOVED_HOTBAR = BUILDER
        .comment("Chat message when locked items are moved out of the hotbar.",
                 "{count} = number of items moved. Supports & color codes.")
        .define("messages.items_moved_hotbar", "&c\uD83D\uDD12 Moved {count} locked item(s) out of your hotbar!");

    private static final ModConfigSpec.ConfigValue<String> MSG_MISSING_DEPENDENCIES = BUILDER
        .comment("Chat message when a stage cannot be granted due to missing dependencies.",
                 "{stage} = stage path, {dependencies} = comma-separated missing deps.",
                 "Supports & color codes.")
        .define("messages.missing_dependencies", "&7[ProgressiveStages] Stage '&f{stage}&7' could not be granted: missing required stage(s): &f{dependencies}&7. Complete the prerequisites first.");

    // --- Command Messages ---

    private static final ModConfigSpec.ConfigValue<String> MSG_CMD_STAGE_NOT_FOUND = BUILDER
        .comment("Command error when a stage ID does not exist.",
                 "{stage} = the stage name. Supports & color codes.")
        .define("messages.cmd_stage_not_found", "&cStage not found: {stage}");

    private static final ModConfigSpec.ConfigValue<String> MSG_CMD_ALREADY_HAS_STAGE = BUILDER
        .comment("Command error when the player already has the stage.",
                 "{stage} = the stage name. Supports & color codes.")
        .define("messages.cmd_already_has_stage", "&ePlayer already has stage: {stage}");

    private static final ModConfigSpec.ConfigValue<String> MSG_CMD_GRANT_SUCCESS = BUILDER
        .comment("Command success when granting a stage.",
                 "{stage} = stage name, {player} = player name. Supports & color codes.")
        .define("messages.cmd_grant_success", "&aGranted stage &2{stage} &ato &f{player}");

    private static final ModConfigSpec.ConfigValue<String> MSG_CMD_GRANT_BYPASS = BUILDER
        .comment("Command success when granting with dependency bypass.",
                 "{stage} = stage name, {player} = player name. Supports & color codes.")
        .define("messages.cmd_grant_bypass", "&aGranted stage &2{stage} &ato &f{player} &e(dependency bypass)");

    private static final ModConfigSpec.ConfigValue<String> MSG_CMD_GRANT_MISSING_DEPS = BUILDER
        .comment("Command error when dependencies are missing.",
                 "{stage} = stage name, {player} = player name, {dependencies} = missing deps.",
                 "Supports & color codes.")
        .define("messages.cmd_grant_missing_deps", "&cCannot grant {stage}: &f{player} &cis missing dependencies: &6{dependencies}");

    private static final ModConfigSpec.ConfigValue<String> MSG_CMD_GRANT_BYPASS_HINT = BUILDER
        .comment("Hint shown after missing deps error.",
                 "Supports & color codes.")
        .define("messages.cmd_grant_bypass_hint", "&7&oType the command again within 10 seconds to bypass.");

    private static final ModConfigSpec.ConfigValue<String> MSG_CMD_REVOKE_SUCCESS = BUILDER
        .comment("Command success when revoking a stage.",
                 "{stage} = stage name, {player} = player name. Supports & color codes.")
        .define("messages.cmd_revoke_success", "&aRevoked stage &c{stage} &afrom &f{player}");

    private static final ModConfigSpec.ConfigValue<String> MSG_CMD_SPECIFY_PLAYER = BUILDER
        .comment("Command error when no player is specified for /stage list.",
                 "Supports & color codes.")
        .define("messages.cmd_specify_player", "&cSpecify a player");

    private static final ModConfigSpec.ConfigValue<String> MSG_CMD_LIST_HEADER = BUILDER
        .comment("Header for /stage list output.",
                 "{player} = player name, {count} = unlocked count, {total} = total stages.",
                 "Supports & color codes.")
        .define("messages.cmd_list_header", "&6=== Stages for &f{player} &6({count}/{total}) ===");

    private static final ModConfigSpec.ConfigValue<String> MSG_CMD_LIST_EMPTY = BUILDER
        .comment("Shown when player has no stages unlocked.",
                 "Supports & color codes.")
        .define("messages.cmd_list_empty", "&7  No stages unlocked");

    private static final ModConfigSpec.ConfigValue<String> MSG_CMD_CHECK_HAS = BUILDER
        .comment("Shown when player has the checked stage.",
                 "{player} = player name, {stage} = stage name. Supports & color codes.")
        .define("messages.cmd_check_has", "&f{player} &ahas &2{stage}");

    private static final ModConfigSpec.ConfigValue<String> MSG_CMD_CHECK_NOT_HAS = BUILDER
        .comment("Shown when player does NOT have the checked stage.",
                 "{player} = player name, {stage} = stage name. Supports & color codes.")
        .define("messages.cmd_check_not_has", "&f{player} &cdoes not have &4{stage}");

    private static final ModConfigSpec.ConfigValue<String> MSG_CMD_RELOAD_SUCCESS = BUILDER
        .comment("Shown after /progressivestages reload.",
                 "{count} = number of synced players. Supports & color codes.")
        .define("messages.cmd_reload_success", "&aReloaded stage definitions and triggers, synced {count} players. EMI will refresh.");

    private static final ModConfigSpec.ConfigValue<String> MSG_CMD_TRIGGER_INVALID_TYPE = BUILDER
        .comment("Shown when an invalid trigger type is given.",
                 "{type} = the invalid type. Supports & color codes.")
        .define("messages.cmd_trigger_invalid_type", "&cInvalid trigger type: {type}. Must be 'dimension' or 'boss'.");

    private static final ModConfigSpec.ConfigValue<String> MSG_CMD_TRIGGER_RESET = BUILDER
        .comment("Shown after resetting a trigger.",
                 "{type} = trigger type, {key} = trigger key, {player} = player name.",
                 "Supports & color codes.")
        .define("messages.cmd_trigger_reset", "&aReset {type} trigger '{key}' for {player}");

    // --- Command messages introduced by v2.0 text-audit pass ---
    // Most of these are used by /stage info, /stage tree, /stage validate, /stage list.
    // Available placeholders are documented per-key.

    private static final ModConfigSpec.ConfigValue<String> MSG_PREFIX = BUILDER
        .comment("Prefix used in some command output (e.g. validate header).",
                 "Supports & color codes.")
        .define("messages.prefix", "&7[ProgressiveStages] ");

    private static final ModConfigSpec.ConfigValue<String> MSG_TOOLTIP_CURRENT_STAGE_NONE = BUILDER
        .comment("Used in tooltips when the player has no current stage.",
                 "Supports & color codes.")
        .define("messages.tooltip_current_stage_none", "None");

    private static final ModConfigSpec.ConfigValue<String> MSG_CMD_LIST_ENTRY = BUILDER
        .comment("Format of one entry in /stage list. Placeholders:",
                 "{name} = display name (already coloured per has/has-not),",
                 "{check} = unicode check mark or empty,",
                 "{deps} = dependency suffix (or empty). Supports & color codes.")
        .define("messages.cmd_list_entry", "  &7• {name}{check}&7{deps}");

    private static final ModConfigSpec.ConfigValue<String> MSG_CMD_LIST_REQUIRES_FORMAT = BUILDER
        .comment("Suffix appended to a /stage list entry when it has dependencies.",
                 "{deps} = comma-separated dependency paths. Supports & color codes.")
        .define("messages.cmd_list_requires_format", " (requires: {deps})");

    private static final ModConfigSpec.ConfigValue<String> MSG_CMD_INFO_HEADER = BUILDER
        .comment("Header line for /stage info. {stage} = stage display name. Supports & color codes.")
        .define("messages.cmd_info_header", "&6=== {stage} ===");

    private static final ModConfigSpec.ConfigValue<String> MSG_CMD_INFO_ID = BUILDER
        .comment("/stage info ID line. {id} = full stage id. Supports & color codes.")
        .define("messages.cmd_info_id", "&7  ID: {id}");

    private static final ModConfigSpec.ConfigValue<String> MSG_CMD_INFO_DEPS_NONE = BUILDER
        .comment("/stage info dependencies line when there are none. Supports & color codes.")
        .define("messages.cmd_info_deps_none", "&7  Dependencies: (none)");

    private static final ModConfigSpec.ConfigValue<String> MSG_CMD_INFO_DEPS = BUILDER
        .comment("/stage info dependencies line. {deps} = comma-separated paths. Supports & color codes.")
        .define("messages.cmd_info_deps", "&e  Dependencies: {deps}");

    private static final ModConfigSpec.ConfigValue<String> MSG_CMD_INFO_DESCRIPTION = BUILDER
        .comment("/stage info description line. {description} = stage description. Supports & color codes.")
        .define("messages.cmd_info_description", "&7  Description: {description}");

    private static final ModConfigSpec.ConfigValue<String> MSG_CMD_INFO_TOTAL_LOCKS = BUILDER
        .comment("/stage info total locks line. {count} = lock count. Supports & color codes.")
        .define("messages.cmd_info_total_locks", "&7  Total locks: {count}");

    private static final ModConfigSpec.ConfigValue<String> MSG_CMD_TREE_HEADER = BUILDER
        .comment("Header for /stage tree. Supports & color codes.")
        .define("messages.cmd_tree_header", "&6=== Stage Dependency Tree ===");

    private static final ModConfigSpec.ConfigValue<String> MSG_CMD_TREE_EMPTY = BUILDER
        .comment("/stage tree shown when no stages defined. Supports & color codes.")
        .define("messages.cmd_tree_empty", "&7  (No stages defined)");

    private static final ModConfigSpec.ConfigValue<String> MSG_CMD_TREE_ORPHANED = BUILDER
        .comment("/stage tree line for an orphaned stage (dependency missing).",
                 "{path} = stage path. Supports & color codes.")
        .define("messages.cmd_tree_orphaned", "&c  ⚠ {path} (orphaned - dependency not found)");

    private static final ModConfigSpec.ConfigValue<String> MSG_CMD_TREE_NODE = BUILDER
        .comment("/stage tree node line. {indent} = ASCII indent prefix,",
                 "{name} = display name, {path} = stage path. Supports & color codes.")
        .define("messages.cmd_tree_node", "&f{indent}{name}&8 [{path}]");

    private static final ModConfigSpec.ConfigValue<String> MSG_CMD_VALIDATE_HEADER = BUILDER
        .comment("Header for /stage validate. Supports & color codes.")
        .define("messages.cmd_validate_header", "&6=== Stage Validation ===");

    private static final ModConfigSpec.ConfigValue<String> MSG_CMD_VALIDATE_STARTING = BUILDER
        .comment("/stage validate progress line. {prefix} = configurable prefix. Supports & color codes.")
        .define("messages.cmd_validate_starting", "&7{prefix}Validating stage files...");

    private static final ModConfigSpec.ConfigValue<String> MSG_CMD_VALIDATE_FOUND = BUILDER
        .comment("/stage validate count line. {count} = file count. Supports & color codes.")
        .define("messages.cmd_validate_found", "&7  Found {count} stage files");

    private static final ModConfigSpec.ConfigValue<String> MSG_CMD_VALIDATE_SUCCESS = BUILDER
        .comment("/stage validate per-file success line. {file} = file name. Supports & color codes.")
        .define("messages.cmd_validate_success", "&a  SUCCESS: {file} validated");

    private static final ModConfigSpec.ConfigValue<String> MSG_CMD_VALIDATE_SYNTAX_ERROR = BUILDER
        .comment("/stage validate syntax-error line. {file} = file, {error} = message. Supports & color codes.")
        .define("messages.cmd_validate_syntax_error", "&c  ERROR: {file} has {error}");

    private static final ModConfigSpec.ConfigValue<String> MSG_CMD_VALIDATE_VALIDATION_ERROR = BUILDER
        .comment("/stage validate validation-error line. {file} = file, {error} = message. Supports & color codes.")
        .define("messages.cmd_validate_validation_error", "&c  ERROR: {file} - {error}");

    private static final ModConfigSpec.ConfigValue<String> MSG_CMD_VALIDATE_INVALID_ITEM = BUILDER
        .comment("/stage validate invalid-item bullet. {item} = item id. Supports & color codes.")
        .define("messages.cmd_validate_invalid_item", "&e      - {item}");

    private static final ModConfigSpec.ConfigValue<String> MSG_CMD_VALIDATE_DEP_WARNING = BUILDER
        .comment("/stage validate dependency warning. {message} = warning text. Supports & color codes.")
        .define("messages.cmd_validate_dep_warning", "&e  ⚠ {message}");

    private static final ModConfigSpec.ConfigValue<String> MSG_CMD_VALIDATE_STARTING_NOT_FOUND = BUILDER
        .comment("/stage validate missing-starting-stage line. {stage} = id. Supports & color codes.")
        .define("messages.cmd_validate_starting_not_found", "&c  ✗ Starting stage not found: {stage}");

    private static final ModConfigSpec.ConfigValue<String> MSG_CMD_VALIDATE_SUMMARY_OK = BUILDER
        .comment("/stage validate summary, all-passed variant.",
                 "{valid} = valid count, {total} = total count. Supports & color codes.")
        .define("messages.cmd_validate_summary_ok", "&a  SUMMARY: {valid}/{total} stage files valid, all passed!");

    private static final ModConfigSpec.ConfigValue<String> MSG_CMD_VALIDATE_SUMMARY_ERRORS = BUILDER
        .comment("/stage validate summary, errors-or-warnings variant.",
                 "{valid}, {total}, {errors_part} (e.g. '2 syntax error(s), '), {warnings_part}.",
                 "Supports & color codes.")
        .define("messages.cmd_validate_summary_errors", "&c  SUMMARY: {valid}/{total} stage files valid, {errors_part}{warnings_part}");

    // --- FTB Quests diagnose status messages (v2.0 audit) ---
    private static final ModConfigSpec.ConfigValue<String> MSG_CMD_FTB_STATUS_HEADER = BUILDER
        .comment("Header line for /stage diagnose ftbquests. Supports & color codes.")
        .define("messages.cmd_ftb_status_header", "&6=== FTB Quests Integration Status ===");
    private static final ModConfigSpec.ConfigValue<String> MSG_CMD_FTB_STATUS_CONFIG_ENABLED = BUILDER
        .comment("/stage diagnose ftbquests — config enabled. {value} = YES/NO. Supports & color codes.")
        .define("messages.cmd_ftb_status_config_enabled", "&7  Config Enabled: &f{value}");
    private static final ModConfigSpec.ConfigValue<String> MSG_CMD_FTB_STATUS_PROVIDER_REGISTERED = BUILDER
        .comment("/stage diagnose ftbquests — provider registered. {value} = YES/NO. Supports & color codes.")
        .define("messages.cmd_ftb_status_provider_registered", "&7  Provider Registered: &f{value}");
    private static final ModConfigSpec.ConfigValue<String> MSG_CMD_FTB_STATUS_COMPAT_ACTIVE = BUILDER
        .comment("/stage diagnose ftbquests — compat active. {value} = YES/NO. Supports & color codes.")
        .define("messages.cmd_ftb_status_compat_active", "&7  Compat Active: &f{value}");
    private static final ModConfigSpec.ConfigValue<String> MSG_CMD_FTB_STATUS_PENDING_RECHECKS = BUILDER
        .comment("/stage diagnose ftbquests — pending rechecks count. {value} = count. Supports & color codes.")
        .define("messages.cmd_ftb_status_pending_rechecks", "&7  Pending Rechecks: &f{value}");
    private static final ModConfigSpec.ConfigValue<String> MSG_CMD_FTB_STATUS_RECHECK_BUDGET = BUILDER
        .comment("/stage diagnose ftbquests — recheck budget. {value} = budget. Supports & color codes.")
        .define("messages.cmd_ftb_status_recheck_budget", "&7  Recheck Budget: &f{value}/tick");
    private static final ModConfigSpec.ConfigValue<String> MSG_CMD_FTB_STATUS_PREVIOUS_PROVIDER = BUILDER
        .comment("/stage diagnose ftbquests — previous provider. {value} = YES/NO. Supports & color codes.")
        .define("messages.cmd_ftb_status_previous_provider", "&7  Previous Provider Stored: &f{value}");
    private static final ModConfigSpec.ConfigValue<String> MSG_CMD_FTB_STATUS_PLAYER_HEADER = BUILDER
        .comment("/stage diagnose ftbquests — player section header. {player} = name. Supports & color codes.")
        .define("messages.cmd_ftb_status_player_header", "&b  --- Player: {player} ---");
    private static final ModConfigSpec.ConfigValue<String> MSG_CMD_FTB_STATUS_PLAYER_STAGES = BUILDER
        .comment("/stage diagnose ftbquests — player stage count. {value} = count. Supports & color codes.")
        .define("messages.cmd_ftb_status_player_stages", "&f  Player Stages: {value}");
    private static final ModConfigSpec.ConfigValue<String> MSG_CMD_FTB_STATUS_PLAYER_STAGE_LIST = BUILDER
        .comment("/stage diagnose ftbquests — player stage list. {list} = comma-separated. Supports & color codes.")
        .define("messages.cmd_ftb_status_player_stage_list", "&7    {list}");
    private static final ModConfigSpec.ConfigValue<String> MSG_CMD_FTB_STATUS_RECHECK_IN_PROGRESS = BUILDER
        .comment("/stage diagnose ftbquests — recheck in progress. {value} = YES/NO. Supports & color codes.")
        .define("messages.cmd_ftb_status_recheck_in_progress", "&7  Recheck In Progress: &f{value}");

    // --- Type labels used as {type} placeholder in messages.type_locked ---
    private static final ModConfigSpec.ConfigValue<String> MSG_TYPE_LABEL_BLOCK = BUILDER
        .comment("Type label used for blocks in chat messages. Substituted into {type} of type_locked.")
        .define("messages.type_label_block", "This block");
    private static final ModConfigSpec.ConfigValue<String> MSG_TYPE_LABEL_DIMENSION = BUILDER
        .comment("Type label used for dimensions in chat messages.")
        .define("messages.type_label_dimension", "This dimension");
    private static final ModConfigSpec.ConfigValue<String> MSG_TYPE_LABEL_ENTITY = BUILDER
        .comment("Type label used for entities in chat messages.")
        .define("messages.type_label_entity", "This entity");
    private static final ModConfigSpec.ConfigValue<String> MSG_TYPE_LABEL_RECIPE = BUILDER
        .comment("Type label used for recipes in chat messages.")
        .define("messages.type_label_recipe", "This recipe");
    private static final ModConfigSpec.ConfigValue<String> MSG_TYPE_LABEL_INTERACTION = BUILDER
        .comment("Type label used for blocked interactions in chat messages.")
        .define("messages.type_label_interaction", "This interaction");
    private static final ModConfigSpec.ConfigValue<String> MSG_TYPE_LABEL_FLUID = BUILDER
        .comment("Type label used for fluids in chat messages.")
        .define("messages.type_label_fluid", "This fluid");
    private static final ModConfigSpec.ConfigValue<String> MSG_TYPE_LABEL_ENCHANTMENT = BUILDER
        .comment("Type label used for enchantments in chat messages.")
        .define("messages.type_label_enchantment", "This enchantment");
    private static final ModConfigSpec.ConfigValue<String> MSG_TYPE_LABEL_SCREEN = BUILDER
        .comment("Type label used for blocked screens (block-side) in chat messages.")
        .define("messages.type_label_screen", "This screen");
    private static final ModConfigSpec.ConfigValue<String> MSG_TYPE_LABEL_SCREEN_ITEM = BUILDER
        .comment("Type label used for blocked item-opened GUIs (backpacks, portable crafting, etc.) in chat messages.")
        .define("messages.type_label_screen_item", "This item's GUI");
    private static final ModConfigSpec.ConfigValue<String> MSG_TYPE_LABEL_STRUCTURE_CONTENTS = BUILDER
        .comment("Type label used for blocked structure contents (containers inside locked structures) in chat messages.")
        .define("messages.type_label_structure_contents", "This structure's contents");
    private static final ModConfigSpec.ConfigValue<String> MSG_TYPE_LABEL_PET_TAMING = BUILDER
        .comment("Type label used for blocked pet taming in chat messages.")
        .define("messages.type_label_pet_taming", "Taming this pet");
    private static final ModConfigSpec.ConfigValue<String> MSG_TYPE_LABEL_PET_BREEDING = BUILDER
        .comment("Type label used for blocked pet breeding in chat messages.")
        .define("messages.type_label_pet_breeding", "Breeding this pet");
    private static final ModConfigSpec.ConfigValue<String> MSG_TYPE_LABEL_PET_COMMANDING = BUILDER
        .comment("Type label used for blocked pet commanding (sit/stand/follow) in chat messages.")
        .define("messages.type_label_pet_commanding", "Commanding this pet");

    // ============ Integration Settings ============

    private static final ModConfigSpec.BooleanValue FTB_TEAMS_INTEGRATION = BUILDER
        .comment("Enable FTB Teams integration",
                 "If true and FTB Teams is installed, stages are shared across team members",
                 "If false or FTB Teams is not installed, falls back to solo mode automatically",
                 "Disabling this prevents ProgressiveStages from loading FTB Teams classes entirely")
        .define("integration.ftbteams.enabled", true);

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

    private static final ModConfigSpec.BooleanValue FTBQUESTS_TEAM_MODE = BUILDER
        .comment("If true, FTB Quests stage rewards/tasks delegate has/add/remove to FTB Teams'",
                 "TeamStagesHelper instead of ProgressiveStages' own backend.",
                 "Useful when running in solo team_mode but wanting team-shared questing.",
                 "Falls back to ProgressiveStages' own backend if FTB Teams isn't available.")
        .define("integration.ftbquests.team_mode", false);

    public static final ModConfigSpec SPEC = BUILDER.build();

    // ============ Cached Values ============

    private static List<String> startingStages;
    private static String teamMode;
    private static boolean debugLogging;
    private static boolean linearProgression;
    private static boolean reapplyStartingStagesOnLogin;
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
    private static boolean blockMobSpawns;
    private static int mobSpawnCheckRadius;
    private static boolean blockEnchants;
    private static boolean blockScreenOpen;
    private static boolean blockCropGrowth;
    private static boolean blockPetInteract;
    private static boolean blockLootDrops;
    private static boolean blockMobReplacements;
    private static boolean blockRegionEntry;
    private static int regionTickFrequency;
    private static boolean blockStructureEntry;
    private static boolean allowCreativeBypass;
    private static boolean revealStageNamesOnlyToOperators;
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
    private static boolean ftbTeamsIntegration;
    private static boolean ftbQuestsIntegration;
    private static int ftbRecheckBudget;
    private static boolean ftbquestsTeamMode;

    // Messages
    private static String msgTooltipMaskedName;
    private static String msgTooltipItemAndRecipeLocked;
    private static String msgTooltipItemLocked;
    private static String msgTooltipRecipeLocked;
    private static String msgTooltipStageRequired;
    private static String msgTooltipCurrentStage;
    private static String msgItemLocked;
    private static String msgTypeLocked;
    private static String msgItemLockedGeneric;
    private static String msgTypeLockedGeneric;
    private static String msgTooltipStageRequiredGeneric;
    private static String msgItemsDropped;
    private static String msgItemsMovedHotbar;
    private static String msgMissingDependencies;
    // Command messages
    private static String msgCmdStageNotFound;
    private static String msgCmdAlreadyHasStage;
    private static String msgCmdGrantSuccess;
    private static String msgCmdGrantBypass;
    private static String msgCmdGrantMissingDeps;
    private static String msgCmdGrantBypassHint;
    private static String msgCmdRevokeSuccess;
    private static String msgCmdSpecifyPlayer;
    private static String msgCmdListHeader;
    private static String msgCmdListEmpty;
    private static String msgCmdCheckHas;
    private static String msgCmdCheckNotHas;
    private static String msgCmdReloadSuccess;
    private static String msgCmdTriggerInvalidType;
    private static String msgCmdTriggerReset;
    // Port E: text audit additions
    private static String msgPrefix;
    private static String msgTooltipCurrentStageNone;
    private static String msgCmdListEntry;
    private static String msgCmdListRequiresFormat;
    private static String msgCmdInfoHeader;
    private static String msgCmdInfoId;
    private static String msgCmdInfoDepsNone;
    private static String msgCmdInfoDeps;
    private static String msgCmdInfoDescription;
    private static String msgCmdInfoTotalLocks;
    private static String msgCmdTreeHeader;
    private static String msgCmdTreeEmpty;
    private static String msgCmdTreeOrphaned;
    private static String msgCmdTreeNode;
    private static String msgCmdValidateHeader;
    private static String msgCmdValidateStarting;
    private static String msgCmdValidateFound;
    private static String msgCmdValidateSuccess;
    private static String msgCmdValidateSyntaxError;
    private static String msgCmdValidateValidationError;
    private static String msgCmdValidateInvalidItem;
    private static String msgCmdValidateDepWarning;
    private static String msgCmdValidateStartingNotFound;
    private static String msgCmdValidateSummaryOk;
    private static String msgCmdValidateSummaryErrors;
    // FTB diagnose
    private static String msgCmdFtbStatusHeader;
    private static String msgCmdFtbStatusConfigEnabled;
    private static String msgCmdFtbStatusProviderRegistered;
    private static String msgCmdFtbStatusCompatActive;
    private static String msgCmdFtbStatusPendingRechecks;
    private static String msgCmdFtbStatusRecheckBudget;
    private static String msgCmdFtbStatusPreviousProvider;
    private static String msgCmdFtbStatusPlayerHeader;
    private static String msgCmdFtbStatusPlayerStages;
    private static String msgCmdFtbStatusPlayerStageList;
    private static String msgCmdFtbStatusRecheckInProgress;
    // Type labels
    private static String msgTypeLabelBlock;
    private static String msgTypeLabelDimension;
    private static String msgTypeLabelEntity;
    private static String msgTypeLabelRecipe;
    private static String msgTypeLabelInteraction;
    private static String msgTypeLabelFluid;
    private static String msgTypeLabelEnchantment;
    private static String msgTypeLabelScreen;
    private static String msgTypeLabelScreenItem;
    private static String msgTypeLabelStructureContents;
    private static String msgTypeLabelPetTaming;
    private static String msgTypeLabelPetBreeding;
    private static String msgTypeLabelPetCommanding;

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
        reapplyStartingStagesOnLogin = REAPPLY_STARTING_STAGES_ON_LOGIN.get();
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
        blockMobSpawns = BLOCK_MOB_SPAWNS.get();
        mobSpawnCheckRadius = MOB_SPAWN_CHECK_RADIUS.get();
        blockEnchants = BLOCK_ENCHANTS.get();
        blockScreenOpen = BLOCK_SCREEN_OPEN.get();
        blockCropGrowth = BLOCK_CROP_GROWTH.get();
        blockPetInteract = BLOCK_PET_INTERACT.get();
        blockLootDrops = BLOCK_LOOT_DROPS.get();
        blockMobReplacements = BLOCK_MOB_REPLACEMENTS.get();
        blockRegionEntry = BLOCK_REGION_ENTRY.get();
        regionTickFrequency = REGION_TICK_FREQUENCY.get();
        blockStructureEntry = BLOCK_STRUCTURE_ENTRY.get();
        allowCreativeBypass = ALLOW_CREATIVE_BYPASS.get();
        revealStageNamesOnlyToOperators = REVEAL_STAGE_NAMES_ONLY_TO_OPERATORS.get();
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
        ftbTeamsIntegration = FTB_TEAMS_INTEGRATION.get();
        ftbQuestsIntegration = FTB_QUESTS_INTEGRATION.get();
        ftbRecheckBudget = FTB_RECHECK_BUDGET.get();
        ftbquestsTeamMode = FTBQUESTS_TEAM_MODE.get();

        // Messages
        msgTooltipMaskedName = MSG_TOOLTIP_MASKED_NAME.get();
        msgTooltipItemAndRecipeLocked = MSG_TOOLTIP_ITEM_AND_RECIPE_LOCKED.get();
        msgTooltipItemLocked = MSG_TOOLTIP_ITEM_LOCKED.get();
        msgTooltipRecipeLocked = MSG_TOOLTIP_RECIPE_LOCKED.get();
        msgTooltipStageRequired = MSG_TOOLTIP_STAGE_REQUIRED.get();
        msgTooltipCurrentStage = MSG_TOOLTIP_CURRENT_STAGE.get();
        msgItemLocked = MSG_ITEM_LOCKED.get();
        msgTypeLocked = MSG_TYPE_LOCKED.get();
        msgItemLockedGeneric = MSG_ITEM_LOCKED_GENERIC.get();
        msgTypeLockedGeneric = MSG_TYPE_LOCKED_GENERIC.get();
        msgTooltipStageRequiredGeneric = MSG_TOOLTIP_STAGE_REQUIRED_GENERIC.get();
        msgItemsDropped = MSG_ITEMS_DROPPED.get();
        msgItemsMovedHotbar = MSG_ITEMS_MOVED_HOTBAR.get();
        msgMissingDependencies = MSG_MISSING_DEPENDENCIES.get();
        // Command messages
        msgCmdStageNotFound = MSG_CMD_STAGE_NOT_FOUND.get();
        msgCmdAlreadyHasStage = MSG_CMD_ALREADY_HAS_STAGE.get();
        msgCmdGrantSuccess = MSG_CMD_GRANT_SUCCESS.get();
        msgCmdGrantBypass = MSG_CMD_GRANT_BYPASS.get();
        msgCmdGrantMissingDeps = MSG_CMD_GRANT_MISSING_DEPS.get();
        msgCmdGrantBypassHint = MSG_CMD_GRANT_BYPASS_HINT.get();
        msgCmdRevokeSuccess = MSG_CMD_REVOKE_SUCCESS.get();
        msgCmdSpecifyPlayer = MSG_CMD_SPECIFY_PLAYER.get();
        msgCmdListHeader = MSG_CMD_LIST_HEADER.get();
        msgCmdListEmpty = MSG_CMD_LIST_EMPTY.get();
        msgCmdCheckHas = MSG_CMD_CHECK_HAS.get();
        msgCmdCheckNotHas = MSG_CMD_CHECK_NOT_HAS.get();
        msgCmdReloadSuccess = MSG_CMD_RELOAD_SUCCESS.get();
        msgCmdTriggerInvalidType = MSG_CMD_TRIGGER_INVALID_TYPE.get();
        msgCmdTriggerReset = MSG_CMD_TRIGGER_RESET.get();
        msgPrefix = MSG_PREFIX.get();
        msgTooltipCurrentStageNone = MSG_TOOLTIP_CURRENT_STAGE_NONE.get();
        msgCmdListEntry = MSG_CMD_LIST_ENTRY.get();
        msgCmdListRequiresFormat = MSG_CMD_LIST_REQUIRES_FORMAT.get();
        msgCmdInfoHeader = MSG_CMD_INFO_HEADER.get();
        msgCmdInfoId = MSG_CMD_INFO_ID.get();
        msgCmdInfoDepsNone = MSG_CMD_INFO_DEPS_NONE.get();
        msgCmdInfoDeps = MSG_CMD_INFO_DEPS.get();
        msgCmdInfoDescription = MSG_CMD_INFO_DESCRIPTION.get();
        msgCmdInfoTotalLocks = MSG_CMD_INFO_TOTAL_LOCKS.get();
        msgCmdTreeHeader = MSG_CMD_TREE_HEADER.get();
        msgCmdTreeEmpty = MSG_CMD_TREE_EMPTY.get();
        msgCmdTreeOrphaned = MSG_CMD_TREE_ORPHANED.get();
        msgCmdTreeNode = MSG_CMD_TREE_NODE.get();
        msgCmdValidateHeader = MSG_CMD_VALIDATE_HEADER.get();
        msgCmdValidateStarting = MSG_CMD_VALIDATE_STARTING.get();
        msgCmdValidateFound = MSG_CMD_VALIDATE_FOUND.get();
        msgCmdValidateSuccess = MSG_CMD_VALIDATE_SUCCESS.get();
        msgCmdValidateSyntaxError = MSG_CMD_VALIDATE_SYNTAX_ERROR.get();
        msgCmdValidateValidationError = MSG_CMD_VALIDATE_VALIDATION_ERROR.get();
        msgCmdValidateInvalidItem = MSG_CMD_VALIDATE_INVALID_ITEM.get();
        msgCmdValidateDepWarning = MSG_CMD_VALIDATE_DEP_WARNING.get();
        msgCmdValidateStartingNotFound = MSG_CMD_VALIDATE_STARTING_NOT_FOUND.get();
        msgCmdValidateSummaryOk = MSG_CMD_VALIDATE_SUMMARY_OK.get();
        msgCmdValidateSummaryErrors = MSG_CMD_VALIDATE_SUMMARY_ERRORS.get();
        msgCmdFtbStatusHeader = MSG_CMD_FTB_STATUS_HEADER.get();
        msgCmdFtbStatusConfigEnabled = MSG_CMD_FTB_STATUS_CONFIG_ENABLED.get();
        msgCmdFtbStatusProviderRegistered = MSG_CMD_FTB_STATUS_PROVIDER_REGISTERED.get();
        msgCmdFtbStatusCompatActive = MSG_CMD_FTB_STATUS_COMPAT_ACTIVE.get();
        msgCmdFtbStatusPendingRechecks = MSG_CMD_FTB_STATUS_PENDING_RECHECKS.get();
        msgCmdFtbStatusRecheckBudget = MSG_CMD_FTB_STATUS_RECHECK_BUDGET.get();
        msgCmdFtbStatusPreviousProvider = MSG_CMD_FTB_STATUS_PREVIOUS_PROVIDER.get();
        msgCmdFtbStatusPlayerHeader = MSG_CMD_FTB_STATUS_PLAYER_HEADER.get();
        msgCmdFtbStatusPlayerStages = MSG_CMD_FTB_STATUS_PLAYER_STAGES.get();
        msgCmdFtbStatusPlayerStageList = MSG_CMD_FTB_STATUS_PLAYER_STAGE_LIST.get();
        msgCmdFtbStatusRecheckInProgress = MSG_CMD_FTB_STATUS_RECHECK_IN_PROGRESS.get();
        msgTypeLabelBlock = MSG_TYPE_LABEL_BLOCK.get();
        msgTypeLabelDimension = MSG_TYPE_LABEL_DIMENSION.get();
        msgTypeLabelEntity = MSG_TYPE_LABEL_ENTITY.get();
        msgTypeLabelRecipe = MSG_TYPE_LABEL_RECIPE.get();
        msgTypeLabelInteraction = MSG_TYPE_LABEL_INTERACTION.get();
        msgTypeLabelFluid = MSG_TYPE_LABEL_FLUID.get();
        msgTypeLabelEnchantment = MSG_TYPE_LABEL_ENCHANTMENT.get();
        msgTypeLabelScreen = MSG_TYPE_LABEL_SCREEN.get();
        msgTypeLabelScreenItem = MSG_TYPE_LABEL_SCREEN_ITEM.get();
        msgTypeLabelStructureContents = MSG_TYPE_LABEL_STRUCTURE_CONTENTS.get();
        msgTypeLabelPetTaming = MSG_TYPE_LABEL_PET_TAMING.get();
        msgTypeLabelPetBreeding = MSG_TYPE_LABEL_PET_BREEDING.get();
        msgTypeLabelPetCommanding = MSG_TYPE_LABEL_PET_COMMANDING.get();

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
    public static boolean isReapplyStartingStagesOnLogin() { return reapplyStartingStagesOnLogin; }
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
    public static boolean isBlockMobSpawns() { return blockMobSpawns; }
    public static int getMobSpawnCheckRadius() { return mobSpawnCheckRadius; }
    public static boolean isBlockEnchants() { return blockEnchants; }
    public static boolean isBlockScreenOpen() { return blockScreenOpen; }
    public static boolean isBlockCropGrowth() { return blockCropGrowth; }
    public static boolean isBlockPetInteract() { return blockPetInteract; }
    public static boolean isBlockLootDrops() { return blockLootDrops; }
    public static boolean isBlockMobReplacements() { return blockMobReplacements; }
    public static boolean isBlockRegionEntry() { return blockRegionEntry; }
    public static int getRegionTickFrequency() { return regionTickFrequency; }
    public static boolean isBlockStructureEntry() { return blockStructureEntry; }
    public static boolean isAllowCreativeBypass() { return allowCreativeBypass; }
    public static boolean isRevealStageNamesOnlyToOperators() { return revealStageNamesOnlyToOperators; }
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
    public static boolean isFtbTeamsIntegrationEnabled() { return ftbTeamsIntegration; }
    public static boolean isFtbQuestsIntegrationEnabled() { return ftbQuestsIntegration; }
    public static int getFtbRecheckBudget() { return ftbRecheckBudget; }
    public static boolean isFtbquestsTeamMode() { return ftbquestsTeamMode; }

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
    public static String getMsgItemLockedGeneric() { return msgItemLockedGeneric != null ? msgItemLockedGeneric : "&c\uD83D\uDD12 You haven't unlocked this item yet!"; }
    public static String getMsgTypeLockedGeneric() { return msgTypeLockedGeneric != null ? msgTypeLockedGeneric : "&c\uD83D\uDD12 {type} is locked!"; }
    public static String getMsgTooltipStageRequiredGeneric() { return msgTooltipStageRequiredGeneric != null ? msgTooltipStageRequiredGeneric : "&7Progress further to unlock."; }
    public static String getMsgItemsDropped() { return msgItemsDropped != null ? msgItemsDropped : "&c\uD83D\uDD12 Dropped {count} locked items from your inventory!"; }
    public static String getMsgItemsMovedHotbar() { return msgItemsMovedHotbar != null ? msgItemsMovedHotbar : "&c\uD83D\uDD12 Moved {count} locked item(s) out of your hotbar!"; }
    public static String getMsgMissingDependencies() { return msgMissingDependencies != null ? msgMissingDependencies : "[ProgressiveStages] Stage '{stage}' could not be granted: missing required stage(s): {dependencies}. Complete the prerequisites first."; }
    // Command message getters
    public static String getMsgCmdStageNotFound() { return msgCmdStageNotFound != null ? msgCmdStageNotFound : "&cStage not found: {stage}"; }
    public static String getMsgCmdAlreadyHasStage() { return msgCmdAlreadyHasStage != null ? msgCmdAlreadyHasStage : "&ePlayer already has stage: {stage}"; }
    public static String getMsgCmdGrantSuccess() { return msgCmdGrantSuccess != null ? msgCmdGrantSuccess : "&aGranted stage &2{stage} &ato &f{player}"; }
    public static String getMsgCmdGrantBypass() { return msgCmdGrantBypass != null ? msgCmdGrantBypass : "&aGranted stage &2{stage} &ato &f{player} &e(dependency bypass)"; }
    public static String getMsgCmdGrantMissingDeps() { return msgCmdGrantMissingDeps != null ? msgCmdGrantMissingDeps : "&cCannot grant {stage}: &f{player} &cis missing dependencies: &6{dependencies}"; }
    public static String getMsgCmdGrantBypassHint() { return msgCmdGrantBypassHint != null ? msgCmdGrantBypassHint : "&7&oType the command again within 10 seconds to bypass."; }
    public static String getMsgCmdRevokeSuccess() { return msgCmdRevokeSuccess != null ? msgCmdRevokeSuccess : "&aRevoked stage &c{stage} &afrom &f{player}"; }
    public static String getMsgCmdSpecifyPlayer() { return msgCmdSpecifyPlayer != null ? msgCmdSpecifyPlayer : "&cSpecify a player"; }
    public static String getMsgCmdListHeader() { return msgCmdListHeader != null ? msgCmdListHeader : "&6=== Stages for &f{player} &6({count}/{total}) ==="; }
    public static String getMsgCmdListEmpty() { return msgCmdListEmpty != null ? msgCmdListEmpty : "&7  No stages unlocked"; }
    public static String getMsgCmdCheckHas() { return msgCmdCheckHas != null ? msgCmdCheckHas : "&f{player} &ahas &2{stage}"; }
    public static String getMsgCmdCheckNotHas() { return msgCmdCheckNotHas != null ? msgCmdCheckNotHas : "&f{player} &cdoes not have &4{stage}"; }
    public static String getMsgCmdReloadSuccess() { return msgCmdReloadSuccess != null ? msgCmdReloadSuccess : "&aReloaded stage definitions and triggers, synced {count} players. EMI will refresh."; }
    public static String getMsgCmdTriggerInvalidType() { return msgCmdTriggerInvalidType != null ? msgCmdTriggerInvalidType : "&cInvalid trigger type: {type}. Must be 'dimension' or 'boss'."; }
    public static String getMsgCmdTriggerReset() { return msgCmdTriggerReset != null ? msgCmdTriggerReset : "&aReset {type} trigger '{key}' for {player}"; }
    public static String getMsgPrefix() { return msgPrefix != null ? msgPrefix : "&7[ProgressiveStages] "; }
    public static String getMsgTooltipCurrentStageNone() { return msgTooltipCurrentStageNone != null ? msgTooltipCurrentStageNone : "None"; }
    public static String getMsgCmdListEntry() { return msgCmdListEntry != null ? msgCmdListEntry : "  &7• {name}{check}&7{deps}"; }
    public static String getMsgCmdListRequiresFormat() { return msgCmdListRequiresFormat != null ? msgCmdListRequiresFormat : " (requires: {deps})"; }
    public static String getMsgCmdInfoHeader() { return msgCmdInfoHeader != null ? msgCmdInfoHeader : "&6=== {stage} ==="; }
    public static String getMsgCmdInfoId() { return msgCmdInfoId != null ? msgCmdInfoId : "&7  ID: {id}"; }
    public static String getMsgCmdInfoDepsNone() { return msgCmdInfoDepsNone != null ? msgCmdInfoDepsNone : "&7  Dependencies: (none)"; }
    public static String getMsgCmdInfoDeps() { return msgCmdInfoDeps != null ? msgCmdInfoDeps : "&e  Dependencies: {deps}"; }
    public static String getMsgCmdInfoDescription() { return msgCmdInfoDescription != null ? msgCmdInfoDescription : "&7  Description: {description}"; }
    public static String getMsgCmdInfoTotalLocks() { return msgCmdInfoTotalLocks != null ? msgCmdInfoTotalLocks : "&7  Total locks: {count}"; }
    public static String getMsgCmdTreeHeader() { return msgCmdTreeHeader != null ? msgCmdTreeHeader : "&6=== Stage Dependency Tree ==="; }
    public static String getMsgCmdTreeEmpty() { return msgCmdTreeEmpty != null ? msgCmdTreeEmpty : "&7  (No stages defined)"; }
    public static String getMsgCmdTreeOrphaned() { return msgCmdTreeOrphaned != null ? msgCmdTreeOrphaned : "&c  ⚠ {path} (orphaned - dependency not found)"; }
    public static String getMsgCmdTreeNode() { return msgCmdTreeNode != null ? msgCmdTreeNode : "&f{indent}{name}&8 [{path}]"; }
    public static String getMsgCmdValidateHeader() { return msgCmdValidateHeader != null ? msgCmdValidateHeader : "&6=== Stage Validation ==="; }
    public static String getMsgCmdValidateStarting() { return msgCmdValidateStarting != null ? msgCmdValidateStarting : "&7{prefix}Validating stage files..."; }
    public static String getMsgCmdValidateFound() { return msgCmdValidateFound != null ? msgCmdValidateFound : "&7  Found {count} stage files"; }
    public static String getMsgCmdValidateSuccess() { return msgCmdValidateSuccess != null ? msgCmdValidateSuccess : "&a  SUCCESS: {file} validated"; }
    public static String getMsgCmdValidateSyntaxError() { return msgCmdValidateSyntaxError != null ? msgCmdValidateSyntaxError : "&c  ERROR: {file} has {error}"; }
    public static String getMsgCmdValidateValidationError() { return msgCmdValidateValidationError != null ? msgCmdValidateValidationError : "&c  ERROR: {file} - {error}"; }
    public static String getMsgCmdValidateInvalidItem() { return msgCmdValidateInvalidItem != null ? msgCmdValidateInvalidItem : "&e      - {item}"; }
    public static String getMsgCmdValidateDepWarning() { return msgCmdValidateDepWarning != null ? msgCmdValidateDepWarning : "&e  ⚠ {message}"; }
    public static String getMsgCmdValidateStartingNotFound() { return msgCmdValidateStartingNotFound != null ? msgCmdValidateStartingNotFound : "&c  ✗ Starting stage not found: {stage}"; }
    public static String getMsgCmdValidateSummaryOk() { return msgCmdValidateSummaryOk != null ? msgCmdValidateSummaryOk : "&a  SUMMARY: {valid}/{total} stage files valid, all passed!"; }
    public static String getMsgCmdValidateSummaryErrors() { return msgCmdValidateSummaryErrors != null ? msgCmdValidateSummaryErrors : "&c  SUMMARY: {valid}/{total} stage files valid, {errors_part}{warnings_part}"; }

    public static String getMsgCmdFtbStatusHeader() { return msgCmdFtbStatusHeader != null ? msgCmdFtbStatusHeader : "&6=== FTB Quests Integration Status ==="; }
    public static String getMsgCmdFtbStatusConfigEnabled() { return msgCmdFtbStatusConfigEnabled != null ? msgCmdFtbStatusConfigEnabled : "&7  Config Enabled: &f{value}"; }
    public static String getMsgCmdFtbStatusProviderRegistered() { return msgCmdFtbStatusProviderRegistered != null ? msgCmdFtbStatusProviderRegistered : "&7  Provider Registered: &f{value}"; }
    public static String getMsgCmdFtbStatusCompatActive() { return msgCmdFtbStatusCompatActive != null ? msgCmdFtbStatusCompatActive : "&7  Compat Active: &f{value}"; }
    public static String getMsgCmdFtbStatusPendingRechecks() { return msgCmdFtbStatusPendingRechecks != null ? msgCmdFtbStatusPendingRechecks : "&7  Pending Rechecks: &f{value}"; }
    public static String getMsgCmdFtbStatusRecheckBudget() { return msgCmdFtbStatusRecheckBudget != null ? msgCmdFtbStatusRecheckBudget : "&7  Recheck Budget: &f{value}/tick"; }
    public static String getMsgCmdFtbStatusPreviousProvider() { return msgCmdFtbStatusPreviousProvider != null ? msgCmdFtbStatusPreviousProvider : "&7  Previous Provider Stored: &f{value}"; }
    public static String getMsgCmdFtbStatusPlayerHeader() { return msgCmdFtbStatusPlayerHeader != null ? msgCmdFtbStatusPlayerHeader : "&b  --- Player: {player} ---"; }
    public static String getMsgCmdFtbStatusPlayerStages() { return msgCmdFtbStatusPlayerStages != null ? msgCmdFtbStatusPlayerStages : "&f  Player Stages: {value}"; }
    public static String getMsgCmdFtbStatusPlayerStageList() { return msgCmdFtbStatusPlayerStageList != null ? msgCmdFtbStatusPlayerStageList : "&7    {list}"; }
    public static String getMsgCmdFtbStatusRecheckInProgress() { return msgCmdFtbStatusRecheckInProgress != null ? msgCmdFtbStatusRecheckInProgress : "&7  Recheck In Progress: &f{value}"; }

    public static String getMsgTypeLabelBlock()       { return msgTypeLabelBlock != null ? msgTypeLabelBlock : "This block"; }
    public static String getMsgTypeLabelDimension()   { return msgTypeLabelDimension != null ? msgTypeLabelDimension : "This dimension"; }
    public static String getMsgTypeLabelEntity()      { return msgTypeLabelEntity != null ? msgTypeLabelEntity : "This entity"; }
    public static String getMsgTypeLabelRecipe()      { return msgTypeLabelRecipe != null ? msgTypeLabelRecipe : "This recipe"; }
    public static String getMsgTypeLabelInteraction() { return msgTypeLabelInteraction != null ? msgTypeLabelInteraction : "This interaction"; }
    public static String getMsgTypeLabelFluid()       { return msgTypeLabelFluid != null ? msgTypeLabelFluid : "This fluid"; }
    public static String getMsgTypeLabelEnchantment() { return msgTypeLabelEnchantment != null ? msgTypeLabelEnchantment : "This enchantment"; }
    public static String getMsgTypeLabelScreen()          { return msgTypeLabelScreen != null ? msgTypeLabelScreen : "This screen"; }
    public static String getMsgTypeLabelScreenItem()      { return msgTypeLabelScreenItem != null ? msgTypeLabelScreenItem : "This item's GUI"; }
    public static String getMsgTypeLabelStructureContents(){ return msgTypeLabelStructureContents != null ? msgTypeLabelStructureContents : "This structure's contents"; }
    public static String getMsgTypeLabelPetTaming()       { return msgTypeLabelPetTaming != null ? msgTypeLabelPetTaming : "Taming this pet"; }
    public static String getMsgTypeLabelPetBreeding()     { return msgTypeLabelPetBreeding != null ? msgTypeLabelPetBreeding : "Breeding this pet"; }
    public static String getMsgTypeLabelPetCommanding()   { return msgTypeLabelPetCommanding != null ? msgTypeLabelPetCommanding : "Commanding this pet"; }
}
