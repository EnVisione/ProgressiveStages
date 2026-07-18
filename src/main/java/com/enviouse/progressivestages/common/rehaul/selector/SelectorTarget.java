package com.enviouse.progressivestages.common.rehaul.selector;

import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record SelectorTarget(ResourceLocation id, ResourceLocation registryId,
                             Set<ResourceLocation> tags, Map<String, Object> properties) {

    public SelectorTarget {
        Objects.requireNonNull(id, "id");
        tags = tags == null ? Set.of() : Set.copyOf(tags);
        properties = properties == null ? Map.of() : Map.copyOf(properties);
    }

    public static SelectorTarget id(ResourceLocation id) {
        return new SelectorTarget(id, null, Set.of(), Map.of());
    }
}
