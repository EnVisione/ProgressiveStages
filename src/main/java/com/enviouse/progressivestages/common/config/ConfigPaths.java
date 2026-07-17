package com.enviouse.progressivestages.common.config;

import com.enviouse.progressivestages.common.util.Constants;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashSet;
import java.util.Set;

/** Central source of truth for the v3.0 config hierarchy and its one-time migration. */
public final class ConfigPaths {

    private ConfigPaths() {}

    public static Path rootDirectory() {
        return FMLPaths.CONFIGDIR.get().resolve(Constants.CONFIG_DIRECTORY);
    }

    public static Path stagesDirectory() {
        return rootDirectory().resolve(Constants.STAGES_DIRECTORY);
    }

    public static Path mainConfig() {
        return rootDirectory().resolve("progressivestages.toml");
    }

    /**
     * Create the hierarchy and migrate both legacy layouts without overwriting a v3.0 file.
     * Handles Linux's old case-sensitive {@code ProgressiveStages/} directory and Windows where
     * that directory is the same path as the new lower-case root.
     */
    public static void prepareAndMigrate(Logger logger) {
        Path configBase = FMLPaths.CONFIGDIR.get();
        Path root = rootDirectory();
        Path stages = stagesDirectory();
        try {
            Files.createDirectories(stages);
        } catch (IOException e) {
            logger.error("[ProgressiveStages] Failed to create config hierarchy {}: {}", stages, e.getMessage());
            return;
        }

        migrateFile(configBase.resolve("progressivestages.toml"), mainConfig(), logger, "main config");

        Set<Path> legacyRoots = new LinkedHashSet<>();
        legacyRoots.add(configBase.resolve("ProgressiveStages"));
        legacyRoots.add(root); // same directory as the legacy root on case-insensitive filesystems
        for (Path legacyRoot : legacyRoots) migrateLooseStageFiles(legacyRoot, stages, logger);
    }

    private static void migrateLooseStageFiles(Path sourceDirectory, Path targetDirectory, Logger logger) {
        if (!Files.isDirectory(sourceDirectory)) return;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(sourceDirectory, "*.toml")) {
            for (Path source : stream) {
                // The v3 main config belongs at the root; every other loose TOML is a legacy stage.
                if (samePath(source, mainConfig())) continue;
                Path target = targetDirectory.resolve(source.getFileName().toString());
                migrateFile(source, target, logger, "stage file");
            }
        } catch (IOException e) {
            logger.warn("[ProgressiveStages] Could not scan legacy stage directory {}: {}",
                sourceDirectory, e.getMessage());
        }
    }

    private static void migrateFile(Path source, Path target, Logger logger, String label) {
        if (!Files.isRegularFile(source) || samePath(source, target)) return;
        if (Files.exists(target)) {
            logger.warn("[ProgressiveStages] Kept legacy {} {} because the v3 target already exists: {}",
                label, source, target);
            return;
        }
        try {
            Files.createDirectories(target.getParent());
            try {
                Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicFailure) {
                Files.move(source, target);
            }
            logger.info("[ProgressiveStages] Migrated {} {} -> {}", label, source, target);
        } catch (IOException moveFailure) {
            // A cross-device or locked-file move can fail. A non-destructive copy still ensures
            // v3 loads the pack; leaving the source is safer than risking data loss.
            try {
                Files.copy(source, target);
                logger.warn("[ProgressiveStages] Copied {} {} -> {} (legacy source retained)",
                    label, source, target);
            } catch (IOException copyFailure) {
                logger.error("[ProgressiveStages] Failed to migrate {} {} -> {}: {}",
                    label, source, target, copyFailure.getMessage());
            }
        }
    }

    private static boolean samePath(Path a, Path b) {
        try {
            if (Files.exists(a) && Files.exists(b)) return Files.isSameFile(a, b);
        } catch (IOException ignored) {}
        return a.toAbsolutePath().normalize().equals(b.toAbsolutePath().normalize());
    }
}
