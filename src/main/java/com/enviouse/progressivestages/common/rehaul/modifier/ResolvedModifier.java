package com.enviouse.progressivestages.common.rehaul.modifier;

import net.minecraft.resources.ResourceLocation;

import java.util.List;

public record ResolvedModifier(ResourceLocation stableId, ResourceLocation sourceRule,
                               int multiplier, int priority, List<AttributeChange> attributes,
                               List<EffectChange> effects, List<NumericTransform> transforms) {

    public ResolvedModifier {
        attributes = attributes == null ? List.of() : List.copyOf(attributes);
        effects = effects == null ? List.of() : List.copyOf(effects);
        transforms = transforms == null ? List.of() : List.copyOf(transforms);
    }
}
