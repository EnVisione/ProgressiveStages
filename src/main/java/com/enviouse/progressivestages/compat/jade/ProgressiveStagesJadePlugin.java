package com.enviouse.progressivestages.compat.jade;

import com.enviouse.progressivestages.common.util.Constants;
import net.minecraft.world.level.block.Block;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

/**
 * v3.0: Jade integration. Adds an in-world "🔒 Requires &lt;stage&gt;" line to the Jade overlay when
 * the player looks at a block gated behind a stage they don't yet own.
 *
 * <p>Pulled from the Modrinth Maven (compileOnly), so this class only loads when Jade is actually
 * installed — Jade discovers it via the {@link WailaPlugin} annotation and calls
 * {@link #registerClient} on the client. Lock state is read from the already-synced client lock
 * cache, so no extra packets or server round-trip are needed.
 */
@WailaPlugin(Constants.MOD_ID)
public class ProgressiveStagesJadePlugin implements IWailaPlugin {

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(new StageLockBlockProvider(), Block.class);
    }
}
