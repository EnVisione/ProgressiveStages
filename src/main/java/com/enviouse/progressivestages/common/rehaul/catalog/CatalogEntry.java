package com.enviouse.progressivestages.common.rehaul.catalog;

import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record CatalogEntry(
        ResourceLocation catalogId,
        String key,
        ResourceLocation registryId,
        String namespace,
        String label,
        String translationKey,
        String sourceType,
        String modId,
        String modName,
        List<ResourceLocation> tags,
        Set<ResourceLocation> capabilities,
        Map<String, Object> metadata) {

    public CatalogEntry {
        Objects.requireNonNull(catalogId, "catalogId");
        key = requireText(key, "key");
        namespace = namespace != null ? namespace : namespaceOf(key);
        label = label != null && !label.isBlank() ? label : key;
        translationKey = translationKey != null ? translationKey : "";
        sourceType = sourceType != null && !sourceType.isBlank() ? sourceType : "unknown";
        modId = modId != null ? modId : "";
        modName = modName != null ? modName : "";
        tags = tags == null ? List.of() : List.copyOf(tags);
        capabilities = capabilities == null ? Set.of() : Set.copyOf(capabilities);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public static CatalogEntry registryEntry(ResourceLocation catalogId, ResourceLocation registryId,
                                             ResourceLocation key, List<ResourceLocation> tags,
                                             String sourceType) {
        return new CatalogEntry(catalogId, key.toString(), registryId, key.getNamespace(),
            key.toString(), "", sourceType, key.getNamespace(), "", tags, Set.of(), Map.of());
    }

    public String searchableText() {
        StringBuilder value = new StringBuilder(key).append(' ').append(label).append(' ')
            .append(translationKey).append(' ').append(namespace).append(' ').append(modId).append(' ')
            .append(modName);
        for (ResourceLocation tag : tags) value.append(' ').append(tag);
        return value.toString().toLowerCase(java.util.Locale.ROOT);
    }

    private static String namespaceOf(String key) {
        ResourceLocation parsed = ResourceLocation.tryParse(key);
        return parsed != null ? parsed.getNamespace() : "";
    }

    private static String requireText(String value, String name) {
        String normalized = Objects.requireNonNull(value, name).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(name + " cannot be blank");
        return normalized;
    }
}
