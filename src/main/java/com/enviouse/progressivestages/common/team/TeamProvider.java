package com.enviouse.progressivestages.common.team;

import com.enviouse.progressivestages.common.config.StageConfig;
import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
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
        // Check if FTB Teams integration is enabled in config
        if (!StageConfig.isFtbTeamsIntegrationEnabled()) {
            ftbTeamsAvailable = false;
            LOGGER.info("[ProgressiveStages] FTB Teams integration disabled by config, using solo mode");
            integration = new SoloIntegration();
            return;
        }

        // Check if FTB Teams is available via reflection (no direct class references)
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
            integration = new ReflectiveFTBTeamsIntegration();
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
     * FTB Teams implementation using reflection to avoid any direct class references
     * to FTB Teams API. This prevents NoClassDefFoundError when FTB Teams is not installed.
     */
    private static class ReflectiveFTBTeamsIntegration implements ITeamIntegration {

        private Object cachedApi;
        private java.lang.reflect.Method getManagerMethod;
        private java.lang.reflect.Method getTeamForPlayerMethod;
        private java.lang.reflect.Method getTeamByIDMethod;
        private java.lang.reflect.Method getIdMethod;
        private java.lang.reflect.Method getMembersMethod;
        private boolean reflectionFailed = false;

        private void ensureReflection() {
            if (cachedApi != null || reflectionFailed) return;
            try {
                Class<?> apiClass = Class.forName("dev.ftb.mods.ftbteams.api.FTBTeamsAPI");
                java.lang.reflect.Method apiMethod = apiClass.getMethod("api");
                cachedApi = apiMethod.invoke(null);
                getManagerMethod = cachedApi.getClass().getMethod("getManager");
            } catch (Exception e) {
                LOGGER.warn("[ProgressiveStages] FTB Teams reflection init failed: {}", e.getMessage());
                reflectionFailed = true;
            }
        }

        @Override
        public UUID getTeamId(ServerPlayer player) {
            ensureReflection();
            if (reflectionFailed) return player.getUUID();

            try {
                Object manager = getManagerMethod.invoke(cachedApi);

                if (getTeamForPlayerMethod == null) {
                    getTeamForPlayerMethod = manager.getClass().getMethod("getTeamForPlayer", ServerPlayer.class);
                }

                @SuppressWarnings("unchecked")
                Optional<Object> teamOpt = (Optional<Object>) getTeamForPlayerMethod.invoke(manager, player);

                if (teamOpt.isPresent()) {
                    Object team = teamOpt.get();
                    if (getIdMethod == null) {
                        getIdMethod = team.getClass().getMethod("getId");
                    }
                    return (UUID) getIdMethod.invoke(team);
                }
            } catch (Exception e) {
                LOGGER.debug("Failed to get FTB Team for player {}: {}", player.getName().getString(), e.getMessage());
            }

            return player.getUUID();
        }

        @Override
        public Set<ServerPlayer> getTeamMembers(UUID teamId, ServerPlayer requester) {
            ensureReflection();
            if (reflectionFailed) return Collections.singleton(requester);

            try {
                Object manager = getManagerMethod.invoke(cachedApi);

                if (getTeamByIDMethod == null) {
                    getTeamByIDMethod = manager.getClass().getMethod("getTeamByID", UUID.class);
                }

                @SuppressWarnings("unchecked")
                Optional<Object> teamOpt = (Optional<Object>) getTeamByIDMethod.invoke(manager, teamId);

                if (teamOpt.isPresent()) {
                    Object team = teamOpt.get();
                    if (getMembersMethod == null) {
                        getMembersMethod = team.getClass().getMethod("getMembers");
                    }

                    @SuppressWarnings("unchecked")
                    Collection<UUID> memberIds = (Collection<UUID>) getMembersMethod.invoke(team);
                    Set<ServerPlayer> members = new java.util.HashSet<>();

                    for (UUID memberId : memberIds) {
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
