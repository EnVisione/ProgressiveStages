package com.enviouse.progressivestages.server.commands;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StageCommandAliasTest {

    private static final Path PROJECT = Path.of(System.getProperty("progressivestages.projectDir"));

    @Test
    void pstagesIsRegisteredAndPsRemainsAvailableToOtherMods() throws IOException {
        String source = Files.readString(PROJECT.resolve(
            "src/main/java/com/enviouse/progressivestages/server/commands/StageCommand.java"));

        assertTrue(source.contains("Commands.literal(\"pstages\")"));
        assertFalse(source.contains("Commands.literal(\"ps\")"));
        assertTrue(source.contains("Commands.literal(\"rule\")"));
        assertTrue(source.contains("activateConditionalRule"));
    }
}
