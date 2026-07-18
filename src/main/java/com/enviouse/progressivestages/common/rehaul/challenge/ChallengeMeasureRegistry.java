package com.enviouse.progressivestages.common.rehaul.challenge;

import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ChallengeMeasureRegistry {

    private static final ChallengeMeasureRegistry INSTANCE = new ChallengeMeasureRegistry();
    private volatile Map<ResourceLocation, ChallengeMeasureProvider> providers;

    private ChallengeMeasureRegistry() {
        Map<ResourceLocation, ChallengeMeasureProvider> initial = new LinkedHashMap<>();
        for (String name : List.of("hits_taken", "hits_dealt", "damage_taken", "damage_dealt",
                "health_gained", "health_lost", "deaths", "consumables", "item_uses",
                "blocks_broken", "time", "movement", "summons", "commands", "custom")) {
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath("progressivestages", name);
            initial.put(id, new EventMeasure(id));
        }
        providers = Map.copyOf(initial);
    }

    public static ChallengeMeasureRegistry get() { return INSTANCE; }

    public synchronized void register(ChallengeMeasureProvider provider) {
        Map<ResourceLocation, ChallengeMeasureProvider> copy = new LinkedHashMap<>(providers);
        if (copy.putIfAbsent(provider.id(), provider) != null) throw new IllegalArgumentException("Duplicate challenge measure. " + provider.id());
        providers = Map.copyOf(copy);
    }

    public Optional<ChallengeMeasureProvider> find(ResourceLocation id) { return Optional.ofNullable(providers.get(id)); }

    public Map<ResourceLocation, ChallengeMeasureProvider> providers() { return providers; }

    private record EventMeasure(ResourceLocation id) implements ChallengeMeasureProvider {
        @Override
        public double amount(ChallengeEvent event, Map<String, Object> filters) {
            if (!event.type().equals(id)) return 0;
            for (Map.Entry<String, Object> filter : filters.entrySet()) {
                Object actual = event.properties().get(filter.getKey());
                if (actual == null || !String.valueOf(actual).equalsIgnoreCase(String.valueOf(filter.getValue()))) return 0;
            }
            return event.amount();
        }
    }
}
