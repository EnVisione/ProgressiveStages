package com.enviouse.progressivestages.common.rehaul;

import com.enviouse.progressivestages.common.lock.PrefixEntry;
import net.minecraft.resources.ResourceLocation;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record SelectorSpec(
        ResourceLocation matcherId,
        String raw,
        String value,
        ResourceLocation resourceId,
        Map<String, Object> arguments,
        Integer explicitPriority,
        String label,
        ConfigProvenance provenance) {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("progressivestages", "id");
    public static final ResourceLocation MOD = ResourceLocation.fromNamespaceAndPath("progressivestages", "mod");
    public static final ResourceLocation TAG = ResourceLocation.fromNamespaceAndPath("progressivestages", "tag");
    public static final ResourceLocation NAME = ResourceLocation.fromNamespaceAndPath("progressivestages", "name");

    public SelectorSpec {
        Objects.requireNonNull(matcherId, "matcherId");
        raw = Objects.requireNonNull(raw, "raw").trim();
        value = Objects.requireNonNull(value, "value").trim();
        arguments = arguments == null ? Map.of() : Map.copyOf(arguments);
        label = label == null ? "" : label.trim();
        if (raw.isEmpty() || value.isEmpty()) throw new IllegalArgumentException("Selector values cannot be blank");
    }

    public SelectorSpec(ResourceLocation matcherId, String raw, String value, ResourceLocation resourceId) {
        this(matcherId, raw, value, resourceId, Map.of(), null, "", null);
    }

    public static Optional<SelectorSpec> parse(String raw) {
        return com.enviouse.progressivestages.common.rehaul.selector.SelectorMatcherRegistry.get().parse(raw);
    }

    public static SelectorSpec fromPrefix(PrefixEntry prefix) {
        Objects.requireNonNull(prefix, "prefix");
        SelectorSpec parsed = switch (prefix.kind()) {
            case ID -> new SelectorSpec(ID, prefix.raw(), prefix.value(), prefix.id());
            case MOD -> new SelectorSpec(MOD, prefix.raw(), prefix.value().toLowerCase(Locale.ROOT), null);
            case TAG -> new SelectorSpec(TAG, prefix.raw(), prefix.value(), prefix.id());
            case NAME -> new SelectorSpec(NAME, prefix.raw(), prefix.value().toLowerCase(Locale.ROOT), null);
        };
        int marker = prefix.raw().lastIndexOf("|priority=");
        return marker > 0 ? parsed.withPriority(Integer.parseInt(prefix.raw().substring(marker + 10).trim())) : parsed;
    }

    public boolean matchesIdOnly(ResourceLocation candidate) {
        if (candidate == null) return false;
        if (matcherId.equals(ID)) return candidate.equals(resourceId);
        if (matcherId.equals(MOD)) return candidate.getNamespace().equalsIgnoreCase(value);
        if (matcherId.equals(NAME)) return candidate.toString().toLowerCase(Locale.ROOT).contains(value);
        return false;
    }

    public boolean isTag() {
        return matcherId.equals(TAG);
    }

    public SelectorSpec withPriority(Integer priority) {
        return new SelectorSpec(matcherId, raw, value, resourceId, arguments, priority, label, provenance);
    }

    public SelectorSpec withMetadata(Map<String, Object> newArguments, String newLabel,
                                     ConfigProvenance newProvenance) {
        return new SelectorSpec(matcherId, raw, value, resourceId, newArguments, explicitPriority,
            newLabel, newProvenance);
    }
}
