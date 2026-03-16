package com.enviouse.progressivestages.mixin;

import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.config.StageConfig;
import com.enviouse.progressivestages.common.lock.LockRegistry;
import com.enviouse.progressivestages.common.stage.StageManager;
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
        StageManager stageManager = StageManager.getInstance();

        // 1. Check if the output ITEM itself is locked (items = [...])
        Optional<StageId> itemStage = registry.getRequiredStage(result.getItem());
        if (itemStage.isPresent() && !stageManager.hasStage(serverPlayer, itemStage.get())) {
            cir.setReturnValue(false);
            ItemEnforcer.notifyLockedWithCooldown(serverPlayer, result.getItem());
            return;
        }

        // 2. Check recipe-item lock (recipe_items = [...]) — locks ALL recipes for this output item
        Optional<StageId> recipeItemStage = registry.getRequiredStageForRecipeByOutput(result.getItem());
        if (recipeItemStage.isPresent() && !stageManager.hasStage(serverPlayer, recipeItemStage.get())) {
            cir.setReturnValue(false);
            ItemEnforcer.notifyLocked(serverPlayer, recipeItemStage.get(), "This recipe");
            return;
        }

        // 3. Check recipe ID lock (recipes = [...]) — uses stored recipe from CraftingMenuMixin
        //    CraftingMenuMixin stores the matched recipe ID when slotChangedCraftingGrid fires,
        //    so we don't need an unreliable runtime recipe lookup here.
        ResourceLocation lastRecipeId = CraftingMenuMixin.progressivestages$getLastRecipe(serverPlayer.getUUID());
        if (lastRecipeId != null) {
            Optional<StageId> recipeStage = registry.getRequiredStageForRecipe(lastRecipeId);
            if (recipeStage.isPresent() && !stageManager.hasStage(serverPlayer, recipeStage.get())) {
                cir.setReturnValue(false);
                ItemEnforcer.notifyLocked(serverPlayer, recipeStage.get(), "This recipe");
                return;
            }
        }
    }
}

