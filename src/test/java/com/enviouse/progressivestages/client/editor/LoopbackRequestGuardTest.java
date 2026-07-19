package com.enviouse.progressivestages.client.editor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoopbackRequestGuardTest {
    private static final int PORT = 48123;
    private static final String SECRET = "singleplayer-editor-secret";

    @Test
    void acceptsCommonSingleplayerBrowserHeaders() {
        assertTrue(LoopbackRequestGuard.allows("127.0.0.1:48123", "http://127.0.0.1:48123",
            "Bearer " + SECRET, null, PORT, SECRET));
        assertTrue(LoopbackRequestGuard.allows("localhost:48123", "http://localhost:48123",
            "Bearer " + SECRET, null, PORT, SECRET));
        assertTrue(LoopbackRequestGuard.allows("127.0.0.1:48123", null,
            "Bearer " + SECRET, null, PORT, SECRET));
        assertTrue(LoopbackRequestGuard.allows("127.0.0.1:48123", "",
            null, SECRET, PORT, SECRET));
        assertTrue(LoopbackRequestGuard.allows("127.0.0.1:48123", "null",
            "Bearer " + SECRET, null, PORT, SECRET));
    }

    @Test
    void rejectsRemoteHostsOriginsPortsAndTokens() {
        assertFalse(LoopbackRequestGuard.allows("example.com:48123", "http://example.com:48123",
            "Bearer " + SECRET, null, PORT, SECRET));
        assertFalse(LoopbackRequestGuard.allows("127.0.0.1:48124", "http://127.0.0.1:48124",
            "Bearer " + SECRET, null, PORT, SECRET));
        assertFalse(LoopbackRequestGuard.allows("127.0.0.1:48123", "https://example.com",
            "Bearer " + SECRET, null, PORT, SECRET));
        assertFalse(LoopbackRequestGuard.allows("127.0.0.1:48123", "http://127.0.0.1:48123",
            "Bearer wrong", "wrong", PORT, SECRET));
    }
}
