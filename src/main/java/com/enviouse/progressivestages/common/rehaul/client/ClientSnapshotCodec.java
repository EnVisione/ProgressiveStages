package com.enviouse.progressivestages.common.rehaul.client;

import com.enviouse.progressivestages.common.rehaul.CompiledSnapshot;

import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.zip.DeflaterOutputStream;

public final class ClientSnapshotCodec {
    public static final int PROTOCOL_VERSION = 2;
    public static final int MAX_CHUNK_BYTES = 24 * 1024;
    public static final int MAX_SNAPSHOT_BYTES = 16 * 1024 * 1024;
    private static final int DELTA_MAGIC = 0x50534434;

    private ClientSnapshotCodec() {}

    public static PreparedClientSnapshot prepare(CompiledSnapshot snapshot, long baseRevision) {
        return prepare(snapshot, baseRevision, null);
    }

    public static PreparedClientSnapshot prepare(CompiledSnapshot snapshot, long baseRevision, byte[] baseBytes) {
        byte[] raw = encode(snapshot);
        byte[] compressed = compress(raw);
        boolean delta = false;
        if (baseRevision > 0 && baseBytes != null && baseBytes.length > 0) {
            byte[] candidate = createDelta(baseBytes, raw);
            byte[] candidateCompressed = compress(candidate);
            if (candidateCompressed.length < compressed.length) {
                compressed = candidateCompressed;
                delta = true;
            }
        }
        List<ClientSnapshotChunk> chunks = new ArrayList<>();
        for (int offset = 0, sequence = 0; offset < compressed.length; offset += MAX_CHUNK_BYTES, sequence++) {
            int length = Math.min(MAX_CHUNK_BYTES, compressed.length - offset);
            chunks.add(new ClientSnapshotChunk(snapshot.revision(), sequence,
                java.util.Arrays.copyOfRange(compressed, offset, offset + length)));
        }
        if (chunks.isEmpty()) chunks.add(new ClientSnapshotChunk(snapshot.revision(), 0, new byte[0]));
        ClientSnapshotManifest manifest = new ClientSnapshotManifest(PROTOCOL_VERSION, 4, snapshot.revision(),
            baseRevision, checksum(raw), chunks.size(), compressed.length, raw.length,
            Set.of("stage_graph", "rule_presentation", "viewer_policy", "challenge_hud", "atomic_activation",
                "safe_delta"), delta);
        return new PreparedClientSnapshot(manifest, chunks);
    }

    public static byte[] createDelta(byte[] base, byte[] current) {
        int prefix = 0;
        int limit = Math.min(base.length, current.length);
        while (prefix < limit && base[prefix] == current[prefix]) prefix++;
        int suffix = 0;
        while (suffix < limit - prefix
                && base[base.length - suffix - 1] == current[current.length - suffix - 1]) suffix++;
        int middle = current.length - prefix - suffix;
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(middle + 20);
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeInt(DELTA_MAGIC);
            output.writeInt(current.length);
            output.writeInt(prefix);
            output.writeInt(suffix);
            output.writeInt(middle);
            output.write(current, prefix, middle);
            output.flush();
            return bytes.toByteArray();
        } catch (IOException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    public static byte[] applyDelta(byte[] base, byte[] delta) {
        try {
            DataInputStream input = new DataInputStream(new ByteArrayInputStream(delta));
            if (input.readInt() != DELTA_MAGIC) throw new IllegalArgumentException("Client snapshot delta marker is invalid");
            int length = input.readInt();
            int prefix = input.readInt();
            int suffix = input.readInt();
            int middle = input.readInt();
            if (length < 0 || length > MAX_SNAPSHOT_BYTES || prefix < 0 || suffix < 0 || middle < 0
                    || prefix + suffix + middle != length || prefix + suffix > base.length
                    || middle != input.available()) {
                throw new IllegalArgumentException("Client snapshot delta bounds are invalid");
            }
            byte[] output = new byte[length];
            System.arraycopy(base, 0, output, 0, prefix);
            input.readFully(output, prefix, middle);
            System.arraycopy(base, base.length - suffix, output, prefix + middle, suffix);
            return output;
        } catch (IOException error) {
            throw new IllegalArgumentException("Client snapshot delta could not be decoded", error);
        }
    }

    public static byte[] encode(CompiledSnapshot snapshot) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeInt(PROTOCOL_VERSION);
            out.writeLong(snapshot.revision());
            out.writeInt(snapshot.stages().size());
            for (var stage : snapshot.stages().values()) {
                text(out, stage.id().toString());
                text(out, stage.displayName());
                text(out, stage.description());
                out.writeInt(stage.priority());
                out.writeInt(stage.rules().size());
                for (var rule : stage.rules()) {
                    text(out, rule.id().toString());
                    text(out, rule.category());
                    text(out, rule.action());
                    text(out, rule.effect().name());
                    text(out, rule.selector().raw());
                    out.writeInt(rule.priority());
                    text(out, rule.viewerPolicy().emi().name());
                    text(out, rule.viewerPolicy().jei().name());
                }
            }
            out.flush();
            if (bytes.size() > MAX_SNAPSHOT_BYTES) throw new IllegalArgumentException("Compiled client snapshot exceeds the configured maximum");
            return bytes.toByteArray();
        } catch (IOException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    public static byte[] compress(byte[] raw) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try (DeflaterOutputStream compressor = new DeflaterOutputStream(output)) { compressor.write(raw); }
            return output.toByteArray();
        } catch (IOException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    public static String checksum(byte[] raw) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw)); }
        catch (Exception impossible) { throw new IllegalStateException(impossible); }
    }

    private static void text(DataOutputStream output, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        output.writeInt(bytes.length);
        output.write(bytes);
    }
}
