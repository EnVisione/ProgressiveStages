package com.enviouse.progressivestages.client.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StageTreeScreenControlAlignmentTest {

    @Test
    void centersPixelIconsInsideTheirControls() {
        assertEquals(23, StageTreeScreen.centeredControlIconX(20, 13, 7));
        assertEquals(12, StageTreeScreen.centeredControlIconY(10, 13, 9));
        assertEquals(18, StageTreeScreen.centeredControlIconY(15, 13, 7));
    }
}
