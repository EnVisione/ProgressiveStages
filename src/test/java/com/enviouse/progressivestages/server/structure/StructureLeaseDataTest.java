package com.enviouse.progressivestages.server.structure;

import com.enviouse.progressivestages.common.api.StageId;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StructureLeaseDataTest {

    @Test
    void introducedLeaseOwnershipSurvivesSaveAndLoad() {
        UUID owner = UUID.randomUUID();
        UUID participant = UUID.randomUUID();
        StageId stage = StageId.parse("test:inside");
        net.minecraft.resources.ResourceLocation provider =
            net.minecraft.resources.ResourceLocation.parse("test:provider");
        var session = com.enviouse.progressivestages.common.api.structure.StructureSessionId.random();
        StructureLeaseData original = new StructureLeaseData();
        original.markIntroduced(owner, stage);
        original.markParticipant(owner, stage, provider, session, participant);

        StructureLeaseData restored = StructureLeaseData.load(
            original.save(new CompoundTag(), null), null);

        assertTrue(restored.wasIntroduced(owner, stage));
        assertEquals(java.util.Set.of(stage), restored.stagesFor(owner));
        assertTrue(restored.stagesFor(UUID.randomUUID()).isEmpty());
        assertEquals(1L, restored.participantCount(owner, stage));
        assertEquals(java.util.Set.of(new StructureLeaseData.PersistedParticipant(
            owner, stage, provider, session, participant)), restored.participantsFor(participant));
        restored.clear(owner, stage);
        assertFalse(restored.wasIntroduced(owner, stage));
        assertEquals(0L, restored.participantCount(owner, stage));
    }
}
