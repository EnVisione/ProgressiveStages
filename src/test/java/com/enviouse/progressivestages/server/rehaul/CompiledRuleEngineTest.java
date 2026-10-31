package com.enviouse.progressivestages.server.rehaul;

import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.rehaul.CompiledRule;
import com.enviouse.progressivestages.common.rehaul.ConditionNode;
import com.enviouse.progressivestages.common.rehaul.ConfigProvenance;
import com.enviouse.progressivestages.common.rehaul.RuleEffect;
import com.enviouse.progressivestages.common.rehaul.RuleLifetime;
import com.enviouse.progressivestages.common.rehaul.SelectorSpec;
import com.enviouse.progressivestages.common.rehaul.ViewerPolicy;
import com.enviouse.progressivestages.common.rehaul.selector.SelectorMatcherRegistry;
import com.enviouse.progressivestages.common.rehaul.selector.SelectorTarget;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompiledRuleEngineTest {
    @Test
    void permanentLocksApplyBeforeOwnershipAndTemporaryRulesRequireOwnership() {
        assertTrue(CompiledRuleEngine.stageStateMatches(false, "", "rules", RuleEffect.LOCK));
        assertFalse(CompiledRuleEngine.stageStateMatches(true, "", "rules", RuleEffect.LOCK));
        assertFalse(CompiledRuleEngine.stageStateMatches(false, "", "temporary_rules", RuleEffect.LOCK));
        assertTrue(CompiledRuleEngine.stageStateMatches(true, "", "temporary_rules", RuleEffect.LOCK));
        assertTrue(CompiledRuleEngine.stageStateMatches(true, "", "rules", RuleEffect.ALLOW));
        assertFalse(CompiledRuleEngine.stageStateMatches(false, "", "rules", RuleEffect.DENY));
        assertTrue(CompiledRuleEngine.stageStateMatches(true, "", "rules", RuleEffect.DENY));
        assertTrue(CompiledRuleEngine.stageStateMatches(false, "always", "rules", RuleEffect.ALLOW));
    }

    @Test
    void viewerIndexMatchesSelectorsOnceAndResolvesBothViewersFromOnePlayerState() {
        StageId broadStage = StageId.parse("test:broad");
        StageId taggedStage = StageId.parse("test:tagged");
        CompiledRule broad = rule("broad", broadStage, "mod:example", RuleEffect.LOCK, 100,
            new ViewerPolicy(ViewerPolicy.Mode.HIDE, ViewerPolicy.Mode.INHERIT, ViewerPolicy.Mode.SHOW,
                true, true, true, true, true));
        CompiledRule exception = rule("exception", broadStage, "id:example:second", RuleEffect.ALLOW, 200,
            ViewerPolicy.INHERIT);
        CompiledRule tagged = rule("tagged", taggedStage, "tag:example:valuable", RuleEffect.LOCK, 150,
            new ViewerPolicy(ViewerPolicy.Mode.SHOW, ViewerPolicy.Mode.SHOW, ViewerPolicy.Mode.HIDE,
                true, true, true, true, true));
        ResourceLocation first = ResourceLocation.parse("example:first");
        ResourceLocation second = ResourceLocation.parse("example:second");
        ResourceLocation third = ResourceLocation.parse("example:third");
        ResourceLocation tag = ResourceLocation.parse("example:valuable");
        AtomicInteger matches = new AtomicInteger();
        var index = CompiledRuleEngine.buildViewerRuleIndex(List.of(
            SelectorTarget.id(first), SelectorTarget.id(second),
            new SelectorTarget(third, null, Set.of(tag), Map.of())),
            List.of(broad, exception, tagged), (selector, target) -> {
                matches.incrementAndGet();
                return SelectorMatcherRegistry.get().match(selector, target);
            });
        assertEquals(9, matches.get());

        AtomicInteger activityChecks = new AtomicInteger();
        Map<ResourceLocation, CompiledRule> byId = Map.of(broad.id(), broad,
            exception.id(), exception, tagged.id(), tagged);
        var locks = CompiledRuleEngine.resolveIndexedViewerItemLocks(index, byId, rule -> {
            activityChecks.incrementAndGet();
            return true;
        });
        assertEquals(Map.of(first, Set.of(broadStage)), locks.emi());
        assertEquals(Map.of(third, Set.of(taggedStage)), locks.jei());
        assertEquals(3, activityChecks.get());
        assertEquals(9, matches.get());

        var afterGrant = CompiledRuleEngine.resolveIndexedViewerItemLocks(index, byId,
            rule -> rule != broad);
        assertTrue(afterGrant.emi().isEmpty());
        assertEquals(Map.of(third, Set.of(taggedStage)), afterGrant.jei());
    }

    @Test
    void largeRegistryDoesNotRepeatSelectorOrConditionWorkForEachPlayer() {
        StageId owner = StageId.parse("test:owner");
        CompiledRule rule = rule("large", owner, "mod:example", RuleEffect.LOCK, 100,
            new ViewerPolicy(ViewerPolicy.Mode.HIDE, ViewerPolicy.Mode.INHERIT,
                ViewerPolicy.Mode.INHERIT, true, true, true, true, true));
        List<SelectorTarget> targets = IntStream.range(0, 4096)
            .mapToObj(index -> SelectorTarget.id(ResourceLocation.parse("example:item_" + index)))
            .toList();
        AtomicInteger matches = new AtomicInteger();
        var index = CompiledRuleEngine.buildViewerRuleIndex(targets, List.of(rule),
            (selector, target) -> {
                matches.incrementAndGet();
                return SelectorMatcherRegistry.get().match(selector, target);
            });
        assertEquals(4096, matches.get());

        AtomicInteger activityChecks = new AtomicInteger();
        for (int player = 0; player < 2; player++) {
            var locks = CompiledRuleEngine.resolveIndexedViewerItemLocks(index,
                Map.of(rule.id(), rule), candidate -> {
                    activityChecks.incrementAndGet();
                    return true;
                });
            assertEquals(4096, locks.emi().size());
            assertEquals(4096, locks.jei().size());
        }
        assertEquals(2, activityChecks.get());
        assertEquals(4096, matches.get());

        var reloaded = CompiledRuleEngine.buildViewerRuleIndex(targets, List.of(),
            SelectorMatcherRegistry.get()::match);
        assertTrue(reloaded.targets().isEmpty());
    }

    private static CompiledRule rule(String name, StageId owner, String selector, RuleEffect effect,
                                     int priority, ViewerPolicy policy) {
        return new CompiledRule(ResourceLocation.parse("test:" + name), owner, "items", "use", effect,
            SelectorSpec.parse(selector).orElseThrow(), priority, RuleLifetime.PERMANENT,
            new ConditionNode.Constant(true), null, policy, Map.of(),
            ConfigProvenance.legacy("test", "test", "items"));
    }
}
