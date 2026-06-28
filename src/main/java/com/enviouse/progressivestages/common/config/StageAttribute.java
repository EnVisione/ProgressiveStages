package com.enviouse.progressivestages.common.config;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

import java.util.Locale;

/**
 * v2.4: one entry of a stage's {@code [attribute]} section — an attribute modifier applied to a
 * player while their team owns the stage (added on grant/login, removed on revoke). Works with any
 * vanilla or modded attribute id (e.g. {@code minecraft:generic.max_health},
 * {@code minecraft:generic.scale}, {@code minecraft:generic.movement_speed}).
 */
public record StageAttribute(ResourceLocation attribute, AttributeModifier.Operation operation, double amount) {

    /** Parse the operation keyword; defaults to ADD_VALUE. */
    public static AttributeModifier.Operation parseOperation(String s) {
        if (s == null) return AttributeModifier.Operation.ADD_VALUE;
        return switch (s.trim().toLowerCase(Locale.ROOT)) {
            case "multiply_base", "add_multiplied_base", "base" -> AttributeModifier.Operation.ADD_MULTIPLIED_BASE;
            case "multiply", "multiply_total", "add_multiplied_total", "total" -> AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL;
            default -> AttributeModifier.Operation.ADD_VALUE; // "add" / "add_value" / anything else
        };
    }
}
