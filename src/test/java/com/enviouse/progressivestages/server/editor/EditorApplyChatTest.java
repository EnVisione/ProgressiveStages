package com.enviouse.progressivestages.server.editor;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EditorApplyChatTest {
    @Test
    void listsEveryAppliedFileAndTheSynchronizationResult() {
        List<DraftDiffEntry> diff = List.of(
            new DraftDiffEntry("stages/wizard/stage.toml", DraftDiffEntry.ChangeType.ADDED,
                "", "one", 0, 130),
            new DraftDiffEntry("stages/wizard/rules.toml", DraftDiffEntry.ChangeType.MODIFIED,
                "one", "two", 40, 157));
        EditorApplyResult result = new EditorApplyResult(true, "transaction1", 42, diff,
            new DraftValidation(true, List.of(), List.of(), 1, 42), "", "applied");

        List<String> messages = EditorApplyChat.messages("EnVy", result);

        assertEquals(4, messages.size());
        assertTrue(messages.get(0).contains("EnVy applied 2 file changes"));
        assertTrue(messages.get(1).contains("[added] stages/wizard/stage.toml, 0 to 130 bytes"));
        assertTrue(messages.get(2).contains("[modified] stages/wizard/rules.toml, 40 to 157 bytes"));
        assertTrue(messages.get(3).contains("files reloaded and synchronized. revision 42"));
    }
}
