package com.enviouse.progressivestages.common.compat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScriptHooksTest {

    @AfterEach
    void resetHooks() {
        ScriptHooks.reset();
    }

    @Test
    void providerIdentifiersAreNormalizedConsistently() {
        ScriptHooks.registerCondition(" Ready ", player -> true);
        ScriptHooks.registerProgress(" VALUE ", player -> 42L);

        assertTrue(ScriptHooks.evalCondition("ready", null));
        assertEquals(42L, ScriptHooks.evalProgress("value", null));
    }
}
