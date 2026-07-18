package com.enviouse.progressivestages.server.loader;

import com.enviouse.progressivestages.common.rehaul.LegacyStageCompiler;
import com.enviouse.progressivestages.common.rehaul.RuleEffect;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StagePackageParserTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void discoversPackagesLegacyStagesAndIgnoresHelperToml() throws Exception {
        Path packageRoot = temporaryDirectory.resolve("iron_age");
        Files.createDirectories(packageRoot);
        Files.writeString(packageRoot.resolve("stage.toml"), identity("pack:iron_age"));
        Files.writeString(temporaryDirectory.resolve("legacy.toml"), """
            [stage]
            id = "legacy"
            """);
        Files.writeString(temporaryDirectory.resolve("variables.toml"), "value = 1\n");

        StagePackageDiscovery.DiscoveryResult discovered = StagePackageDiscovery.discover(temporaryDirectory);

        assertTrue(discovered.isValid(), discovered.errors().toString());
        assertEquals(1, discovered.packages().size());
        assertEquals(1, discovered.legacyFiles().size());
        assertEquals(1, discovered.ignoredFiles().size());
    }

    @Test
    void ignoresEditorAndMigrationArchivesEvenWhenTheyContainStageMarkers() throws Exception {
        Path editorArchive = temporaryDirectory.resolve(".editor-archive/old_stage");
        Path migrationArchive = temporaryDirectory.resolve(".migration-backups/run");
        Files.createDirectories(editorArchive);
        Files.createDirectories(migrationArchive);
        Files.writeString(editorArchive.resolve("stage.toml"), identity("pack:archived"));
        Files.writeString(migrationArchive.resolve("original.toml"), "[stage]\nid = \"pack:original\"\n");

        StagePackageDiscovery.DiscoveryResult discovered = StagePackageDiscovery.discover(temporaryDirectory);

        assertTrue(discovered.packages().isEmpty());
        assertTrue(discovered.legacyFiles().isEmpty());
        assertEquals(2, discovered.ignoredFiles().size());
    }

    @Test
    void parsesAndMergesTheThreeFileSchema() throws Exception {
        Path packageRoot = temporaryDirectory.resolve("iron_age");
        Files.createDirectories(packageRoot);
        Files.writeString(packageRoot.resolve("stage.toml"), identity("pack:iron_age"));
        Files.writeString(packageRoot.resolve("rules.toml"), """
            [items]
            locked = ["mod:ae2"]
            always_unlocked = ["ae2:charger"]
            """);
        Files.writeString(packageRoot.resolve("progression.toml"), """
            [[triggers]]
            type = "pickup"
            target = "minecraft:iron_ingot"
            """);

        StagePackageSource source = StagePackageParser.inspect(temporaryDirectory, packageRoot);
        StageFileParser.ParseResult result = StagePackageParser.parse(source);

        assertTrue(result.isSuccess(), result::getErrorMessage);
        assertEquals("pack:iron_age", result.getStageDefinition().getId().toString());
        assertEquals("Iron Age", result.getStageDefinition().getDisplayName());
        assertEquals(400, result.getStageDefinition().getPriority());
        assertEquals(4, result.getStageDefinition().getSchemaVersion());
        assertEquals(1, result.getStageDefinition().getLocks().items().locked().size());
        assertEquals(1, result.getStageDefinition().getTriggers().size());
        assertFalse(result.getStageDefinition().getProvenance().translatedLegacy());

        var compiled = LegacyStageCompiler.compile(result.getStageDefinition(), source.sourceId());
        assertEquals(2, compiled.rules().size());
        assertEquals(RuleEffect.LOCK, compiled.rules().getFirst().effect());
        assertEquals(RuleEffect.EXCLUDE, compiled.rules().getLast().effect());
        assertTrue(compiled.rules().getLast().parentRule().isPresent());
        assertThrows(UnsupportedOperationException.class, () -> compiled.rules().clear());
    }

    @Test
    void rejectsSectionsPlacedInTheWrongFile() throws Exception {
        Path packageRoot = temporaryDirectory.resolve("broken");
        Files.createDirectories(packageRoot);
        Files.writeString(packageRoot.resolve("stage.toml"), identity("pack:broken"));
        Files.writeString(packageRoot.resolve("rules.toml"), """
            [stage]
            name = "Wrong place"
            """);

        StagePackageSource source = StagePackageParser.inspect(temporaryDirectory, packageRoot);
        StageFileParser.ParseResult result = StagePackageParser.parse(source);

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("wrong file"), result.getErrorMessage());
    }

    @Test
    void rejectsIncludesOutsideThePackage() throws Exception {
        Path packageRoot = temporaryDirectory.resolve("broken");
        Files.createDirectories(packageRoot);
        Files.writeString(temporaryDirectory.resolve("outside.toml"), "[items]\nlocked = []\n");
        Files.writeString(packageRoot.resolve("stage.toml"), """
            [schema]
            version = 4

            [package]
            rules_includes = ["../outside.toml"]

            [stage]
            id = "pack:broken"
            """);

        assertThrows(IllegalArgumentException.class,
            () -> StagePackageParser.inspect(temporaryDirectory, packageRoot));
    }

    private static String identity(String id) {
        return """
            [schema]
            version = 4

            [stage]
            id = "%s"
            name = "Iron Age"
            priority = 400
            """.formatted(id);
    }
}
