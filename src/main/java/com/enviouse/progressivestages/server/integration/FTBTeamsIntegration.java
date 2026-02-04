package com.enviouse.progressivestages.server.integration;

import com.enviouse.progressivestages.common.team.TeamStageSync;
import com.enviouse.progressivestages.common.util.Constants;
import com.mojang.logging.LogUtils;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Integration with FTB Teams mod.
 * Uses polling to detect team changes since FTB Teams events aren't NeoForge events.
 */
@EventBusSubscriber(modid = Constants.MOD_ID)
public class FTBTeamsIntegration {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static boolean initialized = false;

    // Track each player's current team to detect changes
    private static final Map<UUID, UUID> lastKnownTeams = new HashMap<>();

    /**
     * Initialize FTB Teams integration if the mod is present.
     * Call this during mod setup.
     */
    public static void tryRegister() {
        if (ModList.get().isLoaded("ftbteams")) {
            try {
                // Test if we can access FTB Teams API
                Class.forName("dev.ftb.mods.ftbteams.api.FTBTeamsAPI");
                initialized = true;
                LOGGER.info("FTB Teams detected, team integration enabled");
            } catch (ClassNotFoundException e) {
                LOGGER.warn("FTB Teams found but API not accessible: {}", e.getMessage());
                initialized = false;
            }
        } else {
            LOGGER.info("FTB Teams not found, using solo mode");
            initialized = false;
        }
    }

    public static boolean isInitialized() {
        return initialized;
    }

    /**
     * Check for team changes every second (20 ticks).
     * This is needed because FTB Teams events use their own event system,
     * not NeoForge's event bus.
     */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!initialized) return;

        // Only check once per second to reduce overhead
        if (event.getServer().getTickCount() % 20 != 0) return;

        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            checkTeamChange(player);
        }
    }

    /**
     * Track player's initial team on login
     */
    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!initialized) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        try {
            Optional<Team> team = FTBTeamsAPI.api().getManager().getTeamForPlayer(player);
            UUID teamId = team.map(Team::getId).orElse(null);

            if (teamId != null) {
                lastKnownTeams.put(player.getUUID(), teamId);
                LOGGER.debug("Player {} logged in, team: {}", player.getName().getString(), teamId);
            } else {
                LOGGER.debug("Player {} logged in with no team", player.getName().getString());
            }
        } catch (Exception e) {
            LOGGER.error("Error tracking player {} team on login: {}", player.getName().getString(), e.getMessage());
        }
    }

    /**
     * Clean up tracking when player logs out
     */
    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!initialized) return;

        lastKnownTeams.remove(event.getEntity().getUUID());
        LOGGER.debug("Player {} logged out, tracking removed", event.getEntity().getName().getString());
    }

    /**
     * Check if a player's team has changed and handle it
     */
    private static void checkTeamChange(ServerPlayer player) {
        try {
            UUID playerId = player.getUUID();

            // Get current team from FTB Teams
            Optional<Team> currentTeamOpt = FTBTeamsAPI.api().getManager().getTeamForPlayer(player);
            UUID currentTeamId = currentTeamOpt.map(Team::getId).orElse(null);

            // Get last known team
            UUID lastTeamId = lastKnownTeams.get(playerId);

            // Check if team changed
            if (!java.util.Objects.equals(currentTeamId, lastTeamId)) {
                LOGGER.debug("Team change detected for {}: {} -> {}",
                        player.getName().getString(),
                        lastTeamId != null ? lastTeamId : "none",
                        currentTeamId != null ? currentTeamId : "none");

                // Handle team leave
                if (lastTeamId != null && currentTeamId == null) {
                    LOGGER.info("Player {} left team {}", player.getName().getString(), lastTeamId);
                    TeamStageSync.onPlayerLeaveTeam(player, lastTeamId);
                }
                // Handle team join
                else if (currentTeamId != null && lastTeamId == null) {
                    LOGGER.info("Player {} joined team {}", player.getName().getString(), currentTeamId);
                    TeamStageSync.onPlayerJoinTeam(player, currentTeamId, playerId);
                }
                // Handle team switch (left one team, joined another)
                else if (currentTeamId != null && lastTeamId != null && !currentTeamId.equals(lastTeamId)) {
                    LOGGER.info("Player {} switched from team {} to team {}",
                            player.getName().getString(), lastTeamId, currentTeamId);
                    TeamStageSync.onPlayerLeaveTeam(player, lastTeamId);
                    TeamStageSync.onPlayerJoinTeam(player, currentTeamId, lastTeamId);
                }

                // Update tracking
                if (currentTeamId != null) {
                    lastKnownTeams.put(playerId, currentTeamId);
                } else {
                    lastKnownTeams.remove(playerId);
                }
            }
        } catch (Exception e) {
            LOGGER.error("Error checking team change for {}: {}", player.getName().getString(), e.getMessage());
        }
    }

    /**
     * Get the current team ID for a player (if any)
     */
    public static UUID getTeamId(ServerPlayer player) {
        if (!initialized) return player.getUUID(); // Solo mode: player is their own team

        try {
            Optional<Team> team = FTBTeamsAPI.api().getManager().getTeamForPlayer(player);
            return team.map(Team::getId).orElse(player.getUUID());
        } catch (Exception e) {
            LOGGER.error("Error getting team for {}: {}", player.getName().getString(), e.getMessage());
            return player.getUUID();
        }
    }

    /**
     * Check if a player is in a team (not solo)
     */
    public static boolean isInTeam(ServerPlayer player) {
        if (!initialized) return false;

        try {
            Optional<Team> team = FTBTeamsAPI.api().getManager().getTeamForPlayer(player);
            return team.isPresent() && !team.get().getMembers().isEmpty();
        } catch (Exception e) {
            return false;
        }
    }
}
