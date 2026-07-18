package com.enviouse.progressivestages.common.rehaul.profile;

import com.enviouse.progressivestages.common.rehaul.selector.SelectorMatcherRegistry;
import com.enviouse.progressivestages.common.rehaul.selector.SelectorTarget;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class AffinityResolver {

    private final SelectorMatcherRegistry selectors;

    public AffinityResolver(SelectorMatcherRegistry selectors) {
        this.selectors = selectors;
    }

    public Optional<AffinityDecision> resolve(List<AffinityProfile> profiles, SelectorTarget target,
                                              Map<String, Double> values) {
        return profiles.stream().filter(profile -> profile.content().stream()
                .anyMatch(selector -> selectors.match(selector, target).matched()))
            .map(profile -> decision(profile, values.getOrDefault(profile.proficiencyValue(), 0.0)))
            .flatMap(Optional::stream)
            .max(Comparator.comparingInt(AffinityDecision::priority)
                .thenComparing(decision -> decision.profile().toString()));
    }

    private static Optional<AffinityDecision> decision(AffinityProfile profile, double value) {
        ProficiencyLevel selected = null;
        for (ProficiencyLevel level : profile.levels()) if (value >= level.minimum()) selected = level;
        return selected == null ? Optional.empty() : Optional.of(new AffinityDecision(profile.id(),
            selected.id(), selected.effect(), selected.priority(), selected.transforms()));
    }
}
