package com.enviouse.progressivestages.server.structure;

import com.enviouse.progressivestages.common.api.structure.StructureAccessDecision;
import com.enviouse.progressivestages.common.api.structure.StructureAccessRequest;
import com.enviouse.progressivestages.common.api.structure.StructureContextProvider;
import com.enviouse.progressivestages.common.api.structure.StructureSessionId;
import com.enviouse.progressivestages.common.api.structure.StructureSessionSpec;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class StructureContextRegistry {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final StructureContextRegistry INSTANCE = new StructureContextRegistry();
    private static final long ERROR_LOG_INTERVAL = 30_000L;

    private final Map<ResourceLocation, StructureContextProvider> providers = new LinkedHashMap<>();
    private final Map<ResourceLocation, Map<java.util.UUID, List<StructureSessionSpec>>> knownSessions =
        new ConcurrentHashMap<>();
    private final Map<ResourceLocation, Long> lastErrorLog = new ConcurrentHashMap<>();
    private final Set<String> validationWarnings = ConcurrentHashMap.newKeySet();
    private MinecraftServer server;

    public record Evaluation(ResourceLocation providerId, StructureAccessDecision decision) {
        public static Evaluation pass() {
            return new Evaluation(null, StructureAccessDecision.pass());
        }
    }

    public record ProviderStatus(ResourceLocation id, String implementation, int cachedSessions) {}

    public static StructureContextRegistry getInstance() {
        return INSTANCE;
    }

    private StructureContextRegistry() {}

    public void bind(MinecraftServer server) {
        this.server = Objects.requireNonNull(server, "server");
        knownSessions.clear();
        lastErrorLog.clear();
        validationWarnings.clear();
    }

    public void shutdown(MinecraftServer server) {
        if (this.server == server) this.server = null;
        providers.clear();
        knownSessions.clear();
        lastErrorLog.clear();
        validationWarnings.clear();
    }

    public void register(ResourceLocation id, StructureContextProvider provider) {
        requireServerThread();
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(provider, "provider");
        if (providers.containsKey(id)) {
            throw new IllegalArgumentException("Duplicate structure context provider. " + id);
        }
        providers.put(id, provider);
        if (server != null) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                StructureSessionManager.getInstance().reconcile(player, true);
            }
        }
    }

    public boolean unregister(ResourceLocation id) {
        requireServerThread();
        StructureContextProvider removed = providers.remove(id);
        knownSessions.remove(id);
        if (removed != null && server != null) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                StructureSessionManager.getInstance().reconcile(player, true);
            }
        }
        return removed != null;
    }

    public List<ProviderStatus> statuses() {
        requireServerThread();
        List<ProviderStatus> result = new ArrayList<>();
        providers.forEach((id, provider) -> result.add(new ProviderStatus(id,
            provider.getClass().getName(), cachedSessionCount(id))));
        return List.copyOf(result);
    }

    public Evaluation evaluate(StructureAccessRequest request) {
        requireServerThread();
        Evaluation permit = Evaluation.pass();
        for (Map.Entry<ResourceLocation, StructureContextProvider> entry : providers.entrySet()) {
            ResourceLocation id = entry.getKey();
            try {
                StructureAccessDecision decision = entry.getValue().evaluate(request);
                if (decision == null || decision.result() == StructureAccessDecision.Result.PASS) continue;
                if (decision.result() == StructureAccessDecision.Result.DENY) {
                    return new Evaluation(id, decision);
                }
                if (permit.decision().result() == StructureAccessDecision.Result.PASS) {
                    permit = new Evaluation(id, decision);
                }
            } catch (RuntimeException error) {
                logProviderError(id, "access evaluation", error);
                Optional<StructureSessionSpec> claim = knownFor(id, request.player()).stream()
                    .filter(spec -> spec.instance().dimension().equals(request.level().dimension()))
                    .filter(spec -> spec.bounds().contains(request.position()))
                    .findFirst();
                if (claim.isPresent()) {
                    StructureSessionSpec spec = claim.get();
                    return new Evaluation(id, StructureAccessDecision.deny(
                        StructureAccessDecision.Reason.PROVIDER_ERROR, spec.accessStage(),
                        spec.sessionId(), spec.bounds()));
                }
            }
        }
        return permit;
    }

    public List<StructureSessionSpec> sessionsFor(ServerPlayer player) {
        requireServerThread();
        List<StructureSessionSpec> result = new ArrayList<>();
        for (Map.Entry<ResourceLocation, StructureContextProvider> entry : providers.entrySet()) {
            try {
                Collection<StructureSessionSpec> supplied = entry.getValue().sessionsFor(player);
                List<StructureSessionSpec> valid = new ArrayList<>();
                Set<StructureSessionId> seen = new java.util.LinkedHashSet<>();
                if (supplied != null) {
                    for (StructureSessionSpec spec : supplied) {
                        if (spec == null) continue;
                        if (!entry.getKey().equals(spec.providerId())) {
                            warnOnce(entry.getKey() + ".wrong_provider",
                                "Structure provider {} returned a session owned by {}",
                                entry.getKey(), spec.providerId());
                            continue;
                        }
                        if (!seen.add(spec.sessionId())) {
                            warnOnce(entry.getKey() + ".duplicate." + spec.sessionId(),
                                "Structure provider {} returned duplicate session {}",
                                entry.getKey(), spec.sessionId());
                            continue;
                        }
                        valid.add(spec);
                    }
                }
                knownSessions.computeIfAbsent(entry.getKey(), ignored -> new ConcurrentHashMap<>())
                    .put(player.getUUID(), List.copyOf(valid));
                result.addAll(valid);
            } catch (RuntimeException error) {
                logProviderError(entry.getKey(), "session listing", error);
                result.addAll(knownFor(entry.getKey(), player));
            }
        }
        return List.copyOf(result);
    }

    public Optional<StructureSessionSpec> session(ResourceLocation providerId, StructureSessionId sessionId) {
        requireServerThread();
        StructureContextProvider provider = providers.get(providerId);
        if (provider == null) return Optional.empty();
        try {
            Optional<StructureSessionSpec> supplied = provider.session(sessionId);
            return supplied.filter(spec -> providerId.equals(spec.providerId())
                && sessionId.equals(spec.sessionId()));
        } catch (RuntimeException error) {
            logProviderError(providerId, "session lookup", error);
            return knownSessions.getOrDefault(providerId, Map.of()).values().stream()
                .flatMap(Collection::stream)
                .filter(spec -> spec.sessionId().equals(sessionId)).findFirst();
        }
    }

    public boolean hasProviders() {
        return !providers.isEmpty();
    }

    public Optional<StructureSessionSpec> knownSession(ServerPlayer player,
                                                       ResourceLocation providerId,
                                                       StructureSessionId sessionId) {
        return knownFor(providerId, player).stream()
            .filter(spec -> spec.sessionId().equals(sessionId)).findFirst();
    }

    public void requireServerThread() {
        if (server != null && !server.isSameThread()) {
            throw new IllegalStateException("Structure context API must run on the server thread");
        }
    }

    private void logProviderError(ResourceLocation id, String operation, RuntimeException error) {
        long now = System.currentTimeMillis();
        long previous = lastErrorLog.getOrDefault(id, 0L);
        if (now - previous >= ERROR_LOG_INTERVAL) {
            lastErrorLog.put(id, now);
            LOGGER.error("Structure context provider {} failed during {}", id, operation, error);
        }
    }

    private List<StructureSessionSpec> knownFor(ResourceLocation providerId, ServerPlayer player) {
        return knownSessions.getOrDefault(providerId, Map.of())
            .getOrDefault(player.getUUID(), List.of());
    }

    private int cachedSessionCount(ResourceLocation providerId) {
        return (int) knownSessions.getOrDefault(providerId, Map.of()).values().stream()
            .flatMap(Collection::stream).map(StructureSessionSpec::sessionId).distinct().count();
    }

    private void warnOnce(String key, String message, Object... arguments) {
        if (validationWarnings.add(key)) LOGGER.warn(message, arguments);
    }
}
