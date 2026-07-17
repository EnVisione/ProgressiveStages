package com.enviouse.progressivestages.server.enforcement;

import com.enviouse.progressivestages.common.config.StageAttribute;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class StageAttributeApplierTest {

    private static final ResourceLocation MODIFIER_ID =
        ResourceLocation.fromNamespaceAndPath("progressivestages", "test_scale");
    private static final ResourceLocation ATTRIBUTE_ID =
        ResourceLocation.fromNamespaceAndPath("minecraft", "generic.scale");

    @Test
    void unchangedScaleModifierKeepsTheSameInstance() {
        AttributeInstance instance = new AttributeInstance(Attributes.SCALE, ignored -> {});
        StageAttribute configured = new StageAttribute(
            ATTRIBUTE_ID, AttributeModifier.Operation.ADD_VALUE, 0.25D);
        AttributeModifier original = new AttributeModifier(
            MODIFIER_ID, configured.amount(), configured.operation());
        instance.addTransientModifier(original);

        StageAttributeApplier.reconcileModifier(instance, MODIFIER_ID, configured, true);

        assertSame(original, instance.getModifier(MODIFIER_ID));
    }

    @Test
    void changedModifierIsReplacedAndRevokedModifierIsRemoved() {
        AttributeInstance instance = new AttributeInstance(Attributes.SCALE, ignored -> {});
        instance.addTransientModifier(new AttributeModifier(
            MODIFIER_ID, 0.25D, AttributeModifier.Operation.ADD_VALUE));
        StageAttribute changed = new StageAttribute(
            ATTRIBUTE_ID, AttributeModifier.Operation.ADD_MULTIPLIED_BASE, 0.5D);

        StageAttributeApplier.reconcileModifier(instance, MODIFIER_ID, changed, true);

        assertEquals(0.5D, instance.getModifier(MODIFIER_ID).amount());
        assertEquals(AttributeModifier.Operation.ADD_MULTIPLIED_BASE,
            instance.getModifier(MODIFIER_ID).operation());

        StageAttributeApplier.reconcileModifier(instance, MODIFIER_ID, changed, false);
        assertNull(instance.getModifier(MODIFIER_ID));
    }
}
