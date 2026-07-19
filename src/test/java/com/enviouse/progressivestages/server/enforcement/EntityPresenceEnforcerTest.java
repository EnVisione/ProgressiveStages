package com.enviouse.progressivestages.server.enforcement;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntityPresenceEnforcerTest {

    @Test
    void spawnIsCancelledOnlyWhenEveryRelevantPlayerIsDenied() {
        assertFalse(EntityPresenceEnforcer.allRelevantPlayersDenied(List.of()));
        assertTrue(EntityPresenceEnforcer.allRelevantPlayersDenied(List.of(true)));
        assertTrue(EntityPresenceEnforcer.allRelevantPlayersDenied(List.of(true, true, true)));
        assertFalse(EntityPresenceEnforcer.allRelevantPlayersDenied(List.of(true, false)));
        assertFalse(EntityPresenceEnforcer.allRelevantPlayersDenied(List.of(false, false)));
    }

    @Test
    void simulationDistanceUsesTheConfiguredChunkSquare() {
        assertTrue(EntityPresenceEnforcer.withinSimulationDistance(10, 10, 18, 2, 8));
        assertFalse(EntityPresenceEnforcer.withinSimulationDistance(10, 10, 19, 10, 8));
        assertEqualsBlocks(128, EntityPresenceEnforcer.simulationDistanceBlocks(8));
    }

    private static void assertEqualsBlocks(int expected, int actual) {
        if (expected != actual) throw new AssertionError("Expected " + expected + " blocks but got " + actual);
    }
}
