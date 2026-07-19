package com.enviouse.progressivestages.common.lock;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrefixEntryTest {

    @Test
    void allStarMatchesEveryIdentifier() {
        PrefixEntry selector = PrefixEntry.parse("all:*");
        assertNotNull(selector);
        assertEquals(PrefixEntry.Kind.ALL, selector.kind());
        assertTrue(selector.matchesIdOnly(ResourceLocation.parse("minecraft:skeleton")));
        assertTrue(selector.matchesIdOnly(ResourceLocation.parse("example:anything")));
    }

    @Test
    void allRequiresTheExplicitStar() {
        assertNull(PrefixEntry.parse("all:skeleton"));
        assertFalse(PrefixEntry.parse("id:minecraft:skeleton").matchesIdOnly(
            ResourceLocation.parse("minecraft:zombie")));
    }
}
