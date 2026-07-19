package com.enviouse.progressivestages.server.editor;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
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

        List<Component> messages = EditorApplyChat.messages("EnVy", result);

        assertEquals(4, messages.size());
        assertTrue(messages.get(0).getString().contains("EnVy applied 2 file changes"));
        assertEquals(TextColor.fromLegacyFormat(ChatFormatting.GOLD), messages.get(0).getStyle().getColor());
        assertTrue(messages.get(1).getString().contains("[added] stages/wizard/stage.toml, 0 to 130 bytes"));
        assertEquals(TextColor.fromLegacyFormat(ChatFormatting.GREEN), messages.get(1).getStyle().getColor());
        assertTrue(messages.get(2).getString().contains("[modified] stages/wizard/rules.toml, 40 to 157 bytes"));
        assertEquals(TextColor.fromLegacyFormat(ChatFormatting.YELLOW), messages.get(2).getStyle().getColor());
        assertTrue(messages.get(3).getString().contains("files reloaded and synchronized. revision 42"));
        assertEquals(TextColor.fromLegacyFormat(ChatFormatting.GREEN), messages.get(3).getStyle().getColor());
    }

    @Test
    void noFileChangesProduceNoOperatorMessages() {
        EditorApplyResult result = new EditorApplyResult(true, "", 42, List.of(),
            new DraftValidation(true, List.of(), List.of(), 1, 42), "", "no changes");

        assertTrue(EditorApplyChat.messages("EnVy", result).isEmpty());
    }
}
