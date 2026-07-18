package com.enviouse.progressivestages.common.rehaul.state;

import com.enviouse.progressivestages.common.api.StageId;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class StageStateEngine {

    private volatile Map<StageId, StageStateDefinition> definitions = Map.of();
    private final Map<Key, String> states = new ConcurrentHashMap<>();

    public synchronized void rebuild(java.util.Collection<StageStateDefinition> values) {
        Map<StageId, StageStateDefinition> next = new LinkedHashMap<>();
        for (StageStateDefinition definition : values == null ? java.util.List.<StageStateDefinition>of() : values) {
            if (next.putIfAbsent(definition.stage(), definition) != null) throw new IllegalArgumentException("Duplicate stage state definition. " + definition.stage());
        }
        definitions = Map.copyOf(next);
        states.keySet().removeIf(key -> !definitions.containsKey(key.stage()));
    }

    public String get(String subject, StageId stage) {
        StageStateDefinition definition = definitions.get(stage);
        return states.getOrDefault(new Key(subject, stage), definition == null ? "missing" : definition.initialState());
    }

    public boolean transition(String subject, StageId stage, String target) {
        StageStateDefinition definition = definitions.get(stage);
        if (definition == null || !definition.states().contains(target)) return false;
        String current = get(subject, stage);
        if (!definition.transitions().getOrDefault(current, java.util.Set.of()).contains(target)) return false;
        states.put(new Key(subject, stage), target);
        return true;
    }

    public boolean countsAsOwned(String subject, StageId stage) {
        StageStateDefinition definition = definitions.get(stage);
        return definition != null && definition.ownershipStates().contains(get(subject, stage));
    }

    public Map<String, String> snapshot() {
        Map<String, String> result = new LinkedHashMap<>();
        states.forEach((key, value) -> result.put(key.subject() + "|" + key.stage(), value));
        return Map.copyOf(result);
    }

    public void restore(Map<String, String> snapshot) {
        states.clear();
        if (snapshot == null) return;
        snapshot.forEach((key, value) -> {
            int separator = key.indexOf('|');
            if (separator < 1) return;
            StageId stage = StageId.tryParse(key.substring(separator + 1));
            StageStateDefinition definition = stage == null ? null : definitions.get(stage);
            if (definition != null && definition.states().contains(value)) {
                states.put(new Key(key.substring(0, separator), stage), value);
            }
        });
    }

    private record Key(String subject, StageId stage) {}
}
