package com.enviouse.progressivestages.common.lock;

import com.enviouse.progressivestages.common.api.StageId;

import java.util.Objects;
import java.util.Optional;

/**
 * Represents a single lock entry (target -> required stage)
 */
public class LockEntry {

    private final LockType type;
    private final String target;
    private final StageId requiredStage;
    private final String description;

    // For interaction locks
    private final String heldItem;
    private final String targetBlock;
    private final String interactionType;

    private LockEntry(Builder builder) {
        this.type = builder.type;
        this.target = builder.target;
        this.requiredStage = builder.requiredStage;
        this.description = builder.description;
        this.heldItem = builder.heldItem;
        this.targetBlock = builder.targetBlock;
        this.interactionType = builder.interactionType;
    }

    public LockType getType() {
        return type;
    }

    public String getTarget() {
        return target;
    }

    public StageId getRequiredStage() {
        return requiredStage;
    }

    public Optional<String> getDescription() {
        return Optional.ofNullable(description);
    }

    public Optional<String> getHeldItem() {
        return Optional.ofNullable(heldItem);
    }

    public Optional<String> getTargetBlock() {
        return Optional.ofNullable(targetBlock);
    }

    public Optional<String> getInteractionType() {
        return Optional.ofNullable(interactionType);
    }

    /**
     * Check if the target is a tag (starts with #)
     */
    public boolean isTag() {
        return target != null && target.startsWith("#");
    }

    /**
     * Get the tag without the # prefix
     */
    public String getTagTarget() {
        if (isTag()) {
            return target.substring(1);
        }
        return target;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LockEntry lockEntry = (LockEntry) o;
        return type == lockEntry.type &&
            Objects.equals(target, lockEntry.target) &&
            Objects.equals(requiredStage, lockEntry.requiredStage);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, target, requiredStage);
    }

    @Override
    public String toString() {
        return "LockEntry{" +
            "type=" + type +
            ", target='" + target + '\'' +
            ", requiredStage=" + requiredStage +
            '}';
    }

    public static Builder builder(LockType type, StageId requiredStage) {
        return new Builder(type, requiredStage);
    }

    public static LockEntry item(String itemId, StageId stage) {
        return builder(LockType.ITEM, stage).target(itemId).build();
    }

    public static LockEntry itemTag(String tag, StageId stage) {
        return builder(LockType.ITEM_TAG, stage).target(tag).build();
    }

    public static LockEntry recipe(String recipeId, StageId stage) {
        return builder(LockType.RECIPE, stage).target(recipeId).build();
    }

    public static LockEntry recipeTag(String tag, StageId stage) {
        return builder(LockType.RECIPE_TAG, stage).target(tag).build();
    }

    public static LockEntry block(String blockId, StageId stage) {
        return builder(LockType.BLOCK, stage).target(blockId).build();
    }

    public static LockEntry blockTag(String tag, StageId stage) {
        return builder(LockType.BLOCK_TAG, stage).target(tag).build();
    }

    public static LockEntry dimension(String dimensionId, StageId stage) {
        return builder(LockType.DIMENSION, stage).target(dimensionId).build();
    }

    public static LockEntry mod(String modId, StageId stage) {
        return builder(LockType.MOD, stage).target(modId).build();
    }

    public static LockEntry name(String pattern, StageId stage) {
        return builder(LockType.NAME, stage).target(pattern).build();
    }

    public static class Builder {
        private final LockType type;
        private final StageId requiredStage;
        private String target;
        private String description;
        private String heldItem;
        private String targetBlock;
        private String interactionType;

        private Builder(LockType type, StageId requiredStage) {
            this.type = type;
            this.requiredStage = requiredStage;
        }

        public Builder target(String target) {
            this.target = target;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder heldItem(String heldItem) {
            this.heldItem = heldItem;
            return this;
        }

        public Builder targetBlock(String targetBlock) {
            this.targetBlock = targetBlock;
            return this;
        }

        public Builder interactionType(String interactionType) {
            this.interactionType = interactionType;
            return this;
        }

        public LockEntry build() {
            return new LockEntry(this);
        }
    }
}
