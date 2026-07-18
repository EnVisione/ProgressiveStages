package com.enviouse.progressivestages.common.rehaul.extension;

import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;

public record ExtensionArgument(String name, String type, boolean required, Object defaultValue,
                                ResourceLocation catalog, List<String> choices, String help,
                                Map<String, Object> constraints) {
    public ExtensionArgument {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Extension argument name cannot be blank");
        type = type == null || type.isBlank() ? "string" : type;
        choices = choices == null ? List.of() : List.copyOf(choices);
        help = help == null ? "" : help;
        constraints = constraints == null ? Map.of() : Map.copyOf(constraints);
    }
}
