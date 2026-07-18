package com.enviouse.progressivestages.common.api.structure;

import java.util.Locale;

public enum StructureLeaveOutcome {
    INCOMPLETE,
    COMPLETED,
    CANCELLED,
    DEATH,
    TELEPORT,
    DIMENSION_CHANGE,
    DISCONNECT,
    RECOVERY;

    public static StructureLeaveOutcome parse(String value) {
        return valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
