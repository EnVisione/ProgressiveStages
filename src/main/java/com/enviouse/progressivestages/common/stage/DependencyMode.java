package com.enviouse.progressivestages.common.stage;

import java.util.Locale;

/**
 * Controls how a stage interprets its declared dependency list.
 *
 * <p>The default remains {@link #ALL} for complete backwards compatibility. Pack authors can use
 * {@link #ANY} for alternate progression branches or {@link #AT_LEAST} for quorum-style unlocks.
 */
public enum DependencyMode {
    ALL("all"),
    ANY("any"),
    AT_LEAST("at_least");

    private final String configName;

    DependencyMode(String configName) {
        this.configName = configName;
    }

    public String configName() {
        return configName;
    }

    /** Parse friendly aliases while falling back to the legacy all-dependencies behaviour. */
    public static DependencyMode parse(String value) {
        if (value == null) return ALL;
        DependencyMode parsed = tryParse(value);
        return parsed != null ? parsed : ALL;
    }

    public static DependencyMode tryParse(String value) {
        if (value == null) return null;
        return switch (value.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_')) {
            case "all", "all_of", "every", "and" -> ALL;
            case "any", "one", "one_of", "any_of" -> ANY;
            case "at_least", "minimum", "count", "n_of" -> AT_LEAST;
            default -> null;
        };
    }

    /** Number of owned direct dependencies required for a definition with {@code total} entries. */
    public int requiredCount(int total, int configuredCount) {
        if (total <= 0) return 0;
        return switch (this) {
            case ALL -> total;
            case ANY -> 1;
            case AT_LEAST -> Math.min(total, Math.max(1, configuredCount));
        };
    }

    public boolean isSatisfied(int ownedCount, int total, int configuredCount) {
        return ownedCount >= requiredCount(total, configuredCount);
    }
}
