package com.enviouse.progressivestages.compat;

import com.enviouse.progressivestages.mixin.OptionalCompatMixinPlugin;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OptionalCompatMixinPluginTest {

    private final OptionalCompatMixinPlugin plugin = new OptionalCompatMixinPlugin();

    @Test
    void appliesMixinsWhenTheirTargetClassExists() {
        assertTrue(plugin.shouldApplyMixin("java.lang.String", "example.PresentMixin"));
    }

    @Test
    void skipsMixinsWhenTheirOptionalTargetClassIsAbsent() {
        assertFalse(plugin.shouldApplyMixin("missing.optional.ModClass", "example.AbsentMixin"));
    }
}
