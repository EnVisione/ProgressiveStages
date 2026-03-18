package com.enviouse.progressivestages.common.util;

import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks the last matched recipe ID per player during crafting.
 * Populated by CraftingMenuMixin when slotChangedCraftingGrid fires,
 * consumed by ResultSlotMixin to check recipe ID locks without
 * unreliable runtime recipe lookups.
 *
 * Lives outside mixin classes because Mixin forbids non-private static
 * methods on @Mixin targets.
 */
public final class CraftingRecipeTracker {

    private static final Map<UUID, ResourceLocation> LAST_RECIPE_BY_PLAYER = new ConcurrentHashMap<>();

    private CraftingRecipeTracker() {}

    /** Store the recipe ID that just matched the crafting grid for this player. */
    public static void setLastRecipe(UUID playerId, ResourceLocation recipeId) {
        if (recipeId != null) {
            LAST_RECIPE_BY_PLAYER.put(playerId, recipeId);
        } else {
            LAST_RECIPE_BY_PLAYER.remove(playerId);
        }
    }

    /** Get the last matched recipe ID for a player (may be null). */
    public static ResourceLocation getLastRecipe(UUID playerId) {
        return LAST_RECIPE_BY_PLAYER.get(playerId);
    }

    /** Clear stored recipe for a player (e.g., on disconnect). */
    public static void clearLastRecipe(UUID playerId) {
        LAST_RECIPE_BY_PLAYER.remove(playerId);
    }
}

