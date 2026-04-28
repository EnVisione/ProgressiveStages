package com.enviouse.progressivestages.compat.visualworkbench;

import com.mojang.logging.LogUtils;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

import java.lang.reflect.Method;

/**
 * Reflection-only shim for Visual Workbench. When VW is loaded, it replaces vanilla
 * crafting tables with its own variants. Without this shim, locks targeting
 * minecraft:crafting_table would not match VW-replaced workbenches.
 *
 * resolveVanillaEquivalent(state) attempts to call VW's BlockConversionHandler reflectively
 * to recover the underlying vanilla BlockState. Returns null when VW is absent or the block
 * is not VW-replaceable.
 */
public final class VisualWorkbenchShim {

    private static final Logger LOGGER = LogUtils.getLogger();

    // Known class paths across VW forks/ports
    private static final String[] CANDIDATE_CLASSES = new String[] {
        "me.kpavlov.visualworkbench.BlockConversionHandler",
        "com.tfar.craftingstation.block.BlockConversionHandler",
        "com.lothrazar.visualworkbench.BlockConversionHandler"
    };

    private static volatile Boolean visualWorkbenchAvailable = null;
    private static volatile Method convertMethod = null;

    private VisualWorkbenchShim() {}

    /**
     * If Visual Workbench is loaded and the given state is a VW-replaced workbench,
     * return the underlying vanilla BlockState. Otherwise return null.
     */
    public static BlockState resolveVanillaEquivalent(BlockState state) {
        if (state == null) return null;

        Boolean available = visualWorkbenchAvailable;
        if (available == null) {
            available = tryResolveMethod();
            visualWorkbenchAvailable = available;
        }
        if (!available) return null;

        Method m = convertMethod;
        if (m == null) return null;

        try {
            Object result = m.invoke(null, state);
            if (result instanceof BlockState bs && bs != state) {
                return bs;
            }
        } catch (Throwable ignored) {
            // VW may throw on unsupported states; treat as not-applicable
        }
        return null;
    }

    private static boolean tryResolveMethod() {
        for (String cls : CANDIDATE_CLASSES) {
            try {
                Class<?> c = Class.forName(cls);
                Method m = c.getMethod("convertToVanillaBlock", BlockState.class);
                convertMethod = m;
                LOGGER.debug("[ProgressiveStages] Visual Workbench shim resolved: {}", cls);
                return true;
            } catch (ClassNotFoundException | NoSuchMethodException ignored) {
                // try next
            } catch (Throwable t) {
                LOGGER.debug("[ProgressiveStages] VW shim probe failed for {}: {}", cls, t.getMessage());
            }
        }
        return false;
    }
}
