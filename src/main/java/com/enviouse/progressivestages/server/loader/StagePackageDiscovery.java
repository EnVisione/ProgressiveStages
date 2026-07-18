package com.enviouse.progressivestages.server.loader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public final class StagePackageDiscovery {

    private static final Pattern STAGE_HEADER = Pattern.compile("(?m)^\\s*\\[stage]\\s*(?:#.*)?$");

    private StagePackageDiscovery() {}

    public static DiscoveryResult discover(Path stagesRoot) {
        if (stagesRoot == null || !Files.isDirectory(stagesRoot)) {
            return new DiscoveryResult(List.of(), List.of(), List.of(), List.of());
        }
        Path normalizedRoot = stagesRoot.toAbsolutePath().normalize();
        List<Path> allTomlFiles;
        try (var stream = Files.walk(normalizedRoot)) {
            allTomlFiles = stream.filter(Files::isRegularFile)
                .filter(StagePackageDiscovery::isToml)
                .sorted(Comparator.comparing(path -> normalizedRoot.relativize(path).toString()))
                .toList();
        } catch (IOException error) {
            return new DiscoveryResult(List.of(), List.of(), List.of(),
                List.of("Could not scan the stages directory. " + error.getMessage()));
        }
        List<Path> hiddenFiles = allTomlFiles.stream()
            .filter(path -> hasHiddenSegment(normalizedRoot.relativize(path))).toList();
        List<Path> tomlFiles = allTomlFiles.stream()
            .filter(path -> !hasHiddenSegment(normalizedRoot.relativize(path))).toList();

        List<Path> packageRoots = tomlFiles.stream()
            .filter(path -> path.getFileName().toString().equalsIgnoreCase("stage.toml"))
            .map(Path::getParent)
            .sorted(Comparator.comparing(path -> normalizedRoot.relativize(path).toString()))
            .toList();

        List<StagePackageSource> packages = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        for (Path packageRoot : packageRoots) {
            try {
                packages.add(StagePackageParser.inspect(normalizedRoot, packageRoot));
            } catch (IOException | IllegalArgumentException error) {
                errors.add(normalizedRoot.relativize(packageRoot) + ". " + error.getMessage());
            }
        }

        List<Path> legacy = new ArrayList<>();
        List<Path> ignored = new ArrayList<>(hiddenFiles);
        for (Path file : tomlFiles) {
            if (file.getFileName().toString().equalsIgnoreCase("triggers.toml")) {
                ignored.add(file);
                continue;
            }
            boolean belongsToPackage = packageRoots.stream().anyMatch(root -> file.startsWith(root));
            if (belongsToPackage) continue;
            try {
                if (STAGE_HEADER.matcher(Files.readString(file)).find()) legacy.add(file);
                else ignored.add(file);
            } catch (IOException error) {
                errors.add(normalizedRoot.relativize(file) + ". " + error.getMessage());
            }
        }
        return new DiscoveryResult(packages, legacy, ignored, errors);
    }

    private static boolean isToml(Path path) {
        return path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".toml");
    }

    private static boolean hasHiddenSegment(Path path) {
        for (Path segment : path) if (segment.toString().startsWith(".")) return true;
        return false;
    }

    public record DiscoveryResult(
            List<StagePackageSource> packages,
            List<Path> legacyFiles,
            List<Path> ignoredFiles,
            List<String> errors) {
        public DiscoveryResult {
            packages = List.copyOf(packages);
            legacyFiles = List.copyOf(legacyFiles);
            ignoredFiles = List.copyOf(ignoredFiles);
            errors = List.copyOf(errors);
        }

        public boolean isValid() {
            return errors.isEmpty();
        }
    }
}
