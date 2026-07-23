package com.enviouse.progressivestages.client;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

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
        assertTrue(button.contains("textures/gui/lock_icon.png"));
        assertTrue(config.contains("define(\"client.show_inventory_button\", true)"));
        assertTrue(config.contains("defineInRange(\"client.inventory_button_x\", 126, -4096, 4096)"));
        assertTrue(config.contains("defineInRange(\"client.inventory_button_y\", 61, -4096, 4096)"));
        assertTrue(config.contains("defineInRange(\"client.inventory_button_width\", 20, 8, 256)"));
        assertTrue(config.contains("defineInRange(\"client.inventory_button_height\", 18, 8, 256)"));
        assertTrue(config.contains("defineInRange(\"client.inventory_button_icon_size\", 14, 4, 256)"));
        assertTrue(config.contains("isShowInventoryButton()"));
    }
}
