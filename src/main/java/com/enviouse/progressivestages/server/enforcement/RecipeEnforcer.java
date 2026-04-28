package com.enviouse.progressivestages.server.enforcement;

import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.config.StageConfig;
import com.enviouse.progressivestages.common.lock.LockRegistry;
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
     * v2.0: multi-stage aware — blocked when ANY gating stage is missing.
     */
    public static boolean isRecipeLockedForPlayer(ServerPlayer player, ResourceLocation recipeId) {
        return LockRegistry.getInstance().isRecipeBlockedFor(player, recipeId);
    }

    /**
     * Check if a recipe output item is locked for a player (recipe_items = [...]).
     * v2.0: multi-stage aware — blocked when ANY gating stage is missing.
     */
    public static boolean isOutputItemRecipeLocked(ServerPlayer player, Item outputItem) {
        return LockRegistry.getInstance().isRecipeOutputBlockedFor(player, outputItem);
    }

    /**
     * Notify player that recipe is locked.
     * v2.0: shows the first gating stage the player is missing.
     */
    public static void notifyLocked(ServerPlayer player, ResourceLocation recipeId) {
        Optional<StageId> requiredStage =
            LockRegistry.getInstance().primaryRestrictingStageForRecipe(player, recipeId);
        requiredStage.ifPresent(stage ->
            ItemEnforcer.notifyLocked(player, stage, StageConfig.getMsgTypeLabelRecipe()));
    }

    /**
     * Notify player that the recipe for this output item is locked.
     * v2.0: shows the first gating stage the player is missing.
     */
    public static void notifyOutputLocked(ServerPlayer player, Item outputItem) {
        Optional<StageId> requiredStage =
            LockRegistry.getInstance().primaryRestrictingStageForRecipeOutput(player, outputItem);
        requiredStage.ifPresent(stage ->
            ItemEnforcer.notifyLocked(player, stage, StageConfig.getMsgTypeLabelRecipe()));
    }
}
