package com.enviouse.progressivestages.common.trigger;

import java.util.Locale;

/**
 * How the conditions of a single {@link TriggerRule} combine.
 *
 * <ul>
 *   <li>{@link #ALL_OF} — every condition must be satisfied (AND). This is the default.</li>
 *   <li>{@link #ANY_OF} — at least one condition must be satisfied (OR).</li>
 * </ul>
 */
public enum TriggerMode {
    ALL_OF,
    ANY_OF;

    /** Parse a TOML {@code mode = "..."} value, defaulting to {@link #ALL_OF}. */
    public static TriggerMode fromString(String s) {
        if (s == null) return ALL_OF;
        return switch (s.trim().toLowerCase(Locale.ROOT)) {
            case "any_of", "anyof", "any", "or" -> ANY_OF;
            default -> ALL_OF;
        };
    }
}
