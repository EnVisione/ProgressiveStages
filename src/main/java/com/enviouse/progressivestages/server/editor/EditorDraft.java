package com.enviouse.progressivestages.server.editor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class EditorDraft {
    private final UUID id;
    private final UUID owner;
    private final long baseConfigurationRevision;
    private final long baseCatalogRevision;
    private final Map<String, String> baseFiles;
    private final Map<String, String> files;
    private final List<DraftOperation> operations = new ArrayList<>();
    private final Set<UUID> collaborators = new java.util.LinkedHashSet<>();
    private long revision;
    private int cursor;
    private long updatedAt;

    EditorDraft(UUID id, UUID owner, long baseConfigurationRevision, long baseCatalogRevision,
                Map<String, String> sourceFiles) {
        this.id = id;
        this.owner = owner;
        this.baseConfigurationRevision = baseConfigurationRevision;
        this.baseCatalogRevision = baseCatalogRevision;
        this.baseFiles = Map.copyOf(sourceFiles);
        this.files = new LinkedHashMap<>(sourceFiles);
        this.updatedAt = System.currentTimeMillis();
    }

    static EditorDraft recover(UUID id, UUID owner, long baseConfigurationRevision,
                               long baseCatalogRevision, Map<String, String> baseFiles,
                               Map<String, String> currentFiles, long revision,
                               long updatedAt, Set<UUID> collaborators) {
        EditorDraft draft = new EditorDraft(id, owner, baseConfigurationRevision,
            baseCatalogRevision, baseFiles);
        draft.files.clear();
        draft.files.putAll(currentFiles);
        draft.revision = Math.max(0, revision);
        draft.updatedAt = updatedAt;
        draft.collaborators.addAll(collaborators);
        return draft;
    }

    public synchronized long mutate(UUID actor, long expectedRevision, String path, String content) {
        authorize(actor);
        if (expectedRevision != revision) throw new DraftConflictException(revision);
        String normalized = EditorPaths.normalize(path);
        String before = files.get(normalized);
        if (java.util.Objects.equals(before, content)) return revision;
        while (operations.size() > cursor) operations.removeLast();
        DraftOperation operation = new DraftOperation(normalized, before, content, System.currentTimeMillis());
        operations.add(operation);
        cursor++;
        apply(operation, true);
        revision++;
        updatedAt = operation.timestamp();
        return revision;
    }

    public synchronized long undo(UUID actor, long expectedRevision) {
        authorize(actor);
        if (expectedRevision != revision) throw new DraftConflictException(revision);
        if (cursor == 0) return revision;
        DraftOperation operation = operations.get(--cursor);
        apply(operation, false);
        revision++;
        updatedAt = System.currentTimeMillis();
        return revision;
    }

    public synchronized long redo(UUID actor, long expectedRevision) {
        authorize(actor);
        if (expectedRevision != revision) throw new DraftConflictException(revision);
        if (cursor >= operations.size()) return revision;
        DraftOperation operation = operations.get(cursor++);
        apply(operation, true);
        revision++;
        updatedAt = System.currentTimeMillis();
        return revision;
    }

    public synchronized List<DraftDiffEntry> diff() {
        Set<String> paths = new java.util.TreeSet<>();
        paths.addAll(baseFiles.keySet());
        paths.addAll(files.keySet());
        List<DraftDiffEntry> output = new ArrayList<>();
        for (String path : paths) {
            String before = baseFiles.get(path);
            String after = files.get(path);
            if (java.util.Objects.equals(before, after)) continue;
            DraftDiffEntry.ChangeType type = before == null ? DraftDiffEntry.ChangeType.ADDED
                : after == null ? DraftDiffEntry.ChangeType.DELETED : DraftDiffEntry.ChangeType.MODIFIED;
            output.add(new DraftDiffEntry(path, type, EditorPaths.checksum(before), EditorPaths.checksum(after),
                EditorPaths.bytes(before), EditorPaths.bytes(after)));
        }
        return List.copyOf(output);
    }

    public synchronized Map<String, String> files() { return Map.copyOf(files); }
    public synchronized Map<String, String> baseFiles() { return baseFiles; }
    public synchronized List<DraftOperation> operations() { return List.copyOf(operations.subList(0, cursor)); }
    public synchronized long revision() { return revision; }
    public UUID id() { return id; }
    public UUID owner() { return owner; }
    public long baseConfigurationRevision() { return baseConfigurationRevision; }
    public long baseCatalogRevision() { return baseCatalogRevision; }
    public synchronized long updatedAt() { return updatedAt; }
    public synchronized boolean canUndo() { return cursor > 0; }
    public synchronized boolean canRedo() { return cursor < operations.size(); }
    public synchronized Set<UUID> collaborators() { return Set.copyOf(collaborators); }
    public synchronized void addCollaborator(UUID actor, UUID collaborator) { authorizeOwner(actor); collaborators.add(collaborator); }
    public synchronized void removeCollaborator(UUID actor, UUID collaborator) { authorizeOwner(actor); collaborators.remove(collaborator); }

    private void apply(DraftOperation operation, boolean forward) {
        String value = forward ? operation.after() : operation.before();
        if (value == null) files.remove(operation.path());
        else files.put(operation.path(), value);
    }

    private void authorize(UUID actor) {
        if (!owner.equals(actor) && !collaborators.contains(actor)) throw new SecurityException("The operator cannot mutate this draft");
    }

    private void authorizeOwner(UUID actor) {
        if (!owner.equals(actor)) throw new SecurityException("Only the draft owner can change collaborators");
    }

    public static final class DraftConflictException extends RuntimeException {
        private final long currentRevision;
        DraftConflictException(long currentRevision) {
            super("The draft revision is stale");
            this.currentRevision = currentRevision;
        }
        public long currentRevision() { return currentRevision; }
    }
}
