package com.enviouse.progressivestages.server.loader;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StageFileParserTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void acceptsNamespacedStageIdentifiers() throws IOException {
        Path file = write("namespaced.toml", """
            [stage]
            id = "example:root"
            display_name = "Root"
            """);

        StageFileParser.ParseResult result = StageFileParser.parseWithErrors(file);

        assertTrue(result.isSuccess());
        assertEquals("example:root", result.getStageDefinition().getId().toString());
    }

    @Test
    void malformedIdentifiersBecomeValidationErrors() throws IOException {
        Path file = write("invalid.toml", """
            [stage]
            id = "invalid stage"
            """);

        StageFileParser.ParseResult result = StageFileParser.parseWithErrors(file);

        assertFalse(result.isSuccess());
        assertFalse(result.isSyntaxError());
    }

    @Test
    void invalidTriggerTypesRejectTheDefinition() throws IOException {
        Path file = write("trigger.toml", """
            [stage]
            id = "trigger_test"

            [[triggers]]
            type = "missing_provider"
            target = "anything"
            """);

        StageFileParser.ParseResult result = StageFileParser.parseWithErrors(file);

        assertFalse(result.isSuccess());
        assertFalse(result.isSyntaxError());
        assertTrue(result.getErrorMessage().contains("Unknown trigger condition type"), result.getErrorMessage());
    }

    @Test
    void generatedStageTemplatesRemainValid() throws IOException {
        String[] names = {"stone.toml", "iron.toml", "diamond.toml"};
        String[] templates = {
            DefaultStageTemplates.stoneAge(),
            DefaultStageTemplates.ironAge(),
            DefaultStageTemplates.diamondAge()
        };

        for (int index = 0; index < names.length; index++) {
            StageFileParser.ParseResult result = StageFileParser.parseWithErrors(
                write(names[index], templates[index]));
            assertTrue(result.isSuccess(), result.getErrorMessage());
        }
    }

    @Test
    void invalidCostEntriesRejectTheDefinition() throws IOException {
        Path file = write("cost.toml", """
            [stage]
            id = "cost_test"

            [cost]
            items = ["not a resource"]
            """);

        StageFileParser.ParseResult result = StageFileParser.parseWithErrors(file);

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("Invalid cost item"), result.getErrorMessage());
    }

    @Test
    void customProgressionMapBackgroundIsParsed() throws IOException {
        Path file = write("background.toml", """
            [stage]
            id = "background_test"

            [display]
            background = "mypack:gui/progression"
            """);

        StageFileParser.ParseResult result = StageFileParser.parseWithErrors(file);

        assertTrue(result.isSuccess(), result.getErrorMessage());
        assertEquals("mypack:gui/progression", result.getStageDefinition().getUiBackground());
    }

    private Path write(String name, String contents) throws IOException {
        Path file = temporaryDirectory.resolve(name);
        Files.writeString(file, contents);
        return file;
    }
}
