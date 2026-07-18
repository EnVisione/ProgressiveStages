package com.enviouse.progressivestages.client.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StageTreeLayoutTest {
    @Test
    void automaticLayoutGrowsUpwardAndCentersNarrowLayers() {
        assertEquals(new StageTreeLayout.Position(0, 162),
            StageTreeLayout.automaticPosition(0, 3, 0, 3, 3));
        assertEquals(new StageTreeLayout.Position(84, 108),
            StageTreeLayout.automaticPosition(1, 3, 0, 1, 3));
        assertEquals(new StageTreeLayout.Position(168, 0),
            StageTreeLayout.automaticPosition(3, 3, 2, 3, 3));
    }
}
