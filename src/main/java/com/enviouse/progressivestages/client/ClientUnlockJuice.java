package com.enviouse.progressivestages.client;

import com.enviouse.progressivestages.common.util.TextUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;

/**
 * v2.4 client state for the optional "unlock juice": shows the toast popup when a stage is unlocked,
 * and holds the active-goal HUD bar data (label + % to next stage) pushed by the server.
 */
public final class ClientUnlockJuice {

    private static volatile String goalLabel = "";
    private static volatile float goalPercent = 0f;
    private static volatile boolean goalShow = false;

    private ClientUnlockJuice() {}

    /** Show an advancement-style toast for an unlocked stage. */
    public static void showToast(String title, String subtitle, String iconItem) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        var titleC = TextUtil.parseColorCodes(title.isEmpty() ? "Stage Unlocked" : title);
        var msgC = subtitle.isEmpty() ? null : TextUtil.parseColorCodes(subtitle);
        SystemToast.add(mc.getToasts(), new SystemToast.SystemToastId(5000L), titleC, msgC);
    }

    public static void setActiveGoal(String label, float percent, boolean show) {
        goalLabel = label == null ? "" : label;
        goalPercent = Math.max(0f, Math.min(1f, percent));
        goalShow = show;
    }

    public static boolean showGoal() { return goalShow && !goalLabel.isEmpty(); }
    public static String goalLabel() { return goalLabel; }
    public static float goalPercent() { return goalPercent; }

    public static void clear() {
        goalShow = false;
        goalLabel = "";
        goalPercent = 0f;
    }

    /**
     * GUI layer: a thin BLUE "progress to next stage" bar just above the vanilla XP bar. Only drawn
     * when the server has pushed an active goal (a not-yet-owned stage with {@code [unlock].hud_bar}).
     */
    public static void renderHud(net.minecraft.client.gui.GuiGraphics g, net.minecraft.client.DeltaTracker delta) {
        if (!showGoal()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.screen != null) return;

        int sw = g.guiWidth();
        int sh = g.guiHeight();
        int barW = 182, barH = 5;
        int x = (sw - barW) / 2;
        int y = sh - 34; // sits just above the XP bar
        g.fill(x - 1, y - 1, x + barW + 1, y + barH + 1, 0xC0000000);
        g.fill(x, y, x + barW, y + barH, 0xFF1E2630);
        int fill = (int) (barW * goalPercent);
        if (fill > 0) g.fill(x, y, x + fill, y + barH, 0xFF3FA0FF);
        String label = goalLabel + "  " + Math.round(goalPercent * 100f) + "%";
        g.drawString(mc.font, label, x, y - 9, 0xFF9FD4FF, true);
    }
}
