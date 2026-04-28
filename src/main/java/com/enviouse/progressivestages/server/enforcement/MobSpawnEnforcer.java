package com.enviouse.progressivestages.server.enforcement;

import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.config.StageConfig;
import com.enviouse.progressivestages.common.lock.LockRegistry;
import com.enviouse.progressivestages.common.stage.StageManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ServerLevelAccessor;

import java.util.List;
import java.util.Set;

/**
 * v1.5: Gates mob spawns behind stages.
 *
 * <p>Unlike other enforcers which are player-scoped, spawns happen world-wide and
 * are not tied to a single player. Strategy:
 * <ol>
 *   <li>Look up if this entity type has a spawn-gating lock ({@code spawn_entities},
 *       {@code spawn_entity_tags}, or {@code spawn_entity_mods}).</li>
 *   <li>If so, find the nearest player within {@code mob_spawn_check_radius} blocks.</li>
 *   <li>If no player is within range → allow the spawn (no one is there to see it).</li>
 *   <li>If a player is in range → check if they (or their team) have the required stage.</li>
 *   <li>If they have the stage → allow. Otherwise → cancel spawn.</li>
 * </ol>
 *
 * <p>Creative-mode bypass: if {@code allow_creative_bypass} is on and the nearest
 * player is in creative, the spawn is allowed (so admins testing mob spawning can
 * see spawns even before unlocking the stage).
 */
public final class MobSpawnEnforcer {

    private MobSpawnEnforcer() {}

    /**
     * Determine whether a mob spawn should be cancelled.
     *
     * @param mob    the mob about to spawn
     * @param level  the level the mob is spawning into
     * @param x      spawn X
     * @param y      spawn Y
     * @param z      spawn Z
     * @return true if the spawn should be CANCELLED, false to allow
     */
    public static boolean shouldCancelSpawn(Mob mob, ServerLevelAccessor level, double x, double y, double z) {
        if (!StageConfig.isBlockMobSpawns()) {
            return false;
        }

        // v2.0: multi-stage spawn gate. Cancel if the nearest player misses ANY required stage.
        Set<StageId> gating = LockRegistry.getInstance().getRequiredStagesForSpawn(mob.getType());
        if (gating.isEmpty()) {
            return false; // Not gated
        }

        // Find the nearest player in the same level
        if (!(level.getLevel() instanceof net.minecraft.server.level.ServerLevel serverLevel)) {
            return false; // Worldgen or similar — allow
        }

        double radius = StageConfig.getMobSpawnCheckRadius();
        double radiusSq = radius * radius;

        List<ServerPlayer> players = serverLevel.players();
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

        if (nearest == null) {
            // No player nearby → allow; the spawn is irrelevant until someone arrives.
            return false;
        }

        // Creative bypass
        if (StageConfig.isAllowCreativeBypass() && nearest.isCreative()) {
            return false;
        }

        // Multi-stage: any missing → cancel.
        StageManager sm = StageManager.getInstance();
        for (StageId s : gating) {
            if (!sm.hasStage(nearest, s)) return true;
        }
        return false;
    }
}

