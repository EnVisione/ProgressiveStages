package com.enviouse.progressivestages.common.rehaul.challenge;

import net.minecraft.resources.ResourceLocation;

import java.util.Map;

public interface ChallengeMeasureProvider {

    ResourceLocation id();

    default void validate(Map<String, Object> filters) {}

    double amount(ChallengeEvent event, Map<String, Object> filters);
}
