package com.enviouse.progressivestages.mixin;

import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.config.StageConfig;
import com.enviouse.progressivestages.common.lock.LockRegistry;
import com.enviouse.progressivestages.common.stage.StageManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

/**
 * Mixin to hide recipe output for locked recipes in the crafting table.
 */
@Mixin(CraftingMenu.class)
public abstract class CraftingMenuMixin {

    @Shadow @Final private Player player;
    @Shadow @Final private ResultContainer resultSlots;
    @Shadow @Final private CraftingContainer craftSlots;

    /**
     * After the crafting result is set, check if the recipe is locked and clear it.
     */
    @Inject(method = "slotChangedCraftingGrid", at = @At("TAIL"))
    private static void progressivestages$onSlotChangedCraftingGrid(
            net.minecraft.world.inventory.AbstractContainerMenu menu,
            Level level,
            Player player,
            CraftingContainer craftSlots,
            ResultContainer resultSlots,
            RecipeHolder<CraftingRecipe> recipe,
            CallbackInfo ci
    ) {
        if (!StageConfig.isHideLockRecipeOutput()) {
            return;
        }

        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }

        // Check if the result item is locked
        ItemStack result = resultSlots.getItem(0);
        if (result.isEmpty()) {
            return;
        }

        // Check if the recipe output item is locked
        Optional<StageId> requiredStage = LockRegistry.getInstance().getRequiredStage(result.getItem());
        if (requiredStage.isPresent()) {
            if (!StageManager.getInstance().hasStage(serverPlayer, requiredStage.get())) {
                // Clear the result slot - player hasn't unlocked this
                resultSlots.setItem(0, ItemStack.EMPTY);
            }
        }

        // Also check if the recipe itself is locked
        if (recipe != null) {
            Optional<StageId> recipeRequired = LockRegistry.getInstance().getRequiredStageForRecipe(recipe.id());
            if (recipeRequired.isPresent()) {
                if (!StageManager.getInstance().hasStage(serverPlayer, recipeRequired.get())) {
                    resultSlots.setItem(0, ItemStack.EMPTY);
                }
            }
        }
    }
}
