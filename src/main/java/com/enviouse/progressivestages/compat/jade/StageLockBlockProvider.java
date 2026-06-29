package com.enviouse.progressivestages.compat.jade;

import com.enviouse.progressivestages.client.ClientLockCache;
import com.enviouse.progressivestages.client.ClientStageCache;
import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.util.Constants;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Appends "🔒 Requires: &lt;stage(s)&gt;" to the Jade overlay for a block the local player can't yet
 * access. The lock is read from the block's ITEM form against the synced client item-lock cache —
 * that's the data ProgressiveStages already sends for EMI/JEI hiding, and it honors creative bypass
 * (the cache returns no gating stages while bypassing). Owned-stage filtering uses {@link ClientStageCache}.
 */
public class StageLockBlockProvider implements IBlockComponentProvider {

    private static final ResourceLocation UID =
        ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "stage_lock");

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        try {
            Block block = accessor.getBlock();
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(block.asItem());
            if (itemId == null) return;

            Set<StageId> gating = ClientLockCache.getRequiredStagesForItem(itemId);
            // Empty when not gated OR creative bypass is active; owns-all means it's already unlocked.
            if (gating.isEmpty() || ClientLockCache.playerOwnsAllStagesFor(itemId)) return;

            List<String> missing = new ArrayList<>();
            for (StageId s : gating) {
                if (!ClientStageCache.hasStage(s)) missing.add(ClientStageCache.getDisplayName(s));
            }
            if (missing.isEmpty()) return;

            tooltip.add(Component.literal("🔒 Requires: " + String.join(", ", missing))
                .withStyle(ChatFormatting.RED));
        } catch (Throwable ignored) {
            // Never let an overlay error break Jade rendering.
        }
    }
}
