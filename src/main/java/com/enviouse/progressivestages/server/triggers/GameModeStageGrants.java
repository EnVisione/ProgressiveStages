package com.enviouse.progressivestages.server.triggers;

import com.enviouse.progressivestages.common.api.StageCause;
import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.config.StageDefinition;
import com.enviouse.progressivestages.common.stage.StageManager;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/** Maintains a personal source for game mode grants without removing other grant sources. */
public final class GameModeStageGrants {
    private static final String SOURCE = "gamemode";
    private static volatile Map<StageId, StageDefinition> definitions = Map.of();

    private GameModeStageGrants() {}

    public static void rebuild(Collection<StageDefinition> stages) {
        Map<StageId, StageDefinition> next = new LinkedHashMap<>();
        for (StageDefinition stage : stages) {
            if (stage.getGameModeGrant().enabled()) next.put(stage.getId(), stage);
        }
        definitions = Map.copyOf(next);
    }

    public static void reconcile(ServerPlayer player) {
        StageManager manager = StageManager.getInstance();
        java.util.Set<StageId> candidates = new java.util.LinkedHashSet<>(definitions.keySet());
        candidates.addAll(manager.getStoredStages(player));
        for (StageId id : candidates) {
            StageDefinition stage = definitions.get(id);
            boolean hasSource = manager.getStageSources(player, id).contains(SOURCE);
            boolean matches = stage != null
                && stage.getGameModeGrant().modes().contains(player.gameMode.getGameModeForPlayer());
            if (matches && !hasSource) manager.grantStageFromSource(player, id, SOURCE, StageCause.AUTO);
            else if (!matches && hasSource) manager.revokeStageFromSource(player, id, SOURCE, StageCause.REGRESSION);
        }
    }
}
