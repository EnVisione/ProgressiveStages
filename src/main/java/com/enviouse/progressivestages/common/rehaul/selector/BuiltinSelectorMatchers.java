package com.enviouse.progressivestages.common.rehaul.selector;

import com.enviouse.progressivestages.common.rehaul.SelectorSpec;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiPredicate;

final class BuiltinSelectorMatchers {

    private BuiltinSelectorMatchers() {}

    static List<SelectorMatcher> all() {
        return List.of(
            resourceMatcher("id", SelectorSpec.ID, 400, (selector, target) -> target.id().equals(selector.resourceId())),
            textMatcher("mod", SelectorSpec.MOD, 100,
                (selector, target) -> target.id().getNamespace().equalsIgnoreCase(selector.value())),
            resourceMatcher("tag", SelectorSpec.TAG, 300,
                (selector, target) -> target.tags().contains(selector.resourceId())),
            textMatcher("name", SelectorSpec.NAME, 200,
                (selector, target) -> target.id().toString().toLowerCase(Locale.ROOT).contains(selector.value())),
            propertyMatcher("component", "component", 350),
            propertyMatcher("component_value", "component_value", 360),
            propertyMatcher("recipe_type", "recipe_type", 340),
            propertyMatcher("rarity", "rarity", 330),
            propertyMatcher("creative_tab", "creative_tab", 330),
            propertyMatcher("equipment_slot", "equipment_slot", 330),
            propertyMatcher("entity_tag", "entity_tag", 330),
            propertyMatcher("block_state", "block_state", 350),
            propertyMatcher("fluid_property", "fluid_property", 350),
            propertyMatcher("enchantment", "enchantment", 350),
            propertyMatcher("structure_tag", "structure_tag", 330),
            propertyMatcher("dimension_tag", "dimension_tag", 330),
            propertyMatcher("kubejs", "kubejs", 320));
    }

    private static SelectorMatcher resourceMatcher(String prefix, ResourceLocation id, int specificity,
                                                    BiPredicate<SelectorSpec, SelectorTarget> predicate) {
        return new BasicMatcher(prefix, id, specificity, true, prefix, predicate);
    }

    private static SelectorMatcher textMatcher(String prefix, ResourceLocation id, int specificity,
                                                BiPredicate<SelectorSpec, SelectorTarget> predicate) {
        return new BasicMatcher(prefix, id, specificity, false, prefix, predicate);
    }

    private static SelectorMatcher propertyMatcher(String prefix, String property, int specificity) {
        ResourceLocation id = id(prefix);
        return new BasicMatcher(prefix, id, specificity, false, property, (selector, target) -> {
            Object actual = target.properties().get(property);
            if (actual instanceof Iterable<?> values) {
                for (Object value : values) if (selector.value().equalsIgnoreCase(String.valueOf(value))) return true;
                return false;
            }
            return actual != null && selector.value().equalsIgnoreCase(String.valueOf(actual));
        });
    }

    private record BasicMatcher(String prefix, ResourceLocation id, int specificity, boolean resourceValue,
                                String property, BiPredicate<SelectorSpec, SelectorTarget> predicate)
            implements SelectorMatcher {

        @Override
        public Optional<SelectorSpec> parse(String raw, String value, Integer priority) {
            if (value == null || value.isBlank()) return Optional.empty();
            ResourceLocation resource = resourceValue ? ResourceLocation.tryParse(value) : null;
            if (resourceValue && resource == null) return Optional.empty();
            String normalized = resourceValue ? value : value.toLowerCase(Locale.ROOT);
            return Optional.of(new SelectorSpec(id, raw, normalized, resource, Map.of("property", property),
                priority, "", null));
        }

        @Override
        public SelectorMatch match(SelectorSpec selector, SelectorTarget target) {
            boolean matches = predicate.test(selector, target);
            return matches ? SelectorMatch.yes(specificity, "Selector matched") : SelectorMatch.no("Selector did not match");
        }
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("progressivestages", path);
    }
}
