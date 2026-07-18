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
        assertTrue(documentation.contains("[Phase 1 Through Phase 19 Guide](PHASES_1_TO_19.md)"));
    }

    @Test
    void phaseGuideDocumentsEveryPhaseWithExamplesAndVerification() throws IOException {
        String guide = Files.readString(PROJECT.resolve("PHASES_1_TO_19.md"));

        for (int phase = 1; phase <= 19; phase++) {
            String heading = "## Phase " + phase + ".";
            int start = guide.indexOf(heading);
            assertTrue(start >= 0, "Missing phase " + phase);
            int end = phase == 19 ? guide.indexOf("## After phase 19", start)
                : guide.indexOf("## Phase " + (phase + 1) + ".", start);
            assertTrue(end > start, "Missing phase boundary " + phase);
            String section = guide.substring(start, end);
            assertTrue(section.contains("### Goal"), "Missing goal for phase " + phase);
            assertTrue(section.contains("### Verification"), "Missing verification for phase " + phase);
            assertTrue(section.contains("### Common mistakes"), "Missing mistakes for phase " + phase);
        }
        assertTrue(guide.contains("```toml"));
        assertTrue(guide.contains("### Verification"));
        assertTrue(guide.contains("### Common mistakes"));
        assertTrue(guide.contains("/pstages"));
        assertTrue(guide.contains("ProgressiveStages.onGranted"));
        assertTrue(guide.contains("ProgressiveStagesAPI.grantStage"));
        assertTrue(guide.contains("./gradlew clean build"));
        assertTrue(guide.contains("examples/reference/diamond_stage.toml"));
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
