package com.enviouse.progressivestages.server.structure;

import com.enviouse.progressivestages.common.api.structure.StructureSessionId;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StructureLeasePolicyTest {

    @Test
    void introducedStageWaitsForFinalParticipant() {
        var bucket = new StructureSessionManager.LeaseBucket(true);
        var first = lease(UUID.randomUUID());
        var second = lease(UUID.randomUUID());

        assertTrue(bucket.acquire(first));
        assertTrue(bucket.acquire(second));
        assertFalse(bucket.acquire(first));
        assertEquals(2, bucket.participantCount());
        assertTrue(bucket.release(first));
        assertFalse(bucket.shouldRevoke());
        assertTrue(bucket.release(second));
        assertTrue(bucket.shouldRevoke());
    }

    @Test
    void preownedStageIsNeverRevokedByFinalRelease() {
        var bucket = new StructureSessionManager.LeaseBucket(false);
        var participant = lease(UUID.randomUUID());
        bucket.acquire(participant);
        bucket.release(participant);

        assertFalse(bucket.shouldRevoke());
    }

    private static StructureSessionManager.ParticipantLease lease(UUID player) {
        return new StructureSessionManager.ParticipantLease(
            ResourceLocation.parse("test:provider"), StructureSessionId.random(), player);
    }
}
