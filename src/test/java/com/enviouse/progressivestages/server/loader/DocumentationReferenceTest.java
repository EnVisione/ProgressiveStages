package com.enviouse.progressivestages.server.loader;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentationReferenceTest {

    private static final Path PROJECT = Path.of(System.getProperty("progressivestages.projectDir"));
    private static final Path DIAMOND_REFERENCE = PROJECT.resolve("examples/reference/diamond_stage.toml");

    @Test
    void diamondReferenceMatchesTheGeneratedDefaultAndParses() throws IOException {
        String reference = Files.readString(DIAMOND_REFERENCE);

        assertEquals(DefaultStageTemplates.diamondAge(), reference);
        assertTrue(reference.contains("READ THIS FIRST. A SAFE FIVE MINUTE EDIT"));
        assertTrue(reference.contains("TABLE OF CONTENTS"));
        assertTrue(reference.contains("TROUBLESHOOTING"));

        StageFileParser.ParseResult result = StageFileParser.parseWithErrors(DIAMOND_REFERENCE);
        assertTrue(result.isSuccess(), result::getErrorMessage);
        assertEquals("progressivestages:diamond_age", result.getStageDefinition().getId().toString());
    }

    @Test
    void completeDocumentationLinksTheDiamondReference() throws IOException {
        String documentation = Files.readString(PROJECT.resolve("DOCUMENTATION.md"));

        assertTrue(documentation.contains("[`diamond_stage.toml`](examples/reference/diamond_stage.toml)"));
        assertTrue(documentation.contains("[Architecture and Project Structure Guide](ARCHITECTURE.md)"));
    }

    @Test
    void releasePresentationFilesAreConnected() throws IOException {
        String readme = Files.readString(PROJECT.resolve("README.md"));
        String curseForge = Files.readString(PROJECT.resolve("CURSEFORGE.md"));
        String metadata = Files.readString(PROJECT.resolve("src/main/templates/META-INF/neoforge.mods.toml"));

        assertTrue(Files.size(PROJECT.resolve("src/main/resources/progressivestages.png")) > 0);
        assertTrue(metadata.contains("logoFile=\"progressivestages.png\""));
        assertTrue(metadata.contains("displayURL=\"https://www.curseforge.com/minecraft/mc-mods/progressivestages\""));
        assertTrue(readme.contains("[CurseForge 3.0 Description](CURSEFORGE.md)"));
        assertTrue(curseForge.contains("# ProgressiveStages 3.0"));
        assertTrue(curseForge.contains("config/progressivestages/stages/"));
    }
}
