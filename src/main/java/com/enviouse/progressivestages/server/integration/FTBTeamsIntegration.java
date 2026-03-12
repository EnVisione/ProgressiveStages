package com.enviouse.progressivestages.server.integration;

import com.enviouse.progressivestages.common.api.ProgressiveStagesAPI;
import com.enviouse.progressivestages.common.api.StageCause;
import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.team.TeamStageSync;
import com.enviouse.progressivestages.common.util.Constants;
import com.mojang.logging.LogUtils;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftbteams.api.property.TeamProperties;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

import java.util.*;

/**
 * Integration with FTB Teams mod.
 * Uses polling to detect team changes since FTB Teams events aren't NeoForge events.
 *
 * <p>Also monitors FTB Teams' {@code TEAM_STAGES} property to detect stages granted
 * by FTB Quests team rewards. When a StageReward has {@code isTeamReward() == true}
 * (the default), FTB Quests calls {@code TeamStagesHelper.addTeamStage()} which
 * bypasses our StageProvider entirely — the stage is stored in FTB Teams' own
 * property system. This class detects those changes and syncs them to our system.
 */
@EventBusSubscriber(modid = Constants.MOD_ID)
public class FTBTeamsIntegration {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static boolean initialized = false;
    private static boolean initChecked = false;

    // Track each player's current team to detect changes
    private static final Map<UUID, UUID> lastKnownTeams = new HashMap<>();

    // Track FTB Teams' TEAM_STAGES property per team to detect stage changes
    // from FTB Quests team rewards (which bypass our StageProvider)
    private static final Map<UUID, Set<String>> lastKnownFtbTeamStages = new HashMap<>();

    /**
     * Lazy initialization — called on first server tick.
     * Detects FTB Teams availability without requiring an explicit call from startup code.
     */
    private static void ensureInitialized() {
        if (initChecked) return;
        initChecked = true;

        if (ModList.get().isLoaded("ftbteams")) {
            try {
                Class.forName("dev.ftb.mods.ftbteams.api.FTBTeamsAPI");
                initialized = true;
                LOGGER.info("[ProgressiveStages] FTB Teams detected, team stage monitoring enabled");
            } catch (ClassNotFoundException e) {
                LOGGER.warn("[ProgressiveStages] FTB Teams found but API not accessible: {}", e.getMessage());
                initialized = false;
            }
        } else {
            initialized = false;
        }
    }

    public static boolean isInitialized() {
        ensureInitialized();
        return initialized;
    }

    /**
     * Check for team changes every second (20 ticks).
     * This is needed because FTB Teams events use their own event system,
     * not NeoForge's event bus.
     */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        ensureInitialized();
        if (!initialized) return;

        // Only check once per second to reduce overhead
        if (event.getServer().getTickCount() % 20 != 0) return;

        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            checkTeamChange(player);
            checkFtbTeamStages(player);
        }
    }

    /**
     * Track player's initial team on login
     */
    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        ensureInitialized();
        if (!initialized) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        try {
            Optional<Team> team = FTBTeamsAPI.api().getManager().getTeamForPlayer(player);
            UUID teamId = team.map(Team::getId).orElse(null);

            if (teamId != null) {
                lastKnownTeams.put(player.getUUID(), teamId);
                LOGGER.debug("Player {} logged in, team: {}", player.getName().getString(), teamId);

                // Snapshot current FTB Teams stages as baseline for change detection
                team.ifPresent(t -> {
                    Set<String> ftbStages = new HashSet<>(t.getProperty(TeamProperties.TEAM_STAGES));
                    lastKnownFtbTeamStages.put(teamId, ftbStages);
                    if (!ftbStages.isEmpty()) {
                        LOGGER.debug("[ProgressiveStages] Snapshotted {} FTB Teams stages for team {}",
                            ftbStages.size(), teamId);
                    }
                });
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
        ensureInitialized();
        if (!initialized) return;

        UUID playerId = event.getEntity().getUUID();
        lastKnownTeams.remove(playerId);
        // Note: don't remove lastKnownFtbTeamStages here — it's keyed by teamId,
        // and other team members may still be online.
        LOGGER.debug("Player {} logged out, tracking removed", event.getEntity().getName().getString());
    }

    /**
     * Check if FTB Teams' TEAM_STAGES property has changed and sync to our system.
     *
     * <p>This handles the case where FTB Quests grants a stage reward as a "team reward"
     * ({@code isTeamReward() == true}, which is the default). In that case, FTB Quests
     * calls {@code TeamStagesHelper.addTeamStage(team, stage)} which stores the stage
     * in FTB Teams' own {@code TEAM_STAGES} property and <b>completely bypasses</b>
     * our {@code StageProvider.add()} method. Without this check, our system would
     * never know about the stage change, and EMI/JEI would not update.
     *
     * <p>Detection works by comparing the current {@code TEAM_STAGES} set against a
     * snapshot taken at login (or last poll). Newly added stages are granted through
     * our system; removed stages are revoked.
     */
    private static void checkFtbTeamStages(ServerPlayer player) {
        try {
            Optional<Team> teamOpt = FTBTeamsAPI.api().getManager().getTeamForPlayer(player);
            if (teamOpt.isEmpty()) return;

            Team team = teamOpt.get();
            UUID teamId = team.getId();

            // Read current FTB Teams stages
            Set<String> currentFtbStages = new HashSet<>(team.getProperty(TeamProperties.TEAM_STAGES));

            // Get last known snapshot (empty set if first check)
            Set<String> lastKnown = lastKnownFtbTeamStages.getOrDefault(teamId, Collections.emptySet());

            // Detect changes
            Set<String> added = new HashSet<>(currentFtbStages);
            added.removeAll(lastKnown);

            Set<String> removed = new HashSet<>(lastKnown);
            removed.removeAll(currentFtbStages);

            if (added.isEmpty() && removed.isEmpty()) {
                return; // No changes
            }

            // Grant newly added stages (FTB Teams → our system)
            // Use grantStageBypassDependencies because quest rewards are explicitly
            // configured by the modpack developer and should not be blocked by dependencies
            for (String stageName : added) {
                StageId stageId = StageId.parse(stageName);
                if (ProgressiveStagesAPI.stageExists(stageId) && !ProgressiveStagesAPI.hasStage(player, stageId)) {
                    com.enviouse.progressivestages.common.stage.StageManager.getInstance()
                        .grantStageBypassDependencies(player, stageId, StageCause.QUEST_REWARD);
                    LOGGER.info("[ProgressiveStages] Synced FTB Teams stage '{}' → granted to {} (team reward)",
                        stageId, player.getName().getString());
                }
            }

            // Revoke removed stages (FTB Teams removed → revoke from our system)
            for (String stageName : removed) {
                StageId stageId = StageId.parse(stageName);
                if (ProgressiveStagesAPI.stageExists(stageId) && ProgressiveStagesAPI.hasStage(player, stageId)) {
                    ProgressiveStagesAPI.revokeStage(player, stageId, StageCause.QUEST_REWARD);
                    LOGGER.info("[ProgressiveStages] Synced FTB Teams stage '{}' → revoked from {} (team reward removal)",
                        stageId, player.getName().getString());
                }
            }

            // Update snapshot
            lastKnownFtbTeamStages.put(teamId, currentFtbStages);

        } catch (Exception e) {
            LOGGER.error("[ProgressiveStages] Error checking FTB Teams stages for {}: {}",
                player.getName().getString(), e.getMessage());
        }
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
