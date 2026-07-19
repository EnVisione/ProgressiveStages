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
        String favicon = resource("favicon.svg");
        assertTrue(script.contains("Apply changes"));
        assertTrue(script.contains("progressivestages.editor.secret"));
        assertTrue(script.contains("This tab does not have a Minecraft editor session"));
        assertTrue(script.contains("Create a new stage"));
        assertTrue(script.contains("Drag empty space to pan"));
        assertTrue(script.contains("Arrange and save"));
        assertTrue(script.contains("Use automatic layout"));
        assertTrue(script.contains("Fit graph"));
        assertTrue(script.contains("Namespace"));
        assertTrue(script.contains("Registry"));
        assertTrue(script.contains("Player UI"));
        assertTrue(script.contains("Rewards"));
        assertTrue(script.contains("ProgressiveStages lock logo"));
        assertFalse(script.contains("Stage studio"));
        assertFalse(script.contains("Inspector"));
        assertTrue(html.contains("app.js"));
        assertTrue(html.contains("favicon.svg"));
        assertTrue(favicon.contains("<svg"));
        assertTrue(css.contains("prefers-reduced-motion"));
        assertFalse(html.contains("https://"));
        assertFalse(css.contains("url(http"));
        assertFalse(script.contains("fetch(\"http"));
        assertFalse(script.contains("legacy.js"));
        assertNotNull(PackagedEditorAssetsTest.class.getResourceAsStream("/progressivestages.png"));
    }

    @Test
    void editorIsStageFirstGuidedAndDoesNotUsePromptBoxes() throws Exception {
        String css = resource("app.css");
        String script = resource("app.js");
        assertTrue(script.contains("No rules currently active"));
        assertTrue(script.contains("Choose the target"));
        assertTrue(script.contains("Stage rewards"));
        assertTrue(script.contains("Mobs and entities"));
        assertTrue(script.contains("Require every selected path"));
        assertTrue(script.contains("Would create a progression loop"));
        assertTrue(script.contains("How players obtain this stage"));
        assertTrue(script.contains("Buy with items or experience"));
        assertTrue(script.contains("Add a targeted mining bonus"));
        assertTrue(script.contains("Stage slots and stacking"));
        assertTrue(script.contains("replace_lowest_priority"));
        assertTrue(script.contains("Connect stages"));
        assertTrue(script.contains("Progression branch removed"));
        assertTrue(script.contains("Apply failed"));
        assertTrue(script.contains("How players lose this stage"));
        assertTrue(script.contains("Another player dies"));
        assertTrue(script.contains("Stay inside an assigned structure"));
        assertTrue(script.contains("Allow access to structure"));
        assertTrue(script.contains("Deny access to structure"));
        assertTrue(script.contains("Added files are green, modified files are yellow, and removed files are red"));
        assertTrue(script.contains("Java and KubeJS features registered"));
        assertTrue(css.contains("#e3aa32"));
        assertTrue(css.contains("#111214"));
        assertTrue(css.contains("--radius: 7px"));
        assertTrue(css.contains("dependency-preview"));
        assertTrue(css.contains("stage-graph"));
        assertTrue(css.contains("zoom-control"));
        assertTrue(css.contains("dialog-backdrop"));
        assertFalse(script.contains("prompt("));
    }

    private static String resource(String name) throws Exception {
        try (var input = PackagedEditorAssetsTest.class.getResourceAsStream(
                "/assets/progressivestages/editor/" + name)) {
            assertNotNull(input);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
