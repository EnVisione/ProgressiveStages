package com.enviouse.progressivestages.common.rehaul.action;

import com.enviouse.progressivestages.common.api.StageId;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class ActionRegistry {

    private static final ActionRegistry INSTANCE = new ActionRegistry();
    private volatile Map<ResourceLocation, ActionProvider> providers;

    private ActionRegistry() {
        Map<ResourceLocation, ActionProvider> initial = new LinkedHashMap<>();
        for (ActionProvider provider : builtins()) initial.put(provider.id(), provider);
        providers = Map.copyOf(initial);
    }

    public static ActionRegistry get() { return INSTANCE; }

    public synchronized void register(ActionProvider provider) {
        Map<ResourceLocation, ActionProvider> copy = new LinkedHashMap<>(providers);
        if (copy.putIfAbsent(provider.id(), provider) != null) throw new IllegalArgumentException("Duplicate action provider. " + provider.id());
        providers = Map.copyOf(copy);
    }

    public Optional<ActionProvider> find(ResourceLocation id) { return Optional.ofNullable(providers.get(id)); }

    public Map<ResourceLocation, ActionProvider> providers() { return providers; }

    private static java.util.List<ActionProvider> builtins() {
        return java.util.List.of(
            new SimpleProvider("grant_stage", true) {
                @Override ActionResult run(CompiledAction action, ActionContext context) {
                    StageId stage = StageId.parse(required(action, "stage"));
                    boolean changed = context.subject().grantStage(stage);
                    return ActionResult.success(changed ? "Stage granted" : "Stage was already owned", stage);
                }

                @Override ActionResult undo(ActionContext context, Object token) {
                    return ActionResult.success("Stage grant compensated", context.subject().revokeStage((StageId) token));
                }
            },
            new SimpleProvider("revoke_stage", true) {
                @Override ActionResult run(CompiledAction action, ActionContext context) {
                    StageId stage = StageId.parse(required(action, "stage"));
                    boolean changed = context.subject().revokeStage(stage);
                    return ActionResult.success(changed ? "Stage revoked" : "Stage was already missing", stage);
                }

                @Override ActionResult undo(ActionContext context, Object token) {
                    return ActionResult.success("Stage revoke compensated", context.subject().grantStage((StageId) token));
                }
            },
            new SimpleProvider("set_state", false) {
                @Override ActionResult run(CompiledAction action, ActionContext context) {
                    StageId stage = StageId.parse(required(action, "stage"));
                    String state = required(action, "state");
                    String previous = context.subject().stageState(stage);
                    return context.subject().setStageState(stage, state)
                        ? ActionResult.success("Stage state changed", Map.entry(stage, previous))
                        : ActionResult.failure("state_rejected", "The stage state change was rejected");
                }
            },
            new SimpleProvider("set_variable", true) {
                @Override ActionResult run(CompiledAction action, ActionContext context) {
                    String variable = required(action, "variable");
                    double previous = context.subject().variable(variable);
                    context.subject().setVariable(variable, number(action.arguments().get("value")));
                    return ActionResult.success("Variable changed", Map.entry(variable, previous));
                }

                @Override ActionResult undo(ActionContext context, Object token) {
                    Map.Entry<?, ?> entry = (Map.Entry<?, ?>) token;
                    context.subject().setVariable(String.valueOf(entry.getKey()), number(entry.getValue()));
                    return ActionResult.success("Variable change compensated", null);
                }
            },
            new SimpleProvider("add_variable", true) {
                @Override ActionResult run(CompiledAction action, ActionContext context) {
                    String variable = required(action, "variable");
                    double previous = context.subject().variable(variable);
                    context.subject().setVariable(variable, previous + number(action.arguments().get("amount")));
                    return ActionResult.success("Variable changed", Map.entry(variable, previous));
                }

                @Override ActionResult undo(ActionContext context, Object token) {
                    Map.Entry<?, ?> entry = (Map.Entry<?, ?>) token;
                    context.subject().setVariable(String.valueOf(entry.getKey()), number(entry.getValue()));
                    return ActionResult.success("Variable change compensated", null);
                }
            },
            new SimpleProvider("message", false) {
                @Override ActionResult run(CompiledAction action, ActionContext context) {
                    context.subject().sendMessage(required(action, "message"));
                    return ActionResult.success("Message sent", null);
                }
            },
            new SimpleProvider("service", false) {
                @Override ActionResult run(CompiledAction action, ActionContext context) {
                    String service = required(action, "service");
                    ActionContext.ActionService target = context.services().get(service);
                    return target == null ? ActionResult.failure("missing_service", "Action service is unavailable")
                        : target.call(action.arguments(), context);
                }
            });
    }

    private abstract static class SimpleProvider implements ActionProvider {
        private final ResourceLocation id;
        private final boolean compensatable;

        private SimpleProvider(String id, boolean compensatable) {
            this.id = ResourceLocation.fromNamespaceAndPath("progressivestages", id);
            this.compensatable = compensatable;
        }

        @Override public ResourceLocation id() { return id; }
        @Override public boolean supportsCompensation() { return compensatable; }
        @Override public ActionResult execute(CompiledAction action, ActionContext context) { return run(action, context); }
        @Override public ActionResult compensate(CompiledAction action, ActionContext context, Object token) {
            return compensatable ? undo(context, token) : ActionProvider.super.compensate(action, context, token);
        }
        abstract ActionResult run(CompiledAction action, ActionContext context);
        ActionResult undo(ActionContext context, Object token) { return ActionProvider.super.compensate(null, context, token); }
    }

    private static String required(CompiledAction action, String key) {
        Object value = action.arguments().get(key);
        if (value == null || String.valueOf(value).isBlank()) throw new IllegalArgumentException("Action argument is required. " + key);
        return String.valueOf(value);
    }

    private static double number(Object value) {
        if (value instanceof Number number) return number.doubleValue();
        return Double.parseDouble(String.valueOf(value));
    }
}
