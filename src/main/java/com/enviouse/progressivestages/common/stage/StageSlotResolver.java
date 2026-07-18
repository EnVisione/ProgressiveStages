package com.enviouse.progressivestages.common.stage;

import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.config.StageDefinition;
import com.enviouse.progressivestages.common.config.StageSlotPolicy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.ToLongFunction;

public final class StageSlotResolver {

    private StageSlotResolver() {}

    public static Decision resolve(StageDefinition target, Set<StageId> owned,
                                   Function<StageId, Optional<StageDefinition>> definitions,
                                   ToLongFunction<StageId> grantTimes) {
        if (target == null || target.getSlotGroup().isBlank() || target.getSlotLimit() <= 0
                || owned.contains(target.getId())) return Decision.allowed(List.of());
        List<StageDefinition> members = owned.stream().map(definitions).flatMap(Optional::stream)
            .filter(stage -> stage.getSlotGroup().equalsIgnoreCase(target.getSlotGroup()))
            .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        int removeCount = members.size() - target.getSlotLimit() + 1;
        if (removeCount <= 0) return Decision.allowed(List.of());
        if (target.getSlotPolicy() == StageSlotPolicy.DENY) {
            return Decision.denied("Stage group " + target.getSlotGroup() + " allows "
                + target.getSlotLimit() + " active stages");
        }
        Comparator<StageDefinition> oldest = Comparator
            .comparingLong((StageDefinition stage) -> normalizedGrantTime(grantTimes.applyAsLong(stage.getId())))
            .thenComparing(stage -> stage.getId().toString());
        Comparator<StageDefinition> lowestPriority = Comparator.comparingInt(StageDefinition::getPriority)
            .thenComparing(oldest);
        members.sort(target.getSlotPolicy() == StageSlotPolicy.REPLACE_LOWEST_PRIORITY
            ? lowestPriority : oldest);
        int count = target.getSlotPolicy() == StageSlotPolicy.REPLACE_ALL
            ? members.size() : Math.min(removeCount, members.size());
        return Decision.allowed(members.stream().limit(count).map(StageDefinition::getId).toList());
    }

    private static long normalizedGrantTime(long value) {
        return value <= 0 ? Long.MIN_VALUE : value;
    }

    public record Decision(boolean allowed, List<StageId> replacements, String explanation) {
        public Decision {
            replacements = replacements == null ? List.of() : List.copyOf(replacements);
            explanation = explanation == null ? "" : explanation;
        }

        public static Decision allowed(List<StageId> replacements) {
            return new Decision(true, replacements, "");
        }

        public static Decision denied(String explanation) {
            return new Decision(false, List.of(), explanation);
        }
    }
}
