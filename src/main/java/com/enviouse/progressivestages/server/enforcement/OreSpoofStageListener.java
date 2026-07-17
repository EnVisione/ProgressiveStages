package com.enviouse.progressivestages.server.enforcement;

import com.enviouse.progressivestages.common.api.StageChangeEvent;
import com.enviouse.progressivestages.common.api.StagesBulkChangedEvent;
import com.enviouse.progressivestages.common.lock.LockRegistry;
import com.enviouse.progressivestages.common.util.Constants;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/** Refresh ore masking when owned stages change. */
@EventBusSubscriber(modid = Constants.MOD_ID)
public final class OreSpoofStageListener {

    private OreSpoofStageListener() {}

    @SubscribeEvent
    public static void onStageChange(StageChangeEvent event) {
        if (!LockRegistry.getInstance().isOreSpoofActive()) return;
        ServerPlayer p = event.getPlayer();
        if (p == null || p.connection == null) return;
        try {
            OreSpoofManager.get().refreshPlayer(p);
        } catch (Throwable t) {
            org.slf4j.LoggerFactory.getLogger("ProgressiveStages")
                .error("[OreSpoof] Stage change refresh failed", t);
        }
    }

    @SubscribeEvent
    public static void onStagesBulkChanged(StagesBulkChangedEvent event) {
        if (!LockRegistry.getInstance().isOreSpoofActive()) return;
        ServerPlayer p = event.getPlayer();
        if (p == null || p.connection == null) return;
        try {
            OreSpoofManager.get().refreshPlayer(p);
        } catch (Throwable t) {
            org.slf4j.LoggerFactory.getLogger("ProgressiveStages")
                .error("[OreSpoof] Bulk change refresh failed", t);
        }
    }
}
