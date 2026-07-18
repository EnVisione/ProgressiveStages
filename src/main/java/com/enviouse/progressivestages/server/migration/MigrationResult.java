package com.enviouse.progressivestages.server.migration;

import java.nio.file.Path;
import java.util.List;

public record MigrationResult(boolean success, String migrationId, Path backupDirectory,
                              List<Path> writtenFiles, String code, String explanation) {
    public MigrationResult {
        migrationId = migrationId == null ? "" : migrationId;
        writtenFiles = writtenFiles == null ? List.of() : List.copyOf(writtenFiles);
        code = code == null ? "" : code;
        explanation = explanation == null ? "" : explanation;
    }
}
