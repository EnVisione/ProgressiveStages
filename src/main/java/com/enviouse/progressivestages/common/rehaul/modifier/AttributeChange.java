package com.enviouse.progressivestages.common.rehaul.modifier;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

public record AttributeChange(ResourceLocation attribute, double amount,
                              AttributeOperation operation, StackingPolicy stacking,
                              double minimum, double maximum) {

    public AttributeChange {
        Objects.requireNonNull(attribute, "attribute");
        operation = operation == null ? AttributeOperation.ADD_VALUE : operation;
        stacking = stacking == null ? StackingPolicy.ADD : stacking;
        if (!Double.isFinite(amount) || Double.isNaN(minimum) || Double.isNaN(maximum) || minimum > maximum) {
            throw new IllegalArgumentException("Attribute change contains an invalid number");
        }
    }
}
