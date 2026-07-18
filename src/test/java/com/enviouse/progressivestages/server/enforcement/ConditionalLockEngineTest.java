package com.enviouse.progressivestages.server.enforcement;

import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.lock.ConditionalRule;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConditionalLockEngineTest {

    @Test
    void higherPriorityRuleWins() {
        ConditionalLockEngine.Decision staticLock = decision(ConditionalRule.Effect.LOCK, 0, "static");
        ConditionalLockEngine.Decision stageUnlock = decision(ConditionalRule.Effect.UNLOCK, 100, "unlock");
        ConditionalLockEngine.Decision endLock = decision(ConditionalRule.Effect.LOCK, 200, "end_lock");

        assertEquals(stageUnlock, ConditionalLockEngine.choose(staticLock, stageUnlock));
        assertEquals(endLock, ConditionalLockEngine.choose(stageUnlock, endLock));
    }

    @Test
    void lockWinsAnEqualPriorityTie() {
        ConditionalLockEngine.Decision unlock = decision(ConditionalRule.Effect.UNLOCK, 100, "unlock");
        ConditionalLockEngine.Decision lock = decision(ConditionalRule.Effect.LOCK, 100, "lock");

        assertEquals(lock, ConditionalLockEngine.choose(unlock, lock));
        assertEquals(lock, ConditionalLockEngine.choose(lock, unlock));
    }

    @Test
    void firstRuleWinsWhenEffectsAndPrioritiesMatch() {
        ConditionalLockEngine.Decision first = decision(ConditionalRule.Effect.LOCK, 100, "first");
        ConditionalLockEngine.Decision second = decision(ConditionalRule.Effect.LOCK, 100, "second");

        assertEquals(first, ConditionalLockEngine.choose(first, second));
    }

    private static ConditionalLockEngine.Decision decision(ConditionalRule.Effect effect,
                                                            int priority,
                                                            String id) {
        return new ConditionalLockEngine.Decision(effect, priority,
            ResourceLocation.fromNamespaceAndPath("test", id), StageId.of("owner"));
    }
}
