package com.enviouse.progressivestages.server.enforcement;

import com.enviouse.progressivestages.common.api.StageChangeEvent;
import com.enviouse.progressivestages.common.api.StagesBulkChangedEvent;
import com.enviouse.progressivestages.common.lock.LockRegistry;
import com.enviouse.progressivestages.common.util.Constants;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * v2.0.2: bridge stage changes into the chunk-rewriter pipeline. When a
 * player gains (or loses) a stage that gates ore visibility, force a re-send
 * of all loaded chunks in their view distance — the chunk-rewriter mixin
 * will produce the right packet contents based on their new stage set.
 *
 * <p>Whether the change is grant or revoke doesn't matter: both invalidate
 * what the client currently sees.
 */
@EventBusSubscriber(modid = Constants.MOD_ID)
public final class OreSpoofStageListener {

    private OreSpoofStageListener() {}

    @SubscribeEvent
    public static void onStageChange(StageChangeEvent event) {
        if (!LockRegistry.getInstance().isOreSpoofActive()) return;
        ServerPlayer p = event.getPlayer();
        if (p == null || p.connection == null) return;
        try {
            OreSpoofManager.get().resendChunksInView(p);
        } catch (Throwable t) {
            org.slf4j.LoggerFactory.getLogger("ProgressiveStages")
                .error("[OreSpoof] resendChunksInView (stage change) failed", t);
        }
    }

    @SubscribeEvent
    public static void onStagesBulkChanged(StagesBulkChangedEvent event) {
        if (!LockRegistry.getInstance().isOreSpoofActive()) return;
        ServerPlayer p = event.getPlayer();
        if (p == null || p.connection == null) return;
        try {
            OreSpoofManager.get().resendChunksInView(p);
        } catch (Throwable t) {
            org.slf4j.LoggerFactory.getLogger("ProgressiveStages")
                .error("[OreSpoof] resendChunksInView (bulk change) failed", t);
        }
    }
}
