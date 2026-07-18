package com.enviouse.progressivestages.common.rehaul.catalog;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class EditorCatalogService {

    private static final EditorCatalogService INSTANCE = new EditorCatalogService();

    private volatile Map<ResourceLocation, CatalogContributor> contributors;
    private volatile CatalogSnapshot snapshot;
    private long revision;

    private EditorCatalogService() {
        CatalogContributor builtins = new BuiltinCatalogContributor();
        contributors = Map.of(builtins.id(), builtins);
        snapshot = new CatalogSnapshot(0L, 0L, Map.of(), List.of(), CatalogSnapshot.checksum(Map.of()));
    }

    public static EditorCatalogService get() {
        return INSTANCE;
    }

    public synchronized void register(CatalogContributor contributor) {
        Map<ResourceLocation, CatalogContributor> copy = new LinkedHashMap<>(contributors);
        CatalogContributor previous = copy.putIfAbsent(contributor.id(), contributor);
        if (previous != null) throw new IllegalArgumentException("Duplicate catalog contributor. " + contributor.id());
        contributors = Map.copyOf(copy);
    }

    public synchronized CatalogSnapshot rebuild(MinecraftServer server, long configurationRevision) {
        Map<ResourceLocation, Map<String, CatalogEntry>> collected = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();
        CatalogCollector collector = (catalogId, entry) -> collected
            .computeIfAbsent(catalogId, ignored -> new LinkedHashMap<>()).put(entry.key(), entry);
        CatalogBuildContext context = new CatalogBuildContext(server, configurationRevision);
        for (CatalogContributor contributor : contributors.values()) {
            try {
                contributor.contribute(context, collector);
            } catch (RuntimeException error) {
                errors.add(contributor.id() + ". " + error.getClass().getSimpleName() + ". " + error.getMessage());
            }
        }
        Map<ResourceLocation, List<CatalogEntry>> catalogs = new LinkedHashMap<>();
        collected.forEach((id, values) -> catalogs.put(id, values.values().stream()
            .sorted(Comparator.comparing(CatalogEntry::key)).toList()));
        snapshot = new CatalogSnapshot(++revision, configurationRevision, catalogs, errors,
            CatalogSnapshot.checksum(catalogs));
        return snapshot;
    }

    public synchronized CatalogSnapshot reset() {
        snapshot = new CatalogSnapshot(++revision, 0L, Map.of(), List.of(), CatalogSnapshot.checksum(Map.of()));
        return snapshot;
    }

    public CatalogSnapshot snapshot() {
        return snapshot;
    }

    public CatalogPage search(CatalogQuery query) {
        return snapshot.search(query);
    }
}
