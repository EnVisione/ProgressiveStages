package com.enviouse.progressivestages.common.rehaul.challenge;

import net.minecraft.resources.ResourceLocation;

import java.util.Map;

public record ChallengeSessionView(String subject, ResourceLocation challenge, ChallengeStatus status,
                                   long startedAt, long endedAt, int currentStep, int attempts,
                                   Map<ResourceLocation, Double> budgetValues, String explanation) {

    public ChallengeSessionView {
        budgetValues = budgetValues == null ? Map.of() : Map.copyOf(budgetValues);
        explanation = explanation == null ? "" : explanation;
    }
}
