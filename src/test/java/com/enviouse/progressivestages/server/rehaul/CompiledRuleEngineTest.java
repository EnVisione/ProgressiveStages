package com.enviouse.progressivestages.server.rehaul;

import com.enviouse.progressivestages.common.rehaul.RuleEffect;
import org.junit.jupiter.api.Test;

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
}
