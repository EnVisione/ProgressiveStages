package com.enviouse.progressivestages.common.lock;

import com.enviouse.progressivestages.common.config.StageConfig;

import java.util.Locale;
import java.util.function.BooleanSupplier;

/**
 * v2.3: the enforcement behaviours a stage can override per-stage in its {@code [enforcement]}
 * section. Each category maps to a global default toggle in progressivestages.toml; a stage may
 * set the SAME key (e.g. {@code block_item_use = false}) inside its own {@code [enforcement]} to
 * override that behaviour for the resources THAT stage gates.
 *
 * <p>Resolution: a resource is enforced for a category if, for the gating stage(s) the player is
 * missing, the per-stage override (or the global default when unset) is {@code true}. With no
 * overrides anywhere, behaviour is identical to the pure global toggles.
 */
public enum EnforcementCategory {
    ITEM_USE("block_item_use", StageConfig::isBlockItemUse),
    ITEM_PICKUP("block_item_pickup", StageConfig::isBlockItemPickup),
    ITEM_INVENTORY("block_item_inventory", StageConfig::isBlockItemInventory),
    BLOCK_PLACEMENT("block_block_placement", StageConfig::isBlockBlockPlacement),
    BLOCK_INTERACTION("block_block_interaction", StageConfig::isBlockBlockInteraction),
    DIMENSION_TRAVEL("block_dimension_travel", StageConfig::isBlockDimensionTravel),
    ENTITY_ATTACK("block_entity_attack", StageConfig::isBlockEntityAttack),
    SCREEN_OPEN("block_screen_open", StageConfig::isBlockScreenOpen),
    CROP_GROWTH("block_crop_growth", StageConfig::isBlockCropGrowth),
    PET_INTERACT("block_pet_interact", StageConfig::isBlockPetInteract);

    /** The TOML key (identical to the global progressivestages.toml toggle name). */
    private final String key;
    private final BooleanSupplier globalDefault;

    EnforcementCategory(String key, BooleanSupplier globalDefault) {
        this.key = key;
        this.globalDefault = globalDefault;
    }

    public String key() { return key; }

    /** The current global default for this category from progressivestages.toml. */
    public boolean globalDefault() { return globalDefault.getAsBoolean(); }

    /** Resolve a TOML key (with a few short aliases) to a category, or {@code null}. */
    public static EnforcementCategory fromKey(String k) {
        if (k == null) return null;
        String n = k.trim().toLowerCase(Locale.ROOT);
        for (EnforcementCategory c : values()) {
            if (c.key.equals(n)) return c;
        }
        return switch (n) {
            case "use", "item_use" -> ITEM_USE;
            case "pickup", "item_pickup" -> ITEM_PICKUP;
            case "inventory", "item_inventory" -> ITEM_INVENTORY;
            case "placement", "place", "block_placement" -> BLOCK_PLACEMENT;
            case "interaction", "block_interaction" -> BLOCK_INTERACTION;
            case "dimension", "dimension_travel" -> DIMENSION_TRAVEL;
            case "attack", "entity_attack" -> ENTITY_ATTACK;
            case "screen", "screens", "screen_open" -> SCREEN_OPEN;
            case "crop", "crops", "crop_growth" -> CROP_GROWTH;
            case "pet", "pets", "pet_interact" -> PET_INTERACT;
            default -> null;
        };
    }
}
