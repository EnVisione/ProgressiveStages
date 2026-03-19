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
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
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
 * NOTE: The 'recipe' parameter of slotChangedCraftingGrid is just a cache hint
 * and is null during normal slotsChanged() calls. We always perform our own
 * recipe lookup to get the actual matched recipe ID.
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
        // The 'recipe' parameter is only a cache hint and is null when called from
        // CraftingMenu.slotsChanged() (the normal path). We must look up the actual
        // matched recipe ourselves so recipe ID locks work correctly.
        RecipeHolder<CraftingRecipe> matchedRecipe = recipe;
        if (matchedRecipe == null) {
            ItemStack result = resultSlots.getItem(0);
            if (!result.isEmpty() && level.getServer() != null) {
                CraftingInput craftInput = craftSlots.asCraftInput();
                Optional<RecipeHolder<CraftingRecipe>> looked =
                        level.getServer().getRecipeManager()
                                .getRecipeFor(RecipeType.CRAFTING, craftInput, level);
                matchedRecipe = looked.orElse(null);
            }
        }

        // Always store the current recipe ID so ResultSlotMixin has reliable data
        if (matchedRecipe != null) {
            CraftingRecipeTracker.setLastRecipe(serverPlayer.getUUID(), matchedRecipe.id());
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
        StageManager stageManager = StageManager.getInstance();

        // ── Recipe ID lock (recipes = [...]) ──
        // ALWAYS enforced — clearing the result is the only reliable way to prevent
        // crafting. If the output is empty, inputs stay in the grid, nothing is voided.
        if (matchedRecipe != null) {
            Optional<StageId> recipeStage = registry.getRequiredStageForRecipe(matchedRecipe.id());
            if (recipeStage.isPresent() && !stageManager.hasStage(serverPlayer, recipeStage.get())) {
                clearResultAndSync(menu, resultSlots, serverPlayer);
                return;
            }
        }

        // ── Recipe-item lock (recipe_items = [...]) ──
        // ALWAYS enforced — locks ALL recipes that produce this output item.
        Optional<StageId> recipeItemStage = registry.getRequiredStageForRecipeByOutput(result.getItem());
        if (recipeItemStage.isPresent() && !stageManager.hasStage(serverPlayer, recipeItemStage.get())) {
            clearResultAndSync(menu, resultSlots, serverPlayer);
            return;
        }

        // ── Item lock (items = [...]) hiding output ──
        // Only hides the crafting output if the config option is enabled.
        if (StageConfig.isHideLockRecipeOutput()) {
            Optional<StageId> requiredStage = registry.getRequiredStage(result.getItem());
            if (requiredStage.isPresent() && !stageManager.hasStage(serverPlayer, requiredStage.get())) {
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
