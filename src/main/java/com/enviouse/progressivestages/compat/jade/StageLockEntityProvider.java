package com.enviouse.progressivestages.compat.jade;

import com.enviouse.progressivestages.client.ClientLockCache;
import com.enviouse.progressivestages.client.ClientStageCache;
import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.util.Constants;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IEntityComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * v3.0: appends "🔒 Requires: &lt;stage(s)&gt;" to the Jade overlay for a mob/entity gated behind a
 * stage the local player can't yet access (e.g. can't attack/interact with it). Reads the synced
 * client entity-lock cache, which honors creative bypass.
 */
public class StageLockEntityProvider implements IEntityComponentProvider {

    private static final ResourceLocation UID =
        ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "stage_lock_entity");

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    public void appendTooltip(ITooltip tooltip, EntityAccessor accessor, IPluginConfig config) {
        try {
            Entity entity = accessor.getEntity();
            ResourceLocation eid = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
            if (eid == null) return;

            Set<StageId> gating = ClientLockCache.getRequiredStagesForEntity(eid);
            if (gating.isEmpty() || ClientLockCache.playerOwnsAllStagesForEntity(eid)) return;

            List<String> missing = new ArrayList<>();
            for (StageId s : gating) {
                if (!ClientStageCache.hasStage(s)) missing.add(ClientStageCache.getDisplayName(s));
            }
            if (missing.isEmpty()) return;

            tooltip.add(Component.literal("🔒 Requires: " + String.join(", ", missing))
                .withStyle(ChatFormatting.RED));
        } catch (Throwable ignored) {
        }
    }
}
