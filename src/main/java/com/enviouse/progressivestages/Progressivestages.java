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

        // Register config
        modContainer.registerConfig(ModConfig.Type.COMMON, StageConfig.SPEC);

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
        writeStageFile(triggersFile, getTriggersContent());
    }

    private void generateStoneAgeFile(Path configFolder) {
        String content = getStoneAgeContent();
        writeStageFile(configFolder.resolve("stone_age.toml"), content);
    }

    private void generateIronAgeFile(Path configFolder) {
        String content = getIronAgeContent();
        writeStageFile(configFolder.resolve("iron_age.toml"), content);
    }

    private void generateDiamondAgeFile(Path configFolder) {
        String content = getDiamondAgeContent();
        writeStageFile(configFolder.resolve("diamond_age.toml"), content);
    }

    private void writeStageFile(Path file, String content) {
        try {
            Files.writeString(file, content);
            LOGGER.info("[ProgressiveStages] Generated default stage file: {}", file.getFileName());
        } catch (IOException e) {
            LOGGER.error("[ProgressiveStages] Failed to write stage file: {} - {}", file.getFileName(), e.getMessage());
        }
    }

    // Stage file content generators (duplicated here for mod init; StageFileLoader has copies for reload)
    private String getStoneAgeContent() {
        return """
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
            # ITEM_MODS - Lock all items from a mod (items only, not blocks or entities)
            # Use this when you want to lock items but NOT blocks or entities from a mod.
            # Format: "modid" (just the namespace)
            # Example: item_mods = ["mekanism"]
            # -----------------------------------------------------------------------------
            item_mods = []
            
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
            # BLOCK_MODS - Lock all blocks from a mod (blocks only, not items or entities)
            # Use this when you want to lock blocks but NOT items or entities from a mod.
            # Format: "modid" (just the namespace)
            # Example: block_mods = ["mekanism"]
            # -----------------------------------------------------------------------------
            block_mods = []
            
            # -----------------------------------------------------------------------------
            # DIMENSIONS - Lock entire dimensions (prevent portal travel)
            # Format: "namespace:dimension_id"
            # Example: dimensions = ["minecraft:the_nether", "minecraft:the_end"]
            # -----------------------------------------------------------------------------
            dimensions = []
            
            # -----------------------------------------------------------------------------
            # FLUIDS (v1.4) - Lock specific fluids (hides from EMI/JEI)
            # Players cannot see or interact with these fluids in recipe viewers
            # Format: "namespace:fluid_id"
            # Example: fluids = ["mekanism:hydrogen", "mekanism:sulfuric_acid"]
            # -----------------------------------------------------------------------------
            fluids = []
            
            # -----------------------------------------------------------------------------
            # FLUID_TAGS (v1.4) - Lock all fluids in a tag
            # Example: fluid_tags = ["#c:acids"]
            # -----------------------------------------------------------------------------
            fluid_tags = []
            
            # -----------------------------------------------------------------------------
            # FLUID_MODS (v1.4) - Lock all fluids from a mod (EMI/JEI visibility)
            # Use this when you want to hide all fluids from a mod in recipe viewers.
            # Format: "modid" (just the namespace)
            # Example: fluid_mods = ["mekanism"]
            # -----------------------------------------------------------------------------
            fluid_mods = []
            
            # -----------------------------------------------------------------------------
            # MODS - Lock ENTIRE mods (all items, blocks, recipes, AND entities from that mod)
            # Format: "modid" (just the namespace, e.g., "mekanism", "create")
            # NOTE: This locks EVERYTHING from the mod - items, blocks, AND entities.
            # For finer control, use item_mods, block_mods, or entity_mods separately.
            # This is powerful - use carefully!
            # Example: mods = ["mekanism", "ae2", "create"]
            # -----------------------------------------------------------------------------
            mods = []
            
            # -----------------------------------------------------------------------------
            # NAMES - Lock items/blocks by name (case-insensitive substring matching)
            # Locks ANYTHING with this text in its registry ID
            # Example: names = ["iron"] locks "minecraft:iron_ingot", "create:iron_sheet", etc.
            # This is VERY broad - it will lock items from ALL mods containing this text!
            # Use carefully - prefer specific item IDs when possible.
            # -----------------------------------------------------------------------------
            names = []
            
            # -----------------------------------------------------------------------------
            # ENTITIES - Lock specific entity types (prevent attacking)
            # Players cannot attack these entities until stage is unlocked
            # Format: "namespace:entity_id"
            # Example: entities = ["minecraft:warden", "minecraft:elder_guardian"]
            # -----------------------------------------------------------------------------
            entities = []
            
            # -----------------------------------------------------------------------------
            # ENTITY TAGS - Lock all entity types in a tag
            # Example: entity_tags = ["#minecraft:raiders"]
            # -----------------------------------------------------------------------------
            entity_tags = []
            
            # -----------------------------------------------------------------------------
            # ENTITY_MODS - Lock all entities from a mod (attack only, not items)
            # Use this when you want to lock entity attacks separately from items.
            # NOTE: If you use mods = ["modid"], entities are automatically locked too.
            # Format: "modid" (just the namespace)
            # Example: entity_mods = ["mekanism"]
            # -----------------------------------------------------------------------------
            entity_mods = []
            
            # -----------------------------------------------------------------------------
            # UNLOCKED_ITEMS (v1.1) - Whitelist exceptions for items/blocks
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
            # UNLOCKED_BLOCKS (v1.4) - Whitelist exceptions for blocks
            # These blocks are ALWAYS placeable/interactable, even if they would be locked by:
            #   - mods = ["somemod"]
            #   - block_mods = ["somemod"]
            #   - block_tags = ["#sometag"]
            #
            # Use case: Lock entire mod but allow specific blocks from it
            # Example: unlocked_blocks = ["mekanism:teleporter"]
            # -----------------------------------------------------------------------------
            unlocked_blocks = []
            
            # -----------------------------------------------------------------------------
            # UNLOCKED_ENTITIES (v1.1) - Whitelist exceptions for entities
            # These entities can ALWAYS be attacked, even if they would be locked by:
            #   - mods = ["somemod"]
            #   - entity_mods = ["somemod"]
            #   - entity_tags = ["#sometag"]
            #
            # Use case: Lock entire mod entities but allow attacking specific ones
            # Example: unlocked_entities = ["mekanism:robit"]
            # -----------------------------------------------------------------------------
            unlocked_entities = []
            
            # -----------------------------------------------------------------------------
            # UNLOCKED_FLUIDS (v1.4) - Whitelist exceptions for fluids in EMI/JEI
            # These fluids are ALWAYS visible in EMI/JEI, even if their mod is locked.
            # Use case: Lock mods = ["mekanism"] but allow specific fluids to show
            # Example: unlocked_fluids = ["mekanism:hydrogen", "mekanism:oxygen"]
            # -----------------------------------------------------------------------------
            unlocked_fluids = []
            
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
    }

    private String getIronAgeContent() {
        return """
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
                "minecraft:rail"
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
            # ITEM_MODS - Lock all items from a mod (items only, not blocks or entities)
            # Use this when you want to lock items but NOT blocks or entities from a mod.
            # Format: "modid" (just the namespace)
            # Example: item_mods = ["create"]
            # -----------------------------------------------------------------------------
            item_mods = []
            
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
            # BLOCK_MODS - Lock all blocks from a mod (blocks only, not items or entities)
            # Use this when you want to lock blocks but NOT items or entities from a mod.
            # Format: "modid" (just the namespace)
            # Example: block_mods = ["create"]
            # -----------------------------------------------------------------------------
            block_mods = []
            
            # -----------------------------------------------------------------------------
            # DIMENSIONS - Lock entire dimensions (prevent portal travel)
            # Format: "namespace:dimension_id"
            # Iron age doesn't lock dimensions, but you could lock the Nether:
            # dimensions = ["minecraft:the_nether"]
            # -----------------------------------------------------------------------------
            dimensions = []
            
            # -----------------------------------------------------------------------------
            # FLUIDS (v1.4) - Lock specific fluids (hides from EMI/JEI)
            # Players cannot see or interact with these fluids in recipe viewers
            # Format: "namespace:fluid_id"
            # Example: fluids = ["mekanism:hydrogen", "mekanism:sulfuric_acid"]
            # -----------------------------------------------------------------------------
            fluids = []
            
            # -----------------------------------------------------------------------------
            # FLUID_TAGS (v1.4) - Lock all fluids in a tag
            # Example: fluid_tags = ["#c:acids"]
            # -----------------------------------------------------------------------------
            fluid_tags = []
            
            # -----------------------------------------------------------------------------
            # FLUID_MODS (v1.4) - Lock all fluids from a mod (EMI/JEI visibility)
            # Use this when you want to hide all fluids from a mod in recipe viewers.
            # Format: "modid" (just the namespace)
            # Example: fluid_mods = ["mekanism"]
            # -----------------------------------------------------------------------------
            fluid_mods = []
            
            # -----------------------------------------------------------------------------
            # MODS - Lock ENTIRE mods (all items, blocks, recipes, AND entities from that mod)
            # Format: "modid" (just the namespace, e.g., "mekanism", "create")
            # NOTE: This locks EVERYTHING - items, blocks, AND entities.
            # For finer control, use item_mods, block_mods, or entity_mods separately.
            # Example: Lock all of Create mod until iron age
            # mods = ["create"]
            # -----------------------------------------------------------------------------
            mods = []
            
            # -----------------------------------------------------------------------------
            # NAMES - Lock items/blocks by name (case-insensitive substring matching)
            # Locks ANYTHING with this text in its registry ID
            # Example: names = ["iron"] locks "minecraft:iron_ingot", "create:iron_sheet", etc.
            # This is VERY broad - it will lock items from ALL mods containing this text!
            # -----------------------------------------------------------------------------
            names = [
                # "iron"  # Uncomment to lock ALL items with "iron" in the name
            ]
            
            # -----------------------------------------------------------------------------
            # ENTITIES - Lock specific entity types (prevent attacking)
            # Players cannot attack these entities until stage is unlocked
            # Format: "namespace:entity_id"
            # Example: Lock iron golems until iron age
            # -----------------------------------------------------------------------------
            entities = [
                # "minecraft:iron_golem"
            ]
            
            # -----------------------------------------------------------------------------
            # ENTITY TAGS - Lock all entity types in a tag
            # Example: entity_tags = ["#minecraft:raiders"]
            # -----------------------------------------------------------------------------
            entity_tags = []
            
            # -----------------------------------------------------------------------------
            # ENTITY_MODS - Lock all entities from a mod (attack only, not items)
            # Use this when you want to lock entity attacks separately from items.
            # NOTE: If you use mods = ["modid"], entities are automatically locked too.
            # Format: "modid" (just the namespace)
            # Example: entity_mods = ["mekanism"]
            # -----------------------------------------------------------------------------
            entity_mods = []
            
            # -----------------------------------------------------------------------------
            # UNLOCKED_ITEMS (v1.1) - Whitelist exceptions for items/blocks
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
            # UNLOCKED_BLOCKS (v1.4) - Whitelist exceptions for blocks
            # These blocks are ALWAYS placeable/interactable, even if they would be locked by:
            #   - mods = ["somemod"]
            #   - block_mods = ["somemod"]
            #   - block_tags = ["#sometag"]
            #
            # Use case: Lock entire mod but allow specific blocks from it
            # Example: unlocked_blocks = ["create:andesite_casing"]
            # -----------------------------------------------------------------------------
            unlocked_blocks = []
            
            # -----------------------------------------------------------------------------
            # UNLOCKED_ENTITIES (v1.1) - Whitelist exceptions for entities
            # These entities can ALWAYS be attacked, even if they would be locked by:
            #   - mods = ["somemod"]
            #   - entity_mods = ["somemod"]
            #   - entity_tags = ["#sometag"]
            #
            # Use case: Lock entire mod entities but allow attacking specific ones
            # Example: unlocked_entities = ["mekanism:robit"]
            # -----------------------------------------------------------------------------
            unlocked_entities = []
            
            # -----------------------------------------------------------------------------
            # UNLOCKED_FLUIDS (v1.4) - Whitelist exceptions for fluids in EMI/JEI
            # These fluids are ALWAYS visible in EMI/JEI, even if their mod is locked.
            # Use case: Lock mods = ["mekanism"] but allow specific fluids to show
            # Example: unlocked_fluids = ["mekanism:hydrogen", "mekanism:oxygen"]
            # -----------------------------------------------------------------------------
            unlocked_fluids = []
            
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
    }

    private String getDiamondAgeContent() {
        return """
            # ============================================================================
            # Stage definition for Diamond Age (v1.4)
            # This file demonstrates ALL features available in ProgressiveStages v1.4
            # ============================================================================
            #
            # QUICK REFERENCE - Lock Types:
            # ┌──────────────────────┬───────┬────────┬──────────┬────────────────┐
            # │ Lock Type            │ Items │ Blocks │ Entities │ Fluids (EMI/JEI)│
            # ├──────────────────────┼───────┼────────┼──────────┼────────────────┤
            # │ mods = ["modid"]     │  ✅   │   ✅   │    ✅    │      ✅        │
            # │ item_mods = ["modid"]│  ✅   │   ❌   │    ❌    │      ❌        │
            # │ block_mods =["modid"]│  ❌   │   ✅   │    ❌    │      ❌        │
            # │ entity_mods=["modid"]│  ❌   │   ❌   │    ✅    │      ❌        │
            # │ fluid_mods =["modid"]│  ❌   │   ❌   │    ❌    │      ✅        │
            # │ names = ["pattern"]  │  ✅   │   ✅   │    ✅    │      ✅        │
            # └──────────────────────┴───────┴────────┴──────────┴────────────────┘
            #
            # IMPORTANT: Fluid locks ONLY affect EMI/JEI visibility. They do NOT
            # prevent players from piping, pumping, or using fluids in machines.
            # To block fluid transport, lock the machines/pipes themselves.
            #
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
            # It now iterates four registries at resolution time, not one 
            # it requires iterating all registries to resolve 
            # matches. In very large modpacks (10k+ items), 
            # this can add ~50-500ms during server lock resolution.   
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
            """;
    }

    private String getTriggersContent() {
        return """
            # ============================================================================
            # ProgressiveStages v1.1 - Trigger Configuration
            # ============================================================================
            # This file defines automatic stage grants based on player actions.
            # All triggers are EVENT-DRIVEN (no polling/tick scanning).
            #
            # Format: "resource_id" = "stage_id"
            # Stage IDs default to progressivestages namespace if not specified.
            #
            # IMPORTANT:
            # - Trigger persistence is stored per-world (dimension/boss triggers only fire once)
            # - Item triggers also scan inventory on player login
            # - Use /progressivestages trigger reset to clear trigger history
            # ============================================================================
            
            # ============================================================================
            # ADVANCEMENT TRIGGERS
            # When a player earns an advancement, they automatically receive the stage.
            # Format: "namespace:advancement_path" = "stage_id"
            # ============================================================================
            [advancements]
            # Examples:
            # "minecraft:story/mine_stone" = "stone_age"
            # "minecraft:story/smelt_iron" = "iron_age"
            # "minecraft:story/mine_diamond" = "diamond_age"
            # "minecraft:adventure/kill_a_mob" = "hunter_gatherer"
            # "minecraft:nether/return_to_sender" = "nether_warrior"
            # "minecraft:end/kill_dragon" = "dragon_slayer"
            
            # ============================================================================
            # ITEM PICKUP TRIGGERS
            # When a player picks up an item (or has it in inventory on login), they get the stage.
            # This includes items received from any source: chests, crafting, drops, etc.
            # Format: "namespace:item_id" = "stage_id"
            # ============================================================================
            [items]
            # Examples:
            # "minecraft:iron_ingot" = "iron_age"
            # "minecraft:diamond" = "diamond_age"
            # "minecraft:netherite_ingot" = "netherite_age"
            # "minecraft:ender_pearl" = "end_explorer"
            # "minecraft:blaze_rod" = "nether_explorer"
            
            # ============================================================================
            # DIMENSION ENTRY TRIGGERS
            # When a player FIRST enters a dimension, they get the stage (one-time only).
            # Persisted per-world - use /progressivestages trigger reset to clear.
            # Format: "namespace:dimension_id" = "stage_id"
            # ============================================================================
            [dimensions]
            # Examples:
            # "minecraft:the_nether" = "nether_explorer"
            # "minecraft:the_end" = "end_explorer"
            # "aether:the_aether" = "aether_explorer"
            # "twilightforest:twilight_forest" = "twilight_explorer"
            
            # ============================================================================
            # BOSS/ENTITY KILL TRIGGERS
            # When a player kills a specific entity, they get the stage (one-time only).
            # Persisted per-world - use /progressivestages trigger reset to clear.
            # Format: "namespace:entity_id" = "stage_id"
            # ============================================================================
            [bosses]
            # Examples:
            # "minecraft:ender_dragon" = "dragon_slayer"
            # "minecraft:wither" = "wither_slayer"
            # "minecraft:warden" = "warden_slayer"
            # "minecraft:elder_guardian" = "ocean_conqueror"
            # "alexscaves:tremorzilla" = "tremorzilla_slayer"
            # "irons_spellbooks:dead_king" = "dead_king_slayer"
            """;
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
