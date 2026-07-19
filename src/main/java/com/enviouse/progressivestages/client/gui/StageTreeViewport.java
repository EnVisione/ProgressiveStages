package com.enviouse.progressivestages.client.gui;

final class StageTreeViewport {
    static final double MIN_ZOOM = 0.65D;
    static final double MAX_ZOOM = 1.65D;
    private static final double ZOOM_STEP = 0.1D;

    private StageTreeViewport() {}

    static Camera zoomAt(Camera camera, double scrollY, double relativeX, double relativeY) {
        double previous = camera.zoom();
        double zoom = Math.clamp(previous + scrollY * ZOOM_STEP, MIN_ZOOM, MAX_ZOOM);
        if (zoom == previous) return camera;
        return new Camera(
            camera.panX() + relativeX / zoom - relativeX / previous,
            camera.panY() + relativeY / zoom - relativeY / previous,
            zoom);
    }

    static Camera drag(Camera camera, double dragX, double dragY) {
        return new Camera(
            camera.panX() + dragX / camera.zoom(),
            camera.panY() + dragY / camera.zoom(),
            camera.zoom());
    }

    static int nodeTopLeft(int viewportCenter, double nodeCenter, double pan, double zoom, int nodeSize) {
        return viewportCenter + (int) Math.round((nodeCenter + pan) * zoom) - nodeSize / 2;
    }

    static double fitZoom(
            int viewportWidth,
            int viewportHeight,
            double minimumCenterX,
            double maximumCenterX,
            double minimumCenterY,
            double maximumCenterY
    ) {
        double graphWidth = Math.max(44.0D, maximumCenterX - minimumCenterX + 44.0D);
        double graphHeight = Math.max(44.0D, maximumCenterY - minimumCenterY + 44.0D);
        return Math.clamp(Math.min(viewportWidth / graphWidth, viewportHeight / graphHeight), MIN_ZOOM, 1.0D);
    }

    static double clampPan(
            double pan,
            int viewportSize,
            double zoom,
            double minimumCenter,
            double maximumCenter
    ) {
        double visibleRadius = viewportSize / (2.0D * zoom) - 18.0D;
        return Math.clamp(pan, -maximumCenter - visibleRadius, -minimumCenter + visibleRadius);
    }

    record Camera(double panX, double panY, double zoom) {}
}
