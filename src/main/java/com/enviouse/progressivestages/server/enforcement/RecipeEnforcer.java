package com.enviouse.progressivestages.server.enforcement;

import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.config.StageConfig;
import com.enviouse.progressivestages.common.lock.LockRegistry;
import com.enviouse.progressivestages.common.stage.StageManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.Optional;

/**
 * Handles recipe/crafting enforcement.
 *
 * Two lock types affect recipes:
 *   - recipes = ["recipe_id"]      → locks ONE specific recipe by its registry ID
 *   - recipe_items = ["item_id"]   → locks ALL recipes whose output is this item
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

        // Creative bypass
        if (StageConfig.isAllowCreativeBypass() && player.isCreative()) {
            return true;
        }

        if (recipe == null) {
            return true;
        }

        return !isRecipeLockedForPlayer(player, recipe.id());
    }

    /**
     * Check if a recipe is locked for a player by recipe ID (recipes = [...]).
     */
    public static boolean isRecipeLockedForPlayer(ServerPlayer player, ResourceLocation recipeId) {
        if (recipeId == null) {
            return false;
        }

        Optional<StageId> requiredStage = LockRegistry.getInstance().getRequiredStageForRecipe(recipeId);
        if (requiredStage.isEmpty()) {
            return false;
        }

        return !StageManager.getInstance().hasStage(player, requiredStage.get());
    }

    /**
     * Check if a recipe output item is locked for a player (recipe_items = [...]).
     * This locks ALL recipes that produce the given item.
     */
    public static boolean isOutputItemRecipeLocked(ServerPlayer player, Item outputItem) {
        Optional<StageId> requiredStage = LockRegistry.getInstance().getRequiredStageForRecipeByOutput(outputItem);
        if (requiredStage.isEmpty()) {
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
            return;
        }
    }

    /**
     * Notify player that the recipe for this output item is locked
     */
    public static void notifyOutputLocked(ServerPlayer player, Item outputItem) {
        Optional<StageId> requiredStage = LockRegistry.getInstance().getRequiredStageForRecipeByOutput(outputItem);
        if (requiredStage.isPresent()) {
            ItemEnforcer.notifyLocked(player, requiredStage.get(), "This recipe");
        }
    }
}
