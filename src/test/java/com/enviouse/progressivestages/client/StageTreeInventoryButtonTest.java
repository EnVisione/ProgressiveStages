package com.enviouse.progressivestages.client;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StageTreeInventoryButtonTest {

    private static final Path PROJECT = Path.of(System.getProperty("progressivestages.projectDir"));

    @Test
    void survivalInventoryRegistersAConfigurableProgressionMapButton() throws IOException {
        String handler = Files.readString(PROJECT.resolve(
            "src/main/java/com/enviouse/progressivestages/client/ClientEventHandler.java"));
        String button = Files.readString(PROJECT.resolve(
            "src/main/java/com/enviouse/progressivestages/client/gui/StageTreeInventoryButton.java"));
        String config = Files.readString(PROJECT.resolve(
            "src/main/java/com/enviouse/progressivestages/common/config/StageConfig.java"));

        assertTrue(handler.contains("onInventoryScreenInit(ScreenEvent.Init.Post event)"));
        assertTrue(handler.contains("StageConfig.isShowInventoryButton()"));
        assertTrue(handler.contains("new com.enviouse.progressivestages.client.gui.StageTreeInventoryButton(inventory)"));
        assertTrue(button.contains("inventory.getGuiLeft() + StageConfig.getInventoryButtonX()"));
        assertTrue(button.contains("inventory.getGuiTop() + StageConfig.getInventoryButtonY()"));
        assertTrue(button.contains("StageConfig.getInventoryButtonWidth()"));
        assertTrue(button.contains("StageConfig.getInventoryButtonHeight()"));
        assertTrue(button.contains("StageConfig.getInventoryButtonIconSize()"));
        assertTrue(button.contains("(getWidth() - renderedIconSize) / 2"));
        assertTrue(button.contains("(getHeight() - renderedIconSize) / 2"));
        assertTrue(button.contains("ClientTriggerProgress.requestFromServer()"));
        assertTrue(button.contains("textures/gui/stage_map_button.png"));
        assertTrue(button.contains("textures/gui/stage_map_button_highlighted.png"));
        assertTrue(button.contains("textures/gui/stage_map_icon.png"));
        assertFalse(button.contains("textures/gui/lock_icon.png"));
        assertFalse(button.contains("super.renderWidget"));
        assertTrue(button.contains("isHoveredOrFocused()"));
        assertTrue(config.contains("define(\"client.show_inventory_button\", true)"));
        assertTrue(config.contains("defineInRange(\"client.inventory_button_x\", 126, -4096, 4096)"));
        assertTrue(config.contains("defineInRange(\"client.inventory_button_y\", 61, -4096, 4096)"));
        assertTrue(config.contains("defineInRange(\"client.inventory_button_width\", 20, 8, 256)"));
        assertTrue(config.contains("defineInRange(\"client.inventory_button_height\", 18, 8, 256)"));
        assertTrue(config.contains("defineInRange(\"client.inventory_button_icon_size\", 14, 4, 256)"));
        assertTrue(config.contains("isShowInventoryButton()"));

        Path textures = PROJECT.resolve("src/main/resources/assets/progressivestages/textures/gui");
        var normal = ImageIO.read(textures.resolve("stage_map_button.png").toFile());
        var highlighted = ImageIO.read(textures.resolve("stage_map_button_highlighted.png").toFile());
        var icon = ImageIO.read(textures.resolve("stage_map_icon.png").toFile());
        assertNotNull(normal);
        assertNotNull(highlighted);
        assertNotNull(icon);
        assertEquals(20, normal.getWidth());
        assertEquals(18, normal.getHeight());
        assertEquals(20, highlighted.getWidth());
        assertEquals(18, highlighted.getHeight());
        assertEquals(16, icon.getWidth());
        assertEquals(16, icon.getHeight());
        assertEquals(0, normal.getRGB(0, 0) >>> 24);
        assertEquals(0, highlighted.getRGB(0, 0) >>> 24);
        assertEquals(0, icon.getRGB(0, 0) >>> 24);
        assertTrue(normal.getRGB(normal.getWidth() / 2, normal.getHeight() / 2) >>> 24 > 0);
        assertTrue(highlighted.getRGB(highlighted.getWidth() / 2,
            highlighted.getHeight() / 2) >>> 24 > 0);
    }
}
