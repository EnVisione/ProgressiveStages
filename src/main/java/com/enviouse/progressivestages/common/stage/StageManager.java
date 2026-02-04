package com.enviouse.progressivestages.common.stage;

import com.enviouse.progressivestages.common.api.StageId;
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
     * Grant a stage to a player (and all prerequisites)
     * Also grants to all team members if team mode is enabled
     */
    public void grantStage(ServerPlayer player, StageId stageId) {
        UUID teamId = TeamProvider.getInstance().getTeamId(player);
        grantStageToTeam(teamId, stageId);

        // Sync to all team members
        syncToTeamMembers(teamId);
    }

    /**
     * Grant a stage to a team (and all prerequisites)
     */
    public void grantStageToTeam(UUID teamId, StageId stageId) {
        if (!StageOrder.getInstance().stageExists(stageId)) {
            LOGGER.warn("Attempted to grant non-existent stage: {}", stageId);
            return;
        }

        TeamStageData data = getTeamStageData();
        Set<StageId> toGrant = new LinkedHashSet<>();

        // Add all prerequisites
        Set<StageId> prerequisites = StageOrder.getInstance().getPrerequisites(stageId);
        toGrant.addAll(prerequisites);

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
    }

    /**
     * Revoke a stage from a player (and all successor stages)
     * Also revokes from all team members if team mode is enabled
     */
    public void revokeStage(ServerPlayer player, StageId stageId) {
        UUID teamId = TeamProvider.getInstance().getTeamId(player);
        revokeStageFromTeam(teamId, stageId);

        // Sync to all team members
        syncToTeamMembers(teamId);
    }

    /**
     * Revoke a stage from a team (and all successor stages)
     */
    public void revokeStageFromTeam(UUID teamId, StageId stageId) {
        if (!StageOrder.getInstance().stageExists(stageId)) {
            LOGGER.warn("Attempted to revoke non-existent stage: {}", stageId);
            return;
        }

        TeamStageData data = getTeamStageData();
        Set<StageId> toRevoke = new LinkedHashSet<>();

        // Add the target stage
        toRevoke.add(stageId);

        // Add all successors
        Set<StageId> successors = StageOrder.getInstance().getSuccessors(stageId);
        toRevoke.addAll(successors);

        // Revoke all stages
        for (StageId id : toRevoke) {
            if (data.revokeStage(teamId, id)) {
                LOGGER.debug("Revoked stage {} from team {}", id, teamId);
            }
        }
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
     * Grant the starting stage to a new player
     */
    public void grantStartingStage(ServerPlayer player) {
        String startingStageId = StageConfig.getStartingStage();
        if (startingStageId == null || startingStageId.isEmpty()) {
            return;
        }

        StageId stageId = StageId.of(startingStageId);
        if (StageOrder.getInstance().stageExists(stageId)) {
            UUID teamId = TeamProvider.getInstance().getTeamId(player);
            if (getTeamStageData().getStages(teamId).isEmpty()) {
                grantStage(player, stageId);
                LOGGER.debug("Granted starting stage {} to player {}", stageId, player.getName().getString());
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
    public String getProgressString(ServerPlayer player) {
        Optional<StageId> currentStage = getCurrentStage(player);
        if (currentStage.isPresent()) {
            return StageOrder.getInstance().getProgressString(currentStage.get());
        }
        return "0/" + StageOrder.getInstance().getStageCount();
    }
}
