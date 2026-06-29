package com.enviouse.progressivestages.mixin;

import com.enviouse.progressivestages.common.lock.LockRegistry;
import com.enviouse.progressivestages.server.enforcement.NearestPlayerCheck;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * v3.0: hopper-proofs {@code [brewing].locked}. {@code SlotBrewingPickupMixin} stops a player TAKING
 * a locked brewed potion via the GUI; this closes the automation gap by also refusing hopper/funnel
 * extraction of a locked potion from a brewing stand — gated on the NEAREST player (the same
 * heuristic the mod uses for automated crafting), since hopper transfers carry no player. Best-effort:
 * if no player is within range, extraction proceeds (consistent with the automated-crafting model).
 */
@Mixin(BrewingStandBlockEntity.class)
public abstract class BrewingStandBlockEntityMixin {

    @Inject(method = "canTakeItemThroughFace", at = @At("HEAD"), cancellable = true)
    private void progressivestages$gateHopperExtract(int index, ItemStack stack, Direction direction,
                                                     CallbackInfoReturnable<Boolean> cir) {
        if (!LockRegistry.getInstance().hasBrewingLocks()) return;
        ResourceLocation potionId = brewedPotionId(stack);
        if (potionId == null) return;
        BlockEntity be = (BlockEntity) (Object) this;
        if (!(be.getLevel() instanceof ServerLevel level)) return;
        BlockPos pos = be.getBlockPos();
        ServerPlayer near = NearestPlayerCheck.findNearest(
            level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 16.0);
        if (near != null && LockRegistry.getInstance().isBrewingBlockedFor(near, potionId)) {
            cir.setReturnValue(false);
        }
    }

    @Unique
    private static ResourceLocation brewedPotionId(ItemStack stack) {
        if (stack.isEmpty()) return null;
        PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
        if (contents == null) return null;
        return contents.potion().flatMap(h -> h.unwrapKey()).map(k -> k.location()).orElse(null);
    }
}
