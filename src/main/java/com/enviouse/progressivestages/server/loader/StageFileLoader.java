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
                // Skip triggers.toml - it's not a stage definition file
                String fileName = file.getFileName().toString().toLowerCase();
                if (fileName.equals("triggers.toml")) {
                    continue;
                }
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

            # Example: Lock feeding an item to an entity (item_on_entity)
            # Blocks right-clicking the entity while holding the specified item
            # [[locks.interactions]]
            # type = "item_on_entity"
            # held_item = "minecraft:cod"
            # target_entity = "minecraft:dolphin"
            # description = "Feed Dolphin"
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
            # RECIPES - Lock specific crafting recipes by registry ID
            # The item remains usable and visible in EMI, but the recipe is hidden and
            # uncraftable until this stage is unlocked. Use this instead of items = [...]
            # when you want the item to be obtainable via other means (loot, trading, etc.)
            # Example: recipes = ["minecraft:iron_sword", "minecraft:iron_pickaxe"]
            # -----------------------------------------------------------------------------
            recipes = [
                # "minecraft:iron_sword"
            ]

            # -----------------------------------------------------------------------------
            # RECIPE TAGS - Lock all recipes in a tag
            # Example: recipe_tags = ["#c:tools/iron"]
            # -----------------------------------------------------------------------------
            recipe_tags = []

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

            # Example: Lock feeding wheat to a cow until iron age
            # [[locks.interactions]]
            # type = "item_on_entity"
            # held_item = "minecraft:wheat"
            # target_entity = "#minecraft:beehive_inhabitable"
            # description = "Breed Cows (requires iron age)"
            """;

        writeStageFile("iron_age.toml", content);
    }

    private void generateDiamondAgeFile() {
        String content = """
            # ============================================================================
            # Stage definition for Diamond Age (v1.4)
            # This file demonstrates ALL features available in ProgressiveStages v1.4
            # ============================================================================
            
            [stage]
            # Unique identifier (must match filename without .toml)
            id = "diamond_age"
            
            # Display name shown in tooltips, messages, and UI
            display_name = "Diamond Age"
            
            # Description for quest integration or future GUI
            description = "Diamond tools, armor, and advanced equipment - true power awaits"
            
            # Icon item for visual representation (supports any item ID)
            icon = "minecraft:diamond_pickaxe"
            
            # Message sent to ALL team members when this stage is unlocked
            # Supports color codes: &c=red, &a=green, &b=aqua, &e=yellow, &l=bold, &o=italic, &r=reset
            unlock_message = "&b&lDiamond Age Unlocked! &r&7You can now use diamond items and enchanting."
            
            # ============================================================================
            # DEPENDENCY (v1.3) - Stage(s) that must be unlocked BEFORE this one
            # ============================================================================
            #
            # Single dependency (most common):
            dependency = "iron_age"
            #
            # Multiple dependencies (player needs ALL of these):
            # dependency = ["iron_age", "nether_access"]
            #
            # No dependency (can be granted anytime):
            # (simply omit the dependency field)
            #
            # ============================================================================
            
            # ============================================================================
            # LOCKS - Define what is locked UNTIL this stage is unlocked
            # ============================================================================
            #
            # Everything below is LOCKED for players who do NOT have this stage.
            # Once the stage is granted, all locks from this stage are released.
            #
            # ============================================================================
            
            [locks]
            
            # =============================================================================
            # ITEMS - Lock specific items by exact registry ID
            # =============================================================================
            # Players cannot:
            #   - Pick up these items
            #   - Use/right-click with these items
            #   - Hold these items in inventory (they get dropped)
            #   - Craft these items (recipe locked)
            #
            # Format: "namespace:item_id"
            # Use F3+H in-game to see item registry IDs
            # =============================================================================
            items = [
                # ─── Raw Materials ───
                "minecraft:diamond",
                "minecraft:diamond_block",
                "minecraft:diamond_ore",
                "minecraft:deepslate_diamond_ore",
                
                # ─── Tools ───
                "minecraft:diamond_pickaxe",
                "minecraft:diamond_sword",
                "minecraft:diamond_axe",
                "minecraft:diamond_shovel",
                "minecraft:diamond_hoe",
                
                # ─── Armor ───
                "minecraft:diamond_helmet",
                "minecraft:diamond_chestplate",
                "minecraft:diamond_leggings",
                "minecraft:diamond_boots",
                
                # ─── Advanced Equipment ───
                "minecraft:enchanting_table",
                "minecraft:jukebox",
                "minecraft:beacon",
                "minecraft:conduit",
                "minecraft:ender_chest",
                "minecraft:experience_bottle",
                "minecraft:firework_rocket",
                "minecraft:firework_star",
                "minecraft:end_crystal"
            ]
            
            # =============================================================================
            # ITEM TAGS - Lock all items belonging to a tag
            # =============================================================================
            # Format: "#namespace:tag_path"
            # Common tag namespaces: c (common/cross-mod), minecraft, forge, neoforge
            #
            # Use /tags or F3+H to discover tags on items
            # =============================================================================
            item_tags = [
                # Lock all diamond gems from any mod
                # "#c:gems/diamond",
                
                # Lock all diamond storage blocks
                # "#c:storage_blocks/diamond",
                
                # Lock all enchanting-related items
                # "#c:enchanting_fuels"
            ]
            
            # =============================================================================
            # ITEM_MODS - Lock ALL items from entire mods
            # =============================================================================
            # This locks ONLY ITEMS from the mod (not blocks, entities, or fluids).
            # For full mod lock, use mods = ["modid"] instead.
            #
            # Format: "modid" (the namespace, e.g., "ae2", "mekanism", "create")
            # =============================================================================
            item_mods = [
                # Lock all items from Applied Energistics 2 (but not AE2 blocks)
                # "ae2",
                
                # Lock all items from Botania (but not Botania blocks)
                # "botania"
            ]
            
            # =============================================================================
            # RECIPE_ITEMS (v1.4) - Lock recipes by OUTPUT item ID (recipe-only lock)
            # =============================================================================
            # ⚠️ This is DIFFERENT from items = [...] !
            #
            # recipe_items locks ONLY the crafting recipe — the item itself is NOT locked.
            # Players CAN:
            #   ✅ Pick up the item (from loot chests, mob drops, etc.)
            #   ✅ Hold it in inventory
            #   ✅ Use it (right-click, mine, attack, etc.)
            #   ✅ See it in EMI (item stays visible)
            # Players CANNOT:
            #   ❌ Craft the item (recipe is blocked)
            #   ❌ See the recipe in EMI (recipe is hidden)
            #
            # Use case: Allow players to use items found in loot but prevent crafting
            # until they unlock the stage. Perfect for progression-gated gear.
            #
            # Tooltip shows: "🔒 Recipe Locked" (vs "🔒 Item Locked" for items = [...])
            # If BOTH items AND recipe_items contain the same item, tooltip shows:
            #   "🔒 Item and Recipe Locked"
            #
            # Format: "namespace:item_id"
            # =============================================================================
            recipe_items = [
                # Lock crafting of diamond tools (but allow using them if found in loot)
                # "minecraft:diamond_pickaxe",
                # "minecraft:diamond_sword",
                # "minecraft:diamond_axe",
                
                # Lock crafting enchanting table (but allow using one found in a dungeon)
                # "minecraft:enchanting_table"
            ]
            
            # =============================================================================
            # BLOCKS - Lock specific blocks by exact registry ID
            # =============================================================================
            # Players cannot:
            #   - Place these blocks
            #   - Right-click/interact with these blocks
            #   - Break these blocks (optional, via config)
            #
            # Format: "namespace:block_id"
            # =============================================================================
            blocks = [
                "minecraft:diamond_block",
                "minecraft:enchanting_table",
                "minecraft:beacon",
                "minecraft:conduit",
                "minecraft:ender_chest",
                "minecraft:respawn_anchor",
                "minecraft:lodestone"
            ]
            
            # =============================================================================
            # BLOCK TAGS - Lock all blocks belonging to a tag
            # =============================================================================
            # Format: "#namespace:tag_path"
            # =============================================================================
            block_tags = [
                # Lock all beacon base blocks
                # "#minecraft:beacon_base_blocks",
                
                # Lock all crystal sound blocks
                # "#minecraft:crystal_sound_blocks"
            ]
            
            # =============================================================================
            # BLOCK_MODS - Lock ALL blocks from entire mods
            # =============================================================================
            # This locks ONLY BLOCKS from the mod (not items, entities, or fluids).
            # For full mod lock, use mods = ["modid"] instead.
            #
            # Format: "modid"
            # =============================================================================
            block_mods = [
                # Lock all blocks from Applied Energistics 2
                # "ae2"
            ]
            
            # =============================================================================
            # DIMENSIONS - Lock entire dimensions (prevent portal travel)
            # =============================================================================
            # Players cannot enter these dimensions until stage is unlocked.
            # Portal interactions are blocked, and any teleportation is cancelled.
            #
            # Format: "namespace:dimension_id"
            # Common dimensions: minecraft:the_nether, minecraft:the_end
            # =============================================================================
            dimensions = [
                # Lock The End until diamond age (commented out by default)
                # "minecraft:the_end"
            ]
            
            # =============================================================================
            # FLUIDS (v1.4) - Lock specific fluids (EMI/JEI visibility ONLY)
            # =============================================================================
            # ⚠️ IMPORTANT: Fluid locks ONLY hide fluids from EMI/JEI recipe browsers!
            # They do NOT prevent:
            #   - Piping fluids with pipes/conduits
            #   - Pumping fluids with machines
            #   - Using fluids in tanks/machines
            #   - Natural fluid generation
            #
            # To actually block fluid usage, lock the MACHINES that process them.
            #
            # Format: "namespace:fluid_id"
            # =============================================================================
            fluids = [
                # Hide specific fluids from EMI/JEI
                # "mekanism:heavy_water",
                # "mekanism:sulfuric_acid"
            ]
            
            # =============================================================================
            # FLUID_TAGS (v1.4) - Lock all fluids in a tag (EMI/JEI visibility ONLY)
            # =============================================================================
            # Format: "#namespace:tag_path"
            # =============================================================================
            fluid_tags = [
                # Hide all acidic fluids from EMI/JEI
                # "#c:acids",
                
                # Hide all gaseous fluids
                # "#c:gaseous"
            ]
            
            # =============================================================================
            # FLUID_MODS (v1.4) - Lock ALL fluids from entire mods (EMI/JEI only)
            # =============================================================================
            # Hides all fluids from a mod in EMI/JEI. Does NOT affect gameplay.
            # Format: "modid"
            # =============================================================================
            fluid_mods = [
                # Hide all Mekanism fluids from EMI/JEI until stage unlocked
                # "mekanism",
                
                # Hide all Thermal fluids
                # "thermal"
            ]
            
            # =============================================================================
            # MODS - Lock ENTIRE mods (items, blocks, entities, AND fluids)
            # =============================================================================
            # ⚠️ This is the NUCLEAR OPTION - locks EVERYTHING from the mod:
            #   ✅ All items from the mod
            #   ✅ All blocks from the mod
            #   ✅ All entities from the mod (cannot attack)
            #   ✅ All fluids from the mod (hidden in EMI/JEI)
            #   ✅ All recipes involving mod items
            #
            # For finer control, use item_mods, block_mods, entity_mods, or fluid_mods.
            #
            # Format: "modid"
            # =============================================================================
            mods = [
                # Lock ALL of Applied Energistics 2 until diamond age
                # "ae2",
                
                # Lock ALL of Create until diamond age
                # "create"
            ]
            
            # =============================================================================
            # NAMES - Lock by name pattern (case-insensitive substring match)
            # =============================================================================
            # ⚠️ This is VERY POWERFUL and VERY BROAD!
            #
            # Locks EVERYTHING with this text anywhere in its registry ID:
            #   ✅ Items: minecraft:diamond, botania:diamond_pickaxe
            #   ✅ Blocks: minecraft:diamond_block, ae2:diamond_storage
            #   ✅ Entities: somemod:diamond_golem, modpack:diamond_zombie
            #   ✅ Fluids: mekanism:liquid_diamond, somemod:molten_diamond
            #
            # The pattern is matched ANYWHERE in "namespace:path":
            #   "diamond" matches: minecraft:diamond, my_mod:super_diamond_sword
            #   "iron" matches: minecraft:iron_ingot, create:iron_sheet
            #
            # Use with CAUTION - this can lock more than you expect!
            # ⚠️ Performance: iterates ALL registries to resolve matches.
            # In very large modpacks (10k+ items), this can add ~50-500ms at startup.
            # =============================================================================
            names = [
                # Lock everything with "diamond" in the name
                "diamond"
                
                # Also lock everything with "netherite" (uncomment to enable)
                # "netherite"
            ]
            
            # =============================================================================
            # ENTITIES - Lock specific entity types (prevent attacking)
            # =============================================================================
            # Players cannot attack these entities until stage is unlocked.
            # The entity is still visible and can attack the player!
            #
            # Format: "namespace:entity_id"
            # =============================================================================
            entities = [
                # Lock attacking the Warden
                # "minecraft:warden",
                
                # Lock attacking the Elder Guardian
                # "minecraft:elder_guardian"
            ]
            
            # =============================================================================
            # ENTITY TAGS - Lock all entities in a tag
            # =============================================================================
            # Format: "#namespace:tag_path"
            # =============================================================================
            entity_tags = [
                # Lock attacking all boss entities
                # "#c:bosses",
                
                # Lock attacking all raid entities
                # "#minecraft:raiders"
            ]
            
            # =============================================================================
            # ENTITY_MODS - Lock ALL entities from entire mods
            # =============================================================================
            # Players cannot attack any entity from these mods.
            # Note: Using mods = ["modid"] also locks entities (cascade behavior).
            #
            # Format: "modid"
            # =============================================================================
            entity_mods = [
                # Lock attacking all Mekanism entities (Robit, etc.)
                # "mekanism",
                
                # Lock attacking all Alex's Mobs entities
                # "alexsmobs"
            ]
            
            # =============================================================================
            # UNLOCKED_ITEMS (v1.1) - Whitelist exceptions for items
            # =============================================================================
            # These items are ALWAYS accessible, even if they would be locked by:
            #   - mods = ["modid"]
            #   - item_mods = ["modid"]
            #   - names = ["pattern"]
            #   - item_tags = ["#tag"]
            #
            # Use case: Lock names = ["diamond"] but allow diamond horse armor
            # =============================================================================
            unlocked_items = [
                # Allow diamond horse armor even though "diamond" is locked
                # "minecraft:diamond_horse_armor",
                
                # Allow a specific mod item even though the mod is locked
                # "ae2:certus_quartz_crystal"
            ]
            
            # =============================================================================
            # UNLOCKED_BLOCKS (v1.4) - Whitelist exceptions for blocks
            # =============================================================================
            # These blocks are ALWAYS placeable/interactable, even if locked by:
            #   - mods = ["modid"]
            #   - block_mods = ["modid"]
            #   - names = ["pattern"]
            #   - block_tags = ["#tag"]
            # =============================================================================
            unlocked_blocks = [
                # Allow AE2 charger even if ae2 is locked
                # "ae2:charger",
                
                # Allow diamond pressure plate for redstone builds
                # "some_mod:diamond_pressure_plate"
            ]
            
            # =============================================================================
            # UNLOCKED_ENTITIES (v1.1) - Whitelist exceptions for entities
            # =============================================================================
            # These entities can ALWAYS be attacked, even if locked by:
            #   - mods = ["modid"]
            #   - entity_mods = ["modid"]
            #   - names = ["pattern"]
            #   - entity_tags = ["#tag"]
            # =============================================================================
            unlocked_entities = [
                # Allow attacking Robit even if mekanism entities are locked
                # "mekanism:robit"
            ]
            
            # =============================================================================
            # UNLOCKED_FLUIDS (v1.4) - Whitelist exceptions for fluids (EMI/JEI)
            # =============================================================================
            # These fluids are ALWAYS visible in EMI/JEI, even if locked by:
            #   - mods = ["modid"]
            #   - fluid_mods = ["modid"]
            #   - names = ["pattern"]
            #   - fluid_tags = ["#tag"]
            #
            # Use case: Lock mods = ["mekanism"] but show hydrogen for tutorial
            # =============================================================================
            unlocked_fluids = [
                # Show hydrogen and oxygen in EMI even if mekanism is locked
                # "mekanism:hydrogen",
                # "mekanism:oxygen"
            ]
            
            # =============================================================================
            # INTERACTIONS - Lock specific player-world interactions
            # =============================================================================
            # Lock specific combinations of "player does X to Y" actions.
            # Useful for Create mod mechanics, right-click crafting, etc.
            #
            # Supported types:
            #   - "block_right_click" - Right-clicking a specific block
            #   - "item_on_block" - Using an item on a specific block (Create-style)
            #   - "item_on_entity" - Using an item on a specific entity
            # =============================================================================
            
            # ─── Example: Lock using the enchanting table ───
            [[locks.interactions]]
            type = "block_right_click"
            target_block = "minecraft:enchanting_table"
            description = "Use Enchanting Table"
            
            # ─── Example: Lock using the beacon ───
            [[locks.interactions]]
            type = "block_right_click"
            target_block = "minecraft:beacon"
            description = "Configure Beacon"
            
            # ─── Example: Lock Create mechanical crafting (item on block) ───
            # [[locks.interactions]]
            # type = "item_on_block"
            # held_item = "create:andesite_alloy"
            # target_block = "#minecraft:logs"
            # description = "Create Andesite Casing"
            
            # ─── Example: Lock applying diamond to an entity ───
            # [[locks.interactions]]
            # type = "item_on_entity"
            # held_item = "minecraft:diamond"
            # target_entity = "minecraft:villager"
            # description = "Trade with Villager using Diamond"
            
            # ============================================================================
            # ENFORCEMENT EXCEPTIONS (v1.4) - Per-stage overrides for global enforcement
            # ============================================================================
            #
            # Even though items are locked by this stage, these exceptions allow specific
            # items/tags/mods to bypass certain enforcement types. The item is still
            # considered "locked" (shows lock icon, requires stage) but the specific
            # enforcement action is allowed.
            #
            # Each list accepts three formats:
            #   - Item IDs:   "minecraft:diamond_pickaxe"
            #   - Item tags:  "#c:gems/diamond"
            #   - Mod IDs:    "mekanism" (matches ALL items from that mod)
            #
            # Use case: Lock diamond items but allow picking up diamond ore so players
            # can stockpile it for when they unlock the stage.
            #
            # ⚠️ These exceptions only apply when the corresponding global enforcement
            # setting is enabled in progressivestages.toml (e.g., block_item_use = true).
            # If a global enforcement is disabled, these have no effect.
            # ============================================================================
            
            [enforcement]
            
            # Items that can be USED (right-click, left-click, mine, attack) even when locked
            # Example: Allow using diamond ore (it's locked but you can still mine it with other tools)
            allowed_use = [
                # "minecraft:diamond_ore",
                # "minecraft:deepslate_diamond_ore"
            ]
            
            # Items that can be PICKED UP from the ground even when locked
            # Example: Allow picking up raw diamonds so players can stockpile them
            allowed_pickup = [
                # "minecraft:diamond",
                # "#c:gems/diamond"
            ]
            
            # Items that can remain in the HOTBAR even when locked
            # Example: Allow keeping diamond ore in hotbar for quick access
            allowed_hotbar = [
                # "minecraft:diamond"
            ]
            
            # Items that can be MOVED with mouse in GUIs even when locked
            # Example: Allow storing diamonds in chests even though they're locked
            allowed_mouse_pickup = [
                # "minecraft:diamond",
                # "minecraft:diamond_ore"
            ]
            
            # Items that can remain in INVENTORY even when locked (won't be auto-dropped)
            # Example: Allow holding raw diamonds in inventory
            allowed_inventory = [
                # "minecraft:diamond",
                # "minecraft:diamond_ore",
                # "minecraft:deepslate_diamond_ore"
            ]
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
