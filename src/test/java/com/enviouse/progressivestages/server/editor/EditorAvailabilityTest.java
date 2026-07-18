package com.enviouse.progressivestages.server.editor;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EditorAvailabilityTest {
    private static final Path PROJECT = Path.of(System.getProperty("progressivestages.projectDir"));

    @Test
    void integratedAndDedicatedServersShareTheOperatorOnlyEditorPath() throws Exception {
        String command = Files.readString(PROJECT.resolve(
            "src/main/java/com/enviouse/progressivestages/server/commands/StageCommand.java"));
        String service = Files.readString(PROJECT.resolve(
            "src/main/java/com/enviouse/progressivestages/server/editor/EditorSessionService.java"));
        String guide = Files.readString(PROJECT.resolve("REHAUL_GUIDE.md"));

        assertFalse(command.contains("getServer().isDedicatedServer()"));
        assertFalse(service.contains("isDedicatedServer()"));
        assertFalse(service.contains("available only on dedicated servers"));
        assertTrue(command.contains("Commands.literal(\"editor\")"));
        assertTrue(command.contains("requires(source -> source.hasPermission(3))"));
        assertTrue(service.contains("operator.hasPermissions(3)"));
        assertTrue(guide.contains("integrated single-player world and on a dedicated server"));
    }
}
