package com.enviouse.progressivestages.client.renderer;

import com.enviouse.progressivestages.common.config.StageConfig;
import com.enviouse.progressivestages.common.util.Constants;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

/**
 * Renders the lock icon overlay on items
 */
public class LockIconRenderer {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation LOCK_TEXTURE =
        ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "textures/gui/lock_icon.png");

    // Debug render counter (reset each frame)
    private static int debugRenderCount = 0;
    private static long lastDebugLogTime = 0;

    // Flag to prevent double rendering when inside SlotWidget
    private static final ThreadLocal<Boolean> insideSlotWidget = ThreadLocal.withInitial(() -> false);

    /**
     * Mark that we're entering SlotWidget rendering (prevents ItemEmiStack from double-rendering)
     */
    public static void enterSlotWidget() {
        insideSlotWidget.set(true);
    }

    /**
     * Mark that we're leaving SlotWidget rendering
     */
    public static void exitSlotWidget() {
        insideSlotWidget.set(false);
    }

    /**
     * Check if we're currently inside SlotWidget rendering
     */
    public static boolean isInsideSlotWidget() {
        return insideSlotWidget.get();
    }

    /**
     * Render a lock icon at the specified position
     * @param graphics The graphics context
     * @param x X position of the slot
     * @param y Y position of the slot
     * @param slotSize Size of the slot (usually 16 or 18)
     */
    public static void render(GuiGraphics graphics, int x, int y, int slotSize) {
        if (!StageConfig.isShowLockIcon()) {
            return;
        }

        // Debug logging (throttled to once per second)
        if (StageConfig.isDebugLogging()) {
            debugRenderCount++;
            long now = System.currentTimeMillis();
            if (now - lastDebugLogTime > 1000) {
                LOGGER.debug("[ProgressiveStages] Lock icon rendered {} times in last second at ({}, {})",
                    debugRenderCount, x, y);
                debugRenderCount = 0;
                lastDebugLogTime = now;
            }
        }

        int iconSize = StageConfig.getLockIconSize();
        String position = StageConfig.getLockIconPosition();

        // Calculate position based on config
        int iconX = x;
        int iconY = y;
        int padding = 1;

        switch (position.toLowerCase()) {
            case "top_left":
                iconX = x + padding;
                iconY = y + padding;
                break;
            case "top_right":
                iconX = x + slotSize - iconSize - padding;
                iconY = y + padding;
                break;
            case "bottom_left":
                iconX = x + padding;
                iconY = y + slotSize - iconSize - padding;
                break;
            case "bottom_right":
                iconX = x + slotSize - iconSize - padding;
                iconY = y + slotSize - iconSize - padding;
                break;
            case "center":
                iconX = x + (slotSize - iconSize) / 2;
                iconY = y + (slotSize - iconSize) / 2;
                break;
        }

        // Render the lock icon - push z-level to render ON TOP of items
        RenderSystem.enableBlend();
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 200); // Move above item layer (items render at z=150)
        graphics.blit(LOCK_TEXTURE, iconX, iconY, 0, 0, iconSize, iconSize, iconSize, iconSize);
        graphics.pose().popPose();
        RenderSystem.disableBlend();
    }

    /**
     * Render a semi-transparent highlight overlay on a slot
     * @param graphics The graphics context
     * @param x X position of the slot
     * @param y Y position of the slot
     * @param width Width of the slot
     * @param height Height of the slot
     */
    public static void renderHighlight(GuiGraphics graphics, int x, int y, int width, int height) {
        if (!StageConfig.isShowHighlight()) {
            return;
        }

        int color = StageConfig.getHighlightColor();
        // Push z-level to render above items
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 199); // Just below lock icon
        graphics.fill(x, y, x + width, y + height, color);
        graphics.pose().popPose();
    }

    /**
     * Render both highlight and lock icon (for recipe viewer)
     */
    public static void renderLockOverlay(GuiGraphics graphics, int x, int y, int slotSize) {
        renderHighlight(graphics, x, y, slotSize, slotSize);
        render(graphics, x, y, slotSize);
    }

    /**
     * Render just the lock icon without highlight (for search bar/favorites)
     */
    public static void renderLockIconOnly(GuiGraphics graphics, int x, int y, int slotSize) {
        render(graphics, x, y, slotSize);
    }
}
