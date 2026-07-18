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
     * Stage granted because every sub-trigger of a multi-requirement was satisfied.
     * Multi-requirements live in triggers.toml under {@code [[multi]]} and are tracked
     * by {@code MultiTriggerManager}.
     *
     * @deprecated v2.3 replaced the global triggers.toml multi-requirements with per-stage
     *             {@code [[triggers]]} rules tracked by {@code StageTriggerEvaluator}, which
     *             reports {@link #TRIGGER}. Retained for back-compat with stored cause logs.
     */
    @Deprecated
    MULTI_TRIGGER,

    /**
     * Stage granted because a per-stage {@code [[triggers]]} rule was satisfied (v2.3).
     */
    TRIGGER,

    /**
     * Stage granted because the player bought it from the in-game tree GUI (v2.4 skill-tree mode).
     */
    PURCHASE,

    /**
     * Stage revoked by a {@code [revoke]} rule or {@code duration} expiry — death, XP dropping below
     * the maintained threshold, or a temporary stage running out (v2.4 regression).
     */
    REGRESSION,

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

    /** Stage changed by KubeJS or another server-side script. */
    SCRIPT,

    GROUP_POLICY,

    STRUCTURE_ENTER,

    STRUCTURE_LEAVE,

    STRUCTURE_COMPLETE,

    /**
     * Unknown or unspecified cause
     */
    UNKNOWN
}
