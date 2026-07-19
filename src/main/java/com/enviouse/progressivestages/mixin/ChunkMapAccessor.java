package com.enviouse.progressivestages.mixin;

import com.enviouse.progressivestages.server.enforcement.ChunkMapTrackingAccess;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.server.level.ChunkMap;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ChunkMap.class)
public abstract class ChunkMapAccessor implements ChunkMapTrackingAccess {
    @Shadow @Final private Int2ObjectMap<?> entityMap;

    @Override
    public Int2ObjectMap<?> progressivestages$getEntityMap() {
        return entityMap;
    }
}
