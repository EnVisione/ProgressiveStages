package com.enviouse.progressivestages;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MixinConfigurationTest {

    @Test
    void enchantmentSelectionMixinsArePackaged() throws Exception {
        try (var input = MixinConfigurationTest.class.getResourceAsStream(
                "/progressivestages.mixins.json")) {
            assertNotNull(input);
            String mixins = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(mixins.contains("\"EnchantmentMenuMixin\""));
            assertTrue(mixins.contains("\"EnchantWithLevelsFunctionMixin\""));
            assertTrue(mixins.contains("\"EnchantRandomlyFunctionMixin\""));
        }
    }

    @Test
    void enchantmentSelectionTargetsLoadWithRequiredInjectors() throws Exception {
        Class.forName("net.minecraft.world.inventory.EnchantmentMenu");
        Class.forName("net.minecraft.world.level.storage.loot.functions.EnchantWithLevelsFunction");
        Class.forName("net.minecraft.world.level.storage.loot.functions.EnchantRandomlyFunction");
    }
}
