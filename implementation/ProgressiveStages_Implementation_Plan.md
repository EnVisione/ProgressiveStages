# ProgressiveStages - NeoForge 1.21.1 Implementation Plan

## **Mod Identity**
- **Mod ID**: `progressivestages`
- **Display Name**: "ProgressiveStages"
- **Version**: 1.0.0
- **MC Version**: 1.21.1
- **NeoForge**: 21.1.219+
- **Purpose**: Team-scoped linear stage progression system with integrated item/recipe/dimension/mod locking and EMI visual feedback

---

## **1. Project Structure**

```
src/main/java/com/progressivestages/
├── ProgressiveStages.java                     // @Mod entry point
│
├── client/                                    // CLIENT-SIDE ONLY CODE
│   ├── ClientStageCache.java                  // Synced stage data from server
│   ├── emi/
│   │   ├── ProgressiveStagesEMIPlugin.java    // EMI API entrypoint
│   │   ├── LockOverlayRenderer.java           // Draw lock icons on slots
│   │   ├── RecipeTreeHandler.java             // Handle recipe tree locking behavior
│   │   └── TooltipHandler.java                // Add stage info to item tooltips
│   ├── renderer/
│   │   ├── LockIconRenderer.java              // Render lock icon texture
│   │   └── RenderHelper.java                  // Shared rendering utilities
│   └── sound/
│       └── SoundHandler.java                  // Play noteblock ping on lock violation
│
├── common/                                    // SHARED CODE (both sides)
│   ├── api/
│   │   ├── IProgressiveStagesAPI.java         // Public API for other mods
│   │   ├── StageId.java                       // ResourceLocation wrapper
│   │   └── events/
│   │       ├── StageGrantedEvent.java         // Fired when team gains stage (server)
│   │       ├── StageRevokedEvent.java         // Fired when team loses stage (server)
│   │       └── TeamStageChangedEvent.java     // Fired when team stage state changes
│   ├── config/
│   │   ├── StageConfig.java                   // Main config (TOML)
│   │   ├── StageDefinition.java               // Parsed stage file data class
│   │   ├── LockDefinition.java                // Parsed lock data
│   │   └── ConfigManager.java                 // Load/validate/reload configs
│   ├── data/
│   │   ├── StageAttachment.java               // Data attachment for team stages
│   │   ├── StageCodec.java                    // Codec for serialization
│   │   └── TeamStageData.java                 // Per-team stage storage
│   ├── lock/
│   │   ├── LockRegistry.java                  // Central registry: item/recipe/mod → stage
│   │   ├── LockType.java                      // Enum: ITEM, RECIPE, TAG, MOD, DIMENSION, NAME, INTERACTION
│   │   ├── LockEntry.java                     // Data class for a single lock
│   │   └── NameMatcher.java                   // Case-insensitive name matching
│   ├── network/
│   │   ├── NetworkHandler.java                // Register packets
│   │   ├── StageSyncPacket.java               // S→C: Full team stage snapshot
│   │   ├── StageUpdatePacket.java             // S→C: Delta (grant/revoke single stage)
│   │   └── LockRegistrySyncPacket.java        // S→C: Sync lock definitions for client
│   ├── stage/
│   │   ├── StageManager.java                  // Core stage logic (query/grant/revoke)
│   │   ├── StageOrder.java                    // Linear stage ordering system
│   │   └── StageValidator.java                // Validate stage grant/revoke operations
│   ├── team/
│   │   ├── TeamProvider.java                  // Abstract team interface
│   │   ├── FTBTeamsProvider.java              // FTB Teams integration
│   │   └── TeamStageSync.java                 // Sync logic for team stage changes
│   └── util/
│       ├── TextUtil.java                      // Parse color codes (&c, &l, etc.)
│       ├── StageUtil.java                     // Helper methods
│       └── Constants.java                     // Mod constants (ID, version, etc.)
│
├── server/                                    // SERVER-SIDE ONLY CODE
│   ├── commands/
│   │   ├── StageCommand.java                  // /stage grant/revoke/list/check
│   │   ├── ProgressiveStagesCommand.java      // /progressivestages reload/validate
│   │   └── CommandHelper.java                 // Shared command utilities
│   ├── enforcement/
│   │   ├── ItemEnforcer.java                  // Block item use/pickup/hold
│   │   ├── RecipeEnforcer.java                // Hide/block recipes
│   │   ├── BlockEnforcer.java                 // Block placement/interaction
│   │   ├── DimensionEnforcer.java             // Block dimension travel
│   │   ├── ModEnforcer.java                   // Block entire mods
│   │   ├── InteractionEnforcer.java           // Block Create-style interactions
│   │   └── InventoryScanner.java              // Scan inventory, drop locked items
│   ├── integration/
│   │   ├── FTBTeamsIntegration.java           // FTB Teams hooks
│   │   └── FTBQuestsIntegration.java          // FTB Quests command integration
│   └── loader/
│       ├── StageFileLoader.java               // Load stage files from ProgressiveStages/
│       ├── StageFileParser.java               // Parse TOML stage files
│       └── ValidationEngine.java              // Validate loaded stage definitions
│
└── mixins/                                    // MIXIN CLASSES
    ├── client/
    │   ├── EMISlotWidgetMixin.java            // Inject lock rendering into EMI slots
    │   ├── EMIRecipeScreenMixin.java          // Handle recipe screen rendering
    │   └── InventoryScreenMixin.java          // Handle tooltip rendering
    └── common/
        ├── CraftingMenuMixin.java             // Block crafting output for locked recipes
        └── ServerPlayerMixin.java             // Hook inventory changes

src/main/resources/
├── META-INF/
│   └── mods.toml                              // Mod metadata (Gradle-filtered)
├── pack.mcmeta                                // Resource pack format
├── progressivestages.mixins.json              // Mixin configuration
├── assets/progressivestages/
│   ├── textures/gui/
│   │   └── lock_icon.png                      // Lock overlay (8×8, 16×16 variants)
│   └── lang/
│       └── en_us.json                         // Translations
└── data/progressivestages/
    └── stages/
        └── .gitkeep                           // Empty (stages go in minecraft/ProgressiveStages/)
```

---

## **2. Stage File Format (TOML)**

**Location**: `minecraft/ProgressiveStages/<stage_id>.toml`

**Example**: `minecraft/ProgressiveStages/diamond_age.toml`

```toml
# Stage definition for Diamond Age
# This stage unlocks diamond tools, armor, and related items

[stage]
# Unique identifier (must match filename without .toml)
id = "diamond_age"

# Display name (shown in tooltips, messages, etc.)
display_name = "Diamond Age"

# Description (optional, for quest integration or future GUI)
description = "Unlock diamond tools, armor, and blocks"

# Order in progression (lower = earlier, must be unique across all stages)
# Example: 1 = Stone Age, 2 = Iron Age, 3 = Diamond Age
order = 3

# Icon item for visual representation (optional, for future GUI/quest integration)
icon = "minecraft:diamond_pickaxe"

# Message sent to ALL team members when this stage is unlocked (optional)
# Supports Minecraft color codes: &c (red), &a (green), &l (bold), &o (italic), etc.
# Leave empty or omit for no message
unlock_message = "&b&lDiamond Age Unlocked! &r&7You can now use diamond items."

# ============================================================================
# LOCKS - Define what is locked by this stage
# ============================================================================

[locks]

# -----------------------------------------------------------------------------
# ITEMS - Lock specific items by ID
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
    "minecraft:diamond_boots"
]

# -----------------------------------------------------------------------------
# ITEM TAGS - Lock all items in a tag
# Format: "#namespace:path"
# -----------------------------------------------------------------------------
item_tags = [
    "#forge:gems/diamond",
    "#forge:storage_blocks/diamond",
    "#forge:ores/diamond"
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
    "minecraft:diamond_block"
]

# -----------------------------------------------------------------------------
# RECIPE TAGS - Lock all recipes in a tag
# Format: "#namespace:path"
# -----------------------------------------------------------------------------
recipe_tags = [
    "#forge:recipes/diamond_tools",
    "#forge:recipes/diamond_armor"
]

# -----------------------------------------------------------------------------
# BLOCKS - Lock block placement and interaction
# Players cannot place these blocks or interact with them (right-click)
# -----------------------------------------------------------------------------
blocks = [
    "minecraft:diamond_block",
    "minecraft:enchanting_table"  # Example: lock enchanting until diamond age
]

# -----------------------------------------------------------------------------
# BLOCK TAGS - Lock all blocks in a tag
# Format: "#namespace:path"
# -----------------------------------------------------------------------------
block_tags = [
    "#forge:storage_blocks/diamond"
]

# -----------------------------------------------------------------------------
# DIMENSIONS - Lock entire dimensions (prevent portal travel)
# Format: "namespace:dimension_id"
# Example: Lock Nether until a certain stage
# -----------------------------------------------------------------------------
dimensions = [
    # "minecraft:the_nether",  # Uncomment to lock Nether
    # "minecraft:the_end"       # Uncomment to lock End
]

# -----------------------------------------------------------------------------
# MODS - Lock ENTIRE mods (all items, blocks, recipes from that mod)
# Format: "modid" (just the namespace, e.g., "mekanism", "create")
# This is the nuclear option - use sparingly!
# -----------------------------------------------------------------------------
mods = [
    # "mekanism",  # Uncomment to lock all Mekanism content
    # "ae2"        # Uncomment to lock all Applied Energistics 2 content
]

# -----------------------------------------------------------------------------
# NAMES - Lock items/blocks by name (case-insensitive substring matching)
# Locks anything with this text in its ID or display name
# Example: "diamond" locks "minecraft:diamond", "botania:diamond_pickaxe", etc.
# Use carefully - very broad matching!
# -----------------------------------------------------------------------------
names = [
    # "diamond",   # Locks EVERYTHING with "diamond" in the ID/name
    # "netherite"  # Locks EVERYTHING with "netherite" in the ID/name
]

# -----------------------------------------------------------------------------
# INTERACTIONS - Lock specific player-world interactions (Create mod, etc.)
# Useful for Create's "apply item to block" mechanics
# -----------------------------------------------------------------------------

# Example 1: Lock Create's Andesite Casing creation
# (Right-clicking Andesite Alloy on a Stripped Log)
[[locks.interactions]]
type = "item_on_block"              # Type of interaction
held_item = "create:andesite_alloy" # Item player must be holding
target_block = "#minecraft:logs"    # Block being clicked (supports tags with #)
description = "Create Andesite Casing"  # Optional description for debugging

# Example 2: Lock using a specific item on any block
[[locks.interactions]]
type = "item_on_block"
held_item = "minecraft:flint_and_steel"
target_block = "minecraft:tnt"
description = "Ignite TNT"

# Example 3: Lock right-clicking a specific block (no item required)
[[locks.interactions]]
type = "block_right_click"
target_block = "minecraft:enchanting_table"
description = "Use Enchanting Table"

# Example 4: Lock using any item from a tag on a block
[[locks.interactions]]
type = "item_on_block"
held_item = "#forge:gems/diamond"  # Supports item tags
target_block = "minecraft:smithing_table"
description = "Diamond Smithing"
```

---

## **3. Main Config (TOML)**

**Location**: `config/progressivestages-common.toml`

Generated on first run with extensive comments:

```toml
# ============================================================================
# ProgressiveStages - Main Configuration
# ============================================================================
# This config controls the core behavior of ProgressiveStages.
# Stage definitions are loaded from: minecraft/ProgressiveStages/*.toml

[general]

# Starting stage for new players
# Set to a stage ID (e.g., "stone_age") to auto-grant on first join
# Set to "" (empty string) for no starting stage (admin must manually grant)
starting_stage = "stone_age"

# Team mode: "ftb_teams" (requires FTB Teams mod) or "solo" (each player is their own team)
# If "ftb_teams", stages are shared across team members
# If "solo", each player has independent progression
team_mode = "ftb_teams"

# Require FTB Teams to be installed if team_mode = "ftb_teams"
# If true and FTB Teams is missing, mod will error on startup
# If false and FTB Teams is missing, will fall back to solo mode
require_ftb_teams = true

# Debug mode: Log verbose stage checks, lock queries, and team operations
# Useful for pack development, disable for production
debug_logging = false

# ============================================================================
# ENFORCEMENT SETTINGS
# ============================================================================

[enforcement]

# Block item use (right-click with item in hand)
block_item_use = true

# Block item pickup (prevent picking up locked items from ground)
block_item_pickup = true

# Block item holding in inventory (auto-drop locked items)
# If true, scans inventory periodically and drops locked items
block_item_inventory = true

# Frequency to scan inventory for locked items (in ticks, 20 ticks = 1 second)
# Lower = more responsive but higher server load
# Recommended: 20 (1 second) for most servers
# Set to 0 to disable periodic scanning (only check on inventory change events)
inventory_scan_frequency = 20

# Block crafting locked recipes (prevent result extraction)
block_crafting = true

# Hide locked recipes from crafting table output
# If true, crafting grid won't show output for locked recipes
# If false, output shows but extraction is blocked
hide_locked_recipe_output = true

# Block block placement (prevent placing locked blocks)
block_block_placement = true

# Block block interaction (prevent right-clicking locked blocks)
block_block_interaction = true

# Block dimension travel to locked dimensions
block_dimension_travel = true

# Block items from locked mods (entire mod namespace)
block_locked_mods = true

# Block interactions (Create-style item-on-block interactions)
block_interactions = true

# Show chat message when player is blocked by a lock
show_lock_message = true

# Play sound when player is blocked by a lock
play_lock_sound = true

# Sound to play (resource location)
# Default: noteblock ping sound
lock_sound = "minecraft:block.note_block.pling"

# Sound volume (0.0 to 1.0)
lock_sound_volume = 1.0

# Sound pitch (0.5 to 2.0, 1.0 = normal pitch)
lock_sound_pitch = 1.0

# ============================================================================
# TEAM SETTINGS (only applies if team_mode = "ftb_teams")
# ============================================================================

[team]

# Enforce stage matching when joining teams
# If true, players can only join teams if they meet stage requirements (see below)
# If false, anyone can join any team (stages sync immediately on join)
enforce_stage_matching = true

# Stage matching rule for joining teams:
# "exact" - Must have exactly the same stages as team
# "minimum" - Must have at least the team's highest stage
# "none" - No restrictions (anyone can join, stages sync on join)
stage_match_rule = "exact"

# Sync stages when all team members reach the same stage
# If true, when a higher-stage player joins and all members eventually reach
# the same stage, they progress together from that point
# (Implements your requested "catch-up" logic)
sync_on_stage_alignment = true

# Kick members from team if they fall behind in stages
# If true, if a member is manually revoked a stage and no longer matches team,
# they are auto-kicked
# If false, they stay in team but stages desync (not recommended)
kick_on_stage_mismatch = false

# Stages persist when leaving team
# If true, players keep their stages when leaving/being kicked
# If false, stages are reset (very punishing, not recommended)
persist_stages_on_leave = true

# ============================================================================
# EMI INTEGRATION
# ============================================================================

[emi]

# Enable EMI integration (requires EMI mod)
enabled = true

# Show lock icon overlay on locked items/recipes in EMI
show_lock_icon = true

# Lock icon position in slot
# Options: "top_left", "top_right", "bottom_left", "bottom_right", "center"
lock_icon_position = "top_left"

# Lock icon size (pixels, must be power of 2: 8, 16, 32)
lock_icon_size = 8

# Lock icon padding from slot edge (pixels)
lock_icon_padding = 1

# Show semi-transparent highlight on locked recipe outputs
show_highlight = true

# Highlight color (ARGB hex format: 0xAARRGGBB)
# AA = alpha (transparency), RR = red, GG = green, BB = blue
# Default: 0x50FFAA40 = 50% transparent light orange
highlight_color = 0x50FFAA40

# Show lock info in item tooltips
show_tooltip = true

# Tooltip format (supports multiple lines)
# Available placeholders:
#   {item_name} - Item display name
#   {item_id} - Item registry ID
#   {mod_id} - Mod that adds the item
#   {required_stage} - Stage required to unlock
#   {current_stage} - Player's current stage
#   {progress} - Progress (e.g., "2/5")
# Color codes: &c=red, &a=green, &b=aqua, &e=yellow, &7=gray, &l=bold, &o=italic
tooltip_format = [
    "&7{mod_id}",
    "&c🔒 Locked",
    "&7Stage required: &f{required_stage}",
    "&7Current stage: &f{current_stage} ({progress})"
]

# Show locked recipes in EMI (with lock overlays)
# If true, locked recipes are visible but marked as locked
# If false, locked recipes are hidden entirely from EMI
show_locked_recipes = true

# Recipe tree behavior for locked recipes
# "show" - Show locked recipes normally with lock icon
# "gray_out" - Gray out locked recipe branches
# "hide" - Hide locked recipes from tree
recipe_tree_mode = "show"

# ============================================================================
# PERFORMANCE TUNING
# ============================================================================

[performance]

# Cache lock queries (item → stage lookups)
# Rebuilds cache when stages change or lock registry reloads
# Highly recommended for performance
enable_lock_cache = true

# Cache size per player (number of item → locked/unlocked results to cache)
# Higher = more memory, faster lookups
# Lower = less memory, more CPU for repeated checks
lock_cache_size = 1024

# Network sync strategy
# "full" - Always send full stage snapshot
# "delta" - Send deltas (grant/revoke) after initial sync
# Delta is more efficient but slightly more complex
network_sync_strategy = "delta"

# Batch stage updates (useful for quest rewards that grant multiple stages)
# If true, multiple stage grants in the same tick are batched into one packet
batch_stage_updates = true
```

---

## **4. Example Stage Files (Auto-Generated)**

On first run, generate these in `minecraft/ProgressiveStages/`:

### **stone_age.toml**
```toml
[stage]
id = "stone_age"
display_name = "Stone Age"
description = "Basic survival tools and resources"
order = 1
icon = "minecraft:stone_pickaxe"
unlock_message = "&7&lStone Age Unlocked! &r&8Begin your journey."

[locks]
# Stone Age is the starting stage - nothing locked here
# (Or lock wood tools if you want even earlier progression)
items = []
item_tags = []
recipes = []
recipe_tags = []
blocks = []
block_tags = []
dimensions = []
mods = []
names = []
```

### **iron_age.toml**
```toml
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

item_tags = [
    "#forge:ingots/iron",
    "#forge:storage_blocks/iron",
    "#forge:ores/iron",
    "#forge:raw_materials/iron"
]

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

block_tags = [
    "#forge:storage_blocks/iron"
]

dimensions = []
mods = []
names = []
```

### **diamond_age.toml**
(Use the full example from section 2)

---

## **5. Implementation Phases**

### **Phase 1: Core Framework** (Foundation - 3-5 days)

**Goal**: Stage system works, no enforcement yet

**Deliverables**:
1. **Project setup**
   - Gradle build with NeoForge 21.1.219
   - Dependencies: EMI, FTB Teams, FTB Quests
   - Mixin setup
   - 4-directory structure (client/, common/, server/, mixins/)

2. **Stage definition system**
   - `StageDefinition` data class
   - `StageFileLoader` (loads TOML from `minecraft/ProgressiveStages/`)
   - `StageFileParser` (parse TOML using Forge ConfigSpec or TOML4J)
   - `StageOrder` (linear ordering system based on "order" field)
   - `ValidationEngine` (validate stage files: unique IDs, unique orders, valid item/block IDs)

3. **Stage storage**
   - `StageAttachment` (Data Attachment on ServerLevel or custom holder)
   - `TeamStageData` (per-team stage set)
   - `StageCodec` (serialize/deserialize for persistence and sync)

4. **Team integration**
   - `TeamProvider` interface
   - `FTBTeamsProvider` implementation (query FTB Teams API)
   - Solo mode fallback (each player is own team)

5. **Stage manager**
   - `StageManager.hasStage(UUID player, StageId)`
   - `StageManager.grantStage(UUID player, StageId)` - grants stage + all previous stages
   - `StageManager.revokeStage(UUID player, StageId)`
   - `StageManager.getStages(UUID player)` → Set<StageId>
   - Fire events: `StageGrantedEvent`, `StageRevokedEvent`

6. **Commands**
   - `/stage grant <player> <stage>` - grants stage + all prerequisites
   - `/stage revoke <player> <stage>`
   - `/stage list [player]`
   - `/stage check <player> <stage>`

7. **Network sync**
   - `NetworkHandler` (register packets)
   - `StageSyncPacket` (S→C: full snapshot on join)
   - `StageUpdatePacket` (S→C: delta updates)
   - `ClientStageCache` (client-side mirror of server stage data)

8. **Config**
   - `StageConfig` (Forge ConfigSpec TOML)
   - Generate default config with extensive comments

9. **File generation**
   - On first run, create `minecraft/ProgressiveStages/` directory
   - Generate `stone_age.toml`, `iron_age.toml`, `diamond_age.toml`

**Testing**:
- Start server, verify `minecraft/ProgressiveStages/` created with example files
- Join as player, verify auto-granted starting stage (stone_age)
- `/stage grant @s iron_age` → verify both stone_age and iron_age granted (prerequisite logic)
- `/stage list` → verify stages shown
- Restart server → verify stages persist
- Second player joins → verify they get starting stage independently

---

### **Phase 2: Lock Registry** (Lock Definition System - 2-3 days)

**Goal**: Parse and store all lock definitions, no enforcement yet

**Deliverables**:
1. **Lock data structures**
   - `LockEntry` (data class: type, target, stage)
   - `LockType` enum: ITEM, RECIPE, TAG, MOD, DIMENSION, NAME, INTERACTION
   - `LockDefinition` (parsed from stage file [locks] section)

2. **Lock registry**
   - `LockRegistry.register(LockEntry)`
   - `LockRegistry.getRequiredStage(Item)` → Optional<StageId>
   - `LockRegistry.getRequiredStage(ResourceLocation recipeId)` → Optional<StageId>
   - `LockRegistry.getRequiredStage(TagKey<Item>)` → Optional<StageId>
   - `LockRegistry.getRequiredStage(String modId)` → Optional<StageId>
   - `LockRegistry.getRequiredStage(ResourceKey<Level> dimension)` → Optional<StageId>
   - `LockRegistry.getRequiredStageByName(String itemId)` → Optional<StageId> (case-insensitive)
   - `LockRegistry.getRequiredStage(InteractionType, Item, Block)` → Optional<StageId>

3. **Lock loading**
   - Parse [locks] section from each stage TOML
   - Populate `LockRegistry` on server start
   - Validate all item/block/recipe IDs exist (log warnings for invalid IDs)

4. **Name matcher**
   - `NameMatcher.matches(String name, String pattern)` - case-insensitive substring match
   - Build lookup table for "names" locks

5. **Lock registry sync**
   - `LockRegistrySyncPacket` (S→C: send lock summary to client for EMI)
   - Client stores locks in `ClientLockCache`

**Testing**:
- Add lock in `diamond_age.toml`: `items = ["minecraft:diamond"]`
- Start server, query `LockRegistry.getRequiredStage(Items.DIAMOND)` → verify returns "diamond_age"
- Add invalid item "minecraft:fake_item" → verify warning logged
- Add "names = ["diamond"]" → verify matches "minecraft:diamond", "minecraft:diamond_pickaxe", etc.

---

### **Phase 3: Item Enforcement** (Server-Side Blocking - 3-4 days)

**Goal**: Block item use, pickup, and inventory holding

**Deliverables**:
1. **Item enforcer**
   - `ItemEnforcer.checkItemUse(Player, ItemStack)` → boolean allowed
   - Hook `PlayerInteractEvent.RightClickItem` (server)
   - If locked: cancel event, send message, play sound

2. **Pickup blocker**
   - Hook `EntityItemPickupEvent` (server)
   - If locked: cancel event, send message

3. **Inventory scanner**
   - `InventoryScanner.scanAndDrop(ServerPlayer)`
   - Hook `InventoryTickEvent` or `PlayerTickEvent` (server-side only)
   - Check frequency: configurable (default 20 ticks = 1 second)
   - Scan entire inventory: main inventory, armor, offhand, cursor slot
   - Drop locked items at player feet
   - Send message: "&c🔒 You have not unlocked this item yet!"
   - Play sound: noteblock pling

4. **Chest interaction blocker**
   - Hook `PlayerInteractEvent.RightClickBlock` (server)
   - When clicking item in container GUI (chest, furnace, etc.)
   - If item is locked: cancel pickup, place item back in container
   - Send message + sound

5. **Message system**
   - `TextUtil.parseColorCodes(String)` - convert &c → ChatFormatting.RED
   - `MessageSender.sendLockMessage(Player, StageId requiredStage)`
   - Format: "&c🔒 You have not unlocked this item yet! Required stage: {stage}"

6. **Sound system**
   - `SoundHandler.playLockSound(ServerPlayer)`
   - Play configured sound (default: noteblock pling)

**Testing**:
- Lock diamond in diamond_age, player in iron_age
- Pick up diamond from ground → verify blocked, message shown, sound played
- Give diamond via `/give` → verify auto-dropped from inventory within 1 second
- Put diamond in hotbar → verify auto-dropped
- Open chest with diamond, try to take → verify can't pick up
- Grant diamond_age stage → verify can now pick up and hold diamond

---

### **Phase 4: Recipe Enforcement** (Crafting Blocking - 2-3 days)

**Goal**: Block crafting and hide recipe outputs

**Deliverables**:
1. **Recipe enforcer**
   - `RecipeEnforcer.checkRecipe(Player, Recipe)` → boolean allowed
   - Hook crafting result extraction (multiple points):
     - `PlayerEvent.ItemCraftedEvent` (server)
     - Mixin into `CraftingMenu.quickMoveStack()` (server)
     - Mixin into result slot click handler (server)
   - If locked: cancel craft, return ingredients, send message

2. **Recipe output hiding**
   - Mixin into crafting table result calculation (client + server)
   - If recipe is locked: don't show output item (empty slot)
   - Config: `hide_locked_recipe_output = true`

3. **Recipe hiding from book**
   - Hook `RecipesUpdatedEvent` (client)
   - Filter out locked recipes before syncing to client
   - If `show_locked_recipes = false`, remove from list
   - If `show_locked_recipes = true`, keep for EMI overlay

**Testing**:
- Lock diamond_pickaxe recipe
- Open crafting table, place 2 sticks + 3 diamonds in pattern
- Verify output slot is empty (no pickaxe shown)
- Use external mod to force-craft → verify server blocks extraction, ingredients returned
- Grant stage → verify recipe now shows output and can craft

---

### **Phase 5: EMI Integration** (Visual Feedback - 4-5 days)

**Goal**: Lock overlays in EMI, tooltips

**Deliverables**:
1. **EMI plugin**
   - `ProgressiveStagesEMIPlugin` implements `EmiEntrypoint`
   - Register with EMI API
   - Provide lock query method to EMI

2. **Lock icon renderer**
   - `LockIconRenderer.render(PoseStack, int x, int y, int size)`
   - Load texture from `assets/progressivestages/textures/gui/lock_icon.png`
   - Render at configurable position (default: top-left)
   - Configurable size (default: 8×8)

3. **Lock overlay on slots**
   - Mixin: `EMISlotWidgetMixin`
   - Inject into slot rendering (after item render, before tooltip)
   - Check if item/recipe is locked for local player (query ClientStageCache + ClientLockCache)
   - If locked: draw lock icon + highlight

4. **Recipe tree handler**
   - `RecipeTreeHandler`
   - Config modes: "show", "gray_out", "hide"
   - If "gray_out": render locked recipes with reduced alpha
   - If "hide": filter locked recipes from tree

5. **Tooltip handler**
   - Listen to `ItemTooltipEvent` (client)
   - If item is locked: add tooltip lines per config format
   - Parse color codes (&c, &l, etc.)
   - Show: mod ID, 🔒 Locked, required stage, current stage, progress

6. **Highlight renderer**
   - `RenderHelper.drawHighlight(PoseStack, int x, int y, int color)`
   - Draw semi-transparent overlay (default: 0x50FFAA40)
   - Configurable color in config

**Testing**:
- Lock diamond in diamond_age, player in iron_age
- Open EMI, search for diamond
- Verify lock icon shows on diamond in item list
- Click diamond to view recipes → verify lock icon on all recipes using diamond
- Hover diamond → verify tooltip shows "🔒 Locked", "Stage required: Diamond Age", "Current stage: Iron Age (2/3)"
- Grant diamond_age → verify lock icon disappears, tooltip normal

---

### **Phase 6: Block & Dimension Enforcement** (World Interaction - 2-3 days)

**Goal**: Block placement, interaction, dimension travel

**Deliverables**:
1. **Block enforcer**
   - `BlockEnforcer.checkBlockPlace(Player, Block)` → boolean allowed
   - Hook `BlockEvent.EntityPlaceEvent` (server)
   - If locked: cancel, send message

2. **Block interaction enforcer**
   - Hook `PlayerInteractEvent.RightClickBlock` (server)
   - If target block is locked: cancel, send message
   - Example: lock enchanting table, prevent opening GUI

3. **Dimension enforcer**
   - `DimensionEnforcer.checkDimensionTravel(Player, ResourceKey<Level>)` → boolean allowed
   - Hook `EntityTravelToDimensionEvent` (server)
   - If dimension locked: cancel, send message
   - Example: lock Nether until bronze_age

**Testing**:
- Lock `minecraft:diamond_block` placement
- Try to place diamond block → verify blocked, message shown
- Lock `minecraft:enchanting_table` interaction
- Try to right-click enchanting table → verify blocked
- Lock `minecraft:the_nether`
- Try to enter nether portal → verify blocked, message shown

---

### **Phase 7: Mod & Name Locking** (Advanced Locks - 2 days)

**Goal**: Lock entire mods and name-based matching

**Deliverables**:
1. **Mod enforcer**
   - `ModEnforcer.checkMod(Item)` → Optional<StageId>
   - Extract mod ID from item registry name
   - Check if mod is locked in any stage
   - Apply to all enforcement points (items, recipes, blocks)

2. **Name matcher integration**
   - `NameMatcher` (already built in Phase 2)
   - Apply to all enforcement points
   - Example: "names = ["diamond"]" locks "minecraft:diamond", "botania:diamond_pickaxe", "create:diamond_gear", etc.

**Testing**:
- Add `mods = ["botania"]` to a stage
- Verify all Botania items are locked
- Add `names = ["diamond"]` to diamond_age
- Verify minecraft:diamond, modded diamonds, diamond tools from any mod are all locked

---

### **Phase 8: Interaction Locking** (Create/Mod Interactions - 2-3 days)

**Goal**: Block Create-style item-on-block interactions

**Deliverables**:
1. **Interaction data structure**
   - `InteractionDefinition` parsed from stage file [[locks.interactions]]
   - Types: "item_on_block", "block_right_click"

2. **Interaction enforcer**
   - `InteractionEnforcer.checkInteraction(Player, Item held, Block target)` → Optional<StageId>
   - Hook `PlayerInteractEvent.RightClickBlock` (server)
   - Check if interaction is locked
   - If locked: cancel, send message

3. **Tag support for interactions**
   - Support item tags and block tags in interaction definitions
   - Example: `held_item = "#forge:gems/diamond"`, `target_block = "#minecraft:logs"`

**Testing**:
- Add interaction lock: Create Andesite Casing (andesite_alloy on log)
- Hold andesite alloy, right-click log → verify blocked
- Add interaction: flint_and_steel on TNT → verify blocked

---

### **Phase 9: Team Logic** (FTB Teams Integration - 3-4 days)

**Goal**: Team stage syncing, join restrictions, catch-up logic

**Deliverables**:
1. **Team stage sync**
   - `TeamStageSync.syncTeam(TeamId)`
   - When stage granted to one member: grant to all members
   - Send sync packet to all online members

2. **Team join restrictions**
   - `TeamProvider.canJoinTeam(Player, TeamId)` → boolean
   - Check stage matching rule (exact, minimum, none)
   - If player has more stages: allow join but don't sync up
   - If player has fewer stages: deny join with message

3. **Stage alignment detection**
   - `TeamStageSync.checkAlignment(TeamId)`
   - Periodically check if all members now have same stages
   - If aligned: enable "progress together" mode
   - From that point: stages sync across all members

4. **Team leave handling**
   - Listen to FTB Teams events: `PlayerLeftTeamEvent`
   - If `persist_stages_on_leave = true`: keep player stages
   - If false: reset stages (configurable)

5. **Stage mismatch handling**
   - If member manually revoked a stage and no longer matches team
   - If `kick_on_stage_mismatch = true`: kick from team with message
   - If false: allow desync (log warning)

**Testing (requires 2+ players)**:
- Player A (iron_age) and Player B (iron_age) form team
- Grant diamond_age to Player A → verify Player B also gets diamond_age
- Player C (stone_age) tries to join → verify blocked ("stage mismatch")
- Player D (diamond_age) joins team → verify allowed
- Team is now: A (diamond), B (diamond), D (diamond) but D joined late
- Grant netherite_age to any member → verify all get netherite_age (now aligned)
- Player B leaves team → verify B keeps stages (if persist_stages_on_leave = true)

---

### **Phase 10: Polish & Commands** (Final Features - 2-3 days)

**Goal**: Advanced commands, reload, validation

**Deliverables**:
1. **Advanced commands**
   - `/progressivestages reload` - reload all stage files from disk
   - `/progressivestages validate` - validate all stage files, report errors
   - `/progressivestages info <stage>` - show stage details (locks, order, etc.)
   - `/progressivestages debug <player>` - show player's stage state, team, cache

2. **Unlock messages**
   - When stage granted, send `unlock_message` from stage file
   - Parse color codes (&c, &l, etc.)
   - Send to all team members

3. **Localization**
   - `en_us.json` with all translatable strings
   - Command feedback messages
   - Lock messages
   - Tooltip format

4. **Error handling**
   - Graceful handling of missing FTB Teams (fallback to solo mode)
   - Graceful handling of invalid stage files (skip, log error)
   - Validation: detect circular dependencies, duplicate orders, invalid IDs

5. **Documentation comments**
   - Every config option explained in TOML comments
   - Example stage files with detailed comments (done in earlier phases)

**Testing**:
- Add invalid stage file (bad JSON/TOML) → verify skipped, error logged
- `/progressivestages validate` → verify reports errors
- Add stage with `unlock_message = "&a&lTest!"` → grant stage → verify message shown with color
- `/progressivestages reload` → modify stage file → reload → verify changes applied
- Remove FTB Teams mod → restart → verify fallback to solo mode (no crash)

---

## **6. Dependencies & Build Setup**

### **build.gradle**

```groovy
plugins {
    id 'net.neoforged.gradle.userdev' version '7.0.80'
}

version = '1.0.0'
group = 'com.progressivestages'
archivesBaseName = 'progressivestages'

java.toolchain.languageVersion = JavaLanguageVersion.of(21)

minecraft {
    mappings channel: 'official', version: '1.21.1'

    runs {
        client {
            workingDirectory project.file('run/client')
            ideaModule "${rootProject.name}.${project.name}.main"
            args '--username', 'Dev'
            mods {
                progressivestages {
                    source sourceSets.main
                }
            }
        }

        server {
            workingDirectory project.file('run/server')
            ideaModule "${rootProject.name}.${project.name}.main"
            args '--nogui'
            mods {
                progressivestages {
                    source sourceSets.main
                }
            }
        }

        data {
            workingDirectory project.file('run/data')
            ideaModule "${rootProject.name}.${project.name}.main"
            args '--mod', 'progressivestages', '--all', '--output', file('src/generated/resources/'), '--existing', file('src/main/resources/')
            mods {
                progressivestages {
                    source sourceSets.main
                }
            }
        }
    }
}

repositories {
    maven {
        name = 'Modrinth'
        url = 'https://api.modrinth.com/maven'
    }
    maven {
        name = 'FTB Maven'
        url = 'https://maven.ftbteam.com/'
    }
}

dependencies {
    // NeoForge
    implementation "net.neoforged:neoforge:21.1.219"

    // EMI (required)
    implementation fg.deobf("dev.emi:emi-neoforge:1.1.18+1.21.1:api")
    runtimeOnly fg.deobf("dev.emi:emi-neoforge:1.1.18+1.21.1")

    // FTB Teams (required)
    implementation fg.deobf("dev.ftb.mods:ftb-teams-neoforge:2101.1.0")

    // FTB Quests (optional, for command integration)
    compileOnly fg.deobf("dev.ftb.mods:ftb-quests-neoforge:2101.1.0")

    // TOML parser (for stage files)
    implementation 'com.electronwill.night-config:toml:3.6.7'
}

tasks.withType(ProcessResources).configureEach {
    var replaceProperties = [
        minecraft_version: '1.21.1',
        minecraft_version_range: '[1.21.1,1.22)',
        neoforge_version: '21.1.219',
        neoforge_version_range: '[21.1,)',
        loader_version_range: '[2,)',
        mod_id: 'progressivestages',
        mod_name: 'ProgressiveStages',
        mod_license: 'MIT',
        mod_version: version,
        mod_authors: 'YourName',
        mod_description: 'Team-scoped linear stage progression with integrated locking and EMI visual feedback'
    ]

    inputs.properties replaceProperties

    filesMatching(['META-INF/mods.toml']) {
        expand replaceProperties + [project: project]
    }
}
```

### **mods.toml**

```toml
modLoader="javafml"
loaderVersion="${loader_version_range}"
license="${mod_license}"
issueTrackerURL="https://github.com/yourusername/progressivestages/issues"

[[mods]]
modId="${mod_id}"
version="${mod_version}"
displayName="${mod_name}"
description="${mod_description}"
authors="${mod_authors}"
displayURL="https://modrinth.com/mod/progressivestages"
logoFile="logo.png"

[[dependencies.progressivestages]]
modId="neoforge"
type="required"
versionRange="${neoforge_version_range}"
ordering="NONE"
side="BOTH"

[[dependencies.progressivestages]]
modId="minecraft"
type="required"
versionRange="${minecraft_version_range}"
ordering="NONE"
side="BOTH"

[[dependencies.progressivestages]]
modId="emi"
type="required"
versionRange="[1.1,)"
ordering="AFTER"
side="BOTH"

[[dependencies.progressivestages]]
modId="ftbteams"
type="required"
versionRange="[2101.0,)"
ordering="AFTER"
side="BOTH"

[[dependencies.progressivestages]]
modId="ftbquests"
type="optional"
versionRange="[2101.0,)"
ordering="AFTER"
side="BOTH"
```

### **progressivestages.mixins.json**

```json
{
  "required": true,
  "minVersion": "0.8",
  "package": "com.progressivestages.mixins",
  "compatibilityLevel": "JAVA_21",
  "refmap": "progressivestages.refmap.json",
  "client": [
    "client.EMISlotWidgetMixin",
    "client.EMIRecipeScreenMixin",
    "client.InventoryScreenMixin"
  ],
  "mixins": [
    "common.CraftingMenuMixin",
    "common.ServerPlayerMixin"
  ],
  "injectors": {
    "defaultRequire": 1
  }
}
```

### **pack.mcmeta**

```json
{
  "pack": {
    "description": "ProgressiveStages resources",
    "pack_format": 34
  }
}
```

---

## **7. API for External Mods**

**File**: `common/api/IProgressiveStagesAPI.java`

```java
package com.progressivestages.common.api;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;

import java.util.Optional;
import java.util.Set;

public interface IProgressiveStagesAPI {

    /**
     * Check if a player has a specific stage
     * @param player The player to check
     * @param stageId The stage ID (e.g., "progressivestages:diamond_age")
     * @return true if player (or their team) has the stage
     */
    boolean hasStage(ServerPlayer player, ResourceLocation stageId);

    /**
     * Grant a stage to a player (and all prerequisite stages)
     * Also grants to all team members if team mode enabled
     * @param player The player to grant to
     * @param stageId The stage ID
     */
    void grantStage(ServerPlayer player, ResourceLocation stageId);

    /**
     * Revoke a stage from a player (and all subsequent stages)
     * Also revokes from all team members if team mode enabled
     * @param player The player to revoke from
     * @param stageId The stage ID
     */
    void revokeStage(ServerPlayer player, ResourceLocation stageId);

    /**
     * Get all stages a player has
     * @param player The player
     * @return Set of stage IDs
     */
    Set<ResourceLocation> getStages(ServerPlayer player);

    /**
     * Get the current/highest stage a player has reached
     * @param player The player
     * @return The highest stage in progression order, or empty if no stages
     */
    Optional<ResourceLocation> getCurrentStage(ServerPlayer player);

    /**
     * Check if an item is locked for a player
     * @param player The player
     * @param item The item to check
     * @return The required stage, or empty if not locked
     */
    Optional<ResourceLocation> getRequiredStage(ServerPlayer player, Item item);

    /**
     * Get the stage order (1 = earliest, higher = later)
     * @param stageId The stage ID
     * @return The order number, or empty if stage doesn't exist
     */
    Optional<Integer> getStageOrder(ResourceLocation stageId);

    /**
     * Reload all stage definitions from disk
     * Server-side only
     */
    void reloadStages();
}
```

**Access**:
```java
// In your mod
import com.progressivestages.ProgressiveStages;

IProgressiveStagesAPI api = ProgressiveStages.getAPI();
if (api.hasStage(player, new ResourceLocation("progressivestages", "diamond_age"))) {
    // Player has diamond age
}
```

---

## **8. Testing Checklist**

### **Phase 1 Tests**
- [ ] Server starts without errors
- [ ] `minecraft/ProgressiveStages/` directory created
- [ ] Example stage files generated (stone_age, iron_age, diamond_age)
- [ ] Config file generated with comments
- [ ] Player joins, auto-granted starting stage
- [ ] `/stage grant @s iron_age` → both stone_age and iron_age granted
- [ ] `/stage list` shows all stages with order
- [ ] Server restart → stages persist
- [ ] Second player joins → independent stages

### **Phase 2 Tests**
- [ ] Stage files parsed correctly
- [ ] Lock registry populated
- [ ] Query `getRequiredStage(Items.DIAMOND)` → returns "diamond_age"
- [ ] Invalid item ID in stage file → warning logged, skipped
- [ ] Tag locks work (e.g., `#forge:gems/diamond`)
- [ ] Name locks work (e.g., "diamond" matches all diamond items)
- [ ] Client receives lock registry sync packet

### **Phase 3 Tests**
- [ ] Lock diamond, try to use → blocked, message shown, sound played
- [ ] Try to pick up locked diamond → blocked
- [ ] Locked item in inventory → auto-dropped within 1 second
- [ ] Click locked item in chest → can't pick up, stays in chest
- [ ] Grant stage → can now use item
- [ ] Sound volume/pitch configurable

### **Phase 4 Tests**
- [ ] Lock diamond_pickaxe recipe
- [ ] Open crafting table, place ingredients → no output shown
- [ ] Force-craft via mod/hack → server blocks, ingredients returned
- [ ] Grant stage → recipe shows output, can craft
- [ ] Recipe book doesn't show locked recipes (if hide_locked_recipes = true)

### **Phase 5 Tests**
- [ ] Open EMI, locked item shows lock icon in item list
- [ ] Locked recipe output shows lock icon + highlight
- [ ] Hover locked item → tooltip shows required stage, current stage, progress
- [ ] Config: change lock icon size → verify change in EMI
- [ ] Config: change highlight color → verify change in EMI
- [ ] Grant stage → lock icons disappear

### **Phase 6 Tests**
- [ ] Lock diamond_block placement → can't place, message shown
- [ ] Lock enchanting_table interaction → can't open GUI
- [ ] Lock minecraft:the_nether → can't enter portal, message shown
- [ ] Grant stage → can place block, interact, enter dimension

### **Phase 7 Tests**
- [ ] Lock entire mod (e.g., `mods = ["botania"]`) → all Botania items locked
- [ ] Name lock (e.g., `names = ["diamond"]`) → all items with "diamond" locked
- [ ] Grant stage → all items from mod/name unlocked

### **Phase 8 Tests**
- [ ] Lock Create Andesite Casing interaction (andesite_alloy on log)
- [ ] Hold alloy, right-click log → blocked, message shown
- [ ] Lock flint_and_steel on TNT → blocked
- [ ] Tag support: `held_item = "#forge:gems/diamond"` → works
- [ ] Grant stage → interactions allowed

### **Phase 9 Tests (2+ players required)**
- [ ] Player A and B form team, both iron_age
- [ ] Grant diamond_age to A → B also gets diamond_age
- [ ] Player C (stone_age) tries to join → blocked ("stage mismatch")
- [ ] Player D (diamond_age) joins team → allowed
- [ ] Grant netherite_age to any member → all get it (now aligned)
- [ ] Player B leaves team → keeps stages (if persist_stages_on_leave = true)
- [ ] Revoke stage from team member → kicked if kick_on_stage_mismatch = true

### **Phase 10 Tests**
- [ ] Add invalid stage file → skipped, error logged
- [ ] `/progressivestages validate` → reports all errors
- [ ] Add unlock_message with color codes → grants stage → message shown with colors
- [ ] `/progressivestages reload` → modifies stage file → reload → changes applied
- [ ] Remove FTB Teams mod → restart → falls back to solo mode (no crash)
- [ ] `/progressivestages info diamond_age` → shows stage details

---

## **9. Performance Benchmarks**

Target server specs: 10-30 players, ~12-14 GB RAM

### **Expected Performance**
- **Stage queries** (cached): < 1μs per check
- **Lock queries** (cached): < 1μs per check
- **Inventory scan** (20 ticks): < 1ms per player per scan
- **Network sync** (on stage change): < 1KB per player
- **Memory overhead**: ~5 MB for 1000 locks + 30 players

### **Optimization Points**
- Cache player → stages mapping (rebuild on change)
- Cache item → required stage mapping (rebuild on registry reload)
- Batch stage updates (multiple grants in same tick → one packet)
- Delta sync (only send changes, not full snapshots after initial sync)
- Lazy load stage files (on-demand, not all at once)

---

## **10. Release Checklist**

### **Before Release**
- [ ] All phases tested and passing
- [ ] Multiplayer testing with 5+ players
- [ ] Performance testing (30 players, 1000+ locks)
- [ ] Config documented with comments
- [ ] Example stage files polished
- [ ] README written (install, config, commands, examples)
- [ ] Changelog written (features, changes, fixes)
- [ ] License file added (MIT or your choice)
- [ ] Logo/icon created
- [ ] Screenshots/GIFs for Modrinth/CurseForge
- [ ] Version number finalized (1.0.0)

### **Packaging**
- [ ] Build: `./gradlew build`
- [ ] Output JAR: `build/libs/progressivestages-1.0.0.jar`
- [ ] Test JAR in clean instance with only dependencies
- [ ] Verify mods.toml metadata correct
- [ ] Verify pack.mcmeta included

### **Publishing**
- [ ] Upload to Modrinth:
  - Categories: Utility, Adventure and RPG, Progression
  - Dependencies: NeoForge 21.1+, Minecraft 1.21.1, EMI (required), FTB Teams (required), FTB Quests (optional)
  - Description: copy from mods.toml
  - Screenshots: EMI lock overlays, command examples, config examples
- [ ] Upload to CurseForge:
  - Same categories and dependencies
  - Cross-link to Modrinth
- [ ] GitHub release:
  - Tag: v1.0.0
  - Attach JAR
  - Changelog in release notes
- [ ] Announce on Discord/Reddit (if applicable)

---

## **11. Future Enhancements (Post-1.0)**

### **Version 1.1**
- [ ] GUI for managing stages (in-game stage tree viewer)
- [ ] Per-player stage override (admin can give specific player a stage without affecting team)
- [ ] Stage dependency graph (branching progression, not just linear)
- [ ] Integration with other quest mods (HQM, BetterQuesting)

### **Version 1.2**
- [ ] REI/JEI plugins (in addition to EMI)
- [ ] Stage-based world generation (lock biomes, structures)
- [ ] Stage-based mob spawning (lock mobs until stage reached)
- [ ] Achievement system (grant stages via advancements, not just commands)

### **Version 1.3**
- [ ] Datapack-driven stage files (in addition to `minecraft/ProgressiveStages/`)
- [ ] Stage presets (one-click install of common progressions: SkyFactory, SevTech, etc.)
- [ ] API for KubeJS (direct scripting support without Java API calls)

---

## **12. Support & Documentation**

### **README.md** (for GitHub/Modrinth)
Include:
- Installation instructions
- Dependency list
- Quick start (generate example files, grant first stage)
- Config explanation (link to wiki for full docs)
- Command reference
- Stage file format with examples
- FAQ (common issues, bypass prevention, performance tips)
- Credits (EMI, FTB Teams, community)

### **Wiki** (GitHub wiki or separate site)
- Full config reference (every option explained)
- Stage file schema (every field, every lock type)
- Command reference (every command, every argument)
- Integration guide (how to use with FTB Quests, KubeJS, etc.)
- Examples (SkyFactory progression, SevTech progression, etc.)
- Troubleshooting (common errors, log analysis)

### **Support Channels**
- GitHub Issues: bug reports, feature requests
- Discord: community support, pack dev help
- Reddit: announcements, discussions

---

## **End of Plan**

This is a complete, production-ready implementation plan for ProgressiveStages. Follow the phases in order, test thoroughly after each phase, and you'll have a polished, performant, community-ready mod.

**Estimated total dev time**: 6-8 weeks (assuming 1 developer, part-time)

**Questions before starting Phase 1?** Let me know and I'll clarify any section.
