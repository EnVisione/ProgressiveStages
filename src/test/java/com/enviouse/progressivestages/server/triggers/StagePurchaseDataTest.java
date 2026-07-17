package com.enviouse.progressivestages.server.triggers;

import com.enviouse.progressivestages.common.api.StageId;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StagePurchaseDataTest {

    @Test
    void pendingRefundsRetainNamespacedStageIdentifiers() {
        StagePurchaseData data = new StagePurchaseData();
        UUID team = UUID.randomUUID();
        StageId stage = StageId.parse("example:advanced_stage");

        data.markPaid(team, stage);
        assertTrue(data.deferRefund(team, stage));
        assertEquals(Set.of(stage), data.getPendingRefunds(team));
    }
}
