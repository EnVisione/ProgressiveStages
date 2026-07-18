package com.enviouse.progressivestages.common.rehaul.modifier;

import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.rehaul.ConditionNode;
import com.enviouse.progressivestages.common.rehaul.ConfigProvenance;
import com.enviouse.progressivestages.common.rehaul.SelectorSpec;
import com.enviouse.progressivestages.common.rehaul.condition.ConditionContext;
import com.enviouse.progressivestages.common.rehaul.condition.ConditionEvaluator;
import com.enviouse.progressivestages.common.rehaul.condition.ConditionRegistry;
import com.enviouse.progressivestages.common.rehaul.condition.ConditionStateStore;
import com.enviouse.progressivestages.common.rehaul.condition.SubjectScope;
import com.enviouse.progressivestages.common.rehaul.selector.SelectorMatcherRegistry;
import com.enviouse.progressivestages.common.rehaul.selector.SelectorTarget;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ModifierResolverTest {

    @Test
    void appliesForeignWeaponPenaltyPerStackWithoutChangingPlayerState() {
        StageId mage = StageId.parse("pack:mage");
        CompiledModifier modifier = new CompiledModifier(ResourceLocation.parse("pack:foreign_weapon"), mage,
            List.of(SelectorSpec.parse("tag:pack:knight_weapons").orElseThrow()),
            Set.of(ItemContext.MAIN_HAND), Set.of(mage), Set.of(StageId.parse("pack:knight_training")),
            new ConditionNode.Constant(true), AggregationMode.PER_STACK, 4, 300,
            List.of(new AttributeChange(ResourceLocation.parse("minecraft:generic.attack_damage"), -0.25,
                AttributeOperation.ADD_MULTIPLIED_TOTAL, StackingPolicy.ADD,
                Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY)), List.of(), List.of(),
            ConfigProvenance.legacy("test", "test", "item_modifiers"));
        ContextualItem sword = new ContextualItem(new SelectorTarget(ResourceLocation.parse("minecraft:iron_sword"),
            null, Set.of(ResourceLocation.parse("pack:knight_weapons")), Map.of()), 1, Set.of(ItemContext.MAIN_HAND));
        ModifierResolver resolver = new ModifierResolver(SelectorMatcherRegistry.get(),
            new ConditionEvaluator(ConditionRegistry.get(), new ConditionStateStore()));
        ConditionContext context = new ConditionContext("player", SubjectScope.PLAYER, 100,
            Map.of(), Set.of(), Map.of());
        ResolvedModifier resolved = resolver.resolve(List.of(modifier), List.of(sword), Set.of(mage), context).getFirst();
        assertEquals(-0.25, resolved.attributes().getFirst().amount());
        assertEquals(1, resolved.multiplier());
    }
}
