package com.enviouse.progressivestages.common.lock;

/**
 * Enum representing the types of locks that can be applied
 */
public enum LockType {
    /**
     * Lock specific items by registry ID
     */
    ITEM,

    /**
     * Lock all items in a tag
     */
    ITEM_TAG,

    /**
     * Lock specific crafting recipes by ID
     */
    RECIPE,

    /**
     * Lock all recipes in a tag
     */
    RECIPE_TAG,

    /**
     * Lock specific blocks
     */
    BLOCK,

    /**
     * Lock all blocks in a tag
     */
    BLOCK_TAG,

    /**
     * Lock entire dimensions
     */
    DIMENSION,

    /**
     * Lock all content from a mod
     */
    MOD,

    /**
     * Lock by name pattern (substring matching)
     */
    NAME,

    /**
     * Lock specific interactions (item on block, etc.)
     */
    INTERACTION
}
