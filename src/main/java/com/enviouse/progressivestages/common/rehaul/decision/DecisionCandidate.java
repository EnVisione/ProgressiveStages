package com.enviouse.progressivestages.common.rehaul.decision;

import com.enviouse.progressivestages.common.rehaul.CompiledRule;
import com.enviouse.progressivestages.common.rehaul.selector.SelectorMatch;

import java.util.Objects;

public record DecisionCandidate(CompiledRule rule, SelectorMatch selectorMatch, boolean conditionMatched,
                                int declarationOrder, ResolvedPriority resolvedPriority) {

    public DecisionCandidate {
        Objects.requireNonNull(rule, "rule");
        Objects.requireNonNull(selectorMatch, "selectorMatch");
        resolvedPriority = resolvedPriority == null
            ? new ResolvedPriority(rule.priority(), PrioritySource.RULE) : resolvedPriority;
    }

    public boolean matches() {
        return selectorMatch.matched() && conditionMatched;
    }
}
