package com.enviouse.progressivestages.client.renderer;

import com.enviouse.progressivestages.common.config.StageConfig;
import com.enviouse.progressivestages.common.util.Constants;
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

    // v3.0.1: set while JEI is drawing its ingredient grid / recipe slots. Replaces the old
    // per-render StackWalker.walk() in LockedItemDecorator — walking the whole call stack once
    // per item slot per frame was O(slots x stackdepth) and collapsed framerate under EMI/JEI
    // (hundreds of ingredient slots), which is what the render-thread watchdog freeze was.
    // JEI mixins flip this at HEAD/TAIL of their draw methods; the decorator just reads it.
    private static final ThreadLocal<Boolean> insideJeiRender = ThreadLocal.withInitial(() -> false);

    /** Mark that JEI is rendering its own slots (JEI draws its own greying — we skip). */
    public static void enterJeiRender() {
        insideJeiRender.set(true);
    }

    /** Mark that JEI finished rendering its slots. */
    public static void exitJeiRender() {
        insideJeiRender.set(false);
    }

    /** True while a JEI ingredient-grid / recipe-slot draw is in progress on this thread. */
    public static boolean isInsideJeiRender() {
        return insideJeiRender.get();
    }

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

        switch (position.toLowerCase(java.util.Locale.ROOT)) {
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

        // Render the lock icon above the item. The decorator/EMI slot paths call us AFTER the
        // item model batch has been flushed by GuiGraphics, so a high-Z translate is enough to
        // paint on top — no per-item graphics.flush() (that forces a GPU draw-call flush and
        // destroys GUI batching; doing it once per locked slot per frame was a second, compounding
        // cause of the freeze at item-dense locations). blit() manages its own blend state.
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 1000); // High Z to render above everything
        graphics.blit(LOCK_TEXTURE, iconX, iconY, 0, 0, iconSize, iconSize, iconSize, iconSize);
        graphics.pose().popPose();
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
     * v2.3: fully obscure an item's icon. Paints an opaque panel over the slot and a centered
     * "?" glyph so the item is unrecognizable, then the normal lock icon on top. Used when the
     * gating stage sets {@code [display].obscure_icon = true} (or the global default is on).
     */
    public static void renderObscuredOverlay(GuiGraphics graphics, int x, int y, int slotSize) {
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 1000); // above the item model (GuiGraphics z-sorts the batch)
        // Opaque panel hides the underlying item icon entirely.
        graphics.fill(x, y, x + slotSize, y + slotSize, 0xFF1A1A1A);
        graphics.fill(x, y, x + slotSize, y + 1, 0xFF000000);
        graphics.fill(x, y + slotSize - 1, x + slotSize, y + slotSize, 0xFF000000);
        // Centered "?" so the player knows a hidden item occupies the slot.
        var font = net.minecraft.client.Minecraft.getInstance().font;
        String q = "?";
        int tw = font.width(q);
        graphics.drawString(font, q, x + (slotSize - tw) / 2, y + (slotSize - font.lineHeight) / 2 + 1,
            0xFFB0B0B0, false);
        graphics.pose().popPose();
        // Lock icon on top (respects show_lock_icon + position config).
        render(graphics, x, y, slotSize);
    }

    /**
     * Render just the lock icon without highlight (for search bar/favorites)
     */
    public static void renderLockIconOnly(GuiGraphics graphics, int x, int y, int slotSize) {
        render(graphics, x, y, slotSize);
    }

    /**
     * Render just the lock icon without highlight using default slot size (16)
     * Used by sidebar/search index rendering
     */
    public static void renderLockIconOnly(GuiGraphics graphics, int x, int y) {
        render(graphics, x, y, 16);
    }
}
