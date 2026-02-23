package com.enviouse.progressivestages.mixin.client;

import com.enviouse.progressivestages.client.ClientLockCache;
import com.enviouse.progressivestages.client.ClientStageCache;
import com.enviouse.progressivestages.client.renderer.LockIconRenderer;
import com.enviouse.progressivestages.common.config.StageConfig;
import com.mojang.logging.LogUtils;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.SlotWidget;
import dev.emi.emi.api.widget.Bounds;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to add lock overlay to EMI slot widgets
 */
@Mixin(value = SlotWidget.class, remap = false)
public abstract class EmiStackWidgetMixin {

    @Unique
    private static final Logger progressivestages$LOGGER = LogUtils.getLogger();

    @Unique
    private static long progressivestages$lastDebugLog = 0;

    @Unique
    private static int progressivestages$renderCallCount = 0;

    @Shadow
    @Final
    protected EmiIngredient stack;

    @Shadow
    public abstract Bounds getBounds();

    /**
     * Inject at the beginning of slot rendering to set flag
     */
    @Inject(method = "render", at = @At("HEAD"), require = 0)
    private void progressivestages$onRenderStart(GuiGraphics graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        LockIconRenderer.enterSlotWidget();
    }

    /**
     * Inject after slot rendering to draw lock overlay on locked items
     */
    @Inject(method = "render", at = @At("TAIL"), require = 0)
    private void progressivestages$renderLockOverlay(GuiGraphics graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        try {
            progressivestages$renderCallCount++;

            // Creative bypass - skip all lock rendering
            if (ClientLockCache.isCreativeBypass()) {
                LockIconRenderer.exitSlotWidget();
                return;
            }

            if (stack == null || stack.isEmpty()) {
                LockIconRenderer.exitSlotWidget();
                return;
            }

            // Check each stack in the ingredient
            boolean isLocked = progressivestages$checkIfLocked();

            if (isLocked) {
                Bounds bounds = getBounds();
                // Render lock overlay (highlight + icon) for recipe viewer
                LockIconRenderer.renderLockOverlay(graphics, bounds.x(), bounds.y(), bounds.width());

                // Debug logging (throttled)
                if (StageConfig.isDebugLogging()) {
                    long now = System.currentTimeMillis();
                    if (now - progressivestages$lastDebugLog > 2000) {
                        progressivestages$LOGGER.debug("[ProgressiveStages] EMI mixin rendered {} lock overlays in last 2 seconds",
                            progressivestages$renderCallCount);
                        progressivestages$renderCallCount = 0;
                        progressivestages$lastDebugLog = now;
                    }
                }
            }

            LockIconRenderer.exitSlotWidget();
        } catch (Exception e) {
            LockIconRenderer.exitSlotWidget();
            // Log errors in debug mode instead of silently ignoring
            if (StageConfig.isDebugLogging()) {
                progressivestages$LOGGER.debug("[ProgressiveStages] Error in EMI mixin render: {}", e.getMessage());
            }
        }
    }

    @Unique
    private boolean progressivestages$checkIfLocked() {
        for (EmiStack emiStack : stack.getEmiStacks()) {
            if (emiStack.isEmpty()) continue;

            ItemStack itemStack = emiStack.getItemStack();
            if (!itemStack.isEmpty()) {
                Item item = itemStack.getItem();
                if (ClientStageCache.isItemLocked(item)) {
                    return true;
                }
            }
        }
        return false;
    }
}
