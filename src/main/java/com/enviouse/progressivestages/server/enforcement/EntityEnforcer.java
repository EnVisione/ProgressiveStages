package com.enviouse.progressivestages.server.enforcement;

import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.config.StageConfig;
import com.enviouse.progressivestages.common.lock.EnforcementCategory;
import com.enviouse.progressivestages.common.lock.ConditionalRule;
import com.enviouse.progressivestages.common.lock.LockRegistry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;

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
        if (player == null || entityType == null) return true;
        if (StageConfig.isAllowCreativeBypass() && player.isCreative()) return true;
        if (EntityPresenceEnforcer.isPresenceDenied(player, entityType)) return false;

        LockRegistry reg = LockRegistry.getInstance();
        if (!StageConfig.isBlockEntityAttack() && !reg.hasEnforcementOverrides()
                && !ConditionalLockEngine.hasRules(ConditionalRule.TargetType.ENTITY)) {
            return true;
        }

        // v2.3: per-stage override — enforce if ANY missing gating stage requires it (most-restrictive).
        java.util.Set<StageId> restrictions = reg.restrictionStagesForEntity(player, entityType, "attack");
        return restrictions.isEmpty() || !reg.isCategoryEnforced(
            restrictions, EnforcementCategory.ENTITY_ATTACK);
    }

    public static boolean canInteractEntity(ServerPlayer player, EntityType<?> entityType) {
        if (player == null || entityType == null) return true;
        if (StageConfig.isAllowCreativeBypass() && player.isCreative()) return true;
        if (EntityPresenceEnforcer.isPresenceDenied(player, entityType)) return false;
        LockRegistry registry = LockRegistry.getInstance();
        java.util.Set<StageId> restrictions = registry.restrictionStagesForEntity(player, entityType, "interact");
        return restrictions.isEmpty() || !registry.isCategoryEnforced(
            restrictions, EnforcementCategory.ENTITY_ATTACK);
    }

    public static boolean canMountEntity(ServerPlayer player, EntityType<?> entityType) {
        if (player == null || entityType == null) return true;
        if (StageConfig.isAllowCreativeBypass() && player.isCreative()) return true;
        if (EntityPresenceEnforcer.isPresenceDenied(player, entityType)) return false;
        LockRegistry registry = LockRegistry.getInstance();
        java.util.Set<StageId> restrictions = registry.restrictionStagesForEntity(player, entityType, "mount");
        return restrictions.isEmpty() || !registry.isCategoryEnforced(
            restrictions, EnforcementCategory.ENTITY_ATTACK);
    }

    /**
     * Check if an entity type is locked for a specific player.
     * v2.0: multi-stage aware.
     */
    public static boolean isEntityLockedForPlayer(ServerPlayer player, EntityType<?> entityType) {
        return !canAttackEntity(player, entityType);
    }

    /**
     * Notify player that entity interaction is locked.
     * v2.0: shows the primary missing stage.
     */
    public static void notifyLocked(ServerPlayer player, EntityType<?> entityType) {
        LockRegistry.getInstance().primaryRestrictingStageForEntity(player, entityType)
            .ifPresent(stage -> ItemEnforcer.notifyLockedWithCooldown(player, stage,
                com.enviouse.progressivestages.common.config.StageConfig.getMsgTypeLabelEntity()));
    }
}
