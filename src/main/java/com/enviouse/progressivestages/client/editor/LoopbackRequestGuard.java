package com.enviouse.progressivestages.client.editor;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

final class LoopbackRequestGuard {
    private LoopbackRequestGuard() {}

    static boolean allows(String host, String origin, String authorization, String fallbackToken,
                          int port, String secret) {
        if (!isLoopbackAuthority(host, port)) return false;
        if (!isAllowedOrigin(origin, port)) return false;
        return matchesBearer(authorization, secret) || matches(fallbackToken, secret);
    }

    private static boolean isAllowedOrigin(String value, int port) {
        if (value == null || value.isBlank() || value.equalsIgnoreCase("null")) return true;
        try {
            URI origin = URI.create(value);
            return "http".equalsIgnoreCase(origin.getScheme())
                && isLoopbackHost(origin.getHost())
                && origin.getPort() == port;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private static boolean isLoopbackAuthority(String value, int port) {
        if (value == null || value.isBlank()) return false;
        try {
            URI authority = URI.create("http://" + value);
            return isLoopbackHost(authority.getHost()) && authority.getPort() == port;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private static boolean isLoopbackHost(String value) {
        if (value == null) return false;
        return value.equalsIgnoreCase("localhost")
            || value.equals("127.0.0.1")
            || value.equals("0:0:0:0:0:0:0:1")
            || value.equals("::1");
    }

    private static boolean matchesBearer(String authorization, String secret) {
        if (authorization == null || !authorization.startsWith("Bearer ")) return false;
        return matches(authorization.substring(7), secret);
    }

    private static boolean matches(String candidate, String secret) {
        if (candidate == null || secret == null) return false;
        return MessageDigest.isEqual(candidate.getBytes(StandardCharsets.UTF_8),
            secret.getBytes(StandardCharsets.UTF_8));
    }
}
