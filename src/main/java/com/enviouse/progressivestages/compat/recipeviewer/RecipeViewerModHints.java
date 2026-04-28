package com.enviouse.progressivestages.compat.recipeviewer;

import net.neoforged.fml.ModList;

import java.util.Optional;

/**
 * Resolves the owning mod id of a Java class via its module name. NeoForge mods are loaded
 * as named modules, so an EmiStack/JEI ingredient subclass that lives inside a mod jar can
 * be traced back to that mod even when its registry id is "minecraft:" (e.g., Mekanism gases
 * in some configurations).
 */
public final class RecipeViewerModHints {

    private RecipeViewerModHints() {}

    public static Optional<String> owningModIdForClass(Class<?> clazz) {
        if (clazz == null) return Optional.empty();
        Module module = clazz.getModule();
        if (module != null && module.isNamed()) {
            String name = module.getName();
            if (name != null && !name.isEmpty() && ModList.get().isLoaded(name)) {
                return Optional.of(name);
            }
        }
        return Optional.empty();
    }
}
