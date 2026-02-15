package com.enviouse.progressivestages.mixin.ftbquests;

import com.enviouse.progressivestages.compat.ftbquests.StageRequirementHelper;
import dev.ftb.mods.ftblibrary.config.ConfigGroup;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.TeamData;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin to add "Stage Required" field to FTB Quests Quest properties.
 *
 * This allows pack devs to gate individual quests behind ProgressiveStages stages
 * directly in the FTB Quests UI, without needing to create Stage Tasks.
 */
@Mixin(value = Quest.class, remap = false)
public abstract class QuestMixin {

    /**
     * The required stage ID for this quest. If empty, no stage requirement.
     */
    @Unique
    private String progressivestages$requiredStage = "";

    /**
     * Inject into fillConfigGroup to add our "Stage Required" field to the Quest properties UI.
     * Method signature: void fillConfigGroup(ConfigGroup config)
     */
    @Inject(method = "fillConfigGroup", at = @At("TAIL"))
    private void progressivestages$addStageField(ConfigGroup config, CallbackInfo ci) {
        ConfigGroup visibility = config.getOrCreateSubgroup("visibility");
        visibility.addString("required_stage", progressivestages$requiredStage,
            v -> progressivestages$requiredStage = (v != null ? v : ""), "")
            .setNameKey("progressivestages.quest.required_stage");
    }

    /**
     * Inject into isVisible to check stage requirement.
     * Method signature: boolean isVisible(TeamData data)
     */
    @Inject(method = "isVisible", at = @At("HEAD"), cancellable = true)
    private void progressivestages$checkStageVisibility(TeamData data, CallbackInfoReturnable<Boolean> cir) {
        if (progressivestages$requiredStage != null && !progressivestages$requiredStage.isEmpty() && !progressivestages$requiredStage.isBlank()) {
            // Check if player has the required stage (uses client cache on client, server check on server)
            if (!StageRequirementHelper.hasStageClient(progressivestages$requiredStage)) {
                cir.setReturnValue(false);
            }
        }
    }

    /**
     * Inject into writeData to save our field.
     * Method signature: void writeData(CompoundTag nbt, HolderLookup.Provider provider)
     */
    @Inject(method = "writeData", at = @At("TAIL"))
    private void progressivestages$writeStageData(CompoundTag nbt, HolderLookup.Provider provider, CallbackInfo ci) {
        if (progressivestages$requiredStage != null && !progressivestages$requiredStage.isEmpty()) {
            nbt.putString("progressivestages_required_stage", progressivestages$requiredStage);
        }
    }

    /**
     * Inject into readData to load our field.
     * Method signature: void readData(CompoundTag nbt, HolderLookup.Provider provider)
     */
    @Inject(method = "readData", at = @At("TAIL"))
    private void progressivestages$readStageData(CompoundTag nbt, HolderLookup.Provider provider, CallbackInfo ci) {
        progressivestages$requiredStage = nbt.getString("progressivestages_required_stage");
    }

    /**
     * Inject into writeNetData to sync our field to clients.
     * Method signature: void writeNetData(RegistryFriendlyByteBuf buffer)
     */
    @Inject(method = "writeNetData", at = @At("TAIL"))
    private void progressivestages$writeNetStageData(RegistryFriendlyByteBuf buffer, CallbackInfo ci) {
        buffer.writeUtf(progressivestages$requiredStage != null ? progressivestages$requiredStage : "", Short.MAX_VALUE);
    }

    /**
     * Inject into readNetData to receive our field from server.
     * Method signature: void readNetData(RegistryFriendlyByteBuf buffer)
     */
    @Inject(method = "readNetData", at = @At("TAIL"))
    private void progressivestages$readNetStageData(RegistryFriendlyByteBuf buffer, CallbackInfo ci) {
        progressivestages$requiredStage = buffer.readUtf(Short.MAX_VALUE);
    }
}

