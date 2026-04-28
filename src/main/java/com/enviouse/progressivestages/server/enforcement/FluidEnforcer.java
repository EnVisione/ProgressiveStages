package com.enviouse.progressivestages.server.enforcement;

import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.config.StageConfig;
import com.enviouse.progressivestages.common.lock.LockRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;

/**
 * Enforces {@code [fluids] locked = [...]} at gameplay level (in addition to the existing
 * EMI/JEI visibility hiding).
 *
 * <p>Three surfaces:
 * <ul>
 *   <li><b>Placement</b> — {@code BlockEvent.FluidPlaceBlockEvent} cancelled if the fluid
 *       that's flowing or being placed is locked for the nearest player.</li>
 *   <li><b>Bucket pickup</b> — {@code PlayerInteractEvent.RightClickBlock} cancelled when
 *       a player with an empty bucket would scoop a locked fluid source.</li>
 *   <li><b>World submersion</b> — {@code PlayerTickEvent.Post} applies slowness + blindness
 *       while the player stands inside a locked fluid, making it impractical to exploit.</li>
 * </ul>
 */
public final class FluidEnforcer {

    private FluidEnforcer() {}

    /**
     * @return {@code true} if placement of {@code state} at {@code pos} should be cancelled.
     * v2.0: multi-stage — cancels if the nearest player lacks ANY of the gating stages.
     */
    public static boolean shouldCancelFluidPlace(net.minecraft.world.level.LevelAccessor level,
                                                 BlockPos pos, BlockState state) {
        if (level == null) return false;
        if (!(level instanceof net.minecraft.server.level.ServerLevel sl)) return false;

        Fluid fluid = extractFluid(state);
        if (fluid == null) return false;
        ResourceLocation fluidId = BuiltInRegistries.FLUID.getKey(fluid);
        if (fluidId == null) return false;
        java.util.Set<StageId> gating = LockRegistry.getInstance().getRequiredStagesForFluid(fluidId);
        if (gating.isEmpty()) return false;

        double radius = StageConfig.getMobSpawnCheckRadius();
        return NearestPlayerCheck.nearestPlayerLacksAll(sl, pos.getX(), pos.getY(), pos.getZ(),
            radius, gating);
    }

    /** @return {@code true} if a bucket-pickup of this fluid is allowed. */
    public static boolean canPickupFluid(ServerPlayer player, BlockGetter level, BlockPos pos) {
        if (StageConfig.isAllowCreativeBypass() && player.isCreative()) return true;
        BlockState state = level.getBlockState(pos);
        Fluid fluid = extractFluid(state);
        if (fluid == null) return true;
        ResourceLocation fluidId = BuiltInRegistries.FLUID.getKey(fluid);
        if (fluidId == null) return true;
        // v2.0 multi-stage: blocked if any gating stage is missing.
        return !LockRegistry.getInstance().isFluidBlockedFor(player, fluidId);
    }

    public static void notifyPickupLocked(ServerPlayer player, BlockGetter level, BlockPos pos) {
        Fluid fluid = extractFluid(level.getBlockState(pos));
        if (fluid == null) return;
        ResourceLocation fluidId = BuiltInRegistries.FLUID.getKey(fluid);
        if (fluidId == null) return;
        // v2.0: multi-stage — show the first gating stage the player is missing.
        LockRegistry.getInstance().primaryRestrictingStageForFluid(player, fluidId)
            .ifPresent(stage -> ItemEnforcer.notifyLockedWithCooldown(player, stage,
                com.enviouse.progressivestages.common.config.StageConfig.getMsgTypeLabelFluid()));
    }

    /**
     * Apply slowness + blindness if the player is submerged in a locked fluid. Short duration
     * so it naturally wears off when they exit; re-applied every tick of submersion.
     */
    public static void applySubmersionEffects(ServerPlayer player) {
        if (StageConfig.isAllowCreativeBypass() && player.isCreative()) return;
        FluidState fs = player.level().getFluidState(player.blockPosition());
        if (fs.isEmpty()) return;
        ResourceLocation fluidId = BuiltInRegistries.FLUID.getKey(fs.getType());
        if (fluidId == null) return;
        // v2.0 multi-stage: only when at least one gating stage is missing.
        if (!LockRegistry.getInstance().isFluidBlockedFor(player, fluidId)) return;

        // Short, re-applied effects — instant feedback that the player shouldn't be here.
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 2, true, false));
        player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0, true, false));
    }

    /** @return {@code true} if the held stack is an empty/water/lava bucket item. */
    public static boolean isBucket(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof BucketItem;
    }

    private static Fluid extractFluid(BlockState state) {
        if (state.getBlock() instanceof LiquidBlock) {
            return state.getFluidState().getType();
        }
        FluidState fs = state.getFluidState();
        return fs.isEmpty() ? null : fs.getType();
    }
}
