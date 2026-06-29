package com.enviouse.progressivestages.compat.kubejs;

import com.enviouse.progressivestages.common.api.StageCause;
import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.compat.ScriptHooks;
import com.enviouse.progressivestages.common.stage.StageManager;
import com.enviouse.progressivestages.server.triggers.StageTriggerEvaluator;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * v2.5: the global {@code ProgressiveStages} object bound into KubeJS scripts. Gives packs deep,
 * server-authoritative control over stages:
 *
 * <pre>
 *   // react to engine grants/revokes (commands, triggers, quests, purchases, regression — all of them)
 *   ProgressiveStages.onGranted((player, stage) =&gt; player.tell('Unlocked ' + stage))
 *   ProgressiveStages.onRevoked((player, stage) =&gt; player.tell('Lost ' + stage))
 *
 *   // a fully custom trigger condition — reference it from a stage as: type = "script", id = "rich"
 *   ProgressiveStages.condition('rich', player =&gt; player.getMainHandItem().id == 'minecraft:diamond')
 *
 *   // imperative control from any KubeJS event
 *   ServerEvents.tick(e =&gt; e.server.players.forEach(p =&gt; {
 *     if (ProgressiveStages.percent(p, 'diamond_age') &gt;= 100) ProgressiveStages.grant(p, 'diamond_age')
 *   }))
 * </pre>
 */
public final class PSKubeBindings {

    // ---- lifecycle hooks & custom conditions ----

    public void onGranted(ScriptHooks.StageCallback cb) { ScriptHooks.onGranted(cb); }
    public void onRevoked(ScriptHooks.StageCallback cb) { ScriptHooks.onRevoked(cb); }
    public void condition(String id, ScriptHooks.StagePredicate predicate) {
        ScriptHooks.registerCondition(id, predicate);
    }

    // ---- imperative stage control / queries ----

    public boolean has(Player player, String stage) {
        StageId id = parse(stage);
        return player instanceof ServerPlayer sp && id != null && StageManager.getInstance().hasStage(sp, id);
    }

    public boolean grant(Player player, String stage) {
        StageId id = parse(stage);
        if (!(player instanceof ServerPlayer sp) || id == null) return false;
        StageManager.getInstance().grantStageWithCause(sp, id, StageCause.COMMAND);
        return true;
    }

    public boolean revoke(Player player, String stage) {
        StageId id = parse(stage);
        if (!(player instanceof ServerPlayer sp) || id == null) return false;
        StageManager.getInstance().revokeStageWithCause(sp, id, StageCause.COMMAND);
        return true;
    }

    public List<String> list(Player player) {
        List<String> out = new ArrayList<>();
        if (player instanceof ServerPlayer sp) {
            for (StageId id : StageManager.getInstance().getStages(sp)) out.add(id.toString());
        }
        return out;
    }

    /** Completion percent (0..100) of a stage's {@code [[triggers]]} for the player. */
    public int percent(Player player, String stage) {
        StageId id = parse(stage);
        if (!(player instanceof ServerPlayer sp) || id == null) return 0;
        return Math.round(StageTriggerEvaluator.stagePercent(sp, id) * 100f);
    }

    private static StageId parse(String stage) {
        try { return StageId.parse(stage); } catch (Exception e) { return null; }
    }
}
