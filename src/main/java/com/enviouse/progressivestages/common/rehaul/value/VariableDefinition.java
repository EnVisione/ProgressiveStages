package com.enviouse.progressivestages.common.rehaul.value;

import com.enviouse.progressivestages.common.rehaul.condition.SubjectScope;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.Set;

public record VariableDefinition(ResourceLocation id, VariableType type, SubjectScope scope,
                                 Object defaultValue, double minimum, double maximum,
                                 boolean persistent, boolean syncVisible,
                                 Set<String> mutationPermissions, String resetPolicy) {

    public VariableDefinition {
        Objects.requireNonNull(id, "id");
        type = type == null ? VariableType.DECIMAL : type;
        scope = scope == null ? SubjectScope.PLAYER : scope;
        if (Double.isNaN(minimum) || Double.isNaN(maximum) || minimum > maximum) {
            throw new IllegalArgumentException("Variable bounds are invalid");
        }
        mutationPermissions = mutationPermissions == null ? Set.of() : Set.copyOf(mutationPermissions);
        resetPolicy = resetPolicy == null ? "never" : resetPolicy;
    }
}
