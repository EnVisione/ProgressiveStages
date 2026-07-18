package com.enviouse.progressivestages.common.rehaul.client;

import java.util.List;

public record PreparedClientSnapshot(ClientSnapshotManifest manifest, List<ClientSnapshotChunk> chunks) {
    public PreparedClientSnapshot {
        chunks = chunks == null ? List.of() : List.copyOf(chunks);
        if (manifest == null || chunks.size() != manifest.chunks()) throw new IllegalArgumentException("Client snapshot chunks do not match the manifest");
    }
}
