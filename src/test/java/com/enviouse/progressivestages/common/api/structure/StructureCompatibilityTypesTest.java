package com.enviouse.progressivestages.common.api.structure;

import com.enviouse.progressivestages.common.api.StageId;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StructureCompatibilityTypesTest {

    @Test
    void boundsNormalizeContainAndExpandWithoutExposingMutableState() {
        StructureBounds bounds = new StructureBounds(10, 20, 30, 1, 2, 3);

        assertEquals(new StructureBounds(1, 2, 3, 10, 20, 30), bounds);
        assertTrue(bounds.contains(new BlockPos(1, 2, 3)));
        assertTrue(bounds.contains(new BlockPos(10, 20, 30)));
        assertFalse(bounds.contains(new BlockPos(0, 2, 3)));
        assertTrue(bounds.expanded(2).contains(new BlockPos(-1, 2, 3)));
        assertEquals(bounds, bounds.expanded(-4));
    }

    @Test
    void sessionIdentifiersRoundTripAndRejectNull() {
        UUID value = UUID.randomUUID();
        StructureSessionId id = new StructureSessionId(value);

        assertEquals(id, StructureSessionId.parse(id.toString()));
        assertThrows(NullPointerException.class, () -> new StructureSessionId(null));
    }

    @Test
    void sessionViewsDefensivelyCopyParticipants() {
        UUID participant = UUID.randomUUID();
        java.util.Set<UUID> participants = new java.util.HashSet<>();
        participants.add(participant);
        ResourceKey<Level> dimension = ResourceKey.create(
            net.minecraft.core.registries.Registries.DIMENSION,
            ResourceLocation.parse("minecraft:overworld"));
        StructureSessionView view = new StructureSessionView(
            ResourceLocation.parse("test:provider"), StructureSessionId.random(),
            new StructureInstanceKey(dimension, ResourceLocation.parse("minecraft:stronghold"),
                BlockPos.ZERO), new StructureBounds(0, 0, 0, 10, 10, 10), UUID.randomUUID(),
            StructureOwnershipScope.PLAYER, StageId.of("access"), Optional.of(StageId.of("inside")),
            false, StructureCleanupPolicy.REVOKE_ACCESS_ON_COMPLETED_EXIT,
            StructureSessionAvailability.AVAILABLE, 1L, participants, false);

        participants.clear();

        assertEquals(java.util.Set.of(participant), view.participants());
        assertThrows(UnsupportedOperationException.class, () -> view.participants().clear());
    }
}
