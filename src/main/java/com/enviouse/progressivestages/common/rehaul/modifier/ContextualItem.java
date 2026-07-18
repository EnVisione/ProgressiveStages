package com.enviouse.progressivestages.common.rehaul.modifier;

import com.enviouse.progressivestages.common.rehaul.selector.SelectorTarget;

import java.util.Set;

public record ContextualItem(SelectorTarget target, int count, Set<ItemContext> contexts) {

    public ContextualItem {
        if (count < 1) throw new IllegalArgumentException("Item count must be positive");
        contexts = contexts == null ? Set.of() : Set.copyOf(contexts);
    }
}
