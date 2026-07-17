package com.enviouse.progressivestages.common.api;

import com.enviouse.progressivestages.common.config.StageDefinition;
import com.enviouse.progressivestages.common.config.StageConfig;
import com.enviouse.progressivestages.common.stage.StageManager;
import com.enviouse.progressivestages.common.stage.StageOrder;
import com.enviouse.progressivestages.server.loader.StageFileLoader;
import com.enviouse.progressivestages.server.triggers.StageCounterData;
import com.enviouse.progressivestages.server.triggers.StageTriggerEvaluator;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Public API for ProgressiveStages.
 *
 * <p>This is the primary entry point for other mods to interact with ProgressiveStages.
 * Mutation methods must be called on the logical server thread. Query methods read the live
 * server state and should normally be called there as well.
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

    public static boolean hasAllStages(ServerPlayer player, Collection<StageId> stageIds) {
        return stageIds != null && getStages(player).containsAll(stageIds);
    }

    public static boolean hasAnyStage(ServerPlayer player, Collection<StageId> stageIds) {
        if (stageIds == null) return false;
        Set<StageId> owned = getStages(player);
        for (StageId id : stageIds) if (owned.contains(id)) return true;
        return false;
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
        return hasStage(player, stageId);
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
        return !hasStage(player, stageId);
    }

    /** Grant an existing stage without checking or auto-granting its prerequisites. */
    public static boolean grantStageBypass(ServerPlayer player, StageId stageId, StageCause cause) {
        if (!stageExists(stageId) || hasStage(player, stageId)) return false;
        StageManager.getInstance().grantStageBypassDependencies(player, stageId, cause);
        return hasStage(player, stageId);
    }

    /** Grant several requested stages and return how many requested targets changed. */
    public static int grantStages(ServerPlayer player, Collection<StageId> stageIds, StageCause cause) {
        if (stageIds == null) return 0;
        int changed = 0;
        for (StageId id : new java.util.LinkedHashSet<>(stageIds)) {
            if (id != null && grantStage(player, id, cause)) changed++;
        }
        return changed;
    }

    /** Revoke several requested stages and return how many requested targets changed. */
    public static int revokeStages(ServerPlayer player, Collection<StageId> stageIds, StageCause cause) {
        if (stageIds == null) return 0;
        int changed = 0;
        for (StageId id : new java.util.LinkedHashSet<>(stageIds)) {
            if (id != null && revokeStage(player, id, cause)) changed++;
        }
        return changed;
    }

    /** Dependencies the player still needs before this stage can be granted normally. */
    public static java.util.List<StageId> getMissingDependencies(ServerPlayer player, StageId stageId) {
        return java.util.List.copyOf(StageManager.getInstance().getMissingDependencies(player, stageId));
    }

    /** True when the stage exists, is not owned, and all dependencies are currently owned. */
    public static boolean isAvailable(ServerPlayer player, StageId stageId) {
        return stageExists(stageId) && !hasStage(player, stageId)
            && getMissingDependencies(player, stageId).isEmpty();
    }

    /** Existing, unowned stages whose dependency policy is currently satisfied. */
    public static List<StageId> getAvailableStages(ServerPlayer player) {
        return getAllStageIds().stream().filter(id -> isAvailable(player, id)).toList();
    }

    /** Every existing stage the player does not currently own. */
    public static List<StageId> getLockedStages(ServerPlayer player) {
        Set<StageId> owned = getStages(player);
        return getAllStageIds().stream().filter(id -> !owned.contains(id)).toList();
    }

    /** Every declared stage carrying the given lower-case tag. */
    public static java.util.List<StageId> getStagesWithTag(String tag) {
        if (tag == null || tag.isBlank()) return java.util.List.of();
        String wanted = tag.trim().toLowerCase(java.util.Locale.ROOT);
        return getAllDefinitions().stream()
            .filter(def -> def.getTags().contains(wanted))
            .map(StageDefinition::getId)
            .toList();
    }

    /** Every stage in a presentation category (case-insensitive). */
    public static List<StageId> getStagesInCategory(String category) {
        if (category == null) return List.of();
        String wanted = category.trim();
        return getAllDefinitions().stream()
            .filter(def -> def.getCategory().equalsIgnoreCase(wanted))
            .map(StageDefinition::getId).toList();
    }

    public static Set<StageId> getDependencies(StageId stageId) {
        return java.util.Collections.unmodifiableSet(
            new java.util.LinkedHashSet<>(StageOrder.getInstance().getDependencies(stageId)));
    }

    public static Set<StageId> getAllDependencies(StageId stageId) {
        return java.util.Collections.unmodifiableSet(
            new java.util.LinkedHashSet<>(StageOrder.getInstance().getAllDependencies(stageId)));
    }

    public static Set<StageId> getDependents(StageId stageId) {
        return java.util.Collections.unmodifiableSet(
            new java.util.LinkedHashSet<>(StageOrder.getInstance().getDependents(stageId)));
    }

    public static Set<StageId> getAllDependents(StageId stageId) {
        return java.util.Collections.unmodifiableSet(
            new java.util.LinkedHashSet<>(StageOrder.getInstance().getAllDependents(stageId)));
    }

    /** Detailed live trigger progress suitable for integrations and custom UIs. */
    public static List<StageTriggerEvaluator.RuleProgress> getTriggerProgress(
            ServerPlayer player, StageId stageId) {
        return List.copyOf(StageTriggerEvaluator.describeProgress(player, stageId));
    }

    public static float getTriggerPercent(ServerPlayer player, StageId stageId) {
        return StageTriggerEvaluator.stagePercent(player, stageId);
    }

    /** Immediately re-evaluate all declarative trigger rules for this player. */
    public static void evaluateTriggers(ServerPlayer player) {
        StageTriggerEvaluator.evaluatePlayer(player);
    }

    /** Push definitions, lock rules, ownership, and bypass state to a connected client. */
    public static void syncPlayer(ServerPlayer player) {
        com.enviouse.progressivestages.common.network.NetworkHandler.sendStageDefinitionsSync(player);
        com.enviouse.progressivestages.common.network.NetworkHandler.sendLockSync(player);
        com.enviouse.progressivestages.common.network.NetworkHandler.sendStageSync(player, getStages(player));
        com.enviouse.progressivestages.common.network.NetworkHandler.sendCreativeBypass(player,
            StageConfig.isAllowCreativeBypass() && player.isCreative());
    }

    // ========== Named counters (commands/KubeJS/custom integrations) ==========

    public static long getCounter(ServerPlayer player, String counter) {
        return StageCounterData.get(player.server).get(player.getUUID(), customCounterKey(counter));
    }

    public static long addCounter(ServerPlayer player, String counter, long amount) {
        StageCounterData data = StageCounterData.get(player.server);
        String key = customCounterKey(counter);
        data.increment(player.getUUID(), key, amount);
        StageTriggerEvaluator.evaluatePlayer(player);
        return data.get(player.getUUID(), key);
    }

    public static long setCounter(ServerPlayer player, String counter, long value) {
        StageCounterData data = StageCounterData.get(player.server);
        String key = customCounterKey(counter);
        data.set(player.getUUID(), key, value);
        StageTriggerEvaluator.evaluatePlayer(player);
        return data.get(player.getUUID(), key);
    }

    public static void resetCounter(ServerPlayer player, String counter) {
        StageCounterData.get(player.server).reset(player.getUUID(), customCounterKey(counter));
        StageTriggerEvaluator.evaluatePlayer(player);
    }

    private static String customCounterKey(String counter) {
        if (counter == null || counter.isBlank()) {
            throw new IllegalArgumentException("Counter name cannot be blank");
        }
        String normalized = counter.trim().toLowerCase(java.util.Locale.ROOT);
        return normalized.startsWith("custom:") ? normalized : "custom:" + normalized;
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
     * These are loaded from TOML files in config/progressivestages/stages/ and datapacks.
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
