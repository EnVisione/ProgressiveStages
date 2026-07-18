package com.enviouse.progressivestages.common.rehaul.catalog;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.time.Duration;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class CatalogSnapshotTest {

    private static final ResourceLocation ITEMS = id("items");

    @Test
    void ranksAndPagesWithoutExposingMutableState() {
        CatalogSnapshot snapshot = snapshot();
        CatalogPage first = snapshot.search(query("diamond", "id", "", 1, 7));
        assertEquals("minecraft:diamond", first.entries().getFirst().key());
        assertEquals(3, first.totalMatches());
        assertTrue(!first.nextCursor().isEmpty());

        CatalogPage second = snapshot.search(query("diamond", "id", first.nextCursor(), 1, 7));
        assertEquals("example:diamond_hammer", second.entries().getFirst().key());
        assertTrue(!second.nextCursor().isEmpty());

        CatalogPage third = snapshot.search(query("diamond", "id", second.nextCursor(), 1, 7));
        assertEquals("minecraft:diamond_sword", third.entries().getFirst().key());
        assertEquals("", third.nextCursor());
        assertThrows(UnsupportedOperationException.class, () -> second.entries().add(entry("test:bad", List.of())));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.catalogs().clear());
    }

    @Test
    void synthesizesNamespaceAndTagEntries() {
        CatalogSnapshot snapshot = snapshot();
        CatalogPage namespaces = snapshot.search(query("Minecraft", "mod", "", 20, 7));
        assertEquals(List.of("mod:minecraft"), namespaces.entries().stream().map(CatalogEntry::key).toList());

        CatalogPage tags = snapshot.search(query("gems", "tag", "", 20, 7));
        assertEquals(List.of("tag:c:gems/diamond"), tags.entries().stream().map(CatalogEntry::key).toList());
    }

    @Test
    void rejectsStaleRevisionsAndForgedCursors() {
        CatalogSnapshot snapshot = snapshot();
        CatalogPage stale = snapshot.search(query("", "id", "", 20, 6));
        assertTrue(stale.staleRevision());
        assertEquals(7, stale.revision());

        CatalogQuery forged = query("", "id", "definitely_not_a_cursor", 20, 7);
        assertThrows(IllegalArgumentException.class, () -> snapshot.search(forged));
    }

    @Test
    void indexedSearchKeepsLargeCatalogQueriesBounded() {
        List<CatalogEntry> entries = new ArrayList<>();
        for (int index = 0; index < 100_000; index++) {
            String key = "scale:item_" + index;
            ResourceLocation id = ResourceLocation.parse(key);
            String label = index == 99_999 ? "Unique Needle" : "Scale item " + index;
            entries.add(new CatalogEntry(ITEMS, key, ResourceLocation.parse("minecraft:item"), "scale",
                label, "", "static_registry", "scale", "Scale", List.of(), Set.of(), Map.of()));
        }
        Map<ResourceLocation, List<CatalogEntry>> catalogs = Map.of(ITEMS, entries);
        CatalogSnapshot snapshot = new CatalogSnapshot(18, 9, catalogs, List.of(), CatalogSnapshot.checksum(catalogs));

        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            CatalogPage page = snapshot.search(query("needle", "id", "", 20, 18));
            assertEquals(List.of("scale:item_99999"), page.entries().stream().map(CatalogEntry::key).toList());
        });
    }

    private static CatalogSnapshot snapshot() {
        List<CatalogEntry> entries = List.of(
            entry("minecraft:diamond", List.of(ResourceLocation.parse("c:gems/diamond"))),
            entry("minecraft:diamond_sword", List.of(ResourceLocation.parse("c:tools/sword"))),
            entry("example:diamond_hammer", List.of(ResourceLocation.parse("c:tools/hammer"))));
        Map<ResourceLocation, List<CatalogEntry>> catalogs = Map.of(ITEMS, entries);
        return new CatalogSnapshot(7, 3, catalogs, List.of(), CatalogSnapshot.checksum(catalogs));
    }

    private static CatalogEntry entry(String key, List<ResourceLocation> tags) {
        ResourceLocation id = ResourceLocation.parse(key);
        return new CatalogEntry(ITEMS, key, ResourceLocation.parse("minecraft:item"), id.getNamespace(),
            key, "item." + id.getNamespace() + "." + id.getPath(), "static_registry", id.getNamespace(),
            id.getNamespace().equals("minecraft") ? "Minecraft" : "Example", tags, Set.of(), Map.of());
    }

    private static CatalogQuery query(String text, String mode, String cursor, int size, long revision) {
        return new CatalogQuery(ITEMS, "test.field", mode, text, Map.of(), "relevance", size, cursor, revision);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("progressivestages", path);
    }
}
