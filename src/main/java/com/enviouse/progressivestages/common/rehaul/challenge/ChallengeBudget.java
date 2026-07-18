package com.enviouse.progressivestages.common.rehaul.challenge;

import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.Objects;

public record ChallengeBudget(ResourceLocation id, ResourceLocation measure, BudgetMode mode,
                              double minimum, double maximum, double regenerationPerSecond,
                              String sharedPool, Map<String, Object> filters) {

    public ChallengeBudget {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(measure, "measure");
        mode = mode == null ? BudgetMode.MAXIMUM : mode;
        if (!Double.isFinite(minimum) || !Double.isFinite(maximum)
                || !Double.isFinite(regenerationPerSecond) || minimum > maximum) {
            throw new IllegalArgumentException("Challenge budget contains an invalid number");
        }
        sharedPool = sharedPool == null ? "" : sharedPool;
        filters = filters == null ? Map.of() : Map.copyOf(filters);
    }

    public boolean successful(double value) {
        return switch (mode) {
            case MINIMUM -> value >= minimum;
            case MAXIMUM -> value <= maximum;
            case RANGE -> value >= minimum && value <= maximum;
        };
    }

    public boolean failed(double value) {
        return switch (mode) {
            case MINIMUM -> false;
            case MAXIMUM, RANGE -> value > maximum;
        };
    }
}
