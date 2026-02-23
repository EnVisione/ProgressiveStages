package com.enviouse.progressivestages.mixin.client;

import com.enviouse.progressivestages.client.ClientLockCache;
import com.enviouse.progressivestages.client.ClientStageCache;
import com.enviouse.progressivestages.client.renderer.LockIconRenderer;
import com.enviouse.progressivestages.common.config.StageConfig;
import com.mojang.logging.LogUtils;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.runtime.EmiDrawContext;
import dev.emi.emi.screen.EmiScreenManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
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

import java.util.List;

@Mixin(value = EmiScreenManager.ScreenSpace.class, remap = false)
public abstract class EmiScreenManagerMixin {

    @Unique
    private static final Logger progressivestages$LOGGER = LogUtils.getLogger();

    @Shadow @Final public int th;
    @Shadow @Final public int pageSize;

    @Shadow
    public abstract List<? extends EmiIngredient> getStacks();

    @Shadow
    public abstract int getWidth(int y);

    @Shadow
    public abstract int getX(int x, int y);

    @Shadow
    public abstract int getY(int x, int y);

    @Inject(method = "render", at = @At("TAIL"), require = 0)
    private void progressivestages$renderLocksAfterBatch(
            EmiDrawContext context, int mouseX, int mouseY,
            float delta, int startIndex, CallbackInfo ci) {

        if (ClientLockCache.isCreativeBypass()) return;
        if (!StageConfig.isShowLockIcon()) return;
        if (this.pageSize <= 0) return;

        try {
            List<? extends EmiIngredient> stacks = getStacks();
            if (stacks == null || stacks.isEmpty()) return;

            GuiGraphics graphics = context.raw();
            int end = Math.min(startIndex + pageSize, stacks.size());
            int i = startIndex;

            outer:
            for (int yo = 0; yo < this.th; yo++) {
                int rowWidth = getWidth(yo);
                for (int xo = 0; xo < rowWidth; xo++) {
                    // Break out of both loops when we've processed all visible stacks
                    if (i >= end) break outer;

                    EmiIngredient ingredient = stacks.get(i);
                    i++;

                    // Skip empty ingredients
                    if (ingredient == null || ingredient.isEmpty()) continue;

                    // Check if this ingredient is locked
                    boolean isLocked = progressivestages$isIngredientLocked(ingredient);

                    if (isLocked) {
                        // Use EMI's getX/getY methods which correctly handle RTL and row widths
                        int cx = getX(xo, yo);
                        int cy = getY(xo, yo);
                        LockIconRenderer.renderLockIconOnly(graphics, cx + 1, cy + 1);
                    }
                }
            }

        } catch (Exception e) {
            if (StageConfig.isDebugLogging()) {
                progressivestages$LOGGER.debug(
                        "[ProgressiveStages] Error rendering sidebar lock icons: {}",
                        e.getMessage()
                );
            }
        }
    }

    /**
     * Check if an EmiIngredient is locked.
     * Iterates through EmiStacks and checks if any are locked.
     * For items with multiple variants (durability, enchantments), we check the base Item type.
     */
    @Unique
    private boolean progressivestages$isIngredientLocked(EmiIngredient ingredient) {
        // Iterate through all EmiStacks in the ingredient
        for (EmiStack emiStack : ingredient.getEmiStacks()) {
            if (emiStack.isEmpty()) continue;

            // Strategy 1: Try to get the ItemStack and check its Item
            ItemStack itemStack = emiStack.getItemStack();
            if (!itemStack.isEmpty()) {
                Item item = itemStack.getItem();
                if (ClientStageCache.isItemLocked(item)) {
                    return true;
                }
                // Item found and not locked, continue to check other stacks
                // (all stacks in an ingredient should be same item, but check anyway)
                continue;
            }

            // Strategy 2: Try to get the stack's ID (works for fluids and other stack types)
            ResourceLocation stackId = emiStack.getId();
            if (stackId != null) {
                // Check as item
                if (ClientStageCache.isItemLocked(stackId)) {
                    return true;
                }
                // Check as fluid
                if (ClientLockCache.isFluidLocked(stackId)) {
                    return true;
                }
            }
        }

        return false;
    }
}
