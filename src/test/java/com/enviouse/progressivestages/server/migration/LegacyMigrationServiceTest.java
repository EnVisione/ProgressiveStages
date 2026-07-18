package com.enviouse.progressivestages.server.migration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyMigrationServiceTest {
    private static final Path PROJECT = Path.of(System.getProperty("progressivestages.projectDir"));
    @TempDir Path temporary;

    @Test
    void plansWritesVerifiesAndRollsBackWithoutLosingTheOriginal() throws Exception {
        Path stages = temporary.resolve("stages");
        Files.createDirectories(stages);
        Path legacy = stages.resolve("legacy.toml");
        Files.writeString(legacy, """
            # A retained identity comment.
            [stage]
            id = "test:legacy"
            display_name = "Legacy"

            # A retained rule comment.
            [items]
            locked = ["minecraft:diamond"]

            # A retained progression comment.
            [cost]
            xp_levels = 3
            """);

        LegacyMigrationService migrations = new LegacyMigrationService(stages);
        assertEquals(1, migrations.scan().size());
        MigrationPlan plan = migrations.plan(legacy);
        assertTrue(plan.generatedFiles().get("stage.toml").contains("[schema]"));
        assertTrue(plan.generatedFiles().get("rules.toml").contains("A retained rule comment"));
        assertTrue(plan.generatedFiles().get("progression.toml").contains("A retained progression comment"));
        var before = com.enviouse.progressivestages.server.loader.StageFileParser.parseWithErrors(legacy);
        var after = com.enviouse.progressivestages.server.loader.StagePackageParser.parseContents("test",
            "stage.toml", plan.generatedFiles().get("stage.toml"), "rules.toml",
            plan.generatedFiles().get("rules.toml"), "progression.toml",
            plan.generatedFiles().get("progression.toml"));
        assertEquals(LegacyMigrationService.semanticDocument(before.getStageDefinition()),
            LegacyMigrationService.semanticDocument(after.getStageDefinition()));

        MigrationResult written = migrations.write(plan);
        assertTrue(written.success(), written.explanation());
        assertFalse(Files.exists(legacy));
        assertTrue(Files.isRegularFile(plan.targetDirectory().resolve("stage.toml")));
        assertTrue(migrations.verify(plan, plan.targetDirectory()));

        MigrationResult rollback = migrations.rollback(written.migrationId());
        assertTrue(rollback.success(), rollback.explanation());
        assertTrue(Files.isRegularFile(legacy));
        assertFalse(Files.exists(plan.targetDirectory()));
    }

    @Test
    void publishedBeforeAndAfterExampleHaveTheSameLegacyMeaning() {
        Path before = PROJECT.resolve("examples/migration/before/iron_age.toml");
        var legacy = com.enviouse.progressivestages.server.loader.StageFileParser.parseWithErrors(before);
        assertTrue(legacy.isSuccess(), legacy.getErrorMessage());
        var discovery = com.enviouse.progressivestages.server.loader.StagePackageDiscovery
            .discover(PROJECT.resolve("examples/migration/after"));
        assertEquals(1, discovery.packages().size());
        var after = com.enviouse.progressivestages.server.loader.StagePackageParser
            .parse(discovery.packages().getFirst());
        assertTrue(after.isSuccess(), after.getErrorMessage());
        assertEquals(LegacyMigrationService.semanticDocument(legacy.getStageDefinition()),
            LegacyMigrationService.semanticDocument(after.getStageDefinition()));
    }
}
