package com.enviouse.progressivestages.common.rehaul.client;

import java.util.Set;

public record ClientSnapshotManifest(int protocolVersion, int schemaVersion, long configurationRevision,
                                     long baseRevision, String checksum, int chunks, int compressedBytes,
                                     int uncompressedBytes, Set<String> capabilities, boolean delta) {
    public ClientSnapshotManifest {
        if (protocolVersion < 1 || schemaVersion < 1 || configurationRevision < 0 || baseRevision < 0) {
            throw new IllegalArgumentException("Client snapshot versions are invalid");
        }
        if (checksum == null || checksum.isBlank()) throw new IllegalArgumentException("Client snapshot checksum cannot be blank");
        if (chunks < 1 || chunks > 4096 || compressedBytes < 0 || uncompressedBytes < 0) {
            throw new IllegalArgumentException("Client snapshot size is invalid");
        }
        capabilities = capabilities == null ? Set.of() : Set.copyOf(capabilities);
    }
}
