package com.enviouse.progressivestages.mixin;

import net.minecraft.world.level.chunk.LevelChunkSection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * v2.0.2: expose private {@code nonEmptyBlockCount} so the chunk-rewriter
 * can re-serialize sections with the correct fast-path header.
 */
@Mixin(LevelChunkSection.class)
public interface LevelChunkSectionAccessor {
    @Accessor("nonEmptyBlockCount")
    short progressivestages$getNonEmptyBlockCount();
}
