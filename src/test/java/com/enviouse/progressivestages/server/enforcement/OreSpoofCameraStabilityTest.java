package com.enviouse.progressivestages.server.enforcement;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OreSpoofCameraStabilityTest {

    private static final Path PROJECT = Path.of(System.getProperty("progressivestages.projectDir"));

    @Test
    void liveRefreshNeverUnloadsTheChunkUnderThePlayer() throws IOException {
        String manager = Files.readString(PROJECT.resolve(
            "src/main/java/com/enviouse/progressivestages/server/enforcement/OreSpoofManager.java"));
        String listener = Files.readString(PROJECT.resolve(
            "src/main/java/com/enviouse/progressivestages/server/enforcement/OreSpoofStageListener.java"));
        String events = Files.readString(PROJECT.resolve(
            "src/main/java/com/enviouse/progressivestages/server/ServerEventHandler.java"));

        assertFalse(manager.contains("ClientboundForgetLevelChunkPacket"));
        assertFalse(manager.contains("markChunkPendingToSend"));
        assertTrue(manager.contains("public void refreshPlayer(ServerPlayer player)"));
        assertTrue(manager.contains("rescanPlayer(player, sl, st)"));
        assertTrue(listener.contains("OreSpoofManager.get().refreshPlayer(p)"));
        assertTrue(events.contains("OreSpoofManager.get().refreshPlayer(sp)"));
    }
}
