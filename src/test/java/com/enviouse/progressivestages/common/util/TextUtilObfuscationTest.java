package com.enviouse.progressivestages.common.util;

import net.minecraft.network.chat.Style;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TextUtilObfuscationTest {
    @Test
    void supportsBothSectionAndAmpersandFormattingCodesWithReset() {
        var component = TextUtil.parseColorCodes("plain §kSecret§r and &kHidden&r done");
        List<String> obfuscated = new ArrayList<>();
        component.visit((style, text) -> {
            if (style.isObfuscated()) obfuscated.add(text);
            return Optional.<Void>empty();
        }, Style.EMPTY);
        assertEquals(List.of("Secret", "Hidden"), obfuscated);
        assertEquals("plain Secret and Hidden done", component.getString());
    }
}
