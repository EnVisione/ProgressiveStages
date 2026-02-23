package com.enviouse.progressivestages.common.lock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Contains all lock definitions parsed from a stage file.
 *
 * <p>v1.3 changes: Added unlockedItems for whitelist exceptions.
 * <p>v1.4 changes: Added unlockedBlocks, unlockedFluids for whitelist exceptions.
 *                  Added fluids, fluidTags, fluidMods for fluid locking.
 */
public class LockDefinition {

    private final List<String> items;
    private final List<String> itemTags;
    private final List<String> itemMods;
    private final List<String> recipes;
    private final List<String> recipeTags;
    private final List<String> blocks;
    private final List<String> blockTags;
    private final List<String> blockMods;
    private final List<String> fluids;
    private final List<String> fluidTags;
    private final List<String> fluidMods;
    private final List<String> dimensions;
    private final List<String> mods;
    private final List<String> names;
    private final List<InteractionLock> interactions;
    private final List<String> unlockedItems;
    private final List<String> unlockedBlocks;
    private final List<String> unlockedEntities;
    private final List<String> unlockedFluids;
    private final List<String> entities;
    private final List<String> entityTags;
    private final List<String> entityMods;

    private LockDefinition(Builder builder) {
        this.items = Collections.unmodifiableList(new ArrayList<>(builder.items));
        this.itemTags = Collections.unmodifiableList(new ArrayList<>(builder.itemTags));
        this.itemMods = Collections.unmodifiableList(new ArrayList<>(builder.itemMods));
        this.recipes = Collections.unmodifiableList(new ArrayList<>(builder.recipes));
        this.recipeTags = Collections.unmodifiableList(new ArrayList<>(builder.recipeTags));
        this.blocks = Collections.unmodifiableList(new ArrayList<>(builder.blocks));
        this.blockTags = Collections.unmodifiableList(new ArrayList<>(builder.blockTags));
        this.blockMods = Collections.unmodifiableList(new ArrayList<>(builder.blockMods));
        this.fluids = Collections.unmodifiableList(new ArrayList<>(builder.fluids));
        this.fluidTags = Collections.unmodifiableList(new ArrayList<>(builder.fluidTags));
        this.fluidMods = Collections.unmodifiableList(new ArrayList<>(builder.fluidMods));
        this.dimensions = Collections.unmodifiableList(new ArrayList<>(builder.dimensions));
        this.mods = Collections.unmodifiableList(new ArrayList<>(builder.mods));
        this.names = Collections.unmodifiableList(new ArrayList<>(builder.names));
        this.interactions = Collections.unmodifiableList(new ArrayList<>(builder.interactions));
        this.unlockedItems = Collections.unmodifiableList(new ArrayList<>(builder.unlockedItems));
        this.unlockedBlocks = Collections.unmodifiableList(new ArrayList<>(builder.unlockedBlocks));
        this.unlockedEntities = Collections.unmodifiableList(new ArrayList<>(builder.unlockedEntities));
        this.unlockedFluids = Collections.unmodifiableList(new ArrayList<>(builder.unlockedFluids));
        this.entities = Collections.unmodifiableList(new ArrayList<>(builder.entities));
        this.entityTags = Collections.unmodifiableList(new ArrayList<>(builder.entityTags));
        this.entityMods = Collections.unmodifiableList(new ArrayList<>(builder.entityMods));
    }

    public static LockDefinition empty() {
        return new Builder().build();
    }

    public List<String> getItems() {
        return items;
    }

    public List<String> getItemTags() {
        return itemTags;
    }

    /**
     * Get mod IDs whose items are locked.
     * Unlike mods, this only affects items, not blocks or entities.
     */
    public List<String> getItemMods() {
        return itemMods;
    }

    public List<String> getRecipes() {
        return recipes;
    }

    public List<String> getRecipeTags() {
        return recipeTags;
    }

    public List<String> getBlocks() {
        return blocks;
    }

    public List<String> getBlockTags() {
        return blockTags;
    }

    /**
     * Get mod IDs whose blocks are locked.
     * Unlike mods, this only affects blocks, not items or entities.
     */
    public List<String> getBlockMods() {
        return blockMods;
    }

    /**
     * Get specific fluid IDs that are locked (hidden from EMI/JEI).
     */
    public List<String> getFluids() {
        return fluids;
    }

    /**
     * Get fluid tags that are locked (hidden from EMI/JEI).
     */
    public List<String> getFluidTags() {
        return fluidTags;
    }

    /**
     * Get mod IDs whose fluids are locked (hidden from EMI/JEI).
     */
    public List<String> getFluidMods() {
        return fluidMods;
    }

    public List<String> getDimensions() {
        return dimensions;
    }

    public List<String> getMods() {
        return mods;
    }

    public List<String> getNames() {
        return names;
    }

    public List<InteractionLock> getInteractions() {
        return interactions;
    }

    /**
     * Get items that are always unlocked (whitelist exceptions).
     * These items bypass mod locks, name patterns, and tag locks from this stage.
     */
    public List<String> getUnlockedItems() {
        return unlockedItems;
    }

    /**
     * Get blocks that are always unlocked (whitelist exceptions).
     * These blocks bypass mod locks, name patterns, and tag locks from this stage.
     */
    public List<String> getUnlockedBlocks() {
        return unlockedBlocks;
    }

    /**
     * Get entities that are always unlocked (whitelist exceptions).
     * These entities bypass entity mod locks, entity tags, and name locks.
     * Use case: Lock entire mod but allow attacking specific entities.
     */
    public List<String> getUnlockedEntities() {
        return unlockedEntities;
    }

    /**
     * Get fluids that are always unlocked (whitelist exceptions).
     * These fluids bypass mod locks for EMI/JEI visibility.
     * Use case: Lock mods = ["mekanism"] but allow a specific fluid to show in EMI/JEI.
     */
    public List<String> getUnlockedFluids() {
        return unlockedFluids;
    }

    /**
     * Get entity types that are locked (attack/interaction blocked).
     */
    public List<String> getEntities() {
        return entities;
    }

    /**
     * Get entity tags that are locked.
     */
    public List<String> getEntityTags() {
        return entityTags;
    }

    /**
     * Get mod IDs whose entities are locked (attack/interaction blocked).
     */
    public List<String> getEntityMods() {
        return entityMods;
    }

    public boolean isEmpty() {
        return items.isEmpty() && itemTags.isEmpty() && itemMods.isEmpty() &&
            recipes.isEmpty() && recipeTags.isEmpty() &&
            blocks.isEmpty() && blockTags.isEmpty() && blockMods.isEmpty() &&
            fluids.isEmpty() && fluidTags.isEmpty() && fluidMods.isEmpty() &&
            dimensions.isEmpty() && mods.isEmpty() && names.isEmpty() &&
            interactions.isEmpty() && entities.isEmpty() && entityTags.isEmpty() &&
            entityMods.isEmpty();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private List<String> items = new ArrayList<>();
        private List<String> itemTags = new ArrayList<>();
        private List<String> itemMods = new ArrayList<>();
        private List<String> recipes = new ArrayList<>();
        private List<String> recipeTags = new ArrayList<>();
        private List<String> blocks = new ArrayList<>();
        private List<String> blockTags = new ArrayList<>();
        private List<String> blockMods = new ArrayList<>();
        private List<String> fluids = new ArrayList<>();
        private List<String> fluidTags = new ArrayList<>();
        private List<String> fluidMods = new ArrayList<>();
        private List<String> dimensions = new ArrayList<>();
        private List<String> mods = new ArrayList<>();
        private List<String> names = new ArrayList<>();
        private List<InteractionLock> interactions = new ArrayList<>();
        private List<String> unlockedItems = new ArrayList<>();
        private List<String> unlockedBlocks = new ArrayList<>();
        private List<String> unlockedEntities = new ArrayList<>();
        private List<String> unlockedFluids = new ArrayList<>();
        private List<String> entities = new ArrayList<>();
        private List<String> entityTags = new ArrayList<>();
        private List<String> entityMods = new ArrayList<>();

        public Builder items(List<String> items) {
            this.items = items != null ? items : new ArrayList<>();
            return this;
        }

        public Builder itemTags(List<String> itemTags) {
            this.itemTags = itemTags != null ? itemTags : new ArrayList<>();
            return this;
        }

        public Builder itemMods(List<String> itemMods) {
            this.itemMods = itemMods != null ? itemMods : new ArrayList<>();
            return this;
        }

        public Builder recipes(List<String> recipes) {
            this.recipes = recipes != null ? recipes : new ArrayList<>();
            return this;
        }

        public Builder recipeTags(List<String> recipeTags) {
            this.recipeTags = recipeTags != null ? recipeTags : new ArrayList<>();
            return this;
        }

        public Builder blocks(List<String> blocks) {
            this.blocks = blocks != null ? blocks : new ArrayList<>();
            return this;
        }

        public Builder blockTags(List<String> blockTags) {
            this.blockTags = blockTags != null ? blockTags : new ArrayList<>();
            return this;
        }

        public Builder blockMods(List<String> blockMods) {
            this.blockMods = blockMods != null ? blockMods : new ArrayList<>();
            return this;
        }

        public Builder fluids(List<String> fluids) {
            this.fluids = fluids != null ? fluids : new ArrayList<>();
            return this;
        }

        public Builder fluidTags(List<String> fluidTags) {
            this.fluidTags = fluidTags != null ? fluidTags : new ArrayList<>();
            return this;
        }

        public Builder fluidMods(List<String> fluidMods) {
            this.fluidMods = fluidMods != null ? fluidMods : new ArrayList<>();
            return this;
        }

        public Builder dimensions(List<String> dimensions) {
            this.dimensions = dimensions != null ? dimensions : new ArrayList<>();
            return this;
        }

        public Builder mods(List<String> mods) {
            this.mods = mods != null ? mods : new ArrayList<>();
            return this;
        }

        public Builder names(List<String> names) {
            this.names = names != null ? names : new ArrayList<>();
            return this;
        }

        public Builder interactions(List<InteractionLock> interactions) {
            this.interactions = interactions != null ? interactions : new ArrayList<>();
            return this;
        }

        public Builder unlockedItems(List<String> unlockedItems) {
            this.unlockedItems = unlockedItems != null ? unlockedItems : new ArrayList<>();
            return this;
        }

        public Builder unlockedBlocks(List<String> unlockedBlocks) {
            this.unlockedBlocks = unlockedBlocks != null ? unlockedBlocks : new ArrayList<>();
            return this;
        }

        public Builder unlockedEntities(List<String> unlockedEntities) {
            this.unlockedEntities = unlockedEntities != null ? unlockedEntities : new ArrayList<>();
            return this;
        }

        public Builder unlockedFluids(List<String> unlockedFluids) {
            this.unlockedFluids = unlockedFluids != null ? unlockedFluids : new ArrayList<>();
            return this;
        }

        public Builder addItem(String item) {
            this.items.add(item);
            return this;
        }

        public Builder addItemTag(String tag) {
            this.itemTags.add(tag);
            return this;
        }

        public Builder addRecipe(String recipe) {
            this.recipes.add(recipe);
            return this;
        }

        public Builder addBlock(String block) {
            this.blocks.add(block);
            return this;
        }

        public Builder addDimension(String dimension) {
            this.dimensions.add(dimension);
            return this;
        }

        public Builder addMod(String mod) {
            this.mods.add(mod);
            return this;
        }

        public Builder addName(String name) {
            this.names.add(name);
            return this;
        }

        public Builder addInteraction(InteractionLock interaction) {
            this.interactions.add(interaction);
            return this;
        }

        public Builder entities(List<String> entities) {
            this.entities = entities != null ? entities : new ArrayList<>();
            return this;
        }

        public Builder entityTags(List<String> entityTags) {
            this.entityTags = entityTags != null ? entityTags : new ArrayList<>();
            return this;
        }

        public Builder addEntity(String entity) {
            this.entities.add(entity);
            return this;
        }

        public Builder addEntityTag(String tag) {
            this.entityTags.add(tag);
            return this;
        }

        public Builder entityMods(List<String> entityMods) {
            this.entityMods = entityMods != null ? entityMods : new ArrayList<>();
            return this;
        }

        public Builder addEntityMod(String mod) {
            this.entityMods.add(mod);
            return this;
        }

        public LockDefinition build() {
            return new LockDefinition(this);
        }
    }

    /**
     * Represents an interaction lock (e.g., item on block)
     */
    public static class InteractionLock {
        private final String type;
        private final String heldItem;
        private final String targetBlock;
        private final String description;

        public InteractionLock(String type, String heldItem, String targetBlock, String description) {
            this.type = type;
            this.heldItem = heldItem;
            this.targetBlock = targetBlock;
            this.description = description;
        }

        public String getType() {
            return type;
        }

        public String getHeldItem() {
            return heldItem;
        }

        public String getTargetBlock() {
            return targetBlock;
        }

        public String getDescription() {
            return description;
        }
    }
}
