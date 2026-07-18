package com.enviouse.progressivestages.common.rehaul;

import java.util.Objects;

public record ConfigProvenance(
        String sourceId,
        String file,
        String section,
        String field,
        int schemaVersion,
        boolean translatedLegacy) {

    public ConfigProvenance {
        sourceId = normalize(sourceId, "unknown");
        file = normalize(file, "unknown");
        section = normalize(section, "");
        field = normalize(field, "");
        if (schemaVersion < 1) throw new IllegalArgumentException("Schema version must be positive");
    }

    public static ConfigProvenance legacy(String file, String section, String field) {
        return new ConfigProvenance(file, file, section, field, 3, true);
    }

    public static ConfigProvenance legacy(String sourceId, String file, String section, String field) {
        return new ConfigProvenance(sourceId, file, section, field, 3, true);
    }

    public static ConfigProvenance packageField(String sourceId, String file, String section, String field) {
        return new ConfigProvenance(sourceId, file, section, field, 4, false);
    }

    public ConfigProvenance child(String nextSection, String nextField) {
        return new ConfigProvenance(sourceId, file, nextSection, nextField, schemaVersion, translatedLegacy);
    }

    private static String normalize(String value, String fallback) {
        String normalized = Objects.toString(value, fallback).trim();
        return normalized.isEmpty() ? fallback : normalized;
    }
}
