package com.enviouse.progressivestages.common.lock;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;

import java.util.Objects;

/**
 * A single entry in a 2.0-style unified lock list.
 *
 * <p>Source format (as written in TOML):
 * <ul>
 *   <li>{@code id:minecraft:diamond} — exact registry ID match</li>
 *   <li>{@code minecraft:diamond}    — no prefix, implicit {@code id:}</li>
 *   <li>{@code mod:ae2}              — every registry entry from a mod</li>
 *   <li>{@code tag:c:gems/diamond}   — every registry entry in a tag</li>
 *   <li>{@code name:diamond}         — case-insensitive substring of the full ID</li>
 * </ul>
 *
 * <p>Parsed entries are immutable and cheap to carry. Matching is done per-registry via
 * {@link #matches(ResourceLocation, Holder)} — the caller supplies the holder so we can
 * ask {@link Holder#is(TagKey)} without needing a registry lookup.
 */
public final class PrefixEntry {

    public enum Kind {
        /** Exact registry ID match. */
        ID,
        /** Every registry entry whose namespace equals {@code value}. */
        MOD,
        /** Every registry entry belonging to the tag {@code value} (parsed as ResourceLocation). */
        TAG,
        /** Every registry entry whose full ID contains {@code value} as a substring (case-insensitive). */
        NAME
    }

    private final Kind kind;
    private final String raw;          // original source string (for debug / error messages)
    private final String value;        // payload after the prefix has been stripped
    private final ResourceLocation id; // populated for ID and TAG kinds (null otherwise)

    private PrefixEntry(Kind kind, String raw, String value, ResourceLocation id) {
        this.kind = kind;
        this.raw = raw;
        this.value = value;
        this.id = id;
    }

    /**
     * Synthesize a {@link Kind#MOD} entry for the given mod namespace. Used by the
     * {@code minecraft = true} shorthand to register an implicit {@code mod:minecraft}
     * entry across categories without going through the parser.
     */
    public static PrefixEntry fromMod(String modId) {
        if (modId == null) return null;
        String normalized = modId.trim().toLowerCase();
        if (normalized.isEmpty()) return null;
        return new PrefixEntry(Kind.MOD, "mod:" + normalized, normalized, null);
    }

    /**
     * Parse a single raw entry. Returns {@code null} if the string is blank or invalid
     * (for example {@code id:not-a-valid-id} or {@code tag:}).
     */
    public static PrefixEntry parse(String raw) {
        if (raw == null) return null;
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) return null;

        // Legacy #tag syntax: treat as tag:
        if (trimmed.startsWith("#")) {
            String tagStr = trimmed.substring(1).trim();
            ResourceLocation tagId = tryParse(tagStr);
            return tagId == null ? null : new PrefixEntry(Kind.TAG, trimmed, tagStr, tagId);
        }

        int colon = trimmed.indexOf(':');
        if (colon > 0) {
            String prefix = trimmed.substring(0, colon).toLowerCase();
            String rest = trimmed.substring(colon + 1).trim();

            switch (prefix) {
                case "id": {
                    ResourceLocation idLoc = tryParse(rest);
                    return idLoc == null ? null : new PrefixEntry(Kind.ID, trimmed, rest, idLoc);
                }
                case "mod": {
                    if (rest.isEmpty()) return null;
                    return new PrefixEntry(Kind.MOD, trimmed, rest.toLowerCase(), null);
                }
                case "tag": {
                    ResourceLocation tagId = tryParse(rest);
                    return tagId == null ? null : new PrefixEntry(Kind.TAG, trimmed, rest, tagId);
                }
                case "name": {
                    if (rest.isEmpty()) return null;
                    return new PrefixEntry(Kind.NAME, trimmed, rest.toLowerCase(), null);
                }
                // No recognized prefix → fall through to default-id handling.
            }
        }

        // No prefix: treat as exact ID (the "namespace:path" form without "id:").
        ResourceLocation idLoc = tryParse(trimmed);
        return idLoc == null ? null : new PrefixEntry(Kind.ID, trimmed, trimmed, idLoc);
    }

    private static ResourceLocation tryParse(String s) {
        if (s == null || s.isEmpty()) return null;
        try {
            return ResourceLocation.parse(s);
        } catch (Exception e) {
            return null;
        }
    }

    public Kind kind() { return kind; }
    public String raw() { return raw; }
    public String value() { return value; }

    /** The ResourceLocation payload for {@link Kind#ID} and {@link Kind#TAG}; {@code null} for MOD / NAME. */
    public ResourceLocation id() { return id; }

    /**
     * Check whether this entry matches a registry element identified by {@code elementId},
     * using the given registry-keyed {@code holder} for tag membership checks.
     *
     * <p>For TAG entries the caller must pass the element's {@link Holder} from the
     * correct registry (e.g. {@code Item.builtInRegistryHolder()} or a {@link Holder}
     * obtained via {@code registry.getHolderOrThrow(key)}). The tag key is constructed
     * against {@code registryKey} so the holder and the tag must agree on their registry.
     */
    public <T> boolean matches(ResourceLocation elementId, Holder<T> holder, ResourceKey<? extends net.minecraft.core.Registry<T>> registryKey) {
        if (elementId == null) return false;
        switch (kind) {
            case ID:
                return elementId.equals(id);
            case MOD:
                return elementId.getNamespace().equalsIgnoreCase(value);
            case TAG:
                if (holder == null || id == null) return false;
                TagKey<T> tagKey = TagKey.create(registryKey, id);
                return holder.is(tagKey);
            case NAME:
                return elementId.toString().toLowerCase().contains(value);
        }
        return false;
    }

    /**
     * Simpler match that skips tag checks — use when you don't have a holder handy
     * (e.g. matching against a raw ResourceLocation only, as the first gate before
     * paying for the holder-based tag test). Returns false for TAG entries.
     */
    public boolean matchesIdOnly(ResourceLocation elementId) {
        if (elementId == null) return false;
        return switch (kind) {
            case ID   -> elementId.equals(id);
            case MOD  -> elementId.getNamespace().equalsIgnoreCase(value);
            case NAME -> elementId.toString().toLowerCase().contains(value);
            case TAG  -> false;
        };
    }

    @Override
    public String toString() {
        return "PrefixEntry{" + kind + " " + value + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PrefixEntry other)) return false;
        return kind == other.kind && Objects.equals(value, other.value) && Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, value, id);
    }

    /**
     * Convenience: get the common Registries key for the standard lockable registries.
     * Keeps call sites from importing {@link Registries} directly.
     */
    public static final class Keys {
        private Keys() {}
        public static final ResourceKey<net.minecraft.core.Registry<net.minecraft.world.item.Item>> ITEM = Registries.ITEM;
        public static final ResourceKey<net.minecraft.core.Registry<net.minecraft.world.level.block.Block>> BLOCK = Registries.BLOCK;
        public static final ResourceKey<net.minecraft.core.Registry<net.minecraft.world.level.material.Fluid>> FLUID = Registries.FLUID;
        public static final ResourceKey<net.minecraft.core.Registry<net.minecraft.world.entity.EntityType<?>>> ENTITY_TYPE = Registries.ENTITY_TYPE;
        public static final ResourceKey<net.minecraft.core.Registry<net.minecraft.world.item.enchantment.Enchantment>> ENCHANTMENT = Registries.ENCHANTMENT;
    }
}
