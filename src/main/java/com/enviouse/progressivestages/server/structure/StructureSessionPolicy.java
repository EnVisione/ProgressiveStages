package com.enviouse.progressivestages.server.structure;

import com.enviouse.progressivestages.common.api.structure.StructureCleanupPolicy;
import com.enviouse.progressivestages.common.api.structure.StructureLeaveOutcome;

public final class StructureSessionPolicy {
    private StructureSessionPolicy() {}

    public static boolean shouldRevokeAccess(boolean complete, StructureLeaveOutcome outcome,
                                             int remainingParticipants,
                                             StructureCleanupPolicy cleanupPolicy) {
        return complete
            && outcome == StructureLeaveOutcome.COMPLETED
            && remainingParticipants == 0
            && cleanupPolicy == StructureCleanupPolicy.REVOKE_ACCESS_ON_COMPLETED_EXIT;
    }
}
