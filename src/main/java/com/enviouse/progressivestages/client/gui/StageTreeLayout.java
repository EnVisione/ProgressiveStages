package com.enviouse.progressivestages.client.gui;

final class StageTreeLayout {
    static final int LANE_X = 84;
    static final int LAYER_Y = 54;

    private StageTreeLayout() {}

    static Position automaticPosition(int depth, int maxDepth, int lane, int layerSize, int widestLayer) {
        int x = (widestLayer - layerSize) * LANE_X / 2 + lane * LANE_X;
        int y = (maxDepth - depth) * LAYER_Y;
        return new Position(x, y);
    }

    record Position(int x, int y) {}
}
