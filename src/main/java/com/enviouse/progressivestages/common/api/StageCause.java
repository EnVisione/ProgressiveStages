package com.enviouse.progressivestages.common.api;

/**
 * Represents the cause/source of a stage change.
 * Used for tracking and event handling.
 */
public enum StageCause {
    /**
     * Stage changed via command (/stage grant, /stage revoke)
     */
    COMMAND,

    /**
     * Stage granted from advancement completion
     */
    ADVANCEMENT,

    /**
     * Stage granted from item pickup
     */
    ITEM_PICKUP,

    /**
     * Stage granted from inventory check on login
     */
    INVENTORY_CHECK,

    /**
     * Stage granted from FTB Quests reward
     */
    QUEST_REWARD,

    /**
     * Stage granted from dimension entry
     */
    DIMENSION_ENTRY,

    /**
     * Stage granted from boss kill
     */
    BOSS_KILL,

    /**
     * Stage changed due to team propagation
     */
    TEAM_SYNC,

    /**
     * Stage granted automatically (e.g., starting stage, auto-dependency)
     */
    AUTO,

    /**
     * Stage granted as a starting stage on first join (v1.3)
     */
    STARTING_STAGE,

    /**
     * Stage changed via API call
     */
    API,

    /**
     * Unknown or unspecified cause
     */
    UNKNOWN
}

