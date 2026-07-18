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

class DropModifierResolverTest {

    @Test
    void doublesOnlyMatchingFortuneDiamondDropsForTheOwnedStage() {
        StageId engineer = StageId.parse("showcase:diamond_engineer");
        ResourceLocation fortune = ResourceLocation.parse("minecraft:fortune");
        CompiledDropModifier modifier = new CompiledDropModifier(
            ResourceLocation.parse("showcase:diamond_fortune"), engineer,
            List.of(SelectorSpec.parse("minecraft:diamond_ore").orElseThrow(),
                SelectorSpec.parse("minecraft:deepslate_diamond_ore").orElseThrow()),
            List.of(SelectorSpec.parse("minecraft:diamond").orElseThrow()),
            List.of(SelectorSpec.parse("tag:minecraft:pickaxes").orElseThrow()),
            Set.of(engineer), Set.of(), new ConditionNode.Constant(true), fortune, 1,
            0, 2, 0, 64, 600, true,
            ConfigProvenance.legacy("test", "test", "drop_modifiers"));
        DropModifierResolver resolver = new DropModifierResolver(SelectorMatcherRegistry.get(),
            new ConditionEvaluator(ConditionRegistry.get(), new ConditionStateStore()));
        ConditionContext context = new ConditionContext("player", SubjectScope.PLAYER, 100,
            Map.of(), Set.of(), Map.of());
        SelectorTarget diamondOre = SelectorTarget.id(ResourceLocation.parse("minecraft:diamond_ore"));
        SelectorTarget coalOre = SelectorTarget.id(ResourceLocation.parse("minecraft:coal_ore"));
        SelectorTarget diamond = SelectorTarget.id(ResourceLocation.parse("minecraft:diamond"));
        SelectorTarget pickaxe = new SelectorTarget(ResourceLocation.parse("minecraft:diamond_pickaxe"), null,
            Set.of(ResourceLocation.parse("minecraft:pickaxes")), Map.of());

        assertEquals(6, resolver.resolve(List.of(modifier), diamondOre, diamond, pickaxe,
            Set.of(engineer), context, id -> id.equals(fortune) ? 3 : 0, 3));
        assertEquals(3, resolver.resolve(List.of(modifier), diamondOre, diamond, pickaxe,
            Set.of(), context, id -> 3, 3));
        assertEquals(3, resolver.resolve(List.of(modifier), diamondOre, diamond, pickaxe,
            Set.of(engineer), context, id -> 0, 3));
        assertEquals(3, resolver.resolve(List.of(modifier), coalOre, diamond, pickaxe,
            Set.of(engineer), context, id -> 3, 3));
    }
}
