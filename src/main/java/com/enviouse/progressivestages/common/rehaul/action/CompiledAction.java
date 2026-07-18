package com.enviouse.progressivestages.common.rehaul.action;

import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.Objects;

public record CompiledAction(ResourceLocation id, ResourceLocation providerId,
                             Map<String, Object> arguments, FailurePolicy failurePolicy,
                             int retries, boolean compensationRequired) {

    public CompiledAction {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(providerId, "providerId");
        arguments = arguments == null ? Map.of() : Map.copyOf(arguments);
        failurePolicy = failurePolicy == null ? FailurePolicy.ROLLBACK : failurePolicy;
        if (retries < 0 || retries > 10) throw new IllegalArgumentException("Action retry count is outside the allowed range");
    }
}
