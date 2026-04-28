package com.enviouse.progressivestages;

import com.enviouse.progressivestages.common.config.StageConfig;
import com.enviouse.progressivestages.common.data.StageAttachments;
import com.enviouse.progressivestages.common.util.Constants;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

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

        // Register Global Loot Modifier codec (2.0 — filters chest/fishing/archeology loot)
        com.enviouse.progressivestages.common.LootModifiers.register(modEventBus);

        // Register config (custom filename: progressivestages.toml instead of default progressivestages-common.toml)
        modContainer.registerConfig(ModConfig.Type.COMMON, StageConfig.SPEC, "progressivestages.toml");

        // Create config folder early (before world load)
        createConfigFolder();

        // Register setup events
        modEventBus.addListener(this::commonSetup);

        // Register client events on client dist
        if (net.neoforged.fml.loading.FMLEnvironment.dist.isClient()) {
            registerClientEvents(modEventBus);
        }

        LOGGER.info("ProgressiveStages initialized successfully");
    }

    /**
     * Create the ProgressiveStages config folder during mod construction.
     * This ensures it exists before world load so pack devs can put files in it.
     * Also generates default stage files if none exist.
     */
    private void createConfigFolder() {
        Path configFolder = FMLPaths.CONFIGDIR.get().resolve(Constants.STAGE_FILES_DIRECTORY);

        boolean folderCreated = false;
        if (!Files.exists(configFolder)) {
            try {
                Files.createDirectories(configFolder);
                LOGGER.info("[ProgressiveStages] Created config directory: {}", configFolder);
                folderCreated = true;
            } catch (java.nio.file.AccessDeniedException e) {
                LOGGER.error("[ProgressiveStages] Permission denied creating config directory: {}", configFolder);
                LOGGER.error("[ProgressiveStages] Please check file permissions for the config folder.");
                return;
            } catch (IOException e) {
                LOGGER.error("[ProgressiveStages] Failed to create config directory: {} - {}", configFolder, e.getMessage());
                LOGGER.error("[ProgressiveStages] Stage configuration may not work correctly. Check filesystem permissions.");
                return;
            }
        } else {
            // Folder exists - verify it's writable
            if (!Files.isWritable(configFolder)) {
                LOGGER.error("[ProgressiveStages] Config directory exists but is not writable: {}", configFolder);
                LOGGER.error("[ProgressiveStages] Please check file permissions. Stage files cannot be saved!");
                return;
            }
        }

        // Generate default stage files if no stage TOML files exist (excluding triggers.toml)
        generateDefaultStageFilesIfNeeded(configFolder);

        // Generate triggers.toml if it doesn't exist
        generateTriggersFileIfNeeded(configFolder);
    }

    /**
     * Generate default stage files (stone_age, iron_age, diamond_age) if none exist.
     * Called during mod init so pack devs have example files immediately.
     */
    private void generateDefaultStageFilesIfNeeded(Path configFolder) {
        // Count existing stage files (excluding triggers.toml)
        int stageFileCount = 0;
        try (var stream = Files.newDirectoryStream(configFolder, "*.toml")) {
            for (Path file : stream) {
                String fileName = file.getFileName().toString().toLowerCase();
                if (!fileName.equals("triggers.toml")) {
                    stageFileCount++;
                }
            }
        } catch (IOException e) {
            LOGGER.warn("[ProgressiveStages] Could not scan config directory for stage files: {}", e.getMessage());
            return;
        }

        if (stageFileCount > 0) {
            LOGGER.debug("[ProgressiveStages] Found {} existing stage files, skipping default generation", stageFileCount);
            return;
        }

        LOGGER.info("[ProgressiveStages] No stage files found, generating defaults...");

        // Generate the three default stage files
        generateStoneAgeFile(configFolder);
        generateIronAgeFile(configFolder);
        generateDiamondAgeFile(configFolder);
    }

    /**
     * Generate triggers.toml if it doesn't exist.
     * Called during mod init so pack devs have example triggers immediately.
     */
    private void generateTriggersFileIfNeeded(Path configFolder) {
        Path triggersFile = configFolder.resolve("triggers.toml");
        if (Files.exists(triggersFile)) {
            LOGGER.debug("[ProgressiveStages] triggers.toml already exists, skipping generation");
            return;
        }

        LOGGER.info("[ProgressiveStages] Generating triggers.toml...");
        writeStageFile(triggersFile, com.enviouse.progressivestages.server.loader.DefaultStageTemplates.triggers());
    }

    private void generateStoneAgeFile(Path configFolder) {
        writeStageFile(configFolder.resolve("stone_age.toml"),
            com.enviouse.progressivestages.server.loader.DefaultStageTemplates.stoneAge());
    }

    private void generateIronAgeFile(Path configFolder) {
        writeStageFile(configFolder.resolve("iron_age.toml"),
            com.enviouse.progressivestages.server.loader.DefaultStageTemplates.ironAge());
    }

    private void generateDiamondAgeFile(Path configFolder) {
        writeStageFile(configFolder.resolve("diamond_age.toml"),
            com.enviouse.progressivestages.server.loader.DefaultStageTemplates.diamondAge());
    }

    private void writeStageFile(Path file, String content) {
        try {
            Files.writeString(file, content);
            LOGGER.info("[ProgressiveStages] Generated default stage file: {}", file.getFileName());
        } catch (IOException e) {
            LOGGER.error("[ProgressiveStages] Failed to write stage file: {} - {}", file.getFileName(), e.getMessage());
        }
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("ProgressiveStages common setup");
    }

    /**
     * Client-side mod bus events.
     * Register listeners directly to mod event bus instead of using deprecated @EventBusSubscriber(bus=MOD)
     */
    public static void registerClientEvents(IEventBus modEventBus) {
        modEventBus.addListener(ClientModEvents::onClientSetup);
    }

    public static class ClientModEvents {
        public static void onClientSetup(FMLClientSetupEvent event) {
            LOGGER.info("ProgressiveStages client setup");
        }
    }
}
