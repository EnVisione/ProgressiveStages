package com.enviouse.progressivestages.mixin;

import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.config.StageConfig;
import com.enviouse.progressivestages.common.lock.LockRegistry;
import com.enviouse.progressivestages.common.stage.StageManager;
import net.minecraft.resources.ResourceLocation;
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
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mixin to hide recipe output for locked recipes in the crafting table.
 * Also stores the last matched recipe ID per player so ResultSlotMixin can
 * reliably check recipe ID locks without a runtime recipe lookup.
 */
@Mixin(CraftingMenu.class)
public abstract class CraftingMenuMixin {

    @Shadow @Final private Player player;
    @Shadow @Final private ResultContainer resultSlots;
    @Shadow @Final private CraftingContainer craftSlots;

    /**
     * Stores the last recipe ID that matched the crafting grid per player.
     * Populated here (where the recipe is already resolved), consumed by
     * ResultSlotMixin.mayPickup() to avoid unreliable runtime recipe lookups.
     */
    @Unique
    private static final Map<UUID, ResourceLocation> progressivestages$lastRecipeByPlayer = new ConcurrentHashMap<>();

    /**
     * Get the last matched recipe ID for a player.
     * Called by ResultSlotMixin to check recipe ID locks.
     */
    public static ResourceLocation progressivestages$getLastRecipe(UUID playerId) {
        return progressivestages$lastRecipeByPlayer.get(playerId);
    }

    /**
     * Clear stored recipe for a player (e.g., on disconnect).
     */
    public static void progressivestages$clearLastRecipe(UUID playerId) {
        progressivestages$lastRecipeByPlayer.remove(playerId);
    }

    /**
     * After the crafting result is set, store the matched recipe and check locks.
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
        // Always store the current recipe ID (even if locks aren't checked)
        // so ResultSlotMixin has reliable data
        if (player instanceof ServerPlayer serverPlayer && recipe != null) {
            progressivestages$lastRecipeByPlayer.put(serverPlayer.getUUID(), recipe.id());
        } else if (player instanceof ServerPlayer serverPlayer) {
            // No recipe matched — clear the stored recipe
            progressivestages$lastRecipeByPlayer.remove(serverPlayer.getUUID());
        }

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

        // Check if the recipe output item is locked (items = [...] lock)
        Optional<StageId> requiredStage = LockRegistry.getInstance().getRequiredStage(result.getItem());
        if (requiredStage.isPresent()) {
            if (!StageManager.getInstance().hasStage(serverPlayer, requiredStage.get())) {
                // Clear the result slot - player hasn't unlocked this
                resultSlots.setItem(0, ItemStack.EMPTY);
                return;
            }
        }

        // Check recipe-only item lock (recipe_items = [...] — locks crafting but not item itself)
        Optional<StageId> recipeItemStage = LockRegistry.getInstance().getRequiredStageForRecipeByOutput(result.getItem());
        if (recipeItemStage.isPresent()) {
            if (!StageManager.getInstance().hasStage(serverPlayer, recipeItemStage.get())) {
                resultSlots.setItem(0, ItemStack.EMPTY);
                return;
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
