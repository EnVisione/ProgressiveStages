package com.enviouse.progressivestages.common.stage;

import com.enviouse.progressivestages.common.api.StageCause;
import com.enviouse.progressivestages.common.api.StageChangeEvent;
import com.enviouse.progressivestages.common.api.StageChangeType;
import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.api.StagesBulkChangedEvent;
import com.enviouse.progressivestages.common.config.StageDefinition;
import com.enviouse.progressivestages.common.data.StageAttachments;
import com.enviouse.progressivestages.common.data.TeamStageData;
import com.enviouse.progressivestages.common.network.NetworkHandler;
import com.enviouse.progressivestages.common.team.TeamProvider;
import com.enviouse.progressivestages.common.util.TextUtil;
import com.enviouse.progressivestages.common.config.StageConfig;
import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

import java.util.*;

/**
 * Core stage management logic.
 * Handles granting, revoking, and checking stages.
 */
public class StageManager {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static StageManager INSTANCE;
    private MinecraftServer server;

    /** v2.4: synthetic "team" that holds server-wide ({@code scope = "server"}) stages shared by everyone. */
    public static final UUID SERVER_TEAM = new UUID(0L, 0L);

    /** One concrete stage removal from its real persistence owner. */
    private record RevokedStage(UUID owner, StageId stageId) {}

    private record GrantResult(List<StageId> granted, List<StageId> replaced, String denial) {
        private GrantResult {
            granted = List.copyOf(granted);
            replaced = List.copyOf(replaced);
            denial = denial == null ? "" : denial;
        }
    }

    private static boolean isServerScoped(StageId stageId) {
        return StageOrder.getInstance().getStageDefinition(stageId)
            .map(StageDefinition::isServerScope).orElse(false);
    }

    private static UUID storageTeam(UUID subjectTeam, StageId stageId) {
        return isServerScoped(stageId) ? SERVER_TEAM : subjectTeam;
    }

    public static StageManager getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new StageManager();
        }
        return INSTANCE;
    }

    private StageManager() {}

    /**
     * Initialize the stage manager with the server
     */
    public void initialize(MinecraftServer server) {
        this.server = server;
    }

    public void shutdown(MinecraftServer stoppingServer) {
        if (this.server == stoppingServer) this.server = null;
    }

    /**
     * Get the team stage data storage
     */
    private TeamStageData getTeamStageData() {
        if (server == null) {
            return new TeamStageData();
        }
        ServerLevel overworld = server.overworld();
        return overworld.getData(StageAttachments.TEAM_STAGES);
    }

    /**
     * Check if a player has a specific stage
     */
    public boolean hasStage(ServerPlayer player, StageId stageId) {
        UUID teamId = TeamProvider.getInstance().getTeamId(player);
        return hasStage(teamId, stageId);
    }

    /**
     * Check if a team has a specific stage
     */
    public boolean hasStage(UUID teamId, StageId stageId) {
        TeamStageData data = getTeamStageData();
        // v2.4: server-wide stages live under SERVER_TEAM and count for every team.
        return data.hasStage(teamId, stageId) || data.hasStage(SERVER_TEAM, stageId);
    }

    /**
     * Grant a stage to a player (optionally with prerequisites based on config)
     * Also grants to all team members if team mode is enabled.
     * Uses COMMAND as the default cause.
     */
    public void grantStage(ServerPlayer player, StageId stageId) {
        grantStageWithCause(player, stageId, StageCause.COMMAND);
    }

    /**
     * Grant a stage to a player with a specific cause.
     * Also grants to all team members if team mode is enabled.
     * Fires StageChangeEvent for each newly granted stage.
     *
     * <p>v1.3: If linear_progression is enabled, auto-grants missing dependencies.
     * Otherwise, stage is granted directly (use for triggers/rewards that should fail silently on missing deps).
     *
     * @param player The player to grant the stage to
     * @param stageId The stage to grant
     * @param cause The reason for the grant
     */
    public void grantStageWithCause(ServerPlayer player, StageId stageId, StageCause cause) {
        UUID teamId = TeamProvider.getInstance().getTeamId(player);

        // For automatic grants (triggers, rewards), check dependencies unless linear_progression is on
        if (!StageConfig.isLinearProgression()) {
            List<StageId> missing = getMissingDependencies(player, stageId);
            if (!missing.isEmpty()) {
                LOGGER.debug("[ProgressiveStages] Cannot grant stage '{}' to {}: missing dependencies: {}",
                    stageId, player.getName().getString(), missing);
                // Notify the player so quest rewards / triggers don't silently fail
                String missingStr = missing.stream()
                    .map(id -> id.getPath())
                    .collect(java.util.stream.Collectors.joining(", "));
                String template = StageConfig.getMsgMissingDependencies();
                player.sendSystemMessage(TextUtil.parseColorCodes(
                    template.replace("{stage}", stageId.getPath())
                            .replace("{dependencies}", missingStr)));
                return;
            }
        }

        GrantResult result = grantStageToTeamInternal(teamId, stageId, false);
        if (!result.denial().isBlank()) {
            player.sendSystemMessage(TextUtil.parseColorCodes("&c" + result.denial()));
            return;
        }

        for (StageId replaced : result.replaced()) {
            UUID owner = storageTeam(teamId, replaced);
            fireStageChangeEvent(player, owner, replaced,
                StageChangeType.REVOKED, StageCause.GROUP_POLICY);
            refundPurchasedStage(player, new RevokedStage(owner, replaced));
        }

        // Fire events for each newly granted stage
        for (StageId granted : result.granted()) {
            fireStageChangeEvent(player, storageTeam(teamId, granted), granted, StageChangeType.GRANTED, cause);
            applyRewardsOnce(player, granted);
        }

        if (java.util.stream.Stream.concat(result.granted().stream(), result.replaced().stream())
                .anyMatch(StageManager::isServerScoped)) syncAllPlayers();
        else syncToTeamMembers(teamId);
    }

    /** v3.0: apply a newly-granted stage's [rewards] ONCE, to the player who earned/bought it. */
    private void applyRewardsOnce(ServerPlayer player, StageId stageId) {
        StageOrder.getInstance().getStageDefinition(stageId).ifPresent(d ->
            com.enviouse.progressivestages.server.enforcement.StageRewardApplier.apply(player, d));
    }

    /**
     * v3.0: record that {@code player}'s team actually PAID for a stage (called from the purchase
     * handler), so {@code refund_percent} only refunds stages that were bought — not ones earned via
     * trigger/command/quest, which would otherwise mint free items on every revoke/expiry.
     */
    public void markPurchased(ServerPlayer player, StageId stageId) {
        if (player.server == null) return;
        UUID storeTeam = storageTeam(TeamProvider.getInstance().getTeamId(player), stageId);
        com.enviouse.progressivestages.server.triggers.StagePurchaseData.get(player.server)
            .markPaid(storeTeam, stageId);
    }

    /**
     * Grant a stage to a team (optionally with dependencies based on config)
     * Uses COMMAND as the default cause (legacy method, prefer grantStageWithCause)
     */
    public void grantStageToTeam(UUID teamId, StageId stageId) {
        grantStageToTeamInternal(teamId, stageId, false);
    }

    /**
     * Internal method that grants stages and returns newly granted stages.
     *
     * @param teamId The subject team. Each granted stage is stored according to its own scope.
     * @param stageId The stage to grant
     * @param bypassDependencies If true, skip dependency checks (admin bypass)
     */
    private GrantResult grantStageToTeamInternal(UUID teamId, StageId stageId, boolean bypassDependencies) {
        if (!StageOrder.getInstance().stageExists(stageId)) {
            LOGGER.warn("Attempted to grant non-existent stage: {}", stageId);
            return new GrantResult(List.of(), List.of(), "Stage does not exist. " + stageId);
        }

        TeamStageData data = getTeamStageData();
        Set<StageId> toGrant = new LinkedHashSet<>();

        // Auto-grant only the dependency branches needed by this stage's policy. For example, an
        // `any` stage follows one declared branch instead of accidentally granting every branch.
        if (!bypassDependencies && StageConfig.isLinearProgression()) {
            Set<StageId> effectiveOwned = new LinkedHashSet<>(getStages(teamId));
            collectRequiredGrantPlan(stageId, effectiveOwned, toGrant, new HashSet<>());
        } else {
            toGrant.add(stageId);
        }

        Set<StageId> initial = new LinkedHashSet<>(getStages(teamId));
        Set<StageId> simulated = new LinkedHashSet<>(initial);
        LinkedHashSet<StageId> replaced = new LinkedHashSet<>();
        for (StageId id : toGrant) {
            StageDefinition definition = StageOrder.getInstance().getStageDefinition(id).orElse(null);
            StageSlotResolver.Decision decision = slotDecision(teamId, definition, simulated);
            if (!decision.allowed()) {
                return new GrantResult(List.of(), List.of(), decision.explanation());
            }
            decision.replacements().forEach(replaced::add);
            simulated.removeAll(decision.replacements());
            simulated.add(id);
        }

        List<StageId> removedStages = replaced.stream().filter(initial::contains)
            .filter(id -> !simulated.contains(id)).toList();
        for (StageId id : removedStages) {
            data.revokeStage(storageTeam(teamId, id), id);
            LOGGER.debug("Replaced stage {} for subject team {}", id, teamId);
        }
        List<StageId> newlyGranted = toGrant.stream().filter(simulated::contains)
            .filter(id -> !initial.contains(id)).toList();
        for (StageId id : newlyGranted) {
            UUID owner = storageTeam(teamId, id);
            data.grantStage(owner, id);
            LOGGER.debug("Granted stage {} to storage owner {} subject team {}", id, owner, teamId);
        }

        if (!newlyGranted.isEmpty()) {
            List<StageId> serverStages = newlyGranted.stream().filter(StageManager::isServerScoped).toList();
            List<StageId> teamStages = newlyGranted.stream().filter(s -> !isServerScoped(s)).toList();
            if (!serverStages.isEmpty()) sendUnlockMessages(SERVER_TEAM, serverStages);
            if (!teamStages.isEmpty()) sendUnlockMessages(teamId, teamStages);
        }

        return new GrantResult(newlyGranted, removedStages, "");
    }

    public StageSlotResolver.Decision getSlotDecision(ServerPlayer player, StageDefinition definition) {
        UUID teamId = TeamProvider.getInstance().getTeamId(player);
        return slotDecision(teamId, definition, getStages(teamId));
    }

    private StageSlotResolver.Decision slotDecision(UUID teamId, StageDefinition definition,
                                                     Set<StageId> owned) {
        return StageSlotResolver.resolve(definition, owned,
            id -> StageOrder.getInstance().getStageDefinition(id),
            id -> grantTime(teamId, id));
    }

    private long grantTime(UUID teamId, StageId stageId) {
        if (server == null) return -1L;
        return com.enviouse.progressivestages.server.triggers.StageRegressionData.get(server)
            .getGrantTime(storageTeam(teamId, stageId), stageId);
    }

    private void collectRequiredGrantPlan(StageId stageId, Set<StageId> effectiveOwned,
                                          Set<StageId> plan, Set<StageId> visiting) {
        if (effectiveOwned.contains(stageId) || plan.contains(stageId) || !visiting.add(stageId)) return;
        StageDefinition definition = StageOrder.getInstance().getStageDefinition(stageId).orElse(null);
        if (definition == null) {
            visiting.remove(stageId);
            return;
        }

        int ownedDirect = 0;
        for (StageId dependency : definition.getDependencies()) {
            if (effectiveOwned.contains(dependency) || plan.contains(dependency)) ownedDirect++;
        }
        int needed = Math.max(0, definition.getDependencyCount() - ownedDirect);
        for (StageId dependency : definition.getDependencies()) {
            if (needed <= 0) break;
            if (effectiveOwned.contains(dependency) || plan.contains(dependency)) continue;
            collectRequiredGrantPlan(dependency, effectiveOwned, plan, visiting);
            if (plan.contains(dependency)) needed--;
        }

        Set<StageId> prospective = new HashSet<>(effectiveOwned);
        prospective.addAll(plan);
        if (definition.dependenciesSatisfied(prospective)) plan.add(stageId);
        else LOGGER.warn("Cannot build an automatic grant plan for {}: its dependency policy is unsatisfiable", stageId);
        visiting.remove(stageId);
    }

    /**
     * Check if granting a stage would require missing dependencies.
     * Used for admin bypass confirmation flow.
     *
     * @param player The player to check
     * @param stageId The target stage
     * @return List of missing dependency stage IDs (empty if no missing deps)
     */
    public List<StageId> getMissingDependencies(ServerPlayer player, StageId stageId) {
        // Use the effective set (includes server-wide stages) so they satisfy dependencies.
        Set<StageId> currentStages = getStages(player);
        return StageOrder.getInstance().getMissingDependencies(currentStages, stageId);
    }

    /**
     * Grant a stage bypassing dependency checks (admin override).
     */
    public void grantStageBypassDependencies(ServerPlayer player, StageId stageId, StageCause cause) {
        UUID teamId = TeamProvider.getInstance().getTeamId(player);
        GrantResult result = grantStageToTeamInternal(teamId, stageId, true);
        if (!result.denial().isBlank()) {
            player.sendSystemMessage(TextUtil.parseColorCodes("&c" + result.denial()));
            return;
        }

        for (StageId replaced : result.replaced()) {
            UUID owner = storageTeam(teamId, replaced);
            fireStageChangeEvent(player, owner, replaced,
                StageChangeType.REVOKED, StageCause.GROUP_POLICY);
            refundPurchasedStage(player, new RevokedStage(owner, replaced));
        }

        // Fire events for each newly granted stage
        for (StageId granted : result.granted()) {
            fireStageChangeEvent(player, storageTeam(teamId, granted), granted, StageChangeType.GRANTED, cause);
            applyRewardsOnce(player, granted);
        }

        if (java.util.stream.Stream.concat(result.granted().stream(), result.replaced().stream())
                .anyMatch(StageManager::isServerScoped)) syncAllPlayers();
        else syncToTeamMembers(teamId);
    }

    public UUID getStorageOwner(ServerPlayer player, StageId stageId) {
        return storageTeam(TeamProvider.getInstance().getTeamId(player), stageId);
    }

    public boolean grantTemporaryStage(ServerPlayer player, StageId stageId, StageCause cause) {
        StageDefinition definition = StageOrder.getInstance().getStageDefinition(stageId).orElse(null);
        if (definition == null) return false;
        UUID teamId = TeamProvider.getInstance().getTeamId(player);
        StageSlotResolver.Decision decision = slotDecision(teamId, definition, getStages(teamId));
        if (!decision.allowed()) return false;
        for (StageId replaced : decision.replacements()) {
            UUID replacedOwner = storageTeam(teamId, replaced);
            if (getTeamStageData().revokeStage(replacedOwner, replaced)) {
                fireStageChangeEvent(player, replacedOwner, replaced,
                    StageChangeType.REVOKED, StageCause.GROUP_POLICY);
                refundPurchasedStage(player, new RevokedStage(replacedOwner, replaced));
            }
        }
        UUID owner = storageTeam(teamId, stageId);
        if (!getTeamStageData().grantStage(owner, stageId)) return false;
        fireStageChangeEvent(player, owner, stageId, StageChangeType.GRANTED, cause);
        if (isServerScoped(stageId) || decision.replacements().stream().anyMatch(StageManager::isServerScoped)) {
            syncAllPlayers();
        }
        else syncToTeamMembers(teamId);
        return true;
    }

    public boolean revokeTemporaryStage(ServerPlayer player, StageId stageId, StageCause cause) {
        return revokeTemporaryStage(player, getStorageOwner(player, stageId), stageId, cause);
    }

    public boolean revokeTemporaryStage(ServerPlayer player, UUID owner,
                                        StageId stageId, StageCause cause) {
        if (!StageOrder.getInstance().stageExists(stageId) || owner == null) return false;
        if (!getTeamStageData().revokeStage(owner, stageId)) return false;
        fireStageChangeEvent(player, owner, stageId, StageChangeType.REVOKED, cause);
        if (isServerScoped(stageId)) syncAllPlayers();
        else syncToTeamMembers(owner);
        return true;
    }

    /**
     * Revoke a stage from a player (optionally with dependents based on config)
     * Also revokes from all team members if team mode is enabled.
     * Uses COMMAND as the default cause.
     */
    public void revokeStage(ServerPlayer player, StageId stageId) {
        revokeStageWithCause(player, stageId, StageCause.COMMAND);
    }

    /**
     * Revoke a stage from a player with a specific cause.
     * Also revokes from all team members if team mode is enabled.
     * Fires StageChangeEvent for each revoked stage.
     *
     * @param player The player to revoke the stage from
     * @param stageId The stage to revoke
     * @param cause The reason for the revocation
     */
    public void revokeStageWithCause(ServerPlayer player, StageId stageId, StageCause cause) {
        UUID teamId = TeamProvider.getInstance().getTeamId(player);
        boolean serverScoped = isServerScoped(stageId);
        List<RevokedStage> revoked = revokeStageFromTeamInternal(teamId, stageId);

        // Fire one event for each concrete storage owner. A server-stage cascade can revoke the
        // same team-scoped dependent from many teams; collapsing those into one event leaves
        // regression clocks and integration state stale for every other team.
        for (RevokedStage change : revoked) {
            StageId revokedStage = change.stageId();
            ServerPlayer affectedPlayer = onlineRepresentative(change.owner(), player).orElse(player);
            fireStageChangeEvent(affectedPlayer, change.owner(), revokedStage, StageChangeType.REVOKED, cause);
            refundPurchasedStage(player, change);
        }

        boolean affectedMultipleTeams = serverScoped || revoked.stream()
            .anyMatch(change -> SERVER_TEAM.equals(change.owner()) || !teamId.equals(change.owner()));
        if (affectedMultipleTeams) syncAllPlayers(); else syncToTeamMembers(teamId);
    }

    private void refundPurchasedStage(ServerPlayer player, RevokedStage change) {
        StageDefinition definition = StageOrder.getInstance().getStageDefinition(change.stageId()).orElse(null);
        if (definition == null || !definition.isPurchasable() || definition.getCost().refundPercent() <= 0
                || player.server == null) return;
        var purchaseData = com.enviouse.progressivestages.server.triggers.StagePurchaseData.get(player.server);
        if (!purchaseData.isPaid(change.owner(), change.stageId())) return;
        Optional<ServerPlayer> recipient = onlineRepresentative(change.owner(), player);
        if (recipient.isPresent() && purchaseData.consumePaid(change.owner(), change.stageId())) {
            refundCost(recipient.get(), definition.getCost());
        } else {
            purchaseData.deferRefund(change.owner(), change.stageId());
        }
    }

    /** v3.0: return refund_percent of a purchased stage's item/xp cost to the player. */
    private void refundCost(ServerPlayer player, com.enviouse.progressivestages.common.config.StageCost cost) {
        int pct = cost.refundPercent();
        int xp = cost.xpLevels() * pct / 100;
        if (xp > 0) player.giveExperienceLevels(xp);
        for (var ic : cost.items()) {
            int give = ic.count() * pct / 100;
            if (give <= 0) continue;
            net.minecraft.world.item.Item item =
                net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(ic.item()).orElse(null);
            if (item == null) continue;
            int max = Math.max(1, new net.minecraft.world.item.ItemStack(item).getMaxStackSize());
            int remaining = give;
            while (remaining > 0) {
                int n = Math.min(remaining, max);
                net.minecraft.world.item.ItemStack stack = new net.minecraft.world.item.ItemStack(item, n);
                if (!player.getInventory().add(stack) || !stack.isEmpty()) player.drop(stack, false);
                remaining -= n;
            }
        }
    }

    /**
     * Revoke a stage from a team (optionally with successors based on config)
     * Uses COMMAND as the default cause (legacy method, prefer revokeStageWithCause)
     */
    public void revokeStageFromTeam(UUID teamId, StageId stageId) {
        revokeStageFromTeamInternal(teamId, stageId);
    }

    /**
     * Internal method that revokes stages and returns revoked stage ids. Each stage uses its own
     * storage scope. Cascading from a server-wide prerequisite removes team-scoped dependents from
     * every team because the prerequisite disappeared globally.
     */
    private List<RevokedStage> revokeStageFromTeamInternal(UUID teamId, StageId stageId) {
        if (!StageOrder.getInstance().stageExists(stageId)) {
            LOGGER.warn("Attempted to revoke non-existent stage: {}", stageId);
            return Collections.emptyList();
        }

        TeamStageData data = getTeamStageData();
        // Cascade to dependents when linear progression is on globally, OR the stage opts in via
        // its per-stage revoke_cascade flag. Policy-aware cascading preserves dependents whose
        // `any`/`at_least` prerequisites remain satisfied through another branch.
        boolean cascade = StageConfig.isLinearProgression()
            || StageOrder.getInstance().getStageDefinition(stageId)
                .map(d -> d.getRevoke().cascade()).orElse(false);
        List<RevokedStage> revoked = new ArrayList<>();
        Deque<RevokedStage> pending = new ArrayDeque<>();
        Set<RevokedStage> queued = new HashSet<>();
        RevokedStage root = new RevokedStage(storageTeam(teamId, stageId), stageId);
        pending.add(root);
        queued.add(root);

        while (!pending.isEmpty()) {
            RevokedStage change = pending.removeFirst();
            if (!data.revokeStage(change.owner(), change.stageId())) continue;
            revoked.add(change);
            if (!cascade) continue;

            for (StageId dependent : StageOrder.getInstance().getDependents(change.stageId())) {
                StageDefinition definition = StageOrder.getInstance().getStageDefinition(dependent).orElse(null);
                if (definition == null) continue;
                if (definition.isServerScope()) {
                    // Server stages have one concrete owner. Use the initiating team as the subject
                    // context for the unusual (but supported) case of a global stage depending on a
                    // team-scoped stage.
                    if (data.hasStage(SERVER_TEAM, dependent)
                            && !definition.dependenciesSatisfied(effectiveStages(data, teamId))) {
                        RevokedStage next = new RevokedStage(SERVER_TEAM, dependent);
                        if (queued.add(next)) pending.addLast(next);
                    }
                    continue;
                }

                Collection<UUID> owners = SERVER_TEAM.equals(change.owner())
                    ? new ArrayList<>(data.getAllTeamIds()) : List.of(change.owner());
                for (UUID owner : owners) {
                    if (SERVER_TEAM.equals(owner) || !data.hasStage(owner, dependent)) continue;
                    if (!definition.dependenciesSatisfied(effectiveStages(data, owner))) {
                        RevokedStage next = new RevokedStage(owner, dependent);
                        if (queued.add(next)) pending.addLast(next);
                    }
                }
            }
        }

        return revoked;
    }

    private static Set<StageId> effectiveStages(TeamStageData data, UUID teamId) {
        Set<StageId> result = new LinkedHashSet<>(data.getStages(teamId));
        if (!SERVER_TEAM.equals(teamId)) result.addAll(data.getStages(SERVER_TEAM));
        return result;
    }

    /** Prefer the initiating player when they belong to the owner; otherwise find an online member. */
    private Optional<ServerPlayer> onlineRepresentative(UUID owner, ServerPlayer initiator) {
        if (SERVER_TEAM.equals(owner)) return Optional.of(initiator);
        if (TeamProvider.getInstance().getTeamId(initiator).equals(owner)) return Optional.of(initiator);
        if (server == null) return Optional.empty();
        return server.getPlayerList().getPlayers().stream()
            .filter(candidate -> TeamProvider.getInstance().getTeamId(candidate).equals(owner))
            .findFirst();
    }

    /**
     * Get all stages for a player
     */
    public Set<StageId> getStages(ServerPlayer player) {
        UUID teamId = TeamProvider.getInstance().getTeamId(player);
        return getStages(teamId);
    }

    /**
     * Get all stages for a team
     */
    public Set<StageId> getStages(UUID teamId) {
        TeamStageData data = getTeamStageData();
        Set<StageId> server = data.getStages(SERVER_TEAM);
        Set<StageId> team = data.getStages(teamId);
        if (server.isEmpty() || teamId.equals(SERVER_TEAM)) return team; // fast path / server view
        // v2.4: union server-wide stages into every team's effective stage set.
        Set<StageId> union = new LinkedHashSet<>(team);
        union.addAll(server);
        return Collections.unmodifiableSet(union);
    }

    /**
     * Get the highest stage a player has reached
     */
    public Optional<StageId> getCurrentStage(ServerPlayer player) {
        StageId highest = null;
        int highestDepth = -1;
        for (StageId stageId : getStages(player)) {
            int depth = StageOrder.getInstance().getAllDependencies(stageId).size();
            if (depth > highestDepth) {
                highestDepth = depth;
                highest = stageId;
            }
        }
        return Optional.ofNullable(highest);
    }

    /**
     * Grant the starting stages to a new player.
     * v1.3: Supports multiple starting stages.
     */
    public void grantStartingStage(ServerPlayer player) {
        List<String> startingStageIds = StageConfig.getStartingStages();
        if (startingStageIds == null || startingStageIds.isEmpty()) {
            return;
        }

        UUID teamId = TeamProvider.getInstance().getTeamId(player);
        Set<StageId> currentStages = getTeamStageData().getStages(teamId);

        // Only grant starting stages if player has no stages yet, unless reapply is enabled.
        // (data.grantStage already short-circuits when the team already has the stage.)
        if (!currentStages.isEmpty() && !StageConfig.isReapplyStartingStagesOnLogin()) {
            return;
        }

        // Grant all starting stages (bypass dependency checks for starting stages)
        for (String stageIdStr : startingStageIds) {
            if (stageIdStr == null || stageIdStr.isBlank()) {
                continue;
            }
            StageId stageId = StageId.tryParse(stageIdStr);
            if (stageId == null) {
                LOGGER.warn("Skipping invalid starting stage entry '{}'", stageIdStr);
                continue;
            }
            if (StageOrder.getInstance().stageExists(stageId)) {
                grantStageBypassDependencies(player, stageId, StageCause.STARTING_STAGE);
                LOGGER.debug("Granted starting stage {} to player {}", stageId, player.getName().getString());
            } else {
                LOGGER.warn("Starting stage {} does not exist, skipping", stageIdStr);
            }
        }
    }

    /**
     * Sync stage data to all online team members
     */
    private void syncToTeamMembers(UUID teamId) {
        if (server == null) return;

        Set<StageId> stages = getStages(teamId);

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID playerTeamId = TeamProvider.getInstance().getTeamId(player);
            if (playerTeamId.equals(teamId)) {
                NetworkHandler.sendStageSync(player, stages);
                // v2.4: re-apply [attribute] modifiers for this member after the team's stages changed.
                com.enviouse.progressivestages.server.enforcement.StageAttributeApplier.reconcile(player);
                // v2.5: re-send advancements so newly (un)gated ones (dis)appear without a relog.
                com.enviouse.progressivestages.server.enforcement.AdvancementHider.resyncIfNeeded(player);
            }
        }
    }

    /** v2.4: sync + attribute-reconcile EVERY online player (used when a server-wide stage changes). */
    private void syncAllPlayers() {
        if (server == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            NetworkHandler.sendStageSync(player, getStages(player));
            com.enviouse.progressivestages.server.enforcement.StageAttributeApplier.reconcile(player);
            com.enviouse.progressivestages.server.enforcement.AdvancementHider.resyncIfNeeded(player);
        }
    }

    /**
     * Send unlock messages for newly granted stages
     */
    private void sendUnlockMessages(UUID teamId, List<StageId> newlyGranted) {
        if (server == null) return;

        // Server-scoped grants are stored under SERVER_TEAM — a sentinel no real player team matches —
        // so they must reach every online player rather than only members of the (synthetic) team.
        boolean serverScoped = SERVER_TEAM.equals(teamId);

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID playerTeamId = TeamProvider.getInstance().getTeamId(player);
            if (serverScoped || playerTeamId.equals(teamId)) {
                for (StageId stageId : newlyGranted) {
                    Optional<StageDefinition> defOpt = StageOrder.getInstance().getStageDefinition(stageId);
                    if (defOpt.isPresent()) {
                        StageDefinition def = defOpt.get();
                        def.getUnlockMessage().ifPresent(msg -> {
                            Component message = TextUtil.parseColorCodes(msg);
                            player.sendSystemMessage(message);
                        });
                        // v2.4: optional [unlock] toast/title/subtitle/sound/particles. This is
                        // per-member PRESENTATION (everyone on the team sees it). [rewards] are NOT
                        // applied here — they'd duplicate per online member / per online player on a
                        // server-scoped grant. They're applied once to the granting player instead.
                        com.enviouse.progressivestages.server.enforcement.UnlockEffectsApplier.apply(player, def);
                    }
                }

                // Play unlock sound
                if (StageConfig.isPlayLockSound()) {
                    player.playNotifySound(SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, 1.0f, 1.5f);
                }
            }
        }
    }

    /**
     * Get progress string for a player (e.g., "2/5")
     */
    @SuppressWarnings("removal")
    public String getProgressString(ServerPlayer player) {
        Set<StageId> stages = getStages(player);
        int total = StageOrder.getInstance().getStageCount();
        return stages.size() + "/" + total;
    }

    /**
     * Fire a stage change event on the NeoForge event bus.
     * This notifies all listeners (including FTB Quests compat) that a stage changed.
     */
    private void fireStageChangeEvent(ServerPlayer player, UUID teamId, StageId stageId,
                                       StageChangeType changeType, StageCause cause) {
        StageChangeEvent event = new StageChangeEvent(player, teamId, stageId, changeType, cause);
        NeoForge.EVENT_BUS.post(event);

        if (StageConfig.isDebugLogging()) {
            LOGGER.info("[ProgressiveStages] Stage {} {} for player {} (cause: {})",
                stageId, changeType, player.getName().getString(), cause);
        }
    }

    /**
     * Fire a bulk stages changed event.
     * Use this for login, team join, reload, etc. instead of firing N individual events.
     *
     * @param player The affected player
     * @param reason Why the bulk change occurred
     */
    public void fireBulkChangedEvent(ServerPlayer player, StagesBulkChangedEvent.Reason reason) {
        UUID teamId = TeamProvider.getInstance().getTeamId(player);
        Set<StageId> currentStages = Collections.unmodifiableSet(new HashSet<>(getStages(teamId)));

        StagesBulkChangedEvent event = new StagesBulkChangedEvent(player, teamId, currentStages, reason);
        NeoForge.EVENT_BUS.post(event);

        if (StageConfig.isDebugLogging()) {
            LOGGER.info("[ProgressiveStages] Bulk stage change for player {} (reason: {}, {} stages)",
                player.getName().getString(), reason, currentStages.size());
        }
    }

    /**
     * Sync stages to a player on login (fires bulk event instead of individual events).
     * Call this instead of multiple grantStage calls when a player logs in.
     */
    public void syncStagesOnLogin(ServerPlayer player) {
        // Grant starting stage if needed (this is a single operation, not bulk)
        grantStartingStage(player);

        // A global revoke cascade may have removed a purchased team stage while every member was
        // offline. Deliver its persisted refund to the first member who returns.
        if (player.server != null) {
            UUID teamId = TeamProvider.getInstance().getTeamId(player);
            var purchaseData = com.enviouse.progressivestages.server.triggers.StagePurchaseData.get(player.server);
            for (StageId pending : purchaseData.getPendingRefunds(teamId)) {
                StageDefinition def = StageOrder.getInstance().getStageDefinition(pending).orElse(null);
                if (def != null && def.isPurchasable() && def.getCost().refundPercent() > 0
                        && purchaseData.consumePendingRefund(teamId, pending)) {
                    refundCost(player, def.getCost());
                }
            }
        }

        // Fire bulk event for login - FTB Quests will do one recheck
        fireBulkChangedEvent(player, StagesBulkChangedEvent.Reason.LOGIN);

        // v2.4: apply this player's team's [attribute] modifiers (transient — re-applied each login).
        com.enviouse.progressivestages.server.enforcement.StageAttributeApplier.reconcile(player);
    }

    /**
     * Get the server instance.
     */
    public MinecraftServer getServer() {
        return server;
    }
}
