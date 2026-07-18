package com.enviouse.progressivestages.common.rehaul.catalog;

import java.util.List;

public record CatalogPage(
        long revision,
        List<CatalogEntry> entries,
        long totalMatches,
        String nextCursor,
        boolean staleRevision,
        boolean truncated) {

    public CatalogPage {
        entries = entries == null ? List.of() : List.copyOf(entries);
        nextCursor = nextCursor != null ? nextCursor : "";
    }

    public static CatalogPage stale(long revision) {
        return new CatalogPage(revision, List.of(), 0L, "", true, false);
    }
}
