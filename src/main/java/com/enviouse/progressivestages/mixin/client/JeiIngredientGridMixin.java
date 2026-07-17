package com.enviouse.progressivestages.mixin.client;

import com.enviouse.progressivestages.client.renderer.LockIconRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Marks the JEI ingredient-list panel draw so {@link LockIconRenderer#isInsideJeiRender()} is true
 * while JEI paints its own item grid.
 *
 * <p>This is the replacement for the old per-render {@code StackWalker.getInstance().walk(...)} in
 * {@code LockedItemDecorator} that checked for {@code mezz.jei.*} frames. That walk ran once per
 * item slot per frame — O(slots × stack depth) — and the JEI/EMI ingredient grid renders HUNDREDS
 * of slots, so it collapsed the render thread (the 81-second-tick watchdog freeze the beta testers
 * reported). Flipping a ThreadLocal at the boundary of JEI's own draw is O(1) per frame instead.
 *
 * <p>{@code remap = false}: JEI's {@code IngredientGrid} is an implementation class shipped
 * un-obfuscated, not a Mojang-mapped class. {@code require = 0} so the mixin is a no-op when JEI is
 * absent or the internal class name shifts between JEI builds (the decorator still works, it just
 * loses the double-paint guard — which is harmless when JEI isn't there to double-paint).
 */
@Mixin(targets = "mezz.jei.gui.overlay.IngredientGrid", remap = false)
public class JeiIngredientGridMixin {

    @Inject(method = "draw", at = @At("HEAD"), require = 0)
    private void progressivestages$onDrawStart(Minecraft minecraft, GuiGraphics graphics, int mouseX, int mouseY, CallbackInfo ci) {
        LockIconRenderer.enterJeiRender();
    }

    @Inject(method = "draw", at = @At("RETURN"), require = 0)
    private void progressivestages$onDrawEnd(Minecraft minecraft, GuiGraphics graphics, int mouseX, int mouseY, CallbackInfo ci) {
        LockIconRenderer.exitJeiRender();
    }
}
