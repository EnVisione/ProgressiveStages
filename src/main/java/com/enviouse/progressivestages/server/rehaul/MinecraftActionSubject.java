package com.enviouse.progressivestages.server.rehaul;

import com.enviouse.progressivestages.common.api.StageCause;
import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.rehaul.action.ActionSubject;
import com.enviouse.progressivestages.common.stage.StageManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.Set;

final class MinecraftActionSubject implements ActionSubject {

    private final ServerPlayer player;
    private final RehaulRuntime runtime;

    MinecraftActionSubject(ServerPlayer player, RehaulRuntime runtime) {
        this.player = player;
        this.runtime = runtime;
    }

    @Override public String id() { return player.getUUID().toString(); }
    @Override public boolean hasStage(StageId stage) { return StageManager.getInstance().hasStage(player, stage); }

    @Override
    public boolean grantStage(StageId stage) {
        boolean before = hasStage(stage);
        StageManager.getInstance().grantStageWithCause(player, stage, StageCause.TRIGGER);
        return !before && hasStage(stage);
    }

    @Override
    public boolean revokeStage(StageId stage) {
        boolean before = hasStage(stage);
        StageManager.getInstance().revokeStageWithCause(player, stage, StageCause.REGRESSION);
        return before && !hasStage(stage);
    }

    @Override public String stageState(StageId stage) { return runtime.stageStates().get(id(), stage); }
    @Override public boolean setStageState(StageId stage, String state) { return runtime.stageStates().transition(id(), stage, state); }

    @Override
    public double variable(String value) {
        ResourceLocation variable = variableId(value);
        if (!runtime.variables().exists(variable)) return 0;
        Object result = runtime.variables().get(id(), variable);
        return result instanceof Number number ? number.doubleValue() : 0;
    }

    @Override
    public void setVariable(String value, double amount) {
        ResourceLocation variable = variableId(value);
        if (!runtime.variables().exists(variable)) throw new IllegalArgumentException("Unknown variable. " + variable);
        runtime.variables().set(id(), variable, amount);
    }

    @Override public void sendMessage(String message) { player.sendSystemMessage(Component.literal(message)); }

    @Override
    public Object snapshot() {
        return new Snapshot(StageManager.getInstance().getStages(player), runtime.variables().subjectSnapshot(id()));
    }

    @Override
    public void restore(Object snapshot) {
        Snapshot value = (Snapshot) snapshot;
        Set<StageId> current = StageManager.getInstance().getStages(player);
        for (StageId stage : current) if (!value.stages().contains(stage)) {
            StageManager.getInstance().revokeStageWithCause(player, stage, StageCause.REGRESSION);
        }
        for (StageId stage : value.stages()) if (!current.contains(stage)) {
            StageManager.getInstance().grantStageWithCause(player, stage, StageCause.TRIGGER);
        }
        runtime.variables().restoreSubject(id(), value.variables());
    }

    private static ResourceLocation variableId(String value) {
        return value.contains(":") ? ResourceLocation.parse(value)
            : ResourceLocation.fromNamespaceAndPath("progressivestages", value);
    }

    private record Snapshot(Set<StageId> stages, Map<ResourceLocation, Object> variables) {
        private Snapshot {
            stages = Set.copyOf(stages);
            variables = Map.copyOf(variables);
        }
    }
}
