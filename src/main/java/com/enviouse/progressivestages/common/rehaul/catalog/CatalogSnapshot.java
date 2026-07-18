package com.enviouse.progressivestages.common.rehaul.catalog;

import net.minecraft.resources.ResourceLocation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public record CatalogSnapshot(
        long revision,
        long configurationRevision,
        Map<ResourceLocation, List<CatalogEntry>> catalogs,
        List<String> providerErrors,
        String checksum) {

    private static final Map<IndexKey, SearchIndex> INDEXES = java.util.Collections.synchronizedMap(
        new LinkedHashMap<>() {
            @Override protected boolean removeEldestEntry(Map.Entry<IndexKey, SearchIndex> eldest) {
                return size() > 128;
            }
        });

    public CatalogSnapshot {
        if (revision < 0 || configurationRevision < 0) throw new IllegalArgumentException("Revisions cannot be negative");
        Map<ResourceLocation, List<CatalogEntry>> copy = new LinkedHashMap<>();
        catalogs.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry ->
            copy.put(entry.getKey(), List.copyOf(entry.getValue())));
        catalogs = Map.copyOf(copy);
        providerErrors = providerErrors == null ? List.of() : List.copyOf(providerErrors);
        if (checksum == null || checksum.isBlank()) throw new IllegalArgumentException("Checksum cannot be blank");
    }

    public CatalogPage search(CatalogQuery query) {
        if (query.expectedRevision() != 0L && query.expectedRevision() != revision) {
            return CatalogPage.stale(revision);
        }
        SearchIndex index = indexForMode(query.catalogId(), query.prefixMode());
        List<CatalogEntry> source = index.candidates(query.text(), query.filters());
        String normalized = query.text().toLowerCase(Locale.ROOT);
        String signature = signature(query.catalogId(), query.prefixMode(), normalized, query.filters(), query.sort());
        int offset = query.cursor().isEmpty() ? 0 : decodeCursor(query.cursor(), signature);

        List<ScoredEntry> matches = new ArrayList<>();
        for (CatalogEntry entry : source) {
            if (!matchesFilters(entry, query.filters())) continue;
            int score = score(entry, normalized);
            if (score < Integer.MAX_VALUE) matches.add(new ScoredEntry(entry, score));
        }
        Comparator<ScoredEntry> comparator = switch (query.sort()) {
            case "id" -> Comparator.comparing(value -> value.entry().key());
            case "label" -> Comparator.comparing(value -> value.entry().label().toLowerCase(Locale.ROOT));
            default -> Comparator.comparingInt(ScoredEntry::score)
                .thenComparing(value -> value.entry().key());
        };
        matches.sort(comparator);
        if (offset < 0 || offset > matches.size()) throw new IllegalArgumentException("Invalid catalog cursor offset");
        int end = Math.min(matches.size(), offset + query.pageSize());
        List<CatalogEntry> page = matches.subList(offset, end).stream().map(ScoredEntry::entry).toList();
        String next = end < matches.size() ? encodeCursor(end, signature) : "";
        return new CatalogPage(revision, page, matches.size(), next, false, false);
    }

    public Set<ResourceLocation> catalogIds() {
        return catalogs.keySet();
    }

    private List<CatalogEntry> entriesForMode(ResourceLocation catalogId, String mode) {
        List<CatalogEntry> entries = catalogs.getOrDefault(catalogId, List.of());
        if (mode.equals("mod")) {
            Map<String, CatalogEntry> namespaces = new LinkedHashMap<>();
            for (CatalogEntry entry : entries) {
                if (entry.namespace().isBlank()) continue;
                namespaces.putIfAbsent(entry.namespace(), new CatalogEntry(catalogId,
                    "mod:" + entry.namespace(), null, entry.namespace(),
                    entry.modName().isBlank() ? entry.namespace() : entry.modName(), "", "namespace",
                    entry.namespace(), entry.modName(), List.of(), Set.of(), Map.of()));
            }
            return List.copyOf(namespaces.values());
        }
        if (mode.equals("tag")) {
            Map<ResourceLocation, CatalogEntry> tags = new LinkedHashMap<>();
            for (CatalogEntry entry : entries) {
                for (ResourceLocation tag : entry.tags()) {
                    tags.putIfAbsent(tag, new CatalogEntry(catalogId, "tag:" + tag,
                        entry.registryId(), tag.getNamespace(), tag.toString(), "", "tag",
                        tag.getNamespace(), "", List.of(), Set.of(), Map.of()));
                }
            }
            return List.copyOf(tags.values());
        }
        return entries;
    }

    private SearchIndex indexForMode(ResourceLocation catalogId, String mode) {
        IndexKey key = new IndexKey(System.identityHashCode(catalogs), revision, catalogId, mode);
        SearchIndex found = INDEXES.get(key);
        if (found != null) return found;
        SearchIndex created = SearchIndex.create(entriesForMode(catalogId, mode));
        INDEXES.put(key, created);
        return created;
    }

    private static boolean matchesFilters(CatalogEntry entry, Map<String, String> filters) {
        for (Map.Entry<String, String> filter : filters.entrySet()) {
            boolean match = switch (filter.getKey()) {
                case "namespace" -> entry.namespace().equalsIgnoreCase(filter.getValue());
                case "source" -> entry.sourceType().equalsIgnoreCase(filter.getValue());
                case "mod" -> entry.modId().equalsIgnoreCase(filter.getValue());
                case "tag" -> entry.tags().stream().anyMatch(tag -> tag.toString().equals(filter.getValue()));
                default -> true;
            };
            if (!match) return false;
        }
        return true;
    }

    private static int score(CatalogEntry entry, String query) {
        if (query.isEmpty()) return 0;
        String key = entry.key().toLowerCase(Locale.ROOT);
        String label = entry.label().toLowerCase(Locale.ROOT);
        if (key.equals(query) || label.equals(query)) return 0;
        ResourceLocation resource = ResourceLocation.tryParse(entry.key());
        if (resource != null && resource.getPath().equalsIgnoreCase(query)) return 0;
        if (key.startsWith(query) || label.startsWith(query)) return 1;
        if (tokenStarts(entry.searchableText(), query)) return 2;
        if (entry.searchableText().contains(query)) return 3;
        int distance = boundedDistance(key, query, 2);
        return distance <= 2 ? 4 + distance : Integer.MAX_VALUE;
    }

    private static boolean tokenStarts(String text, String query) {
        for (String token : text.split("[^a-z0-9_./:-]+")) {
            if (token.startsWith(query)) return true;
        }
        return false;
    }

    private int decodeCursor(String cursor, String expectedSignature) {
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] parts = decoded.split("\\|", 3);
            if (parts.length != 3 || Long.parseLong(parts[0]) != revision || !parts[1].equals(expectedSignature)) {
                throw new IllegalArgumentException("Stale or forged catalog cursor");
            }
            return Integer.parseInt(parts[2]);
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("Invalid catalog cursor", error);
        }
    }

    private String encodeCursor(int offset, String signature) {
        String raw = revision + "|" + signature + "|" + offset;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private static String signature(ResourceLocation catalog, String mode, String query,
                                    Map<String, String> filters, String sort) {
        StringBuilder input = new StringBuilder(catalog.toString()).append('|').append(mode)
            .append('|').append(query).append('|').append(sort);
        filters.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry ->
            input.append('|').append(entry.getKey()).append('=').append(entry.getValue()));
        return shortHash(input.toString());
    }

    static String checksum(Map<ResourceLocation, List<CatalogEntry>> catalogs) {
        StringBuilder value = new StringBuilder();
        catalogs.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(catalog -> {
            value.append(catalog.getKey()).append('\n');
            catalog.getValue().stream().map(CatalogEntry::key).sorted().forEach(key ->
                value.append(key).append('\n'));
        });
        return shortHash(value.toString());
    }

    private static String shortHash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8))).substring(0, 24);
        } catch (Exception impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static int boundedDistance(String left, String right, int limit) {
        if (Math.abs(left.length() - right.length()) > limit) return limit + 1;
        int[] previous = new int[right.length() + 1];
        int[] current = new int[right.length() + 1];
        for (int index = 0; index <= right.length(); index++) previous[index] = index;
        for (int row = 1; row <= left.length(); row++) {
            current[0] = row;
            int best = current[0];
            for (int column = 1; column <= right.length(); column++) {
                int cost = left.charAt(row - 1) == right.charAt(column - 1) ? 0 : 1;
                current[column] = Math.min(Math.min(current[column - 1] + 1, previous[column] + 1),
                    previous[column - 1] + cost);
                best = Math.min(best, current[column]);
            }
            if (best > limit) return limit + 1;
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[right.length()];
    }

    private record ScoredEntry(CatalogEntry entry, int score) {}

    private record IndexKey(int snapshotIdentity, long revision, ResourceLocation catalog, String mode) {}

    private record SearchIndex(List<CatalogEntry> entries, Map<String, List<CatalogEntry>> tokenPrefixes) {
        static SearchIndex create(List<CatalogEntry> entries) {
            Map<String, List<CatalogEntry>> prefixes = new LinkedHashMap<>();
            for (CatalogEntry entry : entries) {
                Set<String> seen = new LinkedHashSet<>();
                for (String token : entry.searchableText().split("[^a-z0-9_]+")) {
                    if (token.length() >= 2) seen.add(token.substring(0, 2));
                }
                for (String prefix : seen) prefixes.computeIfAbsent(prefix, ignored -> new ArrayList<>()).add(entry);
            }
            prefixes.replaceAll((ignored, values) -> List.copyOf(values));
            return new SearchIndex(List.copyOf(entries), Map.copyOf(prefixes));
        }

        List<CatalogEntry> candidates(String query, Map<String, String> filters) {
            String normalized = query == null ? "" : query.toLowerCase(Locale.ROOT).trim();
            if (normalized.length() < 2 || !filters.isEmpty()) return entries;
            String token = normalized.split("[^a-z0-9_]+", 2)[0];
            if (token.length() < 2) return entries;
            List<CatalogEntry> candidates = tokenPrefixes.get(token.substring(0, 2));
            return candidates == null || candidates.isEmpty() ? entries : candidates;
        }
    }
}
