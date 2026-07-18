package com.enviouse.progressivestages.client;

import com.enviouse.progressivestages.common.rehaul.client.ClientSnapshotChunk;
import com.enviouse.progressivestages.common.rehaul.client.ClientSnapshotCodec;
import com.enviouse.progressivestages.common.rehaul.client.ClientSnapshotManifest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientCompiledSnapshotCacheTest {

    @AfterEach
    void clear() {
        ClientCompiledSnapshotCache.clear();
    }

    @Test
    void activatesDeltaOnlyAgainstTheExactCurrentRevision() {
        byte[] base = "base compiled snapshot".repeat(100).getBytes(StandardCharsets.UTF_8);
        activate(base, base, 4, 0, false);
        byte[] current = base.clone();
        current[5] = 'C';
        byte[] delta = ClientSnapshotCodec.createDelta(base, current);
        activate(delta, current, 5, 4, true);
        assertArrayEquals(current, ClientCompiledSnapshotCache.activeBytes());
        assertEquals(5, ClientCompiledSnapshotCache.revision());
    }

    @Test
    void rejectsDeltaWhenTheAcknowledgedBaseIsMissing() {
        byte[] current = "new snapshot".getBytes(StandardCharsets.UTF_8);
        byte[] delta = ClientSnapshotCodec.createDelta("old snapshot".getBytes(StandardCharsets.UTF_8), current);
        ClientSnapshotManifest manifest = manifest(delta, current, 3, 2, true);
        assertThrows(IllegalStateException.class, () -> ClientCompiledSnapshotCache.begin(manifest));
    }

    private static void activate(byte[] payload, byte[] finalBytes, long revision, long base, boolean delta) {
        ClientSnapshotManifest manifest = manifest(payload, finalBytes, revision, base, delta);
        ClientCompiledSnapshotCache.begin(manifest);
        assertTrue(ClientCompiledSnapshotCache.accept(new ClientSnapshotChunk(revision, 0,
            ClientSnapshotCodec.compress(payload))));
    }

    private static ClientSnapshotManifest manifest(byte[] payload, byte[] finalBytes,
                                                   long revision, long base, boolean delta) {
        byte[] compressed = ClientSnapshotCodec.compress(payload);
        return new ClientSnapshotManifest(ClientSnapshotCodec.PROTOCOL_VERSION, 4, revision, base,
            ClientSnapshotCodec.checksum(finalBytes), 1, compressed.length, finalBytes.length,
            Set.of("safe_delta"), delta);
    }
}
