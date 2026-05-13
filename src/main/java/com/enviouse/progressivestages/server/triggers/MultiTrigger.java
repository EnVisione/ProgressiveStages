package com.enviouse.progressivestages.server.triggers;

import com.enviouse.progressivestages.common.api.StageId;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * One {@code [[multi]]} requirement parsed from triggers.toml.
 *
 * <p>A multi-trigger gates a single stage behind a SET of sub-conditions. The stage
 * is only granted when:
 * <ul>
 *   <li>{@link Mode#ALL_OF} — every sub-trigger in {@link #subTriggers()} has fired
 *       at least once for the player.</li>
 *   <li>{@link Mode#ANY_OF} — at least one sub-trigger has fired.</li>
 * </ul>
 *
 * <p>Per-player progress is persisted by {@link TriggerPersistence} under the
 * synthetic type {@code "multi"} with a key of {@code "<requirementId>:<subKey>"}.
 * Each sub-trigger has a stable string key (e.g. {@code "item:minecraft:diamond"})
 * that is used both for matching incoming events and for persisting which sub-keys
 * the player has already satisfied.
 *
 * <p>The {@code requirementId} is either supplied by the user via {@code id = "..."}
 * or auto-derived from the canonical content (stage + mode + sorted sub-keys), so
 * reordering entries in the file does not lose per-player progress.
 */
public final class MultiTrigger {

    public enum Mode { ALL_OF, ANY_OF }

    /** The four supported sub-trigger surfaces. Matches the existing single-trigger handlers. */
    public enum SubType {
        ITEM,         // pickup or already-in-inventory at login
        ADVANCEMENT,  // earned at runtime or already earned at login
        DIMENSION,    // entered (one-shot) or currently in at login
        BOSS;         // killed (one-shot, cannot be retroactively detected)

        /** Lower-case name used in TOML prefixes ({@code item:}, {@code advancement:}, ...) */
        public String prefix() { return name().toLowerCase(); }

        public static SubType parse(String prefix) {
            if (prefix == null) return null;
            String p = prefix.trim().toLowerCase();
            return switch (p) {
                case "item", "items" -> ITEM;
                case "advancement", "advancements" -> ADVANCEMENT;
                case "dimension", "dimensions" -> DIMENSION;
                case "boss", "bosses" -> BOSS;
                default -> null;
            };
        }
    }

    /** A single requirement entry inside a multi-trigger: "satisfy this sub-condition once". */
    public static final class SubTrigger {
        private final SubType type;
        private final ResourceLocation key;
        private final String raw;

        public SubTrigger(SubType type, ResourceLocation key, String raw) {
            this.type = type;
            this.key = key;
            this.raw = raw;
        }

        public SubType type()         { return type; }
        public ResourceLocation key() { return key; }
        public String raw()           { return raw; }

        /** Canonical "type:namespace:path" string used for matching and persistence. */
        public String canonicalKey() { return type.prefix() + ":" + key.toString(); }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof SubTrigger s)) return false;
            return type == s.type && Objects.equals(key, s.key);
        }

        @Override
        public int hashCode() { return Objects.hash(type, key); }

        @Override
        public String toString() { return canonicalKey(); }
    }

    private final String requirementId;
    private final StageId stageId;
    private final Mode mode;
    private final List<SubTrigger> subTriggers;
    private final String description;

    public MultiTrigger(String requirementId, StageId stageId, Mode mode,
                        List<SubTrigger> subTriggers, String description) {
        this.requirementId = Objects.requireNonNull(requirementId);
        this.stageId = Objects.requireNonNull(stageId);
        this.mode = Objects.requireNonNull(mode);
        this.subTriggers = Collections.unmodifiableList(new ArrayList<>(subTriggers));
        this.description = description == null ? "" : description;
    }

    public String requirementId()        { return requirementId; }
    public StageId stageId()             { return stageId; }
    public Mode mode()                   { return mode; }
    public List<SubTrigger> subTriggers() { return subTriggers; }
    public String description()          { return description; }

    @Override
    public String toString() {
        return "MultiTrigger{" + requirementId + " -> " + stageId
            + " " + mode + " " + subTriggers + "}";
    }
}
