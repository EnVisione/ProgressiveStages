# ProgressiveStages 3.0 Phase 1 Through Phase 19 Guide

This is the long-form, copy-ready path from an empty installation to a release-ready
ProgressiveStages pack. It is written for NeoForge 1.21.1 and ProgressiveStages 3.0.1.
The phases are learning and pack-building phases. They are not Git branches and they do not
claim that unimplemented roadmap ideas in `plan.md` already exist.

Use this guide in order when building a new pack. Use
[`DOCUMENTATION.md`](DOCUMENTATION.md) when you need the exhaustive field reference, and use
the generated [`diamond_stage.toml`](examples/reference/diamond_stage.toml) when you want one
fully commented file containing every supported section.

Every phase contains four things:

1. The goal and the files being changed.
2. A complete or focused example you can copy.
3. What the server and player should do afterward.
4. A verification checklist and common mistakes.

## Phase map

| Phase | Result |
|---:|---|
| 1 | Install the mod and understand the generated folder layout. |
| 2 | Create valid stage identities and a starting stage. |
| 3 | Build linear, branching, and quorum dependency graphs. |
| 4 | Use exact, mod, tag, name, and namespace-wide locks. |
| 5 | Gate items, blocks, recipes, and inventory behavior safely. |
| 6 | Gate fluids, crops, enchantments, brewing, beacons, screens, and interactions. |
| 7 | Gate entities, spawns, replacements, pets, trades, and professions. |
| 8 | Gate dimensions, structures, regions, loot, advancements, and Curios. |
| 9 | Configure ore disguises and encrypted block presentation. |
| 10 | Build automatic unlocks with per-stage trigger rules. |
| 11 | Use every trigger condition type and understand its progress source. |
| 12 | Connect commands, scoreboards, custom counters, and KubeJS providers. |
| 13 | Author the vanilla-style progression map and its player workflow. |
| 14 | Add purchases, rewards, unlock effects, attributes, and ability gates. |
| 15 | Add temporary stages, revocation, scope, tags, categories, and bulk control. |
| 16 | Operate and debug the pack with every command family. |
| 17 | Automate and integrate through the KubeJS and Java APIs. |
| 18 | Configure teams, quests, recipe viewers, overlays, Lootr, and other optional mods. |
| 19 | Ship datapacks, migrate safely, validate, test, package, and release. |

---

## Phase 1. Install the mod and generate the correct folders

### Goal

Start once, confirm the correct Minecraft and Java versions, and learn which files are owned by
the mod and which files are owned by your pack.

### Requirements

- Minecraft 1.21.1.
- NeoForge 21 or newer in the supported 1.21.1 line.
- Java 21.
- The ProgressiveStages JAR in the instance or server `mods` folder.
- Optional integrations only when you want them. EMI, JEI, FTB Teams, FTB Quests, KubeJS,
  Curios, Lootr, Jade, and WTHIT are not required for core startup.

### First start output

After the first successful start, the important layout is:

```text
config/
└── progressivestages/
    ├── progressivestages.toml
    └── stages/
        ├── stone_age.toml
        ├── iron_age.toml
        └── diamond_age.toml
```

`progressivestages.toml` holds global defaults. Every `.toml` below `stages`, including a file
inside a nested folder, is treated as a stage candidate. The `[stage].id` inside a file is the
real identity. The filename is only for organization.

### Safe first actions

1. Stop the server before making the first large edit.
2. Copy the entire `config/progressivestages` folder somewhere safe.
3. Open `progressivestages.toml` and choose solo or FTB Teams ownership.
4. Open `stages/stone_age.toml` and locate its `[stage]` section.
5. Start the server again.
6. Run `/progressivestages validate` as an operator.
7. Open the map with `/pstages` as an ordinary player.

### Minimal global configuration

The generated file contains all supported keys and comments. These are the first settings most
packs should consciously choose:

```toml
[general]
team_mode = "solo"
starting_stages = ["stone_age"]
linear_progression = true
debug_logging = false

[enforcement]
allow_creative_bypass = true

[emi]
enabled = true
show_locked_recipes = false
show_lock_icon = true
show_tooltip = true
```

Use `team_mode = "ftb_teams"` when FTB Teams should own progression. If FTB Teams is absent, the
integration safely falls back to solo ownership.

### Expected behavior

- The game reaches the normal ready state.
- The global config and `stages` folder exist.
- The default stage files load.
- `/pstages` opens the player map and does not require operator permission.
- The conflicting short alias remains unregistered and available to another mod.

### Verification

1. Confirm `java -version` reports Java 21.
2. Confirm the global file exists at `config/progressivestages/progressivestages.toml`.
3. Confirm the generated stage files exist below `config/progressivestages/stages`.
4. Run `/progressivestages validate` and require a successful report.
5. Run `/pstages` as a non-operator and confirm the map opens above the normal menu blur.

### Common mistakes

- Putting stage files beside `progressivestages.toml` instead of inside `stages`.
- Running Java 17 instead of Java 21.
- Editing the JAR instead of the generated config folder.
- Assuming optional integration mods are mandatory.
- Copying an old 1.x guide that uses `minecraft/ProgressiveStages` or
  `progressivestages-common.toml`. Those paths are obsolete.

---

## Phase 2. Create stage identities and a starting stage

### Goal

Create a valid stage that can be stored, synchronized, displayed, queried, granted, and used as a
dependency by later stages.

### Copy-ready starting stage

Create `config/progressivestages/stages/wood_age.toml`:

```toml
[stage]
id = "wood_age"
display_name = "Wood Age"
description = "Gather wood and establish a safe first shelter."
icon = "minecraft:oak_log"
unlock_message = "&aWood Age unlocked."
category = "Survival"
tags = ["starting", "survival", "tier1"]
color = "#A9784D"
hidden = false
scope = "team"

[display]
frame = "task"
reveal = "always"
sort_order = 0
```

Then change the global starting list:

```toml
[general]
starting_stages = ["wood_age"]
```

Run:

```text
/progressivestages reload
/progressivestages validate
/stage info wood_age
/stage check @s wood_age
```

### Stage ID rules

Stage IDs use Minecraft resource-location rules. A short ID such as `wood_age` becomes
`progressivestages:wood_age`. A namespaced ID such as `mypack:technology/electricity` keeps its
namespace.

Valid examples:

```text
wood_age
mypack:wood_age
mypack:technology/electricity
tier_2
```

Invalid examples:

```text
Wood Age
wood age
/wood_age
wood_age/
technology//electricity
```

The stage ID does not need to equal the filename. Matching them is still recommended because it
makes logs, exports, and support requests easier to read.

### Expected behavior

A new player receives `wood_age` according to the starting-stage policy. The map shows an oak-log
node named Wood Age in the Survival category. `/stage info wood_age` prints its metadata.

### Verification

1. Run `/progressivestages validate` before reloading.
2. Run `/progressivestages reload` and confirm the load report names `wood_age` once.
3. Join with a new test player and run `/stage check @s wood_age`.
4. Open `/pstages` and find Wood Age under Survival.
5. Run `/stage info wood_age` and compare its ID, icon, category, tags, and description with the
   file.

### Common mistakes

- Writing `dependencies = [...]`. The supported field is `dependency`, singular, even when its
  value is a list.
- Using uppercase letters or spaces in `id`.
- Expecting `display_name` to act as the ID.
- Adding the same ID to two different files. Duplicate IDs invalidate the candidate reload.
- Setting `hidden = true` and then wondering why the map omits the stage.

---

## Phase 3. Build linear, branching, and quorum dependencies

### Goal

Describe which earlier milestones make a stage reachable. Dependencies decide graph reachability.
Triggers decide whether a reachable stage has been earned. Costs decide whether it can be
purchased. These are separate layers.

### Linear dependency

Create `stages/stone_age.toml`:

```toml
[stage]
id = "stone_age"
display_name = "Stone Age"
description = "Craft stone tools after completing the Wood Age."
icon = "minecraft:stone_pickaxe"
dependency = "wood_age"
dependency_mode = "all"
category = "Survival"
tags = ["survival", "tier2"]
```

### Branching dependency with any route accepted

This stage becomes reachable after either farming or mining:

```toml
[stage]
id = "settled_age"
display_name = "Settled Age"
description = "Prove yourself through farming or mining."
icon = "minecraft:bell"
dependency = ["farmer_path", "miner_path"]
dependency_mode = "any"
category = "Civilization"
```

### Quorum dependency

This stage needs any two of three research branches:

```toml
[stage]
id = "advanced_engineering"
display_name = "Advanced Engineering"
description = "Complete any two engineering disciplines."
icon = "minecraft:comparator"
dependency = ["mechanical_power", "electrical_power", "chemical_processing"]
dependency_mode = "at_least"
dependency_count = 2
category = "Technology"
```

### How modes work

| Mode | Meaning |
|---|---|
| `all` | Every declared dependency must be owned. This is the default. |
| `any` | At least one declared dependency must be owned. |
| `at_least` | At least `dependency_count` declared dependencies must be owned. |

### Verification

```text
/stage tree
/stage progress next @s
/stage simulate @s
```

Grant branches one at a time and rerun `/stage simulate`. The command separates reachable stages
from stages still blocked by missing dependencies.

### Common mistakes

- Creating `a` which depends on `b`, while `b` depends on `a`.
- Setting `dependency_count = 3` with only two dependencies.
- Repeating the same dependency twice.
- Assuming a dependency automatically grants its child. It only makes the child reachable.
- Using `linear_progression = true` while trying to bypass prerequisites with an ordinary grant.

---

## Phase 4. Use the complete lock prefix language

### Goal

Select content precisely without hardcoding hundreds of registry IDs. The same prefix grammar is
available throughout the modern lock categories unless that category explicitly documents a more
specialized value, such as Curios slot identifiers.

### Prefix reference

| Form | Meaning | Example |
|---|---|---|
| `id:` | One exact registry ID. | `id:minecraft:diamond_pickaxe` |
| Bare ID | Same as `id:` when the value is a valid ID. | `minecraft:diamond_pickaxe` |
| `mod:` | Everything in a namespace. | `mod:create` |
| `tag:` | Every member of a tag. | `tag:minecraft:logs` |
| `#` | Short form of `tag:`. | `#minecraft:logs` |
| `name:` | Case-insensitive substring pattern. | `name:diamond` |
| `minecraft = true` | Gate the entire Minecraft namespace for this stage. | Root boolean. |

### Mixed selector example

```toml
[items]
locked = [
  "id:minecraft:diamond",
  "minecraft:diamond_pickaxe",
  "mod:create",
  "tag:minecraft:pickaxes",
  "#minecraft:boats",
  "name:netherite"
]
always_unlocked = [
  "id:create:wrench",
  "id:minecraft:wooden_pickaxe"
]
```

`locked` defines the broad set. `always_unlocked` removes exceptions from that same category.
The per-stage `[unlocks]` table can also carve exact item, mod, fluid, dimension, and entity IDs
out of broad locks declared by that stage.

### Namespace-wide example

```toml
[stage]
id = "vanilla_access"
display_name = "Vanilla Access"

minecraft = true

[unlocks]
items = ["minecraft:stick", "minecraft:crafting_table"]
```

This is intentionally broad. Use it only when the pack is designed around unlocking nearly the
entire vanilla namespace at once.

### Choosing the safest selector

- Prefer `id:` when one object must be gated.
- Prefer `tag:` when a datapack-defined family should move together.
- Prefer `mod:` for a deliberate whole-mod progression gate.
- Use `name:` last. It is convenient but broad and can match content added later.
- Add `always_unlocked` exceptions after testing the actual resolved set.

### Verification

1. Run `/progressivestages validate` to catch malformed exact IDs.
2. Run `/stage info <stage>` and confirm the reported lock count is nonzero.
3. Test one exact selector, one tag member, one namespace member, and one name match.
4. Test every `always_unlocked` exception while the stage is missing.
5. Grant the stage and confirm all selectors stop gating their resolved content.

### Common mistakes

- Writing an item tag in a Curios slot list. Curios slots are plain slot identifiers.
- Assuming a missing or empty tag matches something. It matches nothing.
- Forgetting that `mod:create` can include utility items needed to begin Create progression.
- Treating `name:` as a regular expression. It is a case-insensitive substring selector.

---

## Phase 5. Gate items, blocks, recipes, and inventory behavior

### Goal

Build the most common stage: its missing owner cannot use selected items, place or interact with
selected blocks, or craft selected recipes.

### Complete iron gate

```toml
[stage]
id = "iron_age"
display_name = "Iron Age"
description = "Unlock iron equipment and iron construction."
icon = "minecraft:iron_pickaxe"
dependency = "stone_age"
category = "Survival"

[items]
locked = [
  "id:minecraft:iron_ingot",
  "id:minecraft:iron_pickaxe",
  "id:minecraft:iron_sword",
  "id:minecraft:shield",
  "id:minecraft:bucket"
]

[blocks]
locked = [
  "id:minecraft:iron_block",
  "id:minecraft:iron_door",
  "id:minecraft:smithing_table"
]

[recipes]
locked_ids = [
  "id:minecraft:iron_pickaxe",
  "id:minecraft:shield"
]
locked_items = [
  "id:minecraft:iron_helmet",
  "id:minecraft:iron_chestplate",
  "id:minecraft:iron_leggings",
  "id:minecraft:iron_boots"
]
```

`locked_ids` gates only the recipes with those recipe IDs. `locked_items` gates every recipe whose
output is the listed item. An `[items]` lock is broader: it can block use, pickup, hotbar placement,
mouse pickup, and inventory holding according to global and per-stage enforcement settings.

### Friendly stockpiling policy

Allow players to collect and store raw iron before Iron Age while still blocking its functional
use:

```toml
[items]
locked = ["id:minecraft:raw_iron", "id:minecraft:iron_ingot"]

[enforcement]
allowed_pickup = ["id:minecraft:raw_iron", "id:minecraft:iron_ingot"]
allowed_inventory = ["id:minecraft:raw_iron", "id:minecraft:iron_ingot"]
allowed_mouse_pickup = ["id:minecraft:raw_iron", "id:minecraft:iron_ingot"]
block_item_use = true
block_item_hotbar = true
```

The `allowed` lists are exceptions for content gated by this stage. Boolean values in the
per-stage `[enforcement]` table override the corresponding global enforcement category for this
stage. Omitted booleans inherit the global value.

### Automated crafting protection

```toml
[enforcement]
block_crafting_with_locked_ingredients = true
block_automated_crafting = true
crafter_check_radius = 16
```

This protects the vanilla Crafter and supported crafting paths. Automation from arbitrary mods
can use unique APIs, so test each important machine in your final mod list.

### Verification

Before granting `iron_age`:

1. Attempt to pick up an iron ingot.
2. Attempt to move one into the hotbar.
3. Right-click with an iron tool.
4. Place an iron block.
5. Right-click the smithing table.
6. Arrange an iron-pickaxe recipe.
7. Shift-click the result slot.
8. Test the vanilla Crafter if automation blocking is enabled.

Then run `/stage grant @s iron_age` and repeat every action. All intended restrictions should
disappear immediately.

### Common mistakes

- Using an output item ID in `locked_ids`. Recipe IDs and output item IDs are not always equal.
- Turning off a global enforcement toggle and expecting a lock to override it without a per-stage
  override.
- Setting the full inventory scanner to zero and expecting full-inventory ejection.
- Testing only the visible crafting output and not result pickup or shift-click.

---

## Phase 6. Gate fluids, crops, enchantments, brewing, beacons, screens, and interactions

### Goal

Control mechanics that are more specific than a normal item or block click.

### Copy-ready technology gate

```toml
[stage]
id = "applied_science"
display_name = "Applied Science"
description = "Unlock fluid handling, advanced enchanting, and machine interfaces."
icon = "minecraft:brewing_stand"
dependency = "iron_age"
category = "Technology"

[fluids]
locked = ["id:minecraft:lava", "mod:mekanism"]
always_unlocked = ["id:minecraft:water"]

[crops]
locked = ["id:minecraft:nether_wart", "mod:mysticalagriculture"]

[enchants]
locked = ["id:minecraft:mending"]
max_levels = ["minecraft:sharpness:3", "minecraft:protection:2"]

[beacon]
locked = ["id:minecraft:strength", "id:minecraft:haste"]

[brewing]
locked = ["id:minecraft:strength", "id:minecraft:swiftness"]

[screens]
locked = [
  "id:minecraft:enchanting_table",
  "id:minecraft:brewing_stand",
  "mod:mekanism"
]

[[interactions]]
type = "item_on_block"
held_item = "id:minecraft:flint_and_steel"
target_block = "id:minecraft:tnt"
description = "Ignite TNT"

[[interactions]]
type = "item_on_entity"
held_item = "id:minecraft:name_tag"
target_entity = "tag:minecraft:skeletons"
description = "Name skeletons"
```

### What each category controls

- `[fluids]` covers bucket pickup, bucket placement, world flow enforcement, submersion feedback,
  and recipe-viewer presentation where supported.
- `[crops]` covers planting, growth, bonemeal, and harvest paths.
- `[enchants].locked` gates an enchantment entirely. `max_levels` permits it only up to the listed
  level until the stage is owned.
- `[beacon]` suppresses selected beacon effects only for the missing player.
- `[brewing]` blocks taking selected brewed potions from brewing output slots and protects hopper
  extraction on a nearest-player basis.
- `[screens]` gates block menus and held-item menus resolved to the selected block, item, or mod.
- `[[interactions]]` targets a particular right-click relationship instead of the whole object.

### Interaction types

| Type | Required target field | Typical use |
|---|---|---|
| `block_right_click` | `target_block` | Opening or activating one block. |
| `item_on_block` | `held_item`, `target_block` | Create-style assembly or tool-on-block behavior. |
| `item_on_entity` | `held_item`, `target_entity` | A particular item interaction with a mob. |

### Verification

Test each surface separately. A fluid existing inside a machine is not the same event as bucket
placement. An enchantment-table preview is not the same path as dungeon loot. A screen lock is not
the same as a block-use lock. If your pack relies on automation, test the exact pipe, hopper, and
machine combination used by players.

### Common mistakes

- Putting potion IDs in `[enchants]` or mob-effect IDs in `[brewing]`.
- Expecting `max_levels` to use the prefix grammar. Its value is `enchantment_id:max_level`.
- Assuming a screen lock removes a machine from the world. It only denies the selected GUI path.
- Using `target_block` for an `item_on_entity` rule.

---

## Phase 7. Gate entities, spawns, replacements, pets, trades, and professions

### Goal

Make the living world respond to progression without confusing interaction gates with spawn gates.

### Complete creature gate

```toml
[stage]
id = "monster_hunter"
display_name = "Monster Hunter"
description = "Unlock dangerous mobs, advanced pets, and specialist villagers."
icon = "minecraft:diamond_sword"
dependency = "iron_age"
category = "Combat"

[entities]
locked = ["id:minecraft:ender_dragon", "mod:cataclysm"]

[mobs]
locked_spawns = [
  "id:minecraft:creeper",
  "tag:minecraft:raiders",
  "mod:born_in_chaos_v1"
]

[[mobs.replacements]]
target = "minecraft:witch"
replace_with = "minecraft:zombie"

[pets]
locked_taming = ["id:minecraft:wolf", "mod:alexsmobs"]
locked_breeding = ["id:minecraft:wolf"]
locked_commanding = ["id:minecraft:wolf"]

[trades]
locked = ["id:minecraft:diamond_sword", "name:enchanted_book"]

[professions]
locked = [
  "id:minecraft:weaponsmith",
  "id:minecraft:armorer"
]
```

### Understand the boundaries

- `[entities]` gates player attacks and interactions with matching entity types.
- `[mobs].locked_spawns` prevents matching spawn attempts near the relevant player.
- `[[mobs.replacements]]` swaps the target at spawn time after validating the replacement.
- `[pets].locked_taming` gates taming.
- `[pets].locked_breeding` gates breeding.
- `[pets].locked_commanding` gates commands such as sit, stand, and follow.
- `[trades]` filters individual trade results.
- `[professions]` denies the entire trading GUI for matching villager professions.

Spawn gating uses the nearest applicable player within the configured spawn-check radius. If no
player is close enough, the spawn is allowed because no progression owner can be chosen safely.
This is important on multiplayer servers and for mob farms near chunk loaders.

### Replacement design

Use replacements when an empty world would feel broken. For example, replacing a late-game hostile
mob with a simpler mob keeps caves populated while preserving progression. Avoid replacement loops:

```text
creeper to zombie
zombie to creeper
```

That design is ambiguous and should not be used.

### Verification

1. Use a natural spawn test and a spawn egg test.
2. Test with one eligible and one ineligible player at different distances.
3. Attack and right-click the gated entity.
4. Attempt taming, breeding, and pet commands separately.
5. Open an armorer GUI and a farmer GUI to prove profession specificity.
6. Inspect individual trade rows to prove `[trades]` is independent.

### Common mistakes

- Expecting `[entities]` to prevent spawning.
- Expecting `[mobs].locked_spawns` to block attacking an already existing mob.
- Using an entity ID in `[professions]` instead of a villager-profession ID.
- Treating a wandering trader as a profession. Use `[trades]` for wandering traders.

---

## Phase 8. Gate dimensions, structures, regions, loot, advancements, and Curios

### Goal

Control places, generated content, rewards, and equipment slots while respecting the shared-world
model of Minecraft multiplayer.

### World progression example

```toml
[stage]
id = "ancient_explorer"
display_name = "Ancient Explorer"
description = "Enter dangerous dimensions and claim ancient structures."
icon = "minecraft:recovery_compass"
dependency = "monster_hunter"
category = "Exploration"

[dimensions]
locked = ["minecraft:the_nether", "minecraft:the_end"]

[structures]
locked_entry = [
  "minecraft:ancient_city",
  "minecraft:end_city"
]
entry_padding = 4

[structures.rules]
prevent_block_break = true
prevent_block_place = true
prevent_explosions = true
disable_mob_spawning = true

[[regions]]
dimension = "minecraft:overworld"
pos1 = [100, 40, 100]
pos2 = [160, 100, 160]
prevent_entry = true
prevent_block_break = true
prevent_block_place = true
prevent_explosions = true
disable_mob_spawning = false

[loot]
locked = [
  "id:minecraft:elytra",
  "id:minecraft:echo_shard",
  "mod:cataclysm"
]

[advancements]
locked = [
  "id:minecraft:end/root",
  "mod:cataclysm"
]

[curios]
locked_slots = ["ring", "charm", "belt"]
```

### Dimension safety model

Dimension enforcement has more than one defense. Normal travel is denied before it completes.
Post-travel checks catch modded portals that bypass the normal event. A periodic safety check
handles unusual teleport implementations. If a player arrives somewhere forbidden, the server
returns them to a safe destination.

### Structure behavior

`locked_entry` uses generated structure bounds, not a hand-written box. The player is returned to
their most recent safe position outside the structure. `entry_padding` begins repelling the player
before the exact boundary. Loot-container access inside a locked structure is gated even if the
optional destructive rules are false.

### Conditional structure permissions and End battle rules

Normal structure and dimension gates have priority zero. A later stage can override them with a
higher-priority live permission, then apply stricter rules only in the destination.

In `mage.toml`:

```toml
[structures]
locked_entry = ["minecraft:stronghold"]

[dimensions]
locked = ["minecraft:the_end"]
```

In `end_fight.toml`:

```toml
[[triggers]]
type = "kill"
target = "minecraft:wither"

[[temporary_unlocks]]
id = "end_access"
priority = 100

[temporary_unlocks.targets]
structures = ["minecraft:stronghold"]
dimensions = ["minecraft:the_end"]

[[temporary_locks]]
id = "end_battle_rules"
priority = 200

[temporary_locks.when]
dimensions = ["minecraft:the_end"]

[temporary_locks.targets]
items = ["minecraft:diamond_pickaxe"]
abilities = ["jump", "elytra"]
```

`[[triggers]]` permanently grants End Fight. The temporary unlock is a live permission while that
stage is owned. The End battle rule is live only in the End. See
[TEMPORARY_AND_TRIGGERED_LOCKS.md](TEMPORARY_AND_TRIGGERED_LOCKS.md) for every context, target,
priority rule, structure weapon example, and combat timer.

### Region behavior

Regions are explicit three-dimensional boxes. They are best for protected laboratories, arena
builds, quest areas, or server landmarks. `pos1` and `pos2` may be supplied in either order.
Always include the intended dimension.

### Loot coverage

The global loot modifier filters loot-table output, including chests, fishing, archaeology, mob
drops, and block drops. Lootr receives an additional per-player filter when installed. A mod that
creates items without a loot table may require its own compatibility path.

### Curios coverage

`locked_slots` contains Curios slot identifiers, not items. If a player lacks the stage, matching
equipped stacks are ejected. To gate a particular Curio item everywhere, also list the item under
`[items]`.

### Verification

1. Test vanilla and modded portals in both directions while the stage is missing.
2. Walk toward a locked structure and confirm the padding and last-safe-position behavior.
3. Enter every face of the configured region and test every enabled rule inside it.
4. Roll chest, mob, block, fishing, archeology, and Lootr loot used by the pack.
5. Open the advancement screen before and after granting the stage.
6. Equip an item in every gated Curios slot, then revoke the stage and confirm safe ejection.
7. Test every conditional location rule one block outside, on the boundary, and inside its target.
8. Confirm the priority one hundred permission defeats the normal gate and priority two hundred
   restriction defeats that permission only where intended.

### Common mistakes

- Using a structure ID as a dimension ID.
- Reversing region minimum and maximum corners.
- Assuming a structure rule changes world generation. It controls player behavior in the shared
  generated structure.
- Expecting a Curios slot lock to match an item tag.
- Forgetting to test a modded portal in both directions.

---

## Phase 9. Configure ore disguises and encrypted blocks

### Goal

Hide world information without creating per-player world generation. Every player shares the same
server block, but an ineligible client can be shown a safer substitute and receive a guarded drop.

### Exact ore override

```toml
[stage]
id = "diamond_age"
display_name = "Diamond Age"
description = "Reveal diamond ore and diamond technology."
icon = "minecraft:diamond"
dependency = "iron_age"
category = "Survival"

[[ores.overrides]]
target = "minecraft:diamond_ore"
display_as = "minecraft:stone"
drop_as = "minecraft:cobblestone"

[[ores.overrides]]
target = "minecraft:deepslate_diamond_ore"
display_as = "minecraft:deepslate"
drop_as = "minecraft:cobbled_deepslate"

[enforcement]
ore_spoof_radius = 12
```

The target, display, and drop values must be exact block IDs. Prefix selectors are not supported
inside an override because the server needs one deterministic substitute for each target.

### Encrypt all exact locked blocks for a stage

```toml
[blocks]
locked = [
  "id:create:mechanical_press",
  "id:create:mechanical_mixer"
]

[display]
encrypt_blocks = true
encrypt_as = "minecraft:stone"
```

`encrypt_blocks` reuses the visual substitution pipeline for the exact-ID blocks gated by the
stage. The normal `[blocks]` enforcement still controls placement and interaction.

### Why targeted refresh matters

When a stage or creative-bypass state changes, ProgressiveStages refreshes only the affected block
states. It does not unload the chunk under the player. This prevents camera falling, ground
clipping, and snapback during a live progression change.

### Verification

1. Place test ore naturally or through a controlled test world.
2. Observe it without the stage.
3. Break it and verify the guarded substitute drop.
4. Grant the stage while standing in the same chunk.
5. Verify the ore is revealed without a camera dip or chunk flash.
6. Switch into and out of creative if creative bypass is enabled.
7. Repeat with two players who own different stages.

### Common mistakes

- Trying to personalize actual world generation per player.
- Using a tag in `target`, `display_as`, or `drop_as`.
- Selecting air as the substitute.
- Setting an enormous spoof radius without measuring network and scan cost.

---

## Phase 10. Build automatic unlocks with per-stage trigger rules

### Goal

Place unlock rules in the stage file they grant. The retired global `triggers.toml` is ignored and
must not be recreated.

### One route with multiple required conditions

```toml
[stage]
id = "iron_age"
display_name = "Iron Age"
dependency = "stone_age"

[[triggers]]
mode = "all_of"
description = "Mine iron and prove basic crafting knowledge."

[[triggers.conditions]]
type = "mine"
block = "minecraft:iron_ore"
count = 12

[[triggers.conditions]]
type = "craft"
item = "minecraft:stone_pickaxe"
count = 1

[[triggers.conditions]]
type = "level"
count = 10
```

All three conditions must pass because the rule uses `all_of`.

### One rule where any condition is enough

```toml
[[triggers]]
mode = "any_of"
description = "Enter the Nether or complete the portal advancement."

[[triggers.conditions]]
type = "dimension"
dimension = "minecraft:the_nether"

[[triggers.conditions]]
type = "advancement"
advancement = "minecraft:story/enter_the_nether"
```

### Multiple independent routes

Separate `[[triggers]]` blocks are OR-connected. Finishing any whole rule grants the stage:

```toml
[[triggers]]
mode = "all_of"
description = "Combat route"
conditions = [
  { type = "kill", entity = "minecraft:zombie", count = 50 },
  { type = "kill", entity = "minecraft:skeleton", count = 25 }
]

[[triggers]]
mode = "all_of"
description = "Exploration route"
conditions = [
  { type = "biome", biome = "minecraft:desert" },
  { type = "distance", movement = "walk", count = 5000 }
]
```

The inline `conditions` form and nested `[[triggers.conditions]]` form are equivalent. Use nested
tables for beginner readability and inline conditions for compact files.

### How dependencies and triggers interact

The trigger evaluator may observe progress before dependencies are satisfied. The stage is not
granted until the dependency policy also permits it. Use `/stage progress <stage>` to see both the
dependency state and live trigger progress.

### Verification

```text
/stage progress iron_age @s
/stage progress next @s
/progressivestages triggers list @s
/stage simulate @s
```

Perform one condition at a time and rerun the commands. The stage map inspector also shows each
route, current progress, and threshold.

### Common mistakes

- Putting several intended alternative routes into one `all_of` rule.
- Assuming separate `[[triggers]]` blocks are AND-connected. They are OR-connected.
- Using the global config for stage-owned triggers.
- Misspelling the subject key, such as `item` where a `kill` condition expects `entity`.

---

## Phase 11. Use every trigger condition type

### Goal

Choose the correct condition and understand whether its progress is retroactive, live state,
event-counted, one-shot persisted, or supplied by an external provider.

### Complete condition catalog

| Type | Example keys | Meaning and unit |
|---|---|---|
| `kill` | `entity="minecraft:zombie", count=10` | Matching mobs killed. Entity tags work. |
| `mine` | `block="minecraft:diamond_ore", count=5` | Matching blocks mined. Block tags work. |
| `craft` | `item="minecraft:iron_pickaxe", count=1` | Matching items crafted. |
| `pickup` | `item="minecraft:emerald", count=32` | Matching items picked up. |
| `use` | `item="minecraft:ender_pearl", count=8` | Matching item uses. |
| `drop` | `item="minecraft:rotten_flesh", count=16` | Matching items dropped. |
| `break_item` | `item="minecraft:iron_pickaxe", count=1` | Matching tools broken. |
| `distance` | `movement="walk", count=5000` | Blocks traveled by the selected movement statistic. |
| `stat` | `stat="minecraft:jump", count=100` | Raw vanilla custom-stat value. |
| `play_time` | `count=60` | Minutes played. |
| `level` | `count=30` | Current experience level. |
| `xp` | `count=1500` | Total experience points. |
| `advancement` | `advancement="minecraft:story/mine_diamond"` | Advancement completed. |
| `dimension` | `dimension="minecraft:the_nether"` | Dimension visited. One-shot progress persists. |
| `biome` | `biome="minecraft:desert"` | Biome visited. Biome tags work. |
| `has_item` | `item="minecraft:diamond", count=5` | Current matching inventory count. |
| `effect` | `effect="minecraft:strength"` | Player currently has the effect. |
| `breed` | `entity="minecraft:cow", count=5` | Matching breeding events. Entity is optional. |
| `day_count` | `count=7` | World day has reached the threshold. |
| `world_time` | `count=13000` | Current time of day has reached the tick threshold. |
| `weather` | `weather="thunder"` | Player experienced clear, rain, or thunder. |
| `enter_structure` | `structure="minecraft:village_plains"` | Entered the generated structure. |
| `tame` | `entity="minecraft:wolf", count=3` | Matching animals tamed. Entity is optional. |
| `kill_with` | `entity="minecraft:ender_dragon", with="minecraft:diamond_sword"` | Kills made using the selected item. Victim tags work. |
| `reach_y` | `count=200` | Current Y coordinate is at least the threshold. |
| `fish` | `count=20` | Fish-caught statistic. |
| `sleep` | `count=5` | Times slept in a bed. |
| `ride` | `count=1000` | Blocks traveled while riding. |
| `biome_time` | `biome="minecraft:desert", duration="10m"` | Seconds accumulated in the biome or biome tag. |
| `stage_held_for` | `stage="iron_age", duration="2d"` | Real time the selected stage has been owned. |
| `custom_counter` | `counter="factory_quests", count=12` | Named value controlled by commands, Java, or KubeJS. |
| `scoreboard` | `objective="reputation", count=100` | Current scoreboard objective value. |
| `health` | `count=20` | Current health value. |
| `food` | `count=18` | Current food level. |
| `stage_count` | `count=5` | Number of stages currently owned. |
| `online_team_size` | `count=3` | Online members in the effective progression team. |
| `script` | `id="has_reputation"` | Boolean provider registered by KubeJS. |
| `script_value` | `id="reputation", count=100` | Numeric provider registered by KubeJS. |

### Duration example

```toml
[[triggers]]
mode = "all_of"
description = "Live in the desert and retain Iron Age knowledge."
conditions = [
  { type = "biome_time", biome = "#minecraft:is_badlands", duration = "15m" },
  { type = "stage_held_for", stage = "iron_age", duration = "2h" }
]
```

Friendly duration units are seconds, minutes, hours, and days through `s`, `m`, `h`, and `d`.
Bare numeric `count` for these conditions is measured in seconds.

### Retroactive and live-state guidance

- Vanilla statistics such as fishing, sleeping, distance, and play time can be checked from
  existing player data.
- `has_item`, `health`, `food`, `level`, scoreboard, and team-size conditions are current state.
- Dimension and biome visits use persisted one-shot progress so leaving does not erase the visit.
- Target-specific breed, tame, and kill-with tracking depends on events observed by the mod.
- Script providers exist only after scripts register them.

### Verification

1. Choose one retroactive statistic and prove an existing player receives prior credit.
2. Choose one live-state condition and prove its progress falls when the state falls.
3. Choose one persisted visit condition, visit the target, restart, and confirm the visit remains.
4. Choose one event-counted condition and confirm only matching events increase it.
5. Run `/stage progress <stage> @s` after each step and compare current values with thresholds.

### Common mistakes

- Giving `play_time` seconds when it expects minutes.
- Treating `world_time` as total world age. It is the time-of-day value.
- Using `duration` without quotes.
- Using an item tag where a condition only documents an exact item ID.
- Expecting a script provider to exist when KubeJS failed to load the registering script.

---

## Phase 12. Connect commands, scoreboards, counters, and KubeJS providers

### Goal

Allow quests, machines, scripts, and other mods to feed progress into a declarative stage without
running an arbitrary expression every tick.

### Named counter from commands

Stage file:

```toml
[[triggers]]
mode = "all_of"
description = "Complete twelve factory objectives."
conditions = [
  { type = "custom_counter", counter = "factory_quests", count = 12 }
]
```

Command workflow:

```text
/stage counter get @s factory_quests
/stage counter add @s factory_quests 1
/stage counter set @s factory_quests 11
/stage counter add @s factory_quests 1
/stage counter reset @s factory_quests
```

Add and set immediately request trigger reevaluation. Use a stable lowercase counter name shared by
the stage file and every integration that mutates it.

### Scoreboard condition

```toml
[[triggers]]
mode = "all_of"
description = "Reach one hundred reputation."
conditions = [
  { type = "scoreboard", objective = "reputation", count = 100 }
]
```

Create and update the vanilla objective:

```text
/scoreboard objectives add reputation dummy
/scoreboard players set @s reputation 100
/stage simulate @s
```

### Boolean KubeJS provider

`kubejs/server_scripts/progressive_stages.js`:

```javascript
ProgressiveStages.condition('holding_diamond', player => {
    return player.getMainHandItem().id == 'minecraft:diamond'
})
```

Stage file:

```toml
[[triggers]]
mode = "all_of"
description = "Hold a diamond in the main hand."
conditions = [
  { type = "script", id = "holding_diamond" }
]
```

### Numeric KubeJS provider

```javascript
ProgressiveStages.progressCondition('reputation', player => {
    return player.persistentData.getInt('reputation')
})
```

```toml
[[triggers]]
mode = "all_of"
description = "Earn two hundred script reputation."
conditions = [
  { type = "script_value", id = "reputation", count = 200 }
]
```

Call `ProgressiveStages.evaluate(player)` after changing script-owned state that the engine cannot
observe automatically. Provider registrations and lifecycle callbacks are cleared and rebuilt on
server-script reload, preventing duplicate handlers.

### API-started timed rule

Declare a manual timer in a stage file:

```toml
[[triggered_locks]]
id = "factory_emergency"
trigger = "manual"
duration = "30s"

[triggered_locks.targets]
items = ["tag:mypack:factory_tools"]
```

Start it from a server script only when the real event occurs:

```javascript
ProgressiveStages.activateRule(player, 'factory_stage/factory_emergency')
```

This event-driven call avoids evaluating arbitrary expressions every tick. Inspect it with
`/pstages rule list <player>` and stop it with `ProgressiveStages.clearRule` when the external event
ends early.

### Verification

1. Add one point with `/stage counter add` and confirm `/stage counter get` changes immediately.
2. Set the scoreboard below and above its threshold and compare `/stage simulate @s`.
3. Reload KubeJS server scripts and confirm both provider IDs register without an error.
4. Change the boolean and numeric provider state, call `ProgressiveStages.evaluate(player)`, and
   inspect `/stage progress <stage> @s`.
5. Restart the server and confirm named counters persist while script callbacks register once.
6. Activate a manual timed rule, verify its remaining seconds, let it expire, and test explicit clear.

### Common mistakes

- Incrementing a counter for every player when the intended subject is one quest completer.
- Reusing the same counter name for unrelated meanings.
- Forgetting to reevaluate after changing arbitrary script state.
- Registering providers in a client script instead of a server script.
- Performing expensive world scans inside a provider.

---

## Phase 13. Author the vanilla-style progression map

### Goal

Make progression understandable in game with a draggable, wheel-scrollable, advancement-inspired
map rather than forcing players to read raw config files.

### Display example

```toml
[stage]
id = "advanced_engineering"
display_name = "Advanced Engineering"
description = "Combine mechanical, electrical, and chemical research."
icon = "minecraft:comparator"
dependency = ["mechanical_power", "electrical_power", "chemical_processing"]
dependency_mode = "at_least"
dependency_count = 2
category = "Technology"
color = "#55FFFF"
tags = ["technology", "tier4"]

[display]
x = 240
y = 80
frame = "challenge"
background = "minecraft:textures/block/deepslate_tiles.png"
reveal = "dependencies"
sort_order = 40
display_as_unknown_item = true
obscure_icon = true
show_tooltip = true
show_description_on_tooltip = true
```

### Display fields

| Field | Meaning |
|---|---|
| `x`, `y` | Explicit canvas position. Omit both for automatic dependency layout. |
| `frame` | `task`, `goal`, or `challenge`. |
| `background` | Namespaced client texture used for the tiled map background. |
| `reveal` | `always`, `dependencies`, or `unlocked`. |
| `sort_order` | Stable author-controlled ordering hint. |
| `display_as_unknown_item` | Masks locked item names. |
| `obscure_icon` | Replaces locked item icons with the unknown presentation. |
| `show_tooltip` | Shows lock information in native tooltips. |
| `show_description_on_tooltip` | Adds the stage description to that tooltip. |

Texture examples:

```toml
background = "minecraft:textures/block/stone.png"
background = "minecraft:block/deepslate_tiles"
background = "mypack:textures/gui/progression.png"
background = "mypack:gui/progression"
```

The resource must exist in a client resource pack under the matching namespace and path.

### Player controls

- `/stage`, `/stages`, `/pstages`, and `/stage gui` open the map.
- The configurable progression-tree keybind opens it.
- The lock-shaped inventory button beside the recipe-book button opens it.
- Drag from empty space or from a node to pan.
- Use the mouse wheel, arrow keys, or WASD to move across the graph.
- Hover a node for its summary.
- Click without dragging to pin details.
- Search by stage text or gated item ID.
- Use the Owned control to hide completed stages.
- Cycle categories to reduce a large graph.

The pinned inspector shows dependencies, grouped trigger routes and progress, lock previews, costs,
and the purchase button when applicable. It renders above nodes and icons, so underlying stage art
does not overlap the detail page. Menu blur is rendered below the stage interface.

### Layout advice

- Let automatic layout handle the first working version.
- Add explicit coordinates only for important storytelling or to untangle a dense branch.
- Keep at least one node-width of horizontal space between adjacent routes.
- Use categories for major tabs such as Survival, Magic, Technology, and Exploration.
- Use the same background inside one visual chapter.
- Test small, medium, and large GUI scales.

### Verification

1. Open `/pstages` with menu blur enabled and disabled.
2. Drag from empty space and from a node, then scroll with the wheel, WASD, and arrow keys.
3. Hover every node state and confirm its tooltip is readable.
4. Pin a node with multiple trigger routes and confirm no other icon renders over the inspector.
5. Search by stage name and by a locked item ID.
6. Toggle Owned, cycle categories, and test every background resource at three GUI scales.
7. Open the map from the inventory lock button and the configured keybind.

### Common mistakes

- Supplying `x` without `y`.
- Pointing `background` to a server filesystem path instead of a resource location.
- Hiding a stage that players need to discover visually.
- Using only color to communicate state without descriptive names and tooltips.

---

## Phase 14. Add purchases, rewards, unlock effects, attributes, and abilities

### Goal

Turn a stage into a complete milestone with an optional price, an authoritative grant reward,
player-facing celebration, persistent-while-owned attribute changes, and gated movement abilities.

### Complete purchasable milestone

```toml
[stage]
id = "diamond_age"
display_name = "Diamond Age"
description = "Purchase or earn access to diamond technology."
icon = "minecraft:diamond"
dependency = "iron_age"
category = "Survival"

[cost]
xp_levels = 20
items = ["minecraft:emerald:16", "minecraft:gold_ingot:8"]
bypass_requirements = false
cooldown = "5m"
refund_percent = 50

[rewards]
items = ["minecraft:diamond:3", "minecraft:experience_bottle:8"]
effects = ["minecraft:haste:120:0", "minecraft:regeneration:10:1"]
commands = [
  "title {player} actionbar {\"text\":\"Diamond technology online\",\"color\":\"aqua\"}"
]
xp_levels = 3
xp_points = 100

[unlock]
toast = "Diamond Age reached"
title = "&b&lDIAMOND AGE"
subtitle = "&7A new era begins"
sound = "minecraft:ui.toast.challenge_complete"
particle = "minecraft:totem_of_undying"
progress_nudges = true
hud_bar = true

[[attribute]]
id = "minecraft:generic.max_health"
operation = "add"
amount = 4.0

[[attribute]]
id = "minecraft:generic.movement_speed"
operation = "multiply_base"
amount = 0.10

[abilities]
locked = ["elytra", "sprint", "swim", "climb"]
```

### Cost behavior

- `xp_levels` removes levels when the purchase succeeds.
- Each item cost uses `item_id:count`.
- `bypass_requirements = false` keeps trigger requirements meaningful.
- `bypass_requirements = true` permits payment to replace the trigger grind after dependency and
  other server checks.
- `cooldown` limits repeated purchase operations for that player.
- `refund_percent` returns that percentage of eligible item and experience cost if a purchased
  stage is later revoked.

The stage-map purchase button sends a request. The server checks ownership, dependencies, trigger
policy, inventory, experience, and cooldown before changing anything.

### Reward behavior

Rewards execute on a real grant, not on login synchronization. Commands run through the controlled
reward path with `{player}` substituted. Keep commands deterministic and test them on a dedicated
server. Use `command = "..."` for one command or `commands = ["...", "..."]` for several.

Teleport reward format:

```toml
[rewards]
teleport = "minecraft:the_nether 0 70 0"
```

The dimension is optional if the reward should remain in the current dimension.

### Attribute behavior

Attribute modifiers exist while the effective owner has the stage. They are transient, restored on
login and respawn, and removed on revoke. Supported operations are `add`, `multiply_base`, and
`multiply_total`. Any registered vanilla or modded attribute ID may be used.

Unchanged modifiers are preserved during unrelated stage synchronization. This is especially
important for `minecraft:generic.scale`, because detaching and reattaching a scale modifier could
briefly move the camera or eye height.

### Ability behavior

The listed ability remains gated until this stage is owned. `jump`, `elytra`, `sprint`, `swim`, and `climb`
are recognized. This is independent from locking the equipment item itself.

### Verification

1. Attempt a purchase with missing dependencies, triggers, items, and XP, one failure at a time.
2. Complete the requirements, purchase once, and verify the exact deductions and rewards.
3. Confirm toast, title, subtitle, sound, particle, nudges, and HUD bar independently.
4. Check every attribute before grant, after grant, after relog, after respawn, and after revoke.
5. Test each listed ability before and after ownership.
6. Revoke a purchased stage and confirm the configured refund percentage and cooldown behavior.

### Common mistakes

- Using `minecraft:diamond:0`. Item counts must be positive.
- Expecting `[rewards]` to repeat on every login.
- Treating an amplifier as a human-facing level. Amplifier zero is effect level one.
- Using `0.10` with `multiply_total` without understanding stacking with other modifiers.
- Locking elytra flight but forgetting whether the elytra item should also be under `[items]`.

---

## Phase 15. Add revocation, temporary ownership, scope, tags, categories, and bulk control

### Goal

Model regression and administrative grouping without losing clarity about who owns a stage.

### Temporary and conditional stage

```toml
[stage]
id = "battle_focus"
display_name = "Battle Focus"
description = "A temporary combat certification."
icon = "minecraft:netherite_sword"
dependency = "monster_hunter"
duration = "2h"
scope = "team"
category = "Combat"
tags = ["combat", "temporary", "certification"]

[revoke]
on_death = true
xp_below = 15
cascade = true
```

Do not confuse a temporary stage with a temporary lock rule. `[stage].duration` persists an expiry
timestamp and removes stage ownership when time expires. `[[temporary_locks]]` is a live context
rule with no timer. `[[triggered_locks]]` is a transient runtime timer and clears on logout or server
stop. Use stage duration for progression ownership and conditional rules for situational access.

The stage expires two real hours after grant, including offline time. It is also revoked on death
or when the maintained total-XP threshold falls below 15 points. `cascade = true` allows
descendants that depend on the revoked stage to regress with it.

### Team scope and server scope

```toml
[stage]
id = "server_first_dragon"
display_name = "The Dragon Has Fallen"
scope = "server"
tags = ["server_first", "boss"]
category = "Global Milestones"
```

- `scope = "team"` uses the normal effective team owner. Solo mode gives each player their own
  single-member owner.
- `scope = "server"` is global. The first qualifying team unlocks it for everyone.

Think carefully before combining server scope with consumable purchases or per-player rewards.
The stage ownership is global, but a reward still needs a concrete grant recipient.

### Tags and categories

Tags are machine-friendly bulk groupings. Categories are presentation groupings used by the map
and can also be targeted by commands.

```text
/stage tag list combat
/stage tag grant @a combat
/stage tag revoke @a temporary
/stage category list "Global Milestones"
/stage category grant @a "Global Milestones"
```

Tag grants intentionally bypass dependencies and skip already-owned stages. Use them for
administration, migrations, or deliberate bulk unlocks, not as a substitute for normal player
progression.

### Full-set operations

```text
/stage bulk grant @s
/stage bulk revoke @s
/stage sync @a
```

Bulk mutations are operator tools. `sync` resends definitions, locks, ownership, and bypass state;
it does not grant anything.

### Verification

1. Grant the team-scoped example to one team member and check another online member.
2. Grant the server-scoped example and check players from two different teams.
3. Restart during a temporary stage and confirm offline time counts toward expiry.
4. Trigger death and total-XP revocation separately, with cascade both false and true.
5. Run tag, category, and bulk operations against a disposable test player, then verify the exact
   changed set with `/stage list`.
6. Run `/stage sync` and prove it does not alter ownership.

### Common mistakes

- Assuming `duration = "2h"` counts only online time.
- Using cascade revocation without previewing the dependency descendants.
- Confusing a display category with a tag.
- Using bulk or tag bypass operations during normal gameplay and unintentionally skipping the
  dependency graph.

---

## Phase 16. Operate and debug the pack with commands

### Goal

Use the public player commands, operator mutation commands, authoring helpers, counters, validation,
reload tools, and integration diagnostics correctly.

### Player-facing commands

| Command | Use |
|---|---|
| `/stage`, `/stages`, `/pstages`, `/stage gui` | Open the progression map. |
| `/stage list [player]` | List owned stages. |
| `/stage check <player> <stage>` | Test ownership. |
| `/stage info <stage>` | Show definition metadata and lock count. |
| `/stage tree` | Show the dependency tree in chat. |
| `/stage progress` | Show reachable next stages for the caller. |
| `/stage progress next [player]` | Show every reachable unowned stage. |
| `/stage progress all [player]` | Show every unowned stage, including dependency-blocked ones. |
| `/stage progress <stage> [player]` | Show one stage's dependency and trigger progress. |
| `/stage simulate [player]` | Dry-run reachable, short, and dependency-blocked stages. |

### Mutation and grouping commands

```text
/stage grant @s iron_age
/stage revoke @s iron_age
/stage tag grant @a technology
/stage tag revoke @a temporary
/stage category grant @a "Exploration"
/stage category revoke @a "Exploration"
/stage bulk grant @s
/stage bulk revoke @s
/stage sync @a
```

Grant and revoke require operator permission. A normal grant follows configured dependency behavior.
Tag bulk grants are explicit bypass tools.

### Authoring commands

```text
/stage new mypack:technology/electricity
/stage export
/progressivestages validate
/progressivestages reload
```

`/stage new` refuses to overwrite an existing file. Edit the scaffold, validate it, then reload.
`/stage export` writes a Markdown guide into the ProgressiveStages config folder with dependencies,
dependents, trigger descriptions, and purchase state.

### Trigger and counter commands

```text
/progressivestages triggers list @s
/progressivestages trigger reset @s ancient_explorer
/stage counter get @s factory_quests
/stage counter add @s factory_quests 1
/stage counter set @s factory_quests 12
/stage counter reset @s factory_quests
```

The trigger reset command clears persisted one-shot visit progress for the selected stage. It is not
a general ownership revoke.

### Conditional rule commands

```text
/pstages rule info progressivestages:end_fight/end_battle_rules
/pstages rule list @s
/pstages rule activate @s progressivestages:end_fight/manual_permission
/pstages rule activate @s progressivestages:end_fight/manual_permission 60
/pstages rule clear @s progressivestages:end_fight/manual_permission
/pstages rule clearall @s
```

`info` and self `list` are player-facing. Another player's list and every mutation require operator
permission. ProgressiveStages intentionally does not register `/ps`.

### Integration and preference commands

```text
/progressivestages ftb status @s
/progressivestages no-creative-popup
```

The FTB status report separates configuration, provider registration, active compatibility, queued
rechecks, and the selected player's current stage view. The creative-popup command changes only the
calling player's warning preference.

### Recommended debugging sequence

1. Run `/progressivestages validate`.
2. Run `/stage info <stage>`.
3. Run `/stage progress <stage> <player>`.
4. Run `/stage check <player> <dependency>` for each missing dependency.
5. Run `/stage simulate <player>`.
6. Enable `debug_logging` only while collecting evidence.
7. Run `/progressivestages reload` and read the server log.
8. Run `/stage sync <player>` if only client presentation looks stale.

### Verification

1. Run every player-facing command as a non-operator and confirm mutation commands remain denied.
2. Run every mutation command as an operator against a disposable player.
3. Create a scaffold with `/stage new`, validate it, reload it, then export the graph.
4. Exercise counter get, add, set, and reset and compare each returned value.
5. Intentionally add one invalid file and confirm validation explains it without replacing live
   state.
6. Run the FTB status command with the integration absent and present.
7. Inspect, activate, list, expire, clear, and clear all conditional timers.

### Common mistakes

- Using a conflicting short command instead of `/pstages`.
- Reloading before saving the edited file.
- Granting a stage to hide a broken trigger instead of inspecting progress.
- Confusing client synchronization with ownership mutation.

---

## Phase 17. Automate through KubeJS and the Java API

### Goal

Use supported public surfaces so scripts and other mods receive the same ownership semantics,
events, causes, rewards, attributes, quest rechecks, and client synchronization as commands.

### KubeJS lifecycle example

Create `kubejs/server_scripts/progressive_stages.js`:

```javascript
ProgressiveStages.onGranted((player, stage) => {
    player.tell(`Unlocked ${stage}`)
})

ProgressiveStages.onRevoked((player, stage) => {
    player.tell(`Lost ${stage}`)
})

ProgressiveStages.onChanged((player, stage, change, cause, teamId) => {
    console.info(`[ProgressiveStages] ${change} ${stage} cause ${cause} owner ${teamId}`)
})
```

These callbacks observe engine changes from commands, triggers, purchases, regressions, quests, and
scripts. Do not depend on a nonexistent native KubeJS stage-added event.

### KubeJS query and mutation example

```javascript
PlayerEvents.loggedIn(event => {
    let player = event.player

    if (ProgressiveStages.has(player, 'iron_age')) {
        player.tell('Iron Age is already owned.')
    }

    let missing = ProgressiveStages.missingDependencies(player, 'diamond_age')
    if (missing.length == 0 && ProgressiveStages.available(player, 'diamond_age')) {
        player.tell('Diamond Age is now reachable.')
    }
})
```

Mutation calls include:

```javascript
ProgressiveStages.grant(player, 'iron_age')
ProgressiveStages.revoke(player, 'iron_age')
ProgressiveStages.toggle(player, 'iron_age')
ProgressiveStages.grantBypass(player, 'iron_age')
ProgressiveStages.grantMany(player, ['stone_age', 'iron_age'])
ProgressiveStages.revokeMany(player, ['stone_age', 'iron_age'])
ProgressiveStages.grantTag(player, 'technology')
ProgressiveStages.revokeTag(player, 'temporary')
ProgressiveStages.addCounter(player, 'factory_quests', 1)
ProgressiveStages.evaluate(player)
ProgressiveStages.sync(player)
ProgressiveStages.openGui(player)
ProgressiveStages.activateRule(player, 'end_fight/manual_permission', 60)
ProgressiveStages.clearRule(player, 'end_fight/manual_permission')
ProgressiveStages.clearRules(player)
```

Queries include `exists`, `has`, `hasAll`, `hasAny`, `owned`, `all`, `locked`,
`availableStages`, `dependencies`, `missingDependencies`, `allDependencies`, `dependents`,
`allDependents`, `withTag`, `withCategory`, `categories`, `info`, `progress`, `percent`, and
`counter`.
Conditional rule discovery and timer queries use `ruleIds()`, `ruleInfo(id)`, and
`activeRules(player)`.

KubeJS also receives `player.stages.has`, `player.stages.add`, and `player.stages.remove`. The
dedicated `ProgressiveStages` object is preferable for rich queries, causes, counters, and callbacks.

### Java API query and mutation example

```java
import com.enviouse.progressivestages.common.api.ProgressiveStagesAPI;
import com.enviouse.progressivestages.common.api.StageCause;
import com.enviouse.progressivestages.common.api.StageId;

StageId ironAge = StageId.parse("iron_age");

boolean exists = ProgressiveStagesAPI.stageExists(ironAge);
boolean owned = ProgressiveStagesAPI.hasStage(player, ironAge);

if (exists && !owned && ProgressiveStagesAPI.isAvailable(player, ironAge)) {
    boolean changed = ProgressiveStagesAPI.grantStage(player, ironAge, StageCause.API);
}

var missing = ProgressiveStagesAPI.getMissingDependencies(player, ironAge);
var progress = ProgressiveStagesAPI.getTriggerProgress(player, ironAge);
long factoryProgress = ProgressiveStagesAPI.addCounter(player, "factory_quests", 1);
boolean started = ProgressiveStagesAPI.activateConditionalRule(player,
    "progressivestages:end_fight/manual_permission", 60_000L);
var timedRules = ProgressiveStagesAPI.getActiveConditionalRules(player);
```

Mutations must run on the logical server thread. Use an accurate `StageCause` so listeners know why
the change occurred.

### Java event example

```java
import com.enviouse.progressivestages.common.api.StageCause;
import com.enviouse.progressivestages.common.api.StageChangeEvent;
import com.enviouse.progressivestages.common.api.StageChangeType;
import com.enviouse.progressivestages.common.api.StageId;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;

import java.util.UUID;

@SubscribeEvent
public static void onStageChanged(StageChangeEvent event) {
    ServerPlayer player = event.getPlayer();
    UUID owner = event.getTeamId();
    StageId stage = event.getStageId();
    StageChangeType change = event.getChangeType();
    StageCause cause = event.getCause();
}
```

### Verification

1. Register the lifecycle callbacks once and grant, revoke, purchase, and auto-trigger a stage.
2. Confirm every actual change reports one callback with the expected cause.
3. Exercise every KubeJS query used by the pack with an existing and missing stage ID.
4. Exercise normal, bypass, bulk, tag, counter, evaluate, sync, and GUI mutation helpers.
5. Compile the Java integration against the public API and run it on the logical server thread.
6. Confirm the Java event receives the same stage, change type, cause, player, and effective owner
   observed by the script callback.
7. Start and clear a conditional timer through both KubeJS and Java and compare remaining time.

### Common mistakes

- Changing internal storage directly instead of calling the API.
- Calling server mutations from a client-only event.
- Granting every tick rather than reacting to a real event or counter update.
- Omitting the cause in Java integration code.
- Registering KubeJS callbacks repeatedly outside the normal script reload lifecycle.

---

## Phase 18. Configure optional integrations

### Goal

Add teams, quests, recipe viewers, overlays, per-player loot, equipment slots, and machine shims
without making any of them a core dependency.

### FTB Teams

Use the global ownership mode:

```toml
[general]
team_mode = "ftb_teams"

[integration.ftbteams]
enabled = true
```

Progression is stored against the effective FTB team. Online members synchronize after grants and
revokes. If FTB Teams is absent or disabled, core loads and uses solo ownership.

Test with two real clients. Grant a team-scoped stage to one member, check the other member, relog
both clients, leave and rejoin the team, then restart the server.

### FTB Quests

ProgressiveStages provides the stage helper expected by the FTB suite. Existing FTB Quest stage
tasks and rewards can query and mutate ProgressiveStages ownership. Quests and chapters may use the
native `required_stage` property supplied by the compatibility mixins.

Recommended design:

1. Let FTB Quests own narrative, tasks, chapters, and general rewards.
2. Let ProgressiveStages own content gates and progression state.
3. Use a Game Stage reward to grant or remove the ProgressiveStages stage.
4. Use `/progressivestages ftb status <player>` when a task or chapter looks stale.

Do not build a second quest book inside ProgressiveStages.

### EMI and JEI

Both recipe viewers are optional. They use the authoritative client lock snapshot and stage cache to
hide or annotate gated items, fluids, and recipes. When a stage or creative-bypass state changes,
the integration refreshes its runtime presentation.

Test these combinations separately:

```text
No recipe viewer
EMI only
JEI only
EMI and JEI if your pack deliberately ships both
```

Also test `show_locked_recipes = true` and `false`.

### Jade and WTHIT

When installed, looking at a locked block or entity can show the missing stage information. The
provider reads synchronized client data, so it works on a dedicated server without reading the
server registry from the client.

### Lootr

Lootr receives both the normal global-loot filtering behavior and a per-player filter provider.
Test the same chest with two players who have different stages. Each player's Lootr inventory must
respect their effective ownership.

### Curios

Curios slot locks become active only when Curios is installed. Test equip attempts, already-equipped
items on login, stage revoke, and full inventory fallback behavior.

### Mekanism, Nature's Compass, and Visual Workbench

- Mekanism compatibility adds focused entity and block handling, but arbitrary pipe internals may
  not expose cancellable vanilla events.
- Nature's Compass filters locked biome searches when its supported API is present.
- Visual Workbench resolves its persistent crafting surface through a guarded compatibility shim.

### Optional-mod failure rule

Removing an optional mod must never stop ProgressiveStages core startup. If an integration fails,
core progression should remain usable and the log should explain the unavailable capability once.

### Verification

1. Start core with no optional integration mods installed.
2. Add each integration one at a time and confirm its startup status in the server log.
3. Test the exact complete mod combination intended for release.
4. Use two real clients for FTB Teams, FTB Quests, Lootr, Curios, Jade, and WTHIT behavior.
5. Remove each optional mod again and confirm core still starts without a missing-class error.
6. Record the exact tested version of every optional mod.

### Common mistakes

- Adding compile-only integration JARs to the released modpack by accident.
- Testing optional integrations only in an integrated single-player server.
- Assuming a client overlay is enforcement. The server remains authoritative.
- Hardcoding third-party classes into core code instead of using the compatibility module.

---

## Phase 19. Migrate, validate, test, package, and release

### Goal

Turn the working pack into a reproducible release with safe configuration migration, transactional
reload behavior, dedicated-server evidence, optional-mod coverage, and a known JAR hash.

### Config folder and migration rules

The supported live layout is:

```text
config/
└── progressivestages/
    ├── progressivestages.toml
    └── stages/
        ├── early_game/
        │   ├── wood_age.toml
        │   └── stone_age.toml
        ├── technology/
        │   └── electricity.toml
        └── exploration/
            └── ancient_explorer.toml
```

Version 3 migrates supported legacy root config and stage locations without overwriting an existing
destination. Keep the generated backup until the migrated world has passed multiplayer and restart
tests.

### Datapack-provided defaults

A datapack can ship stages at:

```text
data/<namespace>/progressivestages/stages/*.toml
```

Example:

```text
my_pack/
├── pack.mcmeta
└── data/
    └── mypack/
        └── progressivestages/
            └── stages/
                └── electricity.toml
```

A config-folder stage with the same stage ID wins. This makes datapacks suitable for distributable
defaults while keeping local server overrides possible.

### Safe reload workflow

1. Back up the world and `config/progressivestages`.
2. Edit or add stage files.
3. Run `/progressivestages validate`.
4. Fix every syntax error, duplicate ID, dependency cycle, unreachable stage, invalid exact trigger
   target, invalid profession ID, duplicate conditional rule ID, invalid exact conditional target,
   and missing conditional context stage.
5. Run `/progressivestages reload`.
6. Confirm the reload report and server log.
7. Run `/stage simulate` for representative players.
8. Run `/stage sync @a` only if a connected client presentation needs an explicit refresh.

The loader builds a candidate snapshot before replacing live state. A failed candidate does not
replace the previous working snapshot.

### Automated project verification

For mod development, run from the repository root:

```bash
./gradlew test --rerun-tasks --no-configuration-cache
./gradlew clean build --no-configuration-cache
```

Expected artifact:

```text
build/libs/progressivestages-3.0.1.jar
```

Inspect the JAR before release:

```bash
unzip -l build/libs/progressivestages-3.0.1.jar
sha256sum build/libs/progressivestages-3.0.1.jar
```

Confirm at minimum:

- `META-INF/neoforge.mods.toml` is present.
- `progressivestages.png` is present for the mod list.
- Mixin configuration files are present.
- Language and lock-icon resources are present.
- The artifact version matches the intended release.

### Manual single-player matrix

1. New world config generation.
2. Starting-stage grant.
3. Every lock category used by the pack.
4. Trigger progression and automatic grant.
5. Map opening through `/pstages`, keybind, and inventory button.
6. Blur enabled and disabled.
7. Dragging from empty space and a node.
8. Pinned detail inspector with icons and trigger routes.
9. Purchase success and every purchase failure mode.
10. Stage grant and game-mode change while standing over masked ore, with no camera dip.
11. Normal priority-zero gate, priority-one-hundred permission, and priority-two-hundred restriction.
12. Structure and dimension context entry and exit.
13. Combat activation, timer refresh, expiry, manual activation, clear, logout, and reconnect.

### Manual dedicated-server matrix

1. Two clients with different ownership.
2. Team scope and server scope.
3. Grant, revoke, reconnect, death respawn, and End return.
4. Server restart persistence.
5. `/reload` and ProgressiveStages reload.
6. FTB Teams membership changes.
7. FTB Quests task and chapter refresh.
8. Per-player Lootr results.
9. Ore presentation for two players in the same chunk.
10. Creative bypass entering and leaving creative mode.
11. Conditional timer isolation between two players.
12. Conditional timer cleanup on disconnect and server restart.

### Optional integration matrix

Run core alone, then each installed integration alone where practical, then the exact combination
used by the released pack. A successful compile does not prove that an optional mixin matches the
runtime version shipped by the pack.

### Release evidence record

Record:

```text
Commit.
Branch.
Tag if approved.
Minecraft version.
NeoForge version.
Java version.
Gradle command.
Automated test count and failures.
Manual matrix results.
Artifact path.
Artifact SHA 256.
Known limitations.
```

### Final release rule

Release the exact JAR built from the exact approved commit. Do not rebuild after recording the hash
unless you repeat the tests and update the evidence. Keep active phase work off the approved branch
until it passes the required gates.

### Verification

1. Confirm all automated tests report zero failures and errors.
2. Confirm every used lock category and integration passes its manual matrix.
3. Inspect the JAR contents and record its SHA 256 hash.
4. Install that exact JAR in a clean client and dedicated server.
5. Confirm the Git commit, branch, version, artifact name, and recorded hash all agree.
6. Archive the configuration backup, logs, test record, and released artifact together.

### Common mistakes

- Releasing a locally rebuilt JAR whose hash does not match the tested artifact.
- Treating a successful compile as proof that multiplayer and optional integrations work.
- Replacing the live configuration before proving that migration and reload are safe.
- Recording a branch name but not the exact commit and artifact hash.
- Publishing an active beta branch as the approved release branch before explicit approval.

---

## After phase 19

You now have a complete 3.0 pack workflow:

- Stages and dependencies define progression structure.
- Locks and enforcement define server-authoritative restrictions.
- Triggers, costs, quests, scripts, and APIs define unlock routes.
- Rewards, attributes, unlock effects, and abilities define milestone consequences.
- Scope, teams, tags, categories, and commands define multiplayer operation.
- The advancement-style map explains the result to players.
- Validation, tests, migration, and release evidence make the pack maintainable.

For individual field edge cases, return to [`DOCUMENTATION.md`](DOCUMENTATION.md). For the complete
commented stage template, use [`diamond_stage.toml`](examples/reference/diamond_stage.toml). For a
small pack that can be copied immediately, use [`examples/beginner_pack`](examples/beginner_pack/README.md).
