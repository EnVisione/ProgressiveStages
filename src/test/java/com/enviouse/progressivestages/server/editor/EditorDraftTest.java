package com.enviouse.progressivestages.server.editor;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EditorDraftTest {
    @Test
    void serializesMutationsAndSupportsSemanticUndoRedo() {
        UUID owner = UUID.randomUUID();
        EditorDraft draft = new EditorDraft(UUID.randomUUID(), owner, 5, 8,
            Map.of("stages/test/stage.toml", "old"));
        assertEquals(1, draft.mutate(owner, 0, "stages/test/stage.toml", "new"));
        assertEquals("new", draft.files().get("stages/test/stage.toml"));
        assertEquals(DraftDiffEntry.ChangeType.MODIFIED, draft.diff().getFirst().change());
        assertEquals(2, draft.undo(owner, 1));
        assertEquals("old", draft.files().get("stages/test/stage.toml"));
        assertEquals(3, draft.redo(owner, 2));
        assertEquals("new", draft.files().get("stages/test/stage.toml"));
        assertThrows(EditorDraft.DraftConflictException.class,
            () -> draft.mutate(owner, 1, "stages/test/rules.toml", "rules"));
        assertThrows(SecurityException.class,
            () -> draft.mutate(UUID.randomUUID(), 3, "stages/test/rules.toml", "rules"));
    }

    @Test
    void rejectsEscapingAndNonTomlPaths() {
        UUID owner = UUID.randomUUID();
        EditorDraft draft = new EditorDraft(UUID.randomUUID(), owner, 0, 0, Map.of());
        assertThrows(IllegalArgumentException.class, () -> draft.mutate(owner, 0, "../secret.toml", "bad"));
        assertThrows(IllegalArgumentException.class, () -> draft.mutate(owner, 0, "stages/test/script.js", "bad"));
    }

    @Test
    void appliedDraftBecomesTheNewCleanBase() {
        UUID owner = UUID.randomUUID();
        EditorDraft draft = new EditorDraft(UUID.randomUUID(), owner, 5, 8,
            Map.of("stages/test/stage.toml", "old"));
        draft.mutate(owner, 0, "stages/test/stage.toml", "new");

        draft.acceptApplied(6);

        assertEquals(6, draft.baseConfigurationRevision());
        assertEquals("new", draft.baseFiles().get("stages/test/stage.toml"));
        assertTrue(draft.diff().isEmpty());
        assertTrue(!draft.canUndo());
    }

    @Test
    void validatesACompleteThreeFileDraft() {
        Map<String, String> files = Map.of(
            "stages/test/stage.toml", "[schema]\nversion = 4\n[stage]\nid = \"test:editor\"\ndisplay_name = \"Editor\"\n",
            "stages/test/rules.toml", "[items]\nlocked = [\"minecraft:diamond\"]\n",
            "stages/test/progression.toml", "# Progression may be empty.\n");
        DraftValidation validation = EditorDraftValidator.validate(files, 3);
        assertTrue(validation.valid(), String.join(". ", validation.errors()));
        assertEquals(1, validation.stages());
    }
}
