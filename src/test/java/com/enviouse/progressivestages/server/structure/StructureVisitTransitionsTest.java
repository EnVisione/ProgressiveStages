package com.enviouse.progressivestages.server.structure;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StructureVisitTransitionsTest {

    @Test
    void outsideToInsideEntersOnceAndRemainingInsideDoesNothing() {
        assertEquals(StructureVisitTransitions.Type.ENTER,
            StructureVisitTransitions.decide(false, true, true, -1L, 100L, 10L).type());
        assertEquals(StructureVisitTransitions.Type.NONE,
            StructureVisitTransitions.decide(true, true, true, -1L, 101L, 10L).type());
    }

    @Test
    void exitRequiresDebounceAndReentryCancelsIt() {
        var begin = StructureVisitTransitions.decide(true, false, false, -1L, 100L, 10L);
        assertEquals(StructureVisitTransitions.Type.BEGIN_EXIT, begin.type());
        assertEquals(110L, begin.exitDeadline());
        assertEquals(StructureVisitTransitions.Type.NONE,
            StructureVisitTransitions.decide(true, false, false, begin.exitDeadline(), 109L, 10L).type());
        assertEquals(StructureVisitTransitions.Type.CANCEL_EXIT,
            StructureVisitTransitions.decide(true, true, true, begin.exitDeadline(), 109L, 10L).type());
        assertEquals(StructureVisitTransitions.Type.LEAVE,
            StructureVisitTransitions.decide(true, false, false, begin.exitDeadline(), 110L, 10L).type());
    }

    @Test
    void expandedBoundaryDoesNotBeginAnExit() {
        assertEquals(StructureVisitTransitions.Type.NONE,
            StructureVisitTransitions.decide(true, false, true, -1L, 100L, 10L).type());
    }
}
