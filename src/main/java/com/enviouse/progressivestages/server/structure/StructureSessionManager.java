package com.enviouse.progressivestages.server.structure;

import com.enviouse.progressivestages.common.api.ProgressiveStagesAPI;
import com.enviouse.progressivestages.common.api.StageCause;
import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.api.structure.StructureAction;
import com.enviouse.progressivestages.common.api.structure.StructureCleanupPolicy;
import com.enviouse.progressivestages.common.api.structure.StructureLeaveOutcome;
import com.enviouse.progressivestages.common.api.structure.StructureOwnershipScope;
import com.enviouse.progressivestages.common.api.structure.StructureSessionCompletionEvent;
import com.enviouse.progressivestages.common.api.structure.StructureSessionEnterEvent;
import com.enviouse.progressivestages.common.api.structure.StructureSessionId;
import com.enviouse.progressivestages.common.api.structure.StructureSessionLeaveEvent;
import com.enviouse.progressivestages.common.api.structure.StructureSessionSpec;
import com.enviouse.progressivestages.common.api.structure.StructureSessionView;
import com.enviouse.progressivestages.common.stage.StageManager;
import com.enviouse.progressivestages.common.stage.StageOrder;
import com.enviouse.progressivestages.common.team.TeamProvider;
import com.enviouse.progressivestages.server.enforcement.StructureEnforcer;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class StructureSessionManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final StructureSessionManager INSTANCE = new StructureSessionManager();
    private static final long RECONCILE_INTERVAL = 20L;
    private static final long EXIT_DEBOUNCE_TICKS = 10L;

    private record SessionKey(ResourceLocation providerId, StructureSessionId sessionId) {}
    private record LeaseKey(UUID owner, StageId stage) {}
    record ParticipantLease(ResourceLocation providerId, StructureSessionId sessionId,
                            UUID participant) {}
    private record AcquireResult(boolean acquired, boolean granted, UUID owner) {}

    private static final class ParticipantState {
        private final long visitSequence;
        private UUID leaseOwner;
        private boolean inside = true;
        private long exitDeadline = -1L;
        private BlockPos lastPosition;

        private ParticipantState(long visitSequence, BlockPos position, UUID leaseOwner) {
            this.visitSequence = visitSequence;
            this.lastPosition = position.immutable();
            this.leaseOwner = leaseOwner;
        }
    }

    private static final class RuntimeSession {
        private StructureSessionSpec spec;
        private final Map<UUID, ParticipantState> participants = new LinkedHashMap<>();
        private long visitSequence;
        private boolean complete;
        private boolean accessRevoked;

        private RuntimeSession(StructureSessionSpec spec) {
            this.spec = spec;
            this.complete = spec.complete();
        }
    }

    static final class LeaseBucket {
        private final boolean introduced;
        private final Set<ParticipantLease> participants = new LinkedHashSet<>();

        LeaseBucket(boolean introduced) {
            this.introduced = introduced;
        }

        boolean acquire(ParticipantLease participant) {
            return participants.add(participant);
        }

        boolean release(ParticipantLease participant) {
            return participants.remove(participant);
        }

        boolean contains(ParticipantLease participant) {
            return participants.contains(participant);
        }

        int participantCount() {
            return participants.size();
        }

        boolean shouldRevoke() {
            return introduced && participants.isEmpty();
        }

        boolean introduced() {
            return introduced;
        }
    }

    private final Map<SessionKey, RuntimeSession> sessions = new LinkedHashMap<>();
    private final Map<UUID, Map<SessionKey, StructureSessionSpec>> playerSessions = new HashMap<>();
    private final Map<UUID, Long> lastReconcile = new HashMap<>();
    private final Map<LeaseKey, LeaseBucket> leases = new LinkedHashMap<>();
    private final Set<String> validationWarnings = new LinkedHashSet<>();
    private MinecraftServer server;

    public static StructureSessionManager getInstance() {
        return INSTANCE;
    }

    private StructureSessionManager() {}

    public void bind(MinecraftServer server) {
        this.server = server;
        StructureContextRegistry.getInstance().bind(server);
    }

    public void shutdown(MinecraftServer stoppingServer) {
        if (server != stoppingServer) return;
        sessions.clear();
        playerSessions.clear();
        lastReconcile.clear();
        leases.clear();
        validationWarnings.clear();
        StructureContextRegistry.getInstance().shutdown(stoppingServer);
        server = null;
    }

    public void tick(ServerPlayer player) {
        requireThread();
        if (!StructureContextRegistry.getInstance().hasProviders()) return;
        long now = player.level().getGameTime();
        Long previous = lastReconcile.get(player.getUUID());
        if (previous == null || now - previous >= RECONCILE_INTERVAL) {
            reconcile(player, false);
            lastReconcile.put(player.getUUID(), now);
        }

        Map<SessionKey, StructureSessionSpec> available = playerSessions.getOrDefault(
            player.getUUID(), Map.of());
        for (Map.Entry<SessionKey, StructureSessionSpec> entry : available.entrySet()) {
            StructureSessionSpec spec = entry.getValue();
            if (!spec.instance().dimension().equals(player.level().dimension())) continue;
            RuntimeSession runtime = sessions.computeIfAbsent(entry.getKey(), key -> new RuntimeSession(spec));
            runtime.spec = spec;
            ParticipantState state = runtime.participants.get(player.getUUID());
            boolean inside = spec.bounds().contains(player.blockPosition());
            boolean withinExitBounds = spec.bounds().expanded(1).contains(player.blockPosition());
            if (state != null && !withinExitBounds
                    && distanceSquared(state.lastPosition, player.blockPosition()) > 64L) {
                close(player, entry.getKey(), StructureLeaveOutcome.TELEPORT);
                continue;
            }
            var transition = StructureVisitTransitions.decide(state != null, inside,
                withinExitBounds, state == null ? -1L : state.exitDeadline,
                now, EXIT_DEBOUNCE_TICKS);
            switch (transition.type()) {
                case ENTER -> enter(player, runtime);
                case CANCEL_EXIT -> {
                    state.inside = inside;
                    state.exitDeadline = -1L;
                    state.lastPosition = player.blockPosition().immutable();
                }
                case BEGIN_EXIT -> {
                    state.inside = false;
                    state.exitDeadline = transition.exitDeadline();
                }
                case LEAVE -> close(player, entry.getKey(), runtime.complete
                    ? StructureLeaveOutcome.COMPLETED : StructureLeaveOutcome.INCOMPLETE);
                case NONE -> {
                    if (state != null && withinExitBounds) {
                        state.inside = inside;
                        state.lastPosition = player.blockPosition().immutable();
                    }
                }
            }
        }
    }

    public List<StructureSessionView> reconcile(ServerPlayer player, boolean reconstruct) {
        requireThread();
        List<StructureSessionSpec> supplied = StructureContextRegistry.getInstance().sessionsFor(player);
        Map<SessionKey, StructureSessionSpec> accepted = new LinkedHashMap<>();
        for (StructureSessionSpec spec : supplied) {
            if (!isValidFor(player, spec)) continue;
            SessionKey key = new SessionKey(spec.providerId(), spec.sessionId());
            accepted.put(key, spec);
            RuntimeSession runtime = sessions.computeIfAbsent(key, ignored -> new RuntimeSession(spec));
            runtime.spec = spec;
            if (spec.complete() && !runtime.complete) markComplete(player, runtime);

            ParticipantState participant = runtime.participants.get(player.getUUID());
            boolean physicallyInside = spec.instance().dimension().equals(player.level().dimension())
                && spec.bounds().contains(player.blockPosition());
            if (participant != null) {
                if (!ProgressiveStagesAPI.hasStage(player, spec.accessStage())) {
                    close(player, key, StructureLeaveOutcome.RECOVERY);
                } else {
                    repairLease(player, runtime, participant);
                }
            } else if (reconstruct && physicallyInside && ProgressiveStagesAPI.hasStage(player, spec.accessStage())) {
                AcquireResult acquired = acquireLease(player, runtime);
                if (acquired.acquired()) {
                    runtime.visitSequence++;
                    runtime.participants.put(player.getUUID(),
                        new ParticipantState(runtime.visitSequence, player.blockPosition(), acquired.owner()));
                }
            }
        }
        for (Map.Entry<SessionKey, RuntimeSession> entry : new ArrayList<>(sessions.entrySet())) {
            if (entry.getValue().participants.containsKey(player.getUUID())
                    && !accepted.containsKey(entry.getKey())) {
                close(player, entry.getKey(), StructureLeaveOutcome.RECOVERY);
            }
        }
        playerSessions.put(player.getUUID(), Map.copyOf(accepted));
        recoverPersistedParticipants(player);
        recoverOrphanIntroducedStages(player, accepted);
        return viewsFor(player.getUUID());
    }

    public boolean markComplete(ServerPlayer player, StructureSessionId sessionId) {
        requireThread();
        Map<SessionKey, StructureSessionSpec> known = playerSessions.getOrDefault(player.getUUID(), Map.of());
        for (SessionKey key : known.keySet()) {
            if (!key.sessionId().equals(sessionId)) continue;
            RuntimeSession runtime = sessions.get(key);
            if (runtime == null) return false;
            if (!runtime.complete) markComplete(player, runtime);
            return true;
        }
        return false;
    }

    public boolean close(ServerPlayer player, StructureSessionId sessionId,
                         StructureLeaveOutcome outcome) {
        requireThread();
        for (SessionKey key : new ArrayList<>(sessions.keySet())) {
            if (key.sessionId().equals(sessionId)
                    && sessions.get(key).participants.containsKey(player.getUUID())) {
                return close(player, key, outcome);
            }
        }
        return false;
    }

    public void closeAll(ServerPlayer player, StructureLeaveOutcome outcome) {
        requireThread();
        for (SessionKey key : new ArrayList<>(sessions.keySet())) {
            RuntimeSession runtime = sessions.get(key);
            if (runtime != null && runtime.participants.containsKey(player.getUUID())) {
                close(player, key, outcome);
            }
        }
        playerSessions.remove(player.getUUID());
        lastReconcile.remove(player.getUUID());
    }

    public void closeOutsideTeleport(ServerPlayer player, BlockPos target) {
        requireThread();
        for (SessionKey key : new ArrayList<>(sessions.keySet())) {
            RuntimeSession runtime = sessions.get(key);
            if (runtime != null && runtime.participants.containsKey(player.getUUID())
                    && !runtime.spec.bounds().expanded(1).contains(target)) {
                close(player, key, StructureLeaveOutcome.TELEPORT);
            }
        }
    }

    public List<StructureSessionView> activeSessions(ServerPlayer player) {
        requireThread();
        return viewsFor(player.getUUID());
    }

    public Optional<StructureSessionView> activeSessionForStage(ServerPlayer player, StageId stage) {
        requireThread();
        for (RuntimeSession runtime : sessions.values()) {
            if (!runtime.participants.containsKey(player.getUUID())) continue;
            if (runtime.spec.inProgressStage().filter(stage::equals).isPresent()) {
                return Optional.of(view(runtime));
            }
        }
        return Optional.empty();
    }

    public Optional<StructureSessionSpec> sessionAt(ServerPlayer player) {
        requireThread();
        return playerSessions.getOrDefault(player.getUUID(), Map.of()).values().stream()
            .filter(spec -> spec.instance().dimension().equals(player.level().dimension()))
            .filter(spec -> spec.bounds().contains(player.blockPosition()))
            .findFirst();
    }

    public Collection<StructureSessionSpec> cachedSessions(ServerPlayer player) {
        return List.copyOf(playerSessions.getOrDefault(player.getUUID(), Map.of()).values());
    }

    private boolean enter(ServerPlayer player, RuntimeSession runtime) {
        StructureSessionSpec spec = runtime.spec;
        if (!ProgressiveStagesAPI.hasStage(player, spec.accessStage())) return false;
        StructureEnforcer.EvaluationResult evaluation = StructureEnforcer.evaluate(
            player, player.blockPosition(), StructureAction.ENTRY);
        if (!evaluation.allowed()
                || !spec.providerId().equals(evaluation.providerId())
                || !spec.sessionId().equals(evaluation.sessionId())) return false;

        AcquireResult acquired = acquireLease(player, runtime);
        if (!acquired.acquired()) return false;
        runtime.visitSequence++;
        ParticipantState participant = new ParticipantState(runtime.visitSequence,
            player.blockPosition(), acquired.owner());
        runtime.participants.put(player.getUUID(), participant);
        NeoForge.EVENT_BUS.post(new StructureSessionEnterEvent(player,
            view(runtime, participant.visitSequence),
            effectiveOwner(spec, player), acquired.granted()));
        return true;
    }

    private void markComplete(ServerPlayer player, RuntimeSession runtime) {
        runtime.complete = true;
        ParticipantState participant = runtime.participants.get(player.getUUID());
        long visitSequence = participant == null ? runtime.visitSequence : participant.visitSequence;
        NeoForge.EVENT_BUS.post(new StructureSessionCompletionEvent(player,
            view(runtime, visitSequence),
            effectiveOwner(runtime.spec, player)));
    }

    private boolean close(ServerPlayer player, SessionKey key, StructureLeaveOutcome outcome) {
        RuntimeSession runtime = sessions.get(key);
        if (runtime == null) return false;
        ParticipantState participant = runtime.participants.remove(player.getUUID());
        if (participant == null) return false;
        boolean stageRevoked = releaseLease(player, runtime, participant.leaseOwner);
        if (StructureSessionPolicy.shouldRevokeAccess(runtime.complete, outcome,
                runtime.participants.size(), runtime.spec.cleanupPolicy())) {
            stageRevoked |= revokeCompletedAccess(player, runtime);
        }
        NeoForge.EVENT_BUS.post(new StructureSessionLeaveEvent(player,
            view(runtime, participant.visitSequence),
            effectiveOwner(runtime.spec, player), outcome, stageRevoked));
        return true;
    }

    private AcquireResult acquireLease(ServerPlayer player, RuntimeSession runtime) {
        Optional<StageId> stageOptional = runtime.spec.inProgressStage();
        if (stageOptional.isEmpty()) return new AcquireResult(true, false, null);
        StageId stage = stageOptional.get();
        UUID owner = StageManager.getInstance().getStorageOwner(player, stage);
        LeaseKey key = new LeaseKey(owner, stage);
        ParticipantLease participant = new ParticipantLease(runtime.spec.providerId(),
            runtime.spec.sessionId(), player.getUUID());
        LeaseBucket bucket = leases.get(key);
        if (bucket != null && bucket.contains(participant)) {
            return new AcquireResult(true, false, owner);
        }

        boolean granted = false;
        if (bucket == null) {
            boolean preowned = ProgressiveStagesAPI.hasStage(player, stage);
            boolean persistedIntroduced = server != null
                && StructureLeaseData.get(server).wasIntroduced(owner, stage);
            if (!preowned) {
                granted = StageManager.getInstance().grantTemporaryStage(
                    player, stage, StageCause.STRUCTURE_ENTER);
                if (!granted) return new AcquireResult(false, false, owner);
                if (server != null) StructureLeaseData.get(server).markIntroduced(owner, stage);
            }
            bucket = new LeaseBucket(persistedIntroduced || !preowned);
            leases.put(key, bucket);
        } else if (!ProgressiveStagesAPI.hasStage(player, stage)) {
            if (!StageManager.getInstance().grantTemporaryStage(
                    player, stage, StageCause.STRUCTURE_ENTER)) {
                return new AcquireResult(false, false, owner);
            }
            granted = true;
        }
        bucket.acquire(participant);
        if (bucket.introduced() && server != null) {
            StructureLeaseData.get(server).markParticipant(owner, stage,
                participant.providerId(), participant.sessionId(), participant.participant());
        }
        return new AcquireResult(true, granted, owner);
    }

    private void repairLease(ServerPlayer player, RuntimeSession runtime,
                             ParticipantState participant) {
        if (runtime.spec.inProgressStage().isEmpty()) return;
        StageId stage = runtime.spec.inProgressStage().get();
        UUID expectedOwner = StageManager.getInstance().getStorageOwner(player, stage);
        AcquireResult acquired = acquireLease(player, runtime);
        if (!acquired.acquired()) return;
        if (participant.leaseOwner != null && !participant.leaseOwner.equals(expectedOwner)) {
            releaseLease(player, runtime, participant.leaseOwner);
        }
        participant.leaseOwner = acquired.owner();
    }

    private boolean releaseLease(ServerPlayer player, RuntimeSession runtime, UUID owner) {
        Optional<StageId> stageOptional = runtime.spec.inProgressStage();
        if (stageOptional.isEmpty() || owner == null) return false;
        StageId stage = stageOptional.get();
        LeaseKey key = new LeaseKey(owner, stage);
        LeaseBucket bucket = leases.get(key);
        if (bucket == null) return false;
        ParticipantLease participant = new ParticipantLease(runtime.spec.providerId(),
            runtime.spec.sessionId(), player.getUUID());
        bucket.release(participant);
        if (bucket.introduced() && server != null) {
            StructureLeaseData.get(server).clearParticipant(owner, stage,
                participant.providerId(), participant.sessionId(), participant.participant());
        }
        if (bucket.participantCount() > 0) return false;
        if (bucket.introduced() && server != null
                && StructureLeaseData.get(server).participantCount(owner, stage) > 0) return false;
        leases.remove(key);
        if (!bucket.shouldRevoke()) return false;
        boolean revoked = StageManager.getInstance().revokeTemporaryStage(
            player, owner, stage, StageCause.STRUCTURE_LEAVE);
        if (server != null) StructureLeaseData.get(server).clear(key.owner(), stage);
        return revoked;
    }

    private void recoverPersistedParticipants(ServerPlayer player) {
        if (server == null) return;
        StructureLeaseData data = StructureLeaseData.get(server);
        for (StructureLeaseData.PersistedParticipant persisted
                : data.participantsFor(player.getUUID())) {
            SessionKey sessionKey = new SessionKey(persisted.providerId(), persisted.sessionId());
            RuntimeSession runtime = sessions.get(sessionKey);
            ParticipantState state = runtime == null ? null
                : runtime.participants.get(player.getUUID());
            boolean active = state != null
                && runtime.spec.inProgressStage().filter(persisted.stage()::equals).isPresent();
            if (active && persisted.owner().equals(state.leaseOwner)) continue;
            data.clearParticipant(persisted.owner(), persisted.stage(), persisted.providerId(),
                persisted.sessionId(), persisted.participantId());
            if (data.participantCount(persisted.owner(), persisted.stage()) > 0) continue;
            if (StageManager.getInstance().hasStage(persisted.owner(), persisted.stage())) {
                StageManager.getInstance().revokeTemporaryStage(player, persisted.owner(),
                    persisted.stage(), StageCause.STRUCTURE_LEAVE);
            }
            leases.remove(new LeaseKey(persisted.owner(), persisted.stage()));
            data.clear(persisted.owner(), persisted.stage());
        }
    }

    private void recoverOrphanIntroducedStages(ServerPlayer player,
                                                Map<SessionKey, StructureSessionSpec> accepted) {
        if (server == null) return;
        StructureLeaseData data = StructureLeaseData.get(server);
        Set<UUID> owners = new LinkedHashSet<>();
        owners.add(TeamProvider.getInstance().getTeamId(player));
        owners.add(StageManager.SERVER_TEAM);
        for (UUID owner : owners) {
            for (StageId stage : data.stagesFor(owner)) {
                LeaseKey key = new LeaseKey(owner, stage);
                LeaseBucket bucket = leases.get(key);
                if (bucket != null && bucket.participantCount() > 0) continue;
                if (data.participantCount(owner, stage) > 0) continue;
                boolean expected = accepted.values().stream()
                    .filter(spec -> spec.inProgressStage().filter(stage::equals).isPresent())
                    .filter(spec -> spec.instance().dimension().equals(player.level().dimension()))
                    .filter(spec -> StageManager.getInstance().getStorageOwner(player, stage).equals(owner))
                    .anyMatch(spec -> spec.bounds().contains(player.blockPosition()));
                if (expected) continue;
                if (ProgressiveStagesAPI.hasStage(player, stage)) {
                    StageManager.getInstance().revokeTemporaryStage(
                        player, stage, StageCause.STRUCTURE_LEAVE);
                }
                leases.remove(key);
                data.clear(owner, stage);
            }
        }
    }

    private boolean revokeCompletedAccess(ServerPlayer player, RuntimeSession runtime) {
        if (runtime.accessRevoked
                || runtime.spec.cleanupPolicy() != StructureCleanupPolicy.REVOKE_ACCESS_ON_COMPLETED_EXIT) {
            return false;
        }
        runtime.accessRevoked = true;
        return ProgressiveStagesAPI.revokeStage(player, runtime.spec.accessStage(),
            StageCause.STRUCTURE_COMPLETE);
    }

    private boolean isValidFor(ServerPlayer player, StructureSessionSpec spec) {
        if (spec.availability() != com.enviouse.progressivestages.common.api.structure.StructureSessionAvailability.AVAILABLE) {
            return false;
        }
        if (!StageOrder.getInstance().stageExists(spec.accessStage())) {
            warnOnce(spec.sessionId() + ".missing_access",
                "Structure session {} references missing access stage {}",
                spec.sessionId(), spec.accessStage());
            return false;
        }
        if (spec.inProgressStage().isPresent()
                && !StageOrder.getInstance().stageExists(spec.inProgressStage().get())) {
            warnOnce(spec.sessionId() + ".missing_progress",
                "Structure session {} references missing in progress stage {}",
                spec.sessionId(), spec.inProgressStage().get());
            return false;
        }
        if (spec.inProgressStage().filter(spec.accessStage()::equals).isPresent()) {
            warnOnce(spec.sessionId() + ".same_stage",
                "Structure session {} uses the same access and in progress stage {}",
                spec.sessionId(), spec.accessStage());
            return false;
        }
        if (!effectiveOwner(spec, player).equals(spec.assignmentOwner())) {
            warnOnce(spec.sessionId() + ".owner." + player.getUUID(),
                "Structure session {} owner {} does not match player or team owner {}",
                spec.sessionId(), spec.assignmentOwner(), effectiveOwner(spec, player));
            return false;
        }
        if (spec.ownershipScope() == StructureOwnershipScope.PLAYER
                && TeamProvider.getInstance().isFtbTeamsActive()) {
            warnOnce(spec.sessionId() + ".player_scope_team_stage",
                "Structure session {} is player owned while stages use team storage",
                spec.sessionId());
        }
        spec.inProgressStage().flatMap(StageOrder.getInstance()::getStageDefinition).ifPresent(definition -> {
            if (definition.isPurchasable() || !definition.getDependencies().isEmpty()
                    || !definition.getRewards().isEmpty() || definition.getRevoke().cascade()
                    || definition.hasTriggers()) {
                warnOnce(spec.sessionId() + ".unsafe_progress_stage",
                    "Structure session {} in progress stage {} has permanent progression behavior",
                    spec.sessionId(), definition.getId());
            }
        });
        return true;
    }

    private UUID effectiveOwner(StructureSessionSpec spec, ServerPlayer player) {
        return spec.ownershipScope() == StructureOwnershipScope.PLAYER
            ? player.getUUID() : TeamProvider.getInstance().getTeamId(player);
    }

    private List<StructureSessionView> viewsFor(UUID playerId) {
        List<StructureSessionView> result = new ArrayList<>();
        for (RuntimeSession runtime : sessions.values()) {
            if (runtime.participants.containsKey(playerId)
                    || playerSessions.getOrDefault(playerId, Map.of()).containsKey(
                        new SessionKey(runtime.spec.providerId(), runtime.spec.sessionId()))) {
                result.add(view(runtime));
            }
        }
        return List.copyOf(result);
    }

    private StructureSessionView view(RuntimeSession runtime) {
        return view(runtime, runtime.visitSequence);
    }

    private StructureSessionView view(RuntimeSession runtime, long visitSequence) {
        boolean pending = runtime.participants.values().stream().anyMatch(state -> state.exitDeadline >= 0L);
        StructureSessionSpec spec = runtime.spec;
        return new StructureSessionView(spec.providerId(), spec.sessionId(), spec.instance(), spec.bounds(),
            spec.assignmentOwner(), spec.ownershipScope(), spec.accessStage(), spec.inProgressStage(),
            runtime.complete, spec.cleanupPolicy(), spec.availability(), visitSequence,
            runtime.participants.keySet(), pending);
    }

    private void requireThread() {
        if (server != null && !server.isSameThread()) {
            throw new IllegalStateException("Structure session API must run on the server thread");
        }
    }

    private static long distanceSquared(BlockPos first, BlockPos second) {
        long dx = (long) first.getX() - second.getX();
        long dy = (long) first.getY() - second.getY();
        long dz = (long) first.getZ() - second.getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    private void warnOnce(String key, String message, Object... arguments) {
        if (validationWarnings.add(key)) LOGGER.warn(message, arguments);
    }
}
