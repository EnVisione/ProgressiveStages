package com.enviouse.progressivestages.common.lock;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * All locks parsed from one 2.0 stage file.
 *
 * <p>Each category stores parsed {@link PrefixEntry}s via {@link CategoryLocks} — one
 * unified list per category instead of v1's split {@code items / item_tags / item_mods}
 * triplets. The new TOML schema is:
 *
 * <pre>
 * [items]     locked = ["id:...", "mod:...", "tag:...", "name:..."]
 *             always_unlocked = ["id:..."]
 * [blocks]    locked = [...]      always_unlocked = [...]
 * [fluids]    locked = [...]      always_unlocked = [...]
 * [entities]  locked = [...]      always_unlocked = [...]   # attack/interaction gating
 * [enchants]  locked = [...]
 * [crops]     locked = [...]      always_unlocked = [...]
 * [screens]   locked = [...]
 * [loot]      locked = [...]
 * [pets]      locked_taming = [...]      locked_breeding = [...]
 * [mobs]      locked_spawns = [...]
 * [recipes]   locked_ids = [...]         locked_items = [...]
 * [dimensions] locked = [...]
 * </pre>
 *
 * <p>Complex structures ({@code [[interactions]]}, {@code [[mobs.replacements]]},
 * {@code [[regions]]}, {@code [[ores.overrides]]}, {@code [structures]}) live in their
 * own typed lists/records.
 */
public final class LockDefinition {

    // ---- Unified categories (prefix-based) ----
    private final CategoryLocks items;
    private final CategoryLocks blocks;
    private final CategoryLocks fluids;
    private final CategoryLocks entities;      // attack/interaction (v1 feature, preserved)
    private final CategoryLocks enchants;
    private final CategoryLocks crops;
    private final CategoryLocks screens;
    private final CategoryLocks loot;
    private final CategoryLocks petsTaming;
    private final CategoryLocks petsBreeding;
    private final CategoryLocks petsCommanding;
    private final CategoryLocks mobSpawns;
    /** [curios].locked_slots — slot identifiers (as Curios defines them: "ring", "necklace", etc.). */
    private final java.util.List<String> curioLockedSlots;

    // ---- Recipes ----
    /** [recipes].locked_ids — matches against the recipe's own registry ID. */
    private final CategoryLocks recipeIds;
    /** [recipes].locked_items — matches against the recipe's output item ID. */
    private final CategoryLocks recipeOutputs;

    // ---- Dimensions (simple list, no always_unlocked) ----
    private final List<ResourceLocation> lockedDimensions;

    // ---- Complex typed lists ----
    private final List<InteractionLock> interactions;
    private final List<MobReplacement> mobReplacements;
    private final List<RegionLock> regions;
    private final StructureRules structures;
    /** Parsed but not yet enforced — ore masquerade is deferred post-2.0. */
    private final List<OreOverride> oreOverrides;

    // ---- Enforcement exceptions ----
    private final List<String> allowedUse;
    private final List<String> allowedPickup;
    private final List<String> allowedHotbar;
    private final List<String> allowedMousePickup;
    private final List<String> allowedInventory;

    // ---- v2.0: gates the minecraft: namespace for this stage.
    private final boolean minecraftNamespace;

    // ---- v2.0: per-stage [unlocks] carve-outs (Phase C). Empty by default. ----
    private final UnlockGateLists unlocks;

    private LockDefinition(Builder b) {
        this.items         = b.items;
        this.blocks        = b.blocks;
        this.fluids        = b.fluids;
        this.entities      = b.entities;
        this.enchants      = b.enchants;
        this.crops         = b.crops;
        this.screens       = b.screens;
        this.loot          = b.loot;
        this.petsTaming    = b.petsTaming;
        this.petsBreeding  = b.petsBreeding;
        this.petsCommanding = b.petsCommanding;
        this.mobSpawns     = b.mobSpawns;
        this.curioLockedSlots = java.util.List.copyOf(b.curioLockedSlots);
        this.recipeIds     = b.recipeIds;
        this.recipeOutputs = b.recipeOutputs;
        this.lockedDimensions = List.copyOf(b.lockedDimensions);
        this.interactions     = List.copyOf(b.interactions);
        this.mobReplacements  = List.copyOf(b.mobReplacements);
        this.regions          = List.copyOf(b.regions);
        this.structures       = b.structures;
        this.oreOverrides     = List.copyOf(b.oreOverrides);
        this.allowedUse          = List.copyOf(b.allowedUse);
        this.allowedPickup       = List.copyOf(b.allowedPickup);
        this.allowedHotbar       = List.copyOf(b.allowedHotbar);
        this.allowedMousePickup  = List.copyOf(b.allowedMousePickup);
        this.allowedInventory    = List.copyOf(b.allowedInventory);
        this.minecraftNamespace  = b.minecraftNamespace;
        this.unlocks             = b.unlocks != null ? b.unlocks : UnlockGateLists.EMPTY;
    }

    public static LockDefinition empty() {
        return new Builder().build();
    }

    // ---- Accessors ----
    public CategoryLocks items()        { return items; }
    public CategoryLocks blocks()       { return blocks; }
    public CategoryLocks fluids()       { return fluids; }
    public CategoryLocks entities()     { return entities; }
    public CategoryLocks enchants()     { return enchants; }
    public CategoryLocks crops()        { return crops; }
    public CategoryLocks screens()      { return screens; }
    public CategoryLocks loot()         { return loot; }
    public CategoryLocks petsTaming()     { return petsTaming; }
    public CategoryLocks petsBreeding()   { return petsBreeding; }
    public CategoryLocks petsCommanding() { return petsCommanding; }
    public CategoryLocks mobSpawns()    { return mobSpawns; }
    public java.util.List<String> curioLockedSlots() { return curioLockedSlots; }
    public CategoryLocks recipeIds()    { return recipeIds; }
    public CategoryLocks recipeOutputs(){ return recipeOutputs; }

    public List<ResourceLocation> lockedDimensions() { return lockedDimensions; }
    public List<InteractionLock> interactions()      { return interactions; }
    public List<MobReplacement> mobReplacements()    { return mobReplacements; }
    public List<RegionLock> regions()                { return regions; }
    public StructureRules structures()               { return structures; }
    public List<OreOverride> oreOverrides()          { return oreOverrides; }

    public List<String> allowedUse()         { return allowedUse; }
    public List<String> allowedPickup()      { return allowedPickup; }
    public List<String> allowedHotbar()      { return allowedHotbar; }
    public List<String> allowedMousePickup() { return allowedMousePickup; }
    public List<String> allowedInventory()   { return allowedInventory; }

    /** v2.0: true if this stage gates the {@code minecraft:} namespace (shorthand for adding "minecraft" to mods). */
    public boolean minecraftNamespace() { return minecraftNamespace; }

    /** v2.0: per-stage carve-outs from this stage's own gating set. Empty by default. */
    public UnlockGateLists unlocks() { return unlocks; }

    public boolean isEmpty() {
        return items.isEmpty() && blocks.isEmpty() && fluids.isEmpty() && entities.isEmpty()
            && enchants.isEmpty() && crops.isEmpty() && screens.isEmpty() && loot.isEmpty()
            && petsTaming.isEmpty() && petsBreeding.isEmpty() && petsCommanding.isEmpty() && mobSpawns.isEmpty()
            && recipeIds.isEmpty() && recipeOutputs.isEmpty()
            && lockedDimensions.isEmpty() && interactions.isEmpty()
            && mobReplacements.isEmpty() && regions.isEmpty() && oreOverrides.isEmpty()
            && structures.isEmpty();
    }

    public static Builder builder() { return new Builder(); }

    // ---------------------------------------------------------------
    // Nested types
    // ---------------------------------------------------------------

    /**
     * A [[interactions]] entry: one specific player action that's gated behind a stage.
     * {@code heldItem} and {@code target} may be null (wildcard) or carry a single raw
     * prefix string that will be matched at enforcement time.
     */
    public static final class InteractionLock {
        private final String type;
        private final String heldItem;
        private final String target;
        private final String description;

        public InteractionLock(String type, String heldItem, String target, String description) {
            this.type = type;
            this.heldItem = heldItem;
            this.target = target;
            this.description = description;
        }

        public String type()        { return type; }
        public String heldItem()    { return heldItem; }
        public String target()      { return target; }
        // Legacy alias — InteractionEnforcer historically called this field "targetBlock".
        public String targetBlock() { return target; }
        public String description() { return description; }
    }

    /**
     * A [[mobs.replacements]] entry: when a locked mob would spawn, swap in another one.
     * The target uses the full prefix system so a user can swap out an entire mod's mobs;
     * the replacement is always a single exact entity ID.
     */
    public static final class MobReplacement {
        private final PrefixEntry target;
        private final ResourceLocation replaceWith;

        public MobReplacement(PrefixEntry target, ResourceLocation replaceWith) {
            this.target = target;
            this.replaceWith = replaceWith;
        }

        public PrefixEntry target()            { return target; }
        public ResourceLocation replaceWith()  { return replaceWith; }
    }

    /**
     * A [[regions]] entry: a fixed 3D box in a specific dimension with a set of gating flags.
     */
    public static final class RegionLock {
        private final ResourceLocation dimension;
        private final int[] pos1;   // length 3
        private final int[] pos2;   // length 3
        private final boolean preventEntry;
        private final boolean preventBlockBreak;
        private final boolean preventBlockPlace;
        private final boolean preventExplosions;
        private final boolean disableMobSpawning;

        public RegionLock(ResourceLocation dimension, int[] pos1, int[] pos2,
                          boolean preventEntry, boolean preventBlockBreak,
                          boolean preventBlockPlace, boolean preventExplosions,
                          boolean disableMobSpawning) {
            this.dimension = dimension;
            this.pos1 = pos1;
            this.pos2 = pos2;
            this.preventEntry = preventEntry;
            this.preventBlockBreak = preventBlockBreak;
            this.preventBlockPlace = preventBlockPlace;
            this.preventExplosions = preventExplosions;
            this.disableMobSpawning = disableMobSpawning;
        }

        public ResourceLocation dimension() { return dimension; }
        public int[] pos1()                 { return pos1; }
        public int[] pos2()                 { return pos2; }
        public boolean preventEntry()       { return preventEntry; }
        public boolean preventBlockBreak()  { return preventBlockBreak; }
        public boolean preventBlockPlace()  { return preventBlockPlace; }
        public boolean preventExplosions()  { return preventExplosions; }
        public boolean disableMobSpawning() { return disableMobSpawning; }
    }

    /**
     * The [structures] section: which structures block entry, plus the shared rule flags.
     * Rule flags apply to every structure listed in {@code lockedEntry}.
     */
    public static final class StructureRules {
        public static final StructureRules EMPTY = new StructureRules(
            CategoryLocks.EMPTY, false, false, false, false);

        private final CategoryLocks lockedEntry;
        private final boolean preventBlockBreak;
        private final boolean preventBlockPlace;
        private final boolean preventExplosions;
        private final boolean disableMobSpawning;

        public StructureRules(CategoryLocks lockedEntry, boolean preventBlockBreak,
                              boolean preventBlockPlace, boolean preventExplosions,
                              boolean disableMobSpawning) {
            this.lockedEntry = lockedEntry;
            this.preventBlockBreak = preventBlockBreak;
            this.preventBlockPlace = preventBlockPlace;
            this.preventExplosions = preventExplosions;
            this.disableMobSpawning = disableMobSpawning;
        }

        public CategoryLocks lockedEntry()  { return lockedEntry; }
        public boolean preventBlockBreak()  { return preventBlockBreak; }
        public boolean preventBlockPlace()  { return preventBlockPlace; }
        public boolean preventExplosions()  { return preventExplosions; }
        public boolean disableMobSpawning() { return disableMobSpawning; }

        public boolean isEmpty() {
            return lockedEntry.isEmpty() && !preventBlockBreak && !preventBlockPlace
                && !preventExplosions && !disableMobSpawning;
        }
    }

    /**
     * Per-stage [unlocks] carve-out lists.
     * <p>When this stage gates an item via mod or namespace, an item present in
     * {@code items} (or whose mod is in {@code mods}) is removed from the gating set
     * for THIS stage only. Other stages' gating still applies.
     */
    public static final class UnlockGateLists {
        public static final UnlockGateLists EMPTY = new UnlockGateLists(
            java.util.Set.of(), java.util.Set.of(), java.util.Set.of(),
            java.util.Set.of(), java.util.Set.of());

        private final java.util.Set<ResourceLocation> items;
        private final java.util.Set<String> mods;
        private final java.util.Set<ResourceLocation> fluids;
        private final java.util.Set<ResourceLocation> dimensions;
        private final java.util.Set<ResourceLocation> entities;

        public UnlockGateLists(java.util.Set<ResourceLocation> items, java.util.Set<String> mods,
                                java.util.Set<ResourceLocation> fluids,
                                java.util.Set<ResourceLocation> dimensions,
                                java.util.Set<ResourceLocation> entities) {
            this.items      = items != null ? java.util.Set.copyOf(items) : java.util.Set.of();
            this.mods       = mods != null ? java.util.Set.copyOf(mods) : java.util.Set.of();
            this.fluids     = fluids != null ? java.util.Set.copyOf(fluids) : java.util.Set.of();
            this.dimensions = dimensions != null ? java.util.Set.copyOf(dimensions) : java.util.Set.of();
            this.entities   = entities != null ? java.util.Set.copyOf(entities) : java.util.Set.of();
        }

        public java.util.Set<ResourceLocation> items()      { return items; }
        public java.util.Set<String>           mods()       { return mods; }
        public java.util.Set<ResourceLocation> fluids()     { return fluids; }
        public java.util.Set<ResourceLocation> dimensions() { return dimensions; }
        public java.util.Set<ResourceLocation> entities()   { return entities; }

        public boolean isEmpty() {
            return items.isEmpty() && mods.isEmpty() && fluids.isEmpty()
                && dimensions.isEmpty() && entities.isEmpty();
        }
    }

    /**
     * A [[ores.overrides]] entry. Parsed for forward-compat; enforcement is deferred
     * (see v2_update_plan.md §2.8 — on hold post-2.0).
     */
    public static final class OreOverride {
        private final ResourceLocation target;
        private final ResourceLocation displayAs;
        private final ResourceLocation dropAs;

        public OreOverride(ResourceLocation target, ResourceLocation displayAs, ResourceLocation dropAs) {
            this.target = target;
            this.displayAs = displayAs;
            this.dropAs = dropAs;
        }

        public ResourceLocation target()    { return target; }
        public ResourceLocation displayAs() { return displayAs; }
        public ResourceLocation dropAs()    { return dropAs; }
    }

    // ---------------------------------------------------------------
    // Builder
    // ---------------------------------------------------------------

    public static final class Builder {
        private CategoryLocks items = CategoryLocks.EMPTY;
        private CategoryLocks blocks = CategoryLocks.EMPTY;
        private CategoryLocks fluids = CategoryLocks.EMPTY;
        private CategoryLocks entities = CategoryLocks.EMPTY;
        private CategoryLocks enchants = CategoryLocks.EMPTY;
        private CategoryLocks crops = CategoryLocks.EMPTY;
        private CategoryLocks screens = CategoryLocks.EMPTY;
        private CategoryLocks loot = CategoryLocks.EMPTY;
        private CategoryLocks petsTaming = CategoryLocks.EMPTY;
        private CategoryLocks petsBreeding = CategoryLocks.EMPTY;
        private CategoryLocks petsCommanding = CategoryLocks.EMPTY;
        private CategoryLocks mobSpawns = CategoryLocks.EMPTY;
        private java.util.List<String> curioLockedSlots = new java.util.ArrayList<>();
        private CategoryLocks recipeIds = CategoryLocks.EMPTY;
        private CategoryLocks recipeOutputs = CategoryLocks.EMPTY;

        private List<ResourceLocation> lockedDimensions = new ArrayList<>();
        private List<InteractionLock> interactions = new ArrayList<>();
        private List<MobReplacement> mobReplacements = new ArrayList<>();
        private List<RegionLock> regions = new ArrayList<>();
        private StructureRules structures = StructureRules.EMPTY;
        private List<OreOverride> oreOverrides = new ArrayList<>();

        private List<String> allowedUse = new ArrayList<>();
        private List<String> allowedPickup = new ArrayList<>();
        private List<String> allowedHotbar = new ArrayList<>();
        private List<String> allowedMousePickup = new ArrayList<>();
        private List<String> allowedInventory = new ArrayList<>();

        private boolean minecraftNamespace = false;
        private UnlockGateLists unlocks = UnlockGateLists.EMPTY;

        public Builder items(CategoryLocks v)        { this.items = v != null ? v : CategoryLocks.EMPTY; return this; }
        public Builder blocks(CategoryLocks v)       { this.blocks = v != null ? v : CategoryLocks.EMPTY; return this; }
        public Builder fluids(CategoryLocks v)       { this.fluids = v != null ? v : CategoryLocks.EMPTY; return this; }
        public Builder entities(CategoryLocks v)     { this.entities = v != null ? v : CategoryLocks.EMPTY; return this; }
        public Builder enchants(CategoryLocks v)     { this.enchants = v != null ? v : CategoryLocks.EMPTY; return this; }
        public Builder crops(CategoryLocks v)        { this.crops = v != null ? v : CategoryLocks.EMPTY; return this; }
        public Builder screens(CategoryLocks v)      { this.screens = v != null ? v : CategoryLocks.EMPTY; return this; }
        public Builder loot(CategoryLocks v)         { this.loot = v != null ? v : CategoryLocks.EMPTY; return this; }
        public Builder petsTaming(CategoryLocks v)     { this.petsTaming = v != null ? v : CategoryLocks.EMPTY; return this; }
        public Builder petsBreeding(CategoryLocks v)   { this.petsBreeding = v != null ? v : CategoryLocks.EMPTY; return this; }
        public Builder petsCommanding(CategoryLocks v) { this.petsCommanding = v != null ? v : CategoryLocks.EMPTY; return this; }
        public Builder mobSpawns(CategoryLocks v)    { this.mobSpawns = v != null ? v : CategoryLocks.EMPTY; return this; }
        public Builder curioLockedSlots(java.util.List<String> v) {
            this.curioLockedSlots = v != null ? v : new java.util.ArrayList<>(); return this;
        }
        public Builder recipeIds(CategoryLocks v)    { this.recipeIds = v != null ? v : CategoryLocks.EMPTY; return this; }
        public Builder recipeOutputs(CategoryLocks v){ this.recipeOutputs = v != null ? v : CategoryLocks.EMPTY; return this; }

        public Builder lockedDimensions(List<ResourceLocation> v) {
            this.lockedDimensions = v != null ? v : new ArrayList<>(); return this;
        }
        public Builder interactions(List<InteractionLock> v) {
            this.interactions = v != null ? v : new ArrayList<>(); return this;
        }
        public Builder mobReplacements(List<MobReplacement> v) {
            this.mobReplacements = v != null ? v : new ArrayList<>(); return this;
        }
        public Builder regions(List<RegionLock> v) {
            this.regions = v != null ? v : new ArrayList<>(); return this;
        }
        public Builder structures(StructureRules v) {
            this.structures = v != null ? v : StructureRules.EMPTY; return this;
        }
        public Builder oreOverrides(List<OreOverride> v) {
            this.oreOverrides = v != null ? v : new ArrayList<>(); return this;
        }

        public Builder allowedUse(List<String> v)         { this.allowedUse         = v != null ? v : new ArrayList<>(); return this; }
        public Builder allowedPickup(List<String> v)      { this.allowedPickup      = v != null ? v : new ArrayList<>(); return this; }
        public Builder allowedHotbar(List<String> v)      { this.allowedHotbar      = v != null ? v : new ArrayList<>(); return this; }
        public Builder allowedMousePickup(List<String> v) { this.allowedMousePickup = v != null ? v : new ArrayList<>(); return this; }
        public Builder allowedInventory(List<String> v)   { this.allowedInventory   = v != null ? v : new ArrayList<>(); return this; }

        public Builder minecraftNamespace(boolean v) { this.minecraftNamespace = v; return this; }
        public Builder unlocks(UnlockGateLists v)    { this.unlocks = v != null ? v : UnlockGateLists.EMPTY; return this; }

        public LockDefinition build() {
            return new LockDefinition(this);
        }
    }
}
