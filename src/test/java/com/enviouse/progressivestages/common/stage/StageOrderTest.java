package com.enviouse.progressivestages.common.stage;

import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.config.StageDefinition;
import com.enviouse.progressivestages.common.lock.ConditionalRule;
import com.enviouse.progressivestages.common.lock.PrefixEntry;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
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

    @Test
    void conditionalContextMustReferenceExistingStages() {
        StageId owner = StageId.parse("owner");
        StageId absent = StageId.parse("absent");
        ConditionalRule.Context context = new ConditionalRule.Context(
            ConditionalRule.ContextMode.ALL, List.of(), List.of(), List.of(), null, null,
            null, null, Set.of(absent), Set.of(), Set.of(), null, null, null, null, null, "");
        ConditionalRule rule = rule("shared", owner, context);

        List<String> errors = StageOrder.validateDefinitions(List.of(
            StageDefinition.builder(owner).conditionalRules(List.of(rule)).build()));

        assertTrue(errors.stream().anyMatch(error -> error.contains("references missing stage")));
    }

    @Test
    void conditionalRuleIdentifiersMustBeUniqueAcrossStages() {
        StageId first = StageId.parse("first");
        StageId second = StageId.parse("second");
        ConditionalRule firstRule = rule("shared", first, ConditionalRule.Context.EMPTY);
        ConditionalRule secondRule = rule("shared", second, ConditionalRule.Context.EMPTY);

        List<String> errors = StageOrder.validateDefinitions(List.of(
            StageDefinition.builder(first).conditionalRules(List.of(firstRule)).build(),
            StageDefinition.builder(second).conditionalRules(List.of(secondRule)).build()));

        assertTrue(errors.stream().anyMatch(error -> error.contains("defined by both")));
    }

    private static StageDefinition stage(StageId id, StageId... dependencies) {
        return StageDefinition.builder(id).dependencies(List.of(dependencies)).build();
    }

    private static ConditionalRule rule(String id, StageId owner, ConditionalRule.Context context) {
        PrefixEntry item = PrefixEntry.parse("minecraft:bow");
        ConditionalRule.Targets targets = new ConditionalRule.Targets(
            Map.of(ConditionalRule.TargetType.ITEM, List.of(item)), Map.of());
        return new ConditionalRule(ResourceLocation.fromNamespaceAndPath("test", id), owner,
            ConditionalRule.Effect.LOCK, ConditionalRule.Activation.LIVE,
            ConditionalRule.StageState.OWNED, 100, context, targets,
            ConditionalRule.TriggerType.MANUAL, List.of(), -1L, true);
    }
}
