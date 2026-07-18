package com.enviouse.progressivestages.common.rehaul.lifecycle;

import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.rehaul.ConditionNode;
import com.enviouse.progressivestages.common.rehaul.ConfigProvenance;
import com.enviouse.progressivestages.common.rehaul.action.ActionChain;
import com.enviouse.progressivestages.common.rehaul.condition.SubjectScope;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

public record CompiledLifecycleRule(ResourceLocation id, StageId targetStage, LifecycleDirection direction,
                                    ConditionNode condition, int priority, SubjectScope progressScope,
                                    RepeatMode repeatMode, boolean evaluateWhileOwned,
                                    boolean evaluateWhileMissing, long cooldownMillis,
                                    long debounceMillis, long graceMillis, String targetState,
                                    ActionChain successActions, ActionChain failureActions,
                                    ConfigProvenance provenance) {

    public CompiledLifecycleRule {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(targetStage, "targetStage");
        direction = direction == null ? LifecycleDirection.GRANT : direction;
        condition = condition == null ? new ConditionNode.Constant(true) : condition;
        progressScope = progressScope == null ? SubjectScope.PLAYER : progressScope;
        repeatMode = repeatMode == null ? RepeatMode.ONCE : repeatMode;
        if (cooldownMillis < 0 || debounceMillis < 0 || graceMillis < 0) {
            throw new IllegalArgumentException("Lifecycle timing cannot be negative");
        }
        targetState = targetState == null ? "" : targetState;
        successActions = successActions == null ? ActionChain.EMPTY : successActions;
        failureActions = failureActions == null ? ActionChain.EMPTY : failureActions;
        Objects.requireNonNull(provenance, "provenance");
    }
}
