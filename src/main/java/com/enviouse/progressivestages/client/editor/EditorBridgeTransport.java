package com.enviouse.progressivestages.client.editor;

import com.enviouse.progressivestages.common.network.NetworkHandler;
import com.google.gson.JsonParser;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class EditorBridgeTransport {
    private static final Map<UUID, CompletableFuture<String>> PENDING = new ConcurrentHashMap<>();
    private static final long DEFAULT_TIMEOUT_SECONDS = 15;
    private static final long APPLY_TIMEOUT_SECONDS = 120;

    private EditorBridgeTransport() {}

    public static CompletableFuture<String> request(UUID session, String secret, String body) {
        UUID request = UUID.randomUUID();
        CompletableFuture<String> result = new CompletableFuture<>();
        PENDING.put(request, result);
        PacketDistributor.sendToServer(new NetworkHandler.EditorRequestPayload(session, request, secret, body));
        return result.orTimeout(timeoutSeconds(body), java.util.concurrent.TimeUnit.SECONDS)
            .whenComplete((ignored, error) -> PENDING.remove(request));
    }

    private static long timeoutSeconds(String body) {
        try {
            var request = JsonParser.parseString(body).getAsJsonObject();
            return request.has("action") && request.get("action").getAsString().equals("apply")
                ? APPLY_TIMEOUT_SECONDS : DEFAULT_TIMEOUT_SECONDS;
        } catch (RuntimeException ignored) {
            return DEFAULT_TIMEOUT_SECONDS;
        }
    }

    public static void complete(UUID request, String body) {
        CompletableFuture<String> pending = PENDING.remove(request);
        if (pending != null) pending.complete(body);
    }

    public static void clear() {
        PENDING.values().forEach(future -> future.cancel(false));
        PENDING.clear();
    }
}
