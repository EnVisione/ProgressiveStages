package com.enviouse.progressivestages.common.rehaul.challenge;

import com.enviouse.progressivestages.common.rehaul.ConditionNode;
import com.enviouse.progressivestages.common.rehaul.ConfigProvenance;
import com.enviouse.progressivestages.common.rehaul.action.ActionChain;
import com.enviouse.progressivestages.common.rehaul.condition.SubjectScope;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Objects;

public record CompiledChallenge(ResourceLocation id, String title, SubjectScope scope,
                                ConditionNode startCondition, ConditionNode successCondition,
                                ConditionNode endCondition, List<ChallengeBudget> budgets,
                                List<ChallengeStep> steps, long timeoutMillis, int retries,
                                ActionChain successActions, ActionChain failureActions,
                                ChallengeHud hud, ConfigProvenance provenance) {

    public CompiledChallenge {
        Objects.requireNonNull(id, "id");
        title = title == null ? id.getPath() : title;
        scope = scope == null ? SubjectScope.PLAYER : scope;
        startCondition = startCondition == null ? new ConditionNode.Constant(true) : startCondition;
        successCondition = successCondition == null ? new ConditionNode.Constant(true) : successCondition;
        endCondition = endCondition == null ? new ConditionNode.Constant(false) : endCondition;
        budgets = budgets == null ? List.of() : List.copyOf(budgets);
        steps = steps == null ? List.of() : List.copyOf(steps);
        if (timeoutMillis < 0 || retries < 0) throw new IllegalArgumentException("Challenge timing is invalid");
        successActions = successActions == null ? ActionChain.EMPTY : successActions;
        failureActions = failureActions == null ? ActionChain.EMPTY : failureActions;
        hud = hud == null ? ChallengeHud.defaults() : hud;
        Objects.requireNonNull(provenance, "provenance");
    }
}
