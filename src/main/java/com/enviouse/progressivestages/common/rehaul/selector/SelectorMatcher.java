package com.enviouse.progressivestages.common.rehaul.selector;

import com.enviouse.progressivestages.common.rehaul.SelectorSpec;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

public interface SelectorMatcher {

    ResourceLocation id();

    String prefix();

    Optional<SelectorSpec> parse(String raw, String value, Integer priority);

    SelectorMatch match(SelectorSpec selector, SelectorTarget target);
}
