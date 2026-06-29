package com.enviouse.progressivestages.mixin;

import com.enviouse.progressivestages.common.lock.LockRegistry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.Inject;

/**
 * v3.0: brewing-potion gating via {@code [brewing].locked}. Rather than fighting the brewing-stand
 * fuel/timer loop, this gates the player's ability to TAKE a locked brewed potion out of the stand's
 * potion slots — the potion brews and sits there, but a player missing the gating stage can't pull it
 * until they unlock it. Identifies brewing slots by their {@link BrewingStandBlockEntity} container
 * (the package-private PotionSlot type can't be referenced directly). No-op when no brewing lock exists.
 */
@Mixin(Slot.class)
public abstract class SlotBrewingPickupMixin {

    @Shadow @Final public Container container;

    @Inject(method = "mayPickup", at = @At("HEAD"), cancellable = true)
    private void progressivestages$gateBrewedPotionPickup(Player player, CallbackInfoReturnable<Boolean> cir) {
        if (!LockRegistry.getInstance().hasBrewingLocks()) return;
        if (!(container instanceof BrewingStandBlockEntity)) return;
        if (!(player instanceof ServerPlayer sp)) return;
        ItemStack stack = ((Slot) (Object) this).getItem();
        ResourceLocation potionId = brewedPotionId(stack);
        if (potionId != null && LockRegistry.getInstance().isBrewingBlockedFor(sp, potionId)) {
            cir.setReturnValue(false);
        }
    }

    @org.spongepowered.asm.mixin.Unique
    private static ResourceLocation brewedPotionId(ItemStack stack) {
        if (stack.isEmpty()) return null;
        PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
        if (contents == null) return null;
        return contents.potion().flatMap(h -> h.unwrapKey()).map(k -> k.location()).orElse(null);
    }
}
