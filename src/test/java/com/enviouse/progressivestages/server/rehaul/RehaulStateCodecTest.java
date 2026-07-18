package com.enviouse.progressivestages.server.rehaul;

import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.rehaul.condition.ConditionStateStore;
import com.enviouse.progressivestages.common.rehaul.condition.SubjectScope;
import com.enviouse.progressivestages.common.rehaul.counter.CounterKey;
import com.enviouse.progressivestages.common.rehaul.counter.CounterWindow;
import com.enviouse.progressivestages.common.rehaul.lifecycle.LifecycleDirection;
import com.enviouse.progressivestages.common.rehaul.lifecycle.LifecycleTransactionEngine;
import com.enviouse.progressivestages.common.rehaul.lifecycle.TemporaryRuleEngine;
import com.enviouse.progressivestages.common.rehaul.lifecycle.TransitionHistoryEntry;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RehaulStateCodecTest {

    private final RehaulRuntime runtime = RehaulRuntime.get();

    @AfterEach
    void clean() {
        runtime.conditionStates().clear();
        runtime.temporary().clear();
        runtime.lifecycle().restore(List.of(), 0);
        runtime.counters().clear();
        runtime.transitionHistory().clear();
    }

    @Test
    void mutableRuntimeStateSurvivesAnNbtRoundTrip() {
        ResourceLocation rule = id("trial_rule");
        StageId stage = StageId.parse("progressivestages:trial");
        runtime.conditionStates().restore(Map.of("player|sequence",
            new ConditionStateStore.SequenceSnapshot(2, 50)));
        runtime.temporary().restore(List.of(new TemporaryRuleEngine.TemporaryStateSnapshot(
            "player", rule, true, true, true, 10, 0, 8, 0, 100, 120)));
        runtime.lifecycle().restore(List.of(new LifecycleTransactionEngine.LifecycleStateSnapshot(
            "player", rule, true, true, 5, 20, 80, true)), 7);
        CounterKey counter = new CounterKey("player", SubjectScope.PLAYER, id("damage"), CounterWindow.lifetime());
        runtime.counters().add(counter, 12.5, 40);
        runtime.transitionHistory().add(new TransitionHistoryEntry(7, 60, "player", rule,
            stage, LifecycleDirection.GRANT, true, "Committed"));

        var encoded = RehaulStateCodec.encode(runtime);
        clean();
        RehaulStateCodec.decode(encoded, runtime);

        assertEquals(2, runtime.conditionStates().snapshot().get("player|sequence").index());
        assertTrue(runtime.temporary().snapshot().getFirst().active());
        assertTrue(runtime.lifecycle().snapshot().getFirst().armed());
        assertEquals(7, runtime.lifecycle().transactionId());
        assertEquals(12.5, runtime.counters().value(counter, 100));
        assertEquals(stage, runtime.transitionHistory().entries().getFirst().stage());
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("progressivestages", path);
    }
}
