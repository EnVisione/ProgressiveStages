package com.enviouse.progressivestages.client.gui;

import com.enviouse.progressivestages.client.ClientTriggerProgress;
import com.enviouse.progressivestages.common.config.StageConfig;
import com.enviouse.progressivestages.common.util.Constants;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class StageTreeInventoryButton extends Button {

    private static final ResourceLocation LOCK_TEXTURE = ResourceLocation.fromNamespaceAndPath(
        Constants.MOD_ID, "textures/gui/lock_icon.png");

    private final InventoryScreen inventory;
    private final int iconSize;

    public StageTreeInventoryButton(InventoryScreen inventory) {
        super(0, 0, StageConfig.getInventoryButtonWidth(), StageConfig.getInventoryButtonHeight(),
            Component.translatable("gui.progressivestages.tree.inventory_button"),
            button -> ClientTriggerProgress.requestFromServer(), DEFAULT_NARRATION);
        this.inventory = inventory;
        this.iconSize = StageConfig.getInventoryButtonIconSize();
        setTooltip(Tooltip.create(Component.translatable("gui.progressivestages.tree.inventory_button.tooltip")));
        updatePosition();
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        updatePosition();
        super.renderWidget(graphics, mouseX, mouseY, partialTick);
        int renderedIconSize = Math.min(iconSize, Math.max(1, Math.min(getWidth() - 2, getHeight() - 2)));
        int iconX = getX() + (getWidth() - renderedIconSize) / 2;
        int iconY = getY() + (getHeight() - renderedIconSize) / 2;
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 1.0F);
        graphics.blit(LOCK_TEXTURE, iconX, iconY, renderedIconSize, renderedIconSize,
            0.0F, 0.0F, 23, 23, 23, 23);
        graphics.pose().popPose();
        if (isHoveredOrFocused()) {
            renderPixelRoundedOutline(graphics, getX(), getY(), getWidth(), getHeight(), 0xFFFFC74A);
        }
    }

    @Override
    public void renderString(GuiGraphics graphics, Font font, int color) {
    }

    private void updatePosition() {
        setPosition(inventory.getGuiLeft() + StageConfig.getInventoryButtonX(),
            inventory.getGuiTop() + StageConfig.getInventoryButtonY());
    }

    private static void renderPixelRoundedOutline(
            GuiGraphics graphics,
            int x,
            int y,
            int width,
            int height,
            int color
    ) {
        if (width < 5 || height < 5) {
            graphics.renderOutline(x, y, width, height, color);
            return;
        }
        graphics.fill(x + 2, y, x + width - 2, y + 1, color);
        graphics.fill(x + 1, y + 1, x + 2, y + 2, color);
        graphics.fill(x, y + 2, x + 1, y + height - 2, color);
        graphics.fill(x + 1, y + height - 2, x + 2, y + height - 1, color);
        graphics.fill(x + 2, y + height - 1, x + width - 2, y + height, color);
        graphics.fill(x + width - 2, y + height - 2, x + width - 1, y + height - 1, color);
        graphics.fill(x + width - 1, y + 2, x + width, y + height - 2, color);
        graphics.fill(x + width - 2, y + 1, x + width - 1, y + 2, color);
    }
}
