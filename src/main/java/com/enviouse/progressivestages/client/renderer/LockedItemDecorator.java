package com.enviouse.progressivestages.client.renderer;

import com.enviouse.progressivestages.client.ClientLockCache;
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
        if (stack == null || stack.isEmpty()) return false;
        // Creative bypass suppresses all lock UI (matches the tooltip + EMI paths). Guard here too
        // because isItemLocked()'s integrated-server LockRegistry fallback doesn't honor bypass.
        if (ClientLockCache.isCreativeBypass()) return false;
        // EMI mixin path owns rendering when inside its SlotWidget.
        if (LockIconRenderer.isInsideSlotWidget()) return false;
        // JEI draws its own greying — don't double-paint over its slot widgets. This is a cheap
        // ThreadLocal read set by the JEI grid/recipe-slot mixins (see LockIconRenderer). It
        // REPLACES the old StackWalker.getInstance().walk(...) that ran once per item slot per
        // frame — that full-stack walk was the render-thread freeze the watchdog caught, and it
        // scaled with the number of visible slots (catastrophic under EMI/JEI ingredient grids).
        // When JEI is absent the flag is simply never set, so non-JEI packs pay one boolean read.
        if (LockIconRenderer.isInsideJeiRender()) return false;

        if (!ClientStageCache.isItemLocked(stack.getItem())) return false;

        // v2.3: if the gating stage marks this item to be shown as an unknown item, paint an
        // opaque "?" panel over the icon (independent of the show_lock_icon toggle).
        if (ClientStageCache.shouldObscureItemIcon(stack.getItem())) {
            LockIconRenderer.renderObscuredOverlay(graphics, x, y, 16);
            return true;
        }

        if (!StageConfig.isShowLockIcon()) return false;
        LockIconRenderer.renderLockOverlay(graphics, x, y, 16);
        return true;
    }
}
