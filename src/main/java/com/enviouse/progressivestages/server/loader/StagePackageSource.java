package com.enviouse.progressivestages.server.loader;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record StagePackageSource(
        Path root,
        Path identityFile,
        Optional<Path> rulesFile,
        Optional<Path> progressionFile,
        List<Path> additionalRuleFiles,
        List<Path> additionalProgressionFiles,
        String sourceId) {

    public StagePackageSource {
        root = Objects.requireNonNull(root, "root").toAbsolutePath().normalize();
        identityFile = Objects.requireNonNull(identityFile, "identityFile").toAbsolutePath().normalize();
        rulesFile = rulesFile != null ? rulesFile.map(path -> path.toAbsolutePath().normalize()) : Optional.empty();
        progressionFile = progressionFile != null
            ? progressionFile.map(path -> path.toAbsolutePath().normalize()) : Optional.empty();
        additionalRuleFiles = normalize(additionalRuleFiles);
        additionalProgressionFiles = normalize(additionalProgressionFiles);
        sourceId = Objects.requireNonNull(sourceId, "sourceId").trim();
        if (sourceId.isEmpty()) throw new IllegalArgumentException("Source id cannot be blank");
    }

    public List<Path> allFiles() {
        java.util.ArrayList<Path> files = new java.util.ArrayList<>();
        files.add(identityFile);
        rulesFile.ifPresent(files::add);
        files.addAll(additionalRuleFiles);
        progressionFile.ifPresent(files::add);
        files.addAll(additionalProgressionFiles);
        return List.copyOf(files);
    }

    private static List<Path> normalize(List<Path> paths) {
        if (paths == null) return List.of();
        return paths.stream().map(path -> path.toAbsolutePath().normalize()).toList();
    }
}
