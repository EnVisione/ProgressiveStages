package com.enviouse.progressivestages.mixin.ftbquests;

import com.enviouse.progressivestages.compat.ftbquests.RequiredStageHolder;
import com.enviouse.progressivestages.compat.ftbquests.StageRequirementHelper;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.TeamData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * HEAD-cancels {@link TeamData#canStartTasks(Quest)} when the quest's required
 * ProgressiveStages stage is missing. This complements {@link QuestMixin}'s
 * {@code isVisible} HEAD cancel: even if visibility is overridden by another
 * mod, task progression itself is gated on stage ownership.
 */
@Mixin(value = TeamData.class, remap = false)
public abstract class TeamDataMixin {

    @Inject(method = "canStartTasks", at = @At("HEAD"), cancellable = true)
    private void progressivestages$blockStageLockedQuestTasks(Quest quest, CallbackInfoReturnable<Boolean> cir) {
        // QuestMixin implements RequiredStageHolder at runtime; cast via Object to satisfy compile.
        Object questObj = quest;
        if (!(questObj instanceof RequiredStageHolder holder)) {
            return;
        }
        String required = holder.progressivestages$getRequiredStage();
        if (required == null || required.isEmpty()) {
            return;
        }
        if (!StageRequirementHelper.hasStageForServerLogic(required)) {
            cir.setReturnValue(false);
        }
    }
}
