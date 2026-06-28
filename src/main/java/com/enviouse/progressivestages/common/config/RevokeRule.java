package com.enviouse.progressivestages.common.config;

/**
 * v2.4: a stage's {@code [revoke]} rules — conditions that take the stage back away.
 *
 * <ul>
 *   <li>{@link #onDeath} — revoke the stage when the player dies.</li>
 *   <li>{@link #xpBelow} — "XP-maintained" stage: the team holds the stage only while the player's
 *       total experience points stay {@code >= xpBelow}. Spend below it and the stage is revoked
 *       until the XP is re-earned. {@code -1} disables this rule.</li>
 *   <li>{@link #cascade} — when this stage is revoked, also revoke every stage that depends on it
 *       (per-stage choice between isolated and cascading regression).</li>
 * </ul>
 */
public record RevokeRule(boolean onDeath, long xpBelow, boolean cascade) {

    public static final RevokeRule NONE = new RevokeRule(false, -1L, false);

    public boolean hasAny() {
        return onDeath || xpBelow >= 0;
    }

    public boolean maintainsXp() {
        return xpBelow >= 0;
    }
}
