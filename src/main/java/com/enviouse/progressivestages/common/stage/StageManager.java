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
        return getTeamStageData().hasStage(teamId, stageId);
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
                LOGGER.warn("[ProgressiveStages] Cannot grant stage '{}' to {}: missing dependencies: {}. " +
                    "Use command with bypass or enable linear_progression.",
                    stageId, player.getName().getString(), missing);
                return;
            }
        }

        List<StageId> newlyGranted = grantStageToTeamInternal(teamId, stageId, false);

        // Fire events for each newly granted stage
        for (StageId granted : newlyGranted) {
            fireStageChangeEvent(player, teamId, granted, StageChangeType.GRANTED, cause);
        }

        // Sync to all team members
        syncToTeamMembers(teamId);
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
     * @param teamId The team to grant stages to
     * @param stageId The stage to grant
     * @param bypassDependencies If true, skip dependency checks (admin bypass)
     */
    private List<StageId> grantStageToTeamInternal(UUID teamId, StageId stageId, boolean bypassDependencies) {
        if (!StageOrder.getInstance().stageExists(stageId)) {
            LOGGER.warn("Attempted to grant non-existent stage: {}", stageId);
            return Collections.emptyList();
        }

        TeamStageData data = getTeamStageData();
        Set<StageId> currentStages = data.getStages(teamId);
        Set<StageId> toGrant = new LinkedHashSet<>();

        // Check for missing dependencies (v1.3)
        if (!bypassDependencies && StageConfig.isLinearProgression()) {
            // Auto-grant all dependencies recursively
            Set<StageId> allDeps = StageOrder.getInstance().getAllDependencies(stageId);
            toGrant.addAll(allDeps);
        }

        // Add the target stage
        toGrant.add(stageId);

        // Grant all stages
        List<StageId> newlyGranted = new ArrayList<>();
        for (StageId id : toGrant) {
            if (data.grantStage(teamId, id)) {
                newlyGranted.add(id);
                LOGGER.debug("Granted stage {} to team {}", id, teamId);
            }
        }

        // Send unlock messages for newly granted stages
        if (!newlyGranted.isEmpty()) {
            sendUnlockMessages(teamId, newlyGranted);
        }

        return newlyGranted;
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
        UUID teamId = TeamProvider.getInstance().getTeamId(player);
        Set<StageId> currentStages = getTeamStageData().getStages(teamId);
        return StageOrder.getInstance().getMissingDependencies(currentStages, stageId);
    }

    /**
     * Grant a stage bypassing dependency checks (admin override).
     */
    public void grantStageBypassDependencies(ServerPlayer player, StageId stageId, StageCause cause) {
        UUID teamId = TeamProvider.getInstance().getTeamId(player);
        List<StageId> newlyGranted = grantStageToTeamInternal(teamId, stageId, true);

        // Fire events for each newly granted stage
        for (StageId granted : newlyGranted) {
            fireStageChangeEvent(player, teamId, granted, StageChangeType.GRANTED, cause);
        }

        // Sync to all team members
        syncToTeamMembers(teamId);
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
        List<StageId> revoked = revokeStageFromTeamInternal(teamId, stageId);

        // Fire events for each revoked stage
        for (StageId revokedStage : revoked) {
            fireStageChangeEvent(player, teamId, revokedStage, StageChangeType.REVOKED, cause);
        }

        // Sync to all team members
        syncToTeamMembers(teamId);
    }

    /**
     * Revoke a stage from a team (optionally with successors based on config)
     * Uses COMMAND as the default cause (legacy method, prefer revokeStageWithCause)
     */
    public void revokeStageFromTeam(UUID teamId, StageId stageId) {
        revokeStageFromTeamInternal(teamId, stageId);
    }

    /**
     * Internal method that revokes stages and returns revoked stages.
     */
    private List<StageId> revokeStageFromTeamInternal(UUID teamId, StageId stageId) {
        if (!StageOrder.getInstance().stageExists(stageId)) {
            LOGGER.warn("Attempted to revoke non-existent stage: {}", stageId);
            return Collections.emptyList();
        }

        TeamStageData data = getTeamStageData();
        Set<StageId> toRevoke = new LinkedHashSet<>();

        // Add the target stage
        toRevoke.add(stageId);

        // Only add dependents if linear progression is enabled
        // (Stages that depend on this one should also be revoked)
        if (StageConfig.isLinearProgression()) {
            Set<StageId> dependents = StageOrder.getInstance().getAllDependents(stageId);
            toRevoke.addAll(dependents);
        }

        // Revoke all stages
        List<StageId> revoked = new ArrayList<>();
        for (StageId id : toRevoke) {
            if (data.revokeStage(teamId, id)) {
                revoked.add(id);
                LOGGER.debug("Revoked stage {} from team {}", id, teamId);
            }
        }

        return revoked;
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
        return getTeamStageData().getStages(teamId);
    }

    /**
     * Get the highest stage a player has reached
     */
    public Optional<StageId> getCurrentStage(ServerPlayer player) {
        UUID teamId = TeamProvider.getInstance().getTeamId(player);
        return getTeamStageData().getHighestStage(teamId);
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

        // Only grant starting stages if player has no stages yet
        if (!currentStages.isEmpty()) {
            return;
        }

        // Grant all starting stages (bypass dependency checks for starting stages)
        for (String stageIdStr : startingStageIds) {
            StageId stageId = StageId.of(stageIdStr);
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
            }
        }
    }

    /**
     * Send unlock messages for newly granted stages
     */
    private void sendUnlockMessages(UUID teamId, List<StageId> newlyGranted) {
        if (server == null) return;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID playerTeamId = TeamProvider.getInstance().getTeamId(player);
            if (playerTeamId.equals(teamId)) {
                for (StageId stageId : newlyGranted) {
                    Optional<StageDefinition> defOpt = StageOrder.getInstance().getStageDefinition(stageId);
                    if (defOpt.isPresent()) {
                        StageDefinition def = defOpt.get();
                        def.getUnlockMessage().ifPresent(msg -> {
                            Component message = TextUtil.parseColorCodes(msg);
                            player.sendSystemMessage(message);
                        });
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

        // Fire bulk event for login - FTB Quests will do one recheck
        fireBulkChangedEvent(player, StagesBulkChangedEvent.Reason.LOGIN);
    }

    /**
     * Get the server instance.
     */
    public MinecraftServer getServer() {
        return server;
    }
}
