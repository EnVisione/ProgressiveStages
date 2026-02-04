package com.enviouse.progressivestages.common.config;

import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.lock.LockDefinition;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

/**
 * Represents a parsed stage definition from a TOML file
 */
public class StageDefinition {

    private final StageId id;
    private final String displayName;
    private final String description;
    private final int order;
    private final ResourceLocation icon;
    private final String unlockMessage;
    private final LockDefinition locks;

    private StageDefinition(Builder builder) {
        this.id = builder.id;
        this.displayName = builder.displayName;
        this.description = builder.description;
        this.order = builder.order;
        this.icon = builder.icon;
        this.unlockMessage = builder.unlockMessage;
        this.locks = builder.locks != null ? builder.locks : LockDefinition.empty();
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

    public int getOrder() {
        return order;
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

    @Override
    public String toString() {
        return "StageDefinition{" +
            "id=" + id +
            ", displayName='" + displayName + '\'' +
            ", order=" + order +
            '}';
    }

    public static Builder builder(StageId id) {
        return new Builder(id);
    }

    public static class Builder {
        private final StageId id;
        private String displayName;
        private String description = "";
        private int order = 0;
        private ResourceLocation icon;
        private String unlockMessage;
        private LockDefinition locks;

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

        public Builder order(int order) {
            this.order = order;
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

        public StageDefinition build() {
            return new StageDefinition(this);
        }
    }
}
