package com.enviouse.progressivestages.server.enforcement;

import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.api.structure.StructureAccessDecision;
import com.enviouse.progressivestages.common.api.structure.StructureBounds;
import com.enviouse.progressivestages.common.api.structure.StructureCleanupPolicy;
import com.enviouse.progressivestages.common.api.structure.StructureInstanceKey;
import com.enviouse.progressivestages.common.api.structure.StructureOwnershipScope;
import com.enviouse.progressivestages.common.api.structure.StructureSessionAvailability;
import com.enviouse.progressivestages.common.api.structure.StructureSessionId;
import com.enviouse.progressivestages.common.api.structure.StructureSessionSpec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StructureSessionAccessPolicyTest {
    private static final ResourceKey<Level> OVERWORLD = ResourceKey.create(Registries.DIMENSION,
        ResourceLocation.parse("minecraft:overworld"));
    private static final ResourceKey<Level> NETHER = ResourceKey.create(Registries.DIMENSION,
        ResourceLocation.parse("minecraft:the_nether"));
    private static final ResourceLocation STRONGHOLD = ResourceLocation.parse("minecraft:stronghold");

    @Test
    void exactInstancePermitRequiresTheStableStartPosition() {
        UUID owner = UUID.randomUUID();
        StructureSessionSpec spec = spec(owner, StructureSessionAvailability.AVAILABLE);

        assertTrue(validate(owner, OVERWORLD, new BlockPos(4, 40, 4),
            Optional.of(spec.instance()), spec, true).isEmpty());

        StructureInstanceKey otherStart = new StructureInstanceKey(OVERWORLD, STRONGHOLD,
            new BlockPos(32, 0, 0));
        assertEquals(Optional.of(StructureAccessDecision.Reason.WRONG_INSTANCE),
            validate(owner, OVERWORLD, new BlockPos(4, 40, 4), Optional.of(otherStart), spec, true));
    }

    @Test
    void virtualSessionsMayPermitWithoutAGeneratedCandidate() {
        UUID owner = UUID.randomUUID();
        StructureSessionSpec spec = spec(owner, StructureSessionAvailability.AVAILABLE);

        assertTrue(validate(owner, OVERWORLD, new BlockPos(4, 40, 4),
            Optional.empty(), spec, true).isEmpty());
    }

    @Test
    void ownerDimensionAndBoundsAreValidatedBeforeStageOwnership() {
        UUID owner = UUID.randomUUID();
        StructureSessionSpec spec = spec(owner, StructureSessionAvailability.AVAILABLE);

        assertEquals(Optional.of(StructureAccessDecision.Reason.WRONG_OWNER),
            validate(UUID.randomUUID(), OVERWORLD, new BlockPos(4, 40, 4),
                Optional.of(spec.instance()), spec, false));
        assertEquals(Optional.of(StructureAccessDecision.Reason.WRONG_INSTANCE),
            validate(owner, NETHER, new BlockPos(4, 40, 4), Optional.empty(), spec, false));
        assertEquals(Optional.of(StructureAccessDecision.Reason.WRONG_INSTANCE),
            validate(owner, OVERWORLD, new BlockPos(40, 40, 40), Optional.empty(), spec, false));
    }

    @Test
    void availabilityAndAccessStageRemainAuthoritative() {
        UUID owner = UUID.randomUUID();
        StructureSessionSpec unavailable = spec(owner, StructureSessionAvailability.RESETTING);

        assertEquals(Optional.of(StructureAccessDecision.Reason.UNAVAILABLE),
            validate(owner, OVERWORLD, new BlockPos(4, 40, 4), Optional.empty(), unavailable, false));
        assertEquals(Optional.of(StructureAccessDecision.Reason.MISSING_ACCESS_STAGE),
            validate(owner, OVERWORLD, new BlockPos(4, 40, 4), Optional.empty(),
                spec(owner, StructureSessionAvailability.AVAILABLE), false));
    }

    private static Optional<StructureAccessDecision.Reason> validate(
            UUID owner, ResourceKey<Level> dimension, BlockPos position,
            Optional<StructureInstanceKey> candidate, StructureSessionSpec spec,
            boolean accessStageOwned) {
        return StructureSessionAccessPolicy.validatePermit(owner, dimension, position,
            candidate, spec, accessStageOwned);
    }

    private static StructureSessionSpec spec(UUID owner, StructureSessionAvailability availability) {
        return new StructureSessionSpec(ResourceLocation.parse("test:provider"),
            StructureSessionId.random(),
            new StructureInstanceKey(OVERWORLD, STRONGHOLD, BlockPos.ZERO),
            new StructureBounds(0, 0, 0, 16, 80, 16), owner,
            StructureOwnershipScope.PLAYER, StageId.parse("test:stronghold_access"),
            Optional.of(StageId.parse("test:stronghold_active")), false,
            StructureCleanupPolicy.REVOKE_ACCESS_ON_COMPLETED_EXIT, availability);
    }
}
