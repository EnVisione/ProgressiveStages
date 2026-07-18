package com.enviouse.progressivestages.common.rehaul.schema;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class EditorSchemaRegistry {

    private static final EditorSchemaRegistry INSTANCE = createBuiltIns();

    private volatile Map<ResourceLocation, EditorFieldSchema> fields;

    private EditorSchemaRegistry(Map<ResourceLocation, EditorFieldSchema> fields) {
        this.fields = Map.copyOf(fields);
    }

    public static EditorSchemaRegistry get() {
        return INSTANCE;
    }

    public synchronized void register(EditorFieldSchema field) {
        Map<ResourceLocation, EditorFieldSchema> copy = new LinkedHashMap<>(fields);
        EditorFieldSchema previous = copy.putIfAbsent(field.id(), field);
        if (previous != null) throw new IllegalArgumentException("Duplicate editor field schema. " + field.id());
        fields = Map.copyOf(copy);
    }

    public Optional<EditorFieldSchema> find(ResourceLocation id) {
        return Optional.ofNullable(fields.get(id));
    }

    public List<EditorFieldSchema> all() {
        return fields.values().stream().sorted(Comparator.comparing(field -> field.id().toString())).toList();
    }

    public List<EditorFieldSchema> forFile(String file) {
        return fields.values().stream().filter(field -> field.file().equals(file))
            .sorted(Comparator.comparing(EditorFieldSchema::path)).toList();
    }

    public List<String> validateCoverage(Collection<String> requiredPaths) {
        List<String> missing = new ArrayList<>();
        for (String required : requiredPaths) {
            boolean found = fields.values().stream().anyMatch(field ->
                (field.file() + ":" + field.path()).equals(required));
            if (!found) missing.add(required);
        }
        return List.copyOf(missing);
    }

    private static EditorSchemaRegistry createBuiltIns() {
        Map<ResourceLocation, EditorFieldSchema> fields = new LinkedHashMap<>();
        BuiltinEditorSchemas.populate(fields::put);
        return new EditorSchemaRegistry(fields);
    }
}
