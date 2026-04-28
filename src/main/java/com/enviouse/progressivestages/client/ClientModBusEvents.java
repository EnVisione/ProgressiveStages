package com.enviouse.progressivestages.client;

import com.enviouse.progressivestages.client.renderer.LockedItemDecorator;
import com.enviouse.progressivestages.common.util.Constants;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterItemDecorationsEvent;
import org.slf4j.Logger;

/**
 * Client-side mod-event-bus subscribers (lifecycle and registry events).
 * Game-bus events live in {@link ClientEventHandler}.
 */
@SuppressWarnings("removal")
@EventBusSubscriber(modid = Constants.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientModBusEvents {

    private static final Logger LOGGER = LogUtils.getLogger();

    private ClientModBusEvents() {}

    /**
     * Registers a single shared {@link LockedItemDecorator} for every Item in
     * the registry. This makes lock icons appear in every container Minecraft
     * draws (vanilla inventories, chests, shulker boxes, modded GUIs) — not
     * just inside the EMI panel.
     */
    @SubscribeEvent
    public static void onRegisterItemDecorations(RegisterItemDecorationsEvent event) {
        LockedItemDecorator instance = new LockedItemDecorator();
        int count = 0;
        for (var item : BuiltInRegistries.ITEM) {
            event.register(item, instance);
            count++;
        }
        LOGGER.debug("[ProgressiveStages] Registered LockedItemDecorator for {} items", count);
    }
}
