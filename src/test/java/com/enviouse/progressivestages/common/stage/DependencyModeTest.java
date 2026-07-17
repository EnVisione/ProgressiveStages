package com.enviouse.progressivestages.common.stage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DependencyModeTest {

    @Test
    void parsesFriendlyAliasesAndKeepsLegacyDefault() {
        assertEquals(DependencyMode.ALL, DependencyMode.parse(null));
        assertEquals(DependencyMode.ALL, DependencyMode.parse("unknown"));
        assertEquals(DependencyMode.ANY, DependencyMode.parse("one_of"));
        assertEquals(DependencyMode.AT_LEAST, DependencyMode.parse("at-least"));
    }

    @Test
    void computesClampedQuorums() {
        assertEquals(3, DependencyMode.ALL.requiredCount(3, 1));
        assertEquals(1, DependencyMode.ANY.requiredCount(3, 3));
        assertEquals(2, DependencyMode.AT_LEAST.requiredCount(3, 2));
        assertEquals(3, DependencyMode.AT_LEAST.requiredCount(3, 99));
        assertEquals(0, DependencyMode.AT_LEAST.requiredCount(0, 2));
        assertTrue(DependencyMode.AT_LEAST.isSatisfied(2, 3, 2));
        assertFalse(DependencyMode.AT_LEAST.isSatisfied(1, 3, 2));
    }
}
