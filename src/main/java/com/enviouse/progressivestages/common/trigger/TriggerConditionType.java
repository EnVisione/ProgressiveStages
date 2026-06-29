package com.enviouse.progressivestages.common.trigger;

import java.util.Locale;

/**
 * The kind of a single {@link TriggerCondition}.
 *
 * <p>Most condition types are <b>counters</b> backed by Minecraft's vanilla statistics
 * system ({@code ServerPlayer.getStats()}). Because vanilla already tracks and persists
 * those counters per-player, counter conditions are automatically <i>retroactive</i> (a
 * player who already killed 10 endermen before the trigger existed is credited instantly)
 * and survive server restarts for free.
 *
 * <p>A few types are <b>one-shot</b> world-state facts that vanilla does not persist as a
 * monotonic counter ("has this player ever entered the Nether / visited a desert?"). Those
 * are persisted by {@code TriggerPersistence}. {@link #ADVANCEMENT} is one-shot but is
 * read live from the player's advancement progress (vanilla-persisted). {@link #LEVEL},
 * {@link #XP} and {@link #HAS_ITEM} are momentary <b>state</b> checks read from the live
 * player each evaluation.
 */
public enum TriggerConditionType {
    KILL,        // entity / entity-tag kills      -> Stats.ENTITY_KILLED
    MINE,        // blocks mined                    -> Stats.BLOCK_MINED
    CRAFT,       // items crafted                   -> Stats.ITEM_CRAFTED
    PICKUP,      // items picked up (cumulative)    -> Stats.ITEM_PICKED_UP
    USE,         // items used/right-click          -> Stats.ITEM_USED
    DROP,        // items dropped                   -> Stats.ITEM_DROPPED
    BREAK_ITEM,  // tools/items broken              -> Stats.ITEM_BROKEN
    DISTANCE,    // distance travelled (blocks)     -> Stats.CUSTOM (*_ONE_CM)
    STAT,        // any raw vanilla custom stat     -> Stats.CUSTOM
    PLAY_TIME,   // minutes played                  -> Stats.CUSTOM play_time
    LEVEL,       // experience level (state)
    XP,          // total experience points (state)
    ADVANCEMENT, // advancement earned (vanilla-persisted)
    DIMENSION,   // dimension entered (one-shot, persisted)
    BIOME,       // biome visited (one-shot, persisted)
    HAS_ITEM,    // count currently held in inventory (state)
    // v2.4 additions
    EFFECT,      // currently has a status effect (state)
    BREED,       // animals bred (global stat, or per-species via event counter)
    DAY_COUNT,   // reached world day N (state)
    WORLD_TIME,  // current time-of-day tick within the day 0..23999 (state)
    WEATHER,     // experienced weather rain/thunder/clear (one-shot, persisted)
    ENTER_STRUCTURE, // entered a structure (one-shot, persisted)
    TAME,        // animals tamed (generic counter)
    KILL_WITH;   // kill entity X while holding item Y (generic counter)

    /**
     * Resolve a TOML {@code type = "..."} value (with generous aliases) to a type, or
     * {@code null} if unrecognized.
     */
    public static TriggerConditionType fromString(String s) {
        if (s == null) return null;
        return switch (s.trim().toLowerCase(Locale.ROOT)) {
            case "kill", "kills", "kill_entity", "kill_mob", "mob_kill", "boss", "boss_kill" -> KILL;
            case "mine", "mine_block", "break_block", "blocks_mined", "dig" -> MINE;
            case "craft", "craft_item", "crafted", "item_crafted" -> CRAFT;
            case "pickup", "pick_up", "item_pickup", "picked_up", "item_picked_up" -> PICKUP;
            case "use", "use_item", "item_used", "item_use" -> USE;
            case "drop", "drop_item", "item_dropped", "dropped" -> DROP;
            case "break_item", "item_broken", "tool_broken", "broke_item" -> BREAK_ITEM;
            case "distance", "travel", "travelled", "traveled", "distance_traveled", "distance_travelled" -> DISTANCE;
            case "stat", "statistic", "custom_stat" -> STAT;
            case "play_time", "playtime", "time_played", "time" -> PLAY_TIME;
            case "level", "experience_level", "xp_level" -> LEVEL;
            case "xp", "experience", "total_xp", "experience_points" -> XP;
            case "advancement", "advancements", "achievement" -> ADVANCEMENT;
            case "dimension", "dimensions", "enter_dimension", "dim" -> DIMENSION;
            case "biome", "biomes", "visit_biome" -> BIOME;
            case "has_item", "have_item", "possess", "hold_item", "holding", "inventory" -> HAS_ITEM;
            case "effect", "status_effect", "has_effect", "potion" -> EFFECT;
            case "breed", "bred", "animals_bred" -> BREED;
            case "day", "day_count", "days", "world_day", "reach_day" -> DAY_COUNT;
            case "world_time", "time_of_day", "daytime", "clock" -> WORLD_TIME;
            case "weather", "survive_weather" -> WEATHER;
            case "enter_structure", "structure", "visit_structure" -> ENTER_STRUCTURE;
            case "tame", "tamed", "tame_animal" -> TAME;
            case "kill_with", "kill_using", "killwith" -> KILL_WITH;
            default -> null;
        };
    }

    /** Counter conditions read a monotonic statistic/counter (retroactive, restart-proof). */
    public boolean isCounter() {
        return switch (this) {
            case KILL, MINE, CRAFT, PICKUP, USE, DROP, BREAK_ITEM, DISTANCE, STAT, PLAY_TIME,
                 BREED, TAME, KILL_WITH -> true;
            default -> false;
        };
    }

    /** One-shot world-state facts we persist ourselves (vanilla doesn't track "ever visited"). */
    public boolean isPersistedOneShot() {
        return this == DIMENSION || this == BIOME || this == WEATHER || this == ENTER_STRUCTURE;
    }

    /** Types that require a {@code target} (entity/block/item/id); the rest derive their own. */
    public boolean requiresTarget() {
        return switch (this) {
            // DISTANCE defaults to "all"; BREED/TAME take an OPTIONAL species target.
            case PLAY_TIME, LEVEL, XP, DISTANCE, DAY_COUNT, WORLD_TIME, BREED, TAME -> false;
            default -> true;
        };
    }
}
