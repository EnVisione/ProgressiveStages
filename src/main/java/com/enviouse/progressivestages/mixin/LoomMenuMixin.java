package com.enviouse.progressivestages.mixin;

import com.enviouse.progressivestages.common.config.StageConfig;
import com.enviouse.progressivestages.common.lock.LockRegistry;
import com.enviouse.progressivestages.server.enforcement.IngredientGateHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.LoomMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * v2.0.1: gate the loom when banner/dye/pattern slots include a locked ingredient
 * under a stage that opted into block_crafting_with_locked_ingredients.
 *
 * LoomMenu (1.21.1) does not store the Player; only the Inventory is passed to
 * its constructor and used locally. We capture the player at construction time
 * into a @Unique field so we can check stages during setupResultSlot.
 */
@Mixin(LoomMenu.class)
public abstract class LoomMenuMixin {

    @Shadow @Final private Container inputContainer;
    @Shadow @Final private Container outputContainer;

    @Unique
    private Player progressivestages$player;

    @Inject(
        method = "<init>(ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/inventory/ContainerLevelAccess;)V",
        at = @At("TAIL")
    )
    private void progressivestages$captureOwner(
            int containerId,
            Inventory inv,
            ContainerLevelAccess access,
            CallbackInfo ci) {
        this.progressivestages$player = inv.player;
    }

    @Inject(method = "setupResultSlot", at = @At("TAIL"))
    private void progressivestages$blockLockedIngredients(
            net.minecraft.core.Holder<net.minecraft.world.level.block.entity.BannerPattern> pattern,
            CallbackInfo ci) {
        Player p = this.progressivestages$player;
        if (!(p instanceof ServerPlayer sp)) return;
        if (!LockRegistry.getInstance().isIngredientGatingActive()) return;
        if (StageConfig.isAllowCreativeBypass() && sp.isCreative()) return;

        java.util.Optional<LockRegistry.IngredientBlockResult> r =
            IngredientGateHelper.checkContainer(sp, this.inputContainer);
        if (r.isPresent()) {
            this.outputContainer.setItem(0, ItemStack.EMPTY);
        }
    }
}
