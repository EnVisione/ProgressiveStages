package com.enviouse.progressivestages.server.loader;

import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.config.StageDefinition;
import com.enviouse.progressivestages.common.lock.LockRegistry;
import com.enviouse.progressivestages.common.stage.StageOrder;
import com.enviouse.progressivestages.common.tags.StageTagRegistry;
import com.enviouse.progressivestages.common.util.Constants;
import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Loads stage definition files from the ProgressiveStages directory
 */
public class StageFileLoader {

    private static final Logger LOGGER = LogUtils.getLogger();

    private final Map<StageId, StageDefinition> loadedStages = new LinkedHashMap<>();
    private Path stagesDirectory;

    private static StageFileLoader INSTANCE;

    public static StageFileLoader getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new StageFileLoader();
        }
        return INSTANCE;
    }

    private StageFileLoader() {}

    /**
     * Initialize the loader and create default files if needed
     */
    public void initialize(MinecraftServer server) {
        // Get the world folder path
        Path worldFolder = server.getWorldPath(LevelResource.ROOT);
        stagesDirectory = worldFolder.resolve(Constants.STAGE_FILES_DIRECTORY);

        // Create directory if it doesn't exist
        if (!Files.exists(stagesDirectory)) {
            try {
                Files.createDirectories(stagesDirectory);
                LOGGER.info("Created ProgressiveStages directory: {}", stagesDirectory);

                // Generate default stage files
                generateDefaultStageFiles();
            } catch (IOException e) {
                LOGGER.error("Failed to create ProgressiveStages directory", e);
            }
        }

        // Load all stage files
        loadAllStages();

        // Register with lock registry
        registerLocksFromStages();
    }

    /**
     * Reload all stage files from disk
     */
    public void reload() {
        loadedStages.clear();
        LockRegistry.getInstance().clear();
        StageOrder.getInstance().clear();
        StageTagRegistry.clear();

        loadAllStages();
        registerLocksFromStages();

        LOGGER.info("Reloaded {} stages", loadedStages.size());
    }

    /**
     * Load all .toml files from the stages directory
     */
    private void loadAllStages() {
        if (stagesDirectory == null || !Files.exists(stagesDirectory)) {
            LOGGER.warn("Stages directory not found");
            return;
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(stagesDirectory, "*.toml")) {
            for (Path file : stream) {
                loadStageFile(file);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to list stage files", e);
        }

        // Sort stages by order and register with StageOrder
        List<StageDefinition> sortedStages = new ArrayList<>(loadedStages.values());
        sortedStages.sort(Comparator.comparingInt(StageDefinition::getOrder));

        for (StageDefinition stage : sortedStages) {
            StageOrder.getInstance().registerStage(stage);
        }

        LOGGER.info("Loaded {} stage definitions", loadedStages.size());
    }

    private void loadStageFile(Path file) {
        Optional<StageDefinition> stageOpt = StageFileParser.parse(file);

        if (stageOpt.isPresent()) {
            StageDefinition stage = stageOpt.get();

            // Check for duplicate IDs
            if (loadedStages.containsKey(stage.getId())) {
                LOGGER.warn("Duplicate stage ID: {} in file {}", stage.getId(), file);
                return;
            }

            loadedStages.put(stage.getId(), stage);
            LOGGER.debug("Loaded stage: {} (order: {})", stage.getId(), stage.getOrder());
        } else {
            LOGGER.warn("Failed to parse stage file: {}", file);
        }
    }

    /**
     * Register all locks from loaded stages to the LockRegistry
     */
    private void registerLocksFromStages() {
        LockRegistry registry = LockRegistry.getInstance();

        for (StageDefinition stage : loadedStages.values()) {
            registry.registerStage(stage);
        }

        // Build dynamic stage tags for EMI integration
        StageTagRegistry.rebuildFromStages();

        LOGGER.debug("Registered locks from {} stages", loadedStages.size());
    }

    /**
     * Validation result for a single file
     */
    public static class FileValidationResult {
        public final String fileName;
        public final boolean success;
        public final boolean syntaxError;
        public final String errorMessage;
        public final List<String> invalidItems;

        public FileValidationResult(String fileName, boolean success, boolean syntaxError,
                                     String errorMessage, List<String> invalidItems) {
            this.fileName = fileName;
            this.success = success;
            this.syntaxError = syntaxError;
            this.errorMessage = errorMessage;
            this.invalidItems = invalidItems != null ? invalidItems : new ArrayList<>();
        }
    }

    /**
     * Validate all stage files with detailed error reporting
     */
    public List<FileValidationResult> validateAllStages() {
        List<FileValidationResult> results = new ArrayList<>();

        if (stagesDirectory == null || !Files.exists(stagesDirectory)) {
            return results;
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(stagesDirectory, "*.toml")) {
            for (Path file : stream) {
                results.add(validateStageFile(file));
            }
        } catch (IOException e) {
            LOGGER.error("Failed to list stage files for validation", e);
        }

        return results;
    }

    private FileValidationResult validateStageFile(Path file) {
        String fileName = file.getFileName().toString();

        // Try to parse with error details
        StageFileParser.ParseResult parseResult = StageFileParser.parseWithErrors(file);

        if (!parseResult.isSuccess()) {
            return new FileValidationResult(
                fileName,
                false,
                parseResult.isSyntaxError(),
                parseResult.getErrorMessage(),
                null
            );
        }

        // Parse succeeded, now validate all resource IDs
        StageDefinition stage = parseResult.getStageDefinition();
        List<String> invalidItems = new ArrayList<>();

        var itemRegistry = net.minecraft.core.registries.BuiltInRegistries.ITEM;
        var blockRegistry = net.minecraft.core.registries.BuiltInRegistries.BLOCK;

        // Validate item IDs
        for (String itemId : stage.getLocks().getItems()) {
            try {
                var rl = net.minecraft.resources.ResourceLocation.parse(itemId);
                if (!itemRegistry.containsKey(rl)) {
                    invalidItems.add("Item: " + itemId);
                }
            } catch (Exception e) {
                invalidItems.add("Item: " + itemId + " (invalid format)");
            }
        }

        // Validate block IDs
        for (String blockId : stage.getLocks().getBlocks()) {
            try {
                var rl = net.minecraft.resources.ResourceLocation.parse(blockId);
                if (!blockRegistry.containsKey(rl)) {
                    invalidItems.add("Block: " + blockId);
                }
            } catch (Exception e) {
                invalidItems.add("Block: " + blockId + " (invalid format)");
            }
        }

        // Note: Recipe validation would require recipe manager access which isn't available at this point
        // Recipes are validated at runtime when checking locks

        if (!invalidItems.isEmpty()) {
            return new FileValidationResult(
                fileName,
                false,
                false,
                "Contains " + invalidItems.size() + " invalid resource IDs",
                invalidItems
            );
        }

        return new FileValidationResult(fileName, true, false, null, null);
    }

    /**
     * Get the stages directory path
     */
    public Path getStagesDirectory() {
        return stagesDirectory;
    }

    /**
     * Count total stage files in directory
     */
    public int countStageFiles() {
        if (stagesDirectory == null || !Files.exists(stagesDirectory)) {
            return 0;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(stagesDirectory, "*.toml")) {
            int count = 0;
            for (Path ignored : stream) count++;
            return count;
        } catch (IOException e) {
            return 0;
        }
    }

    /**
     * Get a stage by ID
     */
    public Optional<StageDefinition> getStage(StageId id) {
        return Optional.ofNullable(loadedStages.get(id));
    }

    /**
     * Get all loaded stages
     */
    public Collection<StageDefinition> getAllStages() {
        return Collections.unmodifiableCollection(loadedStages.values());
    }

    /**
     * Get all stage IDs
     */
    public Set<StageId> getAllStageIds() {
        return Collections.unmodifiableSet(loadedStages.keySet());
    }

    /**
     * Generate default example stage files
     */
    private void generateDefaultStageFiles() {
        generateStoneAgeFile();
        generateIronAgeFile();
        generateDiamondAgeFile();
    }

    private void generateStoneAgeFile() {
        String content = """
            # Stage definition for Stone Age
            # This is the starting stage - nothing is locked here
            
            [stage]
            id = "stone_age"
            display_name = "Stone Age"
            description = "Basic survival tools and resources"
            order = 1
            icon = "minecraft:stone_pickaxe"
            unlock_message = "&7&lStone Age Unlocked! &r&8Begin your journey."
            
            [locks]
            # Stone Age is the starting stage - nothing locked here
            items = []
            item_tags = []
            recipes = []
            recipe_tags = []
            blocks = []
            block_tags = []
            dimensions = []
            mods = []
            names = []
            """;

        writeStageFile("stone_age.toml", content);
    }

    private void generateIronAgeFile() {
        String content = """
            # Stage definition for Iron Age
            # Unlocks iron tools, armor, and related items
            
            [stage]
            id = "iron_age"
            display_name = "Iron Age"
            description = "Iron tools, armor, and basic machinery"
            order = 2
            icon = "minecraft:iron_pickaxe"
            unlock_message = "&6&lIron Age Unlocked! &r&7You can now use iron equipment."
            
            [locks]
            items = [
                "minecraft:iron_ingot",
                "minecraft:iron_block",
                "minecraft:iron_ore",
                "minecraft:deepslate_iron_ore",
                "minecraft:raw_iron",
                "minecraft:raw_iron_block",
                "minecraft:iron_pickaxe",
                "minecraft:iron_sword",
                "minecraft:iron_axe",
                "minecraft:iron_shovel",
                "minecraft:iron_hoe",
                "minecraft:iron_helmet",
                "minecraft:iron_chestplate",
                "minecraft:iron_leggings",
                "minecraft:iron_boots",
                "minecraft:shield",
                "minecraft:bucket",
                "minecraft:shears",
                "minecraft:flint_and_steel"
            ]
            
            item_tags = []
            
            recipes = [
                "minecraft:iron_pickaxe",
                "minecraft:iron_sword",
                "minecraft:iron_axe",
                "minecraft:iron_shovel",
                "minecraft:iron_hoe",
                "minecraft:iron_helmet",
                "minecraft:iron_chestplate",
                "minecraft:iron_leggings",
                "minecraft:iron_boots",
                "minecraft:bucket",
                "minecraft:shield"
            ]
            
            recipe_tags = []
            
            blocks = [
                "minecraft:iron_block",
                "minecraft:iron_door",
                "minecraft:iron_trapdoor",
                "minecraft:iron_bars"
            ]
            
            block_tags = []
            dimensions = []
            mods = []
            names = []
            """;

        writeStageFile("iron_age.toml", content);
    }

    private void generateDiamondAgeFile() {
        String content = """
            # ============================================================================
            # Stage definition for Diamond Age
            # This file demonstrates ALL lock types available in ProgressiveStages
            # ============================================================================
            
            [stage]
            # Unique identifier (must match filename without .toml)
            id = "diamond_age"
            
            # Display name shown in tooltips, messages, etc.
            display_name = "Diamond Age"
            
            # Description for quest integration or future GUI
            description = "Diamond tools, armor, and advanced equipment"
            
            # Order in progression (lower = earlier, must be unique across all stages)
            order = 3
            
            # Icon item for visual representation
            icon = "minecraft:diamond_pickaxe"
            
            # Message sent to ALL team members when this stage is unlocked
            # Supports color codes: &c=red, &a=green, &b=aqua, &l=bold, &o=italic, &r=reset
            unlock_message = "&b&lDiamond Age Unlocked! &r&7You can now use diamond items."
            
            # ============================================================================
            # LOCKS - Define what is locked UNTIL this stage is unlocked
            # ============================================================================
            
            [locks]
            
            # -----------------------------------------------------------------------------
            # ITEMS - Lock specific items by registry ID
            # Players cannot use, pickup, or hold these items until stage is unlocked
            # -----------------------------------------------------------------------------
            items = [
                "minecraft:diamond",
                "minecraft:diamond_block",
                "minecraft:diamond_ore",
                "minecraft:deepslate_diamond_ore",
                "minecraft:diamond_pickaxe",
                "minecraft:diamond_sword",
                "minecraft:diamond_axe",
                "minecraft:diamond_shovel",
                "minecraft:diamond_hoe",
                "minecraft:diamond_helmet",
                "minecraft:diamond_chestplate",
                "minecraft:diamond_leggings",
                "minecraft:diamond_boots",
                "minecraft:enchanting_table",
                "minecraft:jukebox"
            ]
            
            # -----------------------------------------------------------------------------
            # ITEM TAGS - Lock all items in a tag (use # prefix)
            # Example: "#c:gems/diamond" locks all items tagged as diamond gems
            # -----------------------------------------------------------------------------
            item_tags = [
                # "#c:gems/diamond",
                # "#c:storage_blocks/diamond"
            ]
            
            # -----------------------------------------------------------------------------
            # RECIPES - Lock specific crafting recipes by ID
            # Players cannot craft these recipes even if they have the items
            # -----------------------------------------------------------------------------
            recipes = [
                "minecraft:diamond_pickaxe",
                "minecraft:diamond_sword",
                "minecraft:diamond_axe",
                "minecraft:diamond_shovel",
                "minecraft:diamond_hoe",
                "minecraft:diamond_helmet",
                "minecraft:diamond_chestplate",
                "minecraft:diamond_leggings",
                "minecraft:diamond_boots",
                "minecraft:diamond_block",
                "minecraft:enchanting_table",
                "minecraft:jukebox"
            ]
            
            recipe_tags = []
            
            # -----------------------------------------------------------------------------
            # BLOCKS - Lock block placement and interaction
            # Players cannot place or right-click these blocks
            # -----------------------------------------------------------------------------
            blocks = [
                "minecraft:diamond_block",
                "minecraft:enchanting_table"
            ]
            
            block_tags = []
            
            # -----------------------------------------------------------------------------
            # DIMENSIONS - Lock entire dimensions (prevent portal travel)
            # Format: "namespace:dimension_id"
            # Uncomment to lock dimensions
            # -----------------------------------------------------------------------------
            dimensions = [
                # "minecraft:the_nether",
                # "minecraft:the_end"
            ]
            
            # -----------------------------------------------------------------------------
            # MODS - Lock ENTIRE mods (all items, blocks, recipes from that mod)
            # Format: "modid" (just the namespace, e.g., "mekanism", "create")
            # This is powerful - use carefully!
            # -----------------------------------------------------------------------------
            mods = [
                # "mekanism",
                # "ae2"
            ]
            
            # -----------------------------------------------------------------------------
            # NAMES - Lock items/blocks by name (case-insensitive substring matching)
            # Locks ANYTHING with this text in its registry ID
            # Example: "diamond" locks "minecraft:diamond", "botania:diamond_pickaxe", etc.
            # Very broad - use carefully!
            # -----------------------------------------------------------------------------
            names = [
                # "netherite"
            ]
            
            # -----------------------------------------------------------------------------
            # INTERACTIONS - Lock specific player-world interactions
            # Useful for Create mod's "apply item to block" mechanics
            # -----------------------------------------------------------------------------
            
            # Example: Lock using enchanting table (right-click block)
            # [[locks.interactions]]
            # type = "block_right_click"
            # target_block = "minecraft:enchanting_table"
            # description = "Use Enchanting Table"
            
            # Example: Lock Create Andesite Casing creation (item on block)
            # [[locks.interactions]]
            # type = "item_on_block"
            # held_item = "create:andesite_alloy"
            # target_block = "#minecraft:logs"
            # description = "Create Andesite Casing"
            """;

        writeStageFile("diamond_age.toml", content);
    }

    private void writeStageFile(String fileName, String content) {
        Path filePath = stagesDirectory.resolve(fileName);
        try {
            Files.writeString(filePath, content);
            LOGGER.info("Generated default stage file: {}", fileName);
        } catch (IOException e) {
            LOGGER.error("Failed to write stage file: {}", fileName, e);
        }
    }
}
