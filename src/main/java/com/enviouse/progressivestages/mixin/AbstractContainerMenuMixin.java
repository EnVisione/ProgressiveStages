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
 * Mixin to prevent moving locked items in containers.
 *
 * <p>Enforcement priority (highest wins):
 * <ul>
 *   <li>{@code block_item_inventory = true} → block ALL mouse interaction with locked items (strictest)</li>
 *   <li>{@code block_item_mouse_pickup = true} → block picking up locked items with mouse cursor</li>
 *   <li>{@code block_item_hotbar = true} → block placing locked items into hotbar slots (slots 0-8)</li>
 *   <li>All false → free movement (items can be moved to chests, between slots)</li>
 * </ul>
 */
@Mixin(AbstractContainerMenu.class)
public abstract class AbstractContainerMenuMixin {

    @Shadow @Final public NonNullList<Slot> slots;

    @Inject(method = "doClick", at = @At("HEAD"), cancellable = true)
    private void progressivestages$blockLockedItemMove(int slotId, int button, ClickType clickType, Player player, CallbackInfo ci) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }

        if (StageConfig.isAllowCreativeBypass() && player.isCreative()) {
            return;
        }

        // Need at least one enforcement option enabled
        if (!StageConfig.isBlockItemInventory() && !StageConfig.isBlockItemMousePickup() && !StageConfig.isBlockItemHotbar()) {
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

            // v2.0 multi-stage: blocked iff ANY gating stage is missing.
            if (!LockRegistry.getInstance().isItemBlockedFor(serverPlayer, stack.getItem())) {
                // Item is not locked for this player — but check hotbar destination restriction
                progressivestages$checkHotbarDestination(slotId, clickType, serverPlayer, ci);
                return;
            }

            // Item IS locked for this player — apply enforcement based on config

            // Strictest: block_item_inventory blocks ALL interaction
            if (StageConfig.isBlockItemInventory()) {
                // Check per-stage inventory exemption
                if (!LockRegistry.getInstance().isExemptFromInventory(stack.getItem())) {
                    ci.cancel();
                    ItemEnforcer.notifyLockedWithCooldown(serverPlayer, stack.getItem());
                    return;
                }
            }

            // Medium: block_item_mouse_pickup blocks picking up the locked item with mouse
            if (StageConfig.isBlockItemMousePickup()) {
                // Check per-stage mouse pickup exemption
                if (!LockRegistry.getInstance().isExemptFromMousePickup(stack.getItem())) {
                    ci.cancel();
                    ItemEnforcer.notifyLockedWithCooldown(serverPlayer, stack.getItem());
                    return;
                }
            }

            // Softest: only block_item_hotbar — allow free movement but check destination
            // (destination check is handled below for the carried item)

        } catch (Exception e) {
            // Silently ignore to prevent crashes
        }
    }

    /**
     * Check if the player is trying to place a locked item (currently carried by mouse)
     * into a hotbar slot. Only applies when block_item_hotbar is enabled.
     */
    @Unique
    private void progressivestages$checkHotbarDestination(int slotId, ClickType clickType, ServerPlayer player, CallbackInfo ci) {
        // This method is called for non-locked items in the clicked slot.
        // We also need to check if the player is CARRYING a locked item on their cursor
        // and trying to place it into a hotbar slot.
        if (!StageConfig.isBlockItemHotbar()) {
            return;
        }

        // Get the item currently carried by the mouse cursor
        ItemStack carried = ((AbstractContainerMenu)(Object)this).getCarried();
        if (carried.isEmpty()) {
            return;
        }

        // v2.0 multi-stage
        if (!LockRegistry.getInstance().isItemBlockedFor(player, carried.getItem())) {
            return; // Carried item is not locked
        }

        // Carried item is locked — check if target is a hotbar slot
        if (progressivestages$isHotbarSlot(slotId)) {
            ci.cancel();
            ItemEnforcer.notifyLockedWithCooldown(player, carried.getItem());
        }
    }

    /**
     * Check if a container slot index maps to a player hotbar slot.
     * In the player inventory container, hotbar slots are indices 36-44 (mapped from inventory 0-8).
     * In other containers, the player hotbar is typically the last 9 slots.
     */
    @Unique
    private boolean progressivestages$isHotbarSlot(int slotId) {
        if (slotId < 0 || slotId >= slots.size()) {
            return false;
        }

        Slot slot = slots.get(slotId);
        if (slot == null) {
            return false;
        }

        // Check if this slot belongs to the player's inventory and is a hotbar slot (index 0-8)
        if (slot.container instanceof net.minecraft.world.entity.player.Inventory) {
            return slot.getContainerSlot() >= 0 && slot.getContainerSlot() <= 8;
        }

        return false;
    }
}
