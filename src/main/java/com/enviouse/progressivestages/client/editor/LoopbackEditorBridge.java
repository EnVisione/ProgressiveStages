package com.enviouse.progressivestages.client.editor;

import com.enviouse.progressivestages.common.network.NetworkHandler;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;

public final class LoopbackEditorBridge {
    private static final int MAX_BODY = 1024 * 1024;
    private static volatile LoopbackEditorBridge active;

    private final NetworkHandler.EditorOpenPayload open;
    private final HttpServer server;
    private final String origin;
    private final ArrayDeque<Long> requests = new ArrayDeque<>();

    private LoopbackEditorBridge(NetworkHandler.EditorOpenPayload open) throws IOException {
        this.open = open;
        server = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
        origin = "http://127.0.0.1:" + server.getAddress().getPort();
        server.createContext("/", this::handle);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.start();
    }

    public static synchronized void open(NetworkHandler.EditorOpenPayload payload) {
        closeActive();
        try {
            active = new LoopbackEditorBridge(payload);
            String url = active.origin + "/#" + payload.secret();
            try { Util.getPlatform().openUri(URI.create(url)); }
            catch (RuntimeException failure) {
                Minecraft minecraft = Minecraft.getInstance();
                if (minecraft.player != null) minecraft.player.displayClientMessage(Component.literal("ProgressiveStages editor. " + url), false);
            }
        } catch (IOException failure) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player != null) minecraft.player.displayClientMessage(Component.literal("Could not start the local ProgressiveStages editor. " + failure.getMessage()), false);
        }
    }

    public static synchronized void closeActive() {
        LoopbackEditorBridge current = active;
        active = null;
        if (current != null) current.server.stop(0);
        EditorBridgeTransport.clear();
    }

    private void handle(HttpExchange exchange) throws IOException {
        securityHeaders(exchange.getResponseHeaders());
        try {
            String path = exchange.getRequestURI().getPath();
            if (path.equals("/") || path.equals("/index.html")) { staticAsset(exchange, "index.html", "text/html; charset=utf-8"); return; }
            if (path.equals("/app.css")) { staticAsset(exchange, "app.css", "text/css; charset=utf-8"); return; }
            if (path.equals("/app.js")) { staticAsset(exchange, "app.js", "text/javascript; charset=utf-8"); return; }
            if (path.equals("/legacy.js")) { staticAsset(exchange, "legacy.js", "text/javascript; charset=utf-8"); return; }
            if (path.equals("/api/request")) { api(exchange); return; }
            send(exchange, 404, "Not found", "text/plain; charset=utf-8");
        } catch (RuntimeException failure) {
            send(exchange, 500, "{\"error\":\"bridge_error\"}", "application/json; charset=utf-8");
        } finally {
            exchange.close();
        }
    }

    private void api(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equals("POST")) { send(exchange, 405, "{\"error\":\"method\"}", "application/json"); return; }
        String host = exchange.getRequestHeaders().getFirst("Host");
        String expectedHost = "127.0.0.1:" + server.getAddress().getPort();
        String requestOrigin = exchange.getRequestHeaders().getFirst("Origin");
        String authorization = exchange.getRequestHeaders().getFirst("Authorization");
        if (!expectedHost.equals(host) || !origin.equals(requestOrigin)
                || !MessageSecrets.matches(authorization, open.secret())) {
            send(exchange, 403, "{\"error\":\"forbidden\"}", "application/json");
            return;
        }
        if (!allowRequest()) { send(exchange, 429, "{\"error\":\"rate_limited\"}", "application/json"); return; }
        int declared = parseLength(exchange.getRequestHeaders().getFirst("Content-Length"));
        if (declared > MAX_BODY) { send(exchange, 413, "{\"error\":\"too_large\"}", "application/json"); return; }
        byte[] body = readBounded(exchange.getRequestBody());
        try {
            String response = EditorBridgeTransport.request(open.sessionId(), open.secret(),
                new String(body, StandardCharsets.UTF_8)).join();
            send(exchange, 200, response, "application/json; charset=utf-8");
        } catch (RuntimeException failure) {
            send(exchange, 504, "{\"error\":\"server_timeout\"}", "application/json; charset=utf-8");
        }
    }

    private synchronized boolean allowRequest() {
        long now = System.currentTimeMillis();
        while (!requests.isEmpty() && requests.getFirst() < now - 60_000) requests.removeFirst();
        if (requests.size() >= 120) return false;
        requests.addLast(now);
        return true;
    }

    private void staticAsset(HttpExchange exchange, String name, String type) throws IOException {
        if (!exchange.getRequestMethod().equals("GET")) { send(exchange, 405, "Method not allowed", "text/plain"); return; }
        try (InputStream input = LoopbackEditorBridge.class.getResourceAsStream("/assets/progressivestages/editor/" + name)) {
            if (input == null) { send(exchange, 404, "Missing editor asset", "text/plain"); return; }
            send(exchange, 200, new String(input.readAllBytes(), StandardCharsets.UTF_8), type);
        }
    }

    private byte[] readBounded(InputStream input) throws IOException {
        byte[] value = input.readNBytes(MAX_BODY + 1);
        if (value.length > MAX_BODY) throw new IOException("Request body exceeds the editor limit");
        return value;
    }

    private static int parseLength(String value) {
        try { return value == null ? 0 : Integer.parseInt(value); }
        catch (NumberFormatException ignored) { return 0; }
    }

    private static void securityHeaders(Headers headers) {
        headers.set("Content-Security-Policy", "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; connect-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'");
        headers.set("X-Frame-Options", "DENY");
        headers.set("X-Content-Type-Options", "nosniff");
        headers.set("Referrer-Policy", "no-referrer");
        headers.set("Cache-Control", "no-store");
        headers.set("Permissions-Policy", "camera=(), microphone=(), geolocation=()");
    }

    private static void send(HttpExchange exchange, int status, String body, String contentType) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    private static final class MessageSecrets {
        static boolean matches(String authorization, String secret) {
            if (authorization == null || !authorization.startsWith("Bearer ")) return false;
            return java.security.MessageDigest.isEqual(authorization.substring(7).getBytes(StandardCharsets.UTF_8),
                secret.getBytes(StandardCharsets.UTF_8));
        }
    }
}
