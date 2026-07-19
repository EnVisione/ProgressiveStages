package com.enviouse.progressivestages.common.api.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;

import java.util.Objects;

public record StructureInstanceKey(ResourceKey<Level> dimension, ResourceLocation structureId,
                                   BlockPos startPosition) {
    public StructureInstanceKey {
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(structureId, "structureId");
        Objects.requireNonNull(startPosition, "startPosition");
        startPosition = startPosition.immutable();
    }

    public static StructureInstanceKey fromStartChunk(ResourceKey<Level> dimension,
                                                       ResourceLocation structureId,
                                                       ChunkPos startChunk) {
        Objects.requireNonNull(startChunk, "startChunk");
        return new StructureInstanceKey(dimension, structureId, startChunk.getWorldPosition());
    }
}
