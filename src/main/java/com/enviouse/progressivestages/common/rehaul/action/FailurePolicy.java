package com.enviouse.progressivestages.common.rehaul.action;

public enum FailurePolicy {
    ROLLBACK,
    CONTINUE,
    RETRY,
    COMPENSATE
}
