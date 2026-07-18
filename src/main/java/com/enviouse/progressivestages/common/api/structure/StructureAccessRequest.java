package com.enviouse.progressivestages.common.api.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;
import java.util.Optional;

public record StructureAccessRequest(ServerPlayer player, ServerLevel level, BlockPos position,
                                     StructureAction action,
                                     Optional<StructureInstanceKey> candidateInstance) {
    public StructureAccessRequest {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(action, "action");
        position = position.immutable();
        candidateInstance = candidateInstance == null ? Optional.empty() : candidateInstance;
    }
}
