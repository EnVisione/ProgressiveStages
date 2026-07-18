package com.enviouse.progressivestages.common.rehaul.condition;

import com.enviouse.progressivestages.common.rehaul.ConditionNode;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.Set;

public interface ConditionProvider {

    ResourceLocation id();

    ConditionValueType valueType();

    ConditionBehavior behavior();

    Set<String> eventInterests();

    Set<SubjectScope> supportedScopes();

    default void validate(Map<String, Object> arguments) {}

    ConditionResult evaluate(ConditionNode.Leaf condition, ConditionContext context);
}
