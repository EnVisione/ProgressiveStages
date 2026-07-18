package com.enviouse.progressivestages.common.api.structure;

import com.enviouse.progressivestages.common.api.StageId;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record StructureSessionSpec(ResourceLocation providerId, StructureSessionId sessionId,
                                   StructureInstanceKey instance, StructureBounds bounds,
                                   UUID assignmentOwner, StructureOwnershipScope ownershipScope,
                                   StageId accessStage, Optional<StageId> inProgressStage,
                                   boolean complete, StructureCleanupPolicy cleanupPolicy,
                                   StructureSessionAvailability availability) {
    public StructureSessionSpec {
        Objects.requireNonNull(providerId, "providerId");
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(instance, "instance");
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(assignmentOwner, "assignmentOwner");
        Objects.requireNonNull(ownershipScope, "ownershipScope");
        Objects.requireNonNull(accessStage, "accessStage");
        inProgressStage = inProgressStage == null ? Optional.empty() : inProgressStage;
        cleanupPolicy = cleanupPolicy == null
            ? StructureCleanupPolicy.REVOKE_ACCESS_ON_COMPLETED_EXIT : cleanupPolicy;
        availability = availability == null ? StructureSessionAvailability.AVAILABLE : availability;
    }
}
