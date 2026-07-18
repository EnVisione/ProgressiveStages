package com.enviouse.progressivestages.common.rehaul.modifier;

import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.rehaul.ConditionNode;
import com.enviouse.progressivestages.common.rehaul.ConfigProvenance;
import com.enviouse.progressivestages.common.rehaul.SelectorSpec;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Objects;
import java.util.Set;

public record CompiledDropModifier(
        ResourceLocation id,
        StageId ownerStage,
        List<SelectorSpec> blocks,
        List<SelectorSpec> drops,
        List<SelectorSpec> tools,
        Set<StageId> requiredStages,
        Set<StageId> missingStages,
        ConditionNode condition,
        ResourceLocation requiredEnchantment,
        int minimumEnchantmentLevel,
        double add,
        double multiply,
        int minimum,
        int maximum,
        int priority,
        boolean exclusive,
        ConfigProvenance provenance) {

    public CompiledDropModifier {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(ownerStage, "ownerStage");
        blocks = blocks == null ? List.of() : List.copyOf(blocks);
        drops = drops == null ? List.of() : List.copyOf(drops);
        tools = tools == null ? List.of() : List.copyOf(tools);
        requiredStages = requiredStages == null ? Set.of(ownerStage) : Set.copyOf(requiredStages);
        missingStages = missingStages == null ? Set.of() : Set.copyOf(missingStages);
        condition = condition == null ? new ConditionNode.Constant(true) : condition;
        minimumEnchantmentLevel = Math.max(0, minimumEnchantmentLevel);
        if (!Double.isFinite(add) || !Double.isFinite(multiply) || multiply < 0) {
            throw new IllegalArgumentException("Drop modifier contains an invalid number");
        }
        minimum = Math.max(0, minimum);
        maximum = Math.max(minimum, maximum);
        if (blocks.isEmpty()) throw new IllegalArgumentException("Drop modifier requires at least one block selector");
        if (drops.isEmpty()) throw new IllegalArgumentException("Drop modifier requires at least one drop selector");
    }

    public int apply(int count) {
        double changed = (count + add) * multiply;
        if (!Double.isFinite(changed)) changed = maximum;
        return Math.max(minimum, Math.min(maximum, (int) Math.floor(changed + 0.0000001D)));
    }
}
