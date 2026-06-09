package com.enviouse.progressivestages.mixin;

import com.enviouse.progressivestages.common.config.StageConfig;
import com.enviouse.progressivestages.common.lock.LockRegistry;
import com.enviouse.progressivestages.server.enforcement.IngredientGateHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ItemCombinerMenu;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * v2.0.1: extend the transitive ingredient gate to the smithing table.
 * Inputs: template (0), base (1), addition (2).
 */
@Mixin(SmithingMenu.class)
public abstract class SmithingMenuMixin extends ItemCombinerMenu {

    public SmithingMenuMixin() { super(null, 0, null, null); }

    @Inject(method = "createResult", at = @At("TAIL"))
    private void progressivestages$blockLockedIngredients(CallbackInfo ci) {
        if (!(this.player instanceof ServerPlayer sp)) return;
        if (!LockRegistry.getInstance().isIngredientGatingActive()) return;
        if (StageConfig.isAllowCreativeBypass() && sp.isCreative()) return;

        java.util.Optional<LockRegistry.IngredientBlockResult> r =
            IngredientGateHelper.checkContainer(sp, this.inputSlots);
        if (r.isPresent()) {
            this.resultSlots.setItem(0, ItemStack.EMPTY);
        }
    }
}
