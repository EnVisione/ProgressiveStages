package com.enviouse.progressivestages.common.rehaul.condition;

import java.util.Map;
import java.util.LinkedHashMap;
import java.util.concurrent.ConcurrentHashMap;

public final class ConditionStateStore {

    private final Map<String, SequenceState> sequences = new ConcurrentHashMap<>();

    SequenceState sequence(String subject, String path) {
        return sequences.computeIfAbsent(subject + "|" + path, ignored -> new SequenceState());
    }

    public void clearSubject(String subject) {
        String prefix = subject + "|";
        sequences.keySet().removeIf(key -> key.startsWith(prefix));
    }

    public void clear() {
        sequences.clear();
    }

    public Map<String, SequenceSnapshot> snapshot() {
        Map<String, SequenceSnapshot> result = new LinkedHashMap<>();
        sequences.forEach((key, state) -> result.put(key,
            new SequenceSnapshot(state.index, state.startedAt)));
        return Map.copyOf(result);
    }

    public void restore(Map<String, SequenceSnapshot> snapshot) {
        sequences.clear();
        if (snapshot == null) return;
        snapshot.forEach((key, value) -> {
            SequenceState state = new SequenceState();
            state.index = Math.max(0, value.index());
            state.startedAt = Math.max(0, value.startedAt());
            sequences.put(key, state);
        });
    }

    public record SequenceSnapshot(int index, long startedAt) {}

    static final class SequenceState {
        int index;
        long startedAt;

        void reset() {
            index = 0;
            startedAt = 0;
        }
    }
}
