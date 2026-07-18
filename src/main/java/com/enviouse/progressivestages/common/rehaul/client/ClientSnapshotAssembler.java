package com.enviouse.progressivestages.common.rehaul.client;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.InflaterInputStream;

public final class ClientSnapshotAssembler {
    private volatile Candidate candidate;

    public synchronized void begin(ClientSnapshotManifest manifest) {
        if (manifest.protocolVersion() != ClientSnapshotCodec.PROTOCOL_VERSION) {
            throw new IllegalArgumentException("Incompatible ProgressiveStages client snapshot protocol");
        }
        if (manifest.compressedBytes() > ClientSnapshotCodec.MAX_SNAPSHOT_BYTES
                || manifest.uncompressedBytes() > ClientSnapshotCodec.MAX_SNAPSHOT_BYTES) {
            throw new IllegalArgumentException("Client snapshot exceeds the configured maximum");
        }
        candidate = new Candidate(manifest);
    }

    public synchronized Optional<byte[]> accept(ClientSnapshotChunk chunk) {
        Candidate active = candidate;
        if (active == null || chunk.revision() != active.manifest.configurationRevision()) return Optional.empty();
        if (chunk.sequence() >= active.manifest.chunks()) throw new IllegalArgumentException("Client snapshot chunk sequence is invalid");
        if (active.chunks.putIfAbsent(chunk.sequence(), chunk.data()) != null) return Optional.empty();
        if (active.chunks.size() != active.manifest.chunks()) return Optional.empty();
        ByteArrayOutputStream compressed = new ByteArrayOutputStream(active.manifest.compressedBytes());
        for (int sequence = 0; sequence < active.manifest.chunks(); sequence++) {
            byte[] value = active.chunks.get(sequence);
            if (value == null) throw new IllegalArgumentException("Client snapshot is missing a chunk");
            compressed.writeBytes(value);
        }
        if (compressed.size() != active.manifest.compressedBytes()) throw new IllegalArgumentException("Client snapshot compressed size does not match");
        byte[] raw = inflate(compressed.toByteArray(), active.manifest.uncompressedBytes(), active.manifest.delta());
        if (!active.manifest.delta() && !ClientSnapshotCodec.checksum(raw).equals(active.manifest.checksum())) {
            throw new IllegalArgumentException("Client snapshot checksum does not match");
        }
        candidate = null;
        return Optional.of(raw);
    }

    public synchronized void clear() { candidate = null; }

    private static byte[] inflate(byte[] compressed, int expected, boolean delta) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(expected, 64 * 1024));
            try (InflaterInputStream input = new InflaterInputStream(new ByteArrayInputStream(compressed))) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    output.write(buffer, 0, read);
                    if (output.size() > ClientSnapshotCodec.MAX_SNAPSHOT_BYTES) {
                        throw new IllegalArgumentException("Client snapshot inflated beyond the configured maximum");
                    }
                }
            }
            if (!delta && output.size() != expected) throw new IllegalArgumentException("Client snapshot uncompressed size does not match");
            return output.toByteArray();
        } catch (IOException error) {
            throw new IllegalArgumentException("Client snapshot could not be decompressed", error);
        }
    }

    private static final class Candidate {
        final ClientSnapshotManifest manifest;
        final Map<Integer, byte[]> chunks = new ConcurrentHashMap<>();
        Candidate(ClientSnapshotManifest manifest) { this.manifest = manifest; }
    }
}
