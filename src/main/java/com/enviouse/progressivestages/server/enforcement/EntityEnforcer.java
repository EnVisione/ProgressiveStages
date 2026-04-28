package com.enviouse.progressivestages.server.enforcement;

import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.config.StageConfig;
import com.enviouse.progressivestages.common.lock.LockRegistry;
import com.enviouse.progressivestages.common.stage.StageManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;

import java.util.Optional;

/**
 * Handles entity attack/interaction enforcement.
 * When an entity type is locked behind a stage, players cannot attack or interact with it.
 */
public class EntityEnforcer {

    /**
     * Check if a player can attack an entity of the given type.
     * @return true if allowed, false if blocked
     */
    public static boolean canAttackEntity(ServerPlayer player, EntityType<?> entityType) {
        if (!StageConfig.isBlockEntityAttack()) {
            return true;
        }

        // Creative bypass
        if (StageConfig.isAllowCreativeBypass() && player.isCreative()) {
            return true;
        }

        if (entityType == null) {
            return true;
        }

        return !isEntityLockedForPlayer(player, entityType);
    }

    /**
     * Check if an entity type is locked for a specific player.
     * v2.0: multi-stage aware.
     */
    public static boolean isEntityLockedForPlayer(ServerPlayer player, EntityType<?> entityType) {
        return LockRegistry.getInstance().isEntityBlockedFor(player, entityType);
    }

    /**
     * Notify player that entity interaction is locked.
     * v2.0: shows the primary missing stage.
     */
    public static void notifyLocked(ServerPlayer player, EntityType<?> entityType) {
        java.util.Set<StageId> gating = LockRegistry.getInstance().getRequiredStagesForEntity(entityType);
        if (gating.isEmpty()) return;
        for (StageId s : gating) {
            if (!StageManager.getInstance().hasStage(player, s)) {
                ItemEnforcer.notifyLockedWithCooldown(player, s, com.enviouse.progressivestages.common.config.StageConfig.getMsgTypeLabelEntity());
                return;
            }
        }
    }
}

