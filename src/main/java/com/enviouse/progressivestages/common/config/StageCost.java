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
 */
public record StageCost(int xpLevels, List<ItemCost> items, boolean bypassRequirements) {

    public StageCost {
        items = List.copyOf(items);
    }

    public record ItemCost(ResourceLocation item, int count) {}
}
