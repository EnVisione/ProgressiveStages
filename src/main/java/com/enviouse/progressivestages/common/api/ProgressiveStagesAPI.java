package com.enviouse.progressivestages.common.api;

import com.enviouse.progressivestages.common.config.StageDefinition;
import com.enviouse.progressivestages.common.stage.StageManager;
import com.enviouse.progressivestages.common.stage.StageOrder;
import com.enviouse.progressivestages.server.loader.StageFileLoader;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;

/**
 * Public API for ProgressiveStages.
 *
 * <p>This is the primary entry point for other mods to interact with ProgressiveStages.
 * All methods are thread-safe and can be called from any context.
 *
 * <p>Example usage:
 * <pre>{@code
 * // Check if player has a stage
 * boolean hasDiamond = ProgressiveStagesAPI.hasStage(player, StageId.of("diamond_age"));
 *
 * // Grant a stage with tracking
 * ProgressiveStagesAPI.grantStage(player, StageId.of("iron_age"), StageCause.QUEST_REWARD);
 *
 * // Get all available stage definitions
 * Collection<StageDefinition> stages = ProgressiveStagesAPI.getAllDefinitions();
 * }</pre>
 */
public final class ProgressiveStagesAPI {

    private ProgressiveStagesAPI() {
        // Static API class
    }

    // ========== Stage Query Methods ==========

    /**
     * Check if a player has a specific stage.
     *
     * @param player The player to check
     * @param stageId The stage ID to check
     * @return true if the player (or their team) has the stage
     */
    public static boolean hasStage(ServerPlayer player, StageId stageId) {
        return StageManager.getInstance().hasStage(player, stageId);
    }

    /**
     * Check if a player has a stage by string ID.
     * The ID can be in format "stage_name" or "namespace:stage_name".
     *
     * @param player The player to check
     * @param stageId The stage ID string
     * @return true if the player has the stage
     */
    public static boolean hasStage(ServerPlayer player, String stageId) {
        return hasStage(player, StageId.parse(stageId));
    }

    /**
     * Get all stages a player currently has.
     *
     * @param player The player to query
     * @return Unmodifiable set of stage IDs the player has
     */
    public static Set<StageId> getStages(ServerPlayer player) {
        return StageManager.getInstance().getStages(player);
    }

    // ========== Stage Modification Methods ==========

    /**
     * Grant a stage to a player.
     * This will also sync to team members if team mode is enabled.
     *
     * <p>Fires {@link StageChangeEvent} for each stage granted.
     *
     * @param player The player to grant the stage to
     * @param stageId The stage to grant
     * @param cause The reason for granting (for tracking/events)
     * @return true if the stage was newly granted, false if already had it
     */
    public static boolean grantStage(ServerPlayer player, StageId stageId, StageCause cause) {
        if (hasStage(player, stageId)) {
            return false;
        }
        StageManager.getInstance().grantStageWithCause(player, stageId, cause);
        return true;
    }

    /**
     * Revoke a stage from a player.
     * This will also sync to team members if team mode is enabled.
     *
     * <p>Fires {@link StageChangeEvent} for each stage revoked.
     *
     * @param player The player to revoke the stage from
     * @param stageId The stage to revoke
     * @param cause The reason for revoking (for tracking/events)
     * @return true if the stage was revoked, false if didn't have it
     */
    public static boolean revokeStage(ServerPlayer player, StageId stageId, StageCause cause) {
        if (!hasStage(player, stageId)) {
            return false;
        }
        StageManager.getInstance().revokeStageWithCause(player, stageId, cause);
        return true;
    }

    // ========== Stage Definition Methods ==========

    /**
     * Get a stage definition by ID.
     *
     * @param stageId The stage ID
     * @return The stage definition if it exists
     */
    public static Optional<StageDefinition> getDefinition(StageId stageId) {
        return StageOrder.getInstance().getStageDefinition(stageId);
    }

    /**
     * Get a stage definition by string ID.
     *
     * @param stageId The stage ID string
     * @return The stage definition if it exists
     */
    public static Optional<StageDefinition> getDefinition(String stageId) {
        return getDefinition(StageId.parse(stageId));
    }

    /**
     * Get all registered stage definitions.
     * These are loaded from the TOML files in config/ProgressiveStages/.
     *
     * @return Collection of all stage definitions
     */
    public static Collection<StageDefinition> getAllDefinitions() {
        return StageFileLoader.getInstance().getAllStages();
    }

    /**
     * Get all registered stage IDs.
     *
     * @return Set of all stage IDs
     */
    public static Set<StageId> getAllStageIds() {
        return StageFileLoader.getInstance().getAllStageIds();
    }

    /**
     * Check if a stage exists (is registered).
     *
     * @param stageId The stage ID to check
     * @return true if the stage is registered
     */
    public static boolean stageExists(StageId stageId) {
        return StageOrder.getInstance().stageExists(stageId);
    }

    /**
     * Check if a stage exists by string ID.
     *
     * @param stageId The stage ID string
     * @return true if the stage is registered
     */
    public static boolean stageExists(String stageId) {
        return stageExists(StageId.parse(stageId));
    }

    // ========== v2.0.1: Automated-Craft Hook (for modded auto-crafters) ==========

    /**
     * Check whether an automated (non-player) craft at the given position should be
     * allowed for the nearest player within the configured radius.
     *
     * <p>Modded auto-crafters (Create's Mechanical Crafter, RFTools Crafter, Mekanism
     * Formulaic Assemblicator, EnderIO crafter, KubeJS-driven custom crafters, etc.)
     * should call this BEFORE consuming ingredients / dispensing output. If the call
     * returns {@code true}, abort the craft.
     *
     * <p>Fast path: returns {@code false} immediately if no stage has opted into
     * {@code enforcement.block_automated_crafting} — zero overhead for packs that
     * don't use the feature.
     *
     * @param level   the server level the crafter sits in
     * @param pos     the crafter block position (used to find nearest player)
     * @param recipeId the recipe ID being crafted (may be null for ad-hoc crafts)
     * @param ingredientStacks the input stacks (the ItemStacks that would be consumed)
     * @param output  the output stack (may be {@link net.minecraft.world.item.ItemStack#EMPTY})
     * @return {@code true} if the craft should be cancelled; {@code false} to allow it
     */
    public static boolean shouldBlockAutomatedCraft(
            net.minecraft.server.level.ServerLevel level,
            net.minecraft.core.BlockPos pos,
            net.minecraft.resources.ResourceLocation recipeId,
            Iterable<net.minecraft.world.item.ItemStack> ingredientStacks,
            net.minecraft.world.item.ItemStack output) {
        com.enviouse.progressivestages.common.lock.LockRegistry reg =
            com.enviouse.progressivestages.common.lock.LockRegistry.getInstance();
        if (!reg.isAutoCraftGatingActive()) return false;
        if (level == null || pos == null) return false;

        int radius = reg.getMaxCrafterCheckRadius();
        net.minecraft.server.level.ServerPlayer nearest =
            com.enviouse.progressivestages.server.enforcement.NearestPlayerCheck.findNearest(
                level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, radius);
        if (nearest == null) return false;
        if (com.enviouse.progressivestages.common.config.StageConfig.isAllowCreativeBypass()
            && nearest.isCreative()) return false;

        java.util.Set<net.minecraft.world.item.Item> distinct = new java.util.HashSet<>();
        if (ingredientStacks != null) {
            for (net.minecraft.world.item.ItemStack s : ingredientStacks) {
                if (s != null && !s.isEmpty()) distinct.add(s.getItem());
            }
        }
        net.minecraft.world.item.Item outItem =
            (output != null && !output.isEmpty()) ? output.getItem() : null;
        java.util.Optional<com.enviouse.progressivestages.common.lock.LockRegistry.IngredientBlockResult> r =
            reg.firstBlockingAutoCraftStage(nearest, distinct, outItem, recipeId);
        if (r.isPresent()) {
            com.enviouse.progressivestages.server.enforcement.IngredientGateHelper
                .notifyAutoBlocked(nearest, r.get());
            return true;
        }
        return false;
    }
}

