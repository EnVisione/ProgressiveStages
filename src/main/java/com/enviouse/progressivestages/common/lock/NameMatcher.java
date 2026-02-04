package com.enviouse.progressivestages.common.lock;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

/**
 * Utility class for case-insensitive name matching.
 * Used for the "names" lock type which matches against registry IDs.
 */
public final class NameMatcher {

    private NameMatcher() {
        // Prevent instantiation
    }

    /**
     * Check if an item ID matches a pattern (case-insensitive substring)
     * @param itemId The full registry ID (e.g., "minecraft:diamond_pickaxe")
     * @param pattern The pattern to match (e.g., "diamond")
     * @return true if pattern is found anywhere in the item ID
     */
    public static boolean matches(String itemId, String pattern) {
        if (itemId == null || pattern == null) {
            return false;
        }
        return itemId.toLowerCase().contains(pattern.toLowerCase());
    }

    /**
     * Check if an item matches a pattern
     */
    public static boolean matches(Item item, String pattern) {
        ResourceLocation rl = BuiltInRegistries.ITEM.getKey(item);
        if (rl == null) {
            return false;
        }
        return matches(rl.toString(), pattern);
    }

    /**
     * Check if an ItemStack matches a pattern
     */
    public static boolean matches(ItemStack stack, String pattern) {
        if (stack.isEmpty()) {
            return false;
        }
        return matches(stack.getItem(), pattern);
    }

    /**
     * Check if a block matches a pattern
     */
    public static boolean matches(Block block, String pattern) {
        ResourceLocation rl = BuiltInRegistries.BLOCK.getKey(block);
        if (rl == null) {
            return false;
        }
        return matches(rl.toString(), pattern);
    }

    /**
     * Check if an item's display name matches a pattern
     */
    public static boolean matchesDisplayName(ItemStack stack, String pattern) {
        if (stack.isEmpty()) {
            return false;
        }
        String displayName = stack.getHoverName().getString();
        return displayName.toLowerCase().contains(pattern.toLowerCase());
    }

    /**
     * Get the mod ID from an item
     */
    public static String getModId(Item item) {
        ResourceLocation rl = BuiltInRegistries.ITEM.getKey(item);
        return rl != null ? rl.getNamespace() : "minecraft";
    }

    /**
     * Get the mod ID from a block
     */
    public static String getModId(Block block) {
        ResourceLocation rl = BuiltInRegistries.BLOCK.getKey(block);
        return rl != null ? rl.getNamespace() : "minecraft";
    }

    /**
     * Check if an item is from a specific mod
     */
    public static boolean isFromMod(Item item, String modId) {
        return getModId(item).equalsIgnoreCase(modId);
    }

    /**
     * Check if a block is from a specific mod
     */
    public static boolean isFromMod(Block block, String modId) {
        return getModId(block).equalsIgnoreCase(modId);
    }
}
