package com.enviouse.progressivestages.common.rehaul.template;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class TemplateEngine {

    private volatile Map<ResourceLocation, TemplateDefinition> templates = Map.of();

    public synchronized void rebuild(java.util.Collection<TemplateDefinition> definitions) {
        Map<ResourceLocation, TemplateDefinition> next = new LinkedHashMap<>();
        for (TemplateDefinition template : definitions == null ? List.<TemplateDefinition>of() : definitions) {
            if (next.putIfAbsent(template.id(), template) != null) throw new IllegalArgumentException("Duplicate template. " + template.id());
        }
        for (ResourceLocation id : next.keySet()) validateIncludes(id, next, new LinkedHashSet<>(), new LinkedHashSet<>());
        templates = Map.copyOf(next);
    }

    public Map<String, Object> expand(ResourceLocation id, Map<String, Object> arguments) {
        return expand(id, arguments == null ? Map.of() : arguments, new LinkedHashSet<>());
    }

    private Map<String, Object> expand(ResourceLocation id, Map<String, Object> arguments,
                                       Set<ResourceLocation> visiting) {
        TemplateDefinition template = templates.get(id);
        if (template == null) throw new IllegalArgumentException("Unknown template. " + id);
        if (!visiting.add(id)) throw new IllegalArgumentException("Template include cycle. " + visiting);
        Map<String, Object> values = validateArguments(template, arguments);
        Map<String, Object> output = new LinkedHashMap<>();
        for (ResourceLocation include : template.includes()) {
            TemplateDefinition included = templates.get(include);
            Map<String, Object> includeArguments = new LinkedHashMap<>();
            for (String parameter : included.parameters().keySet()) {
                if (values.containsKey(parameter)) includeArguments.put(parameter, values.get(parameter));
            }
            merge(output, expand(include, includeArguments, visiting), template.mergePolicy());
        }
        merge(output, substituteMap(template.fragment(), values), template.mergePolicy());
        visiting.remove(id);
        return Map.copyOf(output);
    }

    private static Map<String, Object> validateArguments(TemplateDefinition template, Map<String, Object> arguments) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (TemplateParameter parameter : template.parameters().values()) {
            Object value = arguments.containsKey(parameter.name()) ? arguments.get(parameter.name()) : parameter.defaultValue();
            if (value == null && parameter.required()) throw new IllegalArgumentException("Missing template parameter. " + parameter.name());
            if (value != null) values.put(parameter.name(), validateType(parameter, value));
        }
        for (String key : arguments.keySet()) {
            if (!template.parameters().containsKey(key)) throw new IllegalArgumentException("Unknown template parameter. " + key);
        }
        return Map.copyOf(values);
    }

    private static Object validateType(TemplateParameter parameter, Object value) {
        boolean valid = switch (parameter.type()) {
            case STRING, RESOURCE_ID, STAGE_ID, SELECTOR, DURATION -> value instanceof String;
            case INTEGER -> value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long;
            case DECIMAL -> value instanceof Number;
            case BOOLEAN -> value instanceof Boolean;
            case LIST -> value instanceof List<?>;
            case TABLE -> value instanceof Map<?, ?>;
        };
        if (!valid) throw new IllegalArgumentException("Template parameter has the wrong type. " + parameter.name());
        return value;
    }

    private static Map<String, Object> substituteMap(Map<String, Object> source, Map<String, Object> values) {
        Map<String, Object> output = new LinkedHashMap<>();
        source.forEach((key, value) -> output.put(key, substitute(value, values)));
        return output;
    }

    private static Object substitute(Object source, Map<String, Object> values) {
        if (source instanceof String text) {
            if (text.startsWith("${") && text.endsWith("}") && text.indexOf("}") == text.length() - 1) {
                String key = text.substring(2, text.length() - 1);
                if (values.containsKey(key)) return values.get(key);
            }
            String result = text;
            for (Map.Entry<String, Object> value : values.entrySet()) {
                result = result.replace("${" + value.getKey() + "}", String.valueOf(value.getValue()));
            }
            return result;
        }
        if (source instanceof Map<?, ?> map) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            map.forEach((key, value) -> normalized.put(String.valueOf(key), substitute(value, values)));
            return normalized;
        }
        if (source instanceof List<?> list) return list.stream().map(value -> substitute(value, values)).toList();
        return source;
    }

    @SuppressWarnings("unchecked")
    private static void merge(Map<String, Object> target, Map<String, Object> source, MergePolicy policy) {
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            Object current = target.get(entry.getKey());
            if (current == null) { target.put(entry.getKey(), entry.getValue()); continue; }
            if (policy == MergePolicy.ERROR) throw new IllegalArgumentException("Template merge conflict. " + entry.getKey());
            if (policy == MergePolicy.REPLACE) { target.put(entry.getKey(), entry.getValue()); continue; }
            if (current instanceof Map<?, ?> left && entry.getValue() instanceof Map<?, ?> right
                    && policy == MergePolicy.DEEP_MERGE) {
                Map<String, Object> nested = new LinkedHashMap<>((Map<String, Object>) left);
                merge(nested, (Map<String, Object>) right, policy);
                target.put(entry.getKey(), nested);
            } else if (current instanceof List<?> left && entry.getValue() instanceof List<?> right) {
                List<Object> values = new ArrayList<>();
                if (policy == MergePolicy.PREPEND) { values.addAll(right); values.addAll(left); }
                else { values.addAll(left); values.addAll(right); }
                target.put(entry.getKey(), List.copyOf(values));
            } else target.put(entry.getKey(), entry.getValue());
        }
    }

    private static void validateIncludes(ResourceLocation id, Map<ResourceLocation, TemplateDefinition> templates,
                                         Set<ResourceLocation> visiting, Set<ResourceLocation> visited) {
        if (visiting.contains(id)) throw new IllegalArgumentException("Template include cycle. " + visiting + ". " + id);
        if (!visited.add(id)) return;
        TemplateDefinition template = templates.get(id);
        if (template == null) throw new IllegalArgumentException("Unknown template include. " + id);
        visiting.add(id);
        for (ResourceLocation include : template.includes()) validateIncludes(include, templates, visiting, visited);
        visiting.remove(id);
    }
}
