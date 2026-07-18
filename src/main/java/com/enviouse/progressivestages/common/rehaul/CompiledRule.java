package com.enviouse.progressivestages.common.rehaul;

import com.enviouse.progressivestages.common.api.StageId;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record CompiledRule(
        ResourceLocation id,
        StageId ownerStage,
        String category,
        String action,
        RuleEffect effect,
        SelectorSpec selector,
        int priority,
        RuleLifetime lifetime,
        ConditionNode condition,
        ResourceLocation parentRuleId,
        ViewerPolicy viewerPolicy,
        Map<String, Object> settings,
        ConfigProvenance provenance) {

    public CompiledRule {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(ownerStage, "ownerStage");
        category = requireText(category, "category");
        action = requireText(action, "action");
        Objects.requireNonNull(effect, "effect");
        Objects.requireNonNull(selector, "selector");
        lifetime = lifetime != null ? lifetime : RuleLifetime.PERMANENT;
        condition = condition != null ? condition : new ConditionNode.Constant(true);
        viewerPolicy = viewerPolicy != null ? viewerPolicy : ViewerPolicy.INHERIT;
        settings = settings == null ? Map.of() : Map.copyOf(settings);
        Objects.requireNonNull(provenance, "provenance");
        if (effect == RuleEffect.EXCLUDE && parentRuleId == null) {
            throw new IllegalArgumentException("An exclusion must reference its parent rule");
        }
    }

    public Optional<ResourceLocation> parentRule() {
        return Optional.ofNullable(parentRuleId);
    }

    private static String requireText(String value, String name) {
        String normalized = Objects.requireNonNull(value, name).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(name + " cannot be blank");
        return normalized;
    }
}
