package com.enviouse.progressivestages.server.migration;

import com.enviouse.progressivestages.common.api.StageId;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public record MigrationPlan(StageId stage, Path source, Path targetDirectory, String sourceChecksum,
                            Map<String, String> generatedFiles, List<String> mappings,
                            List<String> warnings, String semanticFingerprint) {
    public MigrationPlan {
        generatedFiles = generatedFiles == null ? Map.of() : Map.copyOf(generatedFiles);
        mappings = mappings == null ? List.of() : List.copyOf(mappings);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
