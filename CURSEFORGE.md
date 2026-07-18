# ProgressiveStages 3.0.1

ProgressiveStages is a deeply customizable stage progression framework for Minecraft 1.21.1 on NeoForge. It gives modpack authors one place to define what players may use, where they may go, what automatically advances them, what a stage rewards, and how the entire progression graph appears in game.

Build a short stone to iron to diamond path, several independent technology and magic branches, a server wide story arc, temporary challenge stages, purchasable skill nodes, or a large quest driven pack. Stages are ordinary TOML files and every shipped feature is optional.

ProgressiveStages 3.0.1 is the current 3.0 release.

The 3.0.1 authoring rehaul adds optional three-file schema 4 packages and `/pstages editor`. An
authorized operator can create, search, validate, simulate, review, apply, synchronize, and roll
back stages through a private localhost editor in an operator-owned single-player world or on a
dedicated server without opening a server web port. Old one-file
stages continue to work. Schema 4 also adds universal condition-driven temporary rules,
deterministic entry priority and exceptions, generic challenge budgets, contextual equipment
modifiers, variables, formulas, templates, stage states, persistent lifecycle progress, expanded
Java and KubeJS registration APIs, automatic compiled client snapshots, and player-facing why,
challenge, equipment, and history panels.

The editor is stage-first and designed for authors who have never written TOML. Type `Iron Age`,
and it creates the namespaced three-file stage package behind one stage card. Guided rule cards ask
what category to affect, what action to gate, whether to lock or allow, how to select an exact ID,
whole mod, tag, or name, which mod to search, what priority wins, what exception applies, whether
JEI or EMI should show it, and which live or triggered condition activates it. The live server
catalog only shows relevant items, blocks, entities, fluids, structures, professions, or other
registry entries. Progression, rewards, costs, stage appearance, and dependency layout have guided
controls too. Rule cards and graph nodes are draggable. Direct TOML and the inspector remain
available for advanced work without becoming required for normal stage creation.

Complete schema 4 instructions and tested packages are available in the
[3.0.1 Schema 4 and Editor Guide](https://github.com/EnVisione/ProgressiveStages/blob/master/REHAUL_GUIDE.md).

---

## The 3.0 progression map

Open the map with `/stage`, `/stages`, `/pstages`, `/stage gui`, the configurable keybind, or the lock button beside the recipe-book button in the survival inventory.

- Familiar vanilla advancement style task, goal, and challenge frames.
- Drag the map from empty space or a node and scroll with the wheel.
- Hover a node for its description, dependencies, trigger progress, and unlock preview.
- Click without dragging to pin full details, including clearly grouped trigger routes.
- Search by stage name, ID, description, category, or locked item.
- Hide stages the player already owns.
- Show or conceal future branches with `reveal = "always"`, `"dependencies"`, or `"unlocked"`.
- Let the dependency graph place nodes automatically or set exact `x` and `y` coordinates.
- Give each stage its own item icon, frame type, background, color, category, and sort order.
- Purchase configured stages from the map. Every purchase is checked again by the server before any cost is removed.

The server remains authoritative. A modified client cannot grant stages, skip dependencies, or fake a purchase.

---

## What can be gated

Each category can be enabled globally and customized inside individual stage files.

- Items. Gate use, pickup, hotbar access, mouse movement, or all inventory holding.
- Blocks. Gate placement and interaction.
- Fluids. Gate bucket pickup, placement, flow, submersion, and recipe viewer visibility.
- Recipes. Gate exact recipe IDs or every recipe that produces a locked item.
- Crops. Gate planting, growth, bonemeal, and harvesting.
- Dimensions. Stop portal and teleport travel with a post travel safety check.
- Enchantments. Gate enchanting, anvils, trades, and existing enchanted gear. Optional maximum levels support partial progression.
- Entities. Gate attacks and interactions.
- Fine grained interactions. Gate item on block, item on entity, and block right click combinations.
- Loot. Filter chest, fishing, archaeology, mob, block, and Lootr rolls.
- Mob spawns. Cancel a locked spawn or replace it with another entity.
- Pets. Gate taming, breeding, commanding, and riding.
- Screens. Gate block menus and held item menus such as backpacks.
- Villager trades and professions. Hide individual offers or block a profession's entire trading screen.
- Advancements. Hide locked advancements from the client until their stage is owned.
- Structures. Gate entry, containers, breaking, placement, explosions, and mob spawning.
- Regions. Gate hand authored three dimensional areas with independent rule flags and debuffs.
- Curios slots. Gate equipment slots when Curios is installed.
- Ores and encrypted blocks. Show a configurable substitute until the required stage is owned and guard the original drops.
- Abilities. Gate jumping, elytra flight, sprinting, swimming, and climbing.
- Beacon effects and brewing outputs. Gate individual effects and potions.

The same content may be gated by several stages. The player must own every still applicable stage. Per stage `[unlocks]` carve outs and `[enforcement]` policies remain scoped to the stage that declared them.

---

## Temporary, triggered, and priority based access

Situational rules can lock or permit content only while their context or timer is active.

- Restrict weapons only inside a generated structure.
- Permit every weapon except bows in an arena.
- Start a swords only rule while fighting a selected mob.
- Let an End Fight stage override an earlier Stronghold gate.
- Block jumping, elytra flight, or a diamond pickaxe only while inside the End.
- Target items, blocks, fluids, entities, recipes, dimensions, structures, and abilities from one rule.
- Match dimension, structure, biome, height, health, stage state, effects, movement state, or a KubeJS predicate.
- Start timers from combat, attack, hurt, kill, `/pstages rule`, KubeJS, or the Java API.

Normal stage gates use priority zero. Conditional rules default to priority one hundred. The highest matching priority wins, and a lock wins an equal priority conflict. Per rule exceptions make broad policies practical without creating a hardcoded weapon list.

## Exact structure sessions in 3.0.1

Assignment, dungeon, arena, and quest mods can register a generic exact structure provider without ProgressiveStages depending on them.

- One generated instance is identified by dimension, structure ID, stable start, and immutable bounds.
- Static stage locks remain authoritative. Provider denial wins and a provider permit cannot bypass a missing normal stage.
- Server committed enter, completion, and debounced leave events include visit sequence, owner, stages, bounds, and typed outcome.
- Team safe leases grant an in progress stage to the first participant and revoke it after the final participant leaves only when the lease introduced it.
- `[active_locks]` can block selected item use only while that leased stage is present inside its matching session.
- `leave_structure` triggers support structure tags, provider filters, required session stages, and incomplete, completed, death, teleport, dimension change, disconnect, cancelled, or recovery outcomes.
- `/pstages structure` commands list providers and sessions, reconcile state, and provide an explicit confirmation gated recovery close.

The full provider contract and acceptance matrix are documented at https://github.com/EnVisione/ProgressiveStages/blob/master/STRUCTURE_SESSION_COMPATIBILITY.md.

---

## One matching language everywhere

Lock lists share the same readable prefixes.

| Entry | Meaning | Example |
|---|---|---|
| `id:` | One exact registry ID | `id:minecraft:diamond` |
| no prefix | Shorthand for `id:` | `minecraft:diamond` |
| `mod:` | An entire mod namespace | `mod:mekanism` |
| `tag:` | A registry tag | `tag:minecraft:logs` |
| `name:` | A case insensitive ID substring | `name:netherite` |
| `#` | Tag shorthand | `#minecraft:logs` |

Broad rules can keep exact exceptions:

```toml
[items]
locked = ["mod:mekanism"]
always_unlocked = ["mekanism:configurator"]
```

---

## Triggers live with their stage

Every automatic route belongs in the same stage TOML as the stage it unlocks. There is no separate global trigger file.

One stage can have several alternative `[[triggers]]` rules. A rule can require every condition with `all_of`, any condition with `any_of`, counts, tags, friendly durations, or custom KubeJS predicates.

Built in condition families cover:

- Items held, picked up, used, dropped, crafted, mined, or broken.
- Entity kills, boss kills, kills with a specific item, breeding, and taming.
- Advancements, effects, levels, XP, statistics, play time, sleeping, fishing, and riding.
- Dimensions, biomes, time spent in a biome, structures, altitude, weather, day count, and world time.
- Distance travelled.
- Another stage being held for a duration.
- Named custom counters controlled by commands, KubeJS, or another mod.
- Fully custom script conditions and script supplied progress.

Example:

```toml
[[triggers]]
mode = "all_of"
description = "Mine diamonds and earn ten quest points"

  [[triggers.conditions]]
  type = "mine"
  id = "minecraft:diamond_ore"
  count = 3

  [[triggers.conditions]]
  type = "custom_counter"
  counter = "quest_points"
  count = 10
```

Relevant game events ask only the indexed conditions to recheck. A configurable polling interval acts as a safety net for state based conditions.

---

## Dependencies, scope, and stage life cycle

- Build linear, branching, converging, or independent dependency graphs.
- Choose `all`, `any`, or counted dependency satisfaction.
- Use team scoped stages for solo or FTB Teams progression.
- Use server scoped stages for world wide milestones.
- Group stages with categories and tags, then query or operate on the group.
- Add temporary durations.
- Revoke on death or when XP falls below a threshold.
- Cascade a revocation through dependent stages.
- Add item and XP purchase costs, cooldowns, and refund percentages.
- Apply stage owned attribute modifiers.
- Reward items, effects, commands, teleports, XP levels, or XP points on a real grant.
- Add a title, toast, sound, particles, progress nudges, or a HUD goal bar.

---

## Commands for players and pack authors

Player and inspection tools include:

- `/stage` or `/stage gui` to open the map.
- `/stage list`, `/stage check`, `/stage info`, and `/stage tree`.
- `/stage progress next`, `/stage progress all`, or `/stage progress <stage>`.
- `/stage simulate` for a dry run of reachable and dependency blocked stages.
- `/pstages rule info` and `list` for conditional rule inspection.

Administrative and authoring tools include:

- `/stage grant` and `/stage revoke`.
- `/stage tag grant`, `revoke`, and `list`.
- `/stage category grant`, `revoke`, and `list`.
- `/stage bulk grant` and `revoke`.
- `/stage counter get`, `add`, `set`, and `reset`.
- `/stage sync` to refresh a player's client state.
- `/stage new <id>` to scaffold a stage file.
- `/stage export` to create a Markdown progression guide in the config folder.
- `/progressivestages reload` for a validated live reload.
- `/progressivestages validate` for parse, dependency, registry, and trigger diagnostics.
- `/progressivestages ftb status` for FTB Quests diagnostics.
- `/pstages rule activate`, `clear`, and `clearall` for timed conditional rules.

Suggestions are generated from loaded stage IDs, tags, categories, and counter names where applicable.

---

## Expanded KubeJS API

KubeJS server scripts receive a global `ProgressiveStages` object.

```js
ProgressiveStages.has(player, 'diamond_age')
ProgressiveStages.grant(player, 'diamond_age')
ProgressiveStages.revoke(player, 'diamond_age')
ProgressiveStages.available(player, 'diamond_age')
ProgressiveStages.percent(player, 'diamond_age')
ProgressiveStages.progress(player, 'diamond_age')
ProgressiveStages.addCounter(player, 'quest_points', 1)
ProgressiveStages.openGui(player)
ProgressiveStages.activateRule(player, 'end_fight/manual_permission', 60)
ProgressiveStages.clearRule(player, 'end_fight/manual_permission')
ProgressiveStages.activeRules(player)
ProgressiveStages.ruleInfo('end_fight/manual_permission')
```

The API also includes actual change callbacks, grant and revoke hooks, bulk operations, tag and category queries, dependency and dependent queries, definition snapshots, custom boolean conditions, custom progress providers, immediate trigger evaluation, synchronization, and the first class `player.stages` bridge.

Grant and revoke methods return whether ownership actually changed. Bulk methods return how many stages changed.

---

## Java API and integration design

`ProgressiveStagesAPI` exposes ownership, dependency, definition, trigger progress, counter, bulk mutation, synchronization, structure provider registration, structure completion, reconciliation, active session views, and detailed item use decisions. NeoForge events report stage changes plus committed structure enter, completion, leave, and access denial notifications.

Optional integrations are isolated behind compatibility seams and guarded mixins. The core does not require optional mod classes to load.

---

## Supported optional integrations

- EMI and JEI. Hide locked entries or show lock overlays, tooltips, highlights, and instant refresh after a stage change.
- FTB Teams. Share team scoped stages while retaining solo fallback.
- FTB Quests and FTB Library. Stage tasks, rewards, required stage fields, and live rechecks.
- KubeJS. Global and player stage scripting APIs.
- Jade and WTHIT. Show the required stage while looking at a locked block or entity.
- Curios. Gate slots and eject locked equipment.
- Lootr. Filter each player's loot rolls.
- Mekanism. Handle NBT heavy item variants and automation surfaces.
- Nature's Compass. Respect locked dimension searches.
- Create and Visual Workbench. Preserve correct block and interaction classification.

All integrations are optional unless your pack uses their features.

---

## Configuration layout

Launch once and ProgressiveStages creates:

```text
config/
└── progressivestages/
    ├── progressivestages.toml
    └── stages/
        ├── stone_age.toml
        ├── iron_age.toml
        └── diamond_age.toml
```

Every editable stage file lives under `config/progressivestages/stages/`. Nested folders are supported.

- `progressivestages.toml` contains global defaults, performance settings, integration switches, disclosure rules, visual settings, and every configurable message.
- `stages/` contains one or more stage TOML files. Nested folders are supported.
- `diamond_age.toml` is the fully commented generated reference and links to the maintained GitHub guides.
- Datapacks may provide stages at `data/<namespace>/progressivestages/stages/*.toml`. A config stage with the same ID wins.

Older loose layouts are migrated without overwriting an existing 3.0 target file.

---

## Safe live reloads

`/progressivestages reload` builds and validates a complete candidate snapshot first. If any file fails to parse, a stage ID is duplicated, the dependency graph is invalid, or application fails, the previous working snapshot remains active.

Use `/progressivestages validate` before shipping a pack and follow the repository testing handbook for multiplayer and integration checks.

---

## Requirements

- Minecraft 1.21.1.
- NeoForge 21.1 or newer within the declared Minecraft compatibility range.
- ProgressiveStages itself has no required recipe viewer, quest, team, scripting, or overlay mod.

---

## Documentation and support

- Beginner guide: https://github.com/EnVisione/ProgressiveStages/blob/master/GETTING_STARTED.md
- Complete documentation: https://github.com/EnVisione/ProgressiveStages/blob/master/DOCUMENTATION.md
- Temporary and triggered locks guide: https://github.com/EnVisione/ProgressiveStages/blob/master/TEMPORARY_AND_TRIGGERED_LOCKS.md
- Fully commented Diamond Stage reference: https://github.com/EnVisione/ProgressiveStages/blob/master/examples/reference/diamond_stage.toml
- Architecture and project structure: https://github.com/EnVisione/ProgressiveStages/blob/master/ARCHITECTURE.md
- Testing handbook: https://github.com/EnVisione/ProgressiveStages/blob/master/TESTING.md
- Bug reports: https://github.com/EnVisione/ProgressiveStages/issues
- Community Discord: https://discord.com/invite/9v4gaRSfdJ

If you find a bug, include the ProgressiveStages version, NeoForge version, relevant stage TOML, `progressivestages.toml`, and the latest log. Never include private server credentials or unrelated secrets.
