package com.enviouse.progressivestages.compat.ftbquests;

/**
 * Implemented by mixins that attach a required-stage field to FTB Quests objects
 * (Quest, Chapter). Allows other mixins (e.g. TeamDataMixin) to read the field
 * across mixin boundaries via a clean cast rather than reflection.
 */
public interface RequiredStageHolder {
    String progressivestages$getRequiredStage();
}
