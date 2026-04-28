package com.enviouse.progressivestages.server.enforcement;

import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.config.StageConfig;
import com.enviouse.progressivestages.common.lock.LockDefinition;
import com.enviouse.progressivestages.common.lock.LockRegistry;
import com.enviouse.progressivestages.common.stage.StageManager;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * Enforces {@code [[regions]]} — fixed 3D boxes with gating flags.
 *
 * <p>Four enforcement surfaces:
 * <ul>
 *   <li>Entry push-back (tick-based)</li>
 *   <li>Block break cancellation when {@code prevent_block_break = true}</li>
 *   <li>Block place cancellation when {@code prevent_block_place = true}</li>
 *   <li>Explosion block-damage clear when {@code prevent_explosions = true}</li>
 *   <li>Mob spawn cancellation when {@code disable_mob_spawning = true}</li>
 * </ul>
 */
public final class RegionEnforcer {

    private RegionEnforcer() {}

    // ---------------------------------------------------------------
    // Tick-based entry enforcement
    // ---------------------------------------------------------------

    /**
     * Called from {@code PlayerTickEvent.Post}. If the player is inside a region
     * that has {@code prevent_entry = true} and lacks the stage, teleport them to
     * the nearest edge of the region.
     *
     * <p>v2.0 note on multi-stage: each {@link LockRegistry.RegionLockEntry} carries the
     * single stage it was registered against (its owning stage TOML). If multiple stages
     * each declare a region with overlapping bounds, the iteration here visits them
     * independently — the player is gated by the FIRST overlapping region whose stage
     * they lack. This is semantically equivalent to multi-stage gating because region
     * entries with different owning stages are independent locks (the iteration ANDs
     * them implicitly: if you fail any single entry's stage check inside its bounds,
     * you're gated). Per-entry stage = single owning stage by design.
     */
    public static void checkPlayerEntry(ServerPlayer player) {
        if (!StageConfig.isBlockRegionEntry()) return;
        if (StageConfig.isAllowCreativeBypass() && player.isCreative()) return;

        ResourceLocation dim = player.level().dimension().location();
        double px = player.getX();
        double py = player.getY();
        double pz = player.getZ();

        for (LockRegistry.RegionLockEntry entry : LockRegistry.getInstance().getRegions()) {
            LockDefinition.RegionLock def = entry.def;
            if (!def.preventEntry()) continue;
            if (!def.dimension().equals(dim)) continue;
            if (!contains(def, px, py, pz)) continue;
            if (StageManager.getInstance().hasStage(player, entry.requiredStage)) continue;

            // Apply debuffs before push-out so the player feels the "you shouldn't be here"
            // moment even if the teleport is instant. Plan §2.10 "severe debuffs".
            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 60, 2, true, false));
            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                net.minecraft.world.effect.MobEffects.BLINDNESS, 60, 0, true, false));

            pushOutside(player, def);
            ItemEnforcer.notifyLockedWithCooldown(player, entry.requiredStage, "This region");
            return;
        }
    }

    /** {@code true} if the player can break a block at this position. */
    public static boolean canBreakBlock(ServerPlayer player, BlockPos pos) {
        return canDoInRegion(player, pos, RegionFlag.BLOCK_BREAK);
    }

    /** {@code true} if the player can place a block at this position. */
    public static boolean canPlaceBlock(ServerPlayer player, BlockPos pos) {
        return canDoInRegion(player, pos, RegionFlag.BLOCK_PLACE);
    }

    private static boolean canDoInRegion(ServerPlayer player, BlockPos pos, RegionFlag flag) {
        if (!StageConfig.isBlockRegionEntry()) return true;
        if (StageConfig.isAllowCreativeBypass() && player.isCreative()) return true;

        ResourceLocation dim = player.level().dimension().location();
        for (LockRegistry.RegionLockEntry entry : LockRegistry.getInstance().getRegions()) {
            LockDefinition.RegionLock def = entry.def;
            if (!def.dimension().equals(dim)) continue;
            if (!contains(def, pos.getX(), pos.getY(), pos.getZ())) continue;

            boolean flagOn = switch (flag) {
                case BLOCK_BREAK -> def.preventBlockBreak();
                case BLOCK_PLACE -> def.preventBlockPlace();
            };
            if (!flagOn) continue;

            if (!StageManager.getInstance().hasStage(player, entry.requiredStage)) {
                return false;
            }
        }
        return true;
    }

    /** Filter an explosion's affected-block list in place: drop any that are inside a prevent_explosions region. */
    public static void filterExplosionBlocks(ServerLevel level, List<BlockPos> affected) {
        if (!StageConfig.isBlockRegionEntry()) return;
        ResourceLocation dim = level.dimension().location();

        affected.removeIf(pos -> {
            for (LockRegistry.RegionLockEntry entry : LockRegistry.getInstance().getRegions()) {
                LockDefinition.RegionLock def = entry.def;
                if (!def.preventExplosions()) continue;
                if (!def.dimension().equals(dim)) continue;
                if (contains(def, pos.getX(), pos.getY(), pos.getZ())) return true;
            }
            return false;
        });
    }

    /** {@code true} if {@code disable_mob_spawning} applies at this position. */
    public static boolean blocksMobSpawn(ServerLevel level, double x, double y, double z) {
        if (!StageConfig.isBlockRegionEntry()) return false;
        ResourceLocation dim = level.dimension().location();
        for (LockRegistry.RegionLockEntry entry : LockRegistry.getInstance().getRegions()) {
            LockDefinition.RegionLock def = entry.def;
            if (!def.disableMobSpawning()) continue;
            if (!def.dimension().equals(dim)) continue;
            if (contains(def, x, y, z)) return true;
        }
        return false;
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private static boolean contains(LockDefinition.RegionLock def, double x, double y, double z) {
        int[] a = def.pos1();
        int[] b = def.pos2();
        int minX = Math.min(a[0], b[0]), maxX = Math.max(a[0], b[0]);
        int minY = Math.min(a[1], b[1]), maxY = Math.max(a[1], b[1]);
        int minZ = Math.min(a[2], b[2]), maxZ = Math.max(a[2], b[2]);
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    /** Teleport the player just outside the nearest face of the region. */
    private static void pushOutside(ServerPlayer player, LockDefinition.RegionLock def) {
        int[] a = def.pos1();
        int[] b = def.pos2();
        double minX = Math.min(a[0], b[0]), maxX = Math.max(a[0], b[0]) + 1;
        double minZ = Math.min(a[2], b[2]), maxZ = Math.max(a[2], b[2]) + 1;
        double px = player.getX();
        double pz = player.getZ();

        // Pick the closest face on the XZ plane.
        double distMinX = Math.abs(px - minX);
        double distMaxX = Math.abs(maxX - px);
        double distMinZ = Math.abs(pz - minZ);
        double distMaxZ = Math.abs(maxZ - pz);
        double best = Math.min(Math.min(distMinX, distMaxX), Math.min(distMinZ, distMaxZ));

        double tx = px, tz = pz;
        if (best == distMinX)       tx = minX - 0.5;
        else if (best == distMaxX)  tx = maxX + 0.5;
        else if (best == distMinZ)  tz = minZ - 0.5;
        else                        tz = maxZ + 0.5;

        player.teleportTo(tx, player.getY(), tz);
    }

    private enum RegionFlag { BLOCK_BREAK, BLOCK_PLACE }
}
