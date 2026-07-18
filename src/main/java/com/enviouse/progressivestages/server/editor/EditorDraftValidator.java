package com.enviouse.progressivestages.server.editor;

import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.config.StageDefinition;
import com.enviouse.progressivestages.common.stage.StageOrder;
import com.enviouse.progressivestages.server.loader.Schema4StageCompiler;
import com.enviouse.progressivestages.server.loader.StageFileParser;
import com.enviouse.progressivestages.server.loader.StagePackageDiscovery;
import com.enviouse.progressivestages.server.loader.StagePackageParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class EditorDraftValidator {
    private EditorDraftValidator() {}

    static DraftValidation validate(Map<String, String> files, long revision) {
        Path temporary = null;
        try {
            temporary = Files.createTempDirectory("progressivestages-editor-validation-");
            for (Map.Entry<String, String> file : files.entrySet()) {
                String path = EditorPaths.normalize(file.getKey());
                if (path.equals("progressivestages.toml")) {
                    new com.electronwill.nightconfig.toml.TomlParser().parse(file.getValue());
                    continue;
                }
                if (!path.startsWith("stages/")) throw new IllegalArgumentException("Draft TOML is outside the supported config paths");
                Path target = temporary.resolve(path.substring("stages/".length())).normalize();
                if (!target.startsWith(temporary)) throw new IllegalArgumentException("Draft path escapes validation root");
                Files.createDirectories(target.getParent());
                Files.writeString(target, file.getValue());
            }
            StagePackageDiscovery.DiscoveryResult discovery = StagePackageDiscovery.discover(temporary);
            List<String> errors = new ArrayList<>(discovery.errors());
            Map<StageId, StageDefinition> definitions = new LinkedHashMap<>();
            for (var source : discovery.packages()) {
                var parsed = StagePackageParser.parse(source);
                if (!parsed.isSuccess()) { errors.add(source.root().getFileName() + ". " + parsed.getErrorMessage()); continue; }
                StageDefinition definition = parsed.getStageDefinition();
                try { Schema4StageCompiler.compile(definition, parsed.getSourceConfig(), source.sourceId(), 0); }
                catch (RuntimeException error) { errors.add(definition.getId() + ". " + error.getMessage()); }
                if (definitions.putIfAbsent(definition.getId(), definition) != null) errors.add("Duplicate stage id. " + definition.getId());
            }
            for (Path source : discovery.legacyFiles()) {
                var parsed = StageFileParser.parseWithErrors(source);
                if (!parsed.isSuccess()) { errors.add(source.getFileName() + ". " + parsed.getErrorMessage()); continue; }
                StageDefinition definition = parsed.getStageDefinition();
                if (definitions.putIfAbsent(definition.getId(), definition) != null) errors.add("Duplicate stage id. " + definition.getId());
            }
            errors.addAll(StageOrder.validateDefinitions(definitions.values()));
            Path validationRoot = temporary;
            List<String> warnings = discovery.ignoredFiles().stream()
                .map(path -> "Ignored helper TOML. " + validationRoot.relativize(path)).toList();
            return new DraftValidation(errors.isEmpty(), errors, warnings, definitions.size(), revision);
        } catch (IOException | RuntimeException error) {
            return new DraftValidation(false, List.of(error.getMessage()), List.of(), 0, revision);
        } finally {
            if (temporary != null) {
                try (var paths = Files.walk(temporary)) {
                    for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
                } catch (IOException ignored) {}
            }
        }
    }
}
