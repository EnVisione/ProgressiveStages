package com.enviouse.progressivestages.server.enforcement;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AbilityEnforcerTest {
    @Test
    void everyDocumentedAbilityHasAuthoritativeEnforcement() {
        assertEquals(Set.of("jump", "elytra", "sprint", "swim", "climb"),
            AbilityEnforcer.ENFORCED_ABILITIES);
    }
}
