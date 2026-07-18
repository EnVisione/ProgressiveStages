package com.enviouse.progressivestages.client.editor;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PackagedEditorAssetsTest {
    @Test
    void editorIsSelfContainedAccessibleAndHasNoExternalRequests() throws Exception {
        String html = resource("index.html");
        String css = resource("app.css");
        String script = resource("app.js");
        String controller = resource("legacy.js");
        assertTrue(script.contains("Visual form"));
        assertTrue(script.contains("Registry"));
        assertTrue(script.contains("Review and apply"));
        assertTrue(html.contains("app.js"));
        assertTrue(css.contains("prefers-reduced-motion"));
        assertTrue(controller.contains("Authorization"));
        assertTrue(controller.contains("catalog"));
        assertTrue(controller.contains("undo"));
        assertFalse(html.contains("https://"));
        assertFalse(css.contains("url(http"));
        assertFalse(script.contains("https://"));
        assertFalse(controller.contains("https://"));
    }

    private static String resource(String name) throws Exception {
        try (var input = PackagedEditorAssetsTest.class.getResourceAsStream(
                "/assets/progressivestages/editor/" + name)) {
            assertNotNull(input);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
