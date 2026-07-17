package com.enviouse.progressivestages.common.trigger;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TriggerConditionTypeTest {

    @Test
    void resolvesFriendlyCustomCounterAliases() {
        assertEquals(TriggerConditionType.CUSTOM_COUNTER, TriggerConditionType.fromString("custom_counter"));
        assertEquals(TriggerConditionType.CUSTOM_COUNTER, TriggerConditionType.fromString("counter"));
        assertEquals(TriggerConditionType.CUSTOM_COUNTER, TriggerConditionType.fromString("kubejs_counter"));
        assertTrue(TriggerConditionType.CUSTOM_COUNTER.isCounter());
        assertTrue(TriggerConditionType.CUSTOM_COUNTER.requiresTarget());
    }

    @Test
    void unknownTypesRemainInvalid() {
        assertNull(TriggerConditionType.fromString("definitely_not_a_trigger"));
    }

    @Test
    void resolvesLiveAndScriptProgressSources() {
        assertEquals(TriggerConditionType.SCOREBOARD, TriggerConditionType.fromString("objective"));
        assertEquals(TriggerConditionType.STAGE_COUNT, TriggerConditionType.fromString("stages_owned"));
        assertEquals(TriggerConditionType.ONLINE_TEAM_SIZE, TriggerConditionType.fromString("team_size"));
        assertEquals(TriggerConditionType.SCRIPT_VALUE, TriggerConditionType.fromString("kubejs_value"));
        assertTrue(TriggerConditionType.SCOREBOARD.requiresTarget());
        assertTrue(TriggerConditionType.SCRIPT_VALUE.requiresTarget());
    }
}
