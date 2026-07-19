package com.enviouse.progressivestages.server.enforcement;

import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.api.structure.StructureAccessDecision;
import com.enviouse.progressivestages.common.api.structure.StructureAccessArbitration;
import com.enviouse.progressivestages.common.api.structure.StructureAccessDeniedEvent;
import com.enviouse.progressivestages.common.api.structure.StructureAccessRequest;
import com.enviouse.progressivestages.common.api.structure.StructureAction;
import com.enviouse.progressivestages.common.api.structure.StructureBounds;
import com.enviouse.progressivestages.common.api.structure.StructureInstanceKey;
import com.enviouse.progressivestages.common.api.structure.StructureSessionId;
import com.enviouse.progressivestages.common.api.structure.StructureOwnershipScope;
import com.enviouse.progressivestages.common.config.StageConfig;
import com.enviouse.progressivestages.common.lock.LockRegistry;
import com.enviouse.progressivestages.common.lock.ConditionalRule;
import com.enviouse.progressivestages.common.stage.StageManager;
import com.enviouse.progressivestages.common.team.TeamProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.neoforged.neoforge.common.NeoForge;
import com.enviouse.progressivestages.server.structure.StructureContextRegistry;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
    private record LookupKey(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension,
                             BlockPos position, ResourceLocation structureId, long tick) {}
    private record LocatedStructure(StructureInstanceKey instance, StructureBounds bounds) {}
    private static final Map<UUID, SafePos> SAFE_POS = new ConcurrentHashMap<>();
    private static final Map<LookupKey, Optional<LocatedStructure>> LOOKUP_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Long> DENIAL_EVENT_TICKS = new ConcurrentHashMap<>();
    private static long lookupCacheTick = Long.MIN_VALUE;

    public record EvaluationResult(boolean allowed, StructureAccessDecision.Reason reason,
                                   StageId displayStage, StructureBounds bounds,
                                   StructureSessionId sessionId, ResourceLocation providerId,
                                   StructureInstanceKey instance) {}

    // ---------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------

    public static void checkPlayerEntry(ServerPlayer player) {
        if (StageConfig.isAllowCreativeBypass() && player.isCreative()) return;
        ServerLevel level = (ServerLevel) player.level();
        EvaluationResult result = evaluate(player, player.blockPosition(), StructureAction.ENTRY);
        if (!result.allowed()) {
            LockRegistry.StructureRulesAggregate aggregate = LockRegistry.getInstance().getStructures();
            if (aggregate.preventBlockBreak) {
                player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.DIG_SLOWDOWN, 60, 4, true, false));
            }
            StructureBounds bounds = result.bounds() != null ? result.bounds()
                : new StructureBounds(player.getBlockX(), player.getBlockY(), player.getBlockZ(),
                    player.getBlockX(), player.getBlockY(), player.getBlockZ());
            repel(player, bounds.toBoundingBox(), aggregate.entryPadding);
            if (result.displayStage() != null) {
                ItemEnforcer.notifyLockedWithCooldown(player, result.displayStage(), "This structure");
            }
            return;
        }
        SAFE_POS.put(player.getUUID(),
            new SafePos(level.dimension(), player.getX(), player.getY(), player.getZ()));
    }

    public static EvaluationResult evaluate(ServerPlayer player, BlockPos pos, StructureAction action) {
        if (player.isSpectator()
                || StageConfig.isAllowCreativeBypass() && player.isCreative()) return allowedResult();
        ServerLevel level = player.serverLevel();
        LockRegistry.StructureRulesAggregate aggregate = LockRegistry.getInstance().getStructures();
        Registry<Structure> registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        EvaluationResult staticDenial = null;
        StructureInstanceKey candidate = null;
        StructureBounds candidateBounds = null;

        if (StageConfig.isBlockStructureEntry()) {
            for (ResourceLocation structureId : candidateStructures(registry, aggregate)) {
                Structure structure = registry.get(structureId);
                if (structure == null) continue;
                Optional<LocatedStructure> found = cachedStructure(level, pos, structureId, structure);
                if (found.isEmpty()) continue;
                StructureBounds bounds = found.get().bounds();
                candidate = found.get().instance();
                candidateBounds = bounds;
                Optional<StageId> missing = firstMissing(player,
                    aggregate.lockedEntry.getOrDefault(structureId, Set.of()));
                ConditionalLockEngine.Decision conditional = ConditionalLockEngine.resolve(player,
                    ConditionalRule.TargetType.STRUCTURE, structureId, null, missing.isPresent());
                boolean locked = conditional != null && conditional.effect() == ConditionalRule.Effect.LOCK;
                if (!locked || !staticProtects(action, aggregate)) continue;
                StageId display = conditional.ownerStage() != null
                    ? conditional.ownerStage() : missing.orElse(null);
                staticDenial = new EvaluationResult(false,
                    StructureAccessDecision.Reason.STATIC_STAGE_REQUIRED, display, bounds,
                    null, null, candidate);
                break;
            }
        }

        if (candidate == null && StructureContextRegistry.getInstance().hasProviders()) {
            Optional<LocatedStructure> discovered = discoverCandidate(level, pos, registry);
            if (discovered.isPresent()) {
                candidate = discovered.get().instance();
                candidateBounds = discovered.get().bounds();
            }
        }

        StructureAccessRequest request = new StructureAccessRequest(player, level, pos, action,
            Optional.ofNullable(candidate));
        StructureContextRegistry.Evaluation provider = StructureContextRegistry.getInstance().evaluate(request);
        StructureAccessDecision.Result finalResult = StructureAccessArbitration.combine(
            staticDenial != null, provider.decision().result());
        if (finalResult == StructureAccessDecision.Result.DENY) {
            if (provider.decision().result() != StructureAccessDecision.Result.DENY) {
                StructureAccessDecision decision = StructureAccessDecision.deny(staticDenial.reason(),
                    staticDenial.displayStage(), null, staticDenial.bounds());
                postDenied(request, decision, null);
                return staticDenial;
            }
            StructureAccessDecision supplied = provider.decision();
            StructureAccessDecision decision = StructureAccessDecision.deny(supplied.reason(),
                supplied.displayStage().orElse(staticDenial == null ? null : staticDenial.displayStage()),
                supplied.sessionId().orElse(null),
                supplied.bounds().orElse(staticDenial == null ? candidateBounds : staticDenial.bounds()));
            EvaluationResult denied = new EvaluationResult(false, decision.reason(),
                decision.displayStage().orElse(null), decision.bounds().orElse(null),
                decision.sessionId().orElse(null), provider.providerId(), candidate);
            postDenied(request, decision, provider.providerId());
            return denied;
        }
        if (finalResult == StructureAccessDecision.Result.PERMIT) {
            var known = provider.decision().sessionId().flatMap(sessionId ->
                StructureContextRegistry.getInstance().knownSession(player, provider.providerId(), sessionId)
                    .or(() -> StructureContextRegistry.getInstance().session(provider.providerId(), sessionId)));
            if (known.isEmpty()) {
                StructureAccessDecision deniedDecision = StructureAccessDecision.deny(
                    StructureAccessDecision.Reason.SESSION_CLOSED,
                    provider.decision().displayStage().orElse(null),
                    provider.decision().sessionId().orElse(null),
                    provider.decision().bounds().orElse(candidateBounds));
                postDenied(request, deniedDecision, provider.providerId());
                return new EvaluationResult(false, StructureAccessDecision.Reason.SESSION_CLOSED,
                    provider.decision().displayStage().orElse(null),
                    provider.decision().bounds().orElse(candidateBounds),
                    provider.decision().sessionId().orElse(null), provider.providerId(), candidate);
            }
            if (known.isPresent()) {
                var spec = known.get();
                UUID effectiveOwner = spec.ownershipScope() == StructureOwnershipScope.PLAYER
                    ? player.getUUID() : TeamProvider.getInstance().getTeamId(player);
                Optional<StructureInstanceKey> sessionCandidate = request.candidateInstance()
                    .filter(instance -> instance.structureId().equals(spec.instance().structureId()));
                if (sessionCandidate.isEmpty()) {
                    Structure sessionStructure = registry.get(spec.instance().structureId());
                    if (sessionStructure != null) {
                        sessionCandidate = cachedStructure(level, pos, spec.instance().structureId(),
                            sessionStructure).map(LocatedStructure::instance);
                    }
                }
                Optional<StructureAccessDecision.Reason> coreReason =
                    StructureSessionAccessPolicy.validatePermit(effectiveOwner, level.dimension(), pos,
                        sessionCandidate, spec,
                        StageManager.getInstance().hasStage(player, spec.accessStage()));
                if (coreReason.isPresent()) {
                    StructureAccessDecision deniedDecision = StructureAccessDecision.deny(coreReason.get(),
                        spec.accessStage(), spec.sessionId(), spec.bounds());
                    postDenied(request, deniedDecision, provider.providerId());
                    return new EvaluationResult(false, coreReason.get(), spec.accessStage(), spec.bounds(),
                        spec.sessionId(), provider.providerId(), spec.instance());
                }
                return new EvaluationResult(true, StructureAccessDecision.Reason.NONE,
                    provider.decision().displayStage().orElse(null), spec.bounds(),
                    spec.sessionId(), provider.providerId(), spec.instance());
            }
        }
        return new EvaluationResult(true, StructureAccessDecision.Reason.NONE,
            null, null, null, null, candidate);
    }

    private static boolean staticProtects(StructureAction action,
                                          LockRegistry.StructureRulesAggregate aggregate) {
        return switch (action) {
            case ENTRY -> true;
            case BLOCK_BREAK -> aggregate.preventBlockBreak;
            case BLOCK_PLACE -> aggregate.preventBlockPlace;
            case CONTAINER_OPEN, BLOCK_INTERACT, ENTITY_INTERACT -> true;
            case ITEM_USE -> false;
        };
    }

    private static EvaluationResult allowedResult() {
        return new EvaluationResult(true, StructureAccessDecision.Reason.NONE,
            null, null, null, null, null);
    }

    /** Drop the per-player safe-position record on logout. */
    public static void cleanupPlayer(UUID playerId) {
        SAFE_POS.remove(playerId);
        String prefix = playerId + "|";
        DENIAL_EVENT_TICKS.keySet().removeIf(key -> key.startsWith(prefix));
    }

    public static void resetRuntimeState() {
        SAFE_POS.clear();
        LOOKUP_CACHE.clear();
        DENIAL_EVENT_TICKS.clear();
        lookupCacheTick = Long.MIN_VALUE;
    }

    public static boolean canBreakBlock(ServerPlayer player, BlockPos pos) {
        return evaluate(player, pos, StructureAction.BLOCK_BREAK).allowed();
    }

    public static boolean canPlaceBlock(ServerPlayer player, BlockPos pos) {
        return evaluate(player, pos, StructureAction.BLOCK_PLACE).allowed();
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

    /** First structure gate at {@code pos} that this player is still missing. */
    public static java.util.Optional<StageId> getLockedEntryStageAt(ServerLevel level, BlockPos pos,
                                                                    ServerPlayer player) {
        EvaluationResult result = evaluate(player, pos, StructureAction.BLOCK_INTERACT);
        return result.allowed() ? Optional.empty() : Optional.ofNullable(result.displayStage());
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

    private static java.util.Set<ResourceLocation> candidateStructures(
            Registry<Structure> registry, LockRegistry.StructureRulesAggregate aggregate) {
        java.util.Set<ResourceLocation> ids = new java.util.LinkedHashSet<>(aggregate.lockedEntry.keySet());
        ids.addAll(ConditionalLockEngine.structureTargetIds(registry));
        return ids;
    }

    private static Optional<LocatedStructure> cachedStructure(ServerLevel level, BlockPos position,
                                                               ResourceLocation structureId,
                                                               Structure structure) {
        long tick = level.getGameTime();
        if (lookupCacheTick != tick) {
            LOOKUP_CACHE.clear();
            lookupCacheTick = tick;
        }
        LookupKey key = new LookupKey(level.dimension(), position.immutable(), structureId, tick);
        return LOOKUP_CACHE.computeIfAbsent(key, ignored -> {
            StructureStart start = level.structureManager().getStructureAt(position, structure);
            if (start == StructureStart.INVALID_START || !start.getBoundingBox().isInside(position)) {
                return Optional.empty();
            }
            StructureInstanceKey instance = StructureInstanceKey.fromStartChunk(
                level.dimension(), structureId, start.getChunkPos());
            return Optional.of(new LocatedStructure(instance, StructureBounds.of(start.getBoundingBox())));
        });
    }

    private static Optional<LocatedStructure> discoverCandidate(ServerLevel level, BlockPos position,
                                                                 Registry<Structure> registry) {
        return level.structureManager().getAllStructuresAt(position).keySet().stream()
            .map(registry::getKey)
            .filter(java.util.Objects::nonNull)
            .sorted(java.util.Comparator.comparing(ResourceLocation::toString))
            .map(id -> {
                Structure structure = registry.get(id);
                return structure == null ? Optional.<LocatedStructure>empty()
                    : cachedStructure(level, position, id, structure);
            })
            .flatMap(Optional::stream)
            .findFirst();
    }

    private static java.util.Optional<StageId> firstMissing(ServerPlayer player,
                                                             java.util.Collection<StageId> stages) {
        for (StageId stage : stages) {
            if (!StageManager.getInstance().hasStage(player, stage)) return java.util.Optional.of(stage);
        }
        return java.util.Optional.empty();
    }

    private static void postDenied(StructureAccessRequest request, StructureAccessDecision decision,
                                   ResourceLocation providerId) {
        long now = request.level().getGameTime();
        String key = request.player().getUUID() + "|" + providerId + "|"
            + decision.sessionId().map(Object::toString).orElse("") + "|"
            + request.action() + "|" + decision.reason();
        Long previous = DENIAL_EVENT_TICKS.get(key);
        if (previous == null || now - previous >= 20L) {
            DENIAL_EVENT_TICKS.put(key, now);
            NeoForge.EVENT_BUS.post(new StructureAccessDeniedEvent(request, decision, providerId));
        }
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
}
