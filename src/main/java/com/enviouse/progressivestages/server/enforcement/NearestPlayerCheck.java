package com.enviouse.progressivestages.server.enforcement;

import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.config.StageConfig;
import com.enviouse.progressivestages.common.stage.StageManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * Shared helper for world-event enforcers that gate by "does the nearest player have this stage?".
 * Used by mob spawns, mob replacement, loot, crops, regions, and structures.
 */
public final class NearestPlayerCheck {

    private NearestPlayerCheck() {}

    /**
     * Find the nearest {@link ServerPlayer} to a world position within {@code radius} blocks.
     * Returns null if none are in range.
     */
    public static ServerPlayer findNearest(ServerLevel level, double x, double y, double z, double radius) {
        if (level == null) return null;
        double radiusSq = radius * radius;
        List<ServerPlayer> players = level.players();
        ServerPlayer nearest = null;
        double nearestDistSq = Double.MAX_VALUE;
        for (ServerPlayer p : players) {
            double dx = p.getX() - x;
            double dy = p.getY() - y;
            double dz = p.getZ() - z;
            double distSq = dx * dx + dy * dy + dz * dz;
            if (distSq <= radiusSq && distSq < nearestDistSq) {
                nearest = p;
                nearestDistSq = distSq;
            }
        }
        return nearest;
    }

    /**
     * Return {@code true} if the nearest player within {@code radius} is missing {@code stage}.
     * Honors {@link StageConfig#isAllowCreativeBypass()} — a creative player counts as having
     * the stage. If no player is in range, returns {@code false} (there's no one to gate for).
     */
    public static boolean nearestPlayerLacks(ServerLevel level, double x, double y, double z,
                                             double radius, StageId stage) {
        ServerPlayer nearest = findNearest(level, x, y, z, radius);
        if (nearest == null) return false;
        if (StageConfig.isAllowCreativeBypass() && nearest.isCreative()) return false;
        return !StageManager.getInstance().hasStage(nearest, stage);
    }

    /**
     * v2.0 multi-stage variant: returns {@code true} when the nearest player within
     * {@code radius} is missing AT LEAST ONE of the gating stages. Honors creative bypass.
     * No player in range → returns {@code false}. Empty gating set → returns {@code false}
     * (nothing to gate).
     */
    public static boolean nearestPlayerLacksAll(ServerLevel level, double x, double y, double z,
                                                double radius, java.util.Set<StageId> gating) {
        if (gating == null || gating.isEmpty()) return false;
        ServerPlayer nearest = findNearest(level, x, y, z, radius);
        if (nearest == null) return false;
        if (StageConfig.isAllowCreativeBypass() && nearest.isCreative()) return false;
        StageManager sm = StageManager.getInstance();
        for (StageId s : gating) if (!sm.hasStage(nearest, s)) return true;
        return false;
    }
}
