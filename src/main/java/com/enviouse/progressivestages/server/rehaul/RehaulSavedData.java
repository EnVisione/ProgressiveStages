package com.enviouse.progressivestages.server.rehaul;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

public final class RehaulSavedData extends SavedData {

    private static final String DATA_NAME = "progressivestages_rehaul";
    private CompoundTag state = new CompoundTag();

    public static RehaulSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
            new Factory<>(RehaulSavedData::new, RehaulSavedData::load), DATA_NAME);
    }

    public static RehaulSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
        RehaulSavedData data = new RehaulSavedData();
        data.state = tag.getCompound("state").copy();
        return data;
    }

    public void capture(RehaulRuntime runtime) {
        state = RehaulStateCodec.encode(runtime);
        setDirty();
    }

    public void restore(RehaulRuntime runtime) {
        RehaulStateCodec.decode(state, runtime);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putInt("format", 1);
        tag.put("state", state.copy());
        return tag;
    }
}
