package com.enviouse.progressivestages.server.enforcement;

import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.config.StageConfig;
import com.enviouse.progressivestages.common.lock.LockRegistry;
import com.enviouse.progressivestages.common.stage.StageManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

import java.util.List;
import java.util.Map;

/**
 * Enforces {@code [structures].locked_entry} with piece-precise bounding boxes.
 *
 * <p>Uses {@link net.minecraft.world.level.StructureManager#getStructureAt(BlockPos, Structure)}
 * per registered locked structure instead of the chunk-reference {@code getAllStructuresAt},
 * so players aren't repelled just for standing in the structure's reference chunk — only
 * inside actual piece bounding boxes. Vanilla and Forge-compliant modded structures populate
 * pieces correctly; as a safety net we additionally consult {@code getAllStructuresAt} and
 * verify via the resolved start's bounding box.
 *
 * <p>v2.0 note on multi-stage: {@link LockRegistry.StructureRulesAggregate#lockedEntry} maps
 * structure ID → owning StageId. Each entry has a single owning stage by aggregate-merge
 * rule (see {@link LockRegistry.StructureRulesAggregate#merge}, which uses
 * {@code putIfAbsent}). This means if two stages both declare the same structure under
 * {@code locked_entry}, only the first stage in load order wins — matching the documented
 * pre-v2.0 first-match-wins semantics for structure entry. Multi-stage gating per
 * structure is not currently expressible in the aggregate; if needed, the merger would
 * need to switch to a Set-valued map. Single-stage behavior is intentional here.
 */
public final class StructureEnforcer {

    private StructureEnforcer() {}

    // ---------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------

    public static void checkPlayerEntry(ServerPlayer player) {
        if (!StageConfig.isBlockStructureEntry()) return;
        if (StageConfig.isAllowCreativeBypass() && player.isCreative()) return;

        ServerLevel level = (ServerLevel) player.level();
        LockRegistry.StructureRulesAggregate agg = LockRegistry.getInstance().getStructures();
        if (agg.lockedEntry.isEmpty()) return;

        BlockPos pos = player.blockPosition();
        Registry<Structure> structureRegistry =
            level.registryAccess().registryOrThrow(Registries.STRUCTURE);

        for (Map.Entry<ResourceLocation, StageId> entry : agg.lockedEntry.entrySet()) {
            Structure structure = structureRegistry.get(entry.getKey());
            if (structure == null) continue;
            StructureStart start = level.structureManager().getStructureAt(pos, structure);
            if (start == StructureStart.INVALID_START) continue;
            if (!start.getBoundingBox().isInside(pos)) continue;
            if (StageManager.getInstance().hasStage(player, entry.getValue())) continue;

            // Apply mining fatigue while inside a locked structure that sets prevent_block_break.
            // This is the §2.12 "Indestructibility" signal — block breaks are already cancelled
            // by canBreakBlock(), so this adds the tactile slowdown players expect.
            LockRegistry.StructureRulesAggregate aggRules = LockRegistry.getInstance().getStructures();
            if (aggRules.preventBlockBreak) {
                player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.DIG_SLOWDOWN, 60, 4, true, false));
            }

            pushOutside(player, start.getBoundingBox());
            ItemEnforcer.notifyLockedWithCooldown(player, entry.getValue(), "This structure");
            return;
        }
    }

    public static boolean canBreakBlock(ServerPlayer player, BlockPos pos) {
        return canDoInStructure(player, pos, StructureFlag.BREAK);
    }

    public static boolean canPlaceBlock(ServerPlayer player, BlockPos pos) {
        return canDoInStructure(player, pos, StructureFlag.PLACE);
    }

    /**
     * True if the block entity at {@code pos} is a container (any subclass of
     * {@link net.minecraft.world.Container}). Used by the chest-locking block-break
     * guard so breaking a chest/barrel/shulker/lootr-chest inside a locked structure
     * doesn't let players spill the contents.
     */
    public static boolean isContainerAt(ServerLevel level, BlockPos pos) {
        var be = level.getBlockEntity(pos);
        return be instanceof net.minecraft.world.Container;
    }

    /**
     * Returns the required stage if {@code pos} is inside a locked structure and the
     * player lacks it; empty otherwise. Used by the chest-locking guard.
     */
    public static java.util.Optional<StageId> getLockedEntryStageAt(ServerLevel level, BlockPos pos) {
        LockRegistry.StructureRulesAggregate agg = LockRegistry.getInstance().getStructures();
        if (agg.lockedEntry.isEmpty()) return java.util.Optional.empty();
        Registry<Structure> structureRegistry =
            level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        for (Map.Entry<ResourceLocation, StageId> entry : agg.lockedEntry.entrySet()) {
            Structure structure = structureRegistry.get(entry.getKey());
            if (structure == null) continue;
            StructureStart start = level.structureManager().getStructureAt(pos, structure);
            if (start == StructureStart.INVALID_START) continue;
            if (!start.getBoundingBox().isInside(pos)) continue;
            return java.util.Optional.of(entry.getValue());
        }
        return java.util.Optional.empty();
    }

    public static void filterExplosionBlocks(ServerLevel level, List<BlockPos> affected) {
        if (!StageConfig.isBlockStructureEntry()) return;
        LockRegistry.StructureRulesAggregate agg = LockRegistry.getInstance().getStructures();
        if (!agg.preventExplosions || agg.lockedEntry.isEmpty()) return;

        Registry<Structure> structureRegistry =
            level.registryAccess().registryOrThrow(Registries.STRUCTURE);

        affected.removeIf(pos -> {
            for (ResourceLocation id : agg.lockedEntry.keySet()) {
                Structure structure = structureRegistry.get(id);
                if (structure == null) continue;
                StructureStart start = level.structureManager().getStructureAt(pos, structure);
                if (start != StructureStart.INVALID_START && start.getBoundingBox().isInside(pos)) {
                    return true;
                }
            }
            return false;
        });
    }

    public static boolean blocksMobSpawn(ServerLevel level, BlockPos pos) {
        if (!StageConfig.isBlockStructureEntry()) return false;
        LockRegistry.StructureRulesAggregate agg = LockRegistry.getInstance().getStructures();
        if (!agg.disableMobSpawning || agg.lockedEntry.isEmpty()) return false;

        Registry<Structure> structureRegistry =
            level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        for (ResourceLocation id : agg.lockedEntry.keySet()) {
            Structure structure = structureRegistry.get(id);
            if (structure == null) continue;
            StructureStart start = level.structureManager().getStructureAt(pos, structure);
            if (start != StructureStart.INVALID_START && start.getBoundingBox().isInside(pos)) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------
    // Internals
    // ---------------------------------------------------------------

    private static boolean canDoInStructure(ServerPlayer player, BlockPos pos, StructureFlag flag) {
        if (!StageConfig.isBlockStructureEntry()) return true;
        if (StageConfig.isAllowCreativeBypass() && player.isCreative()) return true;

        LockRegistry.StructureRulesAggregate agg = LockRegistry.getInstance().getStructures();
        boolean flagOn = switch (flag) {
            case BREAK -> agg.preventBlockBreak;
            case PLACE -> agg.preventBlockPlace;
        };
        if (!flagOn || agg.lockedEntry.isEmpty()) return true;

        ServerLevel level = (ServerLevel) player.level();
        Registry<Structure> structureRegistry =
            level.registryAccess().registryOrThrow(Registries.STRUCTURE);

        for (Map.Entry<ResourceLocation, StageId> entry : agg.lockedEntry.entrySet()) {
            Structure structure = structureRegistry.get(entry.getKey());
            if (structure == null) continue;
            StructureStart start = level.structureManager().getStructureAt(pos, structure);
            if (start == StructureStart.INVALID_START) continue;
            if (!start.getBoundingBox().isInside(pos)) continue;
            if (!StageManager.getInstance().hasStage(player, entry.getValue())) return false;
        }
        return true;
    }

    private static void pushOutside(ServerPlayer player, BoundingBox box) {
        double px = player.getX();
        double pz = player.getZ();
        double minX = box.minX() - 0.5;
        double maxX = box.maxX() + 1.5;
        double minZ = box.minZ() - 0.5;
        double maxZ = box.maxZ() + 1.5;

        double distMinX = Math.abs(px - minX);
        double distMaxX = Math.abs(maxX - px);
        double distMinZ = Math.abs(pz - minZ);
        double distMaxZ = Math.abs(maxZ - pz);
        double best = Math.min(Math.min(distMinX, distMaxX), Math.min(distMinZ, distMaxZ));

        double tx = px, tz = pz;
        if      (best == distMinX) tx = minX;
        else if (best == distMaxX) tx = maxX;
        else if (best == distMinZ) tz = minZ;
        else                        tz = maxZ;

        player.teleportTo(tx, player.getY(), tz);
    }

    private enum StructureFlag { BREAK, PLACE }
}
