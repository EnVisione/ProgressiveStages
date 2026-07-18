package com.enviouse.progressivestages.common.api.structure;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StructureAccessArbitrationTest {

    @Test
    void providerDenialWinsEveryStaticResult() {
        assertEquals(StructureAccessDecision.Result.DENY,
            StructureAccessArbitration.combine(false, StructureAccessDecision.Result.DENY));
        assertEquals(StructureAccessDecision.Result.DENY,
            StructureAccessArbitration.combine(true, StructureAccessDecision.Result.DENY));
    }

    @Test
    void providerPermitCannotOverrideStaticDenial() {
        assertEquals(StructureAccessDecision.Result.DENY,
            StructureAccessArbitration.combine(true, StructureAccessDecision.Result.PERMIT));
    }

    @Test
    void passPreservesStaticBehaviorAndPermitSurvivesStaticAllow() {
        assertEquals(StructureAccessDecision.Result.PASS,
            StructureAccessArbitration.combine(false, StructureAccessDecision.Result.PASS));
        assertEquals(StructureAccessDecision.Result.DENY,
            StructureAccessArbitration.combine(true, StructureAccessDecision.Result.PASS));
        assertEquals(StructureAccessDecision.Result.PERMIT,
            StructureAccessArbitration.combine(false, StructureAccessDecision.Result.PERMIT));
    }
}
