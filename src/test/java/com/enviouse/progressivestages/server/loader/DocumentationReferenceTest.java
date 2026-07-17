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
    }
}
