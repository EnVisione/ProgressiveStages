package com.enviouse.progressivestages.client;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ChatMaskerTest {
    @Test
    void masksWholeTermsAcrossStylesWithoutMaskingPartsOfOtherWords() {
        Component input = Component.literal("Hello do you know ")
            .append(Component.literal("Cre").withStyle(ChatFormatting.AQUA))
            .append(Component.literal("ate").withStyle(ChatFormatting.GOLD))
            .append(Component.literal("? I can recreate it."));
        Component output = ChatMasker.mask(input, List.of("create"));
        assertEquals(input.getString(), output.getString());
        List<String> masked = new ArrayList<>();
        output.visit((style, text) -> {
            if (style.isObfuscated()) masked.add(text);
            return Optional.<Void>empty();
        }, Style.EMPTY);
        assertEquals(List.of("Cre", "ate"), masked);
    }

    @Test
    void leavesUnmatchedTextUntouched() {
        Component input = Component.literal("Recreate this machine");
        assertSame(input, ChatMasker.mask(input, List.of("create")));
    }

    @Test
    void changesTheMatcherWhenSyncedTermsChange() {
        Component input = Component.literal("Create a press");
        ChatMasker.mask(input, List.of("Create"));
        Component updated = ChatMasker.mask(input, List.of("press"));
        List<String> masked = new ArrayList<>();
        updated.visit((style, text) -> {
            if (style.isObfuscated()) masked.add(text);
            return Optional.<Void>empty();
        }, Style.EMPTY);
        assertEquals(List.of("press"), masked);
    }
}
