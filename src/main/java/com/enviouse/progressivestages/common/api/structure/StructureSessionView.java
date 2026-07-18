package com.enviouse.progressivestages.common.api.structure;

import com.enviouse.progressivestages.common.api.StageId;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public record StructureSessionView(ResourceLocation providerId, StructureSessionId sessionId,
                                   StructureInstanceKey instance, StructureBounds bounds,
                                   UUID assignmentOwner, StructureOwnershipScope ownershipScope,
                                   StageId accessStage, Optional<StageId> inProgressStage,
                                   boolean complete, StructureCleanupPolicy cleanupPolicy,
                                   StructureSessionAvailability availability, long visitSequence,
                                   Set<UUID> participants, boolean exitPending) {
    public StructureSessionView {
        inProgressStage = inProgressStage == null ? Optional.empty() : inProgressStage;
        participants = participants == null ? Set.of() : Set.copyOf(participants);
    }
}
