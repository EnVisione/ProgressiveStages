package com.enviouse.progressivestages.common.rehaul.modifier;

import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.rehaul.condition.ConditionContext;
import com.enviouse.progressivestages.common.rehaul.condition.ConditionEvaluator;
import com.enviouse.progressivestages.common.rehaul.selector.SelectorMatcherRegistry;
import com.enviouse.progressivestages.common.rehaul.selector.SelectorTarget;
import net.minecraft.resources.ResourceLocation;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.ToIntFunction;

public final class DropModifierResolver {

    private final SelectorMatcherRegistry selectors;
    private final ConditionEvaluator conditions;

    public DropModifierResolver(SelectorMatcherRegistry selectors, ConditionEvaluator conditions) {
        this.selectors = selectors;
        this.conditions = conditions;
    }

    public int resolve(List<CompiledDropModifier> rules, SelectorTarget block, SelectorTarget drop,
                       SelectorTarget tool, Set<StageId> stages, ConditionContext context,
                       ToIntFunction<ResourceLocation> enchantmentLevels, int initialCount) {
        int result = Math.max(0, initialCount);
        List<CompiledDropModifier> ordered = (rules == null ? List.<CompiledDropModifier>of() : rules).stream()
            .sorted(Comparator.comparingInt(CompiledDropModifier::priority).reversed()
                .thenComparing(value -> value.id().toString()))
            .toList();
        for (CompiledDropModifier rule : ordered) {
            if (!stages.containsAll(rule.requiredStages())) continue;
            if (rule.missingStages().stream().anyMatch(stages::contains)) continue;
            if (!matches(rule.blocks(), block) || !matches(rule.drops(), drop)) continue;
            if (!rule.tools().isEmpty() && (tool == null || !matches(rule.tools(), tool))) continue;
            if (rule.requiredEnchantment() != null
                    && enchantmentLevels.applyAsInt(rule.requiredEnchantment()) < rule.minimumEnchantmentLevel()) continue;
            if (!conditions.evaluate(rule.condition(), context).result().matched()) continue;
            result = rule.apply(result);
            if (rule.exclusive()) break;
        }
        return result;
    }

    private boolean matches(List<com.enviouse.progressivestages.common.rehaul.SelectorSpec> specs,
                            SelectorTarget target) {
        return specs.stream().anyMatch(selector -> selectors.match(selector, target).matched());
    }
}
