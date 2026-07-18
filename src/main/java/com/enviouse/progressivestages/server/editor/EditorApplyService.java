package com.enviouse.progressivestages.server.editor;

import com.enviouse.progressivestages.server.loader.StageFileLoader;
import com.google.gson.Gson;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class EditorApplyService {
    private static final Gson GSON = new Gson();
    private static final DateTimeFormatter IDS = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS")
        .withZone(ZoneOffset.UTC);

    private final Path root;
    private final Path backupRoot;

    EditorApplyService(Path root) {
        this.root = root.toAbsolutePath().normalize();
        this.backupRoot = this.root.resolve(".editor-backups");
    }

    synchronized EditorApplyResult apply(MinecraftServer server, UUID actor, EditorDraft draft,
                                         long currentRevision, boolean confirmed) {
        List<DraftDiffEntry> diff = draft.diff();
        DraftValidation validation = EditorDraftValidator.validate(draft.files(), draft.revision());
        if (!validation.valid()) return result(false, "", currentRevision, diff, validation, "validation_failed", "The draft is invalid");
        if (draft.baseConfigurationRevision() != currentRevision) {
            return result(false, "", currentRevision, diff, validation, "configuration_conflict",
                "The live configuration changed after this draft opened");
        }
        if (diff.isEmpty()) return result(true, "", currentRevision, diff, validation, "no_changes", "The draft has no changes");
        if (!confirmed) return result(false, "", currentRevision, diff, validation, "confirmation_required", "Review and confirm the semantic diff before apply");
        String transaction = IDS.format(Instant.now()) + "_" + actor.toString().substring(0, 8);
        Path backup = backupRoot.resolve(transaction);
        Map<String, String> before = draft.baseFiles();
        try {
            Files.createDirectories(backup);
            for (DraftDiffEntry entry : diff) {
                String old = before.get(entry.path());
                if (old == null) continue;
                Path target = backup.resolve(entry.path());
                Files.createDirectories(target.getParent());
                Files.writeString(target, old, StandardCharsets.UTF_8);
            }
            Files.writeString(backup.resolve("audit.json"), GSON.toJson(new EditorAuditEntry(transaction, actor,
                System.currentTimeMillis(), currentRevision, currentRevision + 1,
                diff.stream().map(DraftDiffEntry::path).toList(), false, "Prepared")), StandardCharsets.UTF_8);
            for (DraftDiffEntry entry : diff) {
                Path target = root.resolve(entry.path()).normalize();
                if (!target.startsWith(root)) throw new IOException("Draft path escaped the stages root");
                String next = draft.files().get(entry.path());
                if (next == null) {
                    Files.deleteIfExists(target);
                    prune(target.getParent());
                } else {
                    Files.createDirectories(target.getParent());
                    Path temporary = target.resolveSibling(target.getFileName() + ".editor.tmp");
                    Files.writeString(temporary, next, StandardCharsets.UTF_8);
                    move(temporary, target);
                }
            }
            if (!StageFileLoader.getInstance().reload()) {
                restore(diff, before);
                StageFileLoader.getInstance().reload();
                return result(false, transaction, currentRevision, diff, validation, "reload_failed",
                    String.join(". ", StageFileLoader.getInstance().getLastReloadErrors()));
            }
            StageFileLoader.getInstance().syncPlayersAfterReload();
            long after = StageFileLoader.getInstance().getCompiledSnapshot().revision();
            Files.writeString(backup.resolve("audit.json"), GSON.toJson(new EditorAuditEntry(transaction, actor,
                System.currentTimeMillis(), currentRevision, after, diff.stream().map(DraftDiffEntry::path).toList(),
                true, "Committed")), StandardCharsets.UTF_8);
            return result(true, transaction, after, diff, validation, "ok", "The draft was applied and synchronized");
        } catch (IOException | RuntimeException error) {
            try { restore(diff, before); StageFileLoader.getInstance().reload(); }
            catch (RuntimeException ignored) {}
            return result(false, transaction, currentRevision, diff, validation, "apply_failed", error.getMessage());
        }
    }

    EditorApplyResult rollback(MinecraftServer server, UUID actor, String transaction, boolean confirmed) {
        long current = StageFileLoader.getInstance().getCompiledSnapshot().revision();
        if (!confirmed) return result(false, transaction, current, List.of(), null, "confirmation_required", "Rollback requires confirmation");
        if (transaction == null || !transaction.matches("[a-zA-Z0-9_]+")) return result(false, transaction, current, List.of(), null, "invalid_transaction", "Invalid transaction id");
        Path backup = backupRoot.resolve(transaction).normalize();
        if (!backup.startsWith(backupRoot) || !Files.isDirectory(backup)) return result(false, transaction, current, List.of(), null, "missing_transaction", "The transaction backup was not found");
        try {
            EditorAuditEntry audit = GSON.fromJson(Files.readString(backup.resolve("audit.json")), EditorAuditEntry.class);
            for (String changed : audit.changedFiles()) {
                Path saved = backup.resolve(changed).normalize();
                Path target = root.resolve(EditorPaths.normalize(changed)).normalize();
                if (Files.isRegularFile(saved)) {
                    Files.createDirectories(target.getParent());
                    Files.copy(saved, target, StandardCopyOption.REPLACE_EXISTING);
                } else Files.deleteIfExists(target);
            }
            if (!StageFileLoader.getInstance().reload()) return result(false, transaction, current, List.of(), null,
                "rollback_reload_failed", String.join(". ", StageFileLoader.getInstance().getLastReloadErrors()));
            StageFileLoader.getInstance().syncPlayersAfterReload();
            return result(true, transaction, StageFileLoader.getInstance().getCompiledSnapshot().revision(),
                List.of(), null, "ok", "The editor transaction was rolled back and synchronized");
        } catch (IOException | RuntimeException error) {
            return result(false, transaction, current, List.of(), null, "rollback_failed", error.getMessage());
        }
    }

    private void restore(List<DraftDiffEntry> diff, Map<String, String> before) {
        for (DraftDiffEntry entry : diff) {
            Path target = root.resolve(entry.path()).normalize();
            String old = before.get(entry.path());
            try {
                if (old == null) Files.deleteIfExists(target);
                else { Files.createDirectories(target.getParent()); Files.writeString(target, old); }
            } catch (IOException error) { throw new IllegalStateException("Could not restore " + entry.path(), error); }
        }
    }

    private void prune(Path directory) throws IOException {
        while (directory != null && !directory.equals(root) && directory.startsWith(root)) {
            try (var entries = Files.list(directory)) { if (entries.findAny().isPresent()) return; }
            Files.deleteIfExists(directory);
            directory = directory.getParent();
        }
    }

    private static void move(Path source, Path target) throws IOException {
        try { Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (AtomicMoveNotSupportedException error) { Files.move(source, target, StandardCopyOption.REPLACE_EXISTING); }
    }

    private static EditorApplyResult result(boolean success, String transaction, long revision,
                                            List<DraftDiffEntry> diff, DraftValidation validation,
                                            String code, String explanation) {
        return new EditorApplyResult(success, transaction, revision, diff, validation, code, explanation);
    }
}
