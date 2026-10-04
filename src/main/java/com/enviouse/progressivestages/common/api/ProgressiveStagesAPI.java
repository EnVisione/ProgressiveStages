package com.enviouse.progressivestages.common.api;

import com.enviouse.progressivestages.common.config.StageDefinition;
import com.enviouse.progressivestages.common.config.StageConfig;
import com.enviouse.progressivestages.common.lock.ConditionalRule;
import com.enviouse.progressivestages.common.api.structure.StructureContextProvider;
import com.enviouse.progressivestages.common.api.structure.StructureSessionId;
import com.enviouse.progressivestages.common.api.structure.StructureSessionView;
import com.enviouse.progressivestages.common.stage.StageManager;
import com.enviouse.progressivestages.common.stage.StageOrder;
import com.enviouse.progressivestages.common.stage.StageSlotResolver;
import com.enviouse.progressivestages.common.stage.StageActorContext;
import com.enviouse.progressivestages.common.stage.EffectiveStageSnapshot;
import com.enviouse.progressivestages.common.stage.OwnerRef;
import com.enviouse.progressivestages.common.stage.StageMutationResult;
import com.enviouse.progressivestages.common.stage.StageOperation;
import com.enviouse.progressivestages.common.stage.StageCapabilities;
import com.enviouse.progressivestages.common.stage.FieldDiagnostic;
import com.enviouse.progressivestages.common.team.TeamProvider;
import com.enviouse.progressivestages.server.enforcement.ConditionalLockEngine;
import com.enviouse.progressivestages.server.enforcement.StageCapabilityResolver;
import com.enviouse.progressivestages.server.enforcement.InventoryTargetResolverRegistry;
import com.enviouse.progressivestages.server.loader.StageFileLoader;
import com.enviouse.progressivestages.server.integration.luckperms.LuckPermsBridge;
import com.enviouse.progressivestages.server.loader.StageFileParser;
import com.enviouse.progressivestages.server.triggers.StageCounterData;
import com.enviouse.progressivestages.server.triggers.StageTriggerEvaluator;
import com.enviouse.progressivestages.server.structure.StructureContextRegistry;
import com.enviouse.progressivestages.server.structure.StructureSessionManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.ArrayList;
import java.util.UUID;

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

    /** Resolve the persistence owner for one stage and actor. */
    public static OwnerRef getStageOwner(ServerPlayer player, StageId stageId) {
        return com.enviouse.progressivestages.common.stage.StageOwnership.owner(player, stageId);
    }

    /** Return the actor and resolved owner context used by actor-aware integrations. */
    public static StageActorContext resolveStageOwner(ServerPlayer player, StageId stageId) {
        return com.enviouse.progressivestages.common.stage.StageOwnership.context(player, stageId);
    }

    /** Resolve a player identity on the server thread even when that actor is offline. */
    public static StageActorContext resolveActorOwner(UUID actorId, StageId stageId) {
        return com.enviouse.progressivestages.common.stage.StageOwnership.contextForActor(actorId, stageId);
    }

    /** Return an immutable individual effective stage snapshot. */
    public static EffectiveStageSnapshot getEffectiveSnapshot(ServerPlayer player) {
        return StageManager.getInstance().getEffectiveSnapshot(player);
    }

    /** Read the current individual view of an explicitly identified online or offline actor. */
    public static EffectiveStageSnapshot getActorSnapshot(UUID actorId) {
        return StageManager.getInstance().getActorSnapshot(actorId);
    }

    /** Apply an actor-aware mutation and return its committed result. */
    public static StageMutationResult mutateStage(StageActorContext context, StageId stageId,
                                                  StageOperation operation, StageCause cause) {
        return StageManager.getInstance().mutateStage(context, stageId, operation, cause);
    }

    /** Subscribe to committed actor-aware stage mutations. */
    public static AutoCloseable subscribeCommittedStageChanges(
            java.util.function.Consumer<StageMutationResult> listener) {
        return StageManager.getInstance().subscribeCommittedStageChanges(listener);
    }

    /** Return the current provider and definition capabilities used by editor validation. */
    public static StageCapabilities getStageCapabilities() {
        return getStageCapabilities(getAllDefinitions());
    }

    /** Inspect only identifiers referenced by these installed or draft definitions. */
    public static StageCapabilities getStageCapabilities(Collection<StageDefinition> definitions) {
        TeamProvider provider = TeamProvider.getInstance();
        StageCapabilities.TeamProviderStatus team = !StageConfig.isFtbTeamsIntegrationEnabled()
            ? StageCapabilities.TeamProviderStatus.DISABLED
            : provider.isFtbTeamsAvailable()
                ? StageCapabilities.TeamProviderStatus.READY
                : StageCapabilities.TeamProviderStatus.ABSENT;
        LuckPermsBridge.StageCapabilitiesView bridge = LuckPermsBridge.capabilities();
        StageCapabilities.LuckPermsStatus luckperms = switch (bridge.state()) {
            case "ready" -> StageCapabilities.LuckPermsStatus.READY;
            case "starting" -> StageCapabilities.LuckPermsStatus.STARTING;
            case "failed" -> StageCapabilities.LuckPermsStatus.FAILED;
            case "disabled" -> StageCapabilities.LuckPermsStatus.DISABLED;
            default -> StageCapabilities.LuckPermsStatus.ABSENT;
        };
        var server = StageManager.getInstance().getServer();
        return StageCapabilityResolver.resolve(definitions, team, luckperms, LuckPermsBridge::groupStatus,
            server == null ? null : server.getCommands().getDispatcher().getRoot(),
            StageFileLoader.getInstance().getCompiledSnapshot().revision());
    }

    /** Validate raw stage source with the same parser used for installed definitions. */
    public static List<FieldDiagnostic> validateStageOptions(Map<String, String> rawFiles,
                                                              StageCapabilities capabilities) {
        if (rawFiles == null || rawFiles.isEmpty()) return List.of();
        List<FieldDiagnostic> diagnostics = new ArrayList<>();
        for (Map.Entry<String, String> entry : rawFiles.entrySet()) {
            String file = entry.getKey() == null ? "<unknown>" : entry.getKey();
            StageFileParser.ParseResult parsed = StageFileParser.parseText(
                entry.getValue() == null ? "" : entry.getValue(), file, "validation", false);
            if (!parsed.isSuccess()) {
                parsed.getFieldDiagnostic(file).ifPresent(diagnostics::add);
                continue;
            }
            diagnostics.addAll(validateStageOptions(parsed.getStageDefinition(), file, capabilities));
        }
        return List.copyOf(diagnostics);
    }

    /** Validate provider references on an already parsed stage without reparsing package child files. */
    public static List<FieldDiagnostic> validateStageOptions(StageDefinition definition, String file,
                                                              StageCapabilities capabilities) {
        List<FieldDiagnostic> diagnostics = new ArrayList<>();
        if (definition.isServerScope() && definition.getTeamStage().isPresent()) {
            diagnostics.add(new FieldDiagnostic(FieldDiagnostic.Severity.ERROR, file, "stage.team_stage",
                Optional.empty(), "server_override", "Server stages cannot declare team_stage."));
        }
        if (capabilities == null) return List.copyOf(diagnostics);
        if (!definition.isServerScope() && definition.getTeamStage().orElse(StageConfig.isFtbTeamsMode())
                && capabilities.teamProvider() != StageCapabilities.TeamProviderStatus.READY) {
            diagnostics.add(new FieldDiagnostic(FieldDiagnostic.Severity.WARNING, file, "stage.team_stage",
                Optional.empty(), "provider_fallback", "The team provider is unavailable. This stage uses solo fallback under the current server settings."));
        }
        var options = definition.getLuckPerms();
        if (options.hasBridgeMappings()) {
            if (!options.enabled()) {
                diagnostics.add(new FieldDiagnostic(FieldDiagnostic.Severity.WARNING, file, "luckperms.enabled",
                    Optional.empty(), "bridge_disabled", "This stage's LuckPerms mappings are disabled. Their configuration is preserved."));
            } else if (capabilities.luckPerms() != StageCapabilities.LuckPermsStatus.READY) {
                diagnostics.add(new FieldDiagnostic(FieldDiagnostic.Severity.WARNING, file, "luckperms",
                    Optional.empty(), "provider_unavailable", "LuckPerms mappings remain dormant until the provider is ready. Current state. "
                        + capabilities.luckPerms().name().toLowerCase(java.util.Locale.ROOT) + "."));
            }
            if (capabilities.luckPerms() == StageCapabilities.LuckPermsStatus.READY) {
                for (var row : options.inbound()) {
                    for (String group : row.groups()) addGroupDiagnostic(diagnostics, file, "luckperms.inbound", row.id(), group, capabilities);
                }
                for (var row : options.outbound()) {
                    if (row.kind() == com.enviouse.progressivestages.common.config.LuckPermsStageOptions.OutboundKind.GROUP) {
                        addGroupDiagnostic(diagnostics, file, "luckperms.outbound", row.id(), row.value(), capabilities);
                    }
                }
            }
        }
        for (var row : options.commandPermissions()) {
            var status = capabilities.configuredCommandStatus().get(row.path());
            if (status == StageCapabilities.CommandStatus.RESOLVED) continue;
            String code = status == StageCapabilities.CommandStatus.MISSING ? "missing_command"
                : status == StageCapabilities.CommandStatus.AMBIGUOUS ? "ambiguous_command" : "command_unobserved";
            String message = status == StageCapabilities.CommandStatus.MISSING
                ? "The command path is not resolved in the current dispatcher. "
                : status == StageCapabilities.CommandStatus.AMBIGUOUS
                    ? "The command path matches multiple literal nodes. Verify each affected command branch. "
                    : "The command dispatcher is unavailable. Validate again after server startup. ";
            diagnostics.add(new FieldDiagnostic(FieldDiagnostic.Severity.WARNING, file,
                "command_permissions", Optional.of(row.id()), code, message + row.path()));
        }
        return List.copyOf(diagnostics);
    }

    private static void addGroupDiagnostic(List<FieldDiagnostic> diagnostics, String file, String field,
                                           String rowId, String group, StageCapabilities capabilities) {
        var status = capabilities.configuredGroupStatus().get(group);
        if (status == StageCapabilities.GroupStatus.PRESENT) return;
        boolean missing = status == StageCapabilities.GroupStatus.MISSING;
        diagnostics.add(new FieldDiagnostic(FieldDiagnostic.Severity.WARNING, file, field, Optional.of(rowId),
            missing ? "missing_group" : "group_unobserved", missing
                ? "The configured LuckPerms group does not exist. " + group
                : "The LuckPerms group lookup is pending or unavailable. Validate again to refresh. " + group));
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
     * @return true if independent ownership was added
     */
    public static boolean grantStage(ServerPlayer player, StageId stageId, StageCause cause) {
        StageManager manager = StageManager.getInstance();
        boolean independent = manager.hasIndependentStage(player, stageId);
        manager.grantStageWithCause(player, stageId, cause);
        return !independent && manager.hasIndependentStage(player, stageId);
    }

    /** Grant derived access while retaining source attribution for later reconciliation. */
    public static boolean grantStageFromSource(ServerPlayer player, StageId stageId, String source,
                                               StageCause cause) {
        return StageManager.getInstance().grantStageFromSource(player, stageId, source, cause);
    }

    /**
     * Grant exactly one stage attributed to a source while bypassing its prerequisites. Call on the server
     * thread. The source identity must be unpadded. The actor must be an online operator with permission level 3.
     * Stage slot, purchase, ownership, and source policy checks still apply. Source and audit reason must be
     * printable values of at most 256 characters. A
     * successful mutation records the actor, source, and required audit reason.
     *
     * @param player the acting player and stage owner context
     * @param stageId the single stage to grant
     * @param source the unpadded source identity retained for reconciliation, limited to 256 printable characters
     * @param auditReason the nonempty operator supplied reason, limited to 256 printable characters
     * @param cause the gameplay cause reported to stage listeners
     * @return true when this source was newly committed
     */
    public static boolean grantStageFromSourceWithDependencyBypass(ServerPlayer player, StageId stageId,
                                                                    String source, String auditReason,
                                                                    StageCause cause) {
        return StageManager.getInstance().grantStageFromSourceWithDependencyBypass(
            player, stageId, source, auditReason, cause);
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
     * @return true if stored access was removed or current permission eligibility was suppressed
     */
    public static boolean revokeStage(ServerPlayer player, StageId stageId, StageCause cause) {
        StageManager manager = StageManager.getInstance();
        long before = manager.getMutationRevision();
        manager.revokeStageWithCause(player, stageId, cause);
        return manager.getMutationRevision() != before;
    }

    /** remove one derived source and retain independently earned access. */
    public static boolean revokeStageFromSource(ServerPlayer player, StageId stageId, String source,
                                                StageCause cause) {
        return StageManager.getInstance().revokeStageFromSource(player, stageId, source, cause);
    }

    /** Grant an existing stage without checking or auto-granting its prerequisites. */
    public static boolean grantStageBypass(ServerPlayer player, StageId stageId, StageCause cause) {
        if (!stageExists(stageId)) return false;
        StageManager manager = StageManager.getInstance();
        boolean independent = manager.hasIndependentStage(player, stageId);
        manager.grantStageBypassDependencies(player, stageId, cause);
        return !independent && manager.hasIndependentStage(player, stageId);
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
            && getMissingDependencies(player, stageId).isEmpty()
            && getSlotDecision(player, stageId).allowed();
    }

    public static StageSlotResolver.Decision getSlotDecision(ServerPlayer player, StageId stageId) {
        StageDefinition definition = getDefinition(stageId).orElse(null);
        if (definition == null) return StageSlotResolver.Decision.denied("Stage does not exist. " + stageId);
        return StageManager.getInstance().getSlotDecision(player, definition);
    }

    public static int getOwnedSlotCount(ServerPlayer player, String group) {
        if (group == null || group.isBlank()) return 0;
        return (int) getStages(player).stream().filter(stage -> getDefinition(stage)
            .map(definition -> definition.getSlotGroup().equalsIgnoreCase(group)).orElse(false)).count();
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
        com.enviouse.progressivestages.common.network.NetworkHandler.sendCompiledSnapshot(player);
    }

    public static boolean activateConditionalRule(ServerPlayer player, String ruleId, long durationMillis) {
        return ConditionalLockEngine.activate(player, ruleId, durationMillis);
    }

    public static boolean activateConditionalRule(ServerPlayer player, String ruleId) {
        return activateConditionalRule(player, ruleId, 0L);
    }

    public static Optional<ConditionalRule> getConditionalRule(String ruleId) {
        return ConditionalLockEngine.findRule(ruleId);
    }

    public static boolean clearConditionalRule(ServerPlayer player, String ruleId) {
        return ConditionalLockEngine.clear(player, ruleId);
    }

    public static int clearConditionalRules(ServerPlayer player) {
        return ConditionalLockEngine.clearAll(player);
    }

    public static Map<net.minecraft.resources.ResourceLocation, Long> getActiveConditionalRules(
            ServerPlayer player) {
        long now = System.currentTimeMillis();
        Map<net.minecraft.resources.ResourceLocation, Long> remaining = new java.util.LinkedHashMap<>();
        ConditionalLockEngine.activeRules(player).forEach((id, expiry) ->
            remaining.put(id, Math.max(0L, expiry - now)));
        return java.util.Collections.unmodifiableMap(remaining);
    }

    public static Set<net.minecraft.resources.ResourceLocation> getConditionalRuleIds() {
        return Set.copyOf(ConditionalLockEngine.ruleIds());
    }

    public static void registerStructureContextProvider(ResourceLocation id,
                                                        StructureContextProvider provider) {
        StructureContextRegistry.getInstance().register(id, provider);
    }

    public static boolean unregisterStructureContextProvider(ResourceLocation id) {
        return StructureContextRegistry.getInstance().unregister(id);
    }

    /**
     * Register a server-side identity resolver for inventories that are not represented by a block or menu.
     *
     * <p>The resolver is invoked only while the server processes a transaction for the supplied player. It must
     * return an identity only when the receiving inventory is known unambiguously. Persisted rules match the
     * returned resource location and tags, never a player name, coordinates, or client supplied value.
     */
    public static void registerInventoryTargetResolver(ResourceLocation id,
            InventoryTargetResolverRegistry.InventoryTargetResolver resolver) {
        InventoryTargetResolverRegistry.get().register(id, resolver);
    }

    /**
     * Register a server-side inventory resolver together with stable editor catalog entries.
     */
    public static void registerInventoryTargetResolver(ResourceLocation id,
            InventoryTargetResolverRegistry.InventoryTargetResolver resolver,
            List<InventoryTargetResolverRegistry.InventoryTargetDescriptor> targets) {
        InventoryTargetResolverRegistry.get().register(id, resolver, targets);
    }

    public static boolean markStructureSessionComplete(ServerPlayer player,
                                                       StructureSessionId sessionId) {
        return StructureSessionManager.getInstance().markComplete(player, sessionId);
    }

    public static List<StructureSessionView> getActiveStructureSessions(ServerPlayer player) {
        return StructureSessionManager.getInstance().activeSessions(player);
    }

    public static List<StructureSessionView> reconcileStructureSessions(ServerPlayer player) {
        return StructureSessionManager.getInstance().reconcile(player, true);
    }

    public static ItemUseDecision evaluateItemUse(ServerPlayer player,
                                                  net.minecraft.world.item.ItemStack stack) {
        return com.enviouse.progressivestages.server.enforcement.ItemEnforcer
            .evaluateItemUse(player, stack);
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
