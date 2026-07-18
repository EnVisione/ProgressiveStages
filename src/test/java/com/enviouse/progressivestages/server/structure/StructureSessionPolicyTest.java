package com.enviouse.progressivestages.server.structure;

import com.enviouse.progressivestages.common.api.structure.StructureCleanupPolicy;
import com.enviouse.progressivestages.common.api.structure.StructureLeaveOutcome;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StructureSessionPolicyTest {

    @Test
    void onlyFinalCommittedCompletedExitRevokesAccess() {
        assertTrue(StructureSessionPolicy.shouldRevokeAccess(true, StructureLeaveOutcome.COMPLETED,
            0, StructureCleanupPolicy.REVOKE_ACCESS_ON_COMPLETED_EXIT));
        assertFalse(StructureSessionPolicy.shouldRevokeAccess(true, StructureLeaveOutcome.COMPLETED,
            1, StructureCleanupPolicy.REVOKE_ACCESS_ON_COMPLETED_EXIT));
        assertFalse(StructureSessionPolicy.shouldRevokeAccess(false, StructureLeaveOutcome.COMPLETED,
            0, StructureCleanupPolicy.REVOKE_ACCESS_ON_COMPLETED_EXIT));
    }

    @Test
    void lifecycleInterruptionsAndKeepPolicyRetainAccess() {
        for (StructureLeaveOutcome outcome : StructureLeaveOutcome.values()) {
            if (outcome == StructureLeaveOutcome.COMPLETED) continue;
            assertFalse(StructureSessionPolicy.shouldRevokeAccess(true, outcome, 0,
                StructureCleanupPolicy.REVOKE_ACCESS_ON_COMPLETED_EXIT));
        }
        assertFalse(StructureSessionPolicy.shouldRevokeAccess(true, StructureLeaveOutcome.COMPLETED,
            0, StructureCleanupPolicy.KEEP_ACCESS));
    }
}
