package com.enviouse.progressivestages.common.config;

import java.util.Locale;

public enum StageSlotPolicy {
    DENY,
    REPLACE_OLDEST,
    REPLACE_LOWEST_PRIORITY,
    REPLACE_ALL;

    public String configName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static StageSlotPolicy parse(String value) {
        if (value == null || value.isBlank()) return DENY;
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "deny", "block" -> DENY;
            case "replace_oldest", "oldest" -> REPLACE_OLDEST;
            case "replace_lowest_priority", "lowest_priority" -> REPLACE_LOWEST_PRIORITY;
            case "replace_all", "all" -> REPLACE_ALL;
            default -> throw new IllegalArgumentException("Invalid stage slot policy. " + value);
        };
    }
}
