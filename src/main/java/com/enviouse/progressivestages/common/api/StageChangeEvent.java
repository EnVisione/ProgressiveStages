package com.enviouse.progressivestages.common.api;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

import java.util.UUID;

/**
 * Event fired when a player's stage changes.
 * This event is fired on the NeoForge event bus.
 *
 * <p>Subscribe to this event to react to stage changes, such as:
 * <ul>
 *   <li>Refreshing FTB Quests stage tasks</li>
 *   <li>Updating EMI visibility</li>
 *   <li>Triggering custom game logic</li>
 * </ul>
 *
 * <p>This event is fired AFTER the stage change has been applied.
 * Cancelling this event has no effect on the stage change itself,
 * but can prevent downstream handlers from processing.
 */
public class StageChangeEvent extends Event implements ICancellableEvent {

    private final ServerPlayer player;
    private final UUID teamId;
    private final StageId stageId;
    private final StageChangeType changeType;
    private final StageCause cause;

    /**
     * Creates a new stage change event.
     *
     * @param player The player whose stage changed
     * @param teamId The team ID (player or FTB Teams team)
     * @param stageId The stage that changed
     * @param changeType Whether the stage was granted or revoked
     * @param cause The source/reason for the change
     */
    public StageChangeEvent(ServerPlayer player, UUID teamId, StageId stageId,
                           StageChangeType changeType, StageCause cause) {
        this.player = player;
        this.teamId = teamId;
        this.stageId = stageId;
        this.changeType = changeType;
        this.cause = cause;
    }

    /**
     * Get the player whose stage changed.
     * Note: In team mode, this is the player who triggered the change,
     * but all team members will have the stage change applied.
     */
    public ServerPlayer getPlayer() {
        return player;
    }

    /**
     * Get the team ID for the stage change.
     */
    public UUID getTeamId() {
        return teamId;
    }

    /**
     * Get the stage that was changed.
     */
    public StageId getStageId() {
        return stageId;
    }

    /**
     * Get the type of change (GRANTED or REVOKED).
     */
    public StageChangeType getChangeType() {
        return changeType;
    }

    /**
     * Check if the stage was granted.
     */
    public boolean wasGranted() {
        return changeType == StageChangeType.GRANTED;
    }

    /**
     * Check if the stage was revoked.
     */
    public boolean wasRevoked() {
        return changeType == StageChangeType.REVOKED;
    }

    /**
     * Get the cause/source of the stage change.
     */
    public StageCause getCause() {
        return cause;
    }
}

