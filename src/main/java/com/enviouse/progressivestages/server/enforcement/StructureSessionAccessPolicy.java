package com.enviouse.progressivestages.server.enforcement;

import com.enviouse.progressivestages.common.api.structure.StructureAccessDecision;
import com.enviouse.progressivestages.common.api.structure.StructureInstanceKey;
import com.enviouse.progressivestages.common.api.structure.StructureSessionAvailability;
import com.enviouse.progressivestages.common.api.structure.StructureSessionSpec;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Optional;
import java.util.UUID;

final class StructureSessionAccessPolicy {
    private StructureSessionAccessPolicy() {}

    static Optional<StructureAccessDecision.Reason> validatePermit(
            UUID effectiveOwner, ResourceKey<Level> currentDimension, BlockPos position,
            Optional<StructureInstanceKey> candidate, StructureSessionSpec spec,
            boolean accessStageOwned) {
        if (!effectiveOwner.equals(spec.assignmentOwner())) {
            return Optional.of(StructureAccessDecision.Reason.WRONG_OWNER);
        }
        if (!spec.instance().dimension().equals(currentDimension)
                || !spec.bounds().contains(position)
                || candidate.filter(instance -> !instance.equals(spec.instance())).isPresent()) {
            return Optional.of(StructureAccessDecision.Reason.WRONG_INSTANCE);
        }
        if (spec.availability() != StructureSessionAvailability.AVAILABLE) {
            return Optional.of(StructureAccessDecision.Reason.UNAVAILABLE);
        }
        if (!accessStageOwned) {
            return Optional.of(StructureAccessDecision.Reason.MISSING_ACCESS_STAGE);
        }
        return Optional.empty();
    }
}
