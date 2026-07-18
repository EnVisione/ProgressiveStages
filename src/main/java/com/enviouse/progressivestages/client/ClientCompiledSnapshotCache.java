package com.enviouse.progressivestages.client;

import com.enviouse.progressivestages.common.rehaul.client.ClientSnapshotAssembler;
import com.enviouse.progressivestages.common.rehaul.client.ClientSnapshotChunk;
import com.enviouse.progressivestages.common.rehaul.client.ClientSnapshotManifest;

import java.util.Optional;

public final class ClientCompiledSnapshotCache {
    private static final ClientSnapshotAssembler ASSEMBLER = new ClientSnapshotAssembler();
    private static volatile long revision;
    private static volatile String checksum = "";
    private static volatile byte[] active = new byte[0];

    private ClientCompiledSnapshotCache() {}

    public static synchronized void begin(ClientSnapshotManifest manifest) {
        if (manifest.delta() && (active.length == 0 || revision != manifest.baseRevision())) {
            throw new IllegalStateException("Client snapshot delta base is unavailable");
        }
        ASSEMBLER.begin(manifest);
        pendingManifest = manifest;
    }

    private static volatile ClientSnapshotManifest pendingManifest;

    public static synchronized boolean accept(ClientSnapshotChunk chunk) {
        Optional<byte[]> completed = ASSEMBLER.accept(chunk);
        if (completed.isEmpty()) return false;
        ClientSnapshotManifest manifest = pendingManifest;
        byte[] payload = completed.orElseThrow();
        byte[] next = manifest.delta()
            ? com.enviouse.progressivestages.common.rehaul.client.ClientSnapshotCodec.applyDelta(active, payload)
            : payload;
        if (next.length != manifest.uncompressedBytes()
                || !com.enviouse.progressivestages.common.rehaul.client.ClientSnapshotCodec.checksum(next)
                    .equals(manifest.checksum())) {
            pendingManifest = null;
            ASSEMBLER.clear();
            throw new IllegalArgumentException("Client snapshot activation checksum does not match");
        }
        active = next.clone();
        revision = manifest.configurationRevision();
        checksum = manifest.checksum();
        pendingManifest = null;
        return true;
    }

    public static long revision() { return revision; }
    public static String checksum() { return checksum; }
    public static byte[] activeBytes() { return active.clone(); }

    public static synchronized void clear() {
        ASSEMBLER.clear();
        pendingManifest = null;
        revision = 0;
        checksum = "";
        active = new byte[0];
    }
}
