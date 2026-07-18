package com.enviouse.progressivestages.common.network;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NetworkCostSummaryTest {

    @Test
    void itemCostsUseReadableNamesAndPluralForms() {
        assertEquals("12x Gold Ingots", NetworkHandler.formatItemCost(
            ResourceLocation.parse("minecraft:gold_ingot"), 12));
        assertEquals("1x Diamond", NetworkHandler.formatItemCost(
            ResourceLocation.parse("minecraft:diamond"), 1));
        assertEquals("32x Lapis Lazuli", NetworkHandler.formatItemCost(
            ResourceLocation.parse("minecraft:lapis_lazuli"), 32));
    }
}
