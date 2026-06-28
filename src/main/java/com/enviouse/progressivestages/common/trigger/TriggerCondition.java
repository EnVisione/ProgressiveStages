package com.enviouse.progressivestages.common.trigger;

import java.util.Locale;
import java.util.Objects;

/**
 * One condition inside a {@link TriggerRule} — "kill 10 endermen", "travel 100000 blocks",
 * "earn this advancement", etc.
 *
 * <p>The {@link #target} is the type-specific subject (entity/block/item/advancement/
 * dimension/biome id, a distance movement keyword, or a raw stat id). It may be a tag,
 * written either {@code #ns:path} or {@code tag:ns:path}. {@link #count} is the threshold:
 * the condition is satisfied once the player's current progress is {@code >= count}.
 *
 * <p>Units by type: kill/mine/craft/pickup/use/drop/break_item/has_item = item or mob count;
 * distance = blocks; play_time = minutes; stat = raw vanilla stat units; level = experience
 * levels; xp = total experience points; advancement/dimension/biome use count = 1.
 */
public final class TriggerCondition {

    private final TriggerConditionType type;
    private final String target; // "" when the type takes no target (play_time/level/xp)
    private final long count;     // threshold, clamped to >= 1

    public TriggerCondition(TriggerConditionType type, String target, long count) {
        this.type = Objects.requireNonNull(type, "type");
        this.target = target == null ? "" : target.trim();
        this.count = Math.max(1L, count);
    }

    public TriggerConditionType type() { return type; }
    public String target()             { return target; }
    public long count()                { return count; }

    /** True if {@link #target} names a tag rather than a single id. */
    public boolean targetIsTag() {
        return target.startsWith("#") || target.startsWith("tag:");
    }

    /** The target with any {@code #} / {@code tag:} / {@code id:} prefix stripped. */
    public String targetBody() {
        if (target.startsWith("#"))    return target.substring(1);
        if (target.startsWith("tag:")) return target.substring(4);
        if (target.startsWith("id:"))  return target.substring(3);
        return target;
    }

    /**
     * Stable key used to persist one-shot progress (dimension/biome). Count-independent so
     * a pack author can raise the threshold without wiping "already visited" progress.
     */
    public String canonicalKey() {
        return type.name().toLowerCase(Locale.ROOT) + "|" + target;
    }

    @Override
    public String toString() {
        return type.name().toLowerCase(Locale.ROOT)
            + (target.isEmpty() ? "" : "(" + target + ")")
            + " x" + count;
    }
}
