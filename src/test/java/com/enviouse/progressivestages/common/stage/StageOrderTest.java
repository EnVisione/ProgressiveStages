package com.enviouse.progressivestages.common.stage;

import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.config.StageDefinition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StageOrderTest {

    private final StageOrder order = StageOrder.getInstance();

    @AfterEach
    void clearOrder() {
        order.clear();
    }

    @Test
    void resolvesConvergingDependencyBranches() {
        StageId base = StageId.parse("base");
        StageId left = StageId.parse("left");
        StageId right = StageId.parse("right");
        StageId top = StageId.parse("top");
        order.registerStage(stage(base));
        order.registerStage(stage(left, base));
        order.registerStage(stage(right, base));
        order.registerStage(stage(top, left, right));

        assertEquals(Set.of(base, left, right), order.getAllDependencies(top));
    }

    @Test
    void cyclicTraversalDoesNotReportTheRootAsItsOwnDependency() {
        StageId first = StageId.parse("first");
        StageId second = StageId.parse("second");
        order.registerStage(stage(first, second));
        order.registerStage(stage(second, first));

        Set<StageId> dependencies = order.getAllDependencies(first);
        assertTrue(dependencies.contains(second));
        assertFalse(dependencies.contains(first));
        assertFalse(order.validateDependencies().isEmpty());
    }

    private static StageDefinition stage(StageId id, StageId... dependencies) {
        return StageDefinition.builder(id).dependencies(List.of(dependencies)).build();
    }
}
