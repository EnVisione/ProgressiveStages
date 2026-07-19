package com.enviouse.progressivestages.common.rehaul.decision;

import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.rehaul.CompiledRule;
import com.enviouse.progressivestages.common.rehaul.ConditionNode;
import com.enviouse.progressivestages.common.rehaul.ConfigProvenance;
import com.enviouse.progressivestages.common.rehaul.RuleEffect;
import com.enviouse.progressivestages.common.rehaul.RuleLifetime;
import com.enviouse.progressivestages.common.rehaul.SelectorSpec;
import com.enviouse.progressivestages.common.rehaul.ViewerPolicy;
import com.enviouse.progressivestages.common.rehaul.selector.SelectorMatch;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DecisionResolverTest {

    @Test
    void globalAllowCanBeatLowerLock() {
        DecisionTrace trace = DecisionResolver.resolve(id("target"), "items", "use", List.of(
            candidate(rule("lock", RuleEffect.LOCK, 100, "mod:example", null), 100, 0, 100),
            candidate(rule("allow", RuleEffect.ALLOW, 500, "id:example:target", null), 500, 1, 400)),
            TiePolicy.SAFE);
        assertEquals(RuleEffect.ALLOW, trace.winningEffect());
        assertFalse(trace.blocked());
    }

    @Test
    void exclusionOnlySuppressesItsParent() {
        ResourceLocation broadId = id("broad");
        DecisionTrace trace = DecisionResolver.resolve(id("target"), "items", "use", List.of(
            candidate(rule(broadId, RuleEffect.LOCK, 100, "mod:example", null), 100, 0, 100),
            candidate(rule("other", RuleEffect.LOCK, 90, "name:target", null), 90, 1, 200),
            candidate(rule("exclude", RuleEffect.EXCLUDE, 150, "id:example:target", broadId), 150, 2, 400)),
            TiePolicy.SAFE);
        assertEquals(id("other"), trace.winningRule());
        assertTrue(trace.blocked());
    }

    @Test
    void highPriorityExceptionCarvesOneEntityOutOfAll() {
        ResourceLocation broadId = id("all_entities");
        DecisionTrace trace = DecisionResolver.resolve(ResourceLocation.parse("minecraft:villager"),
            "entities", "presence", List.of(
                candidate(rule(broadId, RuleEffect.LOCK, 100, "all:*", null), 100, 0, 0),
                candidate(rule("villager_exception", RuleEffect.EXCLUDE, 200,
                    "id:minecraft:villager", broadId), 200, 1, 400)),
            TiePolicy.SAFE);
        assertFalse(trace.blocked());
        assertEquals(null, trace.winningEffect());
    }

    @Test
    void safeTieLocksAndErrorPolicyRejects() {
        List<DecisionCandidate> candidates = List.of(
            candidate(rule("allow", RuleEffect.ALLOW, 100, "id:example:target", null), 100, 0, 400),
            candidate(rule("lock", RuleEffect.LOCK, 100, "mod:example", null), 100, 1, 100));
        assertTrue(DecisionResolver.resolve(id("target"), "items", "use", candidates, TiePolicy.SAFE).blocked());
        assertThrows(IllegalStateException.class, () ->
            DecisionResolver.resolve(id("target"), "items", "use", candidates, TiePolicy.ERROR_ON_TIE));
    }

    private static DecisionCandidate candidate(CompiledRule rule, int priority, int order, int specificity) {
        return new DecisionCandidate(rule, SelectorMatch.yes(specificity, "matched"), true, order,
            new ResolvedPriority(priority, PrioritySource.RULE));
    }

    private static CompiledRule rule(String name, RuleEffect effect, int priority, String selector,
                                     ResourceLocation parent) {
        return rule(id(name), effect, priority, selector, parent);
    }

    private static CompiledRule rule(ResourceLocation id, RuleEffect effect, int priority, String selector,
                                     ResourceLocation parent) {
        return new CompiledRule(id, StageId.parse("test:stage"), "items", "use", effect,
            SelectorSpec.parse(selector).orElseThrow(), priority, RuleLifetime.PERMANENT,
            new ConditionNode.Constant(true), parent, ViewerPolicy.INHERIT, Map.of(),
            ConfigProvenance.legacy("test", "test", "items"));
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("test", path);
    }
}
