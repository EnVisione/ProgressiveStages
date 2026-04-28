package com.enviouse.progressivestages.mixin;

import com.enviouse.progressivestages.common.config.StageConfig;
import com.enviouse.progressivestages.server.enforcement.ItemEnforcer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ItemCombinerMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Clears the anvil result slot when any participating stack is stage-locked.
 * Prevents using anvils to repair / rename / combine locked items.
 */
@Mixin(AnvilMenu.class)
public abstract class AnvilMenuMixin extends ItemCombinerMenu {

    public AnvilMenuMixin() { super(null, 0, null, null); }

    // protected Player player;       // inherited from ItemCombinerMenu — accessible directly
    // protected Container inputSlots;
    // protected ResultContainer resultSlots;

    @Inject(method = "createResult", at = @At("TAIL"))
    private void progressivestages$blockLockedAnvil(CallbackInfo ci) {
        if (!(this.player instanceof ServerPlayer sp)) return;
        if (StageConfig.isAllowCreativeBypass() && sp.isCreative()) return;

        ItemStack input = this.inputSlots.getItem(0);
        ItemStack additional = this.inputSlots.getItem(1);
        ItemStack result = this.resultSlots.getItem(0);

        boolean blocked = false;
        if (!input.isEmpty() && !ItemEnforcer.canHoldItem(sp, input)) blocked = true;
        if (!blocked && !additional.isEmpty() && !ItemEnforcer.canHoldItem(sp, additional)) blocked = true;
        if (!blocked && !result.isEmpty() && !ItemEnforcer.canHoldItem(sp, result)) blocked = true;

        if (blocked) {
            this.resultSlots.setItem(0, ItemStack.EMPTY);
        }
    }
}
