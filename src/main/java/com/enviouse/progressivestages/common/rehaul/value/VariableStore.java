package com.enviouse.progressivestages.common.rehaul.value;

import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class VariableStore {

    private volatile Map<ResourceLocation, VariableDefinition> definitions = Map.of();
    private final Map<Key, Object> values = new ConcurrentHashMap<>();

    public synchronized void rebuild(java.util.Collection<VariableDefinition> variables) {
        Map<ResourceLocation, VariableDefinition> next = new LinkedHashMap<>();
        for (VariableDefinition variable : variables == null ? java.util.List.<VariableDefinition>of() : variables) {
            if (next.putIfAbsent(variable.id(), variable) != null) throw new IllegalArgumentException("Duplicate variable. " + variable.id());
        }
        definitions = Map.copyOf(next);
        values.keySet().removeIf(key -> !definitions.containsKey(key.variable()));
    }

    public Object get(String subject, ResourceLocation id) {
        VariableDefinition definition = require(id);
        return values.getOrDefault(new Key(subject, id), definition.defaultValue());
    }

    public Object set(String subject, ResourceLocation id, Object value) {
        VariableDefinition definition = require(id);
        Object validated = validate(definition, value);
        values.put(new Key(subject, id), validated);
        return validated;
    }

    public double add(String subject, ResourceLocation id, double amount) {
        VariableDefinition definition = require(id);
        if (!numeric(definition.type())) throw new IllegalArgumentException("Variable is not numeric. " + id);
        double next = number(get(subject, id)) + amount;
        return number(set(subject, id, next));
    }

    public Map<String, Object> snapshot() {
        Map<String, Object> output = new LinkedHashMap<>();
        values.forEach((key, value) -> output.put(key.subject() + "|" + key.variable(), value));
        return Map.copyOf(output);
    }

    public Map<String, Object> persistentSnapshot() {
        Map<String, Object> output = new LinkedHashMap<>();
        values.forEach((key, value) -> {
            VariableDefinition definition = definitions.get(key.variable());
            if (definition != null && definition.persistent()) {
                output.put(key.subject() + "|" + key.variable(), value);
            }
        });
        return Map.copyOf(output);
    }

    public void restore(Map<String, Object> snapshot) {
        values.clear();
        if (snapshot == null) return;
        snapshot.forEach((key, value) -> {
            int separator = key.indexOf('|');
            if (separator < 1) return;
            ResourceLocation id = ResourceLocation.tryParse(key.substring(separator + 1));
            VariableDefinition definition = id == null ? null : definitions.get(id);
            if (definition != null && definition.persistent()) set(key.substring(0, separator), id, value);
        });
    }

    public void clear() {
        values.clear();
    }

    public Map<ResourceLocation, Object> subjectSnapshot(String subject) {
        Map<ResourceLocation, Object> output = new LinkedHashMap<>();
        values.forEach((key, value) -> {
            if (key.subject().equals(subject)) output.put(key.variable(), value);
        });
        return Map.copyOf(output);
    }

    public void restoreSubject(String subject, Map<ResourceLocation, Object> snapshot) {
        clearSubject(subject);
        snapshot.forEach((id, value) -> set(subject, id, value));
    }

    public boolean exists(ResourceLocation id) {
        return definitions.containsKey(id);
    }

    public Map<String, Double> numericValues(String subject) {
        Map<String, Double> output = new LinkedHashMap<>();
        definitions.forEach((id, definition) -> {
            if (numeric(definition.type())) output.put(id.toString(), number(get(subject, id)));
        });
        return Map.copyOf(output);
    }

    public void clearSubject(String subject) {
        values.keySet().removeIf(key -> key.subject().equals(subject));
    }

    private VariableDefinition require(ResourceLocation id) {
        VariableDefinition definition = definitions.get(id);
        if (definition == null) throw new IllegalArgumentException("Unknown variable. " + id);
        return definition;
    }

    private static Object validate(VariableDefinition definition, Object value) {
        return switch (definition.type()) {
            case INTEGER, COUNTER -> (long) clamp(number(value), definition.minimum(), definition.maximum());
            case DECIMAL, CURRENCY -> clamp(number(value), definition.minimum(), definition.maximum());
            case BOOLEAN -> value instanceof Boolean bool ? bool : Boolean.parseBoolean(String.valueOf(value));
            case STRING -> String.valueOf(value);
        };
    }

    private static boolean numeric(VariableType type) {
        return type == VariableType.INTEGER || type == VariableType.DECIMAL
            || type == VariableType.CURRENCY || type == VariableType.COUNTER;
    }

    private static double clamp(double value, double minimum, double maximum) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Variable value must be finite");
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static double number(Object value) {
        if (value instanceof Number number) return number.doubleValue();
        return Double.parseDouble(String.valueOf(value));
    }

    private record Key(String subject, ResourceLocation variable) {}
}
