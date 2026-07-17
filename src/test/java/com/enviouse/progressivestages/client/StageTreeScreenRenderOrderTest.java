package com.enviouse.progressivestages.client;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StageTreeScreenRenderOrderTest {

    private static final Path PROJECT = Path.of(System.getProperty("progressivestages.projectDir"));
    private static final Path SCREEN = PROJECT.resolve(
        "src/main/java/com/enviouse/progressivestages/client/gui/StageTreeScreen.java");

    @Test
    void blurPassRunsOnceBeforeTheMapAndWidgets() throws IOException {
        String source = Files.readString(SCREEN);
        int start = source.indexOf("public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick)");
        int end = source.indexOf("private void renderMapBackground", start);
        assertTrue(start >= 0 && end > start);

        String renderMethod = source.substring(start, end);
        int background = renderMethod.indexOf("renderBackground(");
        int map = renderMethod.indexOf("renderMapBackground(");
        int frame = renderMethod.indexOf("renderWindowFrame(");
        int widgets = renderMethod.indexOf("for (var renderable : renderables)");

        assertEquals(1, occurrences(renderMethod, "renderBackground("));
        assertFalse(renderMethod.contains("super.render("));
        assertTrue(background < map);
        assertTrue(frame < widgets);
    }

    private static int occurrences(String value, String target) {
        int count = 0;
        for (int index = value.indexOf(target); index >= 0; index = value.indexOf(target, index + target.length())) {
            count++;
        }
        return count;
    }
}
