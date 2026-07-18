package com.enviouse.progressivestages.common.api.structure;

import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;
import java.util.Optional;

public interface StructureContextProvider {
    StructureAccessDecision evaluate(StructureAccessRequest request);

    Collection<StructureSessionSpec> sessionsFor(ServerPlayer player);

    Optional<StructureSessionSpec> session(StructureSessionId sessionId);
}
