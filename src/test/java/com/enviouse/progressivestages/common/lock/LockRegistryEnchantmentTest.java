package com.enviouse.progressivestages.common.lock;

import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.config.StageDefinition;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LockRegistryEnchantmentTest {

    private final LockRegistry registry = LockRegistry.getInstance();

    @AfterEach
    void clearRegistry() {
        registry.clear();
    }

    @Test
    void enforcementFastPathStaysDisabledWithoutEnchantmentRules() {
        registry.clear();

        assertFalse(registry.isEnchantmentEnforcementConfigured());
        assertFalse(registry.isEnchantmentRetentionConfigured());
        assertFalse(registry.isEnchantmentLockConfigured());
    }

    @Test
    void selectionWeightsMakeLockDefinitionAndRegistryNonEmpty() {
        LockDefinition locks = LockDefinition.builder()
            .enchantSelectionWeights(List.of(new LockDefinition.EnchantSelectionWeight(
                ResourceLocation.withDefaultNamespace("mending"), 0)))
            .build();

        assertFalse(locks.isEmpty());

        registry.clear();
        registry.registerStage(StageDefinition.builder(StageId.parse("test:enchant_weight"))
            .locks(locks)
            .build());

        assertTrue(registry.hasEnchantSelectionWeights());
        assertTrue(registry.isEnchantmentEnforcementConfigured());
        assertFalse(registry.isEnchantmentRetentionConfigured());
        assertFalse(registry.isEnchantmentLockConfigured());
    }
}
