package com.enviouse.progressivestages.common.rehaul.extension;

import net.minecraft.resources.ResourceLocation;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ExtensionMetadataRegistry {
    private static final ExtensionMetadataRegistry INSTANCE = new ExtensionMetadataRegistry();
    private volatile Map<Key, ExtensionMetadata> registrations = Map.of();
    private volatile boolean frozen;
    private volatile long revision;

    private ExtensionMetadataRegistry() {}

    public static ExtensionMetadataRegistry get() { return INSTANCE; }

    public synchronized void beginReload() {
        registrations = Map.of();
        frozen = false;
    }

    public synchronized void register(ExtensionMetadata metadata) {
        if (frozen) throw new IllegalStateException("Extension metadata is frozen until the next supported reload");
        Key key = new Key(metadata.kind(), metadata.id());
        Map<Key, ExtensionMetadata> copy = new LinkedHashMap<>(registrations);
        if (copy.putIfAbsent(key, metadata) != null) throw new IllegalArgumentException("Duplicate extension metadata. " + metadata.id());
        registrations = Map.copyOf(copy);
    }

    public synchronized ExtensionCatalogSnapshot freeze() {
        frozen = true;
        revision++;
        return snapshot();
    }

    public Optional<ExtensionMetadata> find(ExtensionKind kind, ResourceLocation id) {
        return Optional.ofNullable(registrations.get(new Key(kind, id)));
    }

    public ExtensionCatalogSnapshot snapshot() {
        List<ExtensionMetadata> values = registrations.values().stream()
            .sorted(Comparator.comparing((ExtensionMetadata value) -> value.kind().name())
                .thenComparing(value -> value.id().toString())).toList();
        return new ExtensionCatalogSnapshot(revision, frozen, values);
    }

    public boolean frozen() { return frozen; }

    private record Key(ExtensionKind kind, ResourceLocation id) {}
}
