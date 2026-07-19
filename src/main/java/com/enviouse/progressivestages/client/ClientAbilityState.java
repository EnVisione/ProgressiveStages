package com.enviouse.progressivestages.client;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

public final class ClientAbilityState {
    private static volatile Set<String> locked = Set.of();

    private ClientAbilityState() {}

    public static void set(Collection<String> abilities) {
        if (abilities == null || abilities.isEmpty()) {
            locked = Set.of();
            return;
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String ability : abilities) {
            if (ability != null && !ability.isBlank()) normalized.add(ability.trim().toLowerCase(Locale.ROOT));
        }
        locked = Set.copyOf(normalized);
    }

    public static boolean isLocked(String ability) {
        return ability != null && locked.contains(ability.toLowerCase(Locale.ROOT));
    }

    public static Set<String> snapshot() {
        return locked;
    }

    public static void clear() {
        locked = Set.of();
    }
}
