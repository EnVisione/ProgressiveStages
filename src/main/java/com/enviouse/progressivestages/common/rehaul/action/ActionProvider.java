package com.enviouse.progressivestages.common.rehaul.action;

import net.minecraft.resources.ResourceLocation;

import java.util.Map;

public interface ActionProvider {

    ResourceLocation id();

    default void validate(Map<String, Object> arguments) {}

    ActionResult execute(CompiledAction action, ActionContext context);

    default boolean supportsCompensation() {
        return false;
    }

    default ActionResult compensate(CompiledAction action, ActionContext context, Object token) {
        return ActionResult.failure("not_compensatable", "The action does not support compensation");
    }
}
