package com.enviouse.progressivestages.common.rehaul.selector;

import com.enviouse.progressivestages.common.lock.PrefixEntry;
import com.enviouse.progressivestages.common.rehaul.SelectorSpec;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class SelectorMatcherRegistry {

    private static final SelectorMatcherRegistry INSTANCE = new SelectorMatcherRegistry();
    private volatile Map<ResourceLocation, SelectorMatcher> byId;
    private volatile Map<String, SelectorMatcher> byPrefix;

    private SelectorMatcherRegistry() {
        Map<ResourceLocation, SelectorMatcher> ids = new LinkedHashMap<>();
        Map<String, SelectorMatcher> prefixes = new LinkedHashMap<>();
        for (SelectorMatcher matcher : BuiltinSelectorMatchers.all()) {
            ids.put(matcher.id(), matcher);
            prefixes.put(matcher.prefix(), matcher);
        }
        byId = Map.copyOf(ids);
        byPrefix = Map.copyOf(prefixes);
    }

    public static SelectorMatcherRegistry get() {
        return INSTANCE;
    }

    public synchronized void register(SelectorMatcher matcher) {
        if (byId.containsKey(matcher.id())) throw new IllegalArgumentException("Duplicate selector matcher. " + matcher.id());
        String prefix = normalizePrefix(matcher.prefix());
        if (byPrefix.containsKey(prefix)) throw new IllegalArgumentException("Duplicate selector prefix. " + prefix);
        Map<ResourceLocation, SelectorMatcher> ids = new LinkedHashMap<>(byId);
        Map<String, SelectorMatcher> prefixes = new LinkedHashMap<>(byPrefix);
        ids.put(matcher.id(), matcher);
        prefixes.put(prefix, matcher);
        byId = Map.copyOf(ids);
        byPrefix = Map.copyOf(prefixes);
    }

    public Optional<SelectorSpec> parse(String input) {
        if (input == null || input.isBlank()) return Optional.empty();
        ParsedInput parsed = parsePriority(input.trim());
        String raw = parsed.selector();
        if (raw.startsWith("#")) {
            return byPrefix.get("tag").parse(input.trim(), raw.substring(1).trim(), parsed.priority());
        }
        int separator = raw.indexOf(':');
        if (separator > 0) {
            String prefix = normalizePrefix(raw.substring(0, separator));
            SelectorMatcher matcher = byPrefix.get(prefix);
            if (matcher != null) {
                return matcher.parse(input.trim(), raw.substring(separator + 1).trim(), parsed.priority());
            }
        }
        PrefixEntry legacy = PrefixEntry.parse(raw);
        if (legacy == null) return Optional.empty();
        return Optional.of(SelectorSpec.fromPrefix(legacy).withPriority(parsed.priority()));
    }

    public SelectorMatch match(SelectorSpec selector, SelectorTarget target) {
        SelectorMatcher matcher = byId.get(selector.matcherId());
        return matcher == null ? SelectorMatch.no("Selector matcher is unavailable") : matcher.match(selector, target);
    }

    public Map<ResourceLocation, SelectorMatcher> matchers() {
        return byId;
    }

    private static ParsedInput parsePriority(String value) {
        int marker = value.lastIndexOf("|priority=");
        if (marker < 1) return new ParsedInput(value, null);
        String number = value.substring(marker + 10).trim();
        try {
            return new ParsedInput(value.substring(0, marker).trim(), Integer.parseInt(number));
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("Invalid selector priority. " + number, error);
        }
    }

    private static String normalizePrefix(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private record ParsedInput(String selector, Integer priority) {}
}
