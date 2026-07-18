package com.enviouse.progressivestages.common.api.structure;

import java.util.Objects;
import java.util.UUID;

public record StructureSessionId(UUID value) implements Comparable<StructureSessionId> {
    public StructureSessionId {
        Objects.requireNonNull(value, "value");
    }

    public static StructureSessionId random() {
        return new StructureSessionId(UUID.randomUUID());
    }

    public static StructureSessionId parse(String value) {
        return new StructureSessionId(UUID.fromString(value));
    }

    @Override
    public int compareTo(StructureSessionId other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
