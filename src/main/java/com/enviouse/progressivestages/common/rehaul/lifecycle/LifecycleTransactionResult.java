package com.enviouse.progressivestages.common.rehaul.lifecycle;

import com.enviouse.progressivestages.common.api.StageId;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;

public record LifecycleTransactionResult(boolean committed, boolean rolledBack, String code,
                                         List<ResourceLocation> appliedRules,
                                         Map<StageId, LifecycleDirection> transitions,
                                         String explanation) {

    public LifecycleTransactionResult {
        code = code == null ? "" : code;
        appliedRules = appliedRules == null ? List.of() : List.copyOf(appliedRules);
        transitions = transitions == null ? Map.of() : Map.copyOf(transitions);
        explanation = explanation == null ? "" : explanation;
    }
}
