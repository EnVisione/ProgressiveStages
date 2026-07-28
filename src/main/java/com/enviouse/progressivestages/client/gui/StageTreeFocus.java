package com.enviouse.progressivestages.client.gui;

import com.enviouse.progressivestages.common.api.StageId;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

final class StageTreeFocus {

    private StageTreeFocus() {}

    static Set<StageId> branch(
            StageId focus,
            Collection<StageId> visibleStages,
            Function<StageId, List<StageId>> dependencies
    ) {
        Set<StageId> visible = Set.copyOf(visibleStages);
        if (focus == null || !visible.contains(focus)) return Set.of();

        Set<StageId> ancestors = new HashSet<>();
        collectAncestors(focus, visible, dependencies, ancestors);

        Set<StageId> descendants = new HashSet<>();
        descendants.add(focus);
        boolean changed;
        do {
            changed = false;
            for (StageId candidate : visible) {
                if (descendants.contains(candidate)) continue;
                for (StageId dependency : dependencies.apply(candidate)) {
                    if (descendants.contains(dependency)) {
                        changed |= descendants.add(candidate);
                        break;
                    }
                }
            }
        } while (changed);

        ancestors.addAll(descendants);
        return Set.copyOf(ancestors);
    }

    private static void collectAncestors(
            StageId stage,
            Set<StageId> visible,
            Function<StageId, List<StageId>> dependencies,
            Set<StageId> result
    ) {
        if (!visible.contains(stage) || !result.add(stage)) return;
        for (StageId dependency : dependencies.apply(stage)) {
            collectAncestors(dependency, visible, dependencies, result);
        }
    }
}
