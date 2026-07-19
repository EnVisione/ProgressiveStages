package com.enviouse.progressivestages.client;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ClientEntityVisibilityTest {

    @AfterEach
    void clear() {
        ClientEntityVisibility.clear();
    }

    @Test
    void synchronizedVisibilitySetIsAnImmutableCopy() {
        ResourceLocation skeleton = ResourceLocation.parse("minecraft:skeleton");
        Set<ResourceLocation> source = new HashSet<>(Set.of(skeleton));
        ClientEntityVisibility.setConcealed(source);
        source.clear();

        assertEquals(Set.of(skeleton), ClientEntityVisibility.snapshot());
        assertThrows(UnsupportedOperationException.class,
            () -> ClientEntityVisibility.snapshot().add(ResourceLocation.parse("minecraft:zombie")));
    }
}
