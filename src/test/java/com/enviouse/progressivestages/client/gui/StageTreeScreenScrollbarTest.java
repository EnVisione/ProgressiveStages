package com.enviouse.progressivestages.client.gui;

import com.enviouse.progressivestages.common.api.StageId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class StageTreeScreenScrollbarTest {

    @Test
    void mouseWheelOverInspectorScrollsTheSelectedPanel() throws ReflectiveOperationException {
        StageTreeScreen screen = overflowingScreen();
        setInt(screen, "panelScroll", 40);

        assertTrue(screen.mouseScrolled(30, 75, 0, -1));
        assertEquals(52, intField(screen, "panelScroll"));
        assertTrue(screen.mouseScrolled(30, 75, 0, 1));
        assertEquals(40, intField(screen, "panelScroll"));
    }

    @Test
    void openCategoryMenuDoesNotStealWheelOverGuideText() throws ReflectiveOperationException {
        StageTreeScreen screen = overflowingScreen();
        set(screen, "unlockHelpOpen", true);
        set(screen, "categoryOpen", true);
        setInt(screen, "categoryMenuX", 120);
        setInt(screen, "categoryMenuY", 20);
        setInt(screen, "categoryMenuW", 80);
        setInt(screen, "categoryMenuH", 100);

        assertTrue(screen.mouseScrolled(30, 75, 0, -1));
        assertEquals(12, intField(screen, "panelScroll"));
    }

    @Test
    void wheelOverGuideTextDoesNotZoomTheMapWhenNoScrollingIsNeeded()
            throws ReflectiveOperationException {
        StageTreeScreen screen = overflowingScreen();
        setInt(screen, "panelMax", 0);
        setInt(screen, "mapLeft", 0);
        setInt(screen, "mapTop", 0);
        setInt(screen, "mapRight", 200);
        setInt(screen, "mapBottom", 200);
        set(screen, "unlockHelpOpen", true);

        assertTrue(screen.mouseScrolled(30, 75, 0, -1));
        assertEquals(0, intField(screen, "panelScroll"));
        assertEquals(1.0D, doubleField(screen, "zoom"));
    }

    @Test
    void openCategoryMenuStillBlocksWheelZoomOverTheMap() throws ReflectiveOperationException {
        StageTreeScreen screen = overflowingScreen();
        set(screen, "categoryOpen", true);
        setInt(screen, "mapLeft", 0);
        setInt(screen, "mapTop", 0);
        setInt(screen, "mapRight", 200);
        setInt(screen, "mapBottom", 200);

        assertTrue(screen.mouseScrolled(150, 150, 0, -1));
        assertEquals(1.0D, doubleField(screen, "zoom"));
    }

    @Test
    void pageDownScrollsTheOpenGuideBody() throws ReflectiveOperationException {
        StageTreeScreen screen = overflowingScreen();
        setInt(screen, "panelScroll", 40);
        setInt(screen, "panelScrollTrackHeight", 100);
        set(screen, "unlockHelpOpen", true);

        assertTrue(screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_DOWN, 0, 0));
        assertEquals(100, intField(screen, "panelScroll"));
    }

    @Test
    void mapsThumbDragToPanelScrollRange() {
        assertEquals(0, StageTreeScreen.panelScrollForThumbPosition(
            125, 100, 200, 50, 25, 900));
        assertEquals(450, StageTreeScreen.panelScrollForThumbPosition(
            200, 100, 200, 50, 25, 900));
        assertEquals(900, StageTreeScreen.panelScrollForThumbPosition(
            275, 100, 200, 50, 25, 900));
    }

    @Test
    void clampsThumbDragAndHandlesNonScrollableTracks() {
        assertEquals(0, StageTreeScreen.panelScrollForThumbPosition(
            10, 100, 200, 50, 25, 900));
        assertEquals(900, StageTreeScreen.panelScrollForThumbPosition(
            500, 100, 200, 50, 25, 900));
        assertEquals(0, StageTreeScreen.panelScrollForThumbPosition(
            125, 100, 50, 50, 25, 900));
        assertEquals(0, StageTreeScreen.panelScrollForThumbPosition(
            125, 100, 200, 50, 25, 0));
    }

    @Test
    void clampsWheelAndKeyboardTargetsToVisibleContentRange() {
        assertEquals(0, StageTreeScreen.clampPanelScroll(-10, 900));
        assertEquals(450, StageTreeScreen.clampPanelScroll(450, 900));
        assertEquals(900, StageTreeScreen.clampPanelScroll(1200, 900));
        assertEquals(0, StageTreeScreen.clampPanelScroll(10, 0));
    }

    private static StageTreeScreen overflowingScreen() throws ReflectiveOperationException {
        StageTreeScreen screen = new StageTreeScreen();
        set(screen, "selected", StageId.of("scroll-test"));
        setInt(screen, "panelX", 10);
        setInt(screen, "panelY", 20);
        setInt(screen, "panelW", 100);
        setInt(screen, "panelH", 100);
        setInt(screen, "panelMax", 100);
        return screen;
    }

    private static void setInt(StageTreeScreen screen, String fieldName, int value)
            throws ReflectiveOperationException {
        set(screen, fieldName, value);
    }

    private static int intField(StageTreeScreen screen, String fieldName) throws ReflectiveOperationException {
        Field field = StageTreeScreen.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getInt(screen);
    }

    private static double doubleField(StageTreeScreen screen, String fieldName) throws ReflectiveOperationException {
        Field field = StageTreeScreen.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getDouble(screen);
    }

    private static void set(StageTreeScreen screen, String fieldName, Object value)
            throws ReflectiveOperationException {
        Field field = StageTreeScreen.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(screen, value);
    }
}
