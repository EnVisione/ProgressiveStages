package com.enviouse.progressivestages.mixin.client;

import com.enviouse.progressivestages.client.renderer.LockIconRenderer;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Marks JEI recipe-viewer slot draws so {@link LockIconRenderer#isInsideJeiRender()} is true while
 * JEI paints ingredients inside a recipe. Companion to {@link JeiIngredientGridMixin} — the grid
 * covers the ingredient-list panel, this covers the recipe GUI. Together they replace the old
 * StackWalker frame-scan in {@code LockedItemDecorator} (see that mixin for the freeze rationale).
 *
 * <p>{@code remap = false} / {@code require = 0}: {@code RecipeSlot} is a JEI implementation class;
 * no-op if absent or renamed in a future JEI build.
 */
@Mixin(targets = "mezz.jei.library.gui.ingredients.RecipeSlot", remap = false)
public class JeiRecipeSlotMixin {

    @Inject(method = "draw", at = @At("HEAD"), require = 0)
    private void progressivestages$onDrawStart(GuiGraphics graphics, CallbackInfo ci) {
        LockIconRenderer.enterJeiRender();
    }

    @Inject(method = "draw", at = @At("RETURN"), require = 0)
    private void progressivestages$onDrawEnd(GuiGraphics graphics, CallbackInfo ci) {
        LockIconRenderer.exitJeiRender();
    }
}
