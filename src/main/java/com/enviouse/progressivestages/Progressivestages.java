package com.enviouse.progressivestages;

import com.enviouse.progressivestages.common.config.StageConfig;
import com.enviouse.progressivestages.common.data.StageAttachments;
import com.enviouse.progressivestages.common.util.Constants;
import com.mojang.logging.LogUtils;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.slf4j.Logger;

/**
 * Main mod class for ProgressiveStages
 * Team-scoped linear stage progression system with integrated item/recipe/dimension/mod locking and EMI visual feedback
 */
@Mod(Constants.MOD_ID)
public class Progressivestages {

    public static final String MODID = Constants.MOD_ID;
    private static final Logger LOGGER = LogUtils.getLogger();

    public Progressivestages(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("ProgressiveStages initializing...");

        // Register data attachments
        StageAttachments.ATTACHMENT_TYPES.register(modEventBus);

        // Register config
        modContainer.registerConfig(ModConfig.Type.COMMON, StageConfig.SPEC);

        // Register setup events
        modEventBus.addListener(this::commonSetup);

        LOGGER.info("ProgressiveStages initialized successfully");
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("ProgressiveStages common setup");
    }

    @EventBusSubscriber(modid = Constants.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            LOGGER.info("ProgressiveStages client setup");
        }
    }
}
