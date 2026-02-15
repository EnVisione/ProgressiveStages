package com.enviouse.progressivestages.common.lock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Contains all lock definitions parsed from a stage file.
 *
 * <p>v1.3 changes: Added unlockedItems for whitelist exceptions.
 */
public class LockDefinition {

    private final List<String> items;
    private final List<String> itemTags;
    private final List<String> recipes;
    private final List<String> recipeTags;
    private final List<String> blocks;
    private final List<String> blockTags;
    private final List<String> dimensions;
    private final List<String> mods;
    private final List<String> names;
    private final List<InteractionLock> interactions;
    private final List<String> unlockedItems;

    private LockDefinition(Builder builder) {
        this.items = Collections.unmodifiableList(new ArrayList<>(builder.items));
        this.itemTags = Collections.unmodifiableList(new ArrayList<>(builder.itemTags));
        this.recipes = Collections.unmodifiableList(new ArrayList<>(builder.recipes));
        this.recipeTags = Collections.unmodifiableList(new ArrayList<>(builder.recipeTags));
        this.blocks = Collections.unmodifiableList(new ArrayList<>(builder.blocks));
        this.blockTags = Collections.unmodifiableList(new ArrayList<>(builder.blockTags));
        this.dimensions = Collections.unmodifiableList(new ArrayList<>(builder.dimensions));
        this.mods = Collections.unmodifiableList(new ArrayList<>(builder.mods));
        this.names = Collections.unmodifiableList(new ArrayList<>(builder.names));
        this.interactions = Collections.unmodifiableList(new ArrayList<>(builder.interactions));
        this.unlockedItems = Collections.unmodifiableList(new ArrayList<>(builder.unlockedItems));
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

    public boolean isEmpty() {
        return items.isEmpty() && itemTags.isEmpty() && recipes.isEmpty() &&
            recipeTags.isEmpty() && blocks.isEmpty() && blockTags.isEmpty() &&
            dimensions.isEmpty() && mods.isEmpty() && names.isEmpty() &&
            interactions.isEmpty();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private List<String> items = new ArrayList<>();
        private List<String> itemTags = new ArrayList<>();
        private List<String> recipes = new ArrayList<>();
        private List<String> recipeTags = new ArrayList<>();
        private List<String> blocks = new ArrayList<>();
        private List<String> blockTags = new ArrayList<>();
        private List<String> dimensions = new ArrayList<>();
        private List<String> mods = new ArrayList<>();
        private List<String> names = new ArrayList<>();
        private List<InteractionLock> interactions = new ArrayList<>();
        private List<String> unlockedItems = new ArrayList<>();

        public Builder items(List<String> items) {
            this.items = items != null ? items : new ArrayList<>();
            return this;
        }

        public Builder itemTags(List<String> itemTags) {
            this.itemTags = itemTags != null ? itemTags : new ArrayList<>();
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
