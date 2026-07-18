package com.enviouse.progressivestages.server.loader;

import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.toml.TomlParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class StagePackageParser {

    private static final TomlParser PARSER = new TomlParser();

    private static final Set<String> IDENTITY_SECTIONS = Set.of("schema", "package", "stage", "display", "dependencies", "metadata");
    private static final Set<String> PROGRESSION_SECTIONS = Set.of(
        "triggers", "grants", "revoke", "revokes", "duration", "cost", "unlock", "rewards",
        "challenges", "sequences", "variables", "formulas", "states", "transitions", "actions",
        "templates", "profiles", "currencies", "counters");

    private StagePackageParser() {}

    public static StagePackageSource inspect(Path stagesRoot, Path packageRoot) throws IOException {
        Path root = packageRoot.toAbsolutePath().normalize();
        Path identity = root.resolve("stage.toml");
        if (!Files.isRegularFile(identity)) throw new IllegalArgumentException("Missing stage.toml");
        Config identityConfig = parse(identity);
        validateSchema(identityConfig);
        List<Path> ruleIncludes = includes(identityConfig, root, "rules_includes");
        List<Path> progressionIncludes = includes(identityConfig, root, "progression_includes");
        Path normalizedStagesRoot = stagesRoot.toAbsolutePath().normalize();
        String sourceId = "config:" + normalizedStagesRoot.relativize(root).toString().replace('\\', '/');
        return new StagePackageSource(
            root,
            identity,
            optionalFile(root.resolve("rules.toml")),
            optionalFile(root.resolve("progression.toml")),
            ruleIncludes,
            progressionIncludes,
            sourceId);
    }

    public static StageFileParser.ParseResult parse(StagePackageSource source) {
        try {
            Config merged = Config.inMemory();
            merge(merged, parseAndValidate(source.identityFile(), FileRole.IDENTITY));
            if (source.rulesFile().isPresent()) merge(merged,
                parseAndValidate(source.rulesFile().orElseThrow(), FileRole.RULES));
            for (Path include : source.additionalRuleFiles()) {
                merge(merged, parseAndValidate(include, FileRole.RULES));
            }
            if (source.progressionFile().isPresent()) merge(merged,
                parseAndValidate(source.progressionFile().orElseThrow(), FileRole.PROGRESSION));
            for (Path include : source.additionalProgressionFiles()) {
                merge(merged, parseAndValidate(include, FileRole.PROGRESSION));
            }
            return StageFileParser.parseConfig(merged, source.identityFile().getFileName().toString(),
                source.sourceId(), true);
        } catch (IOException | RuntimeException error) {
            String message = error.getMessage() != null ? error.getMessage() : error.getClass().getSimpleName();
            return StageFileParser.ParseResult.validationError("Invalid stage package. " + message);
        }
    }

    public static StageFileParser.ParseResult parseContents(
            String sourceId,
            String identityName,
            String identityContent,
            String rulesName,
            String rulesContent,
            String progressionName,
            String progressionContent) {
        try {
            Config identity = PARSER.parse(identityContent);
            validateSchema(identity);
            validateOwnership(identity, Path.of(identityName), FileRole.IDENTITY);
            Config merged = Config.copy(identity);
            if (rulesContent != null) {
                Config rules = PARSER.parse(rulesContent);
                validateOwnership(rules, Path.of(rulesName), FileRole.RULES);
                merge(merged, rules);
            }
            if (progressionContent != null) {
                Config progression = PARSER.parse(progressionContent);
                validateOwnership(progression, Path.of(progressionName), FileRole.PROGRESSION);
                merge(merged, progression);
            }
            return StageFileParser.parseConfig(merged, identityName, sourceId, true);
        } catch (RuntimeException error) {
            String message = error.getMessage() != null ? error.getMessage() : error.getClass().getSimpleName();
            return StageFileParser.ParseResult.validationError("Invalid stage package. " + message);
        }
    }

    private static Config parseAndValidate(Path file, FileRole role) throws IOException {
        Config parsed = parse(file);
        validateOwnership(parsed, file, role);
        return parsed;
    }

    private static Config parse(Path file) throws IOException {
        try (var reader = Files.newBufferedReader(file)) {
            return PARSER.parse(reader);
        }
    }

    private static void validateSchema(Config identity) {
        Config schema = identity.get("schema");
        if (schema == null) throw new IllegalArgumentException("stage.toml is missing [schema]");
        Number version = schema.get("version");
        if (version == null || version.intValue() != 4) {
            throw new IllegalArgumentException("stage.toml must declare [schema] version = 4");
        }
        if (!(identity.get("stage") instanceof Config)) {
            throw new IllegalArgumentException("stage.toml is missing [stage]");
        }
    }

    private static void validateOwnership(Config config, Path file, FileRole role) {
        for (var entry : config.entrySet()) {
            String key = entry.getKey();
            String root = key.toLowerCase(Locale.ROOT);
            boolean namespacedExtension = root.indexOf(':') > 0;
            if (namespacedExtension) continue;
            if (role == FileRole.IDENTITY && !IDENTITY_SECTIONS.contains(root)) {
                throw new IllegalArgumentException(file.getFileName() + " contains rule or progression section " + key);
            }
            if (role == FileRole.RULES && (IDENTITY_SECTIONS.contains(root) || PROGRESSION_SECTIONS.contains(root))) {
                throw new IllegalArgumentException(file.getFileName() + " contains section " + key + " in the wrong file");
            }
            if (role == FileRole.PROGRESSION && IDENTITY_SECTIONS.contains(root)) {
                throw new IllegalArgumentException(file.getFileName() + " contains identity section " + key);
            }
            if (role == FileRole.PROGRESSION && !PROGRESSION_SECTIONS.contains(root) && !namespacedExtension) {
                throw new IllegalArgumentException(file.getFileName() + " contains rule section " + key);
            }
        }
    }

    private static List<Path> includes(Config identity, Path root, String key) {
        Config packageSection = identity.get("package");
        if (packageSection == null) return List.of();
        Object raw = packageSection.get(key);
        if (raw == null) return List.of();
        List<?> values = raw instanceof List<?> list ? list : List.of(raw);
        List<Path> resolved = new ArrayList<>();
        Set<Path> seen = new LinkedHashSet<>();
        for (Object value : values) {
            if (!(value instanceof String text) || text.isBlank()) {
                throw new IllegalArgumentException(key + " must contain paths");
            }
            Path path = root.resolve(text).normalize();
            if (!path.startsWith(root) || path.isAbsolute() && !path.startsWith(root)) {
                throw new IllegalArgumentException("Include escapes the stage package. " + text);
            }
            if (!path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".toml")) {
                throw new IllegalArgumentException("Include must be a TOML file. " + text);
            }
            if (!Files.isRegularFile(path)) throw new IllegalArgumentException("Include does not exist. " + text);
            if (!seen.add(path)) throw new IllegalArgumentException("Duplicate include. " + text);
            resolved.add(path);
        }
        return List.copyOf(resolved);
    }

    private static void merge(Config target, Config source) {
        for (var entry : source.entrySet()) {
            String key = entry.getKey();
            Object incoming = entry.getValue();
            Object current = target.getRaw(key);
            target.set(key, mergeValue(target, current, incoming));
        }
    }

    private static Object mergeValue(Config parent, Object current, Object incoming) {
        if (current instanceof Config currentConfig && incoming instanceof Config incomingConfig) {
            Config merged = Config.copy(currentConfig);
            merge(merged, incomingConfig);
            return merged;
        }
        if (current instanceof List<?> currentList && incoming instanceof List<?> incomingList) {
            List<Object> merged = new ArrayList<>(currentList);
            merged.addAll(incomingList);
            return List.copyOf(merged);
        }
        return copyValue(parent, incoming);
    }

    private static Object copyValue(Config parent, Object value) {
        if (value instanceof Config config) return Config.copy(config);
        if (value instanceof List<?> list) return List.copyOf(list);
        return value;
    }

    private static java.util.Optional<Path> optionalFile(Path path) {
        return Files.isRegularFile(path) ? java.util.Optional.of(path) : java.util.Optional.empty();
    }

    private enum FileRole {
        IDENTITY,
        RULES,
        PROGRESSION
    }
}
