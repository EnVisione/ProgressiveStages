package com.enviouse.progressivestages.mixin;

import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.config.StageConfig;
import com.enviouse.progressivestages.common.lock.LockRegistry;
import com.enviouse.progressivestages.common.stage.StageManager;
import com.enviouse.progressivestages.common.util.CraftingRecipeTracker;
import com.enviouse.progressivestages.server.enforcement.ItemEnforcer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/**
 * Mixin to hard-block taking crafted items from the result slot when the recipe
 * is locked via recipes = [...] or recipe_items = [...].
 *
 * This is the authoritative crafting enforcement point. CraftingMenuMixin hides
 * the output visually, but this mixin is the actual gate that prevents item take.
 *
 * For recipe ID locks (recipes = [...]), we use the recipe ID stored by
 * CraftingMenuMixin.slotChangedCraftingGrid (where the recipe is already resolved)
 * instead of doing a runtime recipe lookup — which can fail due to timing issues.
 */
@Mixin(ResultSlot.class)
public abstract class ResultSlotMixin extends Slot {

    @Shadow @Final private CraftingContainer craftSlots;
    @Shadow @Final private Player player;

    // Required by Slot superclass
    public ResultSlotMixin() {
        super(null, 0, 0, 0);
    }

    /**
     * Block picking up the crafted item if the recipe is locked for this player.
     * Checks in order:
     *   1. Item lock (items = [...]) — item itself is locked
     *   2. Recipe-item lock (recipe_items = [...]) — all recipes for this output are locked
     *   3. Recipe ID lock (recipes = [...]) — this specific recipe ID is locked
     */
    @Inject(method = "mayPickup", at = @At("HEAD"), cancellable = true)
    private void progressivestages$blockLockedRecipePickup(Player player, CallbackInfoReturnable<Boolean> cir) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }

        if (!StageConfig.isBlockCrafting()) {
            return;
        }

        if (StageConfig.isAllowCreativeBypass() && player.isCreative()) {
            return;
        }

        ItemStack result = this.getItem();
        if (result.isEmpty()) {
            return;
        }

        LockRegistry registry = LockRegistry.getInstance();

        // 1. v2.0 multi-stage: blocked if any item-gating stage is missing
        if (registry.isItemBlockedFor(serverPlayer, result.getItem())) {
            cir.setReturnValue(false);
            ItemEnforcer.notifyLockedWithCooldown(serverPlayer, result.getItem());
            return;
        }

        // 2. recipe_items multi-stage
        java.util.Set<StageId> recipeItemGating = registry.getRequiredStagesForRecipeByOutput(result.getItem());
        if (!recipeItemGating.isEmpty() && !registry.playerHasAllStages(serverPlayer, recipeItemGating)) {
            cir.setReturnValue(false);
            for (StageId s : recipeItemGating) {
                if (!StageManager.getInstance().hasStage(serverPlayer, s)) {
                    ItemEnforcer.notifyLocked(serverPlayer, s, StageConfig.getMsgTypeLabelRecipe());
                    break;
                }
            }
            return;
        }

        // 3. recipe-id multi-stage
        ResourceLocation lastRecipeId = CraftingRecipeTracker.getLastRecipe(serverPlayer.getUUID());
        if (lastRecipeId == null && this.container instanceof net.minecraft.world.inventory.RecipeCraftingHolder holder) {
            var storedRecipe = holder.getRecipeUsed();
            if (storedRecipe != null) {
                lastRecipeId = storedRecipe.id();
            }
        }
        if (lastRecipeId != null) {
            java.util.Set<StageId> rg = registry.getRequiredStagesForRecipe(lastRecipeId);
            if (!rg.isEmpty() && !registry.playerHasAllStages(serverPlayer, rg)) {
                cir.setReturnValue(false);
                for (StageId s : rg) {
                    if (!StageManager.getInstance().hasStage(serverPlayer, s)) {
                        ItemEnforcer.notifyLocked(serverPlayer, s, StageConfig.getMsgTypeLabelRecipe());
                        break;
                    }
                }
            }
        }
    }
}

