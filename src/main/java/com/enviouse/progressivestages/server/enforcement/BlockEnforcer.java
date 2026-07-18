package com.enviouse.progressivestages.server.enforcement;

import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.config.StageConfig;
import com.enviouse.progressivestages.common.lock.EnforcementCategory;
import com.enviouse.progressivestages.common.lock.ConditionalRule;
import com.enviouse.progressivestages.common.lock.LockRegistry;
import com.enviouse.progressivestages.compat.visualworkbench.VisualWorkbenchShim;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Optional;

/**
 * Handles block placement and interaction enforcement
 */
public class BlockEnforcer {

    /**
     * Check if a player can place a block
     * @return true if allowed, false if blocked
     */
    public static boolean canPlaceBlock(ServerPlayer player, Block block) {
        if (block == null) return true;
        return canPlaceBlock(player, block.defaultBlockState());
    }

    /**
     * Check if a player can interact with (right-click) a block
     * @return true if allowed, false if blocked
     */
    public static boolean canInteractWithBlock(ServerPlayer player, Block block) {
        if (block == null) return true;
        return canInteractWithBlock(player, block.defaultBlockState());
    }

    /**
     * Check if a block is locked for a player.
     * v2.0: multi-stage aware — blocked when ANY gating stage is missing.
     */
    public static boolean isBlockLockedForPlayer(ServerPlayer player, Block block) {
        return LockRegistry.getInstance().isBlockBlockedFor(player, block);
    }

    /**
     * State-aware variant. After the normal Block check, if not blocked, asks the
     * Visual Workbench shim whether this state is a VW-replaced workbench and
     * rechecks against the underlying vanilla block.
     */
    public static boolean isBlockLockedForPlayer(ServerPlayer player, BlockState state) {
        if (state == null) return false;
        Block block = state.getBlock();
        if (isBlockLockedForPlayer(player, block)) {
            return true;
        }
        BlockState vanilla = VisualWorkbenchShim.resolveVanillaEquivalent(state);
        if (vanilla != null && vanilla.getBlock() != block) {
            return isBlockLockedForPlayer(player, vanilla.getBlock());
        }
        return false;
    }

    /**
     * State-aware interaction check (covers Visual Workbench replacements).
     */
    public static boolean canInteractWithBlock(ServerPlayer player, BlockState state) {
        LockRegistry reg = LockRegistry.getInstance();
        if (!StageConfig.isBlockBlockInteraction() && !reg.hasEnforcementOverrides()
                && !ConditionalLockEngine.hasRules(ConditionalRule.TargetType.BLOCK)) return true;
        if (StageConfig.isAllowCreativeBypass() && player.isCreative()) return true;
        if (player != null && player.isSpectator()) return true;
        if (state == null) return true;
        if (!isBlockLockedForPlayer(player, state)) return true;
        // v2.3: per-stage override across ALL missing gating stages (most-restrictive wins).
        java.util.Set<StageId> missing = blockRestrictions(player, state);
        return missing.isEmpty() || !reg.isCategoryEnforced(missing, EnforcementCategory.BLOCK_INTERACTION);
    }

    private static java.util.Set<StageId> blockRestrictions(ServerPlayer player, BlockState state) {
        LockRegistry reg = LockRegistry.getInstance();
        java.util.Set<StageId> restrictions = new java.util.LinkedHashSet<>(
            reg.missingStagesForBlock(player, state.getBlock()));
        BlockState vanilla = VisualWorkbenchShim.resolveVanillaEquivalent(state);
        if (vanilla != null && vanilla.getBlock() != state.getBlock()) {
            restrictions.addAll(reg.missingStagesForBlock(player, vanilla.getBlock()));
        }
        return java.util.Set.copyOf(restrictions);
    }

    /**
     * Phase D: state-aware placement check. Tries the block's own id first, then the
     * Visual Workbench vanilla equivalent. Mirrors the interact path.
     */
    public static boolean canPlaceBlock(ServerPlayer player, BlockState state) {
        LockRegistry reg = LockRegistry.getInstance();
        if (!StageConfig.isBlockBlockPlacement() && !reg.hasEnforcementOverrides()
                && !ConditionalLockEngine.hasRules(ConditionalRule.TargetType.BLOCK)) return true;
        if (StageConfig.isAllowCreativeBypass() && player.isCreative()) return true;
        if (player != null && player.isSpectator()) return true;
        if (state == null) return true;
        if (!isBlockLockedForPlayer(player, state)) return true;
        java.util.Set<StageId> missing = blockRestrictions(player, state);
        return missing.isEmpty() || !reg.isCategoryEnforced(missing, EnforcementCategory.BLOCK_PLACEMENT);
    }

    /**
     * State-aware notification helper. Falls back to VW-resolved block id when applicable.
     * v2.0: shows the first gating stage the player is missing.
     */
    public static void notifyInteractionLocked(ServerPlayer player, BlockState state) {
        if (state == null) return;
        Block block = state.getBlock();
        Optional<StageId> requiredStage = LockRegistry.getInstance().primaryRestrictingStageForBlock(player, block);
        if (requiredStage.isEmpty()) {
            BlockState vanilla = VisualWorkbenchShim.resolveVanillaEquivalent(state);
            if (vanilla != null) {
                requiredStage = LockRegistry.getInstance().primaryRestrictingStageForBlock(player, vanilla.getBlock());
            }
        }
        if (requiredStage.isPresent()) {
            ItemEnforcer.notifyLocked(player, requiredStage.get(), StageConfig.getMsgTypeLabelBlock());
        }
    }

    /**
     * Notify player that block is locked.
     * v2.0: shows the first gating stage the player is missing.
     */
    public static void notifyPlacementLocked(ServerPlayer player, Block block) {
        Optional<StageId> requiredStage = LockRegistry.getInstance().primaryRestrictingStageForBlock(player, block);
        if (requiredStage.isPresent()) {
            ItemEnforcer.notifyLocked(player, requiredStage.get(), StageConfig.getMsgTypeLabelBlock());
        }
    }

    /**
     * Notify player that block interaction is locked.
     * v2.0: shows the first gating stage the player is missing.
     */
    public static void notifyInteractionLocked(ServerPlayer player, Block block) {
        Optional<StageId> requiredStage = LockRegistry.getInstance().primaryRestrictingStageForBlock(player, block);
        if (requiredStage.isPresent()) {
            ItemEnforcer.notifyLocked(player, requiredStage.get(), StageConfig.getMsgTypeLabelBlock());
        }
    }
}
