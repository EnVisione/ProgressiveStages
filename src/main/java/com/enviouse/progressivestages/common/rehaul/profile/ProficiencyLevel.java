package com.enviouse.progressivestages.common.rehaul.profile;

import com.enviouse.progressivestages.common.rehaul.modifier.NumericTransform;

import java.util.List;

public record ProficiencyLevel(String id, double minimum, AffinityEffect effect,
                               List<NumericTransform> transforms, int priority) {
    public ProficiencyLevel {
        if (id == null || id.isBlank() || !Double.isFinite(minimum)) throw new IllegalArgumentException("Proficiency level is invalid");
        effect = effect == null ? AffinityEffect.WEAKEN : effect;
        transforms = transforms == null ? List.of() : List.copyOf(transforms);
    }
}
