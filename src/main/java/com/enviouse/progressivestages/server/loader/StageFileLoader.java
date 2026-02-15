package com.enviouse.progressivestages.server.loader;

import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.config.StageDefinition;
import com.enviouse.progressivestages.common.lock.LockRegistry;
import com.enviouse.progressivestages.common.stage.StageOrder;
import com.enviouse.progressivestages.common.tags.StageTagRegistry;
import com.enviouse.progressivestages.common.util.Constants;
import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Loads stage definition files from the ProgressiveStages directory in config folder
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
        // Get the config folder path
        Path configFolder = FMLPaths.CONFIGDIR.get();
        stagesDirectory = configFolder.resolve(Constants.STAGE_FILES_DIRECTORY);

        // Create directory if it doesn't exist
        if (!Files.exists(stagesDirectory)) {
            try {
                Files.createDirectories(stagesDirectory);
                LOGGER.info("Created ProgressiveStages directory: {}", stagesDirectory);
            } catch (IOException e) {
                LOGGER.error("Failed to create ProgressiveStages directory", e);
            }
        }

        // Generate default stage files if none exist
        // Check if there are any .toml files (excluding triggers.toml)
        if (countStageFiles() == 0) {
            LOGGER.info("No stage files found, generating defaults...");
            generateDefaultStageFiles();
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
                // Skip triggers.toml - it's not a stage definition file
                String fileName = file.getFileName().toString().toLowerCase();
                if (fileName.equals("triggers.toml")) {
                    LOGGER.debug("Skipping triggers.toml - not a stage definition file");
                    continue;
                }
                loadStageFile(file);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to list stage files", e);
        }

        // Register all stages with StageOrder (no longer sorted by order in v1.3)
        for (StageDefinition stage : loadedStages.values()) {
            StageOrder.getInstance().registerStage(stage);
        }

        // Validate dependencies after all stages are loaded
        List<String> validationErrors = StageOrder.getInstance().validateDependencies();
        for (String error : validationErrors) {
            LOGGER.error("[ProgressiveStages] Dependency validation error: {}", error);
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
            LOGGER.debug("Loaded stage: {} with {} dependencies", stage.getId(), stage.getDependencies().size());
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
     * Count total stage files in directory (excludes triggers.toml)
     */
    public int countStageFiles() {
        if (stagesDirectory == null || !Files.exists(stagesDirectory)) {
            return 0;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(stagesDirectory, "*.toml")) {
            int count = 0;
            for (Path file : stream) {
                // Skip triggers.toml
                String fileName = file.getFileName().toString().toLowerCase();
                if (!fileName.equals("triggers.toml")) {
                    count++;
                }
            }
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
            # ============================================================================
            # Stage definition for Stone Age (v1.1)
            # This file demonstrates ALL features available in ProgressiveStages v1.1
            # This is a STARTING STAGE - no dependencies, granted to new players
            # ============================================================================
            
            [stage]
            # Unique identifier (must match filename without .toml)
            id = "stone_age"
            
            # Display name shown in tooltips, messages, etc.
            display_name = "Stone Age"
            
            # Description for quest integration or future GUI
            description = "Basic survival tools and resources - the beginning of your journey"
            
            # Icon item for visual representation
            icon = "minecraft:stone_pickaxe"
            
            # Message sent to ALL team members when this stage is unlocked
            # Supports color codes: &c=red, &a=green, &b=aqua, &l=bold, &o=italic, &r=reset
            unlock_message = "&7&lStone Age Unlocked! &r&8Begin your journey into the unknown."
            
            # ============================================================================
            # DEPENDENCY (v1.1) - Stage(s) that must be unlocked BEFORE this one
            # ============================================================================
            
            # No dependency - this is a starting stage (granted automatically to new players)
            # To make this require another stage, uncomment one of these:
            
            # Single dependency:
            # dependency = "tutorial_complete"
            
            # Multiple dependencies (list format):
            # dependency = ["tutorial_complete", "spawn_visit"]
            
            # ============================================================================
            # LOCKS - Define what is locked UNTIL this stage is unlocked
            # Stone Age is the starting stage, so we don't lock anything here.
            # Everything below is empty but shows the available options.
            # ============================================================================
            
            [locks]
            
            # -----------------------------------------------------------------------------
            # ITEMS - Lock specific items by registry ID
            # Players cannot use, pickup, or hold these items until stage is unlocked
            # Example: items = ["minecraft:wooden_pickaxe", "minecraft:wooden_sword"]
            # -----------------------------------------------------------------------------
            items = []
            
            # -----------------------------------------------------------------------------
            # ITEM TAGS - Lock all items in a tag (use # prefix)
            # Example: item_tags = ["#c:tools/wooden", "#minecraft:wooden_slabs"]
            # This locks ALL items that are part of the specified tag
            # -----------------------------------------------------------------------------
            item_tags = []
            
            # -----------------------------------------------------------------------------
            # BLOCKS - Lock block placement and interaction
            # Players cannot place or right-click these blocks
            # Example: blocks = ["minecraft:crafting_table", "minecraft:furnace"]
            # -----------------------------------------------------------------------------
            blocks = []
            
            # -----------------------------------------------------------------------------
            # BLOCK TAGS - Lock all blocks in a tag
            # Example: block_tags = ["#minecraft:logs"]
            # -----------------------------------------------------------------------------
            block_tags = []
            
            # -----------------------------------------------------------------------------
            # DIMENSIONS - Lock entire dimensions (prevent portal travel)
            # Format: "namespace:dimension_id"
            # Example: dimensions = ["minecraft:the_nether", "minecraft:the_end"]
            # -----------------------------------------------------------------------------
            dimensions = []
            
            # -----------------------------------------------------------------------------
            # MODS - Lock ENTIRE mods (all items, blocks, recipes from that mod)
            # Format: "modid" (just the namespace, e.g., "mekanism", "create")
            # This is powerful - use carefully!
            # Example: mods = ["mekanism", "ae2", "create"]
            # -----------------------------------------------------------------------------
            mods = []
            
            # -----------------------------------------------------------------------------
            # NAMES - Lock items/blocks by name (case-insensitive substring matching)
            # Locks ANYTHING with this text in its registry ID
            # Example: names = ["iron"] locks "minecraft:iron_ingot", "create:iron_sheet", etc.
            # Very broad - use carefully!
            # -----------------------------------------------------------------------------
            names = []
            
            # -----------------------------------------------------------------------------
            # UNLOCKED_ITEMS (v1.1) - Whitelist exceptions
            # These items are ALWAYS accessible, even if they would be locked by:
            #   - mods = ["somemod"]
            #   - names = ["stone"]
            #   - item_tags = ["#sometag"]
            #
            # Use case: Lock entire mod but allow specific items from it
            # Example: unlocked_items = ["mekanism:configurator"]
            # -----------------------------------------------------------------------------
            unlocked_items = []
            
            # -----------------------------------------------------------------------------
            # INTERACTIONS - Lock specific player-world interactions
            # Useful for Create mod's "apply item to block" mechanics
            # -----------------------------------------------------------------------------
            
            # Example: Lock using a block (right-click)
            # [[locks.interactions]]
            # type = "block_right_click"
            # target_block = "minecraft:crafting_table"
            # description = "Use Crafting Table"
            
            # Example: Lock applying item to block (Create-style)
            # [[locks.interactions]]
            # type = "item_on_block"
            # held_item = "create:andesite_alloy"
            # target_block = "#minecraft:logs"
            # description = "Create Andesite Casing"
            """;

        writeStageFile("stone_age.toml", content);
    }

    private void generateIronAgeFile() {
        String content = """
            # ============================================================================
            # Stage definition for Iron Age (v1.1)
            # This file demonstrates ALL features available in ProgressiveStages v1.1
            # Requires stone_age to be unlocked first
            # ============================================================================
            
            [stage]
            # Unique identifier (must match filename without .toml)
            id = "iron_age"
            
            # Display name shown in tooltips, messages, etc.
            display_name = "Iron Age"
            
            # Description for quest integration or future GUI
            description = "Iron tools, armor, and basic machinery - industrialization begins"
            
            # Icon item for visual representation
            icon = "minecraft:iron_pickaxe"
            
            # Message sent to ALL team members when this stage is unlocked
            # Supports color codes: &c=red, &a=green, &b=aqua, &l=bold, &o=italic, &r=reset
            unlock_message = "&6&lIron Age Unlocked! &r&7You can now use iron equipment and basic machines."
            
            # ============================================================================
            # DEPENDENCY (v1.1) - Stage(s) that must be unlocked BEFORE this one
            # ============================================================================
            
            # Single dependency - requires stone_age before this can be granted
            dependency = "stone_age"
            
            # Multiple dependencies (list format):
            # dependency = ["stone_age", "tutorial_complete"]
            
            # No dependency (can be obtained anytime):
            # Just omit this field or leave empty
            
            # ============================================================================
            # LOCKS - Define what is locked UNTIL this stage is unlocked
            # ============================================================================
            
            [locks]
            
            # -----------------------------------------------------------------------------
            # ITEMS - Lock specific items by registry ID
            # Players cannot use, pickup, or hold these items until stage is unlocked
            # -----------------------------------------------------------------------------
            items = [
                # Raw materials
                "minecraft:iron_ingot",
                "minecraft:iron_block",
                "minecraft:iron_ore",
                "minecraft:deepslate_iron_ore",
                "minecraft:raw_iron",
                "minecraft:raw_iron_block",
                
                # Tools
                "minecraft:iron_pickaxe",
                "minecraft:iron_sword",
                "minecraft:iron_axe",
                "minecraft:iron_shovel",
                "minecraft:iron_hoe",
                
                # Armor
                "minecraft:iron_helmet",
                "minecraft:iron_chestplate",
                "minecraft:iron_leggings",
                "minecraft:iron_boots",
                
                # Utility items
                "minecraft:shield",
                "minecraft:bucket",
                "minecraft:shears",
                "minecraft:flint_and_steel",
                "minecraft:compass",
                "minecraft:clock",
                "minecraft:minecart",
                "minecraft:rail",
                "minecraft:powered_rail",
                "minecraft:detector_rail",
                "minecraft:activator_rail"
            ]
            
            # -----------------------------------------------------------------------------
            # ITEM TAGS - Lock all items in a tag (use # prefix)
            # Example: "#c:ingots/iron" locks all items tagged as iron ingots
            # -----------------------------------------------------------------------------
            item_tags = [
                # "#c:ingots/iron",
                # "#c:storage_blocks/iron"
            ]
            
            # -----------------------------------------------------------------------------
            # BLOCKS - Lock block placement and interaction
            # Players cannot place or right-click these blocks
            # -----------------------------------------------------------------------------
            blocks = [
                "minecraft:iron_block",
                "minecraft:iron_door",
                "minecraft:iron_trapdoor",
                "minecraft:iron_bars",
                "minecraft:hopper",
                "minecraft:blast_furnace",
                "minecraft:smithing_table"
            ]
            
            # -----------------------------------------------------------------------------
            # BLOCK TAGS - Lock all blocks in a tag
            # Example: block_tags = ["#minecraft:anvil"]
            # -----------------------------------------------------------------------------
            block_tags = []
            
            # -----------------------------------------------------------------------------
            # DIMENSIONS - Lock entire dimensions (prevent portal travel)
            # Format: "namespace:dimension_id"
            # Iron age doesn't lock dimensions, but you could lock the Nether:
            # dimensions = ["minecraft:the_nether"]
            # -----------------------------------------------------------------------------
            dimensions = []
            
            # -----------------------------------------------------------------------------
            # MODS - Lock ENTIRE mods (all items, blocks, recipes from that mod)
            # Format: "modid" (just the namespace, e.g., "mekanism", "create")
            # This is powerful - use carefully!
            # Example: Lock all of Create mod until iron age
            # mods = ["create"]
            # -----------------------------------------------------------------------------
            mods = []
            
            # -----------------------------------------------------------------------------
            # NAMES - Lock items/blocks by name (case-insensitive substring matching)
            # Locks ANYTHING with this text in its registry ID
            # Example: names = ["iron"] locks "minecraft:iron_ingot", "create:iron_sheet", etc.
            # Very broad - use carefully!
            # -----------------------------------------------------------------------------
            names = [
                # "iron"  # Uncomment to lock ALL items with "iron" in the name
            ]
            
            # -----------------------------------------------------------------------------
            # UNLOCKED_ITEMS (v1.1) - Whitelist exceptions
            # These items are ALWAYS accessible, even if they would be locked by:
            #   - mods = ["somemod"]
            #   - names = ["iron"]
            #   - item_tags = ["#sometag"]
            #
            # Use case: Lock by name "iron" but allow iron nuggets
            # Example: unlocked_items = ["minecraft:iron_nugget"]
            # -----------------------------------------------------------------------------
            unlocked_items = []
            
            # -----------------------------------------------------------------------------
            # INTERACTIONS - Lock specific player-world interactions
            # Useful for Create mod's "apply item to block" mechanics
            # -----------------------------------------------------------------------------
            
            # Example: Lock using a smithing table (right-click block)
            # [[locks.interactions]]
            # type = "block_right_click"
            # target_block = "minecraft:smithing_table"
            # description = "Use Smithing Table"
            
            # Example: Lock applying iron to Create blocks
            # [[locks.interactions]]
            # type = "item_on_block"
            # held_item = "minecraft:iron_ingot"
            # target_block = "create:andesite_casing"
            # description = "Apply Iron to Create Casing"
            """;

        writeStageFile("iron_age.toml", content);
    }

    private void generateDiamondAgeFile() {
        String content = """
            # ============================================================================
            # Stage definition for Diamond Age (v1.1)
            # This file demonstrates ALL features available in ProgressiveStages v1.1
            # ============================================================================
            
            [stage]
            # Unique identifier (must match filename without .toml)
            id = "diamond_age"
            
            # Display name shown in tooltips, messages, etc.
            display_name = "Diamond Age"
            
            # Description for quest integration or future GUI
            description = "Diamond tools, armor, and advanced equipment - true power awaits"
            
            # Icon item for visual representation
            icon = "minecraft:diamond_pickaxe"
            
            # Message sent to ALL team members when this stage is unlocked
            # Supports color codes: &c=red, &a=green, &b=aqua, &l=bold, &o=italic, &r=reset
            unlock_message = "&b&lDiamond Age Unlocked! &r&7You can now use diamond items and enchanting."
            
            # ============================================================================
            # DEPENDENCY (v1.1) - Stage(s) that must be unlocked BEFORE this one
            # ============================================================================
            
            # Single dependency:
            dependency = "iron_age"
            
            # Multiple dependencies (list format):
            # dependency = ["iron_age", "stone_age"]
            
            # No dependency (can be obtained anytime):
            # Just omit this field or leave empty
            
            # ============================================================================
            # LOCKS - Define what is locked UNTIL this stage is unlocked
            # ============================================================================
            
            [locks]
            
            # -----------------------------------------------------------------------------
            # ITEMS - Lock specific items by registry ID
            # Players cannot use, pickup, or hold these items until stage is unlocked
            # -----------------------------------------------------------------------------
            items = [
                # Raw materials
                "minecraft:diamond",
                "minecraft:diamond_block",
                "minecraft:diamond_ore",
                "minecraft:deepslate_diamond_ore",
                
                # Tools
                "minecraft:diamond_pickaxe",
                "minecraft:diamond_sword",
                "minecraft:diamond_axe",
                "minecraft:diamond_shovel",
                "minecraft:diamond_hoe",
                
                # Armor
                "minecraft:diamond_helmet",
                "minecraft:diamond_chestplate",
                "minecraft:diamond_leggings",
                "minecraft:diamond_boots",
                
                # Special items
                "minecraft:enchanting_table",
                "minecraft:jukebox",
                "minecraft:beacon",
                "minecraft:conduit",
                "minecraft:ender_chest",
                "minecraft:experience_bottle"
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
            # BLOCKS - Lock block placement and interaction
            # Players cannot place or right-click these blocks
            # -----------------------------------------------------------------------------
            blocks = [
                "minecraft:diamond_block",
                "minecraft:enchanting_table",
                "minecraft:beacon",
                "minecraft:conduit",
                "minecraft:ender_chest"
            ]
            
            # -----------------------------------------------------------------------------
            # BLOCK TAGS - Lock all blocks in a tag
            # Example: block_tags = ["#minecraft:dragon_immune"]
            # -----------------------------------------------------------------------------
            block_tags = []
            
            # -----------------------------------------------------------------------------
            # DIMENSIONS - Lock entire dimensions (prevent portal travel)
            # Format: "namespace:dimension_id"
            # Example: Lock The End until diamond age
            # -----------------------------------------------------------------------------
            dimensions = [
                # "minecraft:the_end"
            ]
            
            # -----------------------------------------------------------------------------
            # MODS - Lock ENTIRE mods (all items, blocks, recipes from that mod)
            # Format: "modid" (just the namespace, e.g., "mekanism", "create")
            # This is powerful - use carefully!
            # Example: Lock all of Applied Energistics 2 until diamond age
            # -----------------------------------------------------------------------------
            mods = [
                # "ae2"
            ]
            
            # -----------------------------------------------------------------------------
            # NAMES - Lock items/blocks by name (case-insensitive substring matching)
            # Locks ANYTHING with this text in its registry ID
            # Example: "diamond" locks "minecraft:diamond", "botania:diamond_pickaxe", etc.
            # Very broad - use carefully!
            # -----------------------------------------------------------------------------
            names = [
                # "netherite"  # Uncomment to lock ALL items with "netherite" in the name
            ]
            
            # -----------------------------------------------------------------------------
            # UNLOCKED_ITEMS (v1.1) - Whitelist exceptions
            # These items are ALWAYS accessible, even if they would be locked by:
            #   - mods = ["somemod"]
            #   - names = ["diamond"]
            #   - item_tags = ["#sometag"]
            #
            # Use case: Lock entire mod but allow specific items from it
            # Example: Lock by name "diamond" but allow diamond horse armor for decoration
            # -----------------------------------------------------------------------------
            unlocked_items = [
                # "minecraft:diamond_horse_armor"
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
