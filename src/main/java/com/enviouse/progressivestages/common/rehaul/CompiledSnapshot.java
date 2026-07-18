package com.enviouse.progressivestages.common.rehaul;

import com.enviouse.progressivestages.common.api.StageId;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

public record CompiledSnapshot(long revision, Map<StageId, CompiledStage> stages, String checksum) {

    public static final CompiledSnapshot EMPTY = create(0L, Map.of());

    public CompiledSnapshot {
        if (revision < 0) throw new IllegalArgumentException("Revision cannot be negative");
        stages = Map.copyOf(new LinkedHashMap<>(stages));
        if (checksum == null || checksum.isBlank()) throw new IllegalArgumentException("Checksum cannot be blank");
    }

    public static CompiledSnapshot create(long revision, Map<StageId, CompiledStage> stages) {
        Map<StageId, CompiledStage> ordered = new LinkedHashMap<>();
        stages.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry ->
            ordered.put(entry.getKey(), entry.getValue()));
        return new CompiledSnapshot(revision, ordered, checksum(ordered));
    }

    private static String checksum(Map<StageId, CompiledStage> stages) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (Map.Entry<StageId, CompiledStage> entry : stages.entrySet()) {
                update(digest, entry.getKey().toString());
                CompiledStage stage = entry.getValue();
                update(digest, stage.displayName());
                update(digest, Integer.toString(stage.priority()));
                for (CompiledRule rule : stage.rules()) {
                    update(digest, rule.id().toString());
                    update(digest, rule.effect().name());
                    update(digest, rule.selector().raw());
                    update(digest, Integer.toString(rule.priority()));
                }
                for (var lifecycle : stage.progression().lifecycleRules()) {
                    update(digest, lifecycle.id().toString());
                    update(digest, lifecycle.direction().name());
                    update(digest, Integer.toString(lifecycle.priority()));
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static void update(MessageDigest digest, String value) {
        digest.update(value.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
    }
}
