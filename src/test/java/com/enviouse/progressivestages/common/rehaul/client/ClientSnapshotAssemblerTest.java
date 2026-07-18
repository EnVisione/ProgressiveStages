package com.enviouse.progressivestages.common.rehaul.client;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientSnapshotAssemblerTest {
    @Test
    void assemblesOutOfOrderAndActivatesOnlyAfterChecksumVerification() {
        byte[] raw = "one complete revision".repeat(4000).getBytes(StandardCharsets.UTF_8);
        byte[] compressed = ClientSnapshotCodec.compress(raw);
        List<ClientSnapshotChunk> chunks = new ArrayList<>();
        int size = 400;
        for (int offset = 0, sequence = 0; offset < compressed.length; offset += size, sequence++) {
            chunks.add(new ClientSnapshotChunk(9, sequence,
                java.util.Arrays.copyOfRange(compressed, offset, Math.min(compressed.length, offset + size))));
        }
        ClientSnapshotManifest manifest = new ClientSnapshotManifest(ClientSnapshotCodec.PROTOCOL_VERSION,
            4, 9, 8, ClientSnapshotCodec.checksum(raw), chunks.size(), compressed.length, raw.length,
            Set.of("atomic_activation"), false);
        ClientSnapshotAssembler assembler = new ClientSnapshotAssembler();
        assembler.begin(manifest);
        java.util.Collections.reverse(chunks);
        byte[] completed = null;
        for (ClientSnapshotChunk chunk : chunks) {
            var result = assembler.accept(chunk);
            if (result.isPresent()) completed = result.orElseThrow();
        }
        assertArrayEquals(raw, completed);
    }

    @Test
    void rejectsCorruptSnapshotsAndProtocolMismatches() {
        ClientSnapshotAssembler assembler = new ClientSnapshotAssembler();
        assertThrows(IllegalArgumentException.class, () -> assembler.begin(new ClientSnapshotManifest(99,
            4, 1, 0, "bad", 1, 1, 1, Set.of(), false)));

        byte[] compressed = ClientSnapshotCodec.compress(new byte[]{1, 2, 3});
        assembler.begin(new ClientSnapshotManifest(ClientSnapshotCodec.PROTOCOL_VERSION,
            4, 2, 0, "not_the_checksum", 1, compressed.length, 3, Set.of(), false));
        assertThrows(IllegalArgumentException.class,
            () -> assembler.accept(new ClientSnapshotChunk(2, 0, compressed)));
    }

    @Test
    void createsAndAppliesBoundedDeltasAgainstTheAcknowledgedBase() {
        byte[] base = "stage one and a large unchanged rule section".repeat(1000).getBytes(StandardCharsets.UTF_8);
        byte[] current = base.clone();
        current[6] = 't';
        current[7] = 'w';
        current[8] = 'o';
        byte[] delta = ClientSnapshotCodec.createDelta(base, current);
        assertArrayEquals(current, ClientSnapshotCodec.applyDelta(base, delta));
        assertTrue(delta.length < current.length);
        assertThrows(IllegalArgumentException.class,
            () -> ClientSnapshotCodec.applyDelta(new byte[0], delta));
    }
}
