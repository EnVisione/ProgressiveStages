package com.enviouse.progressivestages.server.enforcement;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ServerLevelAccessor;

/**
 * gates mob spawns behind stages.
 * spawns check every nonspectator player inside the server simulation distance.
 * cancellation occurs only when every relevant player is denied the entity.
 * mixed access allows the spawn and conceals it from denied players.
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
        return EntityPresenceEnforcer.shouldCancelSpawn(mob, level, x, y, z);
    }
}
