package com.enviouse.progressivestages.common.rehaul.counter;

import com.enviouse.progressivestages.common.rehaul.condition.SubjectScope;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

public record CounterKey(String subject, SubjectScope scope, ResourceLocation metric,
                         CounterWindow window) {

    public CounterKey {
        subject = Objects.requireNonNull(subject, "subject").trim();
        scope = scope == null ? SubjectScope.PLAYER : scope;
        Objects.requireNonNull(metric, "metric");
        window = window == null ? CounterWindow.lifetime() : window;
    }
}
