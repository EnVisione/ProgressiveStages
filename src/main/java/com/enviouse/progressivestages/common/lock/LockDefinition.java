package com.enviouse.progressivestages.common.lock;

import com.enviouse.progressivestages.common.rehaul.ConditionNode;
import com.enviouse.progressivestages.common.rehaul.RuleLifetime;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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

    public static final int MAX_ENCHANT_SELECTION_WEIGHT = 1024;

    // ---- Unified categories (prefix-based) ----
    private final CategoryLocks items;
    private final CategoryLocks blocks;
    private final CategoryLocks fluids;
    private final CategoryLocks entities;      // attack/interaction (v1 feature, preserved)
    private final CategoryLocks enchants;
    /** v3.0 [enchants].max_levels — cap an enchantment at a level until the stage is owned. */
    private final List<EnchantCap> enchantCaps;
    private final List<EnchantSelectionWeight> enchantSelectionWeights;
    private final CategoryLocks crops;
    private final CategoryLocks screens;
    private final CategoryLocks loot;
    /** [trades].locked — villager / wandering-trader offers whose RESULT item matches are hidden + blocked. */
    private final CategoryLocks trades;
    /** v2.5 [professions].locked — villager professions whose trade GUI is blocked until the stage is owned. */
    private final CategoryLocks professions;
    /** v2.5 [advancements].locked — advancements hidden from the advancements screen until the stage is owned. */
    private final CategoryLocks advancements;
    /** v3.0 [beacon].locked — beacon effect ids (MobEffect ids) not applied to a player missing the stage. */
    private final CategoryLocks beacon;
    /** v3.0 [brewing].locked — potion ids that can't be brewed by a player missing the stage. */
    private final CategoryLocks brewing;
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
    /** Block display and drop overrides expanded against one accepted block registry snapshot. */
    private final List<BlockOverride> blockOverrides;

    // ---- Enforcement exceptions ----
    private final List<String> allowedUse;
    private final List<String> allowedPickup;
    private final List<String> allowedHotbar;
    private final List<String> allowedMousePickup;
    private final List<String> allowedInventory;

    // ---- v2.0.1: transitive crafting / automation toggles (per stage) ----
    /**
     * When true, this stage's gating extends to any crafting recipe whose ingredients
     * include an item this stage gates. Defaults to false to preserve prior behavior.
     */
    private final boolean blockCraftingWithLockedIngredients;
    /**
     * When true, this stage gates automated (non-player) crafting using the
     * nearest-player check (vanilla Crafter + ProgressiveStagesAPI hook). Defaults false.
     */
    private final boolean blockAutomatedCrafting;
    /** Search radius (blocks) for the nearest-player check on automated crafters. */
    private final int crafterCheckRadius;

    /**
     * v2.0.1: cube radius (chebyshev distance, blocks) around the player to spoof
     * ore-override target blocks. Default 8 — keeps the per-rescan cost bounded
     * (17^3 = 4913 positions to scan at radius 8, vs 32768 at radius 16).
     */
    private final int oreSpoofRadius;

    // ---- v2.3: per-stage [enforcement] category overrides (nullable per category; absent = inherit global). ----
    private final java.util.Map<EnforcementCategory, Boolean> enforcementOverrides;

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
        this.enchantCaps   = List.copyOf(b.enchantCaps);
        this.enchantSelectionWeights = List.copyOf(b.enchantSelectionWeights);
        this.crops         = b.crops;
        this.screens       = b.screens;
        this.loot          = b.loot;
        this.trades        = b.trades;
        this.professions   = b.professions;
        this.advancements  = b.advancements;
        this.beacon        = b.beacon;
        this.brewing       = b.brewing;
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
        this.blockOverrides   = List.copyOf(b.blockOverrides);
        this.allowedUse          = List.copyOf(b.allowedUse);
        this.allowedPickup       = List.copyOf(b.allowedPickup);
        this.allowedHotbar       = List.copyOf(b.allowedHotbar);
        this.allowedMousePickup  = List.copyOf(b.allowedMousePickup);
        this.allowedInventory    = List.copyOf(b.allowedInventory);
        this.blockCraftingWithLockedIngredients = b.blockCraftingWithLockedIngredients;
        this.blockAutomatedCrafting             = b.blockAutomatedCrafting;
        this.crafterCheckRadius                 = b.crafterCheckRadius;
        this.oreSpoofRadius                     = b.oreSpoofRadius;
        this.enforcementOverrides = b.enforcementOverrides.isEmpty()
            ? java.util.Map.of()
            : java.util.Collections.unmodifiableMap(new java.util.EnumMap<>(b.enforcementOverrides));
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
    public List<EnchantCap> enchantCaps() { return enchantCaps; }
    public List<EnchantSelectionWeight> enchantSelectionWeights() { return enchantSelectionWeights; }
    public CategoryLocks crops()        { return crops; }

    /** v3.0: an enchantment level cap — {@code enchant} is limited to {@code maxLevel} until the stage is owned. */
    public record EnchantCap(ResourceLocation enchant, int maxLevel) {}
    public record EnchantSelectionWeight(ResourceLocation enchant, int weight) {
        public EnchantSelectionWeight {
            Objects.requireNonNull(enchant, "enchant");
            if (weight < 0 || weight > MAX_ENCHANT_SELECTION_WEIGHT) {
                throw new IllegalArgumentException("Enchantment selection weight must be between 0 and "
                    + MAX_ENCHANT_SELECTION_WEIGHT + ".");
            }
        }
    }
    public CategoryLocks screens()      { return screens; }
    public CategoryLocks loot()         { return loot; }
    public CategoryLocks trades()       { return trades; }
    public CategoryLocks professions()  { return professions; }
    public CategoryLocks advancements() { return advancements; }
    public CategoryLocks beacon()       { return beacon; }
    public CategoryLocks brewing()      { return brewing; }
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
    public List<BlockOverride> blockOverrides()      { return blockOverrides; }
    /** Kept for source compatibility with the former exact ore override API. */
    public List<OreOverride> oreOverrides() {
        List<OreOverride> legacy = new ArrayList<>();
        for (BlockOverride override : blockOverrides) {
            if (override.targets().size() != 1 || override.targets().get(0).kind() != PrefixEntry.Kind.ID) continue;
            legacy.add(new OreOverride(override.targets().get(0).id(), override.displayAs(), override.dropAs()));
        }
        return List.copyOf(legacy);
    }

    public List<String> allowedUse()         { return allowedUse; }
    public List<String> allowedPickup()      { return allowedPickup; }
    public List<String> allowedHotbar()      { return allowedHotbar; }
    public List<String> allowedMousePickup() { return allowedMousePickup; }
    public List<String> allowedInventory()   { return allowedInventory; }

    /** v2.0.1: per-stage transitive crafting toggle. */
    public boolean blockCraftingWithLockedIngredients() { return blockCraftingWithLockedIngredients; }
    /** v2.0.1: per-stage automated-crafting toggle. */
    public boolean blockAutomatedCrafting() { return blockAutomatedCrafting; }
    /** v2.0.1: per-stage automated-crafting nearest-player search radius (blocks). */
    public int crafterCheckRadius() { return crafterCheckRadius; }

    /** v2.0.1: per-stage ore-spoof cube radius (chebyshev, blocks). */
    public int oreSpoofRadius() { return oreSpoofRadius; }

    /** v2.0: true if this stage gates the {@code minecraft:} namespace (shorthand for adding "minecraft" to mods). */
    public boolean minecraftNamespace() { return minecraftNamespace; }

    /** v2.0: per-stage carve-outs from this stage's own gating set. Empty by default. */
    public UnlockGateLists unlocks() { return unlocks; }

    /**
     * v2.3: per-stage enforcement-category overrides. Maps a category to the explicit boolean the
     * stage set in {@code [enforcement]}; categories the stage didn't mention are absent (inherit
     * the global default). Empty for stages with no overrides.
     */
    public java.util.Map<EnforcementCategory, Boolean> enforcementOverrides() { return enforcementOverrides; }

    public boolean isEmpty() {
        return items.isEmpty() && blocks.isEmpty() && fluids.isEmpty() && entities.isEmpty()
            && enchants.isEmpty() && enchantCaps.isEmpty() && enchantSelectionWeights.isEmpty()
            && crops.isEmpty() && screens.isEmpty() && loot.isEmpty() && trades.isEmpty()
            && professions.isEmpty() && advancements.isEmpty() && beacon.isEmpty() && brewing.isEmpty()
            && petsTaming.isEmpty() && petsBreeding.isEmpty() && petsCommanding.isEmpty() && mobSpawns.isEmpty()
            && recipeIds.isEmpty() && recipeOutputs.isEmpty()
            && lockedDimensions.isEmpty() && interactions.isEmpty()
            && mobReplacements.isEmpty() && regions.isEmpty() && blockOverrides.isEmpty()
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
        private final String targetKind;
        private final String effect;
        private final int priority;
        private final String description;
        private final ResourceLocation ruleId;
        private final RuleLifetime lifetime;
        private final ConditionNode condition;
        private final ConditionNode resetCondition;
        private final Map<String, Object> activationSettings;

        public InteractionLock(String type, String heldItem, String target, String description) {
            this(type, heldItem, target, "", "lock", 0, description);
        }

        public InteractionLock(String type, String heldItem, String target, String targetKind,
                               String effect, int priority, String description) {
            this(type, heldItem, target, targetKind, effect, priority, description, null,
                RuleLifetime.PERMANENT, new ConditionNode.Constant(true), null, Map.of());
        }

        public InteractionLock(String type, String heldItem, String target, String targetKind,
                               String effect, int priority, String description, ResourceLocation ruleId,
                               RuleLifetime lifetime, ConditionNode condition, ConditionNode resetCondition,
                               Map<String, Object> activationSettings) {
            this.type = type;
            this.heldItem = heldItem;
            this.target = target;
            this.targetKind = targetKind;
            this.effect = effect;
            this.priority = priority;
            this.description = description;
            this.ruleId = ruleId;
            this.lifetime = lifetime != null ? lifetime : RuleLifetime.PERMANENT;
            this.condition = condition != null ? condition : new ConditionNode.Constant(true);
            this.resetCondition = resetCondition;
            this.activationSettings = activationSettings == null ? Map.of() : Map.copyOf(activationSettings);
        }

        public String type()        { return type; }
        public String heldItem()    { return heldItem; }
        public String target()      { return target; }
        public String targetKind()  { return targetKind; }
        public String effect()      { return effect; }
        public int priority()       { return priority; }
        public ResourceLocation ruleId() { return ruleId; }
        public RuleLifetime lifetime() { return lifetime; }
        public ConditionNode condition() { return condition; }
        public ConditionNode resetCondition() { return resetCondition; }
        public Map<String, Object> activationSettings() { return activationSettings; }
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
            this.pos1 = pos1.clone();
            this.pos2 = pos2.clone();
            this.preventEntry = preventEntry;
            this.preventBlockBreak = preventBlockBreak;
            this.preventBlockPlace = preventBlockPlace;
            this.preventExplosions = preventExplosions;
            this.disableMobSpawning = disableMobSpawning;
        }

        public ResourceLocation dimension() { return dimension; }
        public int[] pos1()                 { return pos1.clone(); }
        public int[] pos2()                 { return pos2.clone(); }
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
            CategoryLocks.EMPTY, false, false, false, false, 0, false, null, null, 0);

        private final CategoryLocks lockedEntry;
        private final boolean preventBlockBreak;
        private final boolean preventBlockPlace;
        private final boolean preventExplosions;
        private final boolean disableMobSpawning;
        /** v2.5: extra buffer (blocks) added around the structure box for the entry check. */
        private final int entryPadding;
        /** 3.1: whether this stage contributes entry denial for its listed structures. */
        private final boolean entryAllowed;
        /** 3.1: optional rule priority, retained as null when it was not authored. */
        private final Integer priority;
        /** Existing category priority inherited by structure convenience rules. */
        private final Integer categoryPriority;
        /** Global fallback used by the shared priority cascade. */
        private final int globalPriority;

        public StructureRules(CategoryLocks lockedEntry, boolean preventBlockBreak,
                              boolean preventBlockPlace, boolean preventExplosions,
                              boolean disableMobSpawning, int entryPadding) {
            this(lockedEntry, preventBlockBreak, preventBlockPlace, preventExplosions,
                disableMobSpawning, entryPadding, false, null, null, 0);
        }

        public StructureRules(CategoryLocks lockedEntry, boolean preventBlockBreak,
                              boolean preventBlockPlace, boolean preventExplosions,
                              boolean disableMobSpawning, int entryPadding,
                              boolean entryAllowed, Integer priority) {
            this(lockedEntry, preventBlockBreak, preventBlockPlace, preventExplosions,
                disableMobSpawning, entryPadding, entryAllowed, priority, null, 0);
        }

        public StructureRules(CategoryLocks lockedEntry, boolean preventBlockBreak,
                              boolean preventBlockPlace, boolean preventExplosions,
                              boolean disableMobSpawning, int entryPadding,
                              boolean entryAllowed, Integer priority,
                              Integer categoryPriority, int globalPriority) {
            this.lockedEntry = lockedEntry;
            this.preventBlockBreak = preventBlockBreak;
            this.preventBlockPlace = preventBlockPlace;
            this.preventExplosions = preventExplosions;
            this.disableMobSpawning = disableMobSpawning;
            this.entryPadding = Math.max(0, entryPadding);
            this.entryAllowed = entryAllowed;
            this.priority = priority;
            this.categoryPriority = categoryPriority;
            this.globalPriority = globalPriority;
        }

        public CategoryLocks lockedEntry()  { return lockedEntry; }
        public boolean preventBlockBreak()  { return preventBlockBreak; }
        public boolean preventBlockPlace()  { return preventBlockPlace; }
        public boolean preventExplosions()  { return preventExplosions; }
        public boolean disableMobSpawning() { return disableMobSpawning; }
        public int entryPadding()           { return entryPadding; }
        public boolean entryAllowed()       { return entryAllowed; }
        public Integer priority()           { return priority; }
        public Integer categoryPriority()   { return categoryPriority; }
        public int globalPriority()         { return globalPriority; }

        public boolean isEmpty() {
            return lockedEntry.isEmpty() && !preventBlockBreak && !preventBlockPlace
                && !preventExplosions && !disableMobSpawning && !entryAllowed && priority == null;
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

    /** A source attributed block display and drop override. */
    public static class BlockOverride {
        private final List<PrefixEntry> targets;
        private final ResourceLocation displayAs;
        private final ResourceLocation dropAs;
        private final Integer priority;
        private final String sourceTable;
        private final String sourceField;

        public BlockOverride(List<PrefixEntry> targets, ResourceLocation displayAs,
                             ResourceLocation dropAs, Integer priority, String sourceTable) {
            this(targets, displayAs, dropAs, priority, sourceTable,
                (sourceTable == null ? "blocks.overrides" : sourceTable) + ".targets");
        }

        public BlockOverride(List<PrefixEntry> targets, ResourceLocation displayAs,
                             ResourceLocation dropAs, Integer priority, String sourceTable, String sourceField) {
            this.targets = targets == null ? List.of() : List.copyOf(targets);
            this.displayAs = displayAs;
            this.dropAs = dropAs;
            this.priority = priority;
            this.sourceTable = sourceTable == null ? "blocks.overrides" : sourceTable;
            this.sourceField = sourceField == null ? this.sourceTable + ".targets" : sourceField;
        }

        public List<PrefixEntry> targets()  { return targets; }
        public ResourceLocation displayAs() { return displayAs; }
        public ResourceLocation dropAs()    { return dropAs; }
        public Integer priority()           { return priority; }
        public String sourceTable()         { return sourceTable; }
        public String sourceField()         { return sourceField; }
    }

    /** Compatibility view and constructor for the original scalar ore override API. */
    public static final class OreOverride extends BlockOverride {
        public OreOverride(ResourceLocation target, ResourceLocation displayAs, ResourceLocation dropAs) {
            super(target == null ? List.of() : List.of(PrefixEntry.parse(target.toString())),
                displayAs, dropAs, null, "ores.overrides");
        }

        public ResourceLocation target() {
            return targets().isEmpty() ? null : targets().get(0).id();
        }
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
        private List<EnchantCap> enchantCaps = new ArrayList<>();
        private List<EnchantSelectionWeight> enchantSelectionWeights = new ArrayList<>();
        private CategoryLocks crops = CategoryLocks.EMPTY;
        private CategoryLocks screens = CategoryLocks.EMPTY;
        private CategoryLocks loot = CategoryLocks.EMPTY;
        private CategoryLocks trades = CategoryLocks.EMPTY;
        private CategoryLocks professions = CategoryLocks.EMPTY;
        private CategoryLocks advancements = CategoryLocks.EMPTY;
        private CategoryLocks beacon = CategoryLocks.EMPTY;
        private CategoryLocks brewing = CategoryLocks.EMPTY;
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
        private List<BlockOverride> blockOverrides = new ArrayList<>();

        private List<String> allowedUse = new ArrayList<>();
        private List<String> allowedPickup = new ArrayList<>();
        private List<String> allowedHotbar = new ArrayList<>();
        private List<String> allowedMousePickup = new ArrayList<>();
        private List<String> allowedInventory = new ArrayList<>();

        private boolean blockCraftingWithLockedIngredients = false;
        private boolean blockAutomatedCrafting = false;
        private int crafterCheckRadius = 32;
        private int oreSpoofRadius = 8;

        private java.util.Map<EnforcementCategory, Boolean> enforcementOverrides = new java.util.EnumMap<>(EnforcementCategory.class);
        private boolean minecraftNamespace = false;
        private UnlockGateLists unlocks = UnlockGateLists.EMPTY;

        public Builder items(CategoryLocks v)        { this.items = v != null ? v : CategoryLocks.EMPTY; return this; }
        public Builder blocks(CategoryLocks v)       { this.blocks = v != null ? v : CategoryLocks.EMPTY; return this; }
        public Builder fluids(CategoryLocks v)       { this.fluids = v != null ? v : CategoryLocks.EMPTY; return this; }
        public Builder entities(CategoryLocks v)     { this.entities = v != null ? v : CategoryLocks.EMPTY; return this; }
        public Builder enchants(CategoryLocks v)     { this.enchants = v != null ? v : CategoryLocks.EMPTY; return this; }
        public Builder enchantCaps(List<EnchantCap> v) { this.enchantCaps = v != null ? v : new ArrayList<>(); return this; }
        public Builder enchantSelectionWeights(List<EnchantSelectionWeight> v) {
            this.enchantSelectionWeights = v != null ? v : new ArrayList<>();
            return this;
        }
        public Builder crops(CategoryLocks v)        { this.crops = v != null ? v : CategoryLocks.EMPTY; return this; }
        public Builder screens(CategoryLocks v)      { this.screens = v != null ? v : CategoryLocks.EMPTY; return this; }
        public Builder loot(CategoryLocks v)         { this.loot = v != null ? v : CategoryLocks.EMPTY; return this; }
        public Builder trades(CategoryLocks v)       { this.trades = v != null ? v : CategoryLocks.EMPTY; return this; }
        public Builder professions(CategoryLocks v)  { this.professions = v != null ? v : CategoryLocks.EMPTY; return this; }
        public Builder advancements(CategoryLocks v) { this.advancements = v != null ? v : CategoryLocks.EMPTY; return this; }
        public Builder beacon(CategoryLocks v)       { this.beacon = v != null ? v : CategoryLocks.EMPTY; return this; }
        public Builder brewing(CategoryLocks v)      { this.brewing = v != null ? v : CategoryLocks.EMPTY; return this; }
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
        public Builder blockOverrides(List<BlockOverride> v) {
            this.blockOverrides = v != null ? v : new ArrayList<>(); return this;
        }
        public Builder oreOverrides(List<OreOverride> v) {
            this.blockOverrides = v == null ? new ArrayList<>() : new ArrayList<>(v); return this;
        }

        public Builder allowedUse(List<String> v)         { this.allowedUse         = v != null ? v : new ArrayList<>(); return this; }
        public Builder allowedPickup(List<String> v)      { this.allowedPickup      = v != null ? v : new ArrayList<>(); return this; }
        public Builder allowedHotbar(List<String> v)      { this.allowedHotbar      = v != null ? v : new ArrayList<>(); return this; }
        public Builder allowedMousePickup(List<String> v) { this.allowedMousePickup = v != null ? v : new ArrayList<>(); return this; }
        public Builder allowedInventory(List<String> v)   { this.allowedInventory   = v != null ? v : new ArrayList<>(); return this; }

        public Builder blockCraftingWithLockedIngredients(boolean v) { this.blockCraftingWithLockedIngredients = v; return this; }
        public Builder blockAutomatedCrafting(boolean v) { this.blockAutomatedCrafting = v; return this; }
        public Builder crafterCheckRadius(int v) {
            if (v < 8) v = 8;
            if (v > 256) v = 256;
            this.crafterCheckRadius = v;
            return this;
        }
        public Builder oreSpoofRadius(int v) {
            if (v < 2) v = 2;
            if (v > 32) v = 32;
            this.oreSpoofRadius = v;
            return this;
        }

        public Builder minecraftNamespace(boolean v) { this.minecraftNamespace = v; return this; }
        public Builder unlocks(UnlockGateLists v)    { this.unlocks = v != null ? v : UnlockGateLists.EMPTY; return this; }

        /** v2.3: set a single per-stage enforcement override (null value clears it). */
        public Builder enforcementOverride(EnforcementCategory cat, Boolean value) {
            if (cat != null) {
                if (value == null) this.enforcementOverrides.remove(cat);
                else this.enforcementOverrides.put(cat, value);
            }
            return this;
        }

        public LockDefinition build() {
            return new LockDefinition(this);
        }
    }
}
