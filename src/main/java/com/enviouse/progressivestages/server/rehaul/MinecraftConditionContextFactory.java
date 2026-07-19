package com.enviouse.progressivestages.server.rehaul;

import com.enviouse.progressivestages.common.rehaul.condition.ConditionContext;
import com.enviouse.progressivestages.common.rehaul.condition.SubjectScope;
import com.enviouse.progressivestages.common.stage.StageManager;
import com.enviouse.progressivestages.common.team.TeamProvider;
import com.enviouse.progressivestages.server.structure.StructureSessionManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class MinecraftConditionContextFactory {

    private MinecraftConditionContextFactory() {}

    public static ConditionContext create(ServerPlayer player, RehaulRuntime runtime, Set<String> dirty) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("server_player", player);
        ResourceLocation dimension = player.level().dimension().location();
        values.put("dimension", dimension.toString());
        values.put("dimension." + dimension, dimension.toString());
        var biome = player.level().getBiome(player.blockPosition());
        biome.unwrapKey().ifPresent(key -> {
            values.put("biome", key.location().toString());
            values.put("biome." + key.location(), key.location().toString());
        });
        Set<String> stages = new LinkedHashSet<>();
        for (var stage : StageManager.getInstance().getStages(player)) {
            stages.add(stage.toString());
            values.put("stages." + stage, stages);
            var definition = com.enviouse.progressivestages.common.stage.StageOrder.getInstance()
                .getStageDefinition(stage).orElse(null);
            java.util.UUID owner = definition != null && definition.isServerScope()
                ? StageManager.SERVER_TEAM : TeamProvider.getInstance().getTeamId(player);
            long granted = com.enviouse.progressivestages.server.triggers.StageRegressionData
                .get(player.server).getGrantTime(owner, stage);
            if (granted >= 0) values.put("stage_held_for." + stage,
                Math.max(0, System.currentTimeMillis() - granted));
        }
        values.put("stages", Set.copyOf(stages));
        values.put("stage_count", stages.size());
        values.put("health", player.getHealth());
        values.put("current_health", player.getHealth());
        values.put("food", player.getFoodData().getFoodLevel());
        values.put("altitude", player.getY());
        values.put("reach_y", player.getY());
        values.put("level", player.experienceLevel);
        values.put("xp", player.totalExperience);
        values.put("online_team_size", TeamProvider.getInstance()
            .getTeamMembers(TeamProvider.getInstance().getTeamId(player), player).size());
        values.put("no_damage_for", runtime.noDamageFor(player.getUUID().toString(), System.currentTimeMillis()));
        values.putAll(runtime.metricValues(player.getUUID().toString()));
        values.putAll(runtime.sessionValues(player.getUUID().toString(), System.currentTimeMillis()));
        Set<String> structures = new LinkedHashSet<>();
        for (var session : StructureSessionManager.getInstance().activeSessions(player)) {
            structures.add(session.instance().structureId().toString());
            values.put("session." + session.sessionId(), true);
            values.put("structure_session." + session.instance().structureId(), 1);
        }
        values.put("structures", structures);
        for (String structure : structures) values.put("structures." + structure, structures);
        long longestStructureTime = 0L;
        for (var entry : StructureSessionManager.getInstance().activeStructureSeconds(player).entrySet()) {
            values.put("structure_time." + entry.getKey(), entry.getValue());
            longestStructureTime = Math.max(longestStructureTime, entry.getValue());
        }
        values.put("structure_time", longestStructureTime);
        for (String objectiveName : player.getScoreboard().getObjectiveNames()) {
            var objective = player.getScoreboard().getObjective(objectiveName);
            var score = objective == null ? null : player.getScoreboard().getPlayerScoreInfo(player, objective);
            if (score != null) values.put("scoreboard." + objectiveName, score.value());
        }
        return new ConditionContext(player.getUUID().toString(), SubjectScope.PLAYER,
            System.currentTimeMillis(), values, dirty, Map.of());
    }
}
