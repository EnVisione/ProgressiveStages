package com.enviouse.progressivestages.common.team;

import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.config.StageConfig;
import com.enviouse.progressivestages.common.data.StageAttachments;
import com.enviouse.progressivestages.common.data.TeamStageData;
import com.enviouse.progressivestages.common.network.NetworkHandler;
import com.enviouse.progressivestages.common.stage.StageOrder;
import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

import java.util.*;

/**
 * Handles team stage synchronization and alignment logic.
 *
 * Key concepts:
 * - When a player joins a team, their stages are evaluated against the team's stages
 * - Higher-stage players joining lower-stage teams don't affect the team
 * - Once all members reach the same stage, the team becomes "aligned" and progresses together
 */
public class TeamStageSync {

    private static final Logger LOGGER = LogUtils.getLogger();

    // Track which teams are in "aligned" mode (all members have same stages)
    private static final Map<UUID, Boolean> teamAlignmentStatus = new HashMap<>();

    private static MinecraftServer server;

    public static void initialize(MinecraftServer srv) {
        server = srv;
    }

    /**
     * Check if a team is in aligned mode (all members have the same stages)
     */
    public static boolean isTeamAligned(UUID teamId) {
        return teamAlignmentStatus.getOrDefault(teamId, false);
    }

    /**
     * Set team alignment status
     */
    public static void setTeamAligned(UUID teamId, boolean aligned) {
        teamAlignmentStatus.put(teamId, aligned);
        if (aligned) {
            LOGGER.info("Team {} is now aligned - stages will sync", teamId);
        }
    }

    /**
     * Check and update team alignment status.
     * Called after stage grants/revokes.
     */
    public static void checkTeamAlignment(UUID teamId) {
        if (server == null) return;

        TeamStageData data = server.overworld().getData(StageAttachments.TEAM_STAGES);
        Set<StageId> teamStages = data.getStages(teamId);

        // Get all online team members
        Set<ServerPlayer> members = getOnlineTeamMembers(teamId);
        if (members.size() <= 1) {
            // Solo player or no online members - always aligned
            setTeamAligned(teamId, true);
            return;
        }

        // Check if all members have the same highest stage
        Optional<StageId> teamHighest = data.getHighestStage(teamId);
        if (teamHighest.isEmpty()) {
            setTeamAligned(teamId, true);
            return;
        }

        // All members with the team ID share the same stage data by design
        // The question is: should we track per-member progress before alignment?
        // For simplicity, we consider teams aligned once all online members have joined
        // and acknowledged the team's stage state
        setTeamAligned(teamId, true);
    }

    /**
     * Handle a player joining a team.
     * Returns true if the join should be allowed.
     */
    public static boolean onPlayerJoinTeam(ServerPlayer player, UUID newTeamId, UUID oldTeamId) {
        if (server == null) return true;

        TeamStageData data = server.overworld().getData(StageAttachments.TEAM_STAGES);

        // Get player's current stages (from their old team, or solo)
        Set<StageId> playerStages = data.getStages(oldTeamId);

        // Get new team's stages
        Set<StageId> teamStages = data.getStages(newTeamId);

        // Validate join based on stage matching rule
        if (!validateStageMatch(playerStages, teamStages)) {
            return false;
        }

        // If player has fewer stages than team, they get synced up
        // If player has more stages than team, team stays at their level
        // The player effectively loses their extra stages when joining a lower-stage team

        // Sync player to team stages
        NetworkHandler.sendStageSync(player, teamStages);

        // Check alignment
        checkTeamAlignment(newTeamId);

        return true;
    }

    /**
     * Handle a player leaving a team.
     */
    public static void onPlayerLeaveTeam(ServerPlayer player, UUID oldTeamId) {
        if (!StageConfig.isPersistStagesOnLeave()) {
            // Player loses their stages - create new solo team with no stages
            // This is handled by the solo mode - player's UUID becomes their team ID
            LOGGER.debug("Player {} left team, stages not persisted", player.getName().getString());
        } else {
            // Player keeps their stages
            LOGGER.debug("Player {} left team, stages persisted", player.getName().getString());
        }

        // Re-check alignment for the old team
        checkTeamAlignment(oldTeamId);
    }

    /**
     * Validate if a player can join a team based on stage matching rules.
     */
    private static boolean validateStageMatch(Set<StageId> playerStages, Set<StageId> teamStages) {
        // If team has no stages, anyone can join
        if (teamStages.isEmpty()) {
            return true;
        }

        // Get highest stages for comparison
        Optional<StageId> playerHighest = getHighestStage(playerStages);
        Optional<StageId> teamHighest = getHighestStage(teamStages);

        if (teamHighest.isEmpty()) {
            return true;
        }

        // Player with no stages can always join (they'll be synced to team)
        if (playerHighest.isEmpty()) {
            return true;
        }

        // v1.3: With dependency-based progression, players can always join teams
        // Players with higher or equal stages can join (they accept team's stage level)
        // Players with lower stages can also join (they'll need to catch up, but we allow it)
        // The key is: team's stage level doesn't change when someone joins
        return true;
    }

    /**
     * Sync stage grant to all team members when team is aligned.
     */
    public static void syncStageGrantToTeam(UUID teamId, StageId stageId) {
        if (server == null) return;
        if (!isTeamAligned(teamId)) return;

        Set<ServerPlayer> members = getOnlineTeamMembers(teamId);
        TeamStageData data = server.overworld().getData(StageAttachments.TEAM_STAGES);
        Set<StageId> stages = data.getStages(teamId);

        for (ServerPlayer member : members) {
            NetworkHandler.sendStageSync(member, stages);
        }
    }

    /**
     * Get all online players in a team.
     */
    private static Set<ServerPlayer> getOnlineTeamMembers(UUID teamId) {
        Set<ServerPlayer> members = new HashSet<>();

        if (server == null) return members;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID playerTeamId = TeamProvider.getInstance().getTeamId(player);
            if (playerTeamId.equals(teamId)) {
                members.add(player);
            }
        }

        return members;
    }

    /**
     * Get the "most advanced" stage from a set of stages.
     * v1.3: Uses dependency depth instead of order number.
     */
    private static Optional<StageId> getHighestStage(Set<StageId> stages) {
        if (stages.isEmpty()) {
            return Optional.empty();
        }

        StageId highest = null;
        int highestDepth = -1;

        for (StageId stageId : stages) {
            // v1.3: Use dependency depth instead of order
            int depth = StageOrder.getInstance().getAllDependencies(stageId).size();
            if (depth > highestDepth) {
                highestDepth = depth;
                highest = stageId;
            }
        }

        return Optional.ofNullable(highest);
    }

    /**
     * Clear alignment cache (for reloads).
     */
    public static void clear() {
        teamAlignmentStatus.clear();
    }
}
