package com.enviouse.progressivestages.common.rehaul.condition;

import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class ConditionRegistry {

    private static final ConditionRegistry INSTANCE = new ConditionRegistry();
    private volatile Map<ResourceLocation, ConditionProvider> providers;

    private ConditionRegistry() {
        Map<ResourceLocation, ConditionProvider> initial = new LinkedHashMap<>();
        for (ConditionProvider provider : BuiltinConditionProviders.all()) initial.put(provider.id(), provider);
        providers = Map.copyOf(initial);
    }

    public static ConditionRegistry get() {
        return INSTANCE;
    }

    public synchronized void register(ConditionProvider provider) {
        Map<ResourceLocation, ConditionProvider> copy = new LinkedHashMap<>(providers);
        if (copy.putIfAbsent(provider.id(), provider) != null) {
            throw new IllegalArgumentException("Duplicate condition provider. " + provider.id());
        }
        providers = Map.copyOf(copy);
    }

    public Optional<ConditionProvider> find(ResourceLocation id) {
        return Optional.ofNullable(providers.get(id));
    }

    public Map<ResourceLocation, ConditionProvider> providers() {
        return providers;
    }
}
