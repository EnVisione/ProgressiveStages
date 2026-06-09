package com.enviouse.progressivestages.mixin;

import com.enviouse.progressivestages.common.config.StageConfig;
import com.enviouse.progressivestages.common.lock.LockRegistry;
import com.enviouse.progressivestages.server.enforcement.IngredientGateHelper;
import com.enviouse.progressivestages.server.enforcement.NearestPlayerCheck;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.CrafterBlock;
import net.minecraft.world.level.block.entity.CrafterBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * v2.0.1: gate the vanilla CrafterBlock under stages that opt into
 * {@code enforcement.block_automated_crafting}. Uses NearestPlayerCheck against
 * the per-stage radius — cheapest possible model.
 *
 * <p>If no stage has opted in, the @Inject returns immediately (one boolean read).
 * If opted in, we resolve the would-be recipe + ingredients and check the nearest
 * player against all opted-in stages.
 */
@Mixin(CrafterBlock.class)
public abstract class CrafterBlockMixin {

    @Inject(method = "dispenseFrom", at = @At("HEAD"), cancellable = true)
    private void progressivestages$gateAutomatedCraft(BlockState state, ServerLevel level,
                                                     BlockPos pos, CallbackInfo ci) {
        LockRegistry reg = LockRegistry.getInstance();
        // Fast path: no stage opted in to automated-craft gating
        if (!reg.isAutoCraftGatingActive()) return;

        if (!(level.getBlockEntity(pos) instanceof CrafterBlockEntity be)) return;

        int radius = reg.getMaxCrafterCheckRadius();
        ServerPlayer nearest = NearestPlayerCheck.findNearest(
            level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, radius);
        if (nearest == null) return; // no player around to gate for
        if (StageConfig.isAllowCreativeBypass() && nearest.isCreative()) return;

        CraftingInput input = be.asCraftInput();
        Optional<RecipeHolder<CraftingRecipe>> opt = CrafterBlock.getPotentialResults(level, input);
        if (opt.isEmpty()) return;

        RecipeHolder<CraftingRecipe> holder = opt.get();
        ItemStack out = holder.value().assemble(input, level.registryAccess());

        // Dedupe ingredients
        Set<net.minecraft.world.item.Item> distinct = new HashSet<>();
        for (int i = 0; i < input.size(); i++) {
            ItemStack s = input.getItem(i);
            if (s != null && !s.isEmpty()) distinct.add(s.getItem());
        }

        Optional<LockRegistry.IngredientBlockResult> blocked =
            reg.firstBlockingAutoCraftStage(
                nearest,
                distinct,
                out.isEmpty() ? null : out.getItem(),
                holder.id());
        if (blocked.isPresent()) {
            // Mimic vanilla's "no recipe" failure: play the failure level event & cancel.
            level.levelEvent(1050, pos, 0);
            IngredientGateHelper.notifyAutoBlocked(nearest, blocked.get());
            ci.cancel();
        }
    }
}
