package com.enviouse.progressivestages.common.rehaul.client;

public record ClientSnapshotChunk(long revision, int sequence, byte[] data) {
    public ClientSnapshotChunk {
        if (revision < 0 || sequence < 0) throw new IllegalArgumentException("Client snapshot chunk coordinates are invalid");
        data = data == null ? new byte[0] : data.clone();
    }

    @Override public byte[] data() { return data.clone(); }
}
