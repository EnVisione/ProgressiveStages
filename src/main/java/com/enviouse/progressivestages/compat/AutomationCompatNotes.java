package com.enviouse.progressivestages.compat;

import com.mojang.logging.LogUtils;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;

/**
 * Startup log + documentation anchor for modded-automation compat coverage. Called from
 * {@link ModCompatRegistry#initializeAll()} to produce a single consolidated report so
 * modpack makers know exactly what's gated when a given automation mod is present.
 *
 * <p>Coverage rules of thumb:
 * <ul>
 *   <li><b>Create</b> — Harvesters, Deployers, Drills, Saws, Mechanical Crafters, Mixers:
 *       when they break a block or drop items, they go through vanilla {@code BlockDropsEvent}
 *       which our {@code LootEnforcer} + GLM filter. Fully covered for drop filtering.</li>
 *   <li><b>Botany Pots</b> — Crops in pots drop through vanilla drop paths. Fully covered.</li>
 *   <li><b>Mekanism</b> — See {@link com.enviouse.progressivestages.compat.mekanism.MekanismCompat}.
 *       Machine and fluid coverage is complete via {@code [blocks]} and {@code [fluids]};
 *       pipe transport between machines is not gated (Mekanism's own API).</li>
 *   <li><b>Applied Energistics 2</b> — ME-network item transfers skip vanilla events but ME
 *       imports/exports go through items that we can gate at {@code [items]}.
 *       Recommended: gate the network's source storage block via {@code [blocks]}.</li>
 *   <li><b>Pipez / XNet / Laser IO</b> — same story as Mekanism pipes. Recommended: gate the
 *       block the pipe is extracting from.</li>
 * </ul>
 */
public final class AutomationCompatNotes {

    private static final Logger LOGGER = LogUtils.getLogger();

    private AutomationCompatNotes() {}

    public static void logCoverageReport() {
        StringBuilder report = new StringBuilder("[ProgressiveStages] Automation-mod coverage report:\n");
        report.append(line("create",        "FULL (drops via vanilla events)"));
        report.append(line("botanypots",    "FULL (drops via vanilla events)"));
        report.append(line("mekanism",      "PARTIAL (gate machine blocks; pipe-transport uncovered)"));
        report.append(line("ae2",           "PARTIAL (gate ME source; network transfer uncovered)"));
        report.append(line("pipez",         "PARTIAL (gate source block)"));
        report.append(line("xnet",          "PARTIAL (gate source block)"));
        report.append(line("laserio",       "PARTIAL (gate source block)"));
        LOGGER.info(report.toString().trim());
    }

    private static String line(String modId, String coverage) {
        if (ModList.get().isLoaded(modId)) {
            return "  • " + modId + " (loaded): " + coverage + "\n";
        }
        return "";
    }
}
