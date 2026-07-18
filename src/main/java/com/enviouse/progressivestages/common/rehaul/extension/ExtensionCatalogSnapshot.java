package com.enviouse.progressivestages.common.rehaul.extension;

import java.util.List;

public record ExtensionCatalogSnapshot(long revision, boolean frozen, List<ExtensionMetadata> registrations) {
    public ExtensionCatalogSnapshot {
        registrations = registrations == null ? List.of() : List.copyOf(registrations);
    }
}
