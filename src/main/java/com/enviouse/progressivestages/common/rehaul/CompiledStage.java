package com.enviouse.progressivestages.common.rehaul;

import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.config.StageDefinition;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public record CompiledStage(
        StageId id,
        String displayName,
        String description,
        int priority,
        int schemaVersion,
        String sourceId,
        List<CompiledRule> rules,
        CompiledProgression progression,
        Map<String, Object> metadata,
        StageDefinition compatibilityView,
        ConfigProvenance provenance) {

    public CompiledStage {
        Objects.requireNonNull(id, "id");
        displayName = displayName != null ? displayName : id.getPath();
        description = description != null ? description : "";
        if (schemaVersion < 1) throw new IllegalArgumentException("Schema version must be positive");
        sourceId = sourceId != null && !sourceId.isBlank() ? sourceId : id.toString();
        rules = rules == null ? List.of() : List.copyOf(rules);
        progression = progression == null ? CompiledProgression.EMPTY : progression;
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        Objects.requireNonNull(compatibilityView, "compatibilityView");
        Objects.requireNonNull(provenance, "provenance");
    }

    public CompiledStage(StageId id, String displayName, String description, int priority,
                         int schemaVersion, String sourceId, List<CompiledRule> rules,
                         Map<String, Object> metadata, StageDefinition compatibilityView,
                         ConfigProvenance provenance) {
        this(id, displayName, description, priority, schemaVersion, sourceId, rules,
            CompiledProgression.EMPTY, metadata, compatibilityView, provenance);
    }
}
