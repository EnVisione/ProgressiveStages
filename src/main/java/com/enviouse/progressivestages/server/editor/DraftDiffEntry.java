package com.enviouse.progressivestages.server.editor;

public record DraftDiffEntry(String path, ChangeType change, String beforeChecksum, String afterChecksum,
                             int beforeBytes, int afterBytes) {
    public enum ChangeType { ADDED, MODIFIED, DELETED }
}
