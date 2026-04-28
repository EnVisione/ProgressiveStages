# ProgressiveStages

> **EnVy here — trying to use Claude to make a more detailed README. If it works I'll keep it, if not I won't.**

---

A NeoForge mod for Minecraft 1.21.1 that gives modpack developers complete control over stage-based progression. Define stages as TOML files; ProgressiveStages locks items, blocks, entities, fluids, dimensions, recipes, enchantments, crops, mob spawns, pets, regions, structures, screens, and player interactions until the player has earned the right stage(s).

**ProgressiveStages 2.0** is a ground-up rework: a unified prefix-based lock model, multi-stage gating where a single resource can require all of several stages, per-stage `[unlocks]` carve-outs, and full coverage of crops/mobs/enchants/regions/structures that v1 didn't have.

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
| `[loot]` | `locked` | Filter locked items out of loot tables and mob/block drops |
| `[pets]` | `locked_taming`, `locked_breeding`, `locked_commanding` | Block taming, breeding, or commanding specific entity types |
| `[curios]` | `locked_slots` | Block equipping into specific Curios slot ids |
| `[mobs]` | `locked_spawns` | Cancel mob spawns near gated players |
| `[[mobs.replacements]]` | `target`, `replace_with` | Substitute one mob type for another at spawn |
| `[[interactions]]` | `type`, `held_item`, `target_block` / `target_entity`, `description` | Block right-click / item-on-block / item-on-entity combos |
| `[[regions]]` | `dimension`, `pos1`, `pos2`, `prevent_entry`, `prevent_explosions`, ... | 3D bounding-box gates |
| `[structures]` | `locked_entry` + `[structures.rules]` (`prevent_block_break`, `prevent_block_place`, `prevent_explosions`, `disable_mob_spawning`) | Block entry into specific generated structures |
| `[enforcement]` | `allowed_use`, `allowed_pickup`, `allowed_hotbar`, `allowed_mouse_pickup`, `allowed_inventory` | Per-stage exception lists for in-inventory enforcement |
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
| **KubeJS** | `hasStage` / `requireStage` / `grantStage` script bindings |
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
| `/stage validate` | OP | Validate every stage TOML file (registry existence, dependency cycles, malformed entries) |
| `/stage reload` | OP | Reload stage and trigger configs from disk |
| `/stage diagnose ftbquests` | OP | Dump FTB Quests integration status (provider registered, team mode, recheck queue) |

Every line of every command's output is a configurable template via `messages.cmd_*` keys in `progressivestages.toml`. `&` color codes and `{placeholder}` substitution work everywhere.

---

## Configuration

`config/progressivestages.toml` — high-level groups:

- **`[general]`** — `starting_stages`, `team_mode` (`ftb_teams` / `solo`), `linear_progression`, `reapply_starting_stages_on_login`, `debug_logging`.
- **`[enforcement]`** — every per-category enforcement toggle (`block_item_use`, `block_block_placement`, `block_dimension_travel`, `block_enchants`, `block_crop_growth`, `block_pet_interact`, `block_loot_drops`, `block_mob_spawns`, `block_mob_replacements`, `block_region_entry`, `block_structure_entry`, `block_screen_open`, ...), plus `allow_creative_bypass`, `mask_locked_item_names`, `notification_cooldown`, `reveal_stage_names_only_to_operators`, lock-sound config, eject-blocked-inventory frequency.
- **`[messages]`** — every player-facing string. Supports `&` color codes (`&0`–`&f`, `&l/m/n/o/k/r`) and named placeholders. Generic `*_generic` variants are emitted when `reveal_stage_names_only_to_operators = true` and the player is non-op. Includes the `messages.prefix` template, `messages.tooltip_*`, `messages.cmd_*`, `messages.type_label_*`, and `messages.cmd_ftb_status_*` families.
- **`[emi]`** — `enabled`, `show_lock_icon`, `lock_icon_position`, `lock_icon_size`, `show_highlight`, `highlight_color`, `show_tooltip`, `show_locked_recipes`.
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
