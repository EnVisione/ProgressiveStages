package com.enviouse.progressivestages.server.loader;

import com.enviouse.progressivestages.common.api.StageId;
import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.toml.TomlParser;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReleaseDocumentationExamplesTest {
    private static final Path PROJECT = Path.of(System.getProperty("progressivestages.projectDir"));
    private static final Path EXAMPLES = PROJECT.resolve("examples/3.1.0/stages");

    @Test
    void everyReleaseStageExampleParsesAndShowsItsDocumentedBehavior() throws IOException {
        String gradleProperties = Files.readString(PROJECT.resolve("gradle.properties"));
        assertTrue(gradleProperties.contains("mod_version=3.1.1"));
        var editorPackage = JsonParser.parseString(Files.readString(PROJECT.resolve("editor-ui/package.json")))
            .getAsJsonObject();
        var editorLock = JsonParser.parseString(Files.readString(PROJECT.resolve("editor-ui/package-lock.json")))
            .getAsJsonObject();
        assertEquals("3.1.1", editorPackage.get("version").getAsString());
        assertEquals("3.1.1", editorLock.get("version").getAsString());

        List<Path> files;
        try (var paths = Files.list(EXAMPLES)) {
            files = paths.filter(path -> path.getFileName().toString().endsWith(".toml"))
                .sorted(Comparator.comparing(Path::toString))
                .toList();
        }

        assertEquals(4, files.size());
        var parsed = files.stream().map(file -> {
            StageFileParser.ParseResult result = StageFileParser.parseWithErrors(file);
            assertTrue(result.isSuccess(), file + ". " + result.getErrorMessage());
            return result.getStageDefinition();
        }).toList();
        assertEquals(4, parsed.stream().map(stage -> stage.getId()).collect(java.util.stream.Collectors.toSet()).size());

        var guide = parsed.stream().filter(stage -> stage.getId().equals(StageId.parse("example:iron_guide")))
            .findFirst().orElseThrow();
        assertTrue(guide.getGuide().enabled());
        assertTrue(guide.getGuide().howToUnlock().contains("{stage_name}"));
        assertTrue(guide.hasTriggers());
        assertTrue(guide.getConditionalRules().stream()
            .anyMatch(rule -> rule.stageState().name().equals("OWNED")));

        var protection = parsed.stream()
            .filter(stage -> stage.getId().equals(StageId.parse("example:stronghold_protection")))
            .findFirst().orElseThrow().getLocks().structures();
        assertTrue(protection.entryAllowed());
        assertTrue(protection.preventBlockPlace());
        assertTrue(protection.preventBlockBreak());
        assertTrue(protection.preventExplosions());
        assertTrue(protection.disableMobSpawning());
        assertEquals(Integer.valueOf(50), protection.priority());

        var access = parsed.stream()
            .filter(stage -> stage.getId().equals(StageId.parse("example:stronghold_access")))
            .findFirst().orElseThrow().getLocks().structures();
        assertEquals(List.of("id:minecraft:stronghold"),
            access.lockedEntry().locked().stream().map(entry -> entry.raw()).toList());

        var permit = parsed.stream().filter(stage -> stage.getId().equals(StageId.parse("example:stronghold_allow")))
            .findFirst().orElseThrow();
        var permitRule = permit.getConditionalRules().stream()
            .filter(rule -> rule.effect().name().equals("UNLOCK")
                && rule.stageState().name().equals("OWNED"))
            .findFirst().orElseThrow();
        assertEquals(100, permitRule.priority());

        Config globalConfig;
        try (var reader = Files.newBufferedReader(PROJECT.resolve("examples/3.1.0/progressivestages.toml"))) {
            globalConfig = new TomlParser().parse(reader);
        }
        Object guideEnabled = globalConfig.get("general.enable_stage_guide");
        assertInstanceOf(Boolean.class, guideEnabled);
        assertTrue((Boolean) guideEnabled);
    }
}
