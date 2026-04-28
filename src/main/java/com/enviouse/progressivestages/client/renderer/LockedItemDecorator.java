package com.enviouse.progressivestages.client.renderer;

import com.enviouse.progressivestages.client.ClientStageCache;
import com.enviouse.progressivestages.common.config.StageConfig;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.IItemDecorator;

/**
 * Renders the lock icon on every Minecraft inventory slot (vanilla containers,
 * hotbar, modded chests, shulker boxes, etc.) by hooking the Forge/NeoForge
 * {@code IItemDecorator} pipeline. Skips rendering when:
 * <ul>
 *   <li>Lock-icon display is disabled in config.</li>
 *   <li>The current call originates from JEI (JEI draws its own greying overlay).</li>
 *   <li>The current call is nested inside the EMI {@code SlotWidget} mixin
 *       (EMI gets its own dedicated overlay path; avoids double-render).</li>
 *   <li>The stack is not actually locked for the current player.</li>
 * </ul>
 */
public class LockedItemDecorator implements IItemDecorator {

    @Override
    public boolean render(GuiGraphics graphics, Font font, ItemStack stack, int x, int y) {
        if (!StageConfig.isShowLockIcon()) return false;
        if (stack == null || stack.isEmpty()) return false;
        // EMI mixin path owns rendering when inside its SlotWidget.
        if (LockIconRenderer.isInsideSlotWidget()) return false;
        // JEI draws its own greying — don't double-paint over its slot widgets.
        if (isCalledFromJei()) return false;

        if (!ClientStageCache.isItemLocked(stack.getItem())) return false;

        LockIconRenderer.renderLockOverlay(graphics, x, y, 16);
        return true;
    }

    /**
     * Walk the call stack for any frame originating in {@code mezz.jei.*}.
     * Cheap (StackWalker is lazy and short-circuits) and avoids paying for
     * full stack capture per render.
     */
    private static boolean isCalledFromJei() {
        try {
            return StackWalker.getInstance().walk(s ->
                s.anyMatch(f -> f.getClassName().startsWith("mezz.jei.")));
        } catch (Throwable t) {
            return false;
        }
    }
}
