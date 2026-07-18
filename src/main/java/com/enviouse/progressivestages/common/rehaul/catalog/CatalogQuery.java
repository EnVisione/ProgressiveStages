package com.enviouse.progressivestages.common.rehaul.catalog;

import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.Objects;

public record CatalogQuery(
        ResourceLocation catalogId,
        String fieldId,
        String prefixMode,
        String text,
        Map<String, String> filters,
        String sort,
        int pageSize,
        String cursor,
        long expectedRevision) {

    public CatalogQuery {
        Objects.requireNonNull(catalogId, "catalogId");
        fieldId = fieldId != null ? fieldId : "";
        prefixMode = prefixMode != null ? prefixMode.trim().toLowerCase(java.util.Locale.ROOT) : "id";
        text = text != null ? text.trim() : "";
        filters = filters == null ? Map.of() : Map.copyOf(filters);
        sort = sort != null ? sort : "relevance";
        if (pageSize < 1 || pageSize > 250) throw new IllegalArgumentException("Page size is outside the allowed range");
        cursor = cursor != null ? cursor : "";
        if (expectedRevision < 0) throw new IllegalArgumentException("Expected revision cannot be negative");
    }

    public static CatalogQuery firstPage(ResourceLocation catalogId, String text, String prefixMode, int pageSize) {
        return new CatalogQuery(catalogId, "", prefixMode, text, Map.of(), "relevance", pageSize, "", 0L);
    }
}
