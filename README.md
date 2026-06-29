# ProgressiveStages

> **EnVy here — trying to use Claude to make a more detailed README. If it works I'll keep it, if not I won't.**

---

A NeoForge mod for Minecraft 1.21.1 that gives modpack developers complete control over stage-based progression. Define stages as TOML files; ProgressiveStages locks items, blocks, entities, fluids, dimensions, recipes, enchantments, crops, mob spawns, pets, regions, structures, screens, and player interactions until the player has earned the right stage(s).

**ProgressiveStages 2.0** is a ground-up rework: a unified prefix-based lock model, multi-stage gating where a single resource can require all of several stages, per-stage `[unlocks]` carve-outs, and full coverage of crops/mobs/enchants/regions/structures that v1 didn't have.

---

## What's new in 2.5

- **`[professions]` lock category** — `[professions].locked = ["id:minecraft:weaponsmith", "mod:somemod", "name:cleric"]` gates **opening a villager's trade GUI by the villager's PROFESSION**. A player lacking the gating stage can't trade with that villager at all (vs. `[trades]`, which hides individual offers by result item). `id:` / `mod:` / `name:` matching (no tags). Wandering traders have no profession and are unaffected — use `[trades]` for those. Fully opt-in (no overhead when unused).
- **`[advancements]` lock category** — `[advancements].locked = ["id:minecraft:nether/root", "mod:somemod"]` **hides locked advancements from the advancements screen entirely** — server-side, the client is never even told they exist. When the gating stage is gained, a full advancement re-send makes them pop into view (no relog). `id:` / `mod:` / `name:` matching; opt-in fast-path when unused.
- **Structure gating enhancements** — `[structures]` now supports `entry_padding` (an integer block buffer that places repelled players well clear of the boundary; also accepted under `[structures.rules]`), and a player who breaches a gated structure is now teleported **back to their last safe position** (where they last stood outside any locked structure) rather than just shoved to the nearest edge.
- **New `[[triggers]]` conditions** — `world_time` (aliases `time_of_day`/`daytime`/`clock`: the current time-of-day tick `0..23999`, e.g. trigger at night); `breed` now takes an **optional** species/tag target (no target = all bred animals, retroactive; with a target = that species/tag, event-counted); `kill_with` now accepts a **`#tag`** victim (summed over the tag's members); and `script` (aliases `js`/`kubejs`/`custom`: a fully custom condition evaluated by a KubeJS-registered predicate via `type="script", id="<conditionId>"`).
- **Datapack-loaded stages** — stage TOML files can ship inside datapacks at `data/<namespace>/progressivestages/stages/*.toml`. They load at world load and on `/reload` and merge with the config-folder stages — **a config file with the same stage id always wins**, so datapacks provide overridable defaults.
- **`[stage]` hidden / color / category now work in the GUI** — previously parsed but inert. Now `hidden = true` omits the stage from the Stage Tree, `color = "#RRGGBB"` tints the stage name (status colour is the fallback), and `category = "..."` shows as a tag in the detail header.
- **Deep KubeJS integration** — a KubeJS plugin binds a global `ProgressiveStages` object: `onGranted`/`onRevoked` callbacks that fire on **every** engine grant/revoke (commands, triggers, quest rewards, skill-tree purchase, regression), `condition('id', player => bool)` to register custom `script:` trigger conditions, plus `has` / `grant` / `revoke` / `list` / `percent` helpers. (Corrects a prior doc claim: KubeJS 7.x has **no** native stage events that fire on engine grants — `onGranted`/`onRevoked` are the reliable hook.) Reset each server-script reload.
- **Deeper `/stage validate`** — now detects full multi-node dependency cycles (not just self-loops), transitively-unreachable stages, and dead trigger targets (a `[[triggers]]` condition whose exact-id entity/block/item/effect — or `kill_with` item — doesn't resolve). Profession ids are validated too.

---

## What's new in 2.4

- **`[attribute]` stage buffs** — a stage can grant attribute modifiers (`[[attribute]]` with `id` / `operation` / `amount`) that apply while the team **owns** the stage. Any vanilla or modded attribute (`minecraft:generic.max_health`, `generic.scale`, `generic.movement_speed`, …), `add` / `multiply_base` / `multiply_total` operations. Applied on grant/login, removed on revoke (transient, reconciled); current health clamps down if max health drops.
- **`[revoke]` + temporary stages (regression)** — stages can now be *lost*. `[revoke]` supports `on_death = true`, `xp_below = N` (XP-maintained — hold the stage only while total XP ≥ N), and `cascade = true` (also revoke dependents). `[stage].duration = "30m"` makes a temporary stage that auto-expires after that much **real** time (counting down even while offline; units `s`/`m`/`h`/`d`, bare number = minutes). Both report `StageCause.REGRESSION`.
- **`[cost]` skill-tree stages** — a `[cost]` table (`xp_levels`, `items = ["minecraft:diamond:5", …]`, `bypass_requirements`) makes a stage **purchasable** from an Unlock button in the in-game tree GUI (`/stage gui`). Purchases are fully server-validated (no double-spend / bypass) and report `StageCause.PURCHASE`. `bypass_requirements = true` skips the `[[triggers]]` (but never the prerequisite stages).
- **New `[[triggers]]` condition types** — `effect` (currently has a status effect), `breed`, `day_count` (reached world day N), `weather` (`rain`/`thunder`/`clear`, one-shot), `enter_structure` (one-shot), `tame`, and `kill_with` (kill `entity` while holding `with`). `tame` / `kill_with` use mod-tracked counters; the rest read vanilla stats or live state.
- **`[unlock]` unlock juice** — optional `toast`, `title` + `subtitle`, `sound`, `particle`, `progress_nudges` (chat hints at 50/75/90%), and `hud_bar` (a blue "progress to next stage" bar above the XP bar). Every field is optional; absent = off. (Note: singular `[unlock]` ≠ plural `[unlocks]` carve-outs.)
- **`[abilities]` gating** — `[abilities].locked = ["elytra"]` blocks elytra gliding until the stage is owned (dropped out of flight each tick). Other movement abilities are better done via `[attribute]` or KubeJS.
- **New `[stage]` metadata** — `hidden = true` (hide from the GUI tree), `color = "#55FF55"` (GUI tint / `&`-code), `category = "…"` (group label), and `scope = "server"` (a **server-wide** stage — the first team to satisfy it unlocks it for everyone; default `"team"`).
- **First-class KubeJS stages** — PS stages work through the normal `player.stages.has(...)` / `.add(...)` / `.remove(...)` API and fire `PlayerEvents.stageAdded` / `stageRemoved`. The Java `StageChangeEvent` (NeoForge bus) now carries `PURCHASE` / `REGRESSION` causes alongside `COMMAND` / `TRIGGER` / etc.

---

## What's new in 2.3

- **Per-stage `[[triggers]]`** — auto-grant triggers now live inside each stage's TOML (the global `triggers.toml` is gone). Each `[[triggers]]` block is one OR-ed rule of `all_of` / `any_of` conditions over kills, mining, crafting, pickups, item use/drop/breaking, distance travelled, raw statistics, play time, level/XP, inventory holdings, advancements, dimensions, and biomes. Counter conditions read vanilla statistics, so they're retroactive and need no extra save files.
- **In-game Stage Tree viewer** — an "Open Progression Tree" keybind (unbound by default) and `/stage gui` open a read-only two-pane screen: a status-coloured stage tree on the left, and the selected stage's description, prerequisite checklist, live `[[triggers]]` % progress, and an icon grid of the items it unlocks on the right.
- **Per-stage `[display]` overrides** — `display_as_unknown_item`, `obscure_icon` (mask the icon with a `?`), `show_tooltip`, and `show_description_on_tooltip` let a stage override the global tooltip/icon defaults for its own locked items.
- **Per-stage `[enforcement]` overrides** — a stage's `[enforcement]` can now override the global enforcement toggles (`block_item_use`, `block_item_pickup`, `block_item_inventory`, `block_block_placement`, `block_block_interaction`, `block_dimension_travel`, `block_entity_attack`, `block_screen_open`, `block_crop_growth`, `block_pet_interact`) for just that stage's gated resources — opt a stage out of (or in to) a category without touching global config. Most-restrictive-wins where several stages gate the same resource.
- **Triggers respect dependencies** — a stage's `[[triggers]]` no longer auto-grant until all of its `dependency` prerequisites are owned; counter progress keeps accruing in the meantime. Omit a stage's `dependency` to let its triggers fire freely.

---

## What's new in 2.0

- **Unified prefix lock model** — `id:`, `mod:`, `tag:`, `name:` (no prefix = `id:`) replace v1's scattered `items` / `item_tags` / `item_mods` arrays. One list, one syntax.
- **Multi-stage gating** — the same item can be locked by multiple stages simultaneously. The player must own **all** gating stages to access it. First-match-wins is gone.
- **Per-stage `[unlocks]` carve-outs** — a stage can lock an entire mod yet exempt specific items via its own `[unlocks]` list. Unlike v1's global per-category whitelist, each stage's carve-outs are scoped to that stage.
- **`minecraft = true` shorthand** — equivalent to `mods = ["minecraft"]`, with parent-stage inheritance: granting a child stage that doesn't itself set `minecraft = true` doesn't leak vanilla content the parent had locked.
- **New lock categories**: enchantments, crops, mob spawns, mob replacements (substitute on spawn), pet taming/breeding/commanding, regions, structures, screens, interactions, curio slots.
- **Vanilla inventory lock icons** — an `IItemDecorator` paints the lock overlay in chests, hotbar, modded backpacks, anvils — every Slot the game renders. EMI's panel still uses the dedicated SlotWidget mixin; JEI's own greying is honored via call-stack detection.
- **Anvil gate** — `AnvilMenuMixin` clears the result slot when input/result is stage-locked, so anvils can't repair, rename, or combine locked items.
- **Visual Workbench compat** — block locks apply through VW-replaced workbenches via reflective resolution to the underlying vanilla block.
- **Reveal-stage-names-only-to-operators** — non-ops see generic lock messages without the stage name (spoiler control); ops always see the full stage.
- **Every player-visible message is a config template** with `&` color codes and named placeholders (`{stage}`, `{type}`, `{count}`, `{player}`, `{progress}`, ...). Pack devs can retheme the entire UX from `progressivestages.toml`.
- **JEI deep hiding** — multi-`FluidStack` ingredient-type enumeration, two-pass refresh (JEI clears the blacklist on add), reflective `IngredientFilter.rebuildItemFilter()`, JEI uid embedded namespace regex, generic ingredient sweep for Mekanism gases/pigments via `Class.getModule().getName()`.
- **EMI hide-by-class** — `removeEmiStacks` predicate covers items, fluids, abstract ids, AND class-module owning mod, catching Mekanism chemicals whose registry id is `mekanism:*` but whose class lives in the mekanism module.
- **FTB Quests deep gating** — the `Quest` / `Chapter` `required_stage` field now also gates `TeamData.canStartTasks`, so progression is blocked even if visibility is overridden by another mod. Optional `ftbquests_team_mode` reflectively delegates `has` / `add` / `remove` to FTB Teams' `TeamStagesHelper`.
- **Spectator bypass parity** — spectators always bypass locks (matching the existing creative-bypass pathway).
- **`reapply_starting_stages_on_login`** opt-in lets pack devs add a starting stage to an existing world and have all online players pick it up next login.

---

## Installation

1. Install [NeoForge](https://neoforged.net/) for Minecraft 1.21.1.
2. Drop the jar into `mods/`.
3. Optional integrations (auto-detected when their mods are present): EMI, JEI, FTB Quests + FTB Library, FTB Teams, Curios, Lootr, Mekanism, KubeJS, NaturesCompass, Visual Workbench.
4. Launch the game once. ProgressiveStages generates `config/progressivestages/` with `stone_age.toml` and `diamond_age.toml` examples plus the main `progressivestages.toml` config file.
5. Edit or add `config/progressivestages/<stage_id>.toml` files; reload at runtime with `/stage reload`.

---

## Quick Start

`config/progressivestages/iron_age.toml`:

```toml
[stage]
id             = "iron_age"
display_name   = "Iron Age"
dependency     = ["stone_age"]
unlock_message = "&6&lIron Age Unlocked!"

[items]
locked = [
    "id:minecraft:iron_pickaxe",
    "mod:create",
    "tag:c:gems/diamond",
    "name:steel"
]
# Per-category whitelist: the Create wrench is exempt from any of the locks above.
always_unlocked = ["id:create:wrench"]

[blocks]
locked = ["id:minecraft:enchanting_table"]

[fluids]
locked = ["id:minecraft:lava"]
```

Grant it in-game:

```
/stage grant YourName iron_age
```

The same player can be required to also have `tutorial_done` to use a specific item by listing it under both stages' `[items].locked` — multi-stage gating happens automatically.

---

## Lock Categories

Each category lives in its own TOML section. Lists accept the unified prefix syntax: `id:`, `mod:`, `tag:`, `name:` (no prefix defaults to `id:`).

| Section | Field | Effect |
|---|---|---|
| `[items]` | `locked`, `always_unlocked` | Block use, pickup, holding in inventory/hotbar |
| `[blocks]` | `locked`, `always_unlocked` | Block placement and right-click interaction |
| `[fluids]` | `locked`, `always_unlocked` | Pickup/place buckets, hide from EMI/JEI, prevent submersion effects |
| `[dimensions]` | `locked` (exact ids only) | Cancel travel, eject players from locked dimensions |
| `[entities]` | `locked`, `always_unlocked` | Block attacking and interacting with entity types |
| `[recipes]` | `locked_ids`, `locked_items` | `locked_ids` blocks specific recipe IDs; `locked_items` blocks every recipe producing the output item |
| `[enchants]` | `locked` | Hide enchants in enchanting table, refuse anvil application, strip from inventory |
| `[crops]` | `locked`, `always_unlocked` | Block planting, growth ticks, bonemeal application |
| `[screens]` | `locked` | Block opening container / GUI blocks |
| `[professions]` | `locked` | **New in 2.5.** Gate opening a villager's trade GUI by the villager's PROFESSION (`id:`/`mod:`/`name:`; no tags). Wandering traders unaffected (use `[trades]`). |
| `[advancements]` | `locked` | **New in 2.5.** Hide locked advancements from the advancements screen entirely (server never tells the client they exist; re-sent when the stage is gained). `id:`/`mod:`/`name:`; no tags. |
| `[loot]` | `locked` | Filter locked items out of loot tables and mob/block drops |
| `[pets]` | `locked_taming`, `locked_breeding`, `locked_commanding` | Block taming, breeding, or commanding specific entity types |
| `[curios]` | `locked_slots` | Block equipping into specific Curios slot ids |
| `[mobs]` | `locked_spawns` | Cancel mob spawns near gated players |
| `[[mobs.replacements]]` | `target`, `replace_with` | Substitute one mob type for another at spawn |
| `[[interactions]]` | `type`, `held_item`, `target_block` / `target_entity`, `description` | Block right-click / item-on-block / item-on-entity combos |
| `[[regions]]` | `dimension`, `pos1`, `pos2`, `prevent_entry`, `prevent_explosions`, ... | 3D bounding-box gates |
| `[structures]` | `locked_entry` + `[structures.rules]` (`prevent_block_break`, `prevent_block_place`, `prevent_explosions`, `disable_mob_spawning`, **`entry_padding`** — new in 2.5) | Block entry into specific generated structures. **New in 2.5:** breaching players teleport back to their last safe position; `entry_padding` (blocks) keeps the fallback push clear of the boundary. |
| `[enforcement]` | `allowed_use`, `allowed_pickup`, `allowed_hotbar`, `allowed_mouse_pickup`, `allowed_inventory` | Per-stage exception lists for in-inventory enforcement |
| `[enforcement]` (overrides) | `block_item_use`, `block_item_pickup`, `block_item_inventory`, `block_block_placement`, `block_block_interaction`, `block_dimension_travel`, `block_entity_attack`, `block_screen_open`, `block_crop_growth`, `block_pet_interact` | **New in 2.3.** Per-stage override of the same-named global enforcement toggle, applied only to this stage's gated resources (omit = inherit global). Most-restrictive-wins across multi-stage gates. |
| `[abilities]` | `locked` | **New in 2.4.** Block movement/action abilities (e.g. `["elytra"]` — no gliding) until the stage is owned |
| `[[attribute]]` | `id`, `operation`, `amount` | **New in 2.4.** *Reward* — grant attribute modifiers (any vanilla/modded attribute; `add`/`multiply_base`/`multiply_total`) while the team **owns** the stage |
| `[revoke]` | `on_death`, `xp_below`, `cascade` | **New in 2.4.** Regression — lose the stage on death, while total XP < N, and optionally cascade the revoke to dependents |
| `[cost]` | `xp_levels`, `items`, `bypass_requirements` | **New in 2.4.** Make the stage **purchasable** from the in-game tree GUI (Unlock button); server-validated |
| `[unlock]` | `toast`, `title`/`subtitle`, `sound`, `particle`, `progress_nudges`, `hud_bar` | **New in 2.4.** Unlock "juice" — toast/title/sound/particle on unlock, progress hints, and a blue progress bar above the XP bar (all optional) |
| `[stage]` metadata | `hidden`, `color`, `category`, `scope`, `duration` | **New in 2.4** (`hidden`/`color`/`category` GUI behaviour **activated in 2.5**). Hide from the GUI tree, tint the stage name (`#RRGGBB`), group label/tag, `scope = "server"` (server-wide stage), temporary `duration` (auto-expires after real time) |
| (root) | `minecraft = true` | Shorthand: equivalent to `mods = ["minecraft"]` across items/blocks/fluids/entities |

Two whitelist mechanisms exist:

- **`always_unlocked`** inside a category — exempts specific entries from THIS stage's locks within THAT category. Simple and local.
- **`[unlocks]` table** — per-stage carve-outs that subtract this stage from the multi-stage gating set for items / mods / fluids / dimensions / entities. Use this when a stage owns a broad lock (e.g., `mod:create`) and you want a specific carve-out that survives even if other stages also gate the same resource.

---

## Multi-Stage Gating

When two or more stages list the same resource under their category section, the player must own **all** gating stages to access it.

```toml
# stage_A.toml
[stage]
id = "stage_A"

[items]
locked = ["id:minecraft:diamond"]
```

```toml
# stage_B.toml
[stage]
id = "stage_B"

[items]
locked = ["id:minecraft:diamond"]
```

A player with only `stage_A` still can't use diamond — they need both `stage_A` and `stage_B`. This lets pack devs split a single resource between two progression branches (e.g. "you must clear the questline AND defeat the boss").

The single-stage accessor `getRequiredStage(Item)` still exists for callers that don't need the full set; it returns the first gating stage in canonical order. Internally, all enforcers use the multi-stage `isItemBlockedFor` predicate which AND-checks every gating stage against the player's owned set.

---

## Per-Stage `[unlocks]`

Each stage can carve out scoped exceptions from its own locks:

```toml
[stage]
id = "industrial_age"

[items]
locked = ["mod:create", "mod:mekanism"]   # all Create and Mekanism items

# Per-stage carve-outs (scoped — only removes this stage from the gating set):
[unlocks]
items = ["id:create:wrench"]              # Create wrench is allowed
mods  = ["mekanism_tools"]                # Mekanism Tools is allowed even though mekanism is locked
```

The `[unlocks]` table mirrors the lock side and accepts these fields: `items`, `mods`, `fluids`, `dimensions`, `entities`. Each entry uses bare `namespace:path` (or for `mods`, a bare modid) — the `id:` and `mod:` prefixes are accepted but optional in this table.

Carve-outs are scoped to the declaring stage: `[unlocks]` on stage A removes stage A from the gating set for those resources. If stage B independently locks the same resource without its own `[unlocks]` entry, stage B still gates.

For simpler whitelists scoped to a single category, use `always_unlocked` inside that category section instead.

---

## `minecraft = true` Shorthand

Set as a root-level key on the stage file:

```toml
[stage]
id = "civilized"

# Root-level shorthand: gates the entire minecraft: namespace.
minecraft = true
```

This registers a synthetic `mod:minecraft` entry into items, blocks, fluids, and entities for this stage — equivalent to listing `"mod:minecraft"` under each of `[items].locked`, `[blocks].locked`, etc. Useful for "no vanilla anything until you've earned this stage" packs.

**Parent-stage inheritance**: if `civilized` has `minecraft = true` and `iron_age` depends on `civilized` without setting `minecraft = true`, owning `iron_age` does **not** unlock `civilized`'s vanilla locks — the player must explicitly own `civilized` for vanilla content to be reachable. Without this, granting a child stage would silently leak vanilla content the parent had locked.

---

## Automatic Triggers (`[[triggers]]`)

A stage grants **itself** when a player meets its triggers. Declare them per-stage with `[[triggers]]` blocks (the old global `triggers.toml` is gone). Each block is one **rule**; rules are **OR-ed** (any complete rule unlocks the stage). Inside a rule, one or more `[[triggers.conditions]]` combine via `mode = "all_of"` (default) or `mode = "any_of"`; a single-condition rule can put the condition fields straight on the `[[triggers]]` table.

```toml
# Grant when the player mines 10 diamond ore (single-condition shorthand)
[[triggers]]
type  = "mine"
block = "minecraft:diamond_ore"
count = 10

# ...or kills 10 endermen AND the ender dragon (a second, OR-ed rule)
[[triggers]]
mode = "all_of"

  [[triggers.conditions]]
  type   = "kill"
  entity = "minecraft:enderman"
  count  = 10

  [[triggers.conditions]]
  type   = "kill"
  entity = "minecraft:ender_dragon"
```

Condition types: `kill`, `mine`, `craft`, `pickup`, `use`, `drop`, `break_item`, `distance` (blocks travelled, by movement kind or `all`), `stat` (any vanilla custom statistic), `play_time` (minutes), `level`, `xp`, `has_item`, `advancement`, `dimension`, `biome`, and **new in 2.4** `effect` (currently has a status effect), `breed`, `day_count` (reached world day N), `weather` (`rain`/`thunder`/`clear`, one-shot), `enter_structure` (one-shot), `tame`, `kill_with` (kill an entity while holding a given item), and **new in 2.5** `world_time` (time-of-day tick `0..23999`, e.g. at night) and `script` (a custom KubeJS-registered predicate, referenced by `type="script", id="<conditionId>"`). Subjects accept `#tag`/`tag:` form; `count` defaults to 1. (`tame`/`kill_with` use mod-tracked counters rather than vanilla stats.) **New in 2.5:** `breed` takes an optional species/tag target (no target = all bred animals, retroactive; with a target = that species/tag, event-counted), and `kill_with` accepts a `#tag` victim (summed over the tag's members). The counter types read vanilla statistics, so they're **retroactive** and survive restarts with no extra save files; `dimension`/`biome` "visited" one-shots are persisted per player (reset with `/progressivestages trigger reset <player> <stage>`). Progress is per-player and the first team member to satisfy a rule unlocks the stage for the whole team. Poll cadence is `enforcement.trigger_poll_interval` ticks (relevant events also force an immediate re-check).

**Triggers respect dependencies:** a stage is not auto-granted by its triggers until every `[stage].dependency` prerequisite is owned. Counters keep accruing while a prerequisite is missing, so the next poll after the last prerequisite is granted completes the unlock. Omit a stage's `dependency` to let its triggers fire freely, regardless of progression.

---

## Stage Tree Viewer & Per-Stage `[display]`

Players can open an in-game **Stage Tree / Progression viewer** via `/stage gui` or the "Open Progression Tree" keybind (category *ProgressiveStages*, unbound by default — assign it in Controls). It's a **read-only, two-pane master/detail screen**. The **left pane** is the stage tree: every stage indented by dependency depth and colour-coded by status (unlocked / ready-to-unlock / locked). Selecting a stage fills the **right pane** with its icon, name, and status; description; the **prerequisites needed to advance** (each marked ✓/✗); a **"% to unlock" progress bar** with the live per-condition `[[triggers]]` breakdown; and an **icon-grid preview of the items that stage unlocks**, with a total count.

Each stage can override the global tooltip/icon defaults for its own locked items with a `[display]` block — all keys optional, inheriting the global default when omitted:

```toml
[display]
display_as_unknown_item     = true    # mask the item NAME as "Unknown Item"
obscure_icon                = true    # also replace the ICON with a "?" placeholder
show_tooltip                = true    # show the lock / required-stage tooltip lines
show_description_on_tooltip = true    # append [stage].description to the tooltip
```

---

## Mod Compatibility

| Mod | Integration |
|---|---|
| **EMI** | Items + fluids + abstract ingredients hidden via `removeEmiStacks` predicate; class-module fallback for Mekanism gases/pigments |
| **JEI** | Items + fluids (every `FluidStack` ingredient type, not just `NeoForgeTypes.FLUID_STACK`) hidden; two-pass refresh; reflective ingredient-filter rebuild; uid namespace regex scan; live `IIngredientListener` |
| **FTB Quests** | `Quest` / `Chapter` `required_stage` field gates both `isVisible` and `canStartTasks`; required-stage entry in the editor config UI |
| **FTB Library** | `StageProvider` Proxy registration so FTB's native Stage Required, Stage Task, and Stage Reward all flow through ProgressiveStages |
| **FTB Teams** | Default backend when `team_mode = "ftb_teams"`; optional `integration.ftbquests.team_mode` reflectively delegates FTB Quests stage reads to `TeamStagesHelper` |
| **Curios** | `CurioCanEquipEvent` gate; per-slot stage locks via `[curios].locked_slots` |
| **Lootr** | `ILootrFilterProvider` filters stage-locked loot from per-player chest snapshots |
| **Mekanism** | Entity-join + block-break hooks honor stage gates; gases/pigments hidden in EMI via class-module detection |
| **KubeJS** | **New in 2.5:** a global `ProgressiveStages` object with `onGranted`/`onRevoked` callbacks (fire on every engine grant/revoke), `condition('id', player => bool)` for custom `script:` trigger conditions, and `has`/`grant`/`revoke`/`list`/`percent` helpers. Also first-class `player.stages.has/add/remove(...)`; Java `StageChangeEvent` (NeoForge bus) carries the `StageCause`. (KubeJS 7.x has no native stage events on engine grants — use `onGranted`/`onRevoked`.) |
| **NaturesCompass** | Filters dimension/structure search results so locked dimensions don't appear |
| **Visual Workbench** | Reflective shim — locks targeting `minecraft:crafting_table` apply through VW-replaced workbenches |

All integrations are reflection-loaded; absent mods are silently skipped. Each can be disabled via the `[integration]` config section.

---

## Commands

| Command | Permission | Description |
|---|---|---|
| `/stage grant <player> <stage>` | OP | Grant a stage |
| `/stage revoke <player> <stage>` | OP | Revoke a stage |
| `/stage list [player]` | OP | Show a player's owned stages |
| `/stage check <player> <stage>` | OP | Check if a player has a stage |
| `/stage info <stage>` | OP | Print a stage's full definition |
| `/stage tree` | OP | Print the stage dependency tree |
| `/stage gui` | OP | Open the in-game Stage Tree / Progression viewer (any player can open it via the keybind) |
| `/stage progress [next\|all\|<stage>] [player]` | OP | Show live `[[triggers]]` rule/condition progress toward stages |
| `/stage validate` | OP | Validate every stage TOML file (registry existence, dependency cycles, malformed entries). **Deepened in 2.5:** full multi-node dependency cycles, transitively-unreachable stages, dead trigger targets (unresolved exact-id entity/block/item/effect or `kill_with` item), and profession ids. |
| `/stage reload` | OP | Reload stage configs (incl. each stage's `[[triggers]]`) from disk |
| `/progressivestages triggers list [player]` | OP | List every stage that declares `[[triggers]]`; with a player, show their live per-condition progress |
| `/progressivestages trigger reset <player> <stage>` | OP | Clear a stage's persisted one-shot (dimension / biome visited) progress |
| `/stage diagnose ftbquests` | OP | Dump FTB Quests integration status (provider registered, team mode, recheck queue) |

Every line of every command's output is a configurable template via `messages.cmd_*` keys in `progressivestages.toml`. `&` color codes and `{placeholder}` substitution work everywhere.

---

## Configuration

`config/progressivestages.toml` — high-level groups:

- **`[general]`** — `starting_stages`, `team_mode` (`ftb_teams` / `solo`), `linear_progression`, `reapply_starting_stages_on_login`, `debug_logging`.
- **`[enforcement]`** — every per-category enforcement toggle (`block_item_use`, `block_block_placement`, `block_dimension_travel`, `block_enchants`, `block_crop_growth`, `block_pet_interact`, `block_loot_drops`, `block_mob_spawns`, `block_mob_replacements`, `block_region_entry`, `block_structure_entry`, `block_screen_open`, ...), plus `allow_creative_bypass`, `mask_locked_item_names`, `obscure_locked_item_icons` (new — replace a locked item's icon with a `?`), `trigger_poll_interval` (new — `[[triggers]]` poll cadence in ticks), `notification_cooldown`, `reveal_stage_names_only_to_operators`, lock-sound config, eject-blocked-inventory frequency.
- **`[messages]`** — every player-facing string. Supports `&` color codes (`&0`–`&f`, `&l/m/n/o/k/r`) and named placeholders. Generic `*_generic` variants are emitted when `reveal_stage_names_only_to_operators = true` and the player is non-op. Includes the `messages.prefix` template, `messages.tooltip_*` (now including `tooltip_stage_description`), `messages.cmd_*`, `messages.type_label_*`, and `messages.cmd_ftb_status_*` families.
- **`[emi]`** — `enabled`, `show_lock_icon`, `lock_icon_position`, `lock_icon_size`, `show_highlight`, `highlight_color`, `show_tooltip`, `show_stage_description_on_tooltip` (new — append the gating stage's description to a locked item's tooltip), `show_locked_recipes`.
- **`[integration.ftbquests]`** — `enabled`, `team_mode` (delegates to FTB Teams' `TeamStagesHelper`), `recheck_budget_per_tick`.
- **`[integration.ftbteams]`** — `enabled`.
- **`[performance]`** — `enable_lock_cache`, `lock_cache_size`.

The full list of keys is generated into `progressivestages.toml` on first launch with descriptive comments for each.

---

## Privacy & Disclosure

By default, `enforcement.reveal_stage_names_only_to_operators = true`. Non-operators (permission level &lt; 2) see generic lock messages — `🔒 You haven't unlocked this item yet!` instead of `Required: Iron Age`. Operators always see the stage name in chat and tooltips. The flag is mirrored to the client via a dedicated payload so client-side tooltip rendering also respects it.

Set the flag to `false` in `progressivestages.toml` to expose stage names to everyone.

---

## Architecture (high level)

```
common/
  config/StageConfig          — every config key + cached getters + ConfigValue<String> message templates
  lock/PrefixEntry            — the "id:foo" / "mod:bar" / "tag:c:gems" / "name:diamond" entry type
  lock/CategoryLocks          — category scaffolding (items / blocks / fluids / ...)
  lock/LockRegistry           — central registry; multi-stage gating; per-stage [unlocks]; minecraft=true expansion
  network/NetworkHandler      — payloads (StageSync, LockSync multi-row, RevealPolicy, CreativeBypass, StageDefinitionsSync)
  stage/StageManager          — grant / revoke, dependency walks, FTB Quests recheck triggers
  util/StageDisclosure        — chokepoint for op-vs-non-op stage-name reveal
  util/TextUtil               — & color code parser used by every message template

server/
  enforcement/*Enforcer       — one per category, all multi-stage; spectator + creative bypass
  loader/StageFileParser      — parses the per-category sections + [unlocks] + minecraft=true schema
  loader/DefaultStageTemplates — first-launch templates (stone_age, diamond_age)
  ServerEventHandler          — wires every NeoForge event (block-place, item-pickup, dim-travel, mob-spawn, ...)
  triggers/                   — advancement / item-pickup / boss-kill / dimension grants

client/
  jei/ProgressiveStagesJEIPlugin   — multi-FluidStack types, 2-pass refresh, filter rebuild, uid regex, IIngredientListener
  emi/ProgressiveStagesEMIPlugin   — removeEmiStacks predicate covering items + fluids + class-module
  renderer/LockedItemDecorator     — IItemDecorator for vanilla inventories
  renderer/LockIconRenderer        — z=1000 lock icon + z=199 highlight
  ClientLockCache + ClientStageCache — multi-stage maps + reveal-policy mirror
  ClientEventHandler               — tooltip emission with multi-stage stage-list
  ClientModBusEvents               — RegisterItemDecorationsEvent subscription on the mod-event bus

mixin/
  AbstractContainerMenuMixin       — multi-stage carried-item gate
  AnvilMenuMixin                   — clears result slot when locked
  CraftingMenuMixin                — recipe + recipe-item gate
  ResultSlotMixin                  — pickup gate
  EnchantmentMenuMixin             — clears clues + refuses click
  ServerPlayerMerchantMixin        — gates villager trades
  client/EmiStackWidgetMixin       — paints lock in EMI panel
  client/EmiScreenManagerMixin     — EMI search / sidebar
  ftbquests/QuestMixin             — required_stage field + isVisible
  ftbquests/ChapterMixin           — required_stage field + isVisible
  ftbquests/TeamDataMixin          — canStartTasks gate

compat/
  curios/                — CurioCanEquipEvent gate
  ftbquests/             — StageProvider Proxy + RequiredStageHolder + ftbquests_team_mode
  kubejs/                — script bindings
  lootr/                 — ILootrFilterProvider implementation (registered via META-INF/services)
  mekanism/              — entity + block hooks
  naturescompass/        — search filter
  recipeviewer/          — RecipeViewerModHints (Class.getModule().getName())
  visualworkbench/       — reflective vanilla-equivalent resolution
```

---

## Changelog

### v2.5
- **`[professions]` lock category** — gate opening a villager's trade GUI by the villager's PROFESSION (`id:`/`mod:`/`name:`; no tags). Wandering traders unaffected (use `[trades]`). Opt-in (no overhead when unused).
- **`[advancements]` lock category** — hide locked advancements from the advancements screen entirely (stripped from the update packet server-side; the client never learns they exist; full re-send when the stage is gained). `id:`/`mod:`/`name:` matching.
- **Structure gating enhancements** — `entry_padding` (block buffer; accepted under `[structures]` or `[structures.rules]`) and last-safe-position teleport: a breaching player is sent back to where they last stood outside any locked structure.
- **New `[[triggers]]` conditions** — `world_time` (time-of-day tick `0..23999`; aliases `time_of_day`/`daytime`/`clock`); `breed` gains an optional species/tag target (no target = all/retroactive; target = event-counted); `kill_with` accepts a `#tag` victim; `script` (`js`/`kubejs`/`custom`) — a custom KubeJS-registered predicate via `type="script", id="<id>"`.
- **Datapack-loaded stages** — `data/<namespace>/progressivestages/stages/*.toml` load at world load and on `/reload` and merge with config-folder stages; a same-id config file always wins (datapacks = overridable defaults).
- **`[stage]` hidden/color/category now live in the GUI** — previously inert: `hidden` omits the stage from the tree, `color = "#RRGGBB"` tints its name, `category` shows as a tag in the detail header.
- **Deep KubeJS integration** — global `ProgressiveStages` object: `onGranted`/`onRevoked` (fire on every engine grant/revoke), `condition('id', player => bool)` for `script:` conditions, `has`/`grant`/`revoke`/`list`/`percent`. Reset each server-script reload. Corrects the prior doc claim that KubeJS native `STAGE_ADDED`/`STAGE_REMOVED` fire on engine grants — they do not (KubeJS 7.x has no native stage events).
- **Deeper `/stage validate`** — full multi-node dependency cycles, transitively-unreachable stages, dead trigger targets (unresolved exact-id entity/block/item/effect or `kill_with` item), and profession-id validation.

### v2.4
- **`[attribute]` stage buffs** — `[[attribute]]` entries (`id` / `operation` / `amount`) grant attribute modifiers while the team owns the stage; any vanilla or modded attribute, `add` / `multiply_base` / `multiply_total`. Applied on grant/login, removed on revoke (transient + reconciled); current health clamps down if max health drops.
- **`[revoke]` + temporary stages (regression)** — `[revoke]` with `on_death`, `xp_below = N` (XP-maintained), `cascade` (revoke dependents). `[stage].duration = "30m"` makes a temporary stage that auto-expires after that much real time, counting down while offline (units `s`/`m`/`h`/`d`; bare number = minutes). Both report `StageCause.REGRESSION`.
- **`[cost]` skill-tree purchases** — `[cost]` (`xp_levels`, `items`, `bypass_requirements`) makes a stage purchasable from an Unlock button in `/stage gui`; fully server-validated; reports `StageCause.PURCHASE`.
- **New `[[triggers]]` condition types** — `effect`, `breed`, `day_count`, `weather` (one-shot), `enter_structure` (one-shot), `tame`, `kill_with`. `tame` / `kill_with` use mod-tracked counters.
- **`[unlock]` unlock juice** — optional `toast`, `title`/`subtitle`, `sound`, `particle`, `progress_nudges` (50/75/90% chat hints), `hud_bar` (blue progress bar above the XP bar).
- **`[abilities]` gating** — `[abilities].locked = ["elytra"]` blocks elytra gliding until the stage is owned.
- **New `[stage]` metadata** — `hidden`, `color`, `category`, and `scope = "server"` (server-wide stage; first team to satisfy unlocks it for everyone).
- **First-class KubeJS stages** — `player.stages.has/add/remove(...)` + `PlayerEvents.stageAdded` / `stageRemoved`; Java `StageChangeEvent` causes extended with `PURCHASE` / `REGRESSION`.

### v2.3
- **Per-stage `[[triggers]]`** — auto-grant triggers moved into each stage's TOML; global `triggers.toml` removed. OR-ed rules of `all_of` / `any_of` conditions over kills, mining, crafting, pickups, use/drop/break, distance, raw stats, play time, level/XP, inventory, advancements, dimensions, and biomes. Counter conditions are retroactive (read vanilla statistics).
- **Triggers respect dependencies** — a stage's triggers don't grant until all its `dependency` prerequisites are owned; counters keep accruing while waiting. Omit `dependency` to fire freely.
- **In-game Stage Tree viewer** — read-only two-pane master/detail screen. "Open Progression Tree" keybind (unbound by default) + `/stage gui`; left pane = status-coloured stage tree by dependency depth, right pane = selected stage's description, prerequisite checklist (✓/✗), live `[[triggers]]` % progress, and an icon grid of the items it unlocks.
- **Per-stage `[display]`** — `display_as_unknown_item`, `obscure_icon`, `show_tooltip`, `show_description_on_tooltip` override the global tooltip/icon defaults.
- **Per-stage `[enforcement]` overrides** — override the same-named global enforcement toggles (`block_item_use/pickup/inventory`, `block_block_placement/interaction`, `block_dimension_travel`, `block_entity_attack`, `block_screen_open`, `block_crop_growth`, `block_pet_interact`) for just that stage's gated resources; most-restrictive-wins across multi-stage gates.
- **New config keys** — `enforcement.obscure_locked_item_icons`, `enforcement.trigger_poll_interval`, `emi.show_stage_description_on_tooltip`, message template `messages.tooltip_stage_description`.
- **Command changes** — added `/stage gui` and `/progressivestages triggers list`; `/progressivestages trigger reset` now takes `<stage>`; removed `/progressivestages multi list`.

### v2.0
- **Unified prefix lock model** — `id:`, `mod:`, `tag:`, `name:` replace v1's scattered arrays.
- **Multi-stage gating** — player must own ALL gating stages to access a resource.
- **Per-stage `[unlocks]`** — scoped carve-outs, no more global-only whitelist.
- **`minecraft = true`** namespace shorthand with parent-stage inheritance.
- **New lock categories**: enchants, crops, mob spawns + replacements, pets, regions, structures, screens, interactions, curio slots.
- **Vanilla inventory lock icons** via `IItemDecorator` — chests, hotbar, modded backpacks all show lock overlays.
- **AnvilMenuMixin** result-slot gate.
- **Visual Workbench compat** via reflective shim.
- **Reveal-stage-names-only-to-operators** privacy flag with client mirror.
- **All player-visible text configurable** with `&` color codes and named placeholders.
- **JEI deep hiding**: multi-FluidStack types, 2-pass refresh, ingredient-filter rebuild, uid namespace regex, `IIngredientListener`.
- **EMI hide-by-class** for Mekanism gases / pigments / chemicals.
- **FTB Quests `canStartTasks` gate** — task progression blocked even if visibility is overridden.
- **`ftbquests_team_mode`** reflective delegation to FTB Teams' `TeamStagesHelper`.
- **Spectator bypass parity** across every enforcer.
- **`reapply_starting_stages_on_login`** opt-in.
- **Mod compat added**: Curios, KubeJS, Lootr, Mekanism, NaturesCompass, Visual Workbench. FTB Library `StageProvider` proxy.
- **`isValidStageName` regex** validation on `starting_stages` to skip malformed config entries.

### v1.4
- Mob spawn gating with creeper example in default `diamond_age.toml`.
- Tick-based dimension safety net for modded portals.
- Recipe ID lock debug logging in `CraftingMenuMixin`.
- EMI / JEI / FTB Teams / FTB Quests all optional dependencies.

### v1.3
- Linear progression mode (auto-grant dependencies).
- Multiple `starting_stages` support.
- Admin bypass confirmation for missing dependencies.
- Stage definitions sync to client with dependencies.

### v1.2
- Entity locks, fluid locks, name pattern matching.
- NBT-aware EMI/JEI hiding for all stack variants (Mekanism Chemical Tanks, Meka-Suit, etc.).
- EMI search index lock icons via `EmiScreenManagerMixin`.
- Creative-mode toggle instantly refreshes EMI/JEI without relog.

### v1.1
- FTB Quests integration.
- EMI handling improvements.
- Trigger system expanded.

---

## Documentation

Per-feature documentation: [DOCUMENTATION.md](DOCUMENTATION.md).

## License

MIT
