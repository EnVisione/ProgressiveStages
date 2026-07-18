# ProgressiveStages 3.0 Beginner Guide

This guide assumes you have never configured a Minecraft progression mod before. It explains
what to install, where every file goes, what each important word means, how to create a working
three-stage progression, how to test it, and what to do when something does not work.

For every available field and advanced feature, use the
[complete documentation](DOCUMENTATION.md). For build, server, multiplayer, and compatibility
verification, use the [testing handbook](TESTING.md).
For a detailed start-to-release workflow with copy-ready examples for all nineteen phases, use the
[Phase 1 Through Phase 19 Guide](PHASES_1_TO_19.md).
For the new three-file schema 4 packages and `/pstages editor`, follow the
[Schema 4 and Editor Rehaul Guide](REHAUL_GUIDE.md). Its first tutorial requires no file editing.

## 1. What this mod does

ProgressiveStages gives each player or team a set of named milestones called stages. A stage can
represent anything you want:

- An age such as `stone_age`, `iron_age`, or `diamond_age`.
- A technology such as `create:steam_power`.
- A location such as `visited_nether`.
- A class, faction, research branch, quest milestone, or server event.

A stage file answers three basic questions:

1. What is this stage called?
2. What must happen before the stage is granted?
3. What content stays locked until the stage is owned?

The server owns the real stage data. Clients receive synchronized copies for the progression map,
tooltips, lock icons, EMI, JEI, Jade, and WTHIT. A modified client cannot grant itself a stage or
legally use server-blocked content.

## 2. Before touching any files

Use the supported runtime:

- Minecraft 1.21.1.
- NeoForge 21.1 or a compatible 21.1 build.
- Java 21.
- ProgressiveStages 3.0.1.

Make a backup before changing an existing pack. At minimum, copy these locations somewhere safe:

```text
config/progressivestages/
<world folder>/data/
```

The config folder contains stage definitions. The world data contains the stages already owned by
players and teams. Backing up only one of these is not enough for a complete rollback.

## 3. Install and generate the folders

1. Stop Minecraft or the dedicated server.
2. Put `progressivestages-3.0.1.jar` in the instance or server `mods` folder.
3. Start the game or server once.
4. Wait until startup completes.
5. Stop it cleanly before editing generated files.

ProgressiveStages creates this layout:

```text
config/
└── progressivestages/
    ├── progressivestages.toml
    └── stages/
        ├── mage/
        │   ├── stage.toml
        │   ├── rules.toml
        │   └── progression.toml
        ├── warrior/
        │   ├── stage.toml
        │   ├── rules.toml
        │   └── progression.toml
        ├── diamond_engineer/
        │   ├── stage.toml
        │   ├── rules.toml
        │   └── progression.toml
        └── 27 more showcase stage packages
```

The main file controls server-wide defaults. The first empty launch creates the complete
[thirty stage showcase tree](SHOWCASE_PACK.md). Each folder is one schema 4 stage package. Existing
stage folders are never replaced. Nested folders and legacy one-file stages are also supported, so
a large pack can use paths such as:

```text
config/progressivestages/stages/magic/beginner_magic.toml
config/progressivestages/stages/technology/electricity.toml
```

The filename helps humans organize the pack. The authoritative identifier is `[stage].id` inside
the file.

## 4. The four words you must know

### Stage

A stage is a milestone stored as a Minecraft resource identifier. `iron_age` is automatically
normalized to `progressivestages:iron_age`. A namespaced identifier such as `mypack:iron_age`
keeps its namespace.

Use lowercase letters, numbers, underscores, periods, hyphens, and forward slashes. Do not use
spaces or path traversal such as `../`.

### Dependency

A dependency is another stage that must already be owned. If `diamond_age` depends on `iron_age`,
the player cannot normally unlock Diamond Age first.

### Lock

A lock names content that is unavailable while its owning stage is missing. If `iron_pickaxe` is
listed under the Iron Age stage, it is locked until Iron Age is owned.

### Trigger

A trigger is an automatic way to grant the stage. Examples include mining a block, completing an
advancement, reaching an XP level, entering a dimension, or increasing a custom KubeJS counter.
Triggers live in the same TOML file as their stage. There is no global `triggers.toml`.

## 5. Copy the tested beginner pack

This repository contains a copy-ready pack at
[`examples/beginner_pack`](examples/beginner_pack/README.md). Its stage files are parsed by the
project test suite on every build, so a syntax regression will fail CI instead of surprising a
pack author.

To use it:

1. Stop the game or server.
2. Back up `config/progressivestages/stages`.
3. Move the generated example stages somewhere outside the active `stages` folder.
4. Copy all files from `examples/beginner_pack/stages` into
   `config/progressivestages/stages`.
5. Start the game or server.
6. Run `/progressivestages validate` as an operator.

Expected result:

```text
All stage files are valid.
```

The exact message can vary slightly, but validation must report no failed files, duplicate IDs,
missing dependencies, or cycles.

## 6. Build one stage by hand

Create `config/progressivestages/stages/my_first_stage.toml`:

```toml
[stage]
id = "my_first_stage"
display_name = "My First Stage"
description = "Unlocks iron tools after the player smelts iron."
icon = "minecraft:iron_ingot"
unlock_message = "&aMy First Stage unlocked."

[items]
locked = [
    "minecraft:iron_pickaxe",
    "minecraft:iron_axe"
]
always_unlocked = []

[[triggers]]
type = "advancement"
advancement = "minecraft:story/smelt_iron"
```

Read it from top to bottom:

- `[stage]` begins the required identity table.
- `id` is the stable name used by commands, dependencies, scripts, and saved data.
- `display_name` is the friendly text shown to players.
- `description` explains the purpose in the progression map and tooltips.
- `icon` is a valid item identifier used by the map node.
- `unlock_message` is sent when the stage is newly granted.
- `[items]` begins item enforcement for this stage.
- `locked` lists items unavailable before ownership.
- `always_unlocked` is an exact-ID exception list. It is empty here.
- `[[triggers]]` adds one automatic unlock rule.
- `type = "advancement"` selects the advancement condition.
- `advancement` names the vanilla advancement to watch.

Save the file and run:

```text
/progressivestages validate
/progressivestages reload
```

Validate first. Reload only after validation succeeds. A failed reload keeps the previous valid
runtime snapshot, but validating first gives clearer feedback and avoids needless disruption.

## 7. Make a progression chain

Add a dependency to a later stage:

```toml
[stage]
id = "my_second_stage"
display_name = "My Second Stage"
dependency = "my_first_stage"
```

For several required dependencies:

```toml
dependency = ["my_first_stage", "visited_village"]
dependency_mode = "all"
```

For alternate branches where either dependency is enough:

```toml
dependency = ["magic_path", "technology_path"]
dependency_mode = "any"
```

For a quorum such as any two of three:

```toml
dependency = ["trial_one", "trial_two", "trial_three"]
dependency_mode = "at_least"
dependency_count = 2
```

Never create a cycle. This is invalid:

```text
stage_a requires stage_b
stage_b requires stage_a
```

`/progressivestages validate` reports cycles and stages made unreachable by them.

## 8. Understand lock list prefixes

Every normal lock category uses the same matching language:

| Entry | Meaning |
|---|---|
| `minecraft:diamond` | One exact identifier. The `id:` prefix is optional. |
| `id:minecraft:diamond` | The same exact identifier written explicitly. |
| `mod:create` | Everything from the `create` namespace in this category. |
| `tag:c:ingots/iron` | Everything in that registry tag. |
| `name:steel` | Every identifier containing `steel`. Use carefully. |

Prefixes are evaluated inside the category containing them. `mod:create` under `[items]` locks
Create items. The same entry under `[blocks]` locks Create blocks.

Example with an exception:

```toml
[items]
locked = ["mod:create"]
always_unlocked = ["create:wrench"]
```

This locks Create items while leaving the wrench usable. If several missing stages lock the same
item, the player must own every applicable stage. One stage cannot accidentally erase another
stage's lock.

## 9. Choose the correct category

Common categories are:

| Goal | Section |
|---|---|
| Block item use, pickup, or inventory access | `[items]` |
| Block placement or right click | `[blocks]` |
| Fluid buckets and fluid interactions | `[fluids]` |
| Crafting recipes or recipe outputs | `[recipes]` |
| Dimension travel | `[dimensions]` |
| Attacking entities | `[entities]` |
| Mob spawning | `[mobs]` |
| Crop planting and growth | `[crops]` |
| Enchantments and level caps | `[enchants]` |
| Villager offers | `[trades]` |
| Whole villager professions | `[professions]` |
| Advancement visibility | `[advancements]` |
| Container and item screens | `[screens]` |
| Loot results | `[loot]` |
| Structure entry | `[structures]` |
| Fixed coordinate areas | `[[regions]]` |
| Specific item-on-block or item-on-entity behavior | `[[interactions]]` |

Use [DOCUMENTATION.md](DOCUMENTATION.md) for the full fields and enforcement behavior of each
category.

## 10. Add automatic progress

One simple trigger rule:

```toml
[[triggers]]
type = "mine"
block = "minecraft:diamond_ore"
count = 3
```

One rule requiring both conditions:

```toml
[[triggers]]
mode = "all_of"
description = "Mine diamonds and reach level ten."

  [[triggers.conditions]]
  type = "mine"
  block = "minecraft:diamond_ore"
  count = 3

  [[triggers.conditions]]
  type = "level"
  count = 10
```

Two separate `[[triggers]]` tables are alternatives. Completing either full rule can grant the
stage. Conditions inside one rule obey that rule's `mode`.

Avoid impossible designs. If Diamond Age locks diamond ore against mining, do not require mining
diamond ore to earn Diamond Age. Trigger progress can accumulate before dependencies are owned,
but content enforcement still applies.

Inspect live progress with:

```text
/stage progress <stage> <player>
/stage simulate <player>
```

## 11. Use the progression map

Open the map with any of these:

```text
/stage
/stages
/pstages
/stage gui
```

The map follows the vanilla advancement visual language:

- Drag empty map space to pan.
- You can also begin a drag on a stage node. A short click still opens its details.
- Use the mouse wheel, arrow keys, or WASD to move.
- Hover a node for its name, status, description, and progress.
- Click a node to pin the detailed inspector.
- Use the search field to find stage text or locked item identifiers.
- Use the Owned control to hide completed stages.
- Use the category control to cycle stage categories.
- Use the home control to center on the best next stage.
- Open the same map from the lock button beside the recipe-book button in the survival inventory.

Configure presentation in the stage file:

```toml
[display]
x = 168
y = 0
frame = "goal"
background = "minecraft:block/deepslate_tiles"
reveal = "dependencies"
sort_order = 20
```

Omit both `x` and `y` to use automatic dependency layout. `frame` accepts `task`, `goal`, or
`challenge`. `reveal` accepts `always`, `dependencies`, or `unlocked`.
For a custom texture stored at `assets/mypack/textures/gui/progression.png`, set
`background = "mypack:gui/progression"`.

## 12. Decide who shares stages

Open `config/progressivestages/progressivestages.toml`.

Solo progression:

```toml
[general]
team_mode = "solo"
```

FTB Teams progression:

```toml
[general]
team_mode = "ftb_teams"
```

When FTB Teams mode is active, members of one team share normal team-scoped stages. A stage can
instead be server-wide:

```toml
[stage]
id = "server_entered_hardmode"
scope = "server"
```

The first qualifying grant makes a server-scoped stage count for everyone. Use server scope only
for progression that truly changes the whole server.

## 13. Connect FTB Quests

ProgressiveStages does not replace a quest book. FTB Quests remains responsible for tasks,
chapters, narrative, and quest rewards.

With the integration enabled, FTB's stage task and reward system uses ProgressiveStages as its
stage provider. Quest and chapter properties can also declare a required stage. A required stage
controls visibility and task start checks.

Confirm integration health with:

```text
/progressivestages ftb status <player>
```

If FTB Quests is absent, ProgressiveStages continues without it.

## 14. Use the smallest useful KubeJS API

Server script example:

```javascript
PlayerEvents.loggedIn(event => {
    if (!ProgressiveStages.has(event.player, "welcome")) {
        ProgressiveStages.grant(event.player, "welcome")
    }
})
```

Useful calls include:

```javascript
ProgressiveStages.has(player, "iron_age")
ProgressiveStages.grant(player, "iron_age")
ProgressiveStages.revoke(player, "iron_age")
ProgressiveStages.available(player, "diamond_age")
ProgressiveStages.missingDependencies(player, "diamond_age")
ProgressiveStages.percent(player, "diamond_age")
ProgressiveStages.addCounter(player, "quest_points", 1)
ProgressiveStages.openGui(player)
```

Mutations must run on the logical server. Invalid IDs or non-server players return safe failure
values rather than granting anything.

### Temporary restrictions and permissions

Normal stage locks are enough for most packs. Use conditional rules when access must change only
inside a dimension or structure, during combat, or for a short API-controlled timer.

```toml
[[temporary_locks]]
id = "end_no_flight"
priority = 100

[temporary_locks.when]
dimensions = ["minecraft:the_end"]

[temporary_locks.targets]
abilities = ["jump", "elytra"]
items = ["minecraft:diamond_pickaxe"]
```

The rule above is active only while the containing stage is owned and the player is in the End.
Normal gates have priority zero, this rule defaults to one hundred, the highest priority wins, and
a lock wins an equal-priority tie. Read the copy-ready
[Temporary and Triggered Locks Guide](TEMPORARY_AND_TRIGGERED_LOCKS.md) before using unlock
overrides or combat timers.

## 15. Safe edit cycle

Use this exact loop while authoring:

1. Stop or pause player activity that could be affected.
2. Back up the stage folder.
3. Make one understandable change.
4. Save the TOML file.
5. Run `/progressivestages validate`.
6. Fix every reported error.
7. Run `/progressivestages reload`.
8. Read the server log for warnings.
9. Run `/stage info <stage>`.
10. If conditional rules are present, run `/pstages rule info <rule>`.
11. Run `/stage simulate <player>`.
12. Test the locked action before granting the stage.
13. Grant or earn the stage.
14. Test the same action again.
15. Revoke the stage and confirm enforcement returns.

Do not test only the happy path. A gate is not proven until it blocks before the stage, permits
after the stage, and blocks again after revocation.

## 16. Troubleshooting by symptom

### The file does not load

Run `/progressivestages validate`. Check quotation marks, commas, table names, resource IDs,
duplicate stage IDs, missing dependencies, and cycles. TOML syntax errors normally include the
file and a useful parser message.

### Reload says it was rejected

This is a safety feature. The last valid runtime snapshot remains active. Fix every candidate
error, validate again, then reload again.

### The item is still locked after grant

Run `/stage check <player> <stage>`. Then check whether another stage also locks the same item.
Multi-stage locking requires all applicable stages. Force a client resync with
`/stage sync <player>` if the server state is correct but visuals are stale.

### A trigger does not grant

Run `/stage progress <stage> <player>`. Confirm dependencies are owned, condition targets are
valid, counts are correct, and the action actually records the statistic used by that trigger.

### A conditional rule does not activate

Run `/pstages rule info <rule>` and check the canonical id, `stage_state`, current `.when` context,
priority, target, and exception. Use `/pstages rule list` for timers. Remember that `[[triggers]]`
grants a stage while `[[triggered_locks]]` starts a temporary access timer.

### The UI does not show a stage

Check `[stage].hidden`, `[display].reveal`, category filters, search text, and the Owned filter.
Run `/stage sync <player>` after a successful reload.

### An optional integration does nothing

Confirm the integration mod version, inspect the startup log, and temporarily test with only that
integration installed. Follow the matrix in [TESTING.md](TESTING.md).

## 17. Beginner completion checklist

Before calling your pack ready, verify every item:

- [ ] The game or server starts without a ProgressiveStages error.
- [ ] `/progressivestages validate` reports no invalid files.
- [ ] `/stage tree` shows the intended dependency structure.
- [ ] `/stage simulate <player>` explains the next reachable stage.
- [ ] Every tested lock blocks before its stage.
- [ ] Every tested lock permits after its stage.
- [ ] Revocation restores the lock.
- [ ] The progression map opens, pans, searches, and shows correct details.
- [ ] A second player receives the correct solo or team behavior.
- [ ] Server-scoped stages affect every player only when intended.
- [ ] EMI or JEI updates after grant and revoke when installed.
- [ ] FTB Quests stage tasks and visibility update when installed.
- [ ] Every conditional rule is tested inside and outside its context and across timer expiry.
- [ ] A dedicated server starts without client-only class errors.
- [ ] The config and world data have a current backup.

When this checklist passes, continue with the larger release matrix in
[TESTING.md](TESTING.md).
