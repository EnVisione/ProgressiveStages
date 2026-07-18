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
        assertTrue(script.contains("Easy builder"));
        assertTrue(script.contains("Create stage"));
        assertTrue(script.contains("Stage graph"));
        assertTrue(script.contains("Arrange paths upward"));
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

    @Test
    void editorIsStageFirstGuidedAndDoesNotUsePromptBoxes() throws Exception {
        String css = resource("app.css");
        String controller = resource("legacy.js");
        assertTrue(controller.contains("No rules currently active"));
        assertTrue(controller.contains("What kind of thing"));
        assertTrue(controller.contains("Only show this mod"));
        assertTrue(controller.contains("Temporary rules need a three file stage"));
        assertTrue(controller.contains("Stage rewards"));
        assertTrue(controller.contains("entities: { label: \"Mobs and entities\", catalog: \"entities\""));
        assertTrue(controller.contains("dragRule"));
        assertTrue(controller.contains("Build the path into"));
        assertTrue(controller.contains("Require every selected path"));
        assertTrue(controller.contains("creates a loop"));
        assertTrue(controller.contains("How players obtain this stage"));
        assertTrue(controller.contains("Buy with items"));
        assertTrue(controller.contains("Find an item on this server"));
        assertTrue(controller.contains("Add a targeted mining bonus"));
        assertTrue(css.contains("#e3aa32"));
        assertTrue(css.contains("#101114"));
        assertTrue(css.contains("dependency-visualizer"));
        assertTrue(css.contains("graph-viewport"));
        assertTrue(css.contains("modal-backdrop"));
        assertFalse(controller.contains("prompt("));
        assertFalse(controller.contains("Stages and files"));
    }

    private static String resource(String name) throws Exception {
        try (var input = PackagedEditorAssetsTest.class.getResourceAsStream(
                "/assets/progressivestages/editor/" + name)) {
            assertNotNull(input);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
