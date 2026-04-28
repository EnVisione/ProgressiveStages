package com.enviouse.progressivestages.compat;

import com.enviouse.progressivestages.compat.curios.CuriosCompat;
import com.enviouse.progressivestages.compat.kubejs.KubeJSStagesCompat;
import com.enviouse.progressivestages.compat.lootr.LootrCompat;
import com.enviouse.progressivestages.compat.mekanism.MekanismCompat;
import com.enviouse.progressivestages.compat.naturescompass.NaturesCompassCompat;
import com.mojang.logging.LogUtils;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;

/**
 * Registers per-mod compat modules if their host mod is loaded. Called from
 * {@link com.enviouse.progressivestages.server.ServerEventHandler#onServerStarting}
 * after the stage file loader is up, so the compat modules can resolve stage IDs.
 *
 * <p>Each module is a no-op when its host mod isn't present — no hard dependency,
 * no class-loading traps. Modules use reflection-free {@code ModList.isLoaded}
 * gates; if they're active and the host mod's API shifts between versions, the
 * module catches {@link Throwable} and logs rather than crashing the server.
 */
public final class ModCompatRegistry {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static boolean initialized = false;

    private ModCompatRegistry() {}

    public static void initializeAll() {
        if (initialized) return;
        initialized = true;

        tryLoad("naturescompass", NaturesCompassCompat::init);
        tryLoad("curios",         CuriosCompat::init);
        tryLoad("mekanism",       MekanismCompat::init);
        tryLoad("kubejs",         KubeJSStagesCompat::init);
        tryLoad("lootr",          LootrCompat::init);
        // Create + Botany Pots need no additional hook — their harvest paths fire vanilla
        // BlockDropsEvent / LivingDropsEvent, which our LootEnforcer + GLM already gate.
        AutomationCompatNotes.logCoverageReport();
    }

    private static void tryLoad(String modId, Runnable loader) {
        if (!ModList.get().isLoaded(modId)) {
            LOGGER.debug("[ProgressiveStages] mod '{}' not present, skipping compat module", modId);
            return;
        }
        try {
            loader.run();
            LOGGER.info("[ProgressiveStages] Loaded compat module for '{}'", modId);
        } catch (Throwable t) {
            LOGGER.warn("[ProgressiveStages] Compat module for '{}' failed to initialize — continuing without it: {}",
                modId, t.toString());
        }
    }
}
