package com.enviouse.progressivestages.server.migration;

import com.enviouse.progressivestages.common.api.StageId;

import java.nio.file.Path;
import java.util.List;

public record MigrationScanEntry(StageId stage, Path source, String checksum, boolean valid,
                                 List<String> warnings, List<String> errors) {
    public MigrationScanEntry {
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        errors = errors == null ? List.of() : List.copyOf(errors);
    }
}
