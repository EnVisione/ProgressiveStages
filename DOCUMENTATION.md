# ProgressiveStages - Complete Documentation

## Overview

ProgressiveStages is a NeoForge mod for Minecraft 1.21.1 that provides **stage-based progression locks** for modpack developers.

You define *stages* in TOML files, then ProgressiveStages:
- grants/revokes stages to players (or teams)
- blocks items/blocks/recipes/dimensions until stages are unlocked
- integrates with EMI for visual feedback (lock icons, overlays)
- optionally integrates with **FTB Quests** so Stage Tasks update immediately when stages change

---

## v1.2 Changes

### Dependency Graph (replaces linear order)
- **Removed:** `order` field in stage definitions
- **Added:** `dependency` field for explicit stage dependencies
- Stages can have zero, one, or multiple dependencies
- Dependencies are validated on load (missing/circular dependencies logged)

### Admin Bypass with Confirmation
When granting a stage with missing dependencies:
```
/stage grant Player diamond_age
→ "Cannot grant diamond_age: Player is missing dependency 'iron_age'. Type command again to bypass."

/stage grant Player diamond_age
→ Stage granted (bypassed dependency check)
```
Confirmation expires after 10 seconds.

### Multiple Starting Stages
Config now supports granting multiple stages on first join:
```toml
[general]
starting_stages = ["stone_age", "tutorial_complete", "spawn_protection"]
```

### Unlocked Items (Whitelist Exceptions)
New field to always allow specific items, even if broader locks apply:
```toml
[locks]
mods = ["mekanism"]  # Lock all Mekanism items
unlocked_items = ["mekanism:basic_universal_cable"]  # Except this one
```

### Entity Locks
Lock entity types to prevent attacking them until a stage is unlocked:
```toml
[locks]
entities = ["minecraft:warden"]
entity_tags = ["#minecraft:raiders"]
entity_mods = ["mekanism"]  # Lock all entities from a mod
```
Note: Using `mods = ["modid"]` also locks entities from that mod (cascade behavior).

### Unlocked Entities (Whitelist Exceptions)
New field to always allow attacking specific entities, even if broader locks apply:
```toml
[locks]
mods = ["mekanism"]  # Locks items, blocks, AND entities
unlocked_entities = ["mekanism:robit"]  # But allow attacking robits
```

---

## Core Concepts

### What is a Stage?
A stage is an ID like:
- `stone_age`
- `progressivestages:diamond_age`
- `progressivestages:tech/iron_age` (hierarchical)

Stages are server-authoritative and stored per **team** (FTB Teams mode) or per **player** (solo mode).

### Stage ID Normalization (Matches Minecraft ResourceLocation Rules)
Stage IDs are normalized (so admins/configs are harder to mess up):

| Rule | Example |
|------|---------|
| Whitespace trimmed | `"  iron_age  "` → `"iron_age"` |
| Lowercased | `"Diamond_Age"` → `"diamond_age"` |
| Default namespace added | `"iron_age"` → `"progressivestages:iron_age"` |
| Namespaced preserved | `"mymod:my_stage"` → `"mymod:my_stage"` |
| Hierarchical paths allowed | `"tech/iron_age"` → `"progressivestages:tech/iron_age"` |

**Allowed characters** (aligned with Minecraft/NeoForge resource IDs):
- **Namespace:** `a-z 0-9 _ - .`
- **Path:** `a-z 0-9 _ - . /`

Disallowed examples:
- `Iron Age` (spaces)
- `Stage#1` (special chars)
- `/iron_age` or `iron_age/` (leading/trailing slash)
- `tech//iron_age` (double slash)

---

## Stage Files

### Location
Stage files live in the **global config directory**:

```
config/ProgressiveStages/*.toml
```

That means stages apply to all worlds on that server/instance (not per-save).

### Stage File Format (v1.2)
```toml
[stage]
# Required
id = "iron_age"

# Optional (UI)
display_name = "Iron Age"
description = "Iron tools and basic machinery"
icon = "minecraft:iron_pickaxe"
unlock_message = "&6Iron Age Unlocked!"

# Dependency (v1.2) - stages that must be unlocked BEFORE this one
# Single dependency:
dependency = "stone_age"

# OR multiple dependencies (list format):
# dependency = ["stone_age", "tutorial_complete"]

# OR no dependency (omit field) - can be granted anytime

[locks]
# Items (direct IDs)
items = [
  "minecraft:iron_pickaxe",
  "minecraft:iron_sword"
]

# Item tags (tag keys)
item_tags = [
  "#c:ingots/iron"
]

# Item mods (lock all items from a mod, NOT blocks/entities)
# Use this when you only want to lock items from a mod
item_mods = [
  "mekanism"  # Locks all Mekanism items, but NOT blocks or entities
]

# Name patterns (case-insensitive substring matching)
names = [
  "diamond",  # Locks minecraft:diamond, minecraft:diamond_pickaxe, etc.
  "netherite" # Locks all netherite items
]

# Block mods (lock all blocks from a mod, NOT items/entities)
# Use this when you only want to lock blocks from a mod
block_mods = [
  "mekanism"  # Locks all Mekanism blocks, but NOT items or entities
]

# Mod locks (lock ALL content from a mod: items, blocks, AND entities)
# NOTE: Using mods = ["modid"] locks EVERYTHING from that mod.
# For finer control, use item_mods, block_mods, or entity_mods separately.
mods = [
  "create"  # Locks all items, blocks, AND entities from the Create mod
]

# Blocks
blocks = [
  "minecraft:iron_block"
]

# Block tags
block_tags = [
  "#minecraft:logs"
]

# Dimensions
dimensions = [
  "minecraft:the_nether"
]

# Entities (v1.1) - Lock entity types (prevent attacking)
entities = [
  "minecraft:warden",       # Can't attack wardens until this stage
  "minecraft:elder_guardian" # Can't attack elder guardians
]

# Entity tags (v1.1) - Lock all entity types in a tag
entity_tags = [
  "#minecraft:raiders"  # Can't attack any raiders
]

# Entity mods (v1.1) - Lock all entities from a mod
# NOTE: entity_mods is ONLY for entity attacks. Use this when you want to lock
# entities separately from items. If you use mods = ["modid"], entities are 
# automatically locked as well (no need to also specify entity_mods).
entity_mods = [
  "mekanism"  # Can't attack any Mekanism entities
]

# Unlocked items (v1.2) - Whitelist exceptions
# These items are ALWAYS accessible, even if broader locks apply
# Use case: Lock entire mod but allow specific items
unlocked_items = [
  "mekanism:basic_universal_cable",  # Allow this even though mekanism is locked
  "minecraft:diamond_horse_armor"    # Allow this even though "diamond" name pattern is locked
]

# Unlocked entities (v1.2) - Whitelist exceptions for entities
# These entities can ALWAYS be attacked, even if broader entity locks apply
# Use case: Lock entire mod entities but allow attacking specific ones
unlocked_entities = [
  "mekanism:robit",  # Allow attacking robits even though mekanism entities are locked
  "minecraft:zombie" # Allow attacking zombies even if locked via tag/pattern
]
```

### Notes
- `stage.id` can be bare (`iron_age`) or namespaced (`progressivestages:iron_age`). Both work.
- All stage IDs are normalized (trimmed/lowercased) when loaded.
- v1.2: The `order` field is **removed**. Use `dependency` instead.

### Lock Cascade Behavior

The following table shows which content types are locked by each lock type:

| Lock Type | Items | Blocks | Entities | Fluids (EMI/JEI) |
|-----------|:-----:|:------:|:--------:|:----------------:|
| `mods = ["modid"]` | ✅ | ✅ | ✅ | ✅ |
| `item_mods = ["modid"]` | ✅ | ❌ | ❌ | ❌ |
| `block_mods = ["modid"]` | ❌ | ✅ | ❌ | ❌ |
| `entity_mods = ["modid"]` | ❌ | ❌ | ✅ | ❌ |
| `fluid_mods = ["modid"]` | ❌ | ❌ | ❌ | ✅ |
| `names = ["pattern"]` | ✅ | ✅ | ✅ | ✅ |
| `items = [...]` | ✅ | ❌ | ❌ | ❌ |
| `blocks = [...]` | ❌ | ✅ | ❌ | ❌ |
| `entities = [...]` | ❌ | ❌ | ✅ | ❌ |
| `fluids = [...]` | ❌ | ❌ | ❌ | ✅ |

**Key Points:**
- **`mods = ["modid"]`** is the "lock everything" option — locks all items, blocks, entities, AND fluids from that mod.
- **`names = ["pattern"]`** locks **everything** containing that pattern — items, blocks, entities, AND fluids.
- **Fluid locks only affect EMI/JEI visibility.** They do NOT prevent players from piping, pumping, or using fluids in machines. To block fluid transport, you need to lock the machines/pipes themselves.

### Whitelist Exceptions
Each content type has its own whitelist that takes priority over ALL lock types:

| Whitelist | Exempts |
|-----------|---------|
| `unlocked_items = [...]` | Items from `mods`, `item_mods`, `item_tags`, `names` |
| `unlocked_blocks = [...]` | Blocks from `mods`, `block_mods`, `block_tags`, `names` |
| `unlocked_entities = [...]` | Entities from `mods`, `entity_mods`, `entity_tags`, `names` |
| `unlocked_fluids = [...]` | Fluids from `mods`, `fluid_mods`, `fluid_tags`, `names` |

**Example:** Lock all Mekanism items but allow the configurator:
```toml
[locks]
mods = ["mekanism"]
unlocked_items = ["mekanism:configurator"]
```

---

## Configuration

Config file:
```
config/progressivestages-common.toml
```

The authoritative list of config keys is in `StageConfig.java`.

### General (v1.2)
```toml
[general]
# Multiple starting stages (v1.2)
starting_stages = ["stone_age", "tutorial_complete"]

# Or single stage
starting_stages = ["stone_age"]

# Or no starting stages
starting_stages = []

team_mode = "ftb_teams" # or "solo"
debug_logging = false

# If true: granting a stage also grants all missing dependencies recursively.
# If false: stages require explicit dependency satisfaction.
#   - Admin commands will prompt for confirmation to bypass missing dependencies.
#   - Automatic grants (triggers, rewards) will fail silently if dependencies are missing.
linear_progression = false
```

### Enforcement
```toml
[enforcement]
block_item_use = true
block_item_pickup = true
block_item_inventory = true
inventory_scan_frequency = 20
block_crafting = true
hide_locked_recipe_output = true
block_block_placement = true
block_block_interaction = true
block_dimension_travel = true
block_locked_mods = true
block_interactions = true
block_entity_attack = true  # Block attacking locked entity types

# Bypass checks when player is in creative mode
allow_creative_bypass = true

# Optional identity hiding
mask_locked_item_names = true

# Chat spam protection
notification_cooldown = 3000
show_lock_message = true

# Sound feedback
play_lock_sound = true
lock_sound = "minecraft:block.note_block.pling"
lock_sound_volume = 1.0
lock_sound_pitch = 1.0
```

### EMI
```toml
[emi]
enabled = true
show_lock_icon = true
lock_icon_position = "top_left"
lock_icon_size = 8
show_highlight = true
highlight_color = "0x50FFAA40"
show_tooltip = true

# If false: hide locked stacks from EMI index/search
# If true: show them (with lock icon/overlays depending on context)
show_locked_recipes = false
```

### Integration: FTB Quests
```toml
[integration.ftbquests]
enabled = true
recheck_budget_per_tick = 10
```

---

## Commands

### Stage Commands
| Command | Permission | Description |
|---------|------------|-------------|
| `/stage grant <player> <stage>` | OP | Grant a stage |
| `/stage revoke <player> <stage>` | OP | Revoke a stage |
| `/stage list [player]` | OP | List stages |
| `/stage check <player> <stage>` | OP | Check if player has stage |
| `/stage info <stage>` | OP | Show stage definition info |

### Admin Commands
| Command | Permission | Description |
|---------|------------|-------------|
| `/progressivestages reload` | OP | Reload stage + trigger configs |
| `/progressivestages validate` | OP | Validate stage files (syntax + IDs) |
| `/progressivestages ftb status [player]` | OP | Debug FTB integration |
| `/progressivestages trigger reset <player> <dimension|boss> <key>` | OP | Reset one-time trigger |

### FTB Integration Status Command
`/progressivestages ftb status [player]` prints helpful diagnostics:
- whether integration is enabled in config
- whether ProgressiveStages registered as the StageHelper provider
- whether compat is active
- pending rechecks count and budget
- whether a previous provider was stored (dev/debug)
- for a player: stage count + list + whether a recheck is currently in progress

---

## Trigger System

Triggers grant stages based on events (no per-tick polling required for triggers).

### File
```
config/ProgressiveStages/triggers.toml
```

### Stage IDs in triggers
Both are equivalent:
```toml
[advancements]
"minecraft:story/mine_stone" = "stone_age"
"minecraft:story/smelt_iron" = "progressivestages:iron_age"
```

### Trigger Types

#### Advancements
```toml
[advancements]
"minecraft:story/mine_stone" = "stone_age"
"minecraft:story/mine_diamond" = "diamond_age"
```

#### Item Pickup
```toml
[items]
"minecraft:iron_ingot" = "iron_age"
"minecraft:diamond" = "diamond_age"
```
Also performs a **login inventory scan** to catch players who already have the item.

#### Dimension Entry (one-time)
```toml
[dimensions]
"minecraft:the_nether" = "nether_explorer"
```

#### Boss Kills (one-time)
```toml
[bosses]
"minecraft:ender_dragon" = "dragon_slayer"
```

### Trigger Persistence
Dimension and boss triggers are **one-time** per player:
- persisted in `world/data/progressivestages_triggers.dat` (anchored to overworld storage)
- survives restarts
- won’t re-trigger even if a stage is later revoked

Admin reset:
```
/progressivestages trigger reset <player> dimension minecraft:the_nether
/progressivestages trigger reset <player> boss minecraft:ender_dragon
```

---

## EMI Integration (Visual Feedback)

### What EMI shows
ProgressiveStages provides client-side lock feedback:
- Lock icon overlay on locked stacks (depending on UI context)
- Highlight overlay (configured) for locked recipe components
- Optional tooltip info

### Hiding vs showing locked content
Controlled by:
- `emi.show_locked_recipes`

Behavior:
- `false`: locked stacks are hidden from EMI index/search (so they don’t clutter discovery)
- `true`: locked stacks may appear, but are visually marked as locked

### Important note about “stage tags” in EMI
ProgressiveStages **does not** register custom tags via EMI’s registry API (EMI doesn’t support that).

If you want to search using `#progressivestages:...` style tags, that must come from the **NeoForge tag system** (datapack tags). If/when this mod generates tags, EMI will automatically pick them up.

---

## FTB Quests Integration

ProgressiveStages can act as the **StageHelper provider** used by FTB Quests Stage Tasks.

### How it works
1. ProgressiveStages registers as `StageHelper.INSTANCE` provider (FTB Library)
2. When a stage changes, ProgressiveStages queues a "stage recheck" for the player
3. FTB Quests `StageTask.checkStages(ServerPlayer)` is called (debounced + budgeted)

### Using Stage Rewards in FTB Quests
FTB Quests can grant ProgressiveStages stages as quest rewards:

1. Create a quest in FTB Quests
2. Add a **Stage Reward** to the quest
3. Set the stage ID (e.g., `iron_age` or `progressivestages:iron_age`)
4. When the player claims the reward, ProgressiveStages grants the stage

**Note:** Stage IDs are normalized automatically. Both `iron_age` and `progressivestages:iron_age` work.

### Hiding Quest Chapters/Pages Behind Stages

#### Method 1: Native "Stage Required" Field (Recommended)
ProgressiveStages adds a **"Stage Required"** field directly to Quest and Chapter properties via mixins:

1. Open FTB Quests in **Edit Mode**
2. Select a **Quest** or **Chapter**
3. Right-click → **Properties**
4. Find the **Visibility** section
5. Enter a stage ID in **"Stage Required"** (e.g., `diamond_age` or `progressivestages:iron_age`)
6. Leave empty for no requirement

**How it works:**
- If the field is empty or blank, the quest/chapter is visible to all
- If a stage ID is set, the quest/chapter is hidden until the player unlocks that stage
- Stage IDs are normalized automatically (handles case differences, extra whitespace)
- Data is saved/synced with FTB Quests' normal save system

**Note:** This requires FTB Quests to be installed. The mixins only load when FTB Quests is present.

#### Method 2: Stage Task Dependency (Alternative)
For more complex requirements, use FTB Quests' native Stage Task:

1. Create a hidden "gate quest" with a Stage Task
2. Make the locked quest/chapter depend on the gate quest

This method works for requiring multiple stages or combining with other task types.

### Performance guardrails
- Coalesces multiple stage changes into 1 recheck per player per tick
- Recheck budget: `integration.ftbquests.recheck_budget_per_tick`
- Re-entrancy guard prevents recursive loops (quest reward → stage grant → recheck)

### Soft-disable behavior
If compatibility breaks (FTB API changes), ProgressiveStages:
- logs a clear error
- keeps core locks/EMI working
- only FTB stage-task integration is affected

---

## Enforcement System (Locks)

### Item enforcement
Depending on config, ProgressiveStages can:
- block item use
- block pickup
- block holding in inventory (drop)
- block crafting

### Block enforcement
Depending on config, ProgressiveStages can:
- block placement
- block interaction

### Creative bypass
If `enforcement.allow_creative_bypass = true`, creative mode players bypass enforcement.

### Mask locked item names
If `enforcement.mask_locked_item_names = true`, locked items show as **"Unknown Item"** to the client.

---

## Networking & Client Caches

ProgressiveStages syncs:
- stage state → `ClientStageCache`
- lock registry → `ClientLockCache`

Sync strategy:
- snapshot on join
- deltas on grant/revoke

---

## Public API (Mod Integration)

Package:
```
com.enviouse.progressivestages.common.api
```

### ProgressiveStagesAPI
```java
boolean has = ProgressiveStagesAPI.hasStage(player, StageId.parse("iron_age"));
ProgressiveStagesAPI.grantStage(player, StageId.parse("diamond_age"), StageCause.QUEST_REWARD);
ProgressiveStagesAPI.revokeStage(player, StageId.parse("stone_age"), StageCause.COMMAND);
Set<StageId> stages = ProgressiveStagesAPI.getStages(player);
```

### Events
```java
@SubscribeEvent
public static void onStageChanged(StageChangeEvent event) {
    // event.getPlayer(), event.getStageId(), event.getChangeType(), event.getCause()
}
```

---

## Troubleshooting & Support

### Build / Startup
- If you see compile errors around EMI tag APIs: this mod does not (and should not) call non-existent EMI registry methods.
- If you update FTB mods and stage tasks stop updating: run `/progressivestages ftb status` to inspect provider registration and compat.

### Stage Required field not appearing (FTB Quests)
**Problem:** The "Stage Required" field doesn't show in quest/chapter properties.

**Cause:** FTB Quests isn't installed, or the optional ProgressiveStages FTB Quests mixins failed to load.

**Solution:**
1. Verify FTB Quests is installed (check mods list for `ftb-quests`)
2. Check client logs for mixin errors mentioning `progressivestages-ftbquests.mixins.json`
3. Verify your FTB Quests version is compatible (tested with `ftb-quests-neoforge-2101.1.21`)

**Workaround:** Use a Stage Task dependency instead.

### EMI issues
**Locked items not hiding:**
- Set `emi.show_locked_recipes = false`
- Re-open inventory to trigger EMI refresh

**Lock icon missing:**
- Ensure `emi.show_lock_icon = true`
- Confirm texture exists: `assets/progressivestages/textures/gui/lock_icon.png`

### Triggers don't fire
- Confirm the stage ID exists (use `/stage info <stage>`)
- Confirm triggers.toml formatting
- For dimensions/bosses: if it already triggered once, use `/progressivestages trigger reset ...`

### Reload behavior
**What `/progressivestages reload` does:**
- Reloads stage files (*.toml) from config/ProgressiveStages/
- Reloads triggers.toml
- Re-syncs lock data to all online clients
- Triggers EMI refresh on clients

**What it does NOT do:**
- Clear one-time trigger history (dimension/boss triggers that already fired)
- To reset triggers, use `/progressivestages trigger reset <player> <type> <key>`

### Known limitations
- **Stage Required field requires FTB Quests:** If you open the game without FTB Quests installed, the field won't exist and any saved requirement is effectively ignored.
- **Name pattern lock performance:** `names = ["diamond"]` style locks require iterating all registered items to resolve matches. In very large modpacks (10k+ items), this can add ~50-100ms during server lock resolution. Prefer exact item IDs when possible.

### FTB Quests stage tasks not completing
- Run `/progressivestages ftb status <player>`
- Verify:
  - Config Enabled: YES
  - Provider Registered: YES
  - Compat Active: YES

---

## File/Package Map (High-level)

- `common/config/StageConfig.java` – config keys + cached values
- `common/stage/StageManager.java` – server-authoritative stage storage + events + sync
- `common/lock/LockRegistry.java` – resolved locks mapping item/block/mod → stage
- `server/loader/StageFileLoader.java` – loads stage TOML files from config folder
- `server/triggers/*` – trigger handlers + persistence
- `client/emi/*` – EMI integration
- `compat/ftbquests/*` – FTB integration adapter + recheck scheduling
