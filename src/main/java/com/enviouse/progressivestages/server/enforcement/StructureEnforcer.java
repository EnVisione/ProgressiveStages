package com.enviouse.progressivestages.server.enforcement;

import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.config.StageConfig;
import com.enviouse.progressivestages.common.lock.LockRegistry;
import com.enviouse.progressivestages.common.lock.ConditionalRule;
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
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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
 * <p>Structure entry is multi-stage. Declaring the same structure from several stage files
 * requires all of those stages, matching the rest of ProgressiveStages' lock model.
 */
public final class StructureEnforcer {

    private StructureEnforcer() {}

    /**
     * v2.5: last position (per player) recorded while standing OUTSIDE every locked structure. When
     * a player crosses into a gated structure they lack the stage for, they're teleported back here
     * — a natural "bounce back to where you came from", the way {@code /locate} + a soft barrier would
     * behave. Falls back to a nearest-edge push when no safe spot is on record (e.g. they logged in
     * inside the structure).
     */
    private record SafePos(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dim,
                           double x, double y, double z) {}
    private static final Map<UUID, SafePos> SAFE_POS = new ConcurrentHashMap<>();

    // ---------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------

    public static void checkPlayerEntry(ServerPlayer player) {
        if (!StageConfig.isBlockStructureEntry()) return;
        if (StageConfig.isAllowCreativeBypass() && player.isCreative()) return;

        ServerLevel level = (ServerLevel) player.level();
        LockRegistry.StructureRulesAggregate agg = LockRegistry.getInstance().getStructures();
        if (agg.lockedEntry.isEmpty()
                && !ConditionalLockEngine.hasRules(ConditionalRule.TargetType.STRUCTURE)) return;

        BlockPos pos = player.blockPosition();
        Registry<Structure> structureRegistry =
            level.registryAccess().registryOrThrow(Registries.STRUCTURE);

        for (ResourceLocation structureId : candidateStructures(structureRegistry, agg)) {
            Structure structure = structureRegistry.get(structureId);
            if (structure == null) continue;
            StructureStart start = level.structureManager().getStructureAt(pos, structure);
            if (start == StructureStart.INVALID_START) continue;
            if (!start.getBoundingBox().isInside(pos)) continue;
            java.util.Optional<StageId> missing = firstMissing(player,
                agg.lockedEntry.getOrDefault(structureId, java.util.Set.of()));
            ConditionalLockEngine.Decision decision = ConditionalLockEngine.resolve(player,
                ConditionalRule.TargetType.STRUCTURE, structureId, null, missing.isPresent());
            if (decision == null || decision.effect() != ConditionalRule.Effect.LOCK) continue;
            StageId source = decision.ownerStage() != null ? decision.ownerStage() : missing.orElse(null);
            if (source == null) continue;

            // Apply mining fatigue while inside a locked structure that sets prevent_block_break.
            // This is the §2.12 "Indestructibility" signal — block breaks are already cancelled
            // by canBreakBlock(), so this adds the tactile slowdown players expect.
            if (agg.preventBlockBreak) {
                player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.DIG_SLOWDOWN, 60, 4, true, false));
            }

            repel(player, start.getBoundingBox(), agg.entryPadding);
            ItemEnforcer.notifyLockedWithCooldown(player, source, "This structure");
            return;
        }

        // Outside every locked structure → remember this as the safe spot to bounce back to.
        SAFE_POS.put(player.getUUID(),
            new SafePos(level.dimension(), player.getX(), player.getY(), player.getZ()));
    }

    /** Drop the per-player safe-position record on logout. */
    public static void cleanupPlayer(UUID playerId) {
        SAFE_POS.remove(playerId);
    }

    public static void resetRuntimeState() {
        SAFE_POS.clear();
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
        for (Map.Entry<ResourceLocation, java.util.Set<StageId>> entry : agg.lockedEntry.entrySet()) {
            Structure structure = structureRegistry.get(entry.getKey());
            if (structure == null) continue;
            StructureStart start = level.structureManager().getStructureAt(pos, structure);
            if (start == StructureStart.INVALID_START) continue;
            if (!start.getBoundingBox().isInside(pos)) continue;
            return entry.getValue().stream().findFirst();
        }
        return java.util.Optional.empty();
    }

    /** First structure gate at {@code pos} that this player is still missing. */
    public static java.util.Optional<StageId> getLockedEntryStageAt(ServerLevel level, BlockPos pos,
                                                                    ServerPlayer player) {
        LockRegistry.StructureRulesAggregate agg = LockRegistry.getInstance().getStructures();
        if (agg.lockedEntry.isEmpty()
                && !ConditionalLockEngine.hasRules(ConditionalRule.TargetType.STRUCTURE)) {
            return java.util.Optional.empty();
        }
        Registry<Structure> structureRegistry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        for (ResourceLocation structureId : candidateStructures(structureRegistry, agg)) {
            Structure structure = structureRegistry.get(structureId);
            if (structure == null) continue;
            StructureStart start = level.structureManager().getStructureAt(pos, structure);
            if (start != StructureStart.INVALID_START && start.getBoundingBox().isInside(pos)) {
                java.util.Optional<StageId> missing = firstMissing(player,
                    agg.lockedEntry.getOrDefault(structureId, java.util.Set.of()));
                ConditionalLockEngine.Decision decision = ConditionalLockEngine.resolve(player,
                    ConditionalRule.TargetType.STRUCTURE, structureId, null, missing.isPresent());
                if (decision != null && decision.effect() == ConditionalRule.Effect.LOCK) {
                    if (decision.ownerStage() != null) return java.util.Optional.of(decision.ownerStage());
                    if (missing.isPresent()) return missing;
                }
            }
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
        if (!flagOn || (agg.lockedEntry.isEmpty()
                && !ConditionalLockEngine.hasRules(ConditionalRule.TargetType.STRUCTURE))) return true;

        ServerLevel level = (ServerLevel) player.level();
        Registry<Structure> structureRegistry =
            level.registryAccess().registryOrThrow(Registries.STRUCTURE);

        for (ResourceLocation structureId : candidateStructures(structureRegistry, agg)) {
            Structure structure = structureRegistry.get(structureId);
            if (structure == null) continue;
            StructureStart start = level.structureManager().getStructureAt(pos, structure);
            if (start == StructureStart.INVALID_START) continue;
            if (!start.getBoundingBox().isInside(pos)) continue;
            boolean staticBlocked = firstMissing(player,
                agg.lockedEntry.getOrDefault(structureId, java.util.Set.of())).isPresent();
            if (ConditionalLockEngine.isBlocked(player, ConditionalRule.TargetType.STRUCTURE,
                    structureId, null, staticBlocked)) return false;
        }
        return true;
    }

    private static java.util.Set<ResourceLocation> candidateStructures(
            Registry<Structure> registry, LockRegistry.StructureRulesAggregate aggregate) {
        java.util.Set<ResourceLocation> ids = new java.util.LinkedHashSet<>(aggregate.lockedEntry.keySet());
        ids.addAll(ConditionalLockEngine.structureTargetIds(registry));
        return ids;
    }

    private static java.util.Optional<StageId> firstMissing(ServerPlayer player,
                                                             java.util.Collection<StageId> stages) {
        for (StageId stage : stages) {
            if (!StageManager.getInstance().hasStage(player, stage)) return java.util.Optional.of(stage);
        }
        return java.util.Optional.empty();
    }

    /**
     * Bounce the player out of a gated structure: back to their last recorded safe position when we
     * have one (and it's genuinely outside the box), otherwise to the nearest box edge plus the
     * configured {@code entry_padding} buffer.
     */
    private static void repel(ServerPlayer player, BoundingBox box, int pad) {
        SafePos safe = SAFE_POS.get(player.getUUID());
        // Only bounce back to the safe spot if it's in THIS dimension (a stale cross-dimension
        // coordinate would teleport the player into wrong terrain/void) and genuinely outside the box.
        if (safe != null && safe.dim().equals(player.level().dimension())
                && !box.isInside(BlockPos.containing(safe.x(), safe.y(), safe.z()))) {
            player.teleportTo(safe.x(), safe.y(), safe.z());
            return;
        }
        pushOutside(player, box, Math.max(1.5, pad + 0.5));
    }

    private static void pushOutside(ServerPlayer player, BoundingBox box, double buffer) {
        double px = player.getX();
        double pz = player.getZ();
        double minX = box.minX() - buffer;
        double maxX = box.maxX() + buffer;
        double minZ = box.minZ() - buffer;
        double maxZ = box.maxZ() + buffer;

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
