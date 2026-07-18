package com.enviouse.progressivestages.common.rehaul.schema;

import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public record EditorFieldSchema(
        ResourceLocation id,
        String file,
        String path,
        String label,
        String help,
        SchemaValueType type,
        Object defaultValue,
        boolean required,
        ResourceLocation catalogId,
        Set<String> prefixModes,
        List<String> enumValues,
        Set<ResourceLocation> capabilities,
        RestartRequirement restartRequirement,
        Map<String, Object> controlHints) {

    public EditorFieldSchema {
        Objects.requireNonNull(id, "id");
        file = requireText(file, "file");
        path = requireText(path, "path");
        label = requireText(label, "label");
        help = requireText(help, "help");
        Objects.requireNonNull(type, "type");
        prefixModes = prefixModes == null ? Set.of() : Set.copyOf(prefixModes);
        enumValues = enumValues == null ? List.of() : List.copyOf(enumValues);
        capabilities = capabilities == null ? Set.of() : Set.copyOf(capabilities);
        restartRequirement = restartRequirement != null ? restartRequirement : RestartRequirement.LIVE_APPLY;
        controlHints = controlHints == null ? Map.of() : Map.copyOf(controlHints);
        if (type == SchemaValueType.ENUM && enumValues.isEmpty()) {
            throw new IllegalArgumentException("An enum field requires values");
        }
    }

    public Optional<ResourceLocation> catalog() {
        return Optional.ofNullable(catalogId);
    }

    private static String requireText(String value, String name) {
        String normalized = Objects.requireNonNull(value, name).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(name + " cannot be blank");
        return normalized;
    }
}
