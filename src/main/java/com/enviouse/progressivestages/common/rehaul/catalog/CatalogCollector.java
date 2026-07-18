package com.enviouse.progressivestages.common.rehaul.catalog;

import net.minecraft.resources.ResourceLocation;

import java.util.Collection;

public interface CatalogCollector {
    void add(ResourceLocation catalogId, CatalogEntry entry);

    default void addAll(ResourceLocation catalogId, Collection<CatalogEntry> entries) {
        for (CatalogEntry entry : entries) add(catalogId, entry);
    }
}
