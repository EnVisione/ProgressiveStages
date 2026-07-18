package com.enviouse.progressivestages.common.rehaul.modifier;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

public record NumericTransform(ResourceLocation type, double add, double multiply,
                               double minimum, double maximum) {

    public NumericTransform {
        Objects.requireNonNull(type, "type");
        if (!Double.isFinite(add) || !Double.isFinite(multiply) || Double.isNaN(minimum)
                || Double.isNaN(maximum) || minimum > maximum) {
            throw new IllegalArgumentException("Numeric transform contains an invalid number");
        }
    }

    public double apply(double value) {
        return Math.max(minimum, Math.min(maximum, (value + add) * multiply));
    }
}
