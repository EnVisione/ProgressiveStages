package com.enviouse.progressivestages.client;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientAbilityStateTest {
    @AfterEach
    void clear() {
        ClientAbilityState.clear();
    }

    @Test
    void normalizesAndReplacesServerResolvedAbilityLocks() {
        ClientAbilityState.set(List.of(" Swim ", "ELYTRA", "swim"));

        assertEquals(Set.of("swim", "elytra"), ClientAbilityState.snapshot());
        assertTrue(ClientAbilityState.isLocked("SWIM"));

        ClientAbilityState.set(List.of("jump"));

        assertEquals(Set.of("jump"), ClientAbilityState.snapshot());
        assertFalse(ClientAbilityState.isLocked("swim"));
    }
}
