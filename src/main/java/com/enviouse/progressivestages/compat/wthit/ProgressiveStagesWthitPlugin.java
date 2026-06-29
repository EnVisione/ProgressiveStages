package com.enviouse.progressivestages.compat.wthit;

import com.enviouse.progressivestages.common.util.Constants;
import mcp.mobius.waila.api.IBlockComponentProvider;
import mcp.mobius.waila.api.IEntityComponentProvider;
import mcp.mobius.waila.api.IRegistrar;
import mcp.mobius.waila.api.IWailaPlugin;
import mcp.mobius.waila.api.TooltipPosition;
import mcp.mobius.waila.api.WailaPlugin;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;

/**
 * v3.0: the ProgressiveStages WTHIT plugin (discovered via the {@link WailaPlugin} annotation).
 * Registers one provider for both block and entity bodies. compileOnly dep, so this class only
 * loads when WTHIT is installed.
 */
@WailaPlugin(id = Constants.MOD_ID)
public class ProgressiveStagesWthitPlugin implements IWailaPlugin {

    @Override
    public void register(IRegistrar registrar) {
        StageLockWthitProvider provider = new StageLockWthitProvider();
        // Cast disambiguates the IBlock/IEntity addComponent overloads (one provider implements both).
        registrar.addComponent((IBlockComponentProvider) provider, TooltipPosition.BODY, Block.class);
        registrar.addComponent((IEntityComponentProvider) provider, TooltipPosition.BODY, Entity.class);
    }
}
