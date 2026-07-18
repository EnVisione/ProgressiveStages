package com.enviouse.progressivestages.common.rehaul.condition;

import com.enviouse.progressivestages.common.rehaul.ConditionNode;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ConditionCompiler {

    private final ConditionRegistry registry;

    public ConditionCompiler(ConditionRegistry registry) {
        this.registry = registry;
    }

    public ConditionNode compile(Object source) {
        if (source == null) return new ConditionNode.Constant(true);
        if (source instanceof Boolean bool) return new ConditionNode.Constant(bool);
        if (!(source instanceof Map<?, ?> raw)) throw new IllegalArgumentException("A condition must be a table");
        Map<String, Object> map = normalize(raw);
        if (map.containsKey("all") || map.containsKey("all_of")) return new ConditionNode.All(children(first(map, "all", "all_of")));
        if (map.containsKey("any") || map.containsKey("any_of")) return new ConditionNode.Any(children(first(map, "any", "any_of")));
        if (map.containsKey("not")) return new ConditionNode.Not(compile(map.get("not")));
        if (map.containsKey("at_least")) {
            Object rawAtLeast = map.get("at_least");
            if (rawAtLeast instanceof Number count) return new ConditionNode.AtLeast(count.intValue(), children(map.get("children")));
            Map<String, Object> group = normalize(asMap(rawAtLeast));
            return new ConditionNode.AtLeast(integer(group.get("count"), 1), children(group.get("children")));
        }
        if (map.containsKey("exactly")) {
            Object rawExactly = map.get("exactly");
            if (rawExactly instanceof Number count) return new ConditionNode.Exactly(count.intValue(), children(map.get("children")));
            Map<String, Object> group = normalize(asMap(rawExactly));
            return new ConditionNode.Exactly(integer(group.get("count"), 1), children(group.get("children")));
        }
        if (map.containsKey("sequence")) {
            Object rawSequence = map.get("sequence");
            Map<String, Object> group = rawSequence instanceof List<?> ? Map.of("children", rawSequence)
                : normalize(asMap(rawSequence));
            return new ConditionNode.Sequence(children(group.get("children")), duration(group.get("timeout")),
                bool(group.get("reset_on_failure"), true));
        }
        if (map.containsKey("reference")) return new ConditionNode.Reference(id(map.get("reference")));
        if (map.containsKey("comparison")) {
            Map<String, Object> comparison = normalize(asMap(map.get("comparison")));
            ResourceLocation provider = id(comparison.get("provider"));
            String operator = String.valueOf(comparison.getOrDefault("operator", "equal")).toUpperCase(Locale.ROOT);
            return new ConditionNode.Comparison(provider, ConditionNode.Comparison.Operator.valueOf(operator),
                comparison.get("expected"), without(comparison, "provider", "operator", "expected"));
        }
        Object typeValue = map.getOrDefault("type", map.get("condition"));
        if (typeValue == null) throw new IllegalArgumentException("Condition table is missing type");
        ResourceLocation providerId = id(typeValue);
        ConditionProvider provider = registry.find(providerId).orElse(null);
        boolean optional = bool(map.get("optional"), false);
        if (provider == null && !optional) throw new IllegalArgumentException("Unknown condition provider. " + providerId);
        Map<String, Object> arguments = without(map, "type", "condition");
        if (provider != null) provider.validate(arguments);
        return new ConditionNode.Leaf(providerId, arguments);
    }

    private List<ConditionNode> children(Object value) {
        if (!(value instanceof List<?> list) || list.isEmpty()) {
            throw new IllegalArgumentException("A condition group requires children");
        }
        List<ConditionNode> output = new ArrayList<>();
        for (Object child : list) output.add(compile(child));
        return List.copyOf(output);
    }

    private static Object first(Map<String, Object> map, String first, String second) {
        return map.containsKey(first) ? map.get(first) : map.get(second);
    }

    private static Map<?, ?> asMap(Object value) {
        if (value instanceof Map<?, ?> map) return map;
        throw new IllegalArgumentException("A condition group must be a table");
    }

    private static Map<String, Object> normalize(Map<?, ?> source) {
        Map<String, Object> output = new LinkedHashMap<>();
        source.forEach((key, value) -> output.put(String.valueOf(key), value));
        return output;
    }

    private static Map<String, Object> without(Map<String, Object> source, String... keys) {
        Map<String, Object> output = new LinkedHashMap<>(source);
        for (String key : keys) output.remove(key);
        return Map.copyOf(output);
    }

    private static int integer(Object value, int fallback) {
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private static boolean bool(Object value, boolean fallback) {
        if (value == null) return fallback;
        return value instanceof Boolean bool ? bool : Boolean.parseBoolean(String.valueOf(value));
    }

    private static long duration(Object value) {
        if (value == null) return 0;
        if (value instanceof Number number) return number.longValue();
        String text = String.valueOf(value).trim().toLowerCase(Locale.ROOT);
        long multiplier = 1;
        if (text.endsWith("ms")) text = text.substring(0, text.length() - 2);
        else if (text.endsWith("s")) { multiplier = 1_000; text = text.substring(0, text.length() - 1); }
        else if (text.endsWith("m")) { multiplier = 60_000; text = text.substring(0, text.length() - 1); }
        else if (text.endsWith("h")) { multiplier = 3_600_000; text = text.substring(0, text.length() - 1); }
        return Math.multiplyExact(Long.parseLong(text.trim()), multiplier);
    }

    private static ResourceLocation id(Object value) {
        if (value == null) throw new IllegalArgumentException("A condition ID is required");
        String text = String.valueOf(value).trim().toLowerCase(Locale.ROOT);
        return text.indexOf(':') >= 0 ? ResourceLocation.parse(text)
            : ResourceLocation.fromNamespaceAndPath("progressivestages", text);
    }
}
