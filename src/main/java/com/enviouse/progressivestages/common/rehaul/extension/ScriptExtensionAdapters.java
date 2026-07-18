package com.enviouse.progressivestages.common.rehaul.extension;

import com.enviouse.progressivestages.common.compat.ScriptHooks;
import com.enviouse.progressivestages.common.rehaul.SelectorSpec;
import com.enviouse.progressivestages.common.rehaul.action.ActionContext;
import com.enviouse.progressivestages.common.rehaul.action.ActionProvider;
import com.enviouse.progressivestages.common.rehaul.action.ActionRegistry;
import com.enviouse.progressivestages.common.rehaul.action.ActionResult;
import com.enviouse.progressivestages.common.rehaul.action.CompiledAction;
import com.enviouse.progressivestages.common.rehaul.challenge.ChallengeEvent;
import com.enviouse.progressivestages.common.rehaul.challenge.ChallengeMeasureProvider;
import com.enviouse.progressivestages.common.rehaul.challenge.ChallengeMeasureRegistry;
import com.enviouse.progressivestages.common.rehaul.selector.SelectorMatch;
import com.enviouse.progressivestages.common.rehaul.selector.SelectorMatcher;
import com.enviouse.progressivestages.common.rehaul.selector.SelectorMatcherRegistry;
import com.enviouse.progressivestages.common.rehaul.selector.SelectorTarget;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class ScriptExtensionAdapters {
    private static final Set<ResourceLocation> ACTIONS = ConcurrentHashMap.newKeySet();
    private static final Set<ResourceLocation> SELECTORS = ConcurrentHashMap.newKeySet();
    private static final Set<ResourceLocation> MEASURES = ConcurrentHashMap.newKeySet();

    private ScriptExtensionAdapters() {}

    public static void action(ResourceLocation id) {
        if (!ACTIONS.add(id)) return;
        ActionRegistry.get().register(new ActionProvider() {
            @Override public ResourceLocation id() { return id; }
            @Override public ActionResult execute(CompiledAction action, ActionContext context) {
                Object raw = context.values().get("server_player");
                if (!(raw instanceof ServerPlayer player)) {
                    return ActionResult.failure("missing_player", "The script action requires a player subject");
                }
                return ScriptHooks.runAction(id.toString(), player, action.arguments());
            }
        });
    }

    public static void selector(ResourceLocation id, String prefix) {
        if (!SELECTORS.add(id)) return;
        SelectorMatcherRegistry.get().register(new SelectorMatcher() {
            @Override public ResourceLocation id() { return id; }
            @Override public String prefix() { return prefix; }
            @Override public Optional<SelectorSpec> parse(String raw, String value, Integer priority) {
                if (value == null || value.isBlank()) return Optional.empty();
                return Optional.of(new SelectorSpec(id, raw, value, null, Map.of("callback", id.toString()),
                    priority, "", null));
            }
            @Override public SelectorMatch match(SelectorSpec selector, SelectorTarget target) {
                return ScriptHooks.evalSelector(id.toString(), selector.value(), target)
                    ? SelectorMatch.yes(325, "Script selector matched")
                    : SelectorMatch.no("Script selector did not match");
            }
        });
    }

    public static void measure(ResourceLocation id) {
        if (!MEASURES.add(id)) return;
        ChallengeMeasureRegistry.get().register(new ChallengeMeasureProvider() {
            @Override public ResourceLocation id() { return id; }
            @Override public double amount(ChallengeEvent event, Map<String, Object> filters) {
                return ScriptHooks.evalMeasure(id.toString(), event.subject(), event.type().toString(),
                    event.amount(), event.properties(), filters);
            }
        });
    }
}
