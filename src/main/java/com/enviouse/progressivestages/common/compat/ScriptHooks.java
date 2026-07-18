package com.enviouse.progressivestages.common.compat;

import com.mojang.logging.LogUtils;
import com.enviouse.progressivestages.common.rehaul.action.ActionResult;
import com.enviouse.progressivestages.common.rehaul.selector.SelectorTarget;
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

    @FunctionalInterface public interface ScriptActionCallback {
        Object execute(ServerPlayer player, Map<String, Object> arguments);
    }

    @FunctionalInterface public interface ScriptSelectorCallback {
        boolean test(String value, SelectorTarget target);
    }

    @FunctionalInterface public interface ScriptMeasureCallback {
        double amount(String subject, String event, double amount, Map<String, Object> properties,
                      Map<String, Object> filters);
    }

    @FunctionalInterface public interface StructuredEventCallback {
        void accept(String event, Map<String, Object> data);
    }

    private static final CopyOnWriteArrayList<StageCallback> GRANTED = new CopyOnWriteArrayList<>();
    private static final CopyOnWriteArrayList<StageCallback> REVOKED = new CopyOnWriteArrayList<>();
    private static final CopyOnWriteArrayList<StageChangeCallback> CHANGED = new CopyOnWriteArrayList<>();
    private static final Map<String, StagePredicate> CONDITIONS = new ConcurrentHashMap<>();
    private static final Map<String, StageProgressProvider> PROGRESS = new ConcurrentHashMap<>();
    private static final Map<String, ScriptActionCallback> ACTIONS = new ConcurrentHashMap<>();
    private static final Map<String, ScriptSelectorCallback> SELECTORS = new ConcurrentHashMap<>();
    private static final Map<String, ScriptMeasureCallback> MEASURES = new ConcurrentHashMap<>();
    private static final CopyOnWriteArrayList<StructuredEventCallback> EVENTS = new CopyOnWriteArrayList<>();
    private static volatile boolean active = false;

    private ScriptHooks() {}

    /** Cleared at the start of each server-script reload so handlers don't accumulate. */
    public static void reset() {
        GRANTED.clear();
        REVOKED.clear();
        CHANGED.clear();
        CONDITIONS.clear();
        PROGRESS.clear();
        ACTIONS.clear();
        SELECTORS.clear();
        MEASURES.clear();
        EVENTS.clear();
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

    public static void registerAction(String id, ScriptActionCallback callback) {
        String key = normalizeId(id);
        if (!key.isEmpty() && callback != null) { ACTIONS.put(key, callback); active = true; }
    }

    public static void registerSelector(String id, ScriptSelectorCallback callback) {
        String key = normalizeId(id);
        if (!key.isEmpty() && callback != null) { SELECTORS.put(key, callback); active = true; }
    }

    public static void registerMeasure(String id, ScriptMeasureCallback callback) {
        String key = normalizeId(id);
        if (!key.isEmpty() && callback != null) { MEASURES.put(key, callback); active = true; }
    }

    public static void onEvent(StructuredEventCallback callback) {
        if (callback != null) { EVENTS.add(callback); active = true; }
    }

    public static void fireEvent(String event, Map<String, Object> data) {
        Map<String, Object> immutable = data == null ? Map.of() : Map.copyOf(data);
        for (StructuredEventCallback callback : EVENTS) {
            try { callback.accept(event, immutable); }
            catch (Throwable error) { LOGGER.error("[ProgressiveStages] KubeJS structured event callback threw", error); }
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

    public static ActionResult runAction(String id, ServerPlayer player, Map<String, Object> arguments) {
        ScriptActionCallback callback = ACTIONS.get(normalizeId(id));
        if (callback == null) return ActionResult.failure("missing_script_action", "The script action is unavailable");
        try {
            Object result = callback.execute(player, arguments == null ? Map.of() : Map.copyOf(arguments));
            if (result instanceof Boolean bool && !bool) return ActionResult.failure("script_rejected", "The script action rejected the request");
            if (result instanceof Map<?, ?> map && Boolean.FALSE.equals(map.get("success"))) {
                Object code = map.containsKey("code") ? map.get("code") : "script_rejected";
                Object explanation = map.containsKey("explanation") ? map.get("explanation")
                    : "The script action rejected the request";
                return ActionResult.failure(String.valueOf(code), String.valueOf(explanation));
            }
            return ActionResult.success("Script action completed", result);
        } catch (Throwable error) {
            LOGGER.error("[ProgressiveStages] KubeJS action '{}' threw", id, error);
            return ActionResult.failure("script_error", error.getMessage());
        }
    }

    public static boolean evalSelector(String id, String value, SelectorTarget target) {
        ScriptSelectorCallback callback = SELECTORS.get(normalizeId(id));
        if (callback == null) return false;
        try {
            return callback.test(value, target);
        } catch (Throwable error) {
            LOGGER.error("[ProgressiveStages] KubeJS selector '{}' threw", id, error);
            return false;
        }
    }

    public static double evalMeasure(String id, String subject, String event, double amount,
                                     Map<String, Object> properties, Map<String, Object> filters) {
        ScriptMeasureCallback callback = MEASURES.get(normalizeId(id));
        if (callback == null) return 0;
        try {
            double value = callback.amount(subject, event, amount, properties, filters);
            return Double.isFinite(value) ? value : 0;
        } catch (Throwable error) {
            LOGGER.error("[ProgressiveStages] KubeJS challenge measure '{}' threw", id, error);
            return 0;
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
