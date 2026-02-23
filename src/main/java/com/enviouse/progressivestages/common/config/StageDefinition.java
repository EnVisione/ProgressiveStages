package com.enviouse.progressivestages.common.config;

import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.lock.LockDefinition;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Represents a parsed stage definition from a TOML file.
 *
 * <p>v1.3 changes:
 * <ul>
 *   <li>Removed order field (replaced by dependency graph)</li>
 *   <li>Added dependencies list (stages required before this one)</li>
 *   <li>Added unlockedItems list (whitelist exceptions)</li>
 * </ul>
 *
 * <p>v1.4 changes:
 * <ul>
 *   <li>Added unlockedBlocks list (whitelist for blocks)</li>
 *   <li>Added unlockedEntities list (whitelist for entities)</li>
 *   <li>Added unlockedFluids list (whitelist for fluids)</li>
 * </ul>
 */
public class StageDefinition {

    private final StageId id;
    private final String displayName;
    private final String description;
    private final ResourceLocation icon;
    private final String unlockMessage;
    private final LockDefinition locks;
    private final List<StageId> dependencies;
    private final List<String> unlockedItems;
    private final List<String> unlockedBlocks;
    private final List<String> unlockedEntities;
    private final List<String> unlockedFluids;

    private StageDefinition(Builder builder) {
        this.id = builder.id;
        this.displayName = builder.displayName;
        this.description = builder.description;
        this.icon = builder.icon;
        this.unlockMessage = builder.unlockMessage;
        this.locks = builder.locks != null ? builder.locks : LockDefinition.empty();
        this.dependencies = builder.dependencies != null
            ? Collections.unmodifiableList(new ArrayList<>(builder.dependencies))
            : Collections.emptyList();
        this.unlockedItems = builder.unlockedItems != null
            ? Collections.unmodifiableList(new ArrayList<>(builder.unlockedItems))
            : Collections.emptyList();
        this.unlockedBlocks = builder.unlockedBlocks != null
            ? Collections.unmodifiableList(new ArrayList<>(builder.unlockedBlocks))
            : Collections.emptyList();
        this.unlockedEntities = builder.unlockedEntities != null
            ? Collections.unmodifiableList(new ArrayList<>(builder.unlockedEntities))
            : Collections.emptyList();
        this.unlockedFluids = builder.unlockedFluids != null
            ? Collections.unmodifiableList(new ArrayList<>(builder.unlockedFluids))
            : Collections.emptyList();
    }

    public StageId getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    /**
     * @deprecated Use {@link #getDependencies()} instead. Order-based progression is removed in v1.3.
     */
    @Deprecated(forRemoval = true)
    public int getOrder() {
        return 0; // Legacy compatibility - always return 0
    }

    public Optional<ResourceLocation> getIcon() {
        return Optional.ofNullable(icon);
    }

    public Optional<String> getUnlockMessage() {
        return Optional.ofNullable(unlockMessage);
    }

    public LockDefinition getLocks() {
        return locks;
    }

    /**
     * Get the stages that must be unlocked before this stage can be granted.
     * Empty list means no dependencies (can be granted at any time).
     */
    public List<StageId> getDependencies() {
        return dependencies;
    }

    /**
     * Check if this stage has any dependencies.
     */
    public boolean hasDependencies() {
        return !dependencies.isEmpty();
    }

    /**
     * Get items that are always unlocked (whitelist exceptions).
     * These items bypass mod locks, name patterns, and tag locks.
     */
    public List<String> getUnlockedItems() {
        return unlockedItems;
    }

    /**
     * Get blocks that are always unlocked (whitelist exceptions).
     * These blocks bypass mod locks, name patterns, and tag locks.
     */
    public List<String> getUnlockedBlocks() {
        return unlockedBlocks;
    }

    /**
     * Get entities that are always unlocked (whitelist exceptions).
     * These entities bypass mod locks, name patterns, and tag locks.
     */
    public List<String> getUnlockedEntities() {
        return unlockedEntities;
    }

    /**
     * Get fluids that are always unlocked (whitelist exceptions).
     * These fluids bypass mod locks for EMI/JEI visibility.
     */
    public List<String> getUnlockedFluids() {
        return unlockedFluids;
    }

    @Override
    public String toString() {
        return "StageDefinition{" +
            "id=" + id +
            ", displayName='" + displayName + '\'' +
            ", dependencies=" + dependencies +
            '}';
    }

    public static Builder builder(StageId id) {
        return new Builder(id);
    }

    public static class Builder {
        private final StageId id;
        private String displayName;
        private String description = "";
        private ResourceLocation icon;
        private String unlockMessage;
        private LockDefinition locks;
        private List<StageId> dependencies = new ArrayList<>();
        private List<String> unlockedItems = new ArrayList<>();
        private List<String> unlockedBlocks = new ArrayList<>();
        private List<String> unlockedEntities = new ArrayList<>();
        private List<String> unlockedFluids = new ArrayList<>();

        private Builder(StageId id) {
            this.id = id;
            this.displayName = id.getPath();
        }

        public Builder displayName(String displayName) {
            this.displayName = displayName;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        /**
         * @deprecated Use {@link #dependencies(List)} instead. Order-based progression is removed in v1.3.
         */
        @Deprecated(forRemoval = true)
        public Builder order(int order) {
            // Legacy compatibility - ignore
            return this;
        }

        public Builder icon(ResourceLocation icon) {
            this.icon = icon;
            return this;
        }

        public Builder icon(String icon) {
            if (icon != null && !icon.isEmpty()) {
                this.icon = ResourceLocation.parse(icon);
            }
            return this;
        }

        public Builder unlockMessage(String unlockMessage) {
            this.unlockMessage = unlockMessage;
            return this;
        }

        public Builder locks(LockDefinition locks) {
            this.locks = locks;
            return this;
        }

        /**
         * Set the dependencies for this stage.
         * @param dependencies List of stage IDs that must be unlocked first
         */
        public Builder dependencies(List<StageId> dependencies) {
            this.dependencies = dependencies != null ? dependencies : new ArrayList<>();
            return this;
        }

        /**
         * Add a single dependency.
         */
        public Builder addDependency(StageId dependency) {
            if (dependency != null) {
                this.dependencies.add(dependency);
            }
            return this;
        }

        /**
         * Set the unlocked items (whitelist exceptions).
         * @param unlockedItems List of item IDs that bypass this stage's broader locks
         */
        public Builder unlockedItems(List<String> unlockedItems) {
            this.unlockedItems = unlockedItems != null ? unlockedItems : new ArrayList<>();
            return this;
        }

        /**
         * Set the unlocked blocks (whitelist exceptions).
         * @param unlockedBlocks List of block IDs that bypass this stage's broader locks
         */
        public Builder unlockedBlocks(List<String> unlockedBlocks) {
            this.unlockedBlocks = unlockedBlocks != null ? unlockedBlocks : new ArrayList<>();
            return this;
        }

        /**
         * Set the unlocked entities (whitelist exceptions).
         * @param unlockedEntities List of entity IDs that bypass this stage's broader locks
         */
        public Builder unlockedEntities(List<String> unlockedEntities) {
            this.unlockedEntities = unlockedEntities != null ? unlockedEntities : new ArrayList<>();
            return this;
        }

        /**
         * Set the unlocked fluids (whitelist exceptions).
         * @param unlockedFluids List of fluid IDs that bypass mod locks for EMI/JEI visibility
         */
        public Builder unlockedFluids(List<String> unlockedFluids) {
            this.unlockedFluids = unlockedFluids != null ? unlockedFluids : new ArrayList<>();
            return this;
        }

        public StageDefinition build() {
            return new StageDefinition(this);
        }
    }
}
