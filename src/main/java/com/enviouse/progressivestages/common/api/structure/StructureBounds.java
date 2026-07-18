package com.enviouse.progressivestages.common.api.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.Objects;

public record StructureBounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
    public StructureBounds {
        int lowX = Math.min(minX, maxX);
        int lowY = Math.min(minY, maxY);
        int lowZ = Math.min(minZ, maxZ);
        int highX = Math.max(minX, maxX);
        int highY = Math.max(minY, maxY);
        int highZ = Math.max(minZ, maxZ);
        minX = lowX;
        minY = lowY;
        minZ = lowZ;
        maxX = highX;
        maxY = highY;
        maxZ = highZ;
    }

    public static StructureBounds of(BoundingBox box) {
        Objects.requireNonNull(box, "box");
        return new StructureBounds(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ());
    }

    public boolean contains(BlockPos position) {
        return position != null && position.getX() >= minX && position.getX() <= maxX
            && position.getY() >= minY && position.getY() <= maxY
            && position.getZ() >= minZ && position.getZ() <= maxZ;
    }

    public StructureBounds expanded(int amount) {
        int padding = Math.max(0, amount);
        return new StructureBounds(minX - padding, minY - padding, minZ - padding,
            maxX + padding, maxY + padding, maxZ + padding);
    }

    public BoundingBox toBoundingBox() {
        return new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ);
    }
}
