package com.enviouse.progressivestages.server.editor;

import java.util.List;
import java.util.UUID;

public record EditorAuditEntry(String transactionId, UUID actor, long timestamp, long beforeRevision,
                               long afterRevision, List<String> changedFiles, boolean committed,
                               String explanation) {
    public EditorAuditEntry {
        changedFiles = changedFiles == null ? List.of() : List.copyOf(changedFiles);
    }
}
