package com.enviouse.progressivestages.common.rehaul.catalog;

import net.minecraft.resources.ResourceLocation;

public interface CatalogContributor {
    ResourceLocation id();

    void contribute(CatalogBuildContext context, CatalogCollector collector);
}
