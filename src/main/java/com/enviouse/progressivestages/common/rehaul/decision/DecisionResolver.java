package com.enviouse.progressivestages.common.rehaul.decision;

import com.enviouse.progressivestages.common.rehaul.RuleEffect;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class DecisionResolver {

    private DecisionResolver() {}

    public static DecisionTrace resolve(ResourceLocation target, String category, String action,
                                        List<DecisionCandidate> candidates, TiePolicy tiePolicy) {
        TiePolicy policy = tiePolicy == null ? TiePolicy.SAFE : tiePolicy;
        List<DecisionCandidate> matching = candidates == null ? List.of() : candidates.stream()
            .filter(DecisionCandidate::matches).toList();
        Map<ResourceLocation, DecisionCandidate> byId = new HashMap<>();
        matching.forEach(candidate -> byId.put(candidate.rule().id(), candidate));
        Set<ResourceLocation> suppressed = new HashSet<>();
        Map<ResourceLocation, String> suppressionReasons = new HashMap<>();

        for (DecisionCandidate exclusion : matching) {
            if (exclusion.rule().effect() != RuleEffect.EXCLUDE) continue;
            ResourceLocation parentId = exclusion.rule().parentRuleId();
            DecisionCandidate parent = byId.get(parentId);
            if (parent == null) {
                suppressionReasons.put(exclusion.rule().id(), "The referenced parent lock did not match");
                continue;
            }
            if (exclusion.resolvedPriority().value() >= parent.resolvedPriority().value()) {
                suppressed.add(parentId);
                suppressionReasons.put(parentId, "A parent linked exclusion removed this lock");
            } else {
                suppressionReasons.put(exclusion.rule().id(), "The exclusion priority is lower than its parent lock");
            }
        }

        List<DecisionCandidate> competing = matching.stream()
            .filter(candidate -> candidate.rule().effect() != RuleEffect.EXCLUDE)
            .filter(candidate -> !suppressed.contains(candidate.rule().id()))
            .toList();
        DecisionCandidate winner = choose(competing, policy);
        List<CandidateTrace> traces = new ArrayList<>();
        for (DecisionCandidate candidate : candidates == null ? List.<DecisionCandidate>of() : candidates) {
            boolean isSuppressed = suppressed.contains(candidate.rule().id());
            boolean selected = winner != null && candidate.rule().id().equals(winner.rule().id());
            String explanation = suppressionReasons.getOrDefault(candidate.rule().id(),
                selected ? "This candidate won the priority decision" : candidate.selectorMatch().explanation());
            traces.add(new CandidateTrace(candidate.rule().id(), candidate.rule().effect(),
                candidate.resolvedPriority().value(), candidate.resolvedPriority().source(),
                candidate.selectorMatch().specificity(), candidate.selectorMatch().matched(),
                candidate.conditionMatched(), isSuppressed, selected, explanation));
        }
        traces.sort(Comparator.comparingInt(CandidateTrace::priority).reversed()
            .thenComparing(trace -> trace.ruleId().toString()));
        return new DecisionTrace(target, category, action, policy,
            winner == null ? null : winner.rule().effect(), winner == null ? null : winner.rule().id(),
            traces, winner == null ? "No active rule matched" : "The highest priority active rule won");
    }

    private static DecisionCandidate choose(List<DecisionCandidate> candidates, TiePolicy policy) {
        if (candidates.isEmpty()) return null;
        int highest = candidates.stream().mapToInt(candidate -> candidate.resolvedPriority().value()).max().orElse(0);
        List<DecisionCandidate> tied = candidates.stream()
            .filter(candidate -> candidate.resolvedPriority().value() == highest).toList();
        if (tied.size() == 1) return tied.getFirst();
        if (policy == TiePolicy.ERROR_ON_TIE) {
            throw new IllegalStateException("Equal priority decision conflict. "
                + tied.stream().map(candidate -> candidate.rule().id().toString()).sorted().toList());
        }
        Comparator<DecisionCandidate> stable = Comparator
            .comparingInt(DecisionCandidate::declarationOrder)
            .thenComparing(candidate -> candidate.rule().id().toString());
        return switch (policy) {
            case FIRST_DECLARED -> tied.stream().min(stable).orElseThrow();
            case MOST_SPECIFIC -> tied.stream().max(Comparator
                .comparingInt((DecisionCandidate candidate) -> candidate.selectorMatch().specificity())
                .thenComparing(candidate -> candidate.rule().selector().explicitPriority() != null)
                .thenComparing(candidate -> candidate.rule().id().toString(), Comparator.reverseOrder())).orElseThrow();
            case UNLOCK_WINS -> tied.stream().sorted(Comparator
                .comparingInt((DecisionCandidate candidate) -> allowRank(candidate.rule().effect())).reversed()
                .thenComparing(stable)).findFirst().orElseThrow();
            case LOCK_WINS -> tied.stream().sorted(Comparator
                .comparingInt((DecisionCandidate candidate) -> denyRank(candidate.rule().effect())).reversed()
                .thenComparing(stable)).findFirst().orElseThrow();
            case SAFE -> tied.stream().sorted(Comparator
                .comparingInt((DecisionCandidate candidate) -> denyRank(candidate.rule().effect())).reversed()
                .thenComparing(Comparator.comparingInt(
                    (DecisionCandidate candidate) -> candidate.selectorMatch().specificity()).reversed())
                .thenComparing(Comparator.comparing(
                    (DecisionCandidate candidate) -> candidate.rule().selector().explicitPriority() != null).reversed())
                .thenComparing(candidate -> candidate.rule().id().toString())).findFirst().orElseThrow();
            case ERROR_ON_TIE -> throw new IllegalStateException("Unreachable tie policy");
        };
    }

    private static int denyRank(RuleEffect effect) {
        return effect == RuleEffect.LOCK || effect == RuleEffect.DENY ? 1 : 0;
    }

    private static int allowRank(RuleEffect effect) {
        return effect == RuleEffect.ALLOW || effect == RuleEffect.UNLOCK ? 1 : 0;
    }
}
