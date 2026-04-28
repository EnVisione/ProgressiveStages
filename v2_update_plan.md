# ProgressiveStages 2.0 Update Plan & Outline

> **Deferred to post-2.0:** §2.8 Ore Restrictions (visual masquerade / drop override) and the *indestructibility* and *explosion-immunity* parts of §2.12 Structure Restrictions. Both require client-chunk-packet rewriting or structure bounding-box tracking that warrants a separate release.

## 1. Executive Summary
The ProgressiveStages 2.0 update focuses on expanding the mod's restriction capabilities far beyond standard items and blocks. By introducing deep integrations into world generation, UI, entities, regions, and full fluid control, it empowers modpack creators to design highly customized and narrative-driven progression systems. 

Crucially, the update also introduces a **unified TOML structure** that consolidates the scattered `item`, `item_tags`, `item_mods` arrays into unified arrays using prefix-based string identifiers (e.g., `tag:`, `mod:`, `id:`). Everything is built ground-up to be multiplayer-friendly and fully synchronized across FTB Teams.

---

## 2. Feature Mechanics Outline

### 2.1. Crop Restrictions
* **Mechanic:** Controls the lifecycle and interaction of specific crops based on progression stages. This feature enables tailored farming mechanics, adding depth to resource management.
* **In-Depth Blocking:** 
  * **Planting:** Prevents players from placing locked seeds or crops on farmland or other valid blocks. The item will act as if used on an invalid surface.
  * **Growth:** Halts natural random growth ticks for locked crops. If a crop is placed via world generation or bypasses placement checks, it will remain at age 0.
  * **Bonemeal:** Prevents players and dispensers from applying bone meal or modded fertilizers to locked crops.
  * **Harvesting:** If a player breaks a fully grown locked crop (e.g., generated in a village), the drops are modified to yield only the basic seed or nothing, preventing early access to the harvestable item.
  * **Automation:** Modded farmers (e.g., Create Harvesters, Botany Pots) will fail to process or plant the restricted crops if the owner lacks the required stage.

### 2.2. Dimension Restrictions
* **Mechanic:** Controls player access to specific dimensions within the game world, allowing for customized progression paths or lore-driven gating.
* **In-Depth Blocking:**
  * **Portal Creation:** Prevents the ignition or creation of portals (e.g., lighting a Nether portal, inserting Eyes of Ender).
  * **Teleportation:** Cancels any attempt to teleport into the dimension, whether via vanilla portals, modded teleporters (Waystones, Mekanism Teleporters), or warp commands.
  * **Ejection:** If a player somehow logs into a locked dimension (e.g., stage was revoked while they were offline), they are safely ejected to their spawn point or the Overworld.
  * **UI & Items:** Modded items that peer into or require dimensions (e.g., Nature's Compass) will show the dimension as unavailable or fail to search.

### 2.3. Enchant Restrictions
* **Mechanic:** Automatically removes forbidden enchantments and prevents their acquisition.
* **In-Depth Blocking:**
  * **Enchanting Table:** Locked enchantments simply will not appear as options in the enchanting table UI.
  * **Anvils:** Prevents the application of locked enchanted books to items, showing a "Too Expensive!" or custom blocked message.
  * **Villager Trading:** Librarian villagers will not offer locked enchantments, or the trades will be unselectable.
  * **Inventory Stripping:** If an item with a locked enchantment enters the player's inventory (e.g., from a dungeon chest), the enchantment is dynamically stripped from the item, or the item is rendered unusable until unlocked.
  * **Loot & Fishing:** Dynamically removes locked enchantments from naturally generated loot or fishing catches.

### 2.4. Fluid Restrictions (Full Lock)
* **Mechanic:** Completely locks the interaction, transport, and visibility of specific fluids.
* **In-Depth Blocking:**
  * **Bucket & Container Interaction:** Prevents picking up or placing the fluid in the world using buckets, modded tanks, or other fluid containers.
  * **Piping & Transport:** Stops mechanical pipes, pumps, and fluid conduits from extracting or inserting the restricted fluid from/to world blocks or machines.
  * **Machine Processing:** Prevents modded machines from accepting the fluid via manual insertion or automatic piping if the owner lacks the stage.
  * **World Interaction:** If a player attempts to swim in or step into a locked fluid block in the world, they are physically repelled or suffer debuffs to prevent them from exploiting its properties.
  * **EMI/JEI Visibility:** Hides the fluid entirely from recipe viewers.

### 2.5. Interaction Restrictions
* **Mechanic:** Locks specific player-to-world interactions, right-clicks, and combinations of items used on targets.
* **In-Depth Blocking:**
  * **Block Right-Click:** Prevents clicking on specific blocks without an item (e.g., flipping a specific lever, sleeping in a bed, opening a trapdoor).
  * **Item on Block:** Prevents using a designated held item on a target block. Essential for gating mechanics like Create mechanical crafting (e.g., Andesite Alloy on a stripped log) or lighting portals (Flint and Steel on Obsidian).
  * **Item on Entity:** Prevents using specific items on mobs (e.g., using Shears on a Sheep, applying a Name Tag to a Zombie, or trading specific items to Villagers).

### 2.6. Loot Restrictions
* **Mechanic:** Controls the appearance of specific items in loot tables and mob drops, preventing players from bypassing progression through exploration.
* **In-Depth Blocking:**
  * **Chest Generation:** When a player opens a generated chest (dungeons, villages), the loot is calculated. Any locked items are dynamically removed from the chest before the player can see or grab them.
  * **Mob Drops:** When an entity is killed, the drops are filtered against the killer's stages. Locked items are excised from the drop pool.
  * **Block Drops:** Modifies block drops (e.g., Fortune on ores, breaking gravel for flint) so that locked items never drop.
  * **Fishing & Archeology:** Filters fishing rewards and brushed blocks to remove restricted artifacts or treasures.

### 2.7. Mob Restrictions (Spawn & Replace)
* **Mechanic:** Controls interactions with specific mobs, restricting spawning and allowing for dynamic replacements to balance gameplay.
* **In-Depth Blocking:**
  * **Natural Spawning:** The world prevents locked mobs from naturally spawning in the radius of players who lack the stage.
  * **Spawners & Eggs:** Mob spawners and spawn eggs will fail to produce the locked entity, expending the attempt but doing nothing.
  * **Dynamic Replacement:** Instead of just cancelling the spawn, a mob can be configured to replace itself. For example, if an Enderman tries to spawn but is locked, it instantly transforms into a standard Zombie, preserving the challenge level without breaking progression.
  * **Boss Gating:** Prevents the summoning of bosses (e.g., Wither, Warden) if the conditions or players nearby do not meet the stage requirements.

### 2.8. Ore Restrictions (Masquerade & Override) — **ON HOLD (post-2.0)**
* **Status:** Deferred. TOML schema (`[[ores.overrides]]`) is parsed and stored for forward compatibility, but no visual or drop enforcement ships in 2.0. Requires client-side chunk packet interception and is scoped as its own follow-up release.
* **Mechanic (planned):** Controls the visual and physical availability of ores, hiding them from early-game players.
* **In-Depth Blocking (planned):**
  * **Visual Masquerade:** Ores visually disguise themselves as their surrounding stone type (Stone, Deepslate, Netherrack) to players without the stage.
  * **Info Hiding:** Modded HUDs (WAILA, Jade, The One Probe) will display the masqueraded block's name (e.g., "Stone") instead of the real ore.
  * **Mining Override:** If a player mines the masqueraded ore, it takes as long to break as the fake block, and drops the fake block's drops (e.g., Cobblestone). Silk Touch and Fortune are completely ignored for the locked ore.
  * **Explosions:** If blown up by TNT or creepers, the locked ore drops the masqueraded item or nothing.

### 2.9. Pet Restrictions
* **Mechanic:** Limits the ability to tame, breed, or command specific animals and pets.
* **In-Depth Blocking:**
  * **Taming:** Prevents the consumption of taming items (bones for wolves, fish for cats) on locked pets. The item may be consumed but the tame event will forcefully fail.
  * **Breeding:** Prevents players from feeding breeding items to locked animals. The love hearts will not appear.
  * **Riding:** Prevents saddling or mounting locked entities (horses, pigs, modded mounts).
  * **Commanding:** Prevents commanding already-tamed locked pets (making them sit, stand, or attack).

### 2.10. Region Restrictions
* **Mechanic:** Defines custom 3D bounding boxes or chunk areas in the world with strict, progression-gated rules.
* **In-Depth Blocking:**
  * **Interaction:** Prevents placing or breaking any blocks within the region bounds.
  * **Explosion Nullification:** Any explosion that occurs inside or overlaps with the region will do exactly zero block damage, protecting the area.
  * **Spawn Blocking:** completely disables hostile or passive mob spawning inside the region geometry.
  * **Access Gating:** Can push players back or apply severe debuffs (Blindness, Slowness) if they enter the region without the required stage.

### 2.11. Screen Restrictions
* **Mechanic:** Limits access to specific in-game screens, interfaces, and GUIs based on progression stages.
* **In-Depth Blocking:**
  * **Container Access:** Prevents opening vanilla blocks like Crafting Tables, Anvils, or modded machines (e.g., Mekanism Metallurgic Infuser).
  * **Inventory Tabs:** Can block access to specific tabs or overlays in modded inventories (e.g., Curios, FTB Quests screen).
  * **Item GUIs:** Prevents right-clicking items that open screens (e.g., Backpacks, Portable Crafting Tables).
  * **Feedback:** Plays a locked sound and displays an action bar message when a player attempts to open a restricted screen.

### 2.12. Structure Restrictions — **PARTIAL (entry blocking only)**
* **Status for 2.0:** Entry denial and chest locking are in scope (overlap with region gating). Indestructibility and explosion immunity are **ON HOLD** — they require structure bounding-box tracking per loaded chunk, which is its own engineering lift.
* **Mechanic:** Dynamically applies region-like restrictions to the bounding boxes of generated structures (e.g., Ocean Monuments, Strongholds, modded dungeons).
* **In-Depth Blocking:**
  * **Entry Denial:** Similar to region gating, players are repelled from entering the structure's bounding box. *(2.0)*
  * **Indestructibility:** Applies mining fatigue or completely cancels block break events for blocks that are part of the structure. *(on hold)*
  * **Explosion Immunity:** Prevents creepers, TNT, or boss attacks from damaging the structure's architecture. *(on hold)*
  * **Chest Locking:** Generated chests within the structure cannot be opened or broken until the stage is achieved. *(2.0)*

---

## 3. Reworked TOML Configuration Structure

Instead of having `items`, `item_tags`, and `item_mods` in scattered arrays, we move to a unified list system where the type is defined by a prefix. This greatly reduces boilerplate and makes the config easier to read. Furthermore, core locks are broken out into their own specific categories (`[items]`, `[blocks]`, `[fluids]`, `[recipes]`).

**Prefixes:**
* `id:` (or no prefix) -> Exact registry ID (e.g., `id:minecraft:wheat` or just `minecraft:wheat`)
* `mod:` -> Everything from a mod (e.g., `mod:croptopia`)
* `tag:` -> Everything in a tag (e.g., `tag:minecraft:crops`)
* `name:` -> Substring match (e.g., `name:diamond`)

Every category supports `locked` (blacklisted until stage is unlocked) and `always_unlocked` (whitelisted exceptions).

---

## 4. The 2.0 `diamond_age.toml` Sample File

Below is the exhaustive, 2.0-compliant default stage file showcasing all mechanics with detailed documentation.

```toml
# ============================================================================
# Stage definition for Diamond Age (v2.0)
# This file demonstrates ALL features available in ProgressiveStages v2.0
# ============================================================================
#
# NEW IN 2.0: UNIFIED PREFIX SYSTEM
# Instead of separate arrays for tags, mods, and exact IDs, you now use prefixes:
#   "id:minecraft:diamond"  (or just "minecraft:diamond") -> Exact Match
#   "tag:c:gems/diamond"    -> Tag Match
#   "mod:mekanism"          -> Mod Match
#   "name:diamond"          -> Substring Match
#
# Every category supports `locked` (blacklisted until stage is unlocked) and 
# `always_unlocked` (whitelisted exceptions).
# ============================================================================

[stage]
id = "diamond_age"
display_name = "Diamond Age"
description = "Diamond tools, advanced farming, and new dimensions await."
icon = "minecraft:diamond_pickaxe"
unlock_message = "&b&lDiamond Age Unlocked! &r&7You have ascended."
dependency = "iron_age"

# ============================================================================
# 1. CORE LOCKS (Items, Blocks, Fluids, Recipes)
# Everything below is LOCKED for players who do NOT have this stage.
# ============================================================================

[items]
locked = [
    "id:minecraft:diamond_pickaxe",
    "mod:ae2",
    "tag:c:gems/diamond"
]
always_unlocked = [
    "id:ae2:certus_quartz_crystal" # Exception to the ae2 mod lock
]

[blocks]
locked = [
    "id:minecraft:enchanting_table",
    "tag:minecraft:beacon_base_blocks"
]
always_unlocked = []

[fluids]
# ⚠️ FULL LOCK: Prevents piping, pumping, bucketing, and hides from EMI/JEI.
locked = [
    "mod:mekanism",
    "id:minecraft:lava"
]
always_unlocked = []

[recipes]
# Locks the CRAFTING of these items, but allows finding/using them
locked_items = [
    "id:minecraft:diamond_chestplate"
]

# ============================================================================
# 2. CROP RESTRICTIONS
# Controls plant growth, planting, and harvesting
# ============================================================================
[crops]
locked = [
    "tag:minecraft:crops",   # Locks all vanilla crops from growing/planting
    "mod:croptopia",         # Locks all croptopia crops
    "id:minecraft:wheat"
]
always_unlocked = [
    "id:minecraft:carrots"   # Carrots can always be grown
]

# ============================================================================
# 3. DIMENSION RESTRICTIONS
# Controls teleportation and portal usage
# ============================================================================
[dimensions]
locked = [
    "id:minecraft:the_end",
    "id:minecraft:the_nether"
]

# ============================================================================
# 4. ENCHANT RESTRICTIONS
# Removes enchantments from items, hides them in enchanting tables/anvils
# ============================================================================
[enchants]
locked = [
    "id:minecraft:mending",
    "tag:c:weapon_enchants",
    "mod:apotheosis"
]

# ============================================================================
# 5. INTERACTION RESTRICTIONS
# Locks specific combinations of "player does X to Y" actions.
# Useful for gating Create mod mechanics, right-click actions, etc.
# ============================================================================
[[interactions]]
type = "block_right_click"
target_block = "id:minecraft:enchanting_table"
description = "Use Enchanting Table"

[[interactions]]
type = "item_on_block"
held_item = "id:create:andesite_alloy"
target_block = "tag:minecraft:logs"
description = "Create Andesite Casing"

[[interactions]]
type = "item_on_entity"
held_item = "id:minecraft:name_tag"
target_entity = "id:minecraft:zombie"
description = "Name a Zombie"

# ============================================================================
# 6. LOOT RESTRICTIONS
# Prevents items from dropping from mobs or generating in chests
# ============================================================================
[loot]
locked = [
    "id:minecraft:diamond",
    "mod:artifacts"
]

# ============================================================================
# 7. MOB RESTRICTIONS (Spawn & Replace)
# Prevents spawning, or replaces the mob with another
# ============================================================================
[mobs]
locked_spawns = [
    "id:minecraft:creeper",
    "mod:borninchaos_v1"
]

# If an Enderman tries to spawn near a player without this stage, 
# it spawns a Zombie instead.
[[mobs.replacements]]
target = "id:minecraft:enderman"
replace_with = "id:minecraft:zombie"

# ============================================================================
# 8. ORE RESTRICTIONS (Masquerade / Override)
# Hides blocks visually and drops alternative items if broken
# ============================================================================
[[ores.overrides]]
target = "id:minecraft:diamond_ore"
display_as = "id:minecraft:stone"
drop_as = "id:minecraft:cobblestone"

[[ores.overrides]]
target = "id:minecraft:deepslate_diamond_ore"
display_as = "id:minecraft:deepslate"
drop_as = "id:minecraft:cobbled_deepslate"

# ============================================================================
# 9. PET RESTRICTIONS
# Limits taming, breeding, and interactions with animals
# ============================================================================
[pets]
locked_taming = [
    "id:minecraft:wolf",
    "tag:c:tamable"
]
locked_breeding = [
    "id:minecraft:villager",
    "id:minecraft:cow"
]

# ============================================================================
# 10. SCREEN RESTRICTIONS
# Blocks opening specific GUIs, containers, or inventories
# ============================================================================
[screens]
locked = [
    "id:minecraft:crafting_table",
    "id:minecraft:anvil",
    "mod:create" # Blocks all Create mod GUIs
]

# ============================================================================
# 11. STRUCTURE RESTRICTIONS & REGION RESTRICTIONS
# Blocks interactions, entry, or explosions inside generated structures
# ============================================================================
[structures]
locked_entry = [
    "id:minecraft:ocean_monument",
    "id:minecraft:stronghold"
]

# Defines rules for players inside locked structures
[structures.rules]
prevent_block_break = true
prevent_block_place = true
prevent_explosions = true
disable_mob_spawning = true

# Custom defined regions (x1, y1, z1 to x2, y2, z2 in a specific dimension)
[[regions]]
dimension = "minecraft:overworld"
pos1 = [0, -64, 0]
pos2 = [1000, 320, 1000]
prevent_entry = true
prevent_explosions = true

# ============================================================================
# ENFORCEMENT EXCEPTIONS
# Global rules for interacting with items that are currently locked
# ============================================================================
[enforcement]
allowed_use = [ "id:minecraft:diamond_ore" ]
allowed_pickup = [ "id:minecraft:diamond" ]
allowed_hotbar = []
allowed_mouse_pickup = []
allowed_inventory = [ "id:minecraft:diamond" ]
```
