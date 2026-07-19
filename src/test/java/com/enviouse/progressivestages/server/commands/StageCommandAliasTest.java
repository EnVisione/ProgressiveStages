package com.enviouse.progressivestages.server.commands;

import com.enviouse.progressivestages.common.api.StageId;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        assertTrue(source.contains("Commands.literal(\"structure\")"));
        assertTrue(source.contains("Commands.literal(\"providers\")"));
        assertTrue(source.contains("Commands.literal(\"sessions\")"));
        assertTrue(source.contains("Commands.literal(\"reconcile\")"));
        assertTrue(source.contains("Commands.literal(\"close\")"));
        assertTrue(source.contains("session.instance().startPosition()"));
        assertTrue(source.contains("session.bounds()"));
        assertTrue(source.contains("session.participants().stream()"));
        assertTrue(source.contains("Commands.argument(\"stage\", StringArgumentType.greedyString())"));
    }

    @Test
    void namespacedStageArgumentsAcceptTrailingSpaces() throws Exception {
        AtomicReference<String> captured = new AtomicReference<>();
        CommandDispatcher<Object> dispatcher = new CommandDispatcher<>();
        dispatcher.register(LiteralArgumentBuilder.<Object>literal("grant")
            .then(RequiredArgumentBuilder.<Object, String>argument("stage", StringArgumentType.greedyString())
                .executes(context -> {
                    captured.set(StringArgumentType.getString(context, "stage"));
                    return 1;
                })));

        assertEquals(1, dispatcher.execute("grant wizard:warlock   ", new Object()));
        assertEquals("wizard:warlock", StageId.parse(captured.get()).toString());
    }
}
