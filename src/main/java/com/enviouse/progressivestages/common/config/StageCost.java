package com.enviouse.progressivestages.common.config;

import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * v2.4: a stage's {@code [cost]} section — makes the stage purchasable from the in-game tree GUI
 * (skill-tree mode). The player spends the cost to unlock the stage. A stage is "purchasable" iff
 * its {@link StageDefinition#getCost()} is non-null (i.e. it declared a {@code [cost]} section).
 *
 * @param xpLevels            experience LEVELS consumed on purchase (0 = none)
 * @param items               item costs consumed on purchase
 * @param bypassRequirements  if true, paying unlocks immediately even if the stage's {@code [[triggers]]}
 *                            aren't satisfied (prerequisite STAGES are still required); if false, the
 *                            Unlock button only appears once prerequisites + triggers are met and the
 *                            cost is the final confirmation step.
 * @param cooldownSeconds     v3.0: minimum seconds between this player's skill-tree purchases (0 = none).
 * @param refundPercent       v3.0: percent (0..100) of the item/xp cost returned if the stage is later revoked.
 */
public record StageCost(int xpLevels, List<ItemCost> items, boolean bypassRequirements,
                        int cooldownSeconds, int refundPercent) {

    public StageCost {
        items = List.copyOf(items);
        cooldownSeconds = Math.max(0, cooldownSeconds);
        refundPercent = Math.max(0, Math.min(100, refundPercent));
    }

    public record ItemCost(ResourceLocation item, int count) {}
}
