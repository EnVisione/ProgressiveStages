package com.enviouse.progressivestages.mixin;

import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.config.StageConfig;
import com.enviouse.progressivestages.common.lock.LockRegistry;
import com.enviouse.progressivestages.common.stage.StageManager;
import com.enviouse.progressivestages.server.enforcement.ItemEnforcer;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

/**
 * Mixin to prevent moving locked items in containers (chest drag exploit fix)
 */
@Mixin(AbstractContainerMenu.class)
public abstract class AbstractContainerMenuMixin {

    @Shadow @Final public NonNullList<Slot> slots;

    @Inject(method = "doClick", at = @At("HEAD"), cancellable = true)
    private void progressivestages$blockLockedItemMove(int slotId, int button, ClickType clickType, Player player, CallbackInfo ci) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }

        if (!StageConfig.isBlockItemInventory()) {
            return;
        }

        if (StageConfig.isAllowCreativeBypass() && player.isCreative()) {
            return;
        }

        try {
            if (slotId < 0 || slotId >= slots.size()) {
                return;
            }

            Slot slot = slots.get(slotId);
            if (slot == null || !slot.hasItem()) {
                return;
            }

            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) {
                return;
            }

            Optional<StageId> requiredStage = LockRegistry.getInstance().getRequiredStage(stack.getItem());
            if (requiredStage.isPresent()) {
                if (!StageManager.getInstance().hasStage(serverPlayer, requiredStage.get())) {
                    ci.cancel();
                    ItemEnforcer.notifyLockedWithCooldown(serverPlayer, stack.getItem());
                }
            }
        } catch (Exception e) {
            // Silently ignore to prevent crashes
        }
    }
}
