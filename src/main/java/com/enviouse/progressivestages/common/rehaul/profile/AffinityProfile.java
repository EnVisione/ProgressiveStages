package com.enviouse.progressivestages.common.rehaul.profile;

import com.enviouse.progressivestages.common.rehaul.SelectorSpec;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

public record AffinityProfile(ResourceLocation id, List<SelectorSpec> content,
                              String proficiencyValue, List<ProficiencyLevel> levels) {
    public AffinityProfile {
        content = content == null ? List.of() : List.copyOf(content);
        proficiencyValue = proficiencyValue == null ? "" : proficiencyValue;
        levels = levels == null ? List.of() : levels.stream()
            .sorted(java.util.Comparator.comparingDouble(ProficiencyLevel::minimum)).toList();
    }
}
