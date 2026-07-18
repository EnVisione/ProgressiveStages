package com.enviouse.progressivestages.common.rehaul.modifier;

import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.rehaul.condition.ConditionContext;
import com.enviouse.progressivestages.common.rehaul.condition.ConditionEvaluator;
import com.enviouse.progressivestages.common.rehaul.selector.SelectorMatcherRegistry;
import net.minecraft.resources.ResourceLocation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

public final class ModifierResolver {

    private final SelectorMatcherRegistry selectors;
    private final ConditionEvaluator conditions;

    public ModifierResolver(SelectorMatcherRegistry selectors, ConditionEvaluator conditions) {
        this.selectors = selectors;
        this.conditions = conditions;
    }

    public List<ResolvedModifier> resolve(List<CompiledModifier> rules, List<ContextualItem> inventory,
                                          Set<StageId> stages, ConditionContext context) {
        List<ResolvedModifier> output = new ArrayList<>();
        for (CompiledModifier rule : rules == null ? List.<CompiledModifier>of() : rules) {
            if (!stages.containsAll(rule.requiredStages())) continue;
            if (rule.missingStages().stream().anyMatch(stages::contains)) continue;
            if (!conditions.evaluate(rule.condition(), context).result().matched()) continue;
            List<ContextualItem> matches = inventory.stream().filter(item -> contextMatches(rule, item))
                .filter(item -> rule.items().stream().anyMatch(selector -> selectors.match(selector, item.target()).matched()))
                .toList();
            if (matches.isEmpty() && !rule.items().isEmpty()) continue;
            int multiplier = multiplier(rule, matches);
            if (multiplier < 1) continue;
            output.add(new ResolvedModifier(stableId(rule.id(), multiplier), rule.id(), multiplier,
                rule.priority(), scale(rule.attributes(), multiplier), rule.effects(), rule.transforms()));
        }
        return output.stream().sorted(Comparator.comparingInt(ResolvedModifier::priority).reversed()
            .thenComparing(value -> value.sourceRule().toString())).toList();
    }

    private static boolean contextMatches(CompiledModifier rule, ContextualItem item) {
        return rule.contexts().isEmpty() || item.contexts().stream().anyMatch(rule.contexts()::contains);
    }

    private static int multiplier(CompiledModifier rule, List<ContextualItem> matches) {
        int stacks = matches.size();
        int items = matches.stream().mapToInt(ContextualItem::count).sum();
        return Math.min(rule.cap(), switch (rule.aggregation()) {
            case ONCE, HIGHEST, LOWEST, EXCLUSIVE -> 1;
            case PER_STACK -> stacks;
            case PER_ITEM, SUM -> items;
        });
    }

    private static List<AttributeChange> scale(List<AttributeChange> changes, int multiplier) {
        return changes.stream().map(change -> new AttributeChange(change.attribute(),
            change.amount() * multiplier, change.operation(), change.stacking(), change.minimum(), change.maximum())).toList();
    }

    private static ResourceLocation stableId(ResourceLocation rule, int bucket) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String hash = HexFormat.of().formatHex(digest.digest((rule + "|" + bucket)
                .getBytes(StandardCharsets.UTF_8))).substring(0, 20);
            return ResourceLocation.fromNamespaceAndPath("progressivestages", "modifier/" + hash);
        } catch (Exception impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
