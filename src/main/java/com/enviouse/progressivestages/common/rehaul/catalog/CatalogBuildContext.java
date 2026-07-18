package com.enviouse.progressivestages.common.rehaul.catalog;

import net.minecraft.server.MinecraftServer;

public record CatalogBuildContext(MinecraftServer server, long configurationRevision) {}
