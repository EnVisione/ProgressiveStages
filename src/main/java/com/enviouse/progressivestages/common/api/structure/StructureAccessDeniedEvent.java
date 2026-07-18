package com.enviouse.progressivestages.common.api.structure;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.Event;

import java.util.Objects;
import java.util.Optional;

public final class StructureAccessDeniedEvent extends Event {
    private final StructureAccessRequest request;
    private final StructureAccessDecision decision;
    private final ResourceLocation providerId;

    public StructureAccessDeniedEvent(StructureAccessRequest request,
                                      StructureAccessDecision decision,
                                      ResourceLocation providerId) {
        this.request = Objects.requireNonNull(request, "request");
        this.decision = Objects.requireNonNull(decision, "decision");
        this.providerId = providerId;
    }

    public StructureAccessRequest getRequest() {
        return request;
    }

    public StructureAccessDecision getDecision() {
        return decision;
    }

    public Optional<ResourceLocation> getProviderId() {
        return Optional.ofNullable(providerId);
    }
}
