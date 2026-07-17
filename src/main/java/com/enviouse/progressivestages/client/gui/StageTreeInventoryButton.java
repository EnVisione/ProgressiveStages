package com.enviouse.progressivestages.client.gui;

import com.enviouse.progressivestages.client.ClientTriggerProgress;
import com.enviouse.progressivestages.common.util.Constants;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class StageTreeInventoryButton extends Button {

    private static final int WIDTH = 20;
    private static final int HEIGHT = 18;
    private static final int X_OFFSET = 126;
    private static final ResourceLocation LOCK_TEXTURE = ResourceLocation.fromNamespaceAndPath(
        Constants.MOD_ID, "textures/gui/lock_icon.png");

    private final InventoryScreen inventory;

    public StageTreeInventoryButton(InventoryScreen inventory) {
        super(0, 0, WIDTH, HEIGHT,
            Component.translatable("gui.progressivestages.tree.inventory_button"),
            button -> ClientTriggerProgress.requestFromServer(), DEFAULT_NARRATION);
        this.inventory = inventory;
        setTooltip(Tooltip.create(Component.translatable("gui.progressivestages.tree.inventory_button.tooltip")));
        updatePosition();
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        updatePosition();
        super.renderWidget(graphics, mouseX, mouseY, partialTick);
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 1.0F);
        graphics.blit(LOCK_TEXTURE, getX() + 3, getY() + 2, 14, 14,
            0.0F, 0.0F, 23, 23, 23, 23);
        graphics.pose().popPose();
    }

    @Override
    public void renderString(GuiGraphics graphics, Font font, int color) {
    }

    private void updatePosition() {
        setPosition(inventory.getGuiLeft() + X_OFFSET, inventory.height / 2 - 22);
    }
}
