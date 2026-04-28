package com.enviouse.progressivestages.common.lock;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A category of locks in 2.0 TOML: one {@code locked} list plus one
 * {@code always_unlocked} whitelist. Every category that uses the
 * unified {@code id:/mod:/tag:/name:} prefix system shares this shape
 * ({@code [items]}, {@code [blocks]}, {@code [fluids]}, {@code [entities]},
 * {@code [enchants]}, {@code [crops]}, {@code [screens]}, {@code [loot]},
 * {@code [pets]}, {@code [mobs]} spawn list, etc.).
 *
 * <p>The whitelist stores resolved {@link ResourceLocation}s because it's only
 * ever consulted as an exact-ID exemption. The locked list stores parsed
 * {@link PrefixEntry}s so we can match against ID, mod namespace, tag, or name
 * without re-parsing at query time.
 */
public final class CategoryLocks {

    public static final CategoryLocks EMPTY = new CategoryLocks(List.of(), Set.of());

    private final List<PrefixEntry> locked;
    private final Set<ResourceLocation> alwaysUnlocked;

    private CategoryLocks(List<PrefixEntry> locked, Set<ResourceLocation> alwaysUnlocked) {
        this.locked = Collections.unmodifiableList(locked);
        this.alwaysUnlocked = Collections.unmodifiableSet(alwaysUnlocked);
    }

    public List<PrefixEntry> locked() { return locked; }

    public Set<ResourceLocation> alwaysUnlocked() { return alwaysUnlocked; }

    public boolean isEmpty() {
        return locked.isEmpty() && alwaysUnlocked.isEmpty();
    }

    public boolean isAlwaysUnlocked(ResourceLocation id) {
        return id != null && alwaysUnlocked.contains(id);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final List<PrefixEntry> locked = new ArrayList<>();
        private final Set<ResourceLocation> alwaysUnlocked = new HashSet<>();

        /**
         * Parse and add each raw string in {@code rawLocked} as a {@link PrefixEntry}.
         * Invalid entries are silently dropped — the caller is expected to validate
         * separately when reporting back to the user.
         */
        public Builder addLocked(List<String> rawLocked) {
            if (rawLocked == null) return this;
            for (String raw : rawLocked) {
                PrefixEntry entry = PrefixEntry.parse(raw);
                if (entry != null) locked.add(entry);
            }
            return this;
        }

        /**
         * Parse each raw string as an exact ID. The {@code always_unlocked} list is
         * intentionally narrower than {@code locked} — only exact IDs (with or without
         * the {@code id:} prefix) are accepted, since matching a whole mod or tag as an
         * "exemption" would undermine the lock's intent.
         */
        public Builder addAlwaysUnlocked(List<String> rawAlwaysUnlocked) {
            if (rawAlwaysUnlocked == null) return this;
            for (String raw : rawAlwaysUnlocked) {
                PrefixEntry entry = PrefixEntry.parse(raw);
                if (entry != null && entry.kind() == PrefixEntry.Kind.ID && entry.id() != null) {
                    alwaysUnlocked.add(entry.id());
                }
            }
            return this;
        }

        public CategoryLocks build() {
            if (locked.isEmpty() && alwaysUnlocked.isEmpty()) return EMPTY;
            return new CategoryLocks(new ArrayList<>(locked), new HashSet<>(alwaysUnlocked));
        }
    }
}
