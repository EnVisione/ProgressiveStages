package com.enviouse.progressivestages.common.rehaul.decision;

import com.enviouse.progressivestages.common.rehaul.RuleEffect;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Optional;

public record DecisionTrace(ResourceLocation target, String category, String action, TiePolicy tiePolicy,
                            RuleEffect winningEffect, ResourceLocation winningRule,
                            List<CandidateTrace> candidates, String explanation) {

    public DecisionTrace {
        category = category == null ? "" : category;
        action = action == null ? "" : action;
        tiePolicy = tiePolicy == null ? TiePolicy.SAFE : tiePolicy;
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
        explanation = explanation == null ? "" : explanation;
    }

    public Optional<ResourceLocation> winner() {
        return Optional.ofNullable(winningRule);
    }

    public boolean blocked() {
        return winningEffect == RuleEffect.LOCK || winningEffect == RuleEffect.DENY;
    }
}
