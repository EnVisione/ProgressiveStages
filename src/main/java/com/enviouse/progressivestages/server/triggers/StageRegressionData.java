package com.enviouse.progressivestages.server.triggers;

import com.enviouse.progressivestages.common.api.StageId;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * v2.4: persists the real-world grant time (epoch millis) of temporary stages, so a stage with a
 * {@code [stage].duration} expires after that much WALL-CLOCK time — counting down even while the
 * server is offline. Anchored to the overworld; saved in {@code world/data/progressivestages_regression.dat}.
 */
public class StageRegressionData extends SavedData {

    private static final String DATA_NAME = "progressivestages_regression";

    /** key = teamId|stageId  ->  grant epoch millis */
    private final Map<String, Long> grantTimes = new HashMap<>();

    public static StageRegressionData get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(
            new Factory<>(StageRegressionData::new, StageRegressionData::load), DATA_NAME);
    }

    private static String key(UUID teamId, StageId stageId) {
        return teamId.toString() + "|" + stageId.toString();
    }

    public void markGranted(UUID teamId, StageId stageId, long epochMillis) {
        grantTimes.put(key(teamId, stageId), epochMillis);
        setDirty();
    }

    public long getGrantTime(UUID teamId, StageId stageId) {
        Long v = grantTimes.get(key(teamId, stageId));
        return v == null ? -1L : v;
    }

    public void clear(UUID teamId, StageId stageId) {
        if (grantTimes.remove(key(teamId, stageId)) != null) setDirty();
    }

    public static StageRegressionData load(CompoundTag tag, HolderLookup.Provider provider) {
        StageRegressionData d = new StageRegressionData();
        CompoundTag times = tag.getCompound("grant_times");
        for (String k : times.getAllKeys()) d.grantTimes.put(k, times.getLong(k));
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        CompoundTag times = new CompoundTag();
        for (Map.Entry<String, Long> e : grantTimes.entrySet()) times.putLong(e.getKey(), e.getValue());
        tag.put("grant_times", times);
        return tag;
    }
}
