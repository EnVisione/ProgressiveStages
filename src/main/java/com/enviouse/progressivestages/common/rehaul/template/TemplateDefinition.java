package com.enviouse.progressivestages.common.rehaul.template;

import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;

public record TemplateDefinition(ResourceLocation id, List<ResourceLocation> includes,
                                 Map<String, TemplateParameter> parameters,
                                 Map<String, Object> fragment, MergePolicy mergePolicy) {

    public TemplateDefinition {
        includes = includes == null ? List.of() : List.copyOf(includes);
        parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
        fragment = fragment == null ? Map.of() : Map.copyOf(fragment);
        mergePolicy = mergePolicy == null ? MergePolicy.DEEP_MERGE : mergePolicy;
    }
}
