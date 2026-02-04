package com.enviouse.progressivestages.server.enforcement;

import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.config.StageConfig;
import com.enviouse.progressivestages.common.lock.LockRegistry;
import com.enviouse.progressivestages.common.stage.StageManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.Optional;

/**
 * Handles recipe/crafting enforcement
 */
public class RecipeEnforcer {

    /**
     * Check if a player can craft a recipe
     * @return true if allowed, false if blocked
     */
    public static boolean canCraftRecipe(ServerPlayer player, RecipeHolder<?> recipe) {
        if (!StageConfig.isBlockCrafting()) {
            return true;
        }

        if (recipe == null) {
            return true;
        }

        return !isRecipeLockedForPlayer(player, recipe.id());
    }

    /**
     * Check if a recipe is locked for a player
     */
    public static boolean isRecipeLockedForPlayer(ServerPlayer player, ResourceLocation recipeId) {
        if (recipeId == null) {
            return false;
        }

        Optional<StageId> requiredStage = LockRegistry.getInstance().getRequiredStageForRecipe(recipeId);
        if (requiredStage.isEmpty()) {
            // Also check if the output item is locked
            return false;
        }

        return !StageManager.getInstance().hasStage(player, requiredStage.get());
    }

    /**
     * Notify player that recipe is locked
     */
    public static void notifyLocked(ServerPlayer player, ResourceLocation recipeId) {
        Optional<StageId> requiredStage = LockRegistry.getInstance().getRequiredStageForRecipe(recipeId);
        if (requiredStage.isPresent()) {
            ItemEnforcer.notifyLocked(player, requiredStage.get(), "This recipe");
        }
    }
}
