package com.enviouse.progressivestages.common.api;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;

import java.util.Set;
import java.util.UUID;

/**
 * Event fired when multiple stages change at once (bulk operation).
 *
 * <p>Use cases:
 * <ul>
 *   <li>Player login - snapshot of all stages synced</li>
 *   <li>Team join - inheriting team's stages</li>
 *   <li>Config reload - stages may have changed</li>
 *   <li>Linear progression - prerequisites auto-granted</li>
 * </ul>
 *
 * <p>This event allows listeners to perform ONE recheck instead of N rechecks
 * when multiple stages change simultaneously.
 *
 * <p>Note: Individual {@link StageChangeEvent}s are NOT fired when this event
 * is used - listeners should handle both event types.
 */
public class StagesBulkChangedEvent extends Event {

    /**
     * Reason for the bulk change.
     */
    public enum Reason {
        /** Player logged in, stages loaded from save */
        LOGIN,
        /** Player joined a team, inherited team stages */
        TEAM_JOIN,
        /** Player left a team, stages may have changed */
        TEAM_LEAVE,
        /** Config/stage files reloaded */
        RELOAD,
        /** Linear progression granted prerequisites */
        LINEAR_PROGRESSION,
        /** Team sync from another player's action */
        TEAM_SYNC,
        /** Unknown/other bulk change */
        OTHER
    }

    private final ServerPlayer player;
    private final UUID teamId;
    private final Set<StageId> currentStages;
    private final Reason reason;

    /**
     * Creates a bulk stage change event.
     *
     * @param player The affected player
     * @param teamId The player's team ID
     * @param currentStages The player's current stages after the change
     * @param reason Why the bulk change occurred
     */
    public StagesBulkChangedEvent(ServerPlayer player, UUID teamId,
                                   Set<StageId> currentStages, Reason reason) {
        this.player = player;
        this.teamId = teamId;
        this.currentStages = currentStages;
        this.reason = reason;
    }

    public ServerPlayer getPlayer() {
        return player;
    }

    public UUID getTeamId() {
        return teamId;
    }

    /**
     * Get the player's current stages after the bulk change.
     * This is an unmodifiable snapshot.
     */
    public Set<StageId> getCurrentStages() {
        return currentStages;
    }

    public Reason getReason() {
        return reason;
    }
}

