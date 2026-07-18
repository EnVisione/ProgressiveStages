package com.enviouse.progressivestages.common.rehaul.profile;

import com.enviouse.progressivestages.common.rehaul.modifier.NumericTransform;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

public record AffinityDecision(ResourceLocation profile, String level, AffinityEffect effect,
                               int priority, List<NumericTransform> transforms) {
    public AffinityDecision {
        transforms = transforms == null ? List.of() : List.copyOf(transforms);
    }
}
