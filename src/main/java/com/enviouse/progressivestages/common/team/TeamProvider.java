package com.enviouse.progressivestages.common.team;

import com.enviouse.progressivestages.common.config.StageConfig;
import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;

/**
 * Abstract team provider interface.
 * Supports FTB Teams integration or solo mode.
 */
public class TeamProvider {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static TeamProvider INSTANCE;
    private ITeamIntegration integration;
    private boolean ftbTeamsAvailable = false;

    public static TeamProvider getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new TeamProvider();
        }
        return INSTANCE;
    }

    private TeamProvider() {}

    /**
     * Initialize the team provider
     */
    public void initialize() {
        // Check if FTB Teams is available
        try {
            Class.forName("dev.ftb.mods.ftbteams.api.FTBTeamsAPI");
            ftbTeamsAvailable = true;
            LOGGER.info("FTB Teams detected, enabling team integration");
        } catch (ClassNotFoundException e) {
            ftbTeamsAvailable = false;
            LOGGER.info("FTB Teams not found, using solo mode");
        }

        // Create integration based on config and availability
        if (StageConfig.isFtbTeamsMode() && ftbTeamsAvailable) {
            integration = new FTBTeamsIntegration();
        } else {
            integration = new SoloIntegration();
        }
    }

    /**
     * Get the team ID for a player
     * In solo mode, this is the player's UUID
     * In team mode, this is the team's UUID
     */
    public UUID getTeamId(ServerPlayer player) {
        if (integration == null) {
            // Fallback to solo mode
            return player.getUUID();
        }
        return integration.getTeamId(player);
    }

    /**
     * Get all online players in a team
     */
    public Set<ServerPlayer> getTeamMembers(UUID teamId, ServerPlayer requester) {
        if (integration == null) {
            return Collections.singleton(requester);
        }
        return integration.getTeamMembers(teamId, requester);
    }

    /**
     * Check if FTB Teams is available and enabled
     */
    public boolean isFtbTeamsActive() {
        return ftbTeamsAvailable && StageConfig.isFtbTeamsMode();
    }

    /**
     * Interface for team integrations
     */
    public interface ITeamIntegration {
        UUID getTeamId(ServerPlayer player);
        Set<ServerPlayer> getTeamMembers(UUID teamId, ServerPlayer requester);
    }

    /**
     * Solo mode implementation - each player is their own team
     */
    private static class SoloIntegration implements ITeamIntegration {
        @Override
        public UUID getTeamId(ServerPlayer player) {
            return player.getUUID();
        }

        @Override
        public Set<ServerPlayer> getTeamMembers(UUID teamId, ServerPlayer requester) {
            return Collections.singleton(requester);
        }
    }

    /**
     * FTB Teams implementation
     */
    private static class FTBTeamsIntegration implements ITeamIntegration {
        @Override
        public UUID getTeamId(ServerPlayer player) {
            try {
                // Use FTB Teams API to get team ID
                var api = dev.ftb.mods.ftbteams.api.FTBTeamsAPI.api();
                var manager = api.getManager();
                var teamOpt = manager.getTeamForPlayer(player);

                if (teamOpt.isPresent()) {
                    return teamOpt.get().getId();
                }
            } catch (Exception e) {
                LOGGER.debug("Failed to get FTB Team for player {}: {}", player.getName().getString(), e.getMessage());
            }

            // Fallback to player UUID if no team
            return player.getUUID();
        }

        @Override
        public Set<ServerPlayer> getTeamMembers(UUID teamId, ServerPlayer requester) {
            try {
                var api = dev.ftb.mods.ftbteams.api.FTBTeamsAPI.api();
                var manager = api.getManager();
                var teamOpt = manager.getTeamByID(teamId);

                if (teamOpt.isPresent()) {
                    var team = teamOpt.get();
                    Set<ServerPlayer> members = new java.util.HashSet<>();

                    for (UUID memberId : team.getMembers()) {
                        ServerPlayer member = requester.getServer().getPlayerList().getPlayer(memberId);
                        if (member != null) {
                            members.add(member);
                        }
                    }

                    return members;
                }
            } catch (Exception e) {
                LOGGER.debug("Failed to get FTB Team members for team {}: {}", teamId, e.getMessage());
            }

            return Collections.singleton(requester);
        }
    }
}
