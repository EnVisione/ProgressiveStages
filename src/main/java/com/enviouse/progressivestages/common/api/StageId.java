package com.enviouse.progressivestages.common.api;

import com.enviouse.progressivestages.common.util.Constants;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/**
 * Wrapper for stage identifiers using ResourceLocation
 * Format: namespace:stage_name (e.g., progressivestages:diamond_age)
 */
public final class StageId implements Comparable<StageId> {

    private final ResourceLocation id;

    public StageId(ResourceLocation id) {
        this.id = Objects.requireNonNull(id, "Stage ID cannot be null");
    }

    public StageId(String namespace, String path) {
        this(ResourceLocation.fromNamespaceAndPath(namespace, path));
    }

    public StageId(String id) {
        this(parseId(id));
    }

    private static ResourceLocation parseId(String id) {
        if (id.contains(":")) {
            return ResourceLocation.parse(id);
        } else {
            // Default to our mod namespace if none specified
            return ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, id);
        }
    }

    /**
     * Create a StageId with the progressivestages namespace
     */
    public static StageId of(String name) {
        return new StageId(Constants.MOD_ID, name);
    }

    /**
     * Create a StageId from a full ResourceLocation string
     */
    public static StageId parse(String id) {
        return new StageId(id);
    }

    /**
     * Create a StageId from a ResourceLocation
     */
    public static StageId fromResourceLocation(ResourceLocation location) {
        return new StageId(location);
    }

    public ResourceLocation getResourceLocation() {
        return id;
    }

    public String getNamespace() {
        return id.getNamespace();
    }

    public String getPath() {
        return id.getPath();
    }

    @Override
    public String toString() {
        return id.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StageId stageId = (StageId) o;
        return Objects.equals(id, stageId.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public int compareTo(StageId other) {
        return this.id.compareTo(other.id);
    }
}
