package com.enviouse.progressivestages.common.rehaul.challenge;

import net.minecraft.resources.ResourceLocation;

import java.util.Map;

public record ChallengeEvent(String subject, ResourceLocation type, double amount,
                             long timestamp, Map<String, Object> properties) {

    public ChallengeEvent {
        if (!Double.isFinite(amount)) throw new IllegalArgumentException("Challenge event amount must be finite");
        properties = properties == null ? Map.of() : Map.copyOf(properties);
    }
}
