package com.enviouse.progressivestages.mixin;

import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.config.StageConfig;
import com.enviouse.progressivestages.common.lock.LockRegistry;
import com.enviouse.progressivestages.common.stage.StageManager;
import com.enviouse.progressivestages.common.util.CraftingRecipeTracker;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
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
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

/**
 * Mixin to hide recipe output for locked recipes in the crafting table.
 * Also stores the last matched recipe ID per player (via CraftingRecipeTracker)
 * so ResultSlotMixin can reliably check recipe ID locks without a runtime
 * recipe lookup.
 *
 * <p>Recipe resolution strategy (v1.4):
 * <ol>
 *   <li>Use the {@code recipe} parameter if non-null (passed from recipe book via
 *       {@code finishPlacingRecipe}).</li>
 *   <li>Fall back to {@link ResultContainer#getRecipeUsed()} — vanilla stores the
 *       matched recipe here during {@code slotChangedCraftingGrid}, so by TAIL it
 *       is always populated. This is the most reliable source and fixes recipe ID
 *       lock failures with modded recipes (e.g., KubeJS).</li>
 * </ol>
 */
@Mixin(CraftingMenu.class)
public abstract class CraftingMenuMixin {

    @Shadow @Final private Player player;
    @Shadow @Final private ResultContainer resultSlots;
    @Shadow @Final private CraftingContainer craftSlots;

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
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }

        // ── Resolve the actual matched recipe ──
        // Strategy: use the 'recipe' parameter if provided (recipe book path), then
        // fall back to resultSlots.getRecipeUsed() which vanilla already populated
        // during this same method call. This is more reliable than doing a separate
        // getRecipeFor() lookup, which can fail for modded recipe types.
        ResourceLocation matchedRecipeId = null;
        if (recipe != null) {
            matchedRecipeId = recipe.id();
        } else {
            // Vanilla stores the matched recipe in ResultContainer.recipeUsed during
            // slotChangedCraftingGrid — by TAIL it's always set if a recipe matched.
            RecipeHolder<?> storedRecipe = resultSlots.getRecipeUsed();
            if (storedRecipe != null) {
                matchedRecipeId = storedRecipe.id();
            }
        }

        // Always store the current recipe ID so ResultSlotMixin has reliable data
        if (matchedRecipeId != null) {
            CraftingRecipeTracker.setLastRecipe(serverPlayer.getUUID(), matchedRecipeId);
            if (StageConfig.isDebugLogging()) {
                com.mojang.logging.LogUtils.getLogger().info(
                        "[ProgressiveStages] CraftingMenuMixin: Matched recipe ID = {} for player {}",
                        matchedRecipeId, serverPlayer.getName().getString());
            }
        } else {
            CraftingRecipeTracker.clearLastRecipe(serverPlayer.getUUID());
        }

        // Creative bypass — don't interfere with creative players
        if (StageConfig.isAllowCreativeBypass() && serverPlayer.isCreative()) {
            return;
        }

        ItemStack result = resultSlots.getItem(0);
        if (result.isEmpty()) {
            return;
        }

        LockRegistry registry = LockRegistry.getInstance();

        // ── Recipe ID lock (recipes = [...]) — v2.0 multi-stage ──
        if (matchedRecipeId != null) {
            if (registry.isRecipeBlockedFor(serverPlayer, matchedRecipeId)) {
                if (StageConfig.isDebugLogging()) {
                    com.mojang.logging.LogUtils.getLogger().info(
                            "[ProgressiveStages] CraftingMenuMixin: Blocking recipe {} (multi-stage)",
                            matchedRecipeId);
                }
                clearResultAndSync(menu, resultSlots, serverPlayer);
                return;
            }
        }

        // ── Recipe-item lock (recipe_items = [...]) ──
        java.util.Set<StageId> recipeItemGating = registry.getRequiredStagesForRecipeByOutput(result.getItem());
        if (!recipeItemGating.isEmpty() && !registry.playerHasAllStages(serverPlayer, recipeItemGating)) {
            clearResultAndSync(menu, resultSlots, serverPlayer);
            return;
        }

        // ── Item lock (items = [...]) hiding output ──
        if (StageConfig.isHideLockRecipeOutput()) {
            if (registry.isItemBlockedFor(serverPlayer, result.getItem())) {
                clearResultAndSync(menu, resultSlots, serverPlayer);
            }
        }
    }

    /**
     * Clears the result slot on the server AND sends a packet to the client so
     * the empty slot is reflected immediately (vanilla already sent the non-empty
     * packet before our TAIL injection runs).
     */
    private static void clearResultAndSync(
            net.minecraft.world.inventory.AbstractContainerMenu menu,
            ResultContainer resultSlots,
            ServerPlayer serverPlayer
    ) {
        resultSlots.setItem(0, ItemStack.EMPTY);
        menu.setRemoteSlot(0, ItemStack.EMPTY);
        serverPlayer.connection.send(
                new ClientboundContainerSetSlotPacket(
                        menu.containerId, menu.incrementStateId(), 0, ItemStack.EMPTY
                )
        );
    }
}
