package com.enviouse.progressivestages.common.rehaul.state;

import com.enviouse.progressivestages.common.api.StageId;

import java.util.Map;
import java.util.Set;

public record StageStateDefinition(StageId stage, Set<String> states, Set<String> ownershipStates,
                                   String initialState, Map<String, Set<String>> transitions) {
    public StageStateDefinition {
        states = states == null || states.isEmpty() ? Set.of("missing", "owned") : Set.copyOf(states);
        ownershipStates = ownershipStates == null ? Set.of("owned", "completed") : Set.copyOf(ownershipStates);
        initialState = initialState == null ? "missing" : initialState;
        transitions = transitions == null ? Map.of() : transitions.entrySet().stream().collect(
            java.util.stream.Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> Set.copyOf(entry.getValue())));
        if (!states.contains(initialState) || !states.containsAll(ownershipStates)) {
            throw new IllegalArgumentException("Stage state definition references an unknown state");
        }
        Set<String> stateValues = states;
        transitions.forEach((from, targets) -> {
            if (!stateValues.contains(from) || !stateValues.containsAll(targets)) throw new IllegalArgumentException("Stage transition references an unknown state");
        });
    }
}
