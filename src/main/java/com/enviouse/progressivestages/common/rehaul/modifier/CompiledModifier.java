package com.enviouse.progressivestages.common.rehaul.modifier;

import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.rehaul.ConditionNode;
import com.enviouse.progressivestages.common.rehaul.ConfigProvenance;
import com.enviouse.progressivestages.common.rehaul.SelectorSpec;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Objects;
import java.util.Set;

public record CompiledModifier(ResourceLocation id, StageId ownerStage, List<SelectorSpec> items,
                               Set<ItemContext> contexts, Set<StageId> requiredStages,
                               Set<StageId> missingStages, ConditionNode condition,
                               AggregationMode aggregation, int cap, int priority,
                               List<AttributeChange> attributes, List<EffectChange> effects,
                               List<NumericTransform> transforms, ConfigProvenance provenance) {

    public CompiledModifier {
        Objects.requireNonNull(id, "id");
        items = items == null ? List.of() : List.copyOf(items);
        contexts = contexts == null ? Set.of() : Set.copyOf(contexts);
        requiredStages = requiredStages == null ? Set.of() : Set.copyOf(requiredStages);
        missingStages = missingStages == null ? Set.of() : Set.copyOf(missingStages);
        condition = condition == null ? new ConditionNode.Constant(true) : condition;
        aggregation = aggregation == null ? AggregationMode.ONCE : aggregation;
        if (cap < 1 || cap > 1_000_000) throw new IllegalArgumentException("Modifier cap is outside the allowed range");
        attributes = attributes == null ? List.of() : List.copyOf(attributes);
        effects = effects == null ? List.of() : List.copyOf(effects);
        transforms = transforms == null ? List.of() : List.copyOf(transforms);
        Objects.requireNonNull(provenance, "provenance");
    }
}
