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
        assertTrue(renderMethod.contains("g.pose().translate(0.0F, 0.0F, 200.0F)"));
    }

    @Test
    void mapDragCanBeginOnAStageNode() throws IOException {
        String source = Files.readString(SCREEN);

        assertTrue(source.contains("pressedNode = nodeAt(mouseX, mouseY)"));
        assertTrue(source.contains("dragDistance += Math.hypot(dragX, dragY)"));
        assertTrue(source.contains("StageTreeViewport.drag("));
        assertTrue(source.contains("if (dragDistance < 2.0 && pressedNode != null)"));
    }

    @Test
    void mapWheelUsesTheProgressiveSkillsCursorZoomModel() throws IOException {
        String source = Files.readString(SCREEN);

        assertTrue(source.contains("StageTreeViewport.zoomAt("));
        assertTrue(source.contains("mouseX - (mapLeft + mapRight) / 2.0D"));
        assertTrue(source.contains("mouseY - (mapTop + mapBottom) / 2.0D"));
        assertTrue(source.contains("zoom = camera.zoom()"));
    }

    @Test
    void inspectorUsesStructuredReadableModifierCards() throws IOException {
        String source = Files.readString(SCREEN);

        assertTrue(source.contains("renderModifierPreview(g, modifier"));
        assertTrue(source.contains("humanizeIdentifier(slotGroup)"));
        assertTrue(source.contains("selectorIconCache.computeIfAbsent"));
        assertTrue(source.contains("BuiltInRegistries.BLOCK.getTag"));
        assertTrue(source.contains("BuiltInRegistries.ITEM.getTag"));
        assertFalse(source.contains("for (String modifier : data.modifiers())"));
    }

    @Test
    void progressionWindowUsesTheCompactAdvancementFrame() throws IOException {
        String source = Files.readString(SCREEN);

        assertTrue(source.contains("Math.min(width - 24, 460)"));
        assertTrue(source.contains("Math.min(height - 38, 250)"));
        assertTrue(source.contains("Math.min(x + width, lineX + 42)"));
    }

    @Test
    void guideHelpStaysInsideTheSelectedInspector() throws IOException {
        String source = Files.readString(SCREEN);

        assertFalse(source.contains("renderGuideView(g, mouseX, mouseY)"));
        assertFalse(source.contains("gui.progressivestages.tree.tab.guide"));
        assertTrue(source.contains("gui.progressivestages.tree.guide.unlock.title"));
        assertTrue(source.contains("helpX = panelX + panelW - 31"));
        assertTrue(source.contains("unlockHelpOpen = !unlockHelpOpen"));
        assertTrue(source.contains("renderInspector(g, mouseX, mouseY)"));
        assertTrue(source.contains("guideStages().stream().findFirst()"));
        assertTrue(source.contains("!ClientStageCache.isStageGuideVisible(id)"));
        assertTrue(source.contains("id != null && ClientStageCache.isStageGuideVisible(id)"));
    }

    @Test
    void guideHelpClosesWhenStageFiltersChange() throws IOException {
        String source = Files.readString(SCREEN);
        int searchStart = source.indexOf("searchBox.setResponder(value -> {");
        int searchEnd = source.indexOf("searchBox.visible = true;", searchStart);
        int categoryStart = source.indexOf("private void selectCategory(int index)");
        int categoryEnd = source.indexOf("private int categoryOptionCount()", categoryStart);
        int ownedStart = source.indexOf("if (button == 0 && inside(mouseX, mouseY, ownedX");
        int ownedEnd = source.indexOf("if (button == 0 && inside(mouseX, mouseY, homeX", ownedStart);

        assertTrue(searchStart >= 0 && searchEnd > searchStart);
        assertTrue(categoryStart >= 0 && categoryEnd > categoryStart);
        assertTrue(ownedStart >= 0 && ownedEnd > ownedStart);
        assertTrue(source.contains("private void closeUnlockHelp()"));
        assertTrue(source.contains("unlockHelpOpen = false;\n        keyboardControlFocus = -1;\n        panelScroll = 0;"));
        assertTrue(source.substring(searchStart, searchEnd).contains("closeUnlockHelp();"));
        assertTrue(source.substring(categoryStart, categoryEnd).contains("closeUnlockHelp();"));
        assertTrue(source.substring(ownedStart, ownedEnd).contains("closeUnlockHelp();"));
    }

    @Test
    void helpAndCloseIconsUseTheSharedPixelRenderer() throws IOException {
        String source = Files.readString(SCREEN);

        assertTrue(source.contains("renderControlIcon(g, panelX + panelW - 16, panelY + 4, 13, 13,"));
        assertTrue(source.contains("CLOSE_CONTROL_ICON, closeHover ? RED : 0xFFFFFFFF)"));
        assertTrue(source.contains("renderControlIcon(g, helpX, helpY, helpW, helpH, HELP_CONTROL_ICON, GOLD)"));
        assertTrue(source.contains("private static void renderControlIcon("));
    }

    @Test
    void unlockHelpExplainsEveryConfiguredUnlockRoute() throws IOException {
        String source = Files.readString(SCREEN);
        String summary = Files.readString(PROJECT.resolve(
            "src/main/java/com/enviouse/progressivestages/client/GuideRequirementSummary.java"));

        int currentRequirements = source.indexOf("guideRequirementLines(selected, data)");
        int authoredInstructions = source.indexOf("if (!guide.howToUnlock().isBlank())", currentRequirements);
        assertTrue(currentRequirements >= 0);
        assertTrue(authoredInstructions > currentRequirements);
        assertTrue(source.contains("data.purchasable() && !data.purchaseBypassesTriggers()"));
        assertTrue(source.contains("guideRequirementText(id, data)"));
        assertTrue(summary.contains("gui.progressivestages.tree.guide.requirements.trigger.any"));
        assertTrue(summary.contains("gui.progressivestages.tree.guide.requirements.purchase.with_triggers"));
        assertTrue(source.contains("gui.progressivestages.tree.guide.progress.trigger_purchase"));
    }

    @Test
    void guideSummaryHidesThePurchaseAndSlotDetailsItAlreadyShows() throws IOException {
        String source = Files.readString(SCREEN);
        int start = source.indexOf("private void renderInspector(GuiGraphics g, int mouseX, int mouseY)");
        int end = source.indexOf("private int renderModifierPreview", start);

        assertTrue(start >= 0 && end > start);
        String inspector = source.substring(start, end);
        assertTrue(inspector.contains("data.purchasable() && !unlockHelpOpen"));
        assertTrue(inspector.contains("GuideRequirementSummary.displayPlan(requirementSummary, summaryEmbedded)"));
        assertTrue(inspector.contains("if (displayPlan.showGeneratedSummary())"));
        assertTrue(inspector.contains("if (!dependencies.isEmpty() && !suppressPrerequisiteDetails)"));
        assertTrue(inspector.contains("if (!slotGroup.isBlank() && !suppressSlotDetails)"));
    }

    @Test
    void guideEntryPointsRespectTheServerGuideSwitch() throws IOException {
        String source = Files.readString(SCREEN);
        String cacheSource = Files.readString(PROJECT.resolve(
            "src/main/java/com/enviouse/progressivestages/client/ClientStageCache.java"));
        String eventSource = Files.readString(PROJECT.resolve(
            "src/main/java/com/enviouse/progressivestages/client/ClientEventHandler.java"));
        String keySource = Files.readString(PROJECT.resolve(
            "src/main/java/com/enviouse/progressivestages/client/ClientModBusEvents.java"));

        assertTrue(source.contains("if (!ClientStageCache.isStageGuideEnabled()) return;"));
        assertTrue(source.contains("if (!ClientStageCache.isStageGuideVisible(selected)) unlockHelpOpen = false;"));
        assertTrue(cacheSource.contains("stageGuideEnabled = false"));
        assertTrue(cacheSource.contains("stageGuideEnabled = false;\n        ClientLockCache.clear()"));
        assertTrue(eventSource.contains("OPEN_GUIDE.consumeClick()"));
        assertTrue(eventSource.contains("ClientStageCache.isStageGuideEnabled()"));
        assertTrue(keySource.contains("public static final KeyMapping OPEN_GUIDE"));
    }

    @Test
    void categoryFiltersDoNotExposeHiddenOrUnrevealedStages() throws IOException {
        String source = Files.readString(SCREEN);
        int start = source.indexOf("private void refreshCategories()");
        int end = source.indexOf("private void selectCategory", start);
        assertTrue(start >= 0 && end > start);
        String method = source.substring(start, end);
        assertTrue(method.contains("filter(this::categoryVisible)"));
        assertTrue(method.contains("private boolean categoryVisible(StageId id)"));
        assertTrue(method.contains("ClientStageCache.isHidden(id)"));
        assertTrue(method.contains("return revealed(id, owned, !owned && dependenciesSatisfied(id))"));
    }

    @Test
    void categoryMenuRendersAboveStageItemDepth() throws IOException {
        String source = Files.readString(SCREEN);
        int start = source.indexOf("private void renderCategoryMenu(GuiGraphics g, int mouseX, int mouseY)");
        int end = source.indexOf("private void tileHorizontal", start);
        assertTrue(start >= 0 && end > start);

        String categoryMenu = source.substring(start, end);
        int push = categoryMenu.indexOf("g.pose().pushPose()");
        int foreground = categoryMenu.indexOf("g.pose().translate(0.0F, 0.0F, 1200.0F)");
        int panel = categoryMenu.indexOf("fillPixelRounded(g, categoryMenuX, categoryMenuY");
        int pop = categoryMenu.indexOf("g.pose().popPose()");

        assertTrue(push >= 0);
        assertTrue(push < foreground);
        assertTrue(foreground < panel);
        assertTrue(panel < pop);
        assertEquals(1, occurrences(categoryMenu, "g.pose().pushPose()"));
        assertEquals(1, occurrences(categoryMenu, "g.pose().popPose()"));
    }

    @Test
    void openCategoryOverlayConsumesItsClickAndWheelInput() throws IOException {
        String source = Files.readString(SCREEN);
        int clickStart = source.indexOf("public boolean mouseClicked(double mouseX, double mouseY, int button)");
        int clickEnd = source.indexOf("public boolean mouseDragged", clickStart);
        int scrollStart = source.indexOf("public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY)");
        int scrollEnd = source.indexOf("public boolean keyPressed", scrollStart);

        String click = source.substring(clickStart, clickEnd);
        String scroll = source.substring(scrollStart, scrollEnd);
        assertTrue(click.contains("categoryOpen = false;\n                return true;"));
        assertTrue(scroll.contains("if (categoryOpen && inside(mouseX, mouseY, categoryMenuX, categoryMenuY,"));
        assertTrue(scroll.contains("if (categoryOpen) {\n            return true;"));
        assertTrue(scroll.contains("return true;"));
    }

    private static int occurrences(String value, String target) {
        int count = 0;
        for (int index = value.indexOf(target); index >= 0; index = value.indexOf(target, index + target.length())) {
            count++;
        }
        return count;
    }
}
