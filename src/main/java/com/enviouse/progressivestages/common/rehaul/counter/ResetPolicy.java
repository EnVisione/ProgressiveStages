package com.enviouse.progressivestages.common.rehaul.counter;

public enum ResetPolicy {
    NEVER,
    ON_GRANT,
    ON_REVOKE,
    ON_DEATH,
    ON_RESPAWN,
    ON_LOGIN,
    ON_DIMENSION_CHANGE,
    ON_SESSION_ENTRY,
    ON_SESSION_EXIT,
    ON_SUCCESS,
    ON_FAILURE,
    EXPLICIT
}
