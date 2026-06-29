package com.enviouse.progressivestages.compat.wthit;

import com.enviouse.progressivestages.client.StageLockTooltip;
import mcp.mobius.waila.api.IBlockAccessor;
import mcp.mobius.waila.api.IBlockComponentProvider;
import mcp.mobius.waila.api.IEntityAccessor;
import mcp.mobius.waila.api.IEntityComponentProvider;
import mcp.mobius.waila.api.IPluginConfig;
import mcp.mobius.waila.api.ITooltip;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

/**
 * v3.0: WTHIT equivalent of the Jade overlay — adds "🔒 Requires: &lt;stage&gt;" to a locked block
 * or entity. Unlike Jade's generic provider interfaces, WTHIT's aren't generic, so one class serves
 * both block and entity bodies. Shares {@link StageLockTooltip} with the Jade providers.
 */
public class StageLockWthitProvider implements IBlockComponentProvider, IEntityComponentProvider {

    @Override
    public void appendBody(ITooltip tooltip, IBlockAccessor accessor, IPluginConfig config) {
        StageLockTooltip.blockRequirement(accessor.getBlock()).ifPresent(req ->
            tooltip.addLine(Component.literal("🔒 Requires: " + req).withStyle(ChatFormatting.RED)));
    }

    @Override
    public void appendBody(ITooltip tooltip, IEntityAccessor accessor, IPluginConfig config) {
        Entity entity = accessor.getEntity();
        if (entity == null) return;
        StageLockTooltip.entityRequirement(entity.getType()).ifPresent(req ->
            tooltip.addLine(Component.literal("🔒 Requires: " + req).withStyle(ChatFormatting.RED)));
    }
}
