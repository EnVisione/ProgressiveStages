package com.enviouse.progressivestages.common.util;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.Optional;

/**
 * General utility methods for stages
 */
public final class StageUtil {

    private StageUtil() {
        // Prevent instantiation
    }

    /**
     * Get the mod ID from an item
     */
    public static String getModId(Item item) {
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(item);
        return key != null ? key.getNamespace() : "minecraft";
    }

    /**
     * Get the mod ID from an ItemStack
     */
    public static String getModId(ItemStack stack) {
        if (stack.isEmpty()) {
            return "minecraft";
        }
        return getModId(stack.getItem());
    }

    /**
     * Get the registry name of an item as a string
     */
    public static String getItemId(Item item) {
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(item);
        return key != null ? key.toString() : "minecraft:air";
    }

    /**
     * Get the registry name of an item from a stack
     */
    public static String getItemId(ItemStack stack) {
        if (stack.isEmpty()) {
            return "minecraft:air";
        }
        return getItemId(stack.getItem());
    }

    /**
     * Parse a ResourceLocation from a string, with error handling
     */
    public static Optional<ResourceLocation> parseResourceLocation(String str) {
        try {
            return Optional.of(ResourceLocation.parse(str));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Get an Item from a registry string
     */
    public static Optional<Item> getItem(String id) {
        return parseResourceLocation(id)
            .map(BuiltInRegistries.ITEM::get)
            .filter(item -> item != null && !BuiltInRegistries.ITEM.getKey(item).getPath().equals("air"));
    }

    /**
     * Check if a string matches a name pattern (case-insensitive substring)
     */
    public static boolean matchesName(String itemId, String pattern) {
        return itemId.toLowerCase().contains(pattern.toLowerCase());
    }

    /**
     * Check if a string matches a name pattern against display name
     */
    public static boolean matchesDisplayName(ItemStack stack, String pattern) {
        String displayName = stack.getHoverName().getString().toLowerCase();
        return displayName.contains(pattern.toLowerCase());
    }
}
