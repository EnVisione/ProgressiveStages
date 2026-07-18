package com.enviouse.progressivestages.common.config;

import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.lock.EnforcementCategory;
import com.enviouse.progressivestages.common.lock.ConditionalRule;
import com.enviouse.progressivestages.common.lock.LockDefinition;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StageModelImmutabilityTest {

    @Test
    void rewardsCopyCallerOwnedCollections() {
        List<String> commands = new ArrayList<>(List.of("say ready"));
        StageRewards rewards = new StageRewards(List.of(), List.of(), commands, null, -2, -3);

        commands.add("say changed");

        assertEquals(List.of("say ready"), rewards.commands());
        assertEquals("", rewards.teleport());
        assertEquals(0, rewards.xpLevels());
        assertEquals(0, rewards.xpPoints());
        assertThrows(UnsupportedOperationException.class, () -> rewards.commands().add("say mutate"));
    }

    @Test
    void regionCoordinatesAreDefensiveCopies() {
        int[] first = {1, 2, 3};
        int[] second = {4, 5, 6};
        LockDefinition.RegionLock region = new LockDefinition.RegionLock(
            ResourceLocation.parse("minecraft:overworld"), first, second,
            true, false, false, false, false);

        first[0] = 99;
        int[] returned = region.pos1();
        returned[1] = 99;

        assertArrayEquals(new int[]{1, 2, 3}, region.pos1());
        assertArrayEquals(new int[]{4, 5, 6}, region.pos2());
    }

    @Test
    void enforcementOverridesCannotBeMutatedThroughTheModel() {
        LockDefinition definition = LockDefinition.builder()
            .enforcementOverride(EnforcementCategory.ITEM_USE, true)
            .build();

        assertThrows(UnsupportedOperationException.class, () ->
            definition.enforcementOverrides().put(EnforcementCategory.ITEM_USE, false));
    }

    @Test
    void stageCopiesConditionalRuleCollections() {
        ConditionalRule rule = new ConditionalRule(
            ResourceLocation.fromNamespaceAndPath("test", "rule"), StageId.of("owner"),
            ConditionalRule.Effect.LOCK, ConditionalRule.Activation.LIVE,
            ConditionalRule.StageState.OWNED, 100, ConditionalRule.Context.EMPTY,
            ConditionalRule.Targets.EMPTY, ConditionalRule.TriggerType.MANUAL,
            List.of(), -1L, true);
        List<ConditionalRule> callerOwned = new ArrayList<>(List.of(rule));

        StageDefinition definition = StageDefinition.builder(StageId.of("owner"))
            .conditionalRules(callerOwned).build();
        callerOwned.clear();

        assertEquals(List.of(rule), definition.getConditionalRules());
        assertThrows(UnsupportedOperationException.class,
            () -> definition.getConditionalRules().clear());
    }
}
