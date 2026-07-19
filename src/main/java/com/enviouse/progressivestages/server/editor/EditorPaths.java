package com.enviouse.progressivestages.server.editor;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

final class EditorPaths {
    private EditorPaths() {}

    static String normalize(String path) {
        if (path == null || path.isBlank()) throw new IllegalArgumentException("Editor path cannot be blank");
        String value = path.replace('\\', '/');
        java.nio.file.Path normalized = java.nio.file.Path.of(value).normalize();
        if (normalized.isAbsolute() || normalized.startsWith("..") || value.startsWith(".")
                || !value.toLowerCase(java.util.Locale.ROOT).endsWith(".toml")) {
            throw new IllegalArgumentException("Editor path is not a safe TOML path");
        }
        return normalized.toString().replace('\\', '/');
    }

    static boolean isMigrationPath(Path root, Path path) {
        for (Path part : root.relativize(path)) {
            if (part.toString().startsWith(".migration-")) return true;
        }
        return false;
    }

    static int bytes(String value) { return value == null ? 0 : value.getBytes(StandardCharsets.UTF_8).length; }

    static String checksum(String value) {
        if (value == null) return "";
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception impossible) { throw new IllegalStateException(impossible); }
    }
}
