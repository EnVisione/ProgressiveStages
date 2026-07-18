package com.enviouse.progressivestages.common.rehaul.condition;

import com.enviouse.progressivestages.common.rehaul.ConditionNode;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public record ConditionContext(String subjectId, SubjectScope scope, long nowMillis,
                               Map<String, Object> values, Set<String> dirtyKeys,
                               Map<String, ConditionNode> namedConditions) {

    public ConditionContext {
        subjectId = Objects.requireNonNull(subjectId, "subjectId").trim();
        if (subjectId.isEmpty()) throw new IllegalArgumentException("Subject id cannot be blank");
        scope = scope == null ? SubjectScope.PLAYER : scope;
        values = values == null ? Map.of() : Map.copyOf(values);
        dirtyKeys = dirtyKeys == null ? Set.of() : Set.copyOf(dirtyKeys);
        namedConditions = namedConditions == null ? Map.of() : Map.copyOf(namedConditions);
    }

    public Optional<Object> value(String key) {
        return Optional.ofNullable(values.get(key));
    }

    public boolean dirty(String key) {
        return dirtyKeys.isEmpty() || dirtyKeys.contains(key);
    }
}
