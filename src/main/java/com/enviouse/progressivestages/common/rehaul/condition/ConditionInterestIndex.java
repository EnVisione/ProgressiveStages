package com.enviouse.progressivestages.common.rehaul.condition;

import com.enviouse.progressivestages.common.rehaul.ConditionNode;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class ConditionInterestIndex {

    private final Map<String, Set<ResourceLocation>> byInterest;

    private ConditionInterestIndex(Map<String, Set<ResourceLocation>> byInterest) {
        Map<String, Set<ResourceLocation>> copy = new LinkedHashMap<>();
        byInterest.forEach((key, value) -> copy.put(key, Set.copyOf(value)));
        this.byInterest = Map.copyOf(copy);
    }

    public static ConditionInterestIndex build(Map<ResourceLocation, ConditionNode> conditions,
                                               ConditionRegistry registry) {
        Map<String, Set<ResourceLocation>> output = new LinkedHashMap<>();
        conditions.forEach((id, node) -> collect(node, id, registry, output));
        return new ConditionInterestIndex(output);
    }

    public Set<ResourceLocation> interested(String event) {
        return byInterest.getOrDefault(event, Set.of());
    }

    public Map<String, Set<ResourceLocation>> entries() {
        return byInterest;
    }

    private static void collect(ConditionNode node, ResourceLocation id, ConditionRegistry registry,
                                Map<String, Set<ResourceLocation>> output) {
        if (node instanceof ConditionNode.Leaf leaf) {
            registry.find(leaf.providerId()).ifPresent(provider -> provider.eventInterests().forEach(interest ->
                output.computeIfAbsent(interest, ignored -> new LinkedHashSet<>()).add(id)));
            return;
        }
        if (node instanceof ConditionNode.Not not) collect(not.child(), id, registry, output);
        else if (node instanceof ConditionNode.All all) all.children().forEach(child -> collect(child, id, registry, output));
        else if (node instanceof ConditionNode.Any any) any.children().forEach(child -> collect(child, id, registry, output));
        else if (node instanceof ConditionNode.AtLeast group) group.children().forEach(child -> collect(child, id, registry, output));
        else if (node instanceof ConditionNode.Exactly group) group.children().forEach(child -> collect(child, id, registry, output));
        else if (node instanceof ConditionNode.Sequence group) group.children().forEach(child -> collect(child, id, registry, output));
        else if (node instanceof ConditionNode.Comparison comparison) {
            output.computeIfAbsent(comparison.valueProviderId().toString(), ignored -> new LinkedHashSet<>()).add(id);
        }
    }
}
