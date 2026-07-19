package com.enviouse.progressivestages.client.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StageTreeViewportTest {
    private static final double EPSILON = 0.000001D;

    @Test
    void wheelZoomKeepsTheGraphPointUnderThePointer() {
        StageTreeViewport.Camera before = new StageTreeViewport.Camera(14.0D, -20.0D, 1.0D);
        double relativeX = 120.0D;
        double relativeY = -45.0D;
        double graphX = relativeX / before.zoom() - before.panX();
        double graphY = relativeY / before.zoom() - before.panY();

        StageTreeViewport.Camera after = StageTreeViewport.zoomAt(before, 2.0D, relativeX, relativeY);

        assertEquals(graphX, relativeX / after.zoom() - after.panX(), EPSILON);
        assertEquals(graphY, relativeY / after.zoom() - after.panY(), EPSILON);
        assertEquals(1.2D, after.zoom(), EPSILON);
    }

    @Test
    void wheelZoomStopsAtTheSameLimitsAsProgressiveSkills() {
        StageTreeViewport.Camera camera = new StageTreeViewport.Camera(0.0D, 0.0D, 1.0D);

        assertEquals(StageTreeViewport.MAX_ZOOM,
            StageTreeViewport.zoomAt(camera, 100.0D, 0.0D, 0.0D).zoom(), EPSILON);
        assertEquals(StageTreeViewport.MIN_ZOOM,
            StageTreeViewport.zoomAt(camera, -100.0D, 0.0D, 0.0D).zoom(), EPSILON);
    }

    @Test
    void draggingMovesTheCameraByScreenPixelsAtEveryZoom() {
        StageTreeViewport.Camera camera = new StageTreeViewport.Camera(5.0D, 8.0D, 0.8D);

        StageTreeViewport.Camera dragged = StageTreeViewport.drag(camera, 16.0D, -8.0D);

        assertEquals(25.0D, dragged.panX(), EPSILON);
        assertEquals(-2.0D, dragged.panY(), EPSILON);
        assertEquals(0.8D, dragged.zoom(), EPSILON);
    }

    @Test
    void nodeIconsKeepTheirReadableSizeWhileTheirPositionsScale() {
        int center = 200;
        int nodeSize = 26;

        int normal = StageTreeViewport.nodeTopLeft(center, 84.0D, 0.0D, 1.0D, nodeSize);
        int zoomed = StageTreeViewport.nodeTopLeft(center, 84.0D, 0.0D, 1.5D, nodeSize);

        assertEquals(271, normal);
        assertEquals(313, zoomed);
    }

    @Test
    void fitZoomUsesTheCompleteGraphAndNeverStartsAboveOneHundredPercent() {
        assertEquals(1.0D,
            StageTreeViewport.fitZoom(500, 300, 0.0D, 84.0D, 0.0D, 54.0D), EPSILON);
        assertEquals(StageTreeViewport.MIN_ZOOM,
            StageTreeViewport.fitZoom(240, 140, 0.0D, 1000.0D, 0.0D, 700.0D), EPSILON);
    }
}
