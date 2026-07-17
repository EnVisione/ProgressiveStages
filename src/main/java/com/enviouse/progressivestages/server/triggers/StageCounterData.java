package com.enviouse.progressivestages.server.triggers;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * v2.4: generic per-player counters for trigger conditions that vanilla statistics don't track
 * (e.g. {@code tame}, {@code kill_with}). Incremented from events, read by the evaluator. Persisted
 * in {@code world/data/progressivestages_counters.dat}; survives restarts.
 */
public class StageCounterData extends SavedData {

    private static final String DATA_NAME = "progressivestages_counters";

    /** key = playerUUID|counterKey  ->  count */
    private final Map<String, Long> counters = new HashMap<>();

    public static StageCounterData get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(
            new Factory<>(StageCounterData::new, StageCounterData::load), DATA_NAME);
    }

    private static String key(UUID player, String counterKey) {
        return player.toString() + "|" + counterKey;
    }

    public void increment(UUID player, String counterKey, long amount) {
        if (counterKey == null || counterKey.isBlank() || amount == 0) return;
        counters.merge(key(player, counterKey), amount, Long::sum);
        setDirty();
    }

    public void set(UUID player, String counterKey, long value) {
        if (counterKey == null || counterKey.isBlank()) return;
        String key = key(player, counterKey);
        if (value == 0) counters.remove(key);
        else counters.put(key, value);
        setDirty();
    }

    public void reset(UUID player, String counterKey) {
        if (counterKey != null && !counterKey.isBlank() && counters.remove(key(player, counterKey)) != null) {
            setDirty();
        }
    }

    public void resetPlayer(UUID player) {
        String prefix = player + "|";
        if (counters.keySet().removeIf(k -> k.startsWith(prefix))) setDirty();
    }

    public long get(UUID player, String counterKey) {
        Long v = counters.get(key(player, counterKey));
        return v == null ? 0L : v;
    }

    public static StageCounterData load(CompoundTag tag, HolderLookup.Provider provider) {
        StageCounterData d = new StageCounterData();
        CompoundTag c = tag.getCompound("counters");
        for (String k : c.getAllKeys()) d.counters.put(k, c.getLong(k));
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        CompoundTag c = new CompoundTag();
        for (Map.Entry<String, Long> e : counters.entrySet()) c.putLong(e.getKey(), e.getValue());
        tag.put("counters", c);
        return tag;
    }
}
