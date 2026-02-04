package com.enviouse.progressivestages.mixin.client;

import com.enviouse.progressivestages.client.ClientStageCache;
import com.enviouse.progressivestages.client.renderer.LockIconRenderer;
import com.enviouse.progressivestages.common.config.StageConfig;
import com.mojang.logging.LogUtils;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to add lock overlay to EmiStack rendering in search panel
 * This targets the internal ItemEmiStack class which handles item rendering
 */
@Mixin(targets = "dev.emi.emi.api.stack.ItemEmiStack", remap = false)
public abstract class EmiItemStackMixin {

    @Unique
    private static final Logger progressivestages$LOGGER = LogUtils.getLogger();

    /**
     * Inject after the stack is rendered to draw lock overlay
     * Method signature: render(GuiGraphics graphics, int x, int y, float delta, int flags)
     */
    @Inject(method = "render", at = @At("TAIL"), remap = false, require = 0)
    private void progressivestages$renderLockOverlay(GuiGraphics graphics, int x, int y, float delta, int flags, CallbackInfo ci) {
        try {
            // Skip if we're inside a SlotWidget (it handles its own rendering)
            if (LockIconRenderer.isInsideSlotWidget()) {
                return;
            }

            // Skip if we're in vanilla creative mode inventory to not affect it
            var screen = Minecraft.getInstance().screen;
            if (screen instanceof CreativeModeInventoryScreen) {
                return;
            }

            // Get the item from this stack
            EmiStack self = (EmiStack) (Object) this;
            if (self.isEmpty()) {
                return;
            }

            ItemStack itemStack = self.getItemStack();
            if (itemStack.isEmpty()) {
                return;
            }

            Item item = itemStack.getItem();
            if (ClientStageCache.isItemLocked(item)) {
                // Render lock icon only (no orange overlay) for search bar/favorites
                LockIconRenderer.renderLockIconOnly(graphics, x, y, 16);
            }
        } catch (Exception e) {
            if (StageConfig.isDebugLogging()) {
                progressivestages$LOGGER.debug("[ProgressiveStages] Error in ItemEmiStack mixin: {}", e.getMessage());
            }
        }
    }
}
