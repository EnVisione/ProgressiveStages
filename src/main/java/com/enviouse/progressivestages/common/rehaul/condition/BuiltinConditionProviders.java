package com.enviouse.progressivestages.common.rehaul.condition;

import com.enviouse.progressivestages.common.rehaul.ConditionNode;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class BuiltinConditionProviders {

    private static final Set<SubjectScope> ALL_SCOPES = Set.of(SubjectScope.PLAYER, SubjectScope.TEAM,
        SubjectScope.SERVER, SubjectScope.CUSTOM);

    private BuiltinConditionProviders() {}

    static List<ConditionProvider> all() {
        List<ConditionProvider> providers = new ArrayList<>();
        providers.add(booleanProvider("boolean", "value", ConditionBehavior.LIVE_STATE));
        providers.add(setProvider("stage_owned", "stages", "id", ConditionBehavior.LIVE_STATE));
        providers.add(valueProvider("dimension", "dimension", "id", ConditionBehavior.LIVE_STATE));
        providers.add(valueProvider("biome", "biome", "id", ConditionBehavior.LIVE_STATE));
        providers.add(setProvider("structure", "structures", "id", ConditionBehavior.SESSION));
        providers.add(valueProvider("weather", "weather", "value", ConditionBehavior.LIVE_STATE));
        providers.add(scriptProvider("script"));
        providers.add(scriptProvider("kubejs"));
        providers.add(numericProvider("current_health", "health", ConditionBehavior.LIVE_STATE));
        providers.add(numericProvider("food", "food", ConditionBehavior.LIVE_STATE));
        providers.add(numericProvider("altitude", "altitude", ConditionBehavior.LIVE_STATE));
        providers.add(numericProvider("stage_count", "stage_count", ConditionBehavior.LIVE_STATE));
        providers.add(numericProvider("online_team_size", "online_team_size", ConditionBehavior.LIVE_STATE));
        providers.add(numericProvider("play_time", "play_time", ConditionBehavior.RETROACTIVE));
        providers.add(numericProvider("level", "level", ConditionBehavior.LIVE_STATE));
        providers.add(numericProvider("xp", "xp", ConditionBehavior.LIVE_STATE));
        providers.add(numericProvider("counter", "counter", ConditionBehavior.RETROACTIVE));
        providers.add(numericProvider("scoreboard", "scoreboard", ConditionBehavior.LIVE_STATE));
        providers.add(numericProvider("death", "death", ConditionBehavior.EVENT_EDGE));
        providers.add(numericProvider("other_player_death", "other_player_death", ConditionBehavior.EVENT_EDGE));
        providers.add(numericProvider("respawn", "respawn", ConditionBehavior.EVENT_EDGE));
        providers.add(numericProvider("player_kill", "player_kill", ConditionBehavior.EVENT_EDGE));
        providers.add(numericProvider("health_gained", "health_gained", ConditionBehavior.ROLLING_WINDOW));
        providers.add(numericProvider("health_lost", "health_lost", ConditionBehavior.ROLLING_WINDOW));
        providers.add(numericProvider("damage_taken", "damage_taken", ConditionBehavior.ROLLING_WINDOW));
        providers.add(numericProvider("damage_dealt", "damage_dealt", ConditionBehavior.ROLLING_WINDOW));
        providers.add(numericProvider("hits_taken", "hits_taken", ConditionBehavior.ROLLING_WINDOW));
        providers.add(numericProvider("hits_dealt", "hits_dealt", ConditionBehavior.ROLLING_WINDOW));
        providers.add(booleanProvider("health_threshold_crossed", "health_threshold_crossed", ConditionBehavior.EVENT_EDGE));
        providers.add(numericProvider("no_damage_for", "no_damage_for", ConditionBehavior.LIVE_STATE));
        providers.add(numericProvider("structure_time", "structure_time", ConditionBehavior.SESSION));
        for (String id : List.of("kill", "mine", "craft", "pickup", "use", "drop", "break_item",
                "distance", "stat", "advancement", "item_possession", "has_item", "effect", "breed", "day",
                "day_count", "world_time", "enter_structure", "tame", "kill_with_item", "kill_with", "fish",
                "sleep", "ride", "biome_time", "reach_y", "stage_held_duration", "stage_held_for",
                "custom_counter", "health", "script_value")) {
            providers.add(triggerProvider(id));
        }
        for (String id : List.of("structure_leave_outcome", "leave_structure")) {
            providers.add(numericProvider(id, id, ConditionBehavior.EVENT_EDGE));
        }
        for (String id : List.of("combat_session", "boss_session", "region_session", "custom_session")) {
            providers.add(numericProvider(id, id, ConditionBehavior.SESSION));
        }
        providers.add(new LegacyContextProvider());
        return List.copyOf(providers);
    }

    private static ConditionProvider booleanProvider(String id, String valueKey, ConditionBehavior behavior) {
        return new MapProvider(id(id), ConditionValueType.BOOLEAN, behavior, valueKey) {
            @Override
            boolean test(Object actual, Map<String, Object> arguments) {
                boolean expected = bool(arguments.getOrDefault("expected", true));
                return bool(actual) == expected;
            }
        };
    }

    private static ConditionProvider valueProvider(String id, String valueKey, String argument,
                                                    ConditionBehavior behavior) {
        return new MapProvider(id(id), ConditionValueType.STRING, behavior, valueKey) {
            @Override
            boolean test(Object actual, Map<String, Object> arguments) {
                Object expected = arguments.get(argument);
                return expected != null && String.valueOf(actual).equalsIgnoreCase(String.valueOf(expected));
            }
        };
    }

    private static ConditionProvider setProvider(String id, String valueKey, String argument,
                                                  ConditionBehavior behavior) {
        return new MapProvider(id(id), ConditionValueType.SET, behavior, valueKey) {
            @Override
            boolean test(Object actual, Map<String, Object> arguments) {
                Object expected = arguments.get(argument);
                if (expected == null) return false;
                if (actual instanceof Collection<?> values) {
                    return values.stream().anyMatch(value -> String.valueOf(value).equalsIgnoreCase(String.valueOf(expected)));
                }
                return false;
            }
        };
    }

    private static ConditionProvider numericProvider(String id, String valueKey, ConditionBehavior behavior) {
        return new MapProvider(id(id), ConditionValueType.DECIMAL, behavior, valueKey) {
            @Override
            boolean test(Object actual, Map<String, Object> arguments) {
                double value = number(actual);
                double minimum = number(arguments.getOrDefault("minimum",
                    arguments.getOrDefault("min", arguments.getOrDefault("count", arguments.getOrDefault("amount", 1)))));
                double maximum = number(arguments.getOrDefault("maximum", arguments.getOrDefault("max", Double.MAX_VALUE)));
                return value >= minimum && value <= maximum;
            }

            @Override
            ConditionResult result(Object actual, Map<String, Object> arguments, boolean matched) {
                double value = number(actual);
                double required = number(arguments.getOrDefault("minimum",
                    arguments.getOrDefault("min", arguments.getOrDefault("count", arguments.getOrDefault("amount", 1)))));
                return new ConditionResult(matched, value, required,
                    matched ? "Numeric condition matched" : "Numeric condition has not reached its requirement");
            }
        };
    }

    private static ConditionProvider scriptProvider(String name) {
        return new ConditionProvider() {
            @Override public ResourceLocation id() { return BuiltinConditionProviders.id(name); }
            @Override public ConditionValueType valueType() { return ConditionValueType.BOOLEAN; }
            @Override public ConditionBehavior behavior() { return ConditionBehavior.LIVE_STATE; }
            @Override public Set<String> eventInterests() { return Set.of("kubejs", "script"); }
            @Override public Set<SubjectScope> supportedScopes() { return Set.of(SubjectScope.PLAYER); }
            @Override public ConditionResult evaluate(ConditionNode.Leaf condition, ConditionContext context) {
                Object player = context.values().get("server_player");
                Object callback = condition.arguments().getOrDefault("id", condition.arguments().get("callback"));
                if (!(player instanceof net.minecraft.server.level.ServerPlayer target) || callback == null) {
                    return ConditionResult.failed("Script condition context is unavailable");
                }
                return com.enviouse.progressivestages.common.compat.ScriptHooks.evalCondition(String.valueOf(callback), target)
                    ? ConditionResult.matched("Script condition matched")
                    : ConditionResult.failed("Script condition did not match");
            }
        };
    }

    private static ConditionProvider triggerProvider(String name) {
        return new ConditionProvider() {
            @Override public ResourceLocation id() { return BuiltinConditionProviders.id(name); }
            @Override public ConditionValueType valueType() { return ConditionValueType.DECIMAL; }
            @Override public ConditionBehavior behavior() { return ConditionBehavior.RETROACTIVE; }
            @Override public Set<String> eventInterests() { return Set.of(name, "tick"); }
            @Override public Set<SubjectScope> supportedScopes() { return Set.of(SubjectScope.PLAYER); }
            @Override public ConditionResult evaluate(ConditionNode.Leaf condition, ConditionContext context) {
                Object raw = context.values().get("server_player");
                if (!(raw instanceof net.minecraft.server.level.ServerPlayer player)) {
                    return ConditionResult.failed("Trigger progress requires a player subject");
                }
                long current = com.enviouse.progressivestages.server.triggers.StageTriggerEvaluator
                    .currentProgress(player, name, condition.arguments());
                double minimum = number(condition.arguments().getOrDefault("minimum",
                    condition.arguments().getOrDefault("min", condition.arguments().getOrDefault("count",
                        condition.arguments().getOrDefault("amount", 1)))));
                double maximum = number(condition.arguments().getOrDefault("maximum",
                    condition.arguments().getOrDefault("max", Double.MAX_VALUE)));
                boolean matched = current >= minimum && current <= maximum;
                return new ConditionResult(matched, current, minimum,
                    matched ? "Trigger progress matched" : "Trigger progress has not reached its requirement");
            }
        };
    }

    private abstract static class MapProvider implements ConditionProvider {
        private final ResourceLocation id;
        private final ConditionValueType type;
        private final ConditionBehavior behavior;
        private final String valueKey;

        private MapProvider(ResourceLocation id, ConditionValueType type, ConditionBehavior behavior, String valueKey) {
            this.id = id;
            this.type = type;
            this.behavior = behavior;
            this.valueKey = valueKey;
        }

        @Override
        public ResourceLocation id() { return id; }

        @Override
        public ConditionValueType valueType() { return type; }

        @Override
        public ConditionBehavior behavior() { return behavior; }

        @Override
        public Set<String> eventInterests() { return Set.of(valueKey); }

        @Override
        public Set<SubjectScope> supportedScopes() { return ALL_SCOPES; }

        @Override
        public ConditionResult evaluate(ConditionNode.Leaf condition, ConditionContext context) {
            String key = dynamicKey(valueKey, condition.arguments());
            Object actual = context.values().get(key);
            if (actual == null && !key.equals(valueKey)) actual = context.values().get(valueKey);
            if (actual == null) return ConditionResult.failed("Condition value is unavailable. " + key);
            boolean matched = test(actual, condition.arguments());
            return result(actual, condition.arguments(), matched);
        }

        abstract boolean test(Object actual, Map<String, Object> arguments);

        ConditionResult result(Object actual, Map<String, Object> arguments, boolean matched) {
            return matched ? ConditionResult.matched("Condition matched") : ConditionResult.failed("Condition did not match");
        }
    }

    private static final class LegacyContextProvider implements ConditionProvider {
        @Override
        public ResourceLocation id() { return BuiltinConditionProviders.id("legacy_context"); }

        @Override
        public ConditionValueType valueType() { return ConditionValueType.BOOLEAN; }

        @Override
        public ConditionBehavior behavior() { return ConditionBehavior.LIVE_STATE; }

        @Override
        public Set<String> eventInterests() { return Set.of("legacy_context"); }

        @Override
        public Set<SubjectScope> supportedScopes() { return ALL_SCOPES; }

        @Override
        public ConditionResult evaluate(ConditionNode.Leaf condition, ConditionContext context) {
            Object value = context.values().get("legacy_context");
            return bool(value) ? ConditionResult.matched("Legacy context matched")
                : ConditionResult.failed("Legacy context did not match");
        }
    }

    private static String dynamicKey(String base, Map<String, Object> arguments) {
        Object key = arguments.get("key");
        if (key == null) key = arguments.get("id");
        return key == null || String.valueOf(key).isBlank() ? base : base + "." + key;
    }

    private static boolean bool(Object value) {
        if (value instanceof Boolean bool) return bool;
        if (value instanceof Number number) return number.doubleValue() != 0;
        return value != null && Boolean.parseBoolean(String.valueOf(value));
    }

    private static double number(Object value) {
        if (value instanceof Number number) return number.doubleValue();
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (RuntimeException error) {
            return 0;
        }
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("progressivestages", path.toLowerCase(Locale.ROOT));
    }
}
