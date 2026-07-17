package com.enviouse.progressivestages.common.config;

import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * v3.0: the optional {@code [rewards]} a stage hands out the moment it's granted — the natural
 * companion to {@code [cost]}. Every part is optional; {@link #NONE} (no section) does nothing.
 *
 * <pre>
 * [rewards]
 * items     = ["minecraft:diamond:5", "minecraft:netherite_scrap"]
 * effects   = ["minecraft:strength:60:1"]   # id : seconds : amplifier
 * commands  = ["give {player} minecraft:cake 1"]
 * teleport  = "minecraft:the_nether 0 70 0" # "[dim] x y z" — dim optional
 * xp_levels = 5
 * xp_points = 100
 * </pre>
 */
public record StageRewards(List<StageCost.ItemCost> items,
                           List<EffectReward> effects,
                           List<String> commands,
                           String teleport,
                           int xpLevels,
                           int xpPoints) {

    public static final StageRewards NONE =
        new StageRewards(List.of(), List.of(), List.of(), "", 0, 0);

    public StageRewards {
        items = items == null ? List.of() : List.copyOf(items);
        effects = effects == null ? List.of() : List.copyOf(effects);
        commands = commands == null ? List.of() : List.copyOf(commands);
        teleport = teleport == null ? "" : teleport;
        xpLevels = Math.max(0, xpLevels);
        xpPoints = Math.max(0, xpPoints);
    }

    /** A status effect to apply on grant: effect id, duration in TICKS, amplifier (0 = level I). */
    public record EffectReward(ResourceLocation effect, int durationTicks, int amplifier) {}

    public boolean isEmpty() {
        return items.isEmpty() && effects.isEmpty() && commands.isEmpty()
            && teleport.isEmpty() && xpLevels == 0 && xpPoints == 0;
    }
}
