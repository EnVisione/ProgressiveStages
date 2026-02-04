package com.enviouse.progressivestages.common.data;

import com.enviouse.progressivestages.common.util.Constants;
import com.mojang.serialization.Codec;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/**
 * Registry for data attachments used by the mod
 */
public class StageAttachments {

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
        DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Constants.MOD_ID);

    /**
     * Attachment for storing team stage data on the server level
     */
    public static final Supplier<AttachmentType<TeamStageData>> TEAM_STAGES = ATTACHMENT_TYPES.register(
        "team_stages",
        () -> AttachmentType.builder(TeamStageData::new)
            .serialize(TeamStageData.CODEC)
            .copyOnDeath()
            .build()
    );
}
