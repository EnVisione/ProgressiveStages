package com.enviouse.progressivestages.compat.wthit;

import mcp.mobius.waila.api.IBlockComponentProvider;
import mcp.mobius.waila.api.IClientRegistrar;
import mcp.mobius.waila.api.IEntityComponentProvider;
import mcp.mobius.waila.api.IWailaClientPlugin;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;

/**
 * v3.0: the ProgressiveStages WTHIT client plugin, discovered through
 * {@code waila_plugins.json}. Registers one provider for both block and entity bodies.
 * The WTHIT dependency is compile-only, so this class loads only when WTHIT is installed.
 */
public class ProgressiveStagesWthitPlugin implements IWailaClientPlugin {

    @Override
    public void register(IClientRegistrar registrar) {
        StageLockWthitProvider provider = new StageLockWthitProvider();
        // Cast disambiguates the block/entity body overloads (one provider implements both).
        registrar.body((IBlockComponentProvider) provider, Block.class);
        registrar.body((IEntityComponentProvider) provider, Entity.class);
    }
}
