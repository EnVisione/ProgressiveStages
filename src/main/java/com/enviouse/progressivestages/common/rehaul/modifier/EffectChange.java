package com.enviouse.progressivestages.common.rehaul.modifier;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

public record EffectChange(ResourceLocation effect, int amplifier, int durationTicks,
                           boolean particles, boolean icon, String refreshPolicy) {

    public EffectChange {
        Objects.requireNonNull(effect, "effect");
        if (amplifier < 0 || durationTicks < 1) throw new IllegalArgumentException("Effect values are invalid");
        refreshPolicy = refreshPolicy == null ? "refresh" : refreshPolicy;
    }
}
