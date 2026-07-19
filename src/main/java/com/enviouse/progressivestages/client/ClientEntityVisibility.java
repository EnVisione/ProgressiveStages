package com.enviouse.progressivestages.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

import java.util.Set;

/** per player server resolved entity types that the local client must not reveal. */
public final class ClientEntityVisibility {

    private static volatile Set<ResourceLocation> concealed = Set.of();

    private ClientEntityVisibility() {}

    public static void setConcealed(Set<ResourceLocation> entityTypes) {
        concealed = entityTypes == null ? Set.of() : Set.copyOf(entityTypes);
    }

    public static boolean isConcealed(Entity entity) {
        if (entity == null || entity == Minecraft.getInstance().player) return false;
        return isConcealed(entity.getType());
    }

    public static boolean isConcealed(EntityType<?> type) {
        if (type == null || ClientLockCache.isCreativeBypass()) return false;
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        return id != null && concealed.contains(id);
    }

    public static Set<ResourceLocation> snapshot() {
        return concealed;
    }

    public static void clear() {
        concealed = Set.of();
    }
}
