package com.enviouse.progressivestages.compat;

import com.mojang.logging.LogUtils;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;

/**
 * Registers per-mod compat modules if their host mod is loaded. Called from
 * {@link com.enviouse.progressivestages.server.ServerEventHandler#onServerStarting}
 * after the stage file loader is up, so the compat modules can resolve stage IDs.
 *
 * <p>Compat classes are loaded reflectively by FQN <em>after</em> the {@code ModList.isLoaded}
 * gate passes. Direct symbolic references (e.g. method references like {@code Foo::init})
 * would force the JVM to verify the compat class's imports at the call site, which fails
 * with {@code NoClassDefFoundError} when the host mod's classes aren't on the classpath.
 * Reflection defers classloading until after we've confirmed the mod is present.
 */
public final class ModCompatRegistry {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static boolean initialized = false;

    private ModCompatRegistry() {}

    public static void initializeAll() {
        if (initialized) return;
        initialized = true;

        tryLoad("naturescompass", "com.enviouse.progressivestages.compat.naturescompass.NaturesCompassCompat");
        tryLoad("curios",         "com.enviouse.progressivestages.compat.curios.CuriosCompat");
        tryLoad("mekanism",       "com.enviouse.progressivestages.compat.mekanism.MekanismCompat");
        tryLoad("kubejs",         "com.enviouse.progressivestages.compat.kubejs.KubeJSStagesCompat");
        tryLoad("lootr",          "com.enviouse.progressivestages.compat.lootr.LootrCompat");
        // Create + Botany Pots need no additional hook — their harvest paths fire vanilla
        // BlockDropsEvent / LivingDropsEvent, which our LootEnforcer + GLM already gate.
        AutomationCompatNotes.logCoverageReport();
    }

    private static void tryLoad(String modId, String compatFqn) {
        if (!ModList.get().isLoaded(modId)) {
            LOGGER.debug("[ProgressiveStages] mod '{}' not present, skipping compat module", modId);
            return;
        }
        try {
            Class.forName(compatFqn).getMethod("init").invoke(null);
            LOGGER.info("[ProgressiveStages] Loaded compat module for '{}'", modId);
        } catch (Throwable t) {
            LOGGER.warn("[ProgressiveStages] Compat module for '{}' failed to initialize — continuing without it: {}",
                modId, t.toString());
        }
    }
}
