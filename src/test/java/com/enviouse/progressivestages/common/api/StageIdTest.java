package com.enviouse.progressivestages.common.api;

import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class StageIdTest {

    @Test
    void parsesNamespacedAndDefaultIdentifiers() {
        assertEquals("progressivestages:iron_age", StageId.parse("iron_age").toString());
        assertEquals("example:iron_age", StageId.parse("example:iron_age").toString());
        assertEquals("wizard:warlock", StageId.parse("  wizard:warlock   ").toString());
    }

    @Test
    void normalizationDoesNotDependOnServerLocale() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertEquals("progressivestages:iron_age", StageId.parse("IRON_AGE").toString());
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    void rejectsDotTraversalSegments() {
        assertNull(StageId.tryParse("../hidden"));
        assertNull(StageId.tryParse("path/./stage"));
        assertNull(StageId.tryParse("path/../stage"));
    }
}
