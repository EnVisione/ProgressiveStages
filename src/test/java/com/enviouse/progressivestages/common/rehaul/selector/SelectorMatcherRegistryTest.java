package com.enviouse.progressivestages.common.rehaul.selector;

import com.enviouse.progressivestages.common.rehaul.SelectorSpec;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SelectorMatcherRegistryTest {

    @Test
    void preservesLegacyPrefixesAndCompactPriority() {
        SelectorSpec exact = SelectorSpec.parse("id:minecraft:diamond|priority=600").orElseThrow();
        assertEquals(600, exact.explicitPriority());
        assertTrue(SelectorMatcherRegistry.get().match(exact,
            SelectorTarget.id(ResourceLocation.parse("minecraft:diamond"))).matched());

        SelectorSpec tag = SelectorSpec.parse("#c:gems/diamond").orElseThrow();
        SelectorTarget target = new SelectorTarget(ResourceLocation.parse("minecraft:diamond"), null,
            Set.of(ResourceLocation.parse("c:gems/diamond")), Map.of());
        assertTrue(SelectorMatcherRegistry.get().match(tag, target).matched());
        assertFalse(SelectorMatcherRegistry.get().match(tag,
            SelectorTarget.id(ResourceLocation.parse("minecraft:diamond"))).matched());
    }

    @Test
    void supportsAdvancedMetadataMatchers() {
        SelectorSpec rarity = SelectorSpec.parse("rarity:epic").orElseThrow();
        SelectorTarget target = new SelectorTarget(ResourceLocation.parse("example:staff"), null,
            Set.of(), Map.of("rarity", "epic"));
        assertTrue(SelectorMatcherRegistry.get().match(rarity, target).matched());
    }
}
