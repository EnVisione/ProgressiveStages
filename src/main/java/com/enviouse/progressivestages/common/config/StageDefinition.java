package com.enviouse.progressivestages.common.config;

import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.lock.LockDefinition;
import com.enviouse.progressivestages.common.trigger.TriggerRule;
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
    // v2.3: per-stage auto-grant triggers (replaces the old global triggers.toml).
    private final List<TriggerRule> triggers;
    // v2.3: per-stage [display] overrides. null == inherit the global progressivestages.toml default.
    private final Boolean displayAsUnknownItem;
    private final Boolean obscureIcon;
    private final Boolean showTooltip;
    private final Boolean showDescriptionOnTooltip;
    // v2.4: presentation/organization metadata + new sections.
    private final boolean hidden;
    private final String color;        // "" or a hex/&-code for GUI tinting
    private final String category;     // "" or a group label
    private final java.util.List<String> tags; // v3.0: labels for bulk ops (/stage tag grant ...)
    private final String scope;        // "team" (default) or "server"
    private final long durationMillis; // -1 = permanent; otherwise real-time lifespan after grant
    private final List<StageAttribute> attributes;
    private final RevokeRule revoke;
    private final StageCost cost;      // null = not purchasable
    private final UnlockEffects unlock;
    private final StageRewards rewards; // v3.0: items/effects/commands/teleport/xp granted on unlock
    private final java.util.Set<String> lockedAbilities; // e.g. "elytra" — blocked until this stage is owned

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
        this.triggers = builder.triggers != null
            ? Collections.unmodifiableList(new ArrayList<>(builder.triggers))
            : Collections.emptyList();
        this.displayAsUnknownItem = builder.displayAsUnknownItem;
        this.obscureIcon = builder.obscureIcon;
        this.showTooltip = builder.showTooltip;
        this.showDescriptionOnTooltip = builder.showDescriptionOnTooltip;
        this.hidden = builder.hidden;
        this.color = builder.color != null ? builder.color : "";
        this.category = builder.category != null ? builder.category : "";
        this.tags = builder.tags != null
            ? Collections.unmodifiableList(new ArrayList<>(builder.tags)) : Collections.emptyList();
        this.scope = builder.scope != null ? builder.scope : "team";
        this.durationMillis = builder.durationMillis;
        this.attributes = builder.attributes != null
            ? Collections.unmodifiableList(new ArrayList<>(builder.attributes))
            : Collections.emptyList();
        this.revoke = builder.revoke != null ? builder.revoke : RevokeRule.NONE;
        this.cost = builder.cost;
        this.unlock = builder.unlock != null ? builder.unlock : UnlockEffects.NONE;
        this.rewards = builder.rewards != null ? builder.rewards : StageRewards.NONE;
        this.lockedAbilities = builder.lockedAbilities != null
            ? java.util.Set.copyOf(builder.lockedAbilities) : java.util.Set.of();
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

    /**
     * v2.3: per-stage auto-grant trigger rules parsed from the {@code [[triggers]]} section.
     * Each rule grants this stage when its conditions are met; rules are OR-ed together.
     */
    public List<TriggerRule> getTriggers() {
        return triggers;
    }

    /** True if this stage declares at least one auto-grant trigger rule. */
    public boolean hasTriggers() {
        return !triggers.isEmpty();
    }

    /**
     * v2.3 [display] override: render items locked by this stage with a masked ("???") name.
     * {@code null} means "inherit the global progressivestages.toml default".
     */
    public Boolean getDisplayAsUnknownItem() {
        return displayAsUnknownItem;
    }

    /**
     * v2.3 [display] override: replace the icon of items locked by this stage with a "?" placeholder.
     * {@code null} means "inherit the global default".
     */
    public Boolean getObscureIcon() {
        return obscureIcon;
    }

    /**
     * v2.3 [display] override: whether to show the lock/stage tooltip lines for items locked by
     * this stage at all. {@code null} means "inherit the global default".
     */
    public Boolean getShowTooltip() {
        return showTooltip;
    }

    /**
     * v2.3 [display] override: append this stage's description to the tooltip of items it locks.
     * {@code null} means "inherit the global default".
     */
    public Boolean getShowDescriptionOnTooltip() {
        return showDescriptionOnTooltip;
    }

    // ---- v2.4 ----

    /** True if this stage should be hidden from the GUI tree / progression views. */
    public boolean isHidden() { return hidden; }

    /** Optional GUI tint (hex like {@code #55FF55} or an {@code &}-code), or "" for the default. */
    public String getColor() { return color; }

    /** Optional GUI group label, or "" for none. */
    public String getCategory() { return category; }

    /** v3.0: lower-case tags for bulk commands ({@code /stage tag grant ...}); empty if none. */
    public java.util.List<String> getTags() { return tags; }

    /** Progression scope: {@code "team"} (default) or {@code "server"} (server-wide). */
    public String getScope() { return scope; }

    /** True if this stage is server-wide (shared by everyone), not per-team. */
    public boolean isServerScope() { return "server".equalsIgnoreCase(scope); }

    /** Real-time lifespan in milliseconds after grant, or {@code -1} for a permanent stage. */
    public long getDurationMillis() { return durationMillis; }

    /** True if this is a temporary stage that auto-expires after {@link #getDurationMillis()}. */
    public boolean isTemporary() { return durationMillis > 0; }

    /** Attribute modifiers applied while this stage is owned (empty if none). */
    public List<StageAttribute> getAttributes() { return attributes; }

    /** Revocation rules ({@code [revoke]}); {@link RevokeRule#NONE} if none declared. */
    public RevokeRule getRevoke() { return revoke; }

    /** Purchase cost ({@code [cost]}); {@code null} if the stage is not purchasable. */
    public StageCost getCost() { return cost; }

    /** True if this stage can be bought from the GUI (declared a {@code [cost]} section). */
    public boolean isPurchasable() { return cost != null; }

    /** Unlock presentation ({@code [unlock]}); {@link UnlockEffects#NONE} if none declared. */
    public UnlockEffects getUnlock() { return unlock; }

    /** Rewards granted on unlock ({@code [rewards]}); {@link StageRewards#NONE} if none declared. */
    public StageRewards getRewards() { return rewards; }

    /** Abilities this stage gates (blocked until owned), e.g. {@code "elytra"}. Lower-case. */
    public java.util.Set<String> getLockedAbilities() { return lockedAbilities; }

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
        private List<TriggerRule> triggers = new ArrayList<>();
        private Boolean displayAsUnknownItem = null;
        private Boolean obscureIcon = null;
        private Boolean showTooltip = null;
        private Boolean showDescriptionOnTooltip = null;
        private boolean hidden = false;
        private String color = "";
        private String category = "";
        private java.util.List<String> tags = new ArrayList<>();
        private String scope = "team";
        private long durationMillis = -1L;
        private List<StageAttribute> attributes = new ArrayList<>();
        private RevokeRule revoke = RevokeRule.NONE;
        private StageCost cost = null;
        private UnlockEffects unlock = UnlockEffects.NONE;
        private StageRewards rewards = StageRewards.NONE;
        private java.util.Set<String> lockedAbilities = java.util.Set.of();

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

        /** v2.3: set the per-stage auto-grant trigger rules. */
        public Builder triggers(List<TriggerRule> triggers) {
            this.triggers = triggers != null ? triggers : new ArrayList<>();
            return this;
        }

        /** v2.3 [display]: null leaves the global default in effect. */
        public Builder displayAsUnknownItem(Boolean v) {
            this.displayAsUnknownItem = v;
            return this;
        }

        public Builder obscureIcon(Boolean v) {
            this.obscureIcon = v;
            return this;
        }

        public Builder showTooltip(Boolean v) {
            this.showTooltip = v;
            return this;
        }

        public Builder showDescriptionOnTooltip(Boolean v) {
            this.showDescriptionOnTooltip = v;
            return this;
        }

        // ---- v2.4 ----
        public Builder hidden(boolean v) { this.hidden = v; return this; }
        public Builder color(String v) { this.color = v != null ? v : ""; return this; }
        public Builder category(String v) { this.category = v != null ? v : ""; return this; }
        public Builder tags(java.util.List<String> v) { this.tags = v != null ? v : new ArrayList<>(); return this; }
        public Builder scope(String v) { this.scope = v != null ? v : "team"; return this; }
        public Builder durationMillis(long v) { this.durationMillis = v; return this; }
        public Builder attributes(List<StageAttribute> v) { this.attributes = v != null ? v : new ArrayList<>(); return this; }
        public Builder revoke(RevokeRule v) { this.revoke = v != null ? v : RevokeRule.NONE; return this; }
        public Builder cost(StageCost v) { this.cost = v; return this; }
        public Builder unlock(UnlockEffects v) { this.unlock = v != null ? v : UnlockEffects.NONE; return this; }
        public Builder rewards(StageRewards v) { this.rewards = v != null ? v : StageRewards.NONE; return this; }
        public Builder lockedAbilities(java.util.Set<String> v) { this.lockedAbilities = v != null ? v : java.util.Set.of(); return this; }

        public StageDefinition build() {
            return new StageDefinition(this);
        }
    }
}
