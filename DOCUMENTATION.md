# ProgressiveStages 2.0 — Complete Documentation

> ProgressiveStages **2.0** for NeoForge 1.21.1, Java 21.  
> Mod id: `progressivestages`  Java package root: `com.enviouse.progressivestages`  
> This document is exhaustive — every feature, every TOML field, every config key,
> every command, every integration, every troubleshooting tip. If a section of
> this document conflicts with what the mod actually does, the *code* is the
> authority and this document is a bug.

---

## Table of Contents

1. [What ProgressiveStages Is](#1-what-progressivestages-is)
2. [Core Concepts](#2-core-concepts)
3. [Quick Start — Your First Stage in 90 Seconds](#3-quick-start--your-first-stage-in-90-seconds)
4. [Stage Files — The Unified TOML Schema](#4-stage-files--the-unified-toml-schema)
   - [4.1 The Prefix System](#41-the-prefix-system)
   - [4.2 `[stage]` — identity + dependencies](#42-stage--identity--dependencies)
   - [4.3 `[items]` — use, pickup, inventory holding](#43-items--use-pickup-inventory-holding)
   - [4.4 `[blocks]` — placement and interaction](#44-blocks--placement-and-interaction)
   - [4.5 `[fluids]` — buckets, placement, submersion, recipe-viewer](#45-fluids--buckets-placement-submersion-recipe-viewer)
   - [4.6 `[recipes]` — two flavors of crafting gate](#46-recipes--two-flavors-of-crafting-gate)
   - [4.7 `[crops]` — planting, growth, bonemeal, harvest](#47-crops--planting-growth-bonemeal-harvest)
   - [4.8 `[dimensions]` — portal and teleport gating](#48-dimensions--portal-and-teleport-gating)
   - [4.9 `[enchants]` — table, anvil, villager, inventory strip](#49-enchants--table-anvil-villager-inventory-strip)
   - [4.10 `[entities]` — attack and interact gating](#410-entities--attack-and-interact-gating)
   - [4.11 `[[interactions]]` — fine-grained "X-on-Y" rules](#411-interactions--fine-grained-x-on-y-rules)
   - [4.12 `[loot]` — chest / fishing / archeology / mob / block drop filter](#412-loot--chest--fishing--archeology--mob--block-drop-filter)
   - [4.13 `[mobs]` — spawn gating and dynamic replacement](#413-mobs--spawn-gating-and-dynamic-replacement)
   - [4.14 `[pets]` — taming, breeding, commanding, riding](#414-pets--taming-breeding-commanding-riding)
   - [4.15 `[screens]` — block / item GUI gating](#415-screens--block--item-gui-gating)
   - [4.16 `[structures]` — entry, rules, chest locking](#416-structures--entry-rules-chest-locking)
   - [4.17 `[[regions]]` — fixed 3D boxes with flags + debuffs](#417-regions--fixed-3d-boxes-with-flags--debuffs)
   - [4.18 `[curios]` — per-slot gating when Curios is installed](#418-curios--per-slot-gating-when-curios-is-installed)
   - [4.19 `[[ores.overrides]]` — parsed but deferred post-2.0](#419-oresoverrides--parsed-but-deferred-post-20)
   - [4.20 `[unlocks]` — per-stage carve-outs](#420-unlocks--per-stage-carve-outs)
   - [4.21 `[enforcement]` — per-stage exemptions](#421-enforcement--per-stage-exemptions)
5. [Triggers — Automatic Stage Grants](#5-triggers--automatic-stage-grants)
   - [5.1 Single-trigger sections](#51-single-trigger-sections)
   - [5.2 `[[multi]]` — multi-trigger requirements (NEW IN 2.0)](#52-multi--multi-trigger-requirements-new-in-20)
6. [Global Configuration — `progressivestages.toml`](#6-global-configuration--progressivestagestoml)
7. [Commands](#7-commands)
8. [EMI / JEI Integration](#8-emi--jei-integration)
9. [FTB Quests Integration](#9-ftb-quests-integration)
10. [FTB Teams Integration](#10-ftb-teams-integration)
11. [KubeJS Integration](#11-kubejs-integration)
12. [Lootr Integration](#12-lootr-integration)
13. [Curios Integration](#13-curios-integration)
14. [Other Compatibility Shims](#14-other-compatibility-shims)
15. [Public API (Java)](#15-public-api-java)
16. [Networking & Client Caches](#16-networking--client-caches)
17. [Troubleshooting](#17-troubleshooting)
18. [File / Package Map](#18-file--package-map)

---

## 1. What ProgressiveStages Is

ProgressiveStages 2.0 is a **stage-based progression and content-locking framework**
for NeoForge 1.21.1 modpacks. Pack authors define **stages** (named milestones such
as `stone_age`, `iron_age`, `nether_explorer`) in TOML files. Each stage carries:

- A set of **locks** — content that is unavailable to a player until that player
  (or their team) has the stage. Locks span items, blocks, fluids, recipes, crops,
  enchantments, dimensions, mobs (attack + spawn + replacement), interactions
  (Create-style item-on-block / item-on-entity), loot drops, screens (GUIs),
  pet interactions, regions (3D boxes), and structures (vanilla + modded
  generated structures).
- An optional list of **dependencies** — other stages that must be unlocked
  before this one can be granted.
- Optional **display metadata** (display name, description, icon, unlock
  broadcast message).

Players gain stages through:

- Admin command (`/stage grant`)
- Automatic triggers (advancement, item pickup, dimension entry, boss kill)
- **Multi-trigger requirements** (NEW in 2.0 — see §5.2) where MULTIPLE
  sub-conditions must all fire before the stage is granted
- FTB Quests rewards
- KubeJS scripts
- Starting-stage list (auto-granted on first join)
- Public Java API (mod integration)

Stages are **server-authoritative**: the server stores the truth, the client
caches it for visual feedback (lock icons in EMI/JEI, locked-item tooltips,
masked names). Stages can be **per-player** (solo mode) or **per-team** (FTB
Teams mode); the choice is a single `general.team_mode` config flag.

Everything is **multiplayer-first**: locks sync to clients, EMI/JEI refresh on
stage change, FTB Quests stage-tasks update instantly, FTB Teams stages are
shared inside teams.

---

## 2. Core Concepts

### 2.1 Stages and Stage IDs

A **stage** is an identifier with the same shape as a Minecraft resource
location: `namespace:path`. Path may be hierarchical (`tech/iron_age`). All
stage IDs are **normalized** when loaded:

| Rule | Example |
|------|---------|
| Whitespace trimmed | `"  iron_age  "` → `"iron_age"` |
| Lowercased | `"Diamond_Age"` → `"diamond_age"` |
| Default namespace added | `"iron_age"` → `"progressivestages:iron_age"` |
| Namespace preserved | `"mymod:my_stage"` → `"mymod:my_stage"` |
| Hierarchical paths allowed | `"tech/iron_age"` → `"progressivestages:tech/iron_age"` |

**Allowed characters** (aligned with Minecraft / NeoForge resource IDs):

- **Namespace:** `a-z`, `0-9`, `_`, `-`, `.`
- **Path:** `a-z`, `0-9`, `_`, `-`, `.`, `/`

Disallowed (will throw on parse):

- `Iron Age` (spaces)
- `Stage#1` (special chars)
- `/iron_age` or `iron_age/` (leading/trailing slash)
- `tech//iron_age` (double slash)

The wrapper class is [`com.enviouse.progressivestages.common.api.StageId`](src/main/java/com/enviouse/progressivestages/common/api/StageId.java).
Most APIs accept either `StageId` instances or raw strings (parsed via
`StageId.parse(...)`) — the latter triggers normalization automatically, so
`"iron_age"` and `"progressivestages:iron_age"` and `"  IRON_AGE  "` are all
interchangeable in commands, config, and code.

### 2.2 Stage Storage — Solo vs. FTB Teams

ProgressiveStages stores stage state at the **team** level. In solo mode each
player IS their own team (single-member). In FTB Teams mode the stage set is
shared across every member of the FTB team. This is controlled by
`general.team_mode` in `config/progressivestages.toml`:

- `"ftb_teams"` (default) — uses FTB Teams' team membership. Falls back to
  solo automatically if FTB Teams is not installed.
- `"solo"` — every player is their own team.

The storage is implemented via NeoForge **data attachments** keyed to the
overworld's level data (`world/data/progressivestages_*.dat`). The attachment
type is registered in
[`StageAttachments`](src/main/java/com/enviouse/progressivestages/common/data/StageAttachments.java)
and the team-keyed map lives in
[`TeamStageData`](src/main/java/com/enviouse/progressivestages/common/data/TeamStageData.java).

### 2.3 Stages, Dependencies, and Progression

Each stage may declare zero, one, or many **dependencies** — stages that must
be unlocked first. Dependencies form an arbitrary DAG (not a strict linear
chain). The mod validates the dependency graph at load time and warns about
missing or circular references.

Two progression modes are available, set by `general.linear_progression`:

- **`linear_progression = false`** (default) — granting a stage WITHOUT its
  dependencies fails (silently for triggers/rewards, with a confirmation prompt
  for admin commands). Players must satisfy dependencies in order.
- **`linear_progression = true`** — granting a stage AUTO-GRANTS its full
  dependency chain. Useful for "skip ahead to X" admin operations or for packs
  that use dependencies purely as documentation rather than enforcement.

Admin dependency bypass: with linear progression off, running `/stage grant`
on a stage with missing dependencies prompts a second confirmation:

```
/stage grant Player diamond_age
→ Cannot grant diamond_age: Player is missing dependencies: iron_age
→ Type the command again within 10 seconds to bypass.

/stage grant Player diamond_age   (again, within 10s)
→ Granted stage diamond_age to Player (dependency bypass)
```

### 2.4 Stage File Location

Stage TOMLs live in the **global config directory** (NOT per-world):

```
config/ProgressiveStages/<stage>.toml
config/ProgressiveStages/triggers.toml
config/progressivestages.toml         (global mod config — note: outside ProgressiveStages/)
```

This means stage definitions are **shared across all worlds** on the same
server/instance. Per-world stage state lives inside each world's saved data.

Three default stage files (`stone_age.toml`, `iron_age.toml`, `diamond_age.toml`)
plus `triggers.toml` are generated on first launch if the directory is empty.
The `diamond_age.toml` template is the canonical 2.0 reference file — every
category is shown with inline documentation.

---

## 3. Quick Start — Your First Stage in 90 Seconds

1. Launch the game once with ProgressiveStages installed. The default stage
   files (`stone_age.toml`, `iron_age.toml`, `diamond_age.toml`) plus
   `triggers.toml` appear in `config/ProgressiveStages/`.

2. Open `config/ProgressiveStages/iron_age.toml`. The minimum useful 2.0 stage
   file looks like this:

   ```toml
   [stage]
   id = "iron_age"
   display_name = "Iron Age"
   description = "Iron tools, armor, and basic machinery"
   icon = "minecraft:iron_pickaxe"
   unlock_message = "&6&lIron Age Unlocked!"
   dependency = "stone_age"          # optional — list of stages required first

   [items]
   locked = [
       "minecraft:iron_pickaxe",
       "minecraft:iron_sword",
       "tag:c:ingots/iron",
   ]
   always_unlocked = []              # exceptions — exact IDs only

   [blocks]
   locked = [
       "minecraft:iron_block",
   ]
   ```

3. In `triggers.toml`, point an item-pickup or advancement at the stage:

   ```toml
   [items]
   "minecraft:iron_ingot" = "iron_age"

   [advancements]
   "minecraft:story/smelt_iron" = "iron_age"
   ```

4. Either restart the server or run `/progressivestages reload` in-game.

5. Verify:
   - `/stage info iron_age` — prints the parsed definition.
   - `/stage list <player>` — shows which stages the player has.
   - Pick up an iron ingot. The stage is granted; the unlock message broadcasts;
     locks for `iron_pickaxe`, `iron_sword`, `iron_block` lift instantly.

That's the entire core loop. Sections 4 and 5 below cover every category and
trigger in depth.

---

## 4. Stage Files — The Unified TOML Schema

### 4.1 The Prefix System

ProgressiveStages 2.0 uses a **unified prefix system** for every locked list.
Instead of separate arrays for IDs / tags / mods (the 1.x design), every
`locked = [...]` list accepts **four prefixes**:

| Prefix | Meaning | Example |
|--------|---------|---------|
| `id:<namespace:path>` | Exact registry ID match | `id:minecraft:diamond` |
| *(no prefix)* | Implicit `id:` for `namespace:path` strings | `minecraft:diamond` |
| `mod:<namespace>` | Every registry entry whose namespace equals this | `mod:ae2` |
| `tag:<namespace:path>` | Every registry entry in this tag | `tag:minecraft:logs` |
| `name:<substring>` | Case-insensitive substring of the full ID | `name:netherite` |
| `#<namespace:path>` | Legacy tag shorthand (same as `tag:`) | `#minecraft:crops` |

Each category also has `always_unlocked = [...]` — an **ID-only** whitelist that
exempts specific entries. `mod:` / `tag:` / `name:` are intentionally rejected
inside `always_unlocked` because broad exemptions defeat the lock's purpose.

**Evaluation order inside a single category:**

1. Is the element ID in `always_unlocked`? → **UNLOCKED.**
2. Does ANY prefix in `locked` match the element? → **LOCKED at this stage.**
3. Otherwise → **UNLOCKED.**

**Cross-category interactions:** if the same registry element appears locked in
two categories (e.g. an item id in both `[items]` and `[screens]`), BOTH
enforcement paths apply — item use is blocked AND opening its GUI is blocked.

**Cross-stage interactions:** an element is "locked for the player" if at
least one stage gates it AND the player lacks every gating stage. The
[`LockRegistry`](src/main/java/com/enviouse/progressivestages/common/lock/LockRegistry.java)
collapses all stage definitions into a single resolved map keyed by registry
ID, with the set of stages that gate it.

The parser for prefix entries is
[`PrefixEntry`](src/main/java/com/enviouse/progressivestages/common/lock/PrefixEntry.java) —
inspect it for the exact parsing rules and the `Kind` enum (`ID`, `MOD`, `TAG`,
`NAME`).

### 4.2 `[stage]` — identity + dependencies

```toml
[stage]
id = "diamond_age"
display_name = "Diamond Age"
description = "Diamond tools, advanced farming, and new dimensions await."
icon = "minecraft:diamond_pickaxe"
unlock_message = "&b&lDiamond Age Unlocked! &r&7You have ascended."

# Prerequisite stage(s). Can be a single string OR a list. Omit for starting stages.
dependency = "iron_age"
# dependency = ["iron_age", "nether_explorer"]   # multiple prerequisites
```

| Field | Required? | Notes |
|-------|-----------|-------|
| `id` | Recommended | If omitted, the filename (minus `.toml`) is used. Normalized per §2.1. |
| `display_name` | Optional | Free text, supports `&`-color codes when rendered. Defaults to `id`. |
| `description` | Optional | Free text shown by `/stage info` and FTB Quests. |
| `icon` | Optional | An item ID rendered next to the stage name (lock icon overlay, tooltip, etc.). |
| `unlock_message` | Optional | Broadcast to every team member when the stage is granted. Supports `&`-codes. |
| `dependency` | Optional | One string OR a list of strings. Stages must be unlocked first. |

### 4.3 `[items]` — use, pickup, inventory holding

```toml
[items]
locked = [
    "id:minecraft:diamond_pickaxe",
    "tag:c:gems/diamond",
    "mod:ae2",
    "name:netherite",
]
always_unlocked = [
    "id:minecraft:diamond_horse_armor",    # exception, only exact IDs accepted
]
```

A locked item cannot be:

- **Right-clicked to use** — `PlayerInteractEvent.RightClickItem` and
  `LivingEntityUseItemEvent.Start` are cancelled.
- **Left-clicked / used for mining** — `PlayerInteractEvent.LeftClickBlock`
  is cancelled if the held tool is locked.
- **Picked up from the ground** — `ItemEntityPickupEvent.Pre` is cancelled
  (with a configurable chat-spam cooldown).
- **Crafted** — `PlayerEvent.ItemCraftedEvent` clears the output stack;
  `ResultSlotMixin` also gates the result slot before the click takes effect.
- **Held in the hotbar** — if `enforcement.block_item_hotbar = true`, the
  periodic inventory scanner moves locked items from hotbar slots into main
  inventory (dropped if main inventory is full).
- **Picked up with the mouse in GUIs** — if `enforcement.block_item_mouse_pickup
  = true`, `AbstractContainerMenuMixin` refuses click-pickup of locked items.
- **Held in the main inventory** — if `enforcement.block_item_inventory =
  true`, the periodic scanner auto-drops locked items.

Each of those enforcement paths has a corresponding global toggle in
`progressivestages.toml` plus a per-stage `[enforcement]` exemption — see §4.21
and §6 for the toggle list.

The implementation lives in
[`ItemEnforcer`](src/main/java/com/enviouse/progressivestages/server/enforcement/ItemEnforcer.java)
and [`InventoryScanner`](src/main/java/com/enviouse/progressivestages/server/enforcement/InventoryScanner.java).

### 4.4 `[blocks]` — placement and interaction

```toml
[blocks]
locked = [
    "id:minecraft:enchanting_table",
    "tag:minecraft:beacon_base_blocks",
    "mod:create",
]
always_unlocked = []
```

A locked block cannot be:

- **Placed** — `BlockEvent.EntityPlaceEvent` cancelled.
- **Right-clicked** — `PlayerInteractEvent.RightClickBlock` cancelled (this
  also indirectly blocks any GUI the block would have opened; see §4.15 for
  the dedicated `[screens]` category for finer GUI control).

If `enforcement.block_block_placement = false` globally, no block in any stage
is placement-gated. Likewise for `enforcement.block_block_interaction`.

The implementation lives in
[`BlockEnforcer`](src/main/java/com/enviouse/progressivestages/server/enforcement/BlockEnforcer.java).
The enforcer is **state-aware** (it queries the block state, not just the
block) so it correctly classifies Visual Workbench replacement blocks and
similar shim systems — see [`VisualWorkbenchShim`](src/main/java/com/enviouse/progressivestages/compat/visualworkbench/VisualWorkbenchShim.java).

### 4.5 `[fluids]` — buckets, placement, submersion, recipe-viewer

```toml
[fluids]
locked = [
    "id:minecraft:lava",
    "mod:mekanism",
    "tag:c:acids",
]
always_unlocked = [
    "id:mekanism:hydrogen",
]
```

A locked fluid has THREE in-world enforcement paths plus a recipe-viewer path:

1. **Bucket pickup** — right-clicking a locked source block with an empty
   bucket is refused (`RightClickBlock` event cancelled when the held item is
   a bucket and the target tile is a locked fluid source).
2. **Bucket placement / flow** — `BlockEvent.FluidPlaceBlockEvent` cancelled,
   so emptying a bucket of a locked fluid or letting it flow into open blocks
   is prevented.
3. **Submersion debuffs** — every tick a player is in a locked fluid block,
   they get Slowness II + Blindness (applied by
   [`FluidEnforcer.applySubmersionEffects`](src/main/java/com/enviouse/progressivestages/server/enforcement/FluidEnforcer.java)
   from the player tick). Effectively unable to swim through.
4. **EMI / JEI hide** — locked fluids disappear from the recipe browser when
   `emi.show_locked_recipes = false`.

**Important caveat:** machine-internal fluid transport (pipes, tanks, modded
pumps) does NOT fire vanilla events, so ProgressiveStages cannot intercept it.
The workaround is to lock the SOURCE machine in `[blocks]` or `[screens]`
instead — players who can't open / configure the machine can't pull or push
the fluid through it.

### 4.6 `[recipes]` — two flavors of crafting gate

```toml
[recipes]
locked_ids = [
    "id:minecraft:diamond_chestplate_from_smithing",
    "mod:createaddition",
]
locked_items = [
    "id:minecraft:diamond_chestplate",
    "id:minecraft:diamond_sword",
]
```

Two independent lists:

- **`locked_ids`** — blocks ONE specific recipe by its registry ID. Useful when
  the same output has multiple recipes and you only want to gate a particular
  path (e.g. the smithing path but not the crafting-table path).
- **`locked_items`** — blocks EVERY recipe whose output is the listed item.
  The item itself remains fully usable — players can pick it up from loot,
  hold it, and use it — but no craft anywhere produces it (crafting table,
  mechanical crafter, autocrafter).

Both lists use the prefix system. Hidden side effect: locking an item in
`[items]` also implicitly locks every recipe that produces it (because the
output stack itself is locked).

EMI / JEI show the locked recipe with the configurable overlay (see §8); when
`emi.show_locked_recipes = false`, the recipe disappears from the browser
entirely.

The enforcer is
[`RecipeEnforcer`](src/main/java/com/enviouse/progressivestages/server/enforcement/RecipeEnforcer.java).
The mixin gating the crafting result slot is
[`ResultSlotMixin`](src/main/java/com/enviouse/progressivestages/mixin/ResultSlotMixin.java);
the recipe-id capture for the `ItemCraftedEvent` fallback path is
[`CraftingMenuMixin`](src/main/java/com/enviouse/progressivestages/mixin/CraftingMenuMixin.java).

### 4.7 `[crops]` — planting, growth, bonemeal, harvest

```toml
[crops]
locked = [
    "tag:minecraft:crops",
    "mod:croptopia",
    "id:minecraft:wheat",
]
always_unlocked = [
    "id:minecraft:carrots",
]
```

Four independent enforcement surfaces — all triggered by the crop block being
present in the `locked` list:

| Surface | Event | Behavior |
|---------|-------|----------|
| Planting | `BlockEvent.EntityPlaceEvent` | Cancelled. The seed item is returned to the player. |
| Growth | `CropGrowEvent.Pre` | If the **nearest player** within `enforcement.mob_spawn_check_radius` lacks the stage, the result is set to `DO_NOT_GROW`. |
| Bonemeal | `BonemealEvent` | Cancelled. The bonemeal item is NOT consumed. |
| Harvest | `BlockDropsEvent` | Keeps only items whose registry path contains `"seed"` (e.g. `wheat_seeds` but NOT `wheat`). |

Crops are blocks, so `tag:` in this section uses **block tags**, not item tags.

The implementation is
[`CropEnforcer`](src/main/java/com/enviouse/progressivestages/server/enforcement/CropEnforcer.java).
Note the same nearest-player pattern used for mob spawns: if no player is in
range, growth is allowed because nobody is there to see it.

### 4.8 `[dimensions]` — portal and teleport gating

```toml
[dimensions]
locked = [
    "id:minecraft:the_end",
    "id:minecraft:the_nether",
    "id:twilightforest:twilight_forest",
]
```

Dimensions only accept **exact IDs** — a dimension is a single registry value,
not a class of values, so `mod:` / `tag:` / `name:` would be meaningless.

Enforcement runs at THREE layers (each one a safety net for the layer above):

1. **`EntityTravelToDimensionEvent`** — fires BEFORE the dimension change.
   Cancels travel for vanilla portals and most modded teleporters.
2. **`PlayerEvent.PlayerChangedDimensionEvent`** — fires AFTER the dimension
   change, used as a safety net for mods (e.g. Twilight Forest) that bypass
   the pre-travel event. The player is teleported back to their last
   pre-travel position.
3. **Per-second player tick check** — if the player is somehow still in a
   locked dimension (revoked stage while offline, edge case), they are
   teleported to the overworld (or any other unlocked dimension if the
   overworld itself is locked).

In addition, when **Nature's Compass** is installed, the compass item is
gated automatically when any dimension is locked for the player —
[`NaturesCompassCompat`](src/main/java/com/enviouse/progressivestages/compat/naturescompass/NaturesCompassCompat.java)
registers the compass against the player's item-use checks.

The implementation is
[`DimensionEnforcer`](src/main/java/com/enviouse/progressivestages/server/enforcement/DimensionEnforcer.java).

### 4.9 `[enchants]` — table, anvil, villager, inventory strip

```toml
[enchants]
locked = [
    "id:minecraft:mending",
    "id:minecraft:infinity",
    "tag:c:curse",
    "mod:apotheosis",
]
```

The most ambitious category — every surface where an enchantment can reach a
player's gear is gated:

- **Enchanting Table** — `EnchantmentMenuMixin` filters the preview clues.
  Locked enchantments do not appear in the three preview slots; the apply
  button refuses if the primary pick is locked. XP / lapis are NOT consumed
  on refusal.
- **Anvil** — `AnvilUpdateEvent` and `AnvilMenuMixin`. If either input
  (the target item OR the enchantment source) carries a locked enchantment,
  the output is refused.
- **Villager trades** — `ServerPlayerMerchantMixin` filters merchant offers
  server-side before the offer list is sent to the client. Librarian books
  with locked enchantments simply do not appear.
- **Inventory strip** — every periodic inventory scan rewrites the
  `ItemEnchantments` / `StoredEnchantments` data components, stripping any
  locked entries. Catches loot drops, fishing, dungeon chests, etc.
- **Curios slots** — if Curios is installed, items inside curio slots also
  get scrubbed (see §13).

The implementation is
[`EnchantEnforcer`](src/main/java/com/enviouse/progressivestages/server/enforcement/EnchantEnforcer.java).
Mixin entry points: [`EnchantmentMenuMixin`](src/main/java/com/enviouse/progressivestages/mixin/EnchantmentMenuMixin.java),
[`AnvilMenuMixin`](src/main/java/com/enviouse/progressivestages/mixin/AnvilMenuMixin.java),
[`ServerPlayerMerchantMixin`](src/main/java/com/enviouse/progressivestages/mixin/ServerPlayerMerchantMixin.java).

**Known limitation:** the enchanting-table preview is generated from secondary
enchantments via `getEnchantmentList()`. Edge cases can occasionally let a
locked enchant slip into the preview; the inventory-strip pass removes it
within one tick of being applied. Players cannot keep a locked enchant, but
they may briefly see one in the preview.

### 4.10 `[entities]` — attack and interact gating

```toml
[entities]
locked = [
    "id:minecraft:warden",
    "tag:minecraft:raiders",
    "mod:alexsmobs",
]
always_unlocked = [
    "id:alexsmobs:capuchin_monkey",
]
```

This category gates the player's **direct interaction** with specific entity
types — primarily **attacking**. The entity still spawns, exists, can attack
the player, etc., but the player's `AttackEntityEvent` is cancelled.

To gate **spawning**, use `[mobs]` (§4.13) instead.

The implementation is
[`EntityEnforcer`](src/main/java/com/enviouse/progressivestages/server/enforcement/EntityEnforcer.java).

### 4.11 `[[interactions]]` — fine-grained "X-on-Y" rules

This is a **table-array** rather than a single table, so you can write as many
entries as you want, each describing one specific interaction to gate.

```toml
[[interactions]]
type = "block_right_click"
target_block = "minecraft:enchanting_table"
description = "Use Enchanting Table"

[[interactions]]
type = "item_on_block"
held_item = "create:andesite_alloy"
target_block = "#minecraft:logs"
description = "Create Andesite Casing"

[[interactions]]
type = "item_on_entity"
held_item = "minecraft:name_tag"
target_entity = "minecraft:zombie"
description = "Name a Zombie"
```

Three shapes:

| `type` | Required fields | Effect |
|--------|-----------------|--------|
| `block_right_click` | `target_block` | Cancel right-clicking the block (even empty-handed) |
| `item_on_block` | `held_item`, `target_block` | Cancel right-click when held item + target block match |
| `item_on_entity` | `held_item`, `target_entity` | Cancel right-click when held item + target entity match |

The `held_item` / `target_block` / `target_entity` fields accept **single
prefix entries** (e.g. `tag:minecraft:logs`, `mod:create`). The `description`
field is free text used in messages and `/stage info`.

The implementation is
[`InteractionEnforcer`](src/main/java/com/enviouse/progressivestages/server/enforcement/InteractionEnforcer.java).

### 4.12 `[loot]` — chest / fishing / archeology / mob / block drop filter

```toml
[loot]
locked = [
    "id:minecraft:diamond",
    "mod:artifacts",
    "tag:c:gems/diamond",
]
```

A single unified list filters **every loot table roll in the game** through a
**Global Loot Modifier**. Items in the locked list are silently removed from
the loot stack before the player sees them.

Sources covered automatically:

- Vanilla & modded chest loot
- Mob drops (via `LivingDropsEvent`)
- Block drops (via `BlockDropsEvent`)
- Fishing rolls
- Archeology brushing
- **Lootr** per-player rolls (filter chain integration; see §12)

**Player resolution** for "who is the loot for" (in order):

1. `LAST_DAMAGE_PLAYER` parameter — mob/block drops caused by a kill.
2. `THIS_ENTITY` parameter — chest opener.
3. Nearest player to `ORIGIN` within `enforcement.mob_spawn_check_radius`.

If no player is in range at all, the loot passes through unchanged. This
matches the nearest-player policy used by mob spawning.

The implementation:

- GLM: [`StageLootModifier`](src/main/java/com/enviouse/progressivestages/server/enforcement/StageLootModifier.java)
  registered via [`LootModifiers`](src/main/java/com/enviouse/progressivestages/common/LootModifiers.java)
- Drop filter (mob + block): [`LootEnforcer`](src/main/java/com/enviouse/progressivestages/server/enforcement/LootEnforcer.java)
- Lootr filter: [`LootrStageFilter`](src/main/java/com/enviouse/progressivestages/compat/lootr/LootrStageFilter.java)
  via the `ILootrFilterProvider` ServiceLoader.

### 4.13 `[mobs]` — spawn gating and dynamic replacement

```toml
[mobs]
locked_spawns = [
    "id:minecraft:creeper",
    "mod:borninchaos_v1",
    "tag:minecraft:raiders",
]

[[mobs.replacements]]
target = "id:minecraft:enderman"
replace_with = "id:minecraft:zombie"

[[mobs.replacements]]
target = "mod:alexsmobs"
replace_with = "id:minecraft:zombie"
```

Two independent surfaces:

#### `locked_spawns` — block the mob entirely

Fires at `FinalizeSpawnEvent`. If the nearest player within
`enforcement.mob_spawn_check_radius` lacks the gating stage, the spawn is
cancelled. Covers natural spawns, spawners, spawn eggs, and most modded spawn
paths (anything that goes through `Mob.finalizeSpawn`).

If **no player is in range**, the spawn is ALLOWED — nobody is there to see
the mob, so there's no reason to suppress it. Increasing `mob_spawn_check_radius`
makes the gate more aggressive (max 512 blocks).

**Boss gating:** add `id:minecraft:wither` or `id:minecraft:ender_dragon` to
`locked_spawns` — both fire `FinalizeSpawnEvent` so they're gated automatically.

Cancellation uses `setSpawnCancelled(true)`, NOT `setCanceled(true)` — the
latter only skips `finalizeSpawn` while the entity still gets added to the
world.

#### `[[mobs.replacements]]` — swap one mob for another

Instead of cancelling a spawn, swap in a different mob at the same coordinates.
The picked entry is the FIRST matching one. Replacement is validated via
`Mob.checkSpawnRules` + `checkSpawnObstruction` before spawning, so a water mob
replaced on land gracefully falls back to a plain cancel rather than a broken
despawn.

`target` accepts the full prefix system (you can swap out a whole mod's mobs).
`replace_with` is always a single exact entity ID.

The implementations:
- [`MobSpawnEnforcer`](src/main/java/com/enviouse/progressivestages/server/enforcement/MobSpawnEnforcer.java)
- [`MobReplacementEnforcer`](src/main/java/com/enviouse/progressivestages/server/enforcement/MobReplacementEnforcer.java)
- [`NearestPlayerCheck`](src/main/java/com/enviouse/progressivestages/server/enforcement/NearestPlayerCheck.java)

### 4.14 `[pets]` — taming, breeding, commanding, riding

```toml
[pets]
locked_taming = [
    "id:minecraft:wolf",
    "tag:c:tamable",
]
locked_breeding = [
    "id:minecraft:cow",
]
locked_commanding = [
    "id:minecraft:wolf",   # tame player can't tell their own wolf to sit
]
```

Four independent enforcement slots, picked automatically based on what the
player is trying to do:

| Slot | Triggered by | Cancels |
|------|--------------|---------|
| `locked_taming` | Right-click a wild tameable with its taming item | The tame attempt |
| `locked_breeding` | Feed an already-tame animal its breeding food | The breeding (love hearts) |
| `locked_commanding` | Right-click your own tame pet | Sit/stand/follow toggle |
| (Riding) | `EntityMountEvent` | Mounting onto the entity |

Riding has no list of its own — it uses whichever of the three slots above
matches the pet's current state. A `locked_taming` entry alone is enough to
block riding a wild horse.

**Fall-through order**: commanding → breeding → taming. If you only fill
`locked_taming`, all three scenarios use it as the gate. The implementation
is [`PetEnforcer`](src/main/java/com/enviouse/progressivestages/server/enforcement/PetEnforcer.java).

### 4.15 `[screens]` — block / item GUI gating

```toml
[screens]
locked = [
    "id:minecraft:crafting_table",
    "id:minecraft:anvil",
    "id:minecraft:ender_chest",
    "mod:create",                      # every Create block GUI
    "tag:minecraft:shulker_boxes",     # block tag — locked shulker BLOCKS
    "tag:c:shulker_boxes",             # item tag — locked HELD shulkers (item-GUI surface)
    "tag:lootr:chests",                # Lootr's published block tag
]
```

A **unified list** matched against BOTH:

- **Block IDs**, when the player right-clicks a block that opens a GUI
  (crafting tables, anvils, chests, modded machines).
- **Item IDs**, when the player right-clicks a held item that opens a GUI
  (backpacks, portable crafting tables, held shulker boxes).

One list, two surfaces. The enforcer figures out which surface fired based on
the event.

Works automatically for every container type — chest, trapped chest, barrel,
shulker, ender chest, hopper, dispenser, dropper, and modded containers that
extend the same base classes.

**Lootr users:** Lootr publishes block tags you can use directly:

| Tag | Locks |
|-----|-------|
| `tag:lootr:chests` | Every chest-shaped Lootr container |
| `tag:lootr:barrels` | Lootr barrels only |
| `tag:lootr:shulkers` | Lootr shulkers only |
| `tag:lootr:containers` | Every Lootr container |
| `tag:lootr:trapped_chests` | Lootr trapped chests |

The implementation is
[`ScreenEnforcer`](src/main/java/com/enviouse/progressivestages/server/enforcement/ScreenEnforcer.java).

### 4.16 `[structures]` — entry, rules, chest locking

```toml
[structures]
locked_entry = [
    "id:minecraft:ocean_monument",
    "id:minecraft:stronghold",
    "id:minecraft:ancient_city",
    "tag:minecraft:on_ocean_explorer_maps",
]

[structures.rules]
prevent_block_break = true
prevent_block_place = true
prevent_explosions = true
disable_mob_spawning = true
```

Two parts: the list of locked structure IDs (`locked_entry`) and a single
`[structures.rules]` table whose flags apply **across all listed structures**.

#### `locked_entry`

When a player enters the piece bounding box of any listed structure and lacks
the gating stage, they are teleported to the nearest outside edge. The check
runs every `enforcement.region_tick_frequency` ticks (default 20 = 1s). Piece
precision means players can't sneak in through a partial overlap — every
structure piece's bounding box is checked individually.

#### `[structures.rules]`

| Flag | Effect inside locked structures |
|------|------------------------------|
| `prevent_block_break` | `BlockEvent.BreakEvent` cancelled. Players also get Mining Fatigue V while inside (tactile feedback). |
| `prevent_block_place` | `BlockEvent.EntityPlaceEvent` cancelled. |
| `prevent_explosions` | Block positions inside locked structures are removed from the explosion's affected-blocks list. |
| `disable_mob_spawning` | `FinalizeSpawnEvent` cancelled for spawns inside the structure. |

#### Chest locking (always on, no flag needed)

**Right-click on any container inside a locked structure is always refused**
regardless of `prevent_block_break` / `prevent_block_place`. So is **breaking
the container**. This is the "you can't spill the loot by mining the chest"
guarantee — see §2.12 in the v2 update plan and
[`StructureEnforcer.isContainerAt`](src/main/java/com/enviouse/progressivestages/server/enforcement/StructureEnforcer.java).

Containers include:
- Vanilla: chest, trapped chest, barrel, shulker, ender chest, hopper, dispenser, dropper
- Modded: anything whose block entity implements `Container`
- Lootr: every Lootr block (chests, barrels, shulkers, trapped chests)
- Entity-based containers: lootr minecarts, item frames with loot

The implementation is
[`StructureEnforcer`](src/main/java/com/enviouse/progressivestages/server/enforcement/StructureEnforcer.java).

> **Status note:** the *indestructibility* and *explosion-immunity for
> structure architecture* aspects of §2.12 in the v2 update plan are **on
> hold** post-2.0 — they require structure bounding-box tracking per loaded
> chunk, which is its own engineering lift. Entry denial + chest locking + the
> four `[structures.rules]` flags ARE in scope and shipped.

### 4.17 `[[regions]]` — fixed 3D boxes with flags + debuffs

```toml
[[regions]]
dimension = "minecraft:overworld"
pos1 = [0, -64, 0]
pos2 = [1000, 320, 1000]
prevent_entry = true
prevent_block_break = false
prevent_block_place = false
prevent_explosions = true
disable_mob_spawning = false
```

A region is a hand-authored 3D box in a specific dimension. Define as many as
you want — each `[[regions]]` block is one box. Useful for spawn protection,
admin zones, story areas that gate open as stages unlock.

| Field | Type | Meaning |
|-------|------|---------|
| `dimension` | string (exact ID) | Which dimension the box lives in |
| `pos1` | `[x, y, z]` | One corner |
| `pos2` | `[x, y, z]` | Opposite corner (order doesn't matter) |
| `prevent_entry` | bool | Push players out + apply Slowness III + Blindness for 3s |
| `prevent_block_break` | bool | Cancel break events inside |
| `prevent_block_place` | bool | Cancel place events inside |
| `prevent_explosions` | bool | Filter affected-blocks list inside |
| `disable_mob_spawning` | bool | Cancel mob spawns inside |

Y range is clamped to the dimension's height at runtime. The entry check runs
every `enforcement.region_tick_frequency` ticks (shared with structures).

The implementation is
[`RegionEnforcer`](src/main/java/com/enviouse/progressivestages/server/enforcement/RegionEnforcer.java).

### 4.18 `[curios]` — per-slot gating when Curios is installed

```toml
[curios]
locked_slots = [
    "ring",
    "necklace",
    "curious_armor:artifact",
]
```

`locked_slots` is a **plain list of Curios slot identifiers** (as Curios
defines them — `"ring"`, `"necklace"`, `"belt"`, modded names, etc.). This
section is NOT prefix-parsed; the strings are literal slot names.

When Curios is loaded:

- A periodic scan ejects items from locked slots into the main inventory
  (or drops them on the ground if no slot fits).
- The same scan strips locked enchantments from curio-held stacks.

If Curios is NOT installed, this section is parsed and ignored — safe to
leave in place.

The implementation is
[`CuriosCompat`](src/main/java/com/enviouse/progressivestages/compat/curios/CuriosCompat.java).

### 4.19 `[[ores.overrides]]` — parsed but deferred post-2.0

```toml
# [[ores.overrides]]
# target = "id:minecraft:diamond_ore"
# display_as = "id:minecraft:stone"
# drop_as = "id:minecraft:cobblestone"
```

The TOML schema is parsed for forward compatibility, but **enforcement is on
hold post-2.0**. Visual masquerade requires client-side chunk packet
rewriting, which is scoped as a separate release. You can leave entries in
your stage files — they won't error, they just won't do anything yet.

Tracked in
[`OreOverride`](src/main/java/com/enviouse/progressivestages/common/lock/LockDefinition.java)
inside `LockDefinition.java` (search "OreOverride").

### 4.20 `[unlocks]` — per-stage carve-outs

```toml
[unlocks]
items = ["minecraft:diamond_horse_armor"]
mods = []
fluids = []
dimensions = []
entities = []
```

A small but powerful concept: when a stage gates content broadly (e.g.
`mod:ae2`), the **`[unlocks]`** table on the SAME stage carves out specific
exceptions that this stage's gating set DOES NOT cover. Other stages' gating
still applies — `[unlocks]` only affects the stage it's written on.

Format: per-category lists, ID-only (no prefix system here; this is an
exemption from a gating *set*, not a match against the locked list).

| Field | Carve-out from |
|-------|----------------|
| `items` | Item gating contributed by this stage |
| `mods` | Mod gating contributed by this stage |
| `fluids` | Fluid gating contributed by this stage |
| `dimensions` | Dimension gating contributed by this stage |
| `entities` | Entity gating contributed by this stage |

This is most often used alongside `[locks].minecraft = true` or
`mod:vanilla_namespace` — see the next subsection.

### 4.21 `[enforcement]` — per-stage exemptions

```toml
[enforcement]
allowed_use = [
    "minecraft:diamond_ore",
    "minecraft:deepslate_diamond_ore",
]
allowed_pickup = [
    "minecraft:diamond",
    "#c:gems/diamond",
]
allowed_hotbar = [
    "minecraft:diamond",
]
allowed_mouse_pickup = [
    "minecraft:diamond",
    "minecraft:diamond_ore",
]
allowed_inventory = [
    "minecraft:diamond",
    "minecraft:diamond_ore",
    "minecraft:deepslate_diamond_ore",
]
```

Each list exempts items from a SPECIFIC enforcement action **while this stage
is locked**. The item is still "locked" (shows lock icon, counted by commands),
but the specific action in each list is allowed.

Accepted formats in every list:

- `"minecraft:diamond"` — exact item ID
- `"#c:gems/diamond"` — item tag (note the `#` prefix; this is a legacy form
  predating the unified prefix system)
- `"mekanism"` — bare mod ID (no colon)

Each list applies **only** when the matching global toggle in
`progressivestages.toml` is ON:

| Stage list | Requires global toggle | Effect |
|-----------|------------------------|--------|
| `allowed_use` | `block_item_use = true` | Right-click / left-click / mine / attack permitted |
| `allowed_pickup` | `block_item_pickup = true` | Picking up from ground permitted |
| `allowed_hotbar` | `block_item_hotbar = true` | Items stay in hotbar (not moved out) |
| `allowed_mouse_pickup` | `block_item_mouse_pickup = true` | Click-pickup in GUIs permitted |
| `allowed_inventory` | `block_item_inventory = true` | Items stay in inventory (not auto-dropped) |

**Worked example.** "Lock diamonds globally but let players stockpile raw
diamonds until they unlock the stage":

```toml
[items]
locked = ["name:diamond"]

[enforcement]
allowed_pickup = ["minecraft:diamond"]
allowed_inventory = ["minecraft:diamond"]
allowed_mouse_pickup = ["minecraft:diamond"]
# allowed_hotbar omitted — diamonds get bumped out of the hotbar
# allowed_use omitted    — diamonds still can't be used
```

The implementation: per-stage exemption lists are stored on
[`LockDefinition`](src/main/java/com/enviouse/progressivestages/common/lock/LockDefinition.java)
and queried by each enforcer at the corresponding event.

---

## 5. Triggers — Automatic Stage Grants

Triggers live in **one file**: `config/ProgressiveStages/triggers.toml`. They
grant stages automatically based on player actions, without per-tick polling.

### 5.1 Single-trigger sections

Four sections — each is a flat `"key" = "stage_id"` map. The stage ID
on the right side is normalized (case, namespace, whitespace) when loaded,
so `"iron_age"` and `"progressivestages:iron_age"` are interchangeable.

#### `[advancements]` — one advancement → one stage

```toml
[advancements]
"minecraft:story/mine_stone" = "stone_age"
"minecraft:story/smelt_iron" = "iron_age"
"minecraft:story/mine_diamond" = "diamond_age"
"minecraft:nether/return_to_sender" = "nether_warrior"
```

Fires immediately when the player earns the advancement, no relog required.
Stage is granted once per (player, stage); re-earning has no effect.

#### `[items]` — one item → one stage

```toml
[items]
"minecraft:iron_ingot" = "iron_age"
"minecraft:diamond" = "diamond_age"
"minecraft:netherite_ingot" = "netherite_age"
```

Fires on TWO surfaces:

- **Pickup** — `ItemEntityPickupEvent.Pre`.
- **Login inventory scan** — `PlayerLoggedInEvent`. Walks the player's main
  inventory; if they already have the item, the stage is granted retroactively.

#### `[dimensions]` — first entry → stage (one-time, persisted)

```toml
[dimensions]
"minecraft:the_nether" = "nether_explorer"
"minecraft:the_end" = "end_explorer"
```

Fires on `PlayerEvent.PlayerChangedDimensionEvent`. **Persisted per-world** in
`world/data/progressivestages_triggers.dat` so it survives restarts. The
trigger fires once per (player, dimension) — re-entering after the trigger
has fired does nothing, even if the stage is later revoked.

Reset: `/progressivestages trigger reset <player> dimension <id>`

#### `[bosses]` — first kill → stage (one-time, persisted)

```toml
[bosses]
"minecraft:ender_dragon" = "dragon_slayer"
"minecraft:wither" = "wither_slayer"
"minecraft:warden" = "warden_slayer"
```

Fires on `LivingDeathEvent`. **Persisted per-world.** Attribution accepts:

1. Direct killer (entity that dealt the killing blow).
2. Projectile source (e.g. player who shot an arrow).
3. Last player who hurt the entity within 5 seconds (100 ticks).

Reset: `/progressivestages trigger reset <player> boss <id>`

### 5.2 `[[multi]]` — multi-trigger requirements (NEW IN 2.0)

> **The user-requested feature.** When a stage should require MULTIPLE
> conditions to be met before being granted — e.g. "the player has picked up
> a stone sword, stone pickaxe, AND stone axe" — single triggers are not
> enough. `[[multi]]` requirements solve this.

A multi-trigger is a **table-array** entry that lists a stage plus a set of
sub-conditions that must ALL fire (or, optionally, ANY ONE fire) before the
stage is granted.

#### Shape

```toml
[[multi]]
stage = "stone_tools_master"
description = "Own a full set of stone tools"
id = "stone_tools_master"        # optional — see PROGRESS + PERSISTENCE below
all_of = [
    "item:minecraft:stone_sword",
    "item:minecraft:stone_pickaxe",
    "item:minecraft:stone_axe",
]
```

| Field | Required? | Notes |
|-------|-----------|-------|
| `stage` | **Yes** | The stage ID granted on completion. Normalized like every other stage ID. |
| `all_of` | Exactly one of `all_of` / `any_of` | List of sub-trigger strings. Stage granted when EVERY sub-trigger has fired at least once. |
| `any_of` | (alternative) | Stage granted the moment ANY ONE sub-trigger fires. Equivalent to N separate single-trigger lines, but groups intent. |
| `description` | Optional | Free text used by `/progressivestages multi list`. |
| `id` | Optional | Stable persistence handle; defaults to a hash of (stage + mode + sorted sub-keys). |

#### Sub-trigger prefixes

Each sub-trigger string uses a **surface prefix** plus the resource ID it
listens on:

| Prefix | Surface |
|--------|---------|
| `item:<namespace:path>` | Item picked up OR found in inventory at login |
| `advancement:<namespace:path>` | Advancement earned (or already earned at login) |
| `dimension:<namespace:path>` | Dimension entered (current dimension at login also counts) |
| `boss:<namespace:path>` | Entity killed by the player |

#### Worked examples

**Example 1 — The canonical case (the user's stone-tools request):**

```toml
[[multi]]
stage = "stone_tools_master"
description = "Own a full set of stone tools"
all_of = [
    "item:minecraft:stone_sword",
    "item:minecraft:stone_pickaxe",
    "item:minecraft:stone_axe",
]
```

A player must pick up (or log in already holding) all three tools before
`stone_tools_master` is granted. The three sub-triggers can be satisfied in
any order, over any timespan, across sessions.

**Example 2 — Cross-surface:**

```toml
[[multi]]
stage = "true_dragon_slayer"
description = "Kill the dragon AND finish Free The End"
all_of = [
    "boss:minecraft:ender_dragon",
    "advancement:minecraft:end/kill_dragon",
]
```

Mixes a boss-kill sub-trigger with an advancement sub-trigger.

**Example 3 — Any-of:**

```toml
[[multi]]
stage = "off_world_explorer"
description = "Visit the Nether or the End"
any_of = [
    "dimension:minecraft:the_nether",
    "dimension:minecraft:the_end",
]
```

Functionally equivalent to two `[dimensions]` entries pointing at the same
stage, but the `[[multi]]` form documents intent and groups the routes.

**Example 4 — Big endgame gate:**

```toml
[[multi]]
id = "endgame_gate"
stage = "endgame"
description = "Beat the major bosses + reach the End"
all_of = [
    "boss:minecraft:ender_dragon",
    "boss:minecraft:wither",
    "dimension:minecraft:the_end",
    "advancement:minecraft:adventure/totem_of_undying",
]
```

#### Progress and persistence

Per-player progress is persisted in
`world/data/progressivestages_triggers.dat` (the same file used by the
boss/dimension one-time triggers). Each sub-key of each requirement is either
**satisfied** or **not** for a given player.

The persistence key is `multi:<requirementId>:<surface>:<resourceId>`. The
`requirementId` is either:

- The user-provided `id = "..."` field, OR
- A hash of `<stage> | <mode> | <sorted sub-keys>` — stable across reorderings
  of the file but invalidated when you ADD or REMOVE a sub-trigger.

**Retroactive credit** (only relevant when a requirement is added to a server
that already has played players):

| Surface | Retroactive? | How |
|---------|--------------|-----|
| `item` | Yes | Login inventory scan |
| `advancement` | Yes | Login scan of completed advancements |
| `dimension` | Partial | Current dimension at login counts as visited |
| `boss` | **No** | Past kills cannot be retrieved; the player must kill the entity again |

#### Inspecting and resetting

- `/progressivestages multi list` — every loaded requirement, mode, and
  sub-trigger list.
- `/progressivestages multi list <player>` — same, plus that player's
  progress per requirement (`[2/3]` or `GRANTED`, with ✓ / ✗ next to each
  sub-trigger).
- `/progressivestages trigger reset <player> multi <requirement-id>` — clear
  every sub-key for a player so they can re-satisfy the requirement. Useful
  for testing and admin overrides.

#### Implementation pointers

- Config parsing: [`TriggerConfigLoader.loadMultiRequirements`](src/main/java/com/enviouse/progressivestages/server/triggers/TriggerConfigLoader.java)
- Runtime registry + dispatcher: [`MultiTriggerManager`](src/main/java/com/enviouse/progressivestages/server/triggers/MultiTriggerManager.java)
- Data class: [`MultiTrigger`](src/main/java/com/enviouse/progressivestages/server/triggers/MultiTrigger.java)
- Cause enum value: `StageCause.MULTI_TRIGGER`
- Persistence: shared with the boss/dimension `TriggerPersistence`, under the
  synthetic type `"multi"`.

---

## 6. Global Configuration — `progressivestages.toml`

The main mod config lives at `config/progressivestages.toml` (NOT inside the
`ProgressiveStages/` directory — it's a NeoForge `ModConfigSpec` registered
during mod construction). Every key has an inline comment in the generated
file; this section summarizes them.

The authoritative source is
[`StageConfig`](src/main/java/com/enviouse/progressivestages/common/config/StageConfig.java).
Default values are shown below.

### 6.1 `[general]`

| Key | Default | Meaning |
|-----|---------|---------|
| `starting_stages` | `["stone_age"]` | Stages auto-granted on first join. Empty list = no auto-grant. |
| `reapply_starting_stages_on_login` | `false` | If true, the starting list is re-checked on every login (idempotent — already-granted stages are not re-granted). |
| `team_mode` | `"ftb_teams"` | `"ftb_teams"` (shared per FTB team) or `"solo"` (per player). Falls back to solo if FTB Teams is not installed. |
| `debug_logging` | `false` | Verbose logging for stage checks, lock queries, team operations. |
| `linear_progression` | `false` | If true, granting a stage auto-grants all missing dependencies recursively. |

### 6.2 `[enforcement]` — global toggles

Every category has a master toggle. Setting any of these to `false` disables
that enforcement path for every stage, regardless of stage file content.

| Key | Default | Affects |
|-----|---------|---------|
| `block_item_use` | `true` | Right-click / left-click / mine / attack with locked items |
| `block_item_pickup` | `true` | Picking up locked items from the ground |
| `block_item_hotbar` | `true` | Locked items moved out of the hotbar (softer than `block_item_inventory`) |
| `block_item_mouse_pickup` | `true` | Click-pickup of locked items in GUIs |
| `block_item_inventory` | `true` | Strictest: auto-drop locked items from anywhere in inventory |
| `inventory_scan_frequency` | `0` | Ticks between inventory scans. `0` = scanning OFF. Pickup blocking still prevents new locked items from entering. |
| `block_crafting` | `true` | Locked recipes refuse to hand over outputs |
| `hide_locked_recipe_output` | `true` | Recipe-book / crafting-table preview hides locked outputs |
| `block_block_placement` | `true` | Placing locked blocks |
| `block_block_interaction` | `true` | Right-clicking locked blocks |
| `block_dimension_travel` | `true` | Traveling to locked dimensions |
| `block_locked_mods` | `true` | The `mod:` prefix is honored (turn off to ignore mod-locks entirely) |
| `block_interactions` | `true` | `[[interactions]]` entries |
| `block_entity_attack` | `true` | `[entities]` attack gating |
| `block_mob_spawns` | `true` | `[mobs].locked_spawns` gating |
| `mob_spawn_check_radius` | `128` | Radius for the nearest-player check used by mob spawn + crop growth + loot drop |
| `block_enchants` | `true` | Enchantment-table + anvil + villager + inventory-strip |
| `block_screen_open` | `true` | `[screens]` GUI gating |
| `block_crop_growth` | `true` | `[crops]` planting / growth / bonemeal / harvest |
| `block_pet_interact` | `true` | `[pets]` taming / breeding / commanding / riding |
| `block_loot_drops` | `true` | `[loot]` GLM + mob + block drops |
| `block_mob_replacements` | `true` | `[[mobs.replacements]]` |
| `block_region_entry` | `true` | `[[regions]]` push-back + flags |
| `region_tick_frequency` | `20` | Ticks between region + structure entry checks. 20 = 1s. |
| `block_structure_entry` | `true` | `[structures]` entry + chest locking + rule flags |
| `allow_creative_bypass` | `true` | Creative-mode players bypass enforcement |
| `reveal_stage_names_only_to_operators` | `true` | Non-op players see generic lock messages without stage names (spoiler-free progression) |
| `mask_locked_item_names` | `true` | Locked items show as `"Unknown Item"` (configurable text) |
| `notification_cooldown` | `3000` ms | Cooldown between repeat lock messages to the same player |
| `show_lock_message` | `true` | Send a chat message when an action is blocked |
| `play_lock_sound` | `true` | Play a sound when an action is blocked |
| `lock_sound` | `"minecraft:block.note_block.pling"` | Sound resource location |
| `lock_sound_volume` | `1.0` | 0.0 – 1.0 |
| `lock_sound_pitch` | `1.0` | 0.5 – 2.0 |
| `show_creative_bypass_popup` | `true` | Warn a player on creative-mode entry that bypass is on |

### 6.3 `[emi]` — recipe-viewer feedback

| Key | Default | Meaning |
|-----|---------|---------|
| `enabled` | `true` | Master toggle for EMI integration |
| `show_lock_icon` | `true` | Overlay a lock icon on locked stacks |
| `lock_icon_position` | `"top_left"` | Where to draw the icon: `top_left`, `top_right`, `bottom_left`, `bottom_right`, `center` |
| `lock_icon_size` | `8` | Icon size in pixels (4–32) |
| `show_highlight` | `true` | Translucent overlay on locked recipe outputs |
| `highlight_color` | `"0x50FFAA40"` | ARGB hex |
| `show_tooltip` | `true` | Add lock info to item tooltips |
| `show_locked_recipes` | `false` | If `false`, locked items / recipes are hidden from the EMI index entirely; if `true`, they are shown with overlays |

### 6.4 `[performance]`

| Key | Default | Meaning |
|-----|---------|---------|
| `enable_lock_cache` | `true` | Cache lock-query results per (player, item) |
| `lock_cache_size` | `1024` | Cache entries per player (128–8192) |

### 6.5 `[team]`

| Key | Default | Meaning |
|-----|---------|---------|
| `persist_stages_on_leave` | `true` | Stages persist on the player record when they leave their team |

### 6.6 `[integration]`

| Key | Default | Meaning |
|-----|---------|---------|
| `integration.ftbteams.enabled` | `true` | Master toggle for FTB Teams integration |
| `integration.ftbquests.enabled` | `true` | Master toggle for FTB Quests integration |
| `integration.ftbquests.recheck_budget_per_tick` | `10` | Max stage-task rechecks per tick (1–100) |
| `integration.ftbquests.team_mode` | `false` | Delegate FTB Quests stage operations to FTB Teams' `TeamStagesHelper` instead of the local backend |

### 6.7 `[messages]`

Every player-facing message — tooltips, chat lines, command outputs, validate
output, FTB-status output, type labels (`"This block"`, `"This dimension"`,
etc.), and the creative-bypass popup — is configurable. All values support
`&`-color codes (`&0`–`&f`, `&k`, `&l`, `&m`, `&n`, `&o`, `&r`). Placeholders
(`{stage}`, `{player}`, `{count}`, …) are substituted before color codes are
parsed, so colored placeholders work.

There are roughly 70 message keys. Rather than list them all here, refer to
the generated `progressivestages.toml` for inline documentation of each key.
Source of truth: search `StageConfig.java` for `MSG_`.

---

## 7. Commands

All `/stage` and `/progressivestages` commands require **permission level 2**
unless otherwise noted.

### 7.1 `/stage` — player stage operations

| Command | Description |
|---------|-------------|
| `/stage grant <player> <stage>` | Grant a stage. Prompts for second-confirm if dependencies missing and `linear_progression = false`. |
| `/stage revoke <player> <stage>` | Revoke a stage. |
| `/stage list [player]` | List the player's stages with progression check marks. |
| `/stage check <player> <stage>` | Boolean check. |
| `/stage info <stage>` | Print stage metadata: id, dependencies, description, lock count. |
| `/stage tree` | ASCII dependency tree of every loaded stage. |
| `/stage progress` | Shortcut for `/stage progress next` — what the caller can unlock right now. |
| `/stage progress next [player]` | Lists every stage the player can currently unlock (deps met, not yet granted) with the full per-trigger breakdown for each one. Player defaults to the caller. |
| `/stage progress all [player]` | Lists **every** stage the player doesn't yet have — including those still locked behind unmet dependencies — in registration order. Useful for pack-author audits and "show me the whole roadmap" queries. |
| `/stage progress <stage> [player]` | Per-trigger breakdown for one specific stage. Player defaults to the caller. |

> **Using `/stage progress`.** Three views, one rendering:
>
> - **`/stage progress next`** — the "what can I do right now?" view. Walks
>   every stage you don't own, keeps the ones whose declared dependencies are
>   all satisfied, and renders the per-trigger breakdown for each. If nothing
>   is reachable, it says so and points you at `/stage tree`.
> - **`/stage progress all`** — same renderer, but applied to every stage you
>   don't yet have (including ones still gated by locked prerequisites). The
>   full roadmap; useful when authoring a pack.
> - **`/stage progress <stage>`** — the targeted view. Same per-trigger
>   breakdown as before, scoped to one stage.
>
> Every view reports each surface independently:
>
> - **Dependencies** — ✓/✗ against the player's current stage set.
> - **Advancement** — checked against `PlayerAdvancements` (live advancement state).
> - **Item** — stateless trigger; shown as ✓ only if the item is currently in the player's inventory.
> - **Dimension / Boss** — checked against persisted one-shot trigger state (`world/data/progressivestages_triggers.dat`).
> - **`[[multi]]`** — same `n/total` summary and per-sub-trigger ✓/✗ as `/progressivestages multi list`, filtered to multi-requirements that target this stage. Both `all_of` and `any_of` modes are labeled and counted.
>
> If a stage has no triggers and is granted only by `/stage grant`, the
> single-stage view says so explicitly; the list views hide that line to keep
> output compact.

Tab-completion uses normalized stage IDs — short paths for the default
namespace (`iron_age`), full IDs for other namespaces (`mymod:my_stage`).
For the `<stage>` argument of `/stage progress`, suggestions are sorted so
the caller's next-reachable stages appear first (with every stage still
selectable for free-form lookups). Brigadier surfaces the `next` and `all`
literals alongside the stage list.

> **Edge case.** If you have a stage literally named `next` or `all`,
> `/stage progress <that-name>` routes to the literal subcommand. Use
> `/stage info <name>` to query that stage instead, or rename it.

### 7.2 `/progressivestages` — admin operations

Most subcommands require permission level **3** (admin). The exception is
`no-creative-popup`, which is open to every player (per-player toggle).

| Command | Permission | Description |
|---------|------------|-------------|
| `/progressivestages reload` | 3 | Reload stage TOMLs + triggers.toml; re-sync every online player. |
| `/progressivestages validate` | 3 | Parse every stage file with detailed error reporting; warn about dependency issues. |
| `/progressivestages ftb status [player]` | 3 | Diagnostics for the FTB Quests integration. |
| `/progressivestages trigger reset <player> <type> <key>` | 3 | Clear a persisted trigger. Types: `dimension`, `boss`, `multi` (key is the multi-requirement id). |
| `/progressivestages multi list [player]` | 3 | List every `[[multi]]` requirement and, if a player is given, their per-requirement progress. |
| `/progressivestages no-creative-popup` | any player | Toggle the creative-bypass warning popup for the calling player only. |

### 7.3 `/progressivestages ftb status` output

```
=== FTB Quests Integration Status ===
  Config Enabled: YES
  Provider Registered: YES
  Compat Active: YES
  Pending Rechecks: 0
  Recheck Budget: 10/tick
  Previous Provider Stored: NO

  --- Player: Foo ---
  Player Stages: 3
    stone_age, iron_age, nether_explorer
  Recheck In Progress: NO
```

`Config Enabled` reflects the config toggle. `Provider Registered` is whether
this mod successfully claimed the `StageHelper.INSTANCE` provider on FTB
Library. `Compat Active` indicates the recheck pipeline is wired up. If
`Previous Provider Stored` is `YES`, another mod or dev tool replaced the
provider before this mod could register — useful diagnostic.

---

## 8. EMI / JEI Integration

ProgressiveStages provides client-side recipe-viewer feedback for both EMI and
JEI. Both plugins are loaded only when the corresponding mod is present; no
hard dependency.

### 8.1 Visual treatment

Three layers, each independently configurable in the `[emi]` section of
`progressivestages.toml`:

- **Lock icon overlay** — small lock graphic on every locked stack. Position
  + size configurable. Texture: `assets/progressivestages/textures/gui/lock_icon.png`.
- **Highlight overlay** — translucent color over the recipe output slot when
  the output is locked. Color is an ARGB hex literal.
- **Tooltip line** — appends a `🔒 Item Locked` / `🔒 Recipe Locked` /
  `🔒 Item and Recipe Locked` line plus a "Stage required: X" line. With
  `reveal_stage_names_only_to_operators = true` and a non-op viewer, the
  generic `Progress further to unlock.` is shown instead.

### 8.2 Hiding vs. showing locked content

Controlled by `emi.show_locked_recipes`:

- `false` — locked stacks are hidden from the EMI/JEI index. Players can't
  even browse them, which avoids spoilers but means players don't know what
  they're working toward.
- `true` — locked stacks appear with overlays. Players see "this exists, I
  can't make it yet".

### 8.3 EMI tags

EMI does NOT support custom tag registration via its API, so this mod does
**not** publish `#progressivestages:...` tags through EMI's registry. If
you want tag-based searches in EMI, generate them through the standard
NeoForge tag system (datapack tags) and EMI will pick them up automatically.

### 8.4 Implementation

- EMI plugin: [`ProgressiveStagesEMIPlugin`](src/main/java/com/enviouse/progressivestages/client/emi/ProgressiveStagesEMIPlugin.java)
- JEI plugin: [`ProgressiveStagesJEIPlugin`](src/main/java/com/enviouse/progressivestages/client/jei/ProgressiveStagesJEIPlugin.java)
- Lock icon renderer: [`LockIconRenderer`](src/main/java/com/enviouse/progressivestages/client/renderer/LockIconRenderer.java)
- Locked-item tooltip + name decorator: [`LockedItemDecorator`](src/main/java/com/enviouse/progressivestages/client/renderer/LockedItemDecorator.java)
- EMI screen mixin (for inventory overlays): [`EmiScreenManagerMixin`](src/main/java/com/enviouse/progressivestages/mixin/client/EmiScreenManagerMixin.java)
- EMI stack widget mixin: [`EmiStackWidgetMixin`](src/main/java/com/enviouse/progressivestages/mixin/client/EmiStackWidgetMixin.java)

---

## 9. FTB Quests Integration

Two-way integration when FTB Quests is installed.

### 9.1 As a StageHelper provider

ProgressiveStages registers as the `StageHelper.INSTANCE` provider used by
FTB Quests' built-in Stage Tasks. This means:

- FTB Quests Stage Tasks ask **this mod** whether a player has a stage.
- When ProgressiveStages grants/revokes a stage, every player's Stage Tasks
  are queued for recheck (debounced — at most one recheck per player per
  tick — and budgeted via `integration.ftbquests.recheck_budget_per_tick`).
- A re-entrancy guard prevents recursive loops (quest reward grants stage →
  stage triggers recheck → recheck completes quest → quest grants stage →
  etc.).

### 9.2 As a stage-reward backend

FTB Quests can grant ProgressiveStages stages as quest rewards. Stage IDs are
normalized automatically — both `iron_age` and `progressivestages:iron_age`
work in the FTB Quests reward editor.

### 9.3 Native "Stage Required" property on quests and chapters

When FTB Quests is installed, this mod's mixins add a **"Stage Required"**
field directly to Quest and Chapter properties via
[`QuestMixin`](src/main/java/com/enviouse/progressivestages/mixin/ftbquests/QuestMixin.java),
[`ChapterMixin`](src/main/java/com/enviouse/progressivestages/mixin/ftbquests/ChapterMixin.java),
and [`TeamDataMixin`](src/main/java/com/enviouse/progressivestages/mixin/ftbquests/TeamDataMixin.java).

How to use it:

1. Open FTB Quests in Edit Mode.
2. Select a Quest or Chapter.
3. Right-click → Properties.
4. In the Visibility section, find **"Stage Required"**.
5. Enter a stage ID. The quest/chapter is hidden until that stage is unlocked.
6. Leave empty for no requirement.

Data is saved/synced with FTB Quests' own save system.

If the mixins fail to load (FTB Quests version mismatch), the integration
gracefully degrades: stage-task integration still works, but the property
field disappears. The mod logs a clear error in this case.

### 9.4 Soft-disable behavior

If FTB API surface changes cause compatibility to break, the mod:

- Logs a clear error pointing at which API call failed.
- Continues to enforce locks and update EMI/JEI normally.
- Only the FTB stage-task pipeline is affected.

You can also force-disable with `integration.ftbquests.enabled = false`.

### 9.5 Implementation

- Provider registration: [`FtbQuestsHooks`](src/main/java/com/enviouse/progressivestages/compat/ftbquests/FtbQuestsHooks.java)
- Compat layer + recheck queue: [`FTBQuestsCompat`](src/main/java/com/enviouse/progressivestages/compat/ftbquests/FTBQuestsCompat.java)
- Stage-requirement field helpers: [`RequiredStageHolder`](src/main/java/com/enviouse/progressivestages/compat/ftbquests/RequiredStageHolder.java),
  [`StageRequirementHelper`](src/main/java/com/enviouse/progressivestages/compat/ftbquests/StageRequirementHelper.java)

---

## 10. FTB Teams Integration

When `general.team_mode = "ftb_teams"` and FTB Teams is installed, stage state
is shared across every member of an FTB team. The mod listens to FTB Teams
events to:

- Migrate stage data when a player joins or leaves a team.
- Sync the new team's stage set to the joining player.
- Optionally persist a player's old stages when they leave a team
  (`team.persist_stages_on_leave = true`).

Implementation uses reflection at the entry point in `ServerEventHandler`
to avoid hard-loading the FTB Teams class, so a missing or incompatible FTB
Teams installation just degrades to solo mode without a class-loading error.

- Entry point: [`FTBTeamsIntegration`](src/main/java/com/enviouse/progressivestages/server/integration/FTBTeamsIntegration.java)
- Team-id provider: [`TeamProvider`](src/main/java/com/enviouse/progressivestages/common/team/TeamProvider.java)
- Stage propagation: [`TeamStageSync`](src/main/java/com/enviouse/progressivestages/common/team/TeamStageSync.java)

---

## 11. KubeJS Integration

When KubeJS is installed, ProgressiveStages stages are first-class KubeJS
stages — KubeJS scripts can read, add, and remove them via the normal
`player.stages` API.

```javascript
// server_scripts/stages.js
PlayerEvents.loggedIn(event => {
    if (event.player.stages.has("diamond_age")) {
        event.player.tell("Welcome back, Diamond-bearer.")
    }
})

PlayerEvents.stageAdded("diamond_age", event => {
    event.player.tell("You earned the diamond stage!")
    event.player.give("minecraft:diamond_pickaxe")
})
```

The compat bridge fires KubeJS's standard `STAGE_ADDED` / `STAGE_REMOVED`
events on every grant/revoke. Implementation:
[`KubeJSStagesCompat`](src/main/java/com/enviouse/progressivestages/compat/kubejs/KubeJSStagesCompat.java).

A common pattern: use KubeJS to define a recipe with a unique recipe ID, then
gate that recipe ID in a stage's `[recipes].locked_ids` list — gives KubeJS
recipes the same lock treatment as built-in or datapack recipes.

---

## 12. Lootr Integration

Lootr replaces vanilla loot chests with per-player instances. When both mods
are installed, lock enforcement applies at TWO layers automatically:

1. **Global Loot Modifier** — every `LootTable` roll, including the rolls
   Lootr performs per-player, runs through this mod's GLM. Locked items are
   removed from the loot stack before Lootr wraps it in a per-player
   inventory.
2. **`ILootrFilterProvider`** — a ServiceLoader-registered filter plugs into
   Lootr's own filter chain at priority 1000 (late). The filter removes
   anything still locked for the player Lootr identified from the
   `LootContext`.

### Tag exposure

Lootr publishes block tags this mod can use directly in `[blocks]`,
`[screens]`, or `[structures]` sections:

| Tag | Locks |
|-----|-------|
| `tag:lootr:chests` | Every Lootr chest-shaped block |
| `tag:lootr:barrels` | Lootr barrels |
| `tag:lootr:shulkers` | Lootr shulkers |
| `tag:lootr:containers` | All Lootr containers |
| `tag:lootr:trapped_chests` | Lootr trapped chests |

### Structure interaction

Right-clicking a Lootr chest inside a locked structure is refused by the
structure chest-locking guard. Breaking it is also refused (can't skip the
gate by mining). No config required.

Implementation:
- [`LootrCompat`](src/main/java/com/enviouse/progressivestages/compat/lootr/LootrCompat.java)
- [`LootrStageFilter`](src/main/java/com/enviouse/progressivestages/compat/lootr/LootrStageFilter.java)
- [`LootrStageFilterProvider`](src/main/java/com/enviouse/progressivestages/compat/lootr/LootrStageFilterProvider.java) (ServiceLoader)

---

## 13. Curios Integration

Soft dependency. When Curios is installed, the per-player tick scan covers
curio slots in addition to the main inventory:

- **Locked items in curio slots** — ejected into the main inventory, dropped
  on the ground if main inventory is full.
- **Locked enchantments on curio-held items** — stripped, same as the main
  inventory scrub.
- **Locked slots** (`[curios].locked_slots` in stage files) — entire slot is
  vacated regardless of what's in it.

Implementation: [`CuriosCompat`](src/main/java/com/enviouse/progressivestages/compat/curios/CuriosCompat.java).

---

## 14. Other Compatibility Shims

- **Mekanism** — [`MekanismCompat`](src/main/java/com/enviouse/progressivestages/compat/mekanism/MekanismCompat.java).
  Hooks for Mekanism-specific quirks (some entity registrations don't follow
  the standard registry path).
- **Nature's Compass** — [`NaturesCompassCompat`](src/main/java/com/enviouse/progressivestages/compat/naturescompass/NaturesCompassCompat.java).
  Compass item is gated when ANY dimension is locked for the holder.
- **Visual Workbench** — [`VisualWorkbenchShim`](src/main/java/com/enviouse/progressivestages/compat/visualworkbench/VisualWorkbenchShim.java).
  State-aware block classification so visual replacements don't slip through
  block enforcement.
- **Recipe-viewer hints** — [`RecipeViewerModHints`](src/main/java/com/enviouse/progressivestages/compat/recipeviewer/RecipeViewerModHints.java).
  Hints for cross-recipe-viewer tag emission.
- **Automation notes** — [`AutomationCompatNotes`](src/main/java/com/enviouse/progressivestages/compat/AutomationCompatNotes.java).
  Documentation of expected behavior for common automation mods (Create,
  Mekanism pipes, AE2, etc.).

The full compat registry is
[`ModCompatRegistry.initializeAll`](src/main/java/com/enviouse/progressivestages/compat/ModCompatRegistry.java),
called from `ServerEventHandler.onServerStarting`.

---

## 15. Public API (Java)

Package: `com.enviouse.progressivestages.common.api`

### 15.1 ProgressiveStagesAPI

Static facade in [`ProgressiveStagesAPI.java`](src/main/java/com/enviouse/progressivestages/common/api/ProgressiveStagesAPI.java).
Thread-safe; callable from any context.

```java
// Existence checks
boolean exists = ProgressiveStagesAPI.stageExists(StageId.parse("iron_age"));
Set<StageId> all = ProgressiveStagesAPI.getAllStageIds();

// Per-player queries
boolean has = ProgressiveStagesAPI.hasStage(player, "diamond_age");
Set<StageId> stages = ProgressiveStagesAPI.getStages(player);

// Mutations (fire StageChangeEvent / StagesBulkChangedEvent)
ProgressiveStagesAPI.grantStage(player, StageId.parse("iron_age"), StageCause.QUEST_REWARD);
ProgressiveStagesAPI.revokeStage(player, StageId.parse("stone_age"), StageCause.COMMAND);

// Definitions
Optional<StageDefinition> def = ProgressiveStagesAPI.getDefinition("iron_age");
Collection<StageDefinition> defs = ProgressiveStagesAPI.getAllDefinitions();
```

### 15.2 StageId

Wrapper around `ResourceLocation` with normalization rules per §2.1. Factory
methods:

- `StageId.parse("iron_age")` — normalizes (case, namespace, whitespace).
- `StageId.of("iron_age")` — alias for `parse`.
- `new StageId(ResourceLocation)` — strict, no normalization.

### 15.3 StageCause

The enum tracks the source of a stage change for events and logging:

`COMMAND`, `ADVANCEMENT`, `ITEM_PICKUP`, `INVENTORY_CHECK`, `QUEST_REWARD`,
`DIMENSION_ENTRY`, `BOSS_KILL`, **`MULTI_TRIGGER`** (NEW in 2.0),
`TEAM_SYNC`, `AUTO`, `STARTING_STAGE`, `API`, `UNKNOWN`.

### 15.4 Events

```java
@SubscribeEvent
public static void onStageChanged(StageChangeEvent event) {
    ServerPlayer player = event.getPlayer();
    StageId stageId   = event.getStageId();
    StageChangeType t = event.getChangeType();  // GRANT | REVOKE
    StageCause cause  = event.getCause();
}

@SubscribeEvent
public static void onBulkChange(StagesBulkChangedEvent event) {
    // Fired once at login after stages are synced; lets FTB Quests etc.
    // batch their recheck instead of running one per stage.
}
```

Event classes:
- [`StageChangeEvent`](src/main/java/com/enviouse/progressivestages/common/api/StageChangeEvent.java)
- [`StagesBulkChangedEvent`](src/main/java/com/enviouse/progressivestages/common/api/StagesBulkChangedEvent.java)
- [`StageChangeType`](src/main/java/com/enviouse/progressivestages/common/api/StageChangeType.java)

---

## 16. Networking & Client Caches

ProgressiveStages syncs three pieces of state to the client:

| State | Cache class | Packet |
|-------|-------------|--------|
| Player's stage set | [`ClientStageCache`](src/main/java/com/enviouse/progressivestages/client/ClientStageCache.java) | `STAGE_SYNC_PACKET` (snapshot), `STAGE_UPDATE_PACKET` (delta) |
| Lock registry (item → set of gating stages) | [`ClientLockCache`](src/main/java/com/enviouse/progressivestages/client/ClientLockCache.java) | `LOCK_SYNC_PACKET` |
| Stage definitions (id, display name, deps, icon) | (in `ClientStageCache`) | `STAGE_DEFINITIONS_SYNC_PACKET` |
| Creative bypass flag | (in `ClientStageCache`) | `CREATIVE_BYPASS_PACKET` |
| Reveal-stage-names policy | (in `ClientStageCache`) | `REVEAL_POLICY_PACKET` |

Sync strategy:

- **Snapshot on login** — full stage set + lock registry + stage definitions
  pushed once.
- **Deltas on grant/revoke** — only the changed stage is pushed.
- **Re-sync on `/progressivestages reload`** — same as login, for every
  online player.

The network channel is registered in
[`NetworkHandler`](src/main/java/com/enviouse/progressivestages/common/network/NetworkHandler.java)
using NeoForge's `PayloadRegistrar`. Packets are bound to the mod's
`Constants.STAGE_SYNC_PACKET` etc. resource locations.

---

## 17. Troubleshooting

### 17.1 Stages won't load

Run `/progressivestages validate`. The output lists every TOML file with
syntax errors, validation errors, and invalid item / block / mob IDs. Common
causes:

- Trailing comma in a list (TOML rejects this).
- Wrong section name (`[item]` vs. `[items]`).
- Wrong field name (`locked_recipes` vs. `locked_items`).
- Invalid resource ID (`minecraft::diamond` → double colon).

### 17.2 Items in my locked list aren't locked

Check, in order:

1. **The item ID exactly** — F3+H in-game shows the full registry ID. Copy
   from there to avoid typos.
2. **The corresponding global toggle** — `block_item_use` etc. must be ON in
   `progressivestages.toml`.
3. **`[enforcement]` in the stage file** — maybe you exempted the item.
4. **Stage state** — is the player actually missing the stage?
   `/stage check <player> <stage>`. For a "what triggers are left" view,
   run `/stage progress <stage> [player]`.
5. **debug_logging** — set `general.debug_logging = true` and run
   `/progressivestages reload`. The server log will show which locks were
   registered for each stage.

### 17.3 `tag:` prefix does nothing

Tag membership is resolved at runtime from datapacks. If the tag doesn't
exist or has zero members at the time the lock registry resolves, the prefix
matches nothing. Check with `/tag` ingame.

A reload is the usual fix: `/progressivestages reload` re-resolves tags
against the current world's datapacks.

### 17.4 Mobs are still spawning

The nearest-player check uses `enforcement.mob_spawn_check_radius` (default
128 blocks). If no player is within that range, the spawn is allowed because
no one is there to see it. Increase the radius if you want more aggressive
gating — max is 512.

### 17.5 Multi-trigger isn't granting

Run `/progressivestages multi list <player>`. Each requirement prints the
mode, sub-trigger list, and ✓/✗ per sub-key. If a sub-key shows ✗ that you
think should be ✓:

- For `item:` and `advancement:`, log out and back in — the login scan will
  refresh.
- For `dimension:`, the player has to ENTER the dimension (`PlayerEvent.PlayerChangedDimensionEvent`).
  Currently being in it on login is also enough.
- For `boss:`, past kills are NOT credited retroactively. The player has to
  kill the boss again, or you can `/stage grant` directly.

To start over:
`/progressivestages trigger reset <player> multi <requirement-id>`.

### 17.6 FTB Quests stage tasks not completing

Run `/progressivestages ftb status <player>`. Verify:

- `Config Enabled: YES`
- `Provider Registered: YES`
- `Compat Active: YES`

If `Provider Registered: NO`, another mod claimed the FTB Library
`StageHelper.INSTANCE` provider before this mod could. `Previous Provider
Stored: YES` confirms this. There's no automatic recovery — disable the
conflicting mod or accept that ProgressiveStages stages won't drive FTB Quests.

### 17.7 Enchant is still appearing in the enchanting table

Secondary enchants from `getEnchantmentList` can slip past the clue-based
preview filter. The inventory scan strips them within a tick of application,
so players cannot keep them, but the PREVIEW may briefly show one. A
lower-level fix would need a different mixin approach than 2.0 attempts.

### 17.8 Players are grief-breaking a locked structure's walls

Turn on `[structures.rules].prevent_block_break = true`. Players also get
Mining Fatigue V while inside the structure, so even without the flag
practical progress is slow.

If you also want the structure's BLOCKS to be indestructible (rather than
just break-events cancelled for players) — that's the deferred §2.12
indestructibility feature, on hold post-2.0.

### 17.9 Create harvesters / Mekanism pipes interact with locked content

Create harvesters fire vanilla `BlockDropsEvent`, which the loot filter
catches — so the drops are filtered, but the block still gets broken. If you
want the harvest to refuse entirely, add the crop's block ID to `[blocks]`
in addition to `[crops]`.

Mekanism pipe-to-pipe transport doesn't fire vanilla events, so this mod
can't intercept it. Workaround: lock the SOURCE machine in `[blocks]` or
`[screens]`. Players can't open/configure it → pipes have nothing to pull.
Documented in
[`AutomationCompatNotes`](src/main/java/com/enviouse/progressivestages/compat/AutomationCompatNotes.java).

### 17.10 Reload behavior

`/progressivestages reload` does:

- Reload every `*.toml` in `config/ProgressiveStages/` (stage files).
- Reload `triggers.toml`.
- Re-sync lock data + stage definitions to every online client.
- Trigger EMI/JEI refresh on every client.

It does NOT:

- Clear one-time trigger history (dimension / boss / multi).
- Reset player stage state.
- Re-fire starting-stage grants (unless `reapply_starting_stages_on_login` is
  on, in which case the next login of each player will catch up).

To reset trigger persistence: `/progressivestages trigger reset <player> <type> <key>`.

### 17.11 EMI lock icon missing

- Confirm `emi.show_lock_icon = true`.
- Confirm the texture exists at
  `assets/progressivestages/textures/gui/lock_icon.png` inside the mod jar.
- If you've replaced the texture via a resource pack, ensure the pack is
  enabled and the path matches exactly.

### 17.12 EMI locked items not hiding

Set `emi.show_locked_recipes = false`. Re-open inventory to trigger EMI
refresh. If they still appear, the lock registry may not have been synced;
relog or `/progressivestages reload`.

---

## 18. File / Package Map

Core layout under `src/main/java/com/enviouse/progressivestages/`:

```
Progressivestages.java          ← mod entry point (config registration, default file gen)

common/
  api/
    ProgressiveStagesAPI.java        ← public API facade
    StageId.java                     ← normalized stage identifier
    StageCause.java                  ← enum: why a stage changed
    StageChangeEvent.java            ← per-stage event
    StagesBulkChangedEvent.java      ← bulk login event
    StageChangeType.java             ← GRANT | REVOKE
  config/
    StageConfig.java                 ← every config key + cached value + getter
    StageDefinition.java             ← parsed stage from a TOML file
  data/
    StageAttachments.java            ← NeoForge data attachment registration
    TeamStageData.java               ← team → stage set storage
  lock/
    LockRegistry.java                ← collapsed stage-set per registry ID
    LockDefinition.java              ← all parsed locks from one stage file
    CategoryLocks.java               ← one category's locked + always_unlocked
    PrefixEntry.java                 ← prefix parser (id:/mod:/tag:/name:)
  network/
    NetworkHandler.java              ← packet registration + send helpers
  stage/
    StageManager.java                ← grant / revoke / sync core
    StageOrder.java                  ← dependency graph + lookups
  tags/
    StageTagRegistry.java            ← NeoForge tag emission for stages
    DynamicTagProvider.java          ← datapack tag generation
  team/
    TeamProvider.java                ← solo + FTB Teams team-id resolver
    TeamStageSync.java               ← stage propagation across team members
  util/                              ← misc utility classes
  LootModifiers.java                 ← Global Loot Modifier codec registration

server/
  ServerEventHandler.java            ← top-level event handler (init + most enforcers)
  CreativeBypassNotifier.java        ← creative-mode bypass popup
  commands/
    StageCommand.java                ← every /stage and /progressivestages subcommand
  enforcement/
    ItemEnforcer.java                ← items: use, pickup, hotbar, inventory
    BlockEnforcer.java               ← block placement + interaction
    CropEnforcer.java                ← planting, growth, bonemeal, harvest
    DimensionEnforcer.java           ← portal + teleport + tick safety net
    EnchantEnforcer.java             ← table + anvil + villager + inventory strip
    EntityEnforcer.java              ← attack gating
    FluidEnforcer.java               ← bucket, place, submersion debuff
    InteractionEnforcer.java         ← [[interactions]] X-on-Y rules
    InventoryScanner.java            ← periodic locked-item scan
    LootEnforcer.java                ← mob + block drop filter
    MobReplacementEnforcer.java      ← [[mobs.replacements]] swap
    MobSpawnEnforcer.java            ← [mobs].locked_spawns
    NearestPlayerCheck.java          ← shared nearest-player utility
    PetEnforcer.java                 ← taming + breeding + commanding + riding
    RecipeEnforcer.java              ← [recipes].locked_ids + locked_items
    RegionEnforcer.java              ← [[regions]] entry + flags
    ScreenEnforcer.java              ← [screens] block + item GUI
    StageLootModifier.java           ← Global Loot Modifier impl
    StructureEnforcer.java           ← [structures] entry + chest locking
  integration/
    FTBTeamsIntegration.java         ← FTB Teams membership wiring
  loader/
    DefaultStageTemplates.java       ← built-in stage + trigger TOML strings
    StageFileLoader.java             ← directory scan + reload
    StageFileParser.java             ← TOML → StageDefinition
  triggers/
    AdvancementStageGrants.java      ← [advancements] section
    ItemPickupStageGrants.java       ← [items] section + login inventory scan
    DimensionStageGrants.java        ← [dimensions] section (persisted, one-time)
    BossKillStageGrants.java         ← [bosses] section (persisted, one-time)
    MultiTrigger.java                ← data class for one [[multi]] requirement (2.0)
    MultiTriggerManager.java         ← registry + dispatcher + login scan (2.0)
    TriggerConfigLoader.java         ← parse triggers.toml (incl. [[multi]])
    TriggerPersistence.java          ← per-world "already triggered" record

client/
  ClientEventHandler.java            ← client-side event subscriptions
  ClientModBusEvents.java            ← mod bus client setup
  ClientStageCache.java              ← client-side stage state mirror
  ClientLockCache.java               ← client-side lock registry mirror
  emi/ProgressiveStagesEMIPlugin.java
  jei/ProgressiveStagesJEIPlugin.java
  renderer/
    LockIconRenderer.java
    LockedItemDecorator.java
  util/
    ClientStageDisclosure.java       ← stage-name reveal policy on the client

mixin/                               ← every mixin (vanilla menus + EMI screen + FTB Quests)
  EnchantmentMenuMixin.java
  AnvilMenuMixin.java
  AbstractContainerMenuMixin.java
  CraftingMenuMixin.java
  ResultSlotMixin.java
  ServerPlayerMerchantMixin.java
  client/
    EmiScreenManagerMixin.java
    EmiStackWidgetMixin.java
  ftbquests/
    QuestMixin.java
    ChapterMixin.java
    TeamDataMixin.java

compat/                              ← every soft-dep integration
  ModCompatRegistry.java
  AutomationCompatNotes.java
  curios/CuriosCompat.java
  ftbquests/
    FTBQuestsCompat.java
    FtbQuestsHooks.java
    RequiredStageHolder.java
    StageRequirementHelper.java
  kubejs/KubeJSStagesCompat.java
  lootr/
    LootrCompat.java
    LootrStageFilter.java
    LootrStageFilterProvider.java
  mekanism/MekanismCompat.java
  naturescompass/NaturesCompassCompat.java
  recipeviewer/RecipeViewerModHints.java
  visualworkbench/VisualWorkbenchShim.java
```

The default stage templates are bundled in
[`DefaultStageTemplates`](src/main/java/com/enviouse/progressivestages/server/loader/DefaultStageTemplates.java) —
specifically `stoneAge()`, `ironAge()`, `diamondAge()`, and `triggers()`. The
`diamondAge()` template is the canonical 2.0 reference file; reading it
end-to-end is the fastest way to internalize the schema.

---

*End of document.*
