package com.enviouse.progressivestages.common.api.structure;

public final class StructureAccessArbitration {
    private StructureAccessArbitration() {}

    public static StructureAccessDecision.Result combine(
            boolean staticDenied, StructureAccessDecision.Result providerResult) {
        if (staticDenied || providerResult == StructureAccessDecision.Result.DENY) {
            return StructureAccessDecision.Result.DENY;
        }
        if (providerResult == StructureAccessDecision.Result.PERMIT) {
            return StructureAccessDecision.Result.PERMIT;
        }
        return StructureAccessDecision.Result.PASS;
    }
}
