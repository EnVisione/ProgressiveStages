package com.enviouse.progressivestages.server.enforcement;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashSet;
import java.util.Set;

/**
 * v2.0.1: per-dimension persistent set of player-placed block positions. The ore-spoof
 * enforcer skips any position recorded here so player decorations are never replaced.
 *
 * <p>Storage: BlockPos.asLong() packed into a LongArrayTag for cheap NBT round-trip.
 * Lookups are O(1) on a Long2BooleanMap-equivalent (HashSet&lt;Long&gt;).
 */
public final class PlayerPlacedBlocksData extends SavedData {

    private static final String DATA_NAME = "progressivestages_player_placed";
    private static final String KEY_POSITIONS = "Positions";

    private final Set<Long> positions = new HashSet<>();

    public static PlayerPlacedBlocksData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
            new Factory<>(PlayerPlacedBlocksData::new, PlayerPlacedBlocksData::load, null),
            DATA_NAME);
    }

    public static PlayerPlacedBlocksData load(CompoundTag tag, HolderLookup.Provider lookup) {
        PlayerPlacedBlocksData data = new PlayerPlacedBlocksData();
        if (tag.contains(KEY_POSITIONS)) {
            for (long packed : tag.getLongArray(KEY_POSITIONS)) {
                data.positions.add(packed);
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider lookup) {
        long[] arr = new long[positions.size()];
        int i = 0;
        for (Long p : positions) arr[i++] = p;
        tag.put(KEY_POSITIONS, new LongArrayTag(arr));
        return tag;
    }

    public boolean isPlayerPlaced(BlockPos pos) {
        return positions.contains(pos.asLong());
    }

    public void markPlayerPlaced(BlockPos pos) {
        if (positions.add(pos.asLong())) setDirty();
    }

    /** Remove tracking when a player-placed block is broken so the position can be re-spoofed later. */
    public void clearPlayerPlaced(BlockPos pos) {
        if (positions.remove(pos.asLong())) setDirty();
    }

    public int size() {
        return positions.size();
    }
}
