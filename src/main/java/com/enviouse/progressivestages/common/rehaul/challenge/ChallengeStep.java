package com.enviouse.progressivestages.common.rehaul.challenge;

import com.enviouse.progressivestages.common.rehaul.ConditionNode;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

public record ChallengeStep(ResourceLocation id, ConditionNode condition, long timeoutMillis,
                            boolean resetOnFailure) {

    public ChallengeStep {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(condition, "condition");
        if (timeoutMillis < 0) throw new IllegalArgumentException("Challenge step timeout cannot be negative");
    }
}
