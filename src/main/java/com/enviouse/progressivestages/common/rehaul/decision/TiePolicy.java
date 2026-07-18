package com.enviouse.progressivestages.common.rehaul.decision;

public enum TiePolicy {
    SAFE,
    LOCK_WINS,
    UNLOCK_WINS,
    MOST_SPECIFIC,
    FIRST_DECLARED,
    ERROR_ON_TIE
}
