package com.enviouse.progressivestages.common.compat;

import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * v2.5: dependency-free seam scripts (KubeJS) plug into. Holds the callbacks and custom-condition
 * predicates a script registers, and is invoked by ProgressiveStages' own engine — so this class has
 * NO KubeJS import and is safe to reference from the trigger evaluator and stage-change path whether
 * or not KubeJS is installed (the maps are simply empty when nothing registered them).
 *
 * <p>The functional interfaces are plain Java SAMs; KubeJS/Rhino converts a script lambda to one at
 * the binding call site (see {@code ProgressiveStagesKubeJSPlugin}). Registrations are cleared each
 * server-script reload so a {@code /reload} doesn't stack duplicate handlers.
 */
public final class ScriptHooks {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** A script reaction to a stage being granted/revoked: {@code (player, stageId) -> {}}. */
    @FunctionalInterface public interface StageCallback { void accept(ServerPlayer player, String stage); }

    /** Rich lifecycle callback: player, stage, change type, cause, and concrete storage/team id. */
    @FunctionalInterface public interface StageChangeCallback {
        void accept(ServerPlayer player, String stage, String change, String cause, String teamId);
    }

    /** A script-evaluated trigger condition: returns true when the stage's {@code script:<id>} is met. */
    @FunctionalInterface public interface StagePredicate { boolean test(ServerPlayer player); }

    /** A script-supplied numeric progress source for {@code type = "script_value"}. */
    @FunctionalInterface public interface StageProgressProvider { long get(ServerPlayer player); }

    private static final CopyOnWriteArrayList<StageCallback> GRANTED = new CopyOnWriteArrayList<>();
    private static final CopyOnWriteArrayList<StageCallback> REVOKED = new CopyOnWriteArrayList<>();
    private static final CopyOnWriteArrayList<StageChangeCallback> CHANGED = new CopyOnWriteArrayList<>();
    private static final Map<String, StagePredicate> CONDITIONS = new ConcurrentHashMap<>();
    private static final Map<String, StageProgressProvider> PROGRESS = new ConcurrentHashMap<>();
    private static volatile boolean active = false;

    private ScriptHooks() {}

    /** Cleared at the start of each server-script reload so handlers don't accumulate. */
    public static void reset() {
        GRANTED.clear();
        REVOKED.clear();
        CHANGED.clear();
        CONDITIONS.clear();
        PROGRESS.clear();
        active = false;
    }

    public static void onGranted(StageCallback cb) { if (cb != null) { GRANTED.add(cb); active = true; } }
    public static void onRevoked(StageCallback cb) { if (cb != null) { REVOKED.add(cb); active = true; } }
    public static void onChanged(StageChangeCallback cb) { if (cb != null) { CHANGED.add(cb); active = true; } }
    public static void registerCondition(String id, StagePredicate pred) {
        String key = normalizeId(id);
        if (!key.isEmpty() && pred != null) { CONDITIONS.put(key, pred); active = true; }
    }
    public static void registerProgress(String id, StageProgressProvider provider) {
        String key = normalizeId(id);
        if (!key.isEmpty() && provider != null) {
            PROGRESS.put(key, provider);
            active = true;
        }
    }

    /** True if a script registered any callback or condition (cheap fast-path gate). */
    public static boolean isActive() { return active; }

    public static void fireGranted(ServerPlayer player, String stage) {
        for (StageCallback cb : GRANTED) invoke(cb, player, stage);
    }

    public static void fireRevoked(ServerPlayer player, String stage) {
        for (StageCallback cb : REVOKED) invoke(cb, player, stage);
    }

    public static void fireChanged(ServerPlayer player, String stage, String change,
                                   String cause, String teamId) {
        for (StageChangeCallback cb : CHANGED) {
            try {
                cb.accept(player, stage, change, cause, teamId);
            } catch (Throwable t) {
                LOGGER.error("[ProgressiveStages] KubeJS rich stage callback threw", t);
            }
        }
    }

    /** Evaluate a {@code script:<id>} trigger condition; false if no script registered that id. */
    public static boolean evalCondition(String id, ServerPlayer player) {
        StagePredicate pred = CONDITIONS.get(normalizeId(id));
        if (pred == null) return false;
        try {
            return pred.test(player);
        } catch (Throwable t) {
            LOGGER.error("[ProgressiveStages] KubeJS custom condition '{}' threw", id, t);
            return false;
        }
    }

    /** Evaluate a numeric script progress provider; returns zero if it is absent or throws. */
    public static long evalProgress(String id, ServerPlayer player) {
        StageProgressProvider provider = PROGRESS.get(normalizeId(id));
        if (provider == null) return 0L;
        try {
            return Math.max(0L, provider.get(player));
        } catch (Throwable t) {
            LOGGER.error("[ProgressiveStages] KubeJS progress provider '{}' threw", id, t);
            return 0L;
        }
    }

    private static void invoke(StageCallback cb, ServerPlayer player, String stage) {
        try {
            cb.accept(player, stage);
        } catch (Throwable t) {
            LOGGER.error("[ProgressiveStages] KubeJS stage callback threw", t);
        }
    }

    private static String normalizeId(String id) {
        return id == null ? "" : id.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
