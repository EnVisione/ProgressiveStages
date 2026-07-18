package com.enviouse.progressivestages.server.editor;

public record DraftOperation(String path, String before, String after, long timestamp) {
    public DraftOperation {
        if (path == null || path.isBlank()) throw new IllegalArgumentException("Draft path cannot be blank");
    }
}
