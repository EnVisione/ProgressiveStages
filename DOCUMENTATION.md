# ProgressiveStages 3.0 — Complete Documentation

> ProgressiveStages **3.0** for NeoForge 1.21.1, Java 21.  
> Mod id: `progressivestages`  Java package root: `com.enviouse.progressivestages`  
> This document is exhaustive — every feature, every TOML field, every config key,
> every command, every integration, every troubleshooting tip. If a section of
> this document conflicts with what the mod actually does, the *code* is the
> authority and this document is a bug.
>
> **Project, support, and community:** [CurseForge](https://www.curseforge.com/minecraft/mc-mods/progressivestages) · [Discord](https://discord.com/invite/9v4gaRSfdJ) · [Issues](https://github.com/EnVisione/ProgressiveStages/issues). The mod metadata wires in the CurseForge project and GitHub issue tracker through `displayURL` and `issueTrackerURL`.

> **New to stage mods?** Start with [GETTING_STARTED.md](GETTING_STARTED.md), copy the tested
> [beginner pack](examples/beginner_pack/README.md), and return here when the beginner guide links
> to a specific advanced feature. Release maintainers should also follow
> [TESTING.md](TESTING.md). This file is the exhaustive reference, not the shortest learning path.
> For a complete pack-building course, follow the
> [Phase 1 Through Phase 19 Guide](PHASES_1_TO_19.md). It starts with an empty installation and
> ends with migration, dedicated-server testing, artifact inspection, and release evidence. Every
> phase includes copy-ready configuration, expected behavior, verification, and common mistakes.
> Maintainers and integration authors should also read the
> [Architecture and Project Structure Guide](ARCHITECTURE.md), which follows each file from disk,
> through parsing and validation, into runtime enforcement, networking, UI, and tests.
> For the schema 4 three-file packages, secure localhost editor, all thirty rehaul feature groups,
> migration, automatic client snapshots, and copy-ready tested rehaul examples, use the
> [ProgressiveStages 3.0.1 Schema 4 and Editor Guide](REHAUL_GUIDE.md).
>
> **Want one complete file you can read and copy?** Open
> [`diamond_stage.toml`](examples/reference/diamond_stage.toml). It is the directly browsable,
> fully commented legacy one-file reference. Start with its numbered
> safe five minute edit, then use its table of contents to find every lock category and feature.
> Conditional access authors can also use the focused
> [Temporary and Triggered Locks Guide](TEMPORARY_AND_TRIGGERED_LOCKS.md).
> The equivalent machine-tested three-file schema 4 reference is the
> [`examples:diamond` package](examples/rehaul/diamond).

---

## Table of Contents

1. [What ProgressiveStages Is](#1-what-progressivestages-is)
2. [Core Concepts](#2-core-concepts)
3. [Quick Start — Your First Stage in 90 Seconds](#3-quick-start--your-first-stage-in-90-seconds)
   - [3.1 How to use the complete legacy Diamond Stage reference](#31-how-to-use-the-complete-legacy-diamond-stage-reference)
   - [3.2 No-code localhost stage editor](#32-no-code-localhost-stage-editor)
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
   - [4.15a `[trades]` — villager / wandering-trader trade gating](#415a-trades--villager--wandering-trader-trade-gating)
   - [4.15b `[professions]` — gate trading by villager profession](#415b-professions--gate-trading-by-villager-profession) — **New in 2.5**
   - [4.15c `[advancements]` — hide advancements from the screen](#415c-advancements--hide-advancements-from-the-screen) — **New in 2.5**
   - [4.16 `[structures]` — entry, rules, chest locking](#416-structures--entry-rules-chest-locking)
   - [4.17 `[[regions]]` — fixed 3D boxes with flags + debuffs](#417-regions--fixed-3d-boxes-with-flags--debuffs)
   - [4.18 `[curios]` — per-slot gating when Curios is installed](#418-curios--per-slot-gating-when-curios-is-installed)
   - [4.19 `[[ores.overrides]]` — ore masquerade](#419-oresoverrides--ore-masquerade)
   - [4.20 `[unlocks]` — per-stage carve-outs](#420-unlocks--per-stage-carve-outs)
   - [4.21 `[enforcement]` — per-stage exemptions + toggle overrides](#421-enforcement--per-stage-exemptions--toggle-overrides)
   - [4.22 `[display]` — per-stage tooltip + icon overrides](#422-display--per-stage-tooltip--icon-overrides)
   - [4.23 `[[triggers]]` — automatic stage grants](#423-triggers--automatic-stage-grants)
   - [4.24 `[attribute]` — attribute modifiers while a stage is owned](#424-attribute--attribute-modifiers-while-a-stage-is-owned) — **New in 2.4**
   - [4.25 `[revoke]` + temporary stages — regression](#425-revoke--temporary-stages--regression) — **New in 2.4**
   - [4.26 `[cost]` — skill-tree purchasable stages](#426-cost--skill-tree-purchasable-stages) — **New in 2.4** *(cooldown / refund **New in 3.0**)*
   - [4.27 `[unlock]` — unlock "juice"](#427-unlock--unlock-juice) — **New in 2.4**
   - [4.28 `[abilities]` — ability gating](#428-abilities--ability-gating) — **New in 2.4** *(sprint / swim / climb **New in 3.0**)*
   - [4.29 `[stage]` new metadata — hidden / color / category / scope / tags](#429-stage-new-metadata--hidden--color--category--scope--tags) — **New in 2.4** *(hidden / color / category live in the GUI in 2.5; `tags` **New in 3.0**)*
   - [4.30 Datapack-loaded stages](#430-datapack-loaded-stages) — **New in 2.5**
   - [4.31 `[rewards]` — items / effects / commands / teleport / xp on grant](#431-rewards--items--effects--commands--teleport--xp-on-grant) — **New in 3.0**
   - [4.32 `[display].encrypt_blocks` — encrypted-block visual](#432-displayencrypt_blocks--encrypted-block-visual) — **New in 3.0**
   - [4.33 `[enchants].max_levels`, `[beacon]`, `[brewing]` — finer-grained gates](#433-enchantsmax_levels-beacon-brewing--finer-grained-gates) — **New in 3.0**
   - [4.34 Temporary, triggered, and priority-based lock rules](#434-temporary-triggered-and-priority-based-lock-rules) — **New in 3.0.1**
   - [4.35 Structure session providers, leased stages, and active locks](#435-structure-session-providers-leased-stages-and-active-locks) — **New in 3.0.1**
   - [4.36 `[[drop_modifiers]]` — selector based block output bonuses](#436-drop_modifiers--selector-based-block-output-bonuses) — **New in 3.0.1**
   - [4.37 Stage slots, class limits, replacements, and stacking](#437-stage-slots-class-limits-replacements-and-stacking) — **New in 3.0.1**
5. [Triggers — The Per-Stage `[[triggers]]` System](#5-triggers--the-per-stage-triggers-system)
   - [5.1 Rules, conditions, and modes](#51-rules-conditions-and-modes)
   - [5.2 Condition types](#52-condition-types)
   - [5.3 Tags, counts, and retroactivity](#53-tags-counts-and-retroactivity)
   - [5.4 Worked examples](#54-worked-examples)
   - [5.5 Polling, persistence, and reset](#55-polling-persistence-and-reset)
   - [5.6 The Stage Tree viewer + keybind](#56-the-stage-tree-viewer--keybind)
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
18. [File, Package, and Runtime Structure](#18-file-package-and-runtime-structure)
19. [Phase 1 Through Phase 19 Guide](#19-phase-1-through-phase-19-guide)

---

## 1. What ProgressiveStages Is

ProgressiveStages 3.0 is a **stage-based progression and content-locking framework**
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
  broadcast message) plus per-stage tooltip / lock-icon overrides.
- Optional **automatic-grant triggers** — a per-stage `[[triggers]]` block that
  watches player actions (kills, mining, crafting, travel, advancements,
  dimensions, biomes, XP, …) and grants the stage when any rule is satisfied.

Players gain stages through:

- Admin command (`/stage grant`)
- Automatic per-stage `[[triggers]]` (see §5) — one or more OR-ed rules, each a
  set of AND/OR-combined conditions over kills, mining, crafting, distance,
  statistics, advancements, dimensions, biomes, and inventory/level state
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
`general.team_mode` in `config/progressivestages/progressivestages.toml`:

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
config/progressivestages/stages/<stage>.toml
config/progressivestages/progressivestages.toml
```

This means stage definitions are **shared across all worlds** on the same
server/instance. Per-world stage state lives inside each world's saved data.

> **Changed in 2.1.** The global legacy `triggers.toml` is
> **gone**. Auto-grant triggers are now declared **per-stage** inside each
> stage file's `[[triggers]]` block (see §5). A leftover legacy `triggers.toml`
> is simply ignored.

Fifty schema 4 stage packages are generated on first launch if the stages directory is empty.
They form a complete branching class tree with three limited beginner paths, evolutions, merged
classes, stackable specialists, replaceable modes, temporary powers, item purchases, automatic
grants, rewards, conditions, modifiers, challenges, and an at-least-three finale. See the
[Showcase Pack Guide](SHOWCASE_PACK.md) for every stage and a
manual verification checklist. Existing stage files are never deleted, replaced, or supplemented
with the showcase during an update.

The directly browsable [`diamond_stage.toml`](examples/reference/diamond_stage.toml) remains the
canonical fully commented legacy one-file reference. It is documentation and is not generated on
new installations.

---

## 3. Quick Start — Your First Stage in 90 Seconds

1. Launch the game once with ProgressiveStages installed. Fifty showcase stage package folders
   appear in `config/progressivestages/stages/` when that directory was empty.

2. Run `/pstages editor`, select a showcase stage, or click the gold plus button to create one. If
   you prefer a manual one-file stage, this remains the minimum valid example:

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

3. In the same manual `iron_age.toml`, add a `[[triggers]]` rule so the stage
   grants itself when the player earns the smelt-iron advancement:

   ```toml
   [[triggers]]
   type = "advancement"
   advancement = "minecraft:story/smelt_iron"
   ```

   This is the single-condition shorthand — one rule, one condition, fields
   placed directly on the `[[triggers]]` table. See §5 for multi-condition
   rules and the full condition list.

4. Either restart the server or run `/progressivestages reload` in-game.

5. Verify:
   - `/stage info iron_age` — prints the parsed definition.
   - `/stage list <player>` — shows which stages the player has.
   - Smelt iron. The stage is granted; the unlock message broadcasts;
     locks for `iron_pickaxe`, `iron_sword`, `iron_block` lift instantly.

That's the entire core loop. Sections 4 and 5 below cover every category and
trigger in depth.

### 3.1 How to use the complete legacy Diamond Stage reference

The repository contains a real, parseable, directly browsable reference named
[`examples/reference/diamond_stage.toml`](examples/reference/diamond_stage.toml). This is not a
short fragment. It is an exact tracked copy of the legacy Diamond Age reference returned by
`DefaultStageTemplates.diamondAge()`. An automated test fails if the tracked file and Java
reference ever differ.

The two filenames have different jobs:

- `examples/reference/diamond_stage.toml` is the friendly GitHub documentation filename. Read it
  in a browser, download it, or copy sections from it.
- `config/progressivestages/stages/diamond_age.toml` is an optional filename you may create by
  copying the reference. It is not part of the new first-launch showcase.
- `id = "diamond_age"` inside `[stage]` is the authoritative identifier. Commands, dependencies,
  KubeJS, FTB Quests, saved ownership, and API calls use this ID rather than the documentation
  filename.

If you have never edited TOML, follow this exact routine:

1. Open [`diamond_stage.toml`](examples/reference/diamond_stage.toml).
2. Read only the numbered safe five minute edit at the top.
3. Remember that a line beginning with `#` is an explanation and does not change the mod.
4. Find `[stage]`. Change only `display_name` or `description` for your first edit.
5. Keep text inside quotation marks. Keep square brackets around lists. Keep commas between list
   entries.
6. Copy the edited file into `config/progressivestages/stages` and name it `diamond_age.toml`.
7. Run `/progressivestages validate`. Do not reload while validation reports an error.
8. Run `/progressivestages reload` after validation succeeds.
9. Run `/stage info diamond_age` to confirm the server loaded the values you expected.
10. Test one lock before granting the stage, after granting it, and after revoking it.

Every enabled setting in the reference has a nearby explanation. Optional examples remain
commented out, so they teach the syntax without activating dozens of unrelated rules. To use one,
copy the relevant example into a small test stage first. This makes mistakes easy to identify and
keeps an experimental rule from changing the production Diamond Age unexpectedly.

### 3.2 No-code localhost stage editor

An operator at permission level 3 may run `/pstages editor` in an integrated single-player world
or while connected to a dedicated server. The client opens a private loopback website. It does not
expose a public server port, and no configuration is sent to a hosted editing service.

The editor begins with stages, not files. The left side contains one card for each stage. A schema
4 stage may use `stage.toml`, `rules.toml`, and `progression.toml` internally, but those filenames
only appear in the optional `TOML source` view.

To create a stage without knowing TOML:

1. Click the gold plus button beside `Stages`.
2. Type `Iron Age` in `Stage name`. Do not type `pack:` and do not add `.toml`.
3. Leave the collapsed pack name at `pack`, or change it to the modpack namespace.
4. Confirm the preview, such as `pack:iron_age`, and click `Create stage`.
5. Fill out the stage name, description, icon, required stages, stage slots, team or server ownership, map
   category, color, frame, reveal policy, and advancement background.
   `Required stages` is a visual branch builder. It lists existing stages as selectable cards,
   explains each card's parents, prevents dependency cycles, and previews selected paths flowing
   upward into the stage being edited. Choose all selected paths for a hybrid class, any one path
   for alternatives, or an exact minimum for a quorum.
6. Click `Add rule`. Choose a category. The action menu changes to the actions supported by that
   category, and the search catalog changes to the matching registry. Entity rules cannot
   accidentally choose an item, block rules cannot accidentally choose a fluid, and a mod filter
   can reduce a large result set to one installed mod.
7. Choose Exact ID, Whole mod, Tag, or Name match. Choose Lock, Deny, Allow, Unlock, Replace, or
   viewer-only presentation. Set priority, JEI policy, EMI policy, and an optional exclusion.
8. For a situational rule, choose a live, duration, session, latched, or scheduled lifetime and a
   dimension, biome, structure, stage, mob, item, advancement, combat, boss, KubeJS, or other
   listed activation condition.
9. Click `Add progression` to make a condition grant or revoke the stage. Choose its count, repeat
   policy, player/team/server scope, priority, and cooldown. The **How players obtain this stage**
   section separately shows automatic gameplay, item purchase, and quest, command, or API paths.
   Choose **Set up purchase**, search the live item registry, click payment items, and set each
   amount plus optional XP, cooldown, refund, and trigger bypass. Use reward cards for items,
   effects, commands, teleportation, and XP granted after ownership changes.
10. Choose **Targeted mining bonus** to select a broken block, final output item, optional tool,
    optional enchantment and level, multiplier, addition, priority, and exclusive stacking. This
    creates `[[drop_modifiers]]`; the generated Diamond Engineer demonstrates 32-diamond purchase
    plus a Fortune-only double-diamond rule.
11. Drag advanced rule cards to organize them. Open `Stage graph`, filter by category, search with
    prerequisite ancestry, zoom, fit the complete graph, or drag nodes to save their coordinates.
    Curved connectors follow at every zoom. Automatic layout puts beginner stages at the bottom,
    reduces crossings, and places evolutions above them. `Arrange paths upward` clears manual
    coordinates and restores that branching view.
12. Click `Check my work`. Then click `Review and apply`, inspect the file diff, and confirm.

The easy builder writes the same schema that a TOML expert would write. There is no reduced
runtime, separate simple-rule engine, or client-only shortcut. Priority, exclusions, temporary
conditions, registry prefixes, viewer policy, server validation, transaction backup, reload, and
client synchronization all use the normal authoritative implementation.

For example, make Mage, Warrior, and Ranger with no required stages. Edit Wizard and
Warlock and select only Mage. Edit Knight and select only Warrior. Edit Wizard Knight, select both
Wizard and Knight, and leave the path rule on `Require every selected path`. The resulting TOML is
equivalent to:

```toml
[stage]
id = "pack:wizard_knight"
dependencies = ["pack:wizard", "pack:knight"]
dependency_mode = "all"
dependency_count = 2
```

Use `dependency_mode = "any"` when either selected path is sufficient. Use `at_least` with
`dependency_count = 2` when two of three or more selected paths are required. The same choices are
available in the visual builder without typing these fields.

The source and Inspector tabs remain available. Source mode has one tab for each file in the
selected stage package and preserves unknown extension fields. The Inspector lists Java and KubeJS
metadata supplied by the running server, provides registry lookup, explains fields, analyzes
explicit priorities, and simulates a candidate decision. Normal stage creation never requires a
browser prompt box, JSON object, inline TOML value, or direct file selection.

---

## 4. Stage Files — The Unified TOML Schema

### 4.1 The Prefix System

ProgressiveStages 3.0 uses a **unified prefix system** for every locked list.
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
`emi.show_locked_recipes = false`, locked recipes are hidden from the browser
entirely. For `locked_items`, the gated OUTPUT ITEM is also removed from the
EMI/JEI item panel (which takes all of its recipes with it), so a recipe-locked
item can be neither browsed nor have its recipe viewed — while remaining usable
if obtained another way. (`locked_ids` only hides the one recipe; the output item
stays visible because it may have other, unlocked recipes.)

> **JEI caveat:** for `locked_ids`, JEI can only hide recipes of *vanilla* recipe
> types (crafting, smelting, blasting, smoking, campfire, stonecutting, smithing).
> A `locked_ids` entry pointing at a *modded* recipe type is still blocked from
> crafting server-side and is hidden in EMI, but is not removed from the JEI
> recipe browser — use `locked_items` (or an `[items]` lock) to fully hide a
> modded output in JEI.

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

**Level cap instead of a full lock — New in 3.0.** To cap an enchantment at a
maximum level (rather than hiding it entirely) until a stage is owned, use
`[enchants].max_levels` — see §4.33.

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

### 4.15a `[trades]` — villager / wandering-trader trade gating

```toml
[trades]
locked = [
    "id:minecraft:diamond_pickaxe",
    "tag:c:tools",
]
always_unlocked = [
    "id:minecraft:diamond_hoe",
]
```

Hides **and** blocks any villager or wandering-trader offer whose **result** item
matches the list, using the prefix system. Unlike `[items]`, this gates the
*trade* only — the player can still hold, use, and obtain the result another way;
they just can't buy it from a merchant until the stage is earned. This is the
"diamonds are post-Nether: lock the diamond-tool trades, not the diamonds"
pattern.

Enforcement has two halves, sharing one predicate
([`TradeEnforcer`](src/main/java/com/enviouse/progressivestages/server/enforcement/TradeEnforcer.java)):

- **Hidden** — locked offers are filtered out of the merchant GUI before it
  reaches the client
  ([`ServerPlayerMerchantMixin`](src/main/java/com/enviouse/progressivestages/mixin/ServerPlayerMerchantMixin.java)),
  so a vanilla client never sees them. The client/server trade index is
  reconciled by
  [`ServerGamePacketListenerMerchantMixin`](src/main/java/com/enviouse/progressivestages/mixin/ServerGamePacketListenerMerchantMixin.java)
  so the offers that remain visible still select correctly.
- **Blocked** — the result slot is cleared server-side
  ([`MerchantContainerMixin`](src/main/java/com/enviouse/progressivestages/mixin/MerchantContainerMixin.java)),
  so even a tampered / desynced client cannot complete a locked trade.

A trade is also gated (preserving prior behavior) when its result is locked via
`[items]`, or when the result carries an `[enchants]`-locked enchantment — so
enchanted-book / enchanted-gear trades from librarians are NBT-aware without
listing `enchanted_book` here.

Creative and spectator players bypass. Toggle globally with
`enforcement.block_trades`. Covers both villagers and the wandering trader (both
route through the same merchant code path).

### 4.15b `[professions]` — gate trading by villager profession

> **New in 2.5.**

```toml
[professions]
locked = [
    "id:minecraft:weaponsmith",
    "mod:somemod",
    "name:cleric",
]
```

Where `[trades]` hides **individual offers** by their *result* item, `[professions]`
gates the **whole trade GUI** by the villager's **profession**. A player who lacks
the gating stage simply **cannot open** a gated villager's trades at all — the
`EntityInteract` is cancelled and the standard lock notice fires (with a per-player
cooldown).

The list is **id-only prefix matching** — `id:`, `mod:`, and `name:` against the
profession's registry id (e.g. `minecraft:weaponsmith`). **Tags are not supported**
(professions carry no tags). Examples:

| Entry | Matches |
|-------|---------|
| `id:minecraft:weaponsmith` | exactly the weaponsmith profession |
| `mod:somemod` | every profession registered by `somemod` |
| `name:cleric` | any profession whose id contains `cleric` |

- **Wandering traders have no profession** and are therefore **never** affected —
  use `[trades]` (§4.15a) to gate their offers.
- **Entirely opt-in.** With no `[professions]` locks declared anywhere, every
  villager-interact check is a cheap no-op (no per-tick overhead).
- Creative players bypass. There is **no separate global toggle** — the feature is
  active whenever any stage declares a `[professions]` lock.

The implementation is
[`VillagerProfessionEnforcer`](src/main/java/com/enviouse/progressivestages/server/enforcement/VillagerProfessionEnforcer.java),
invoked from `ServerEventHandler.onEntityInteract`.

### 4.15c `[advancements]` — hide advancements from the screen

> **New in 2.5.**

```toml
[advancements]
locked = [
    "id:minecraft:nether/root",
    "mod:somemod",
]
```

Locked advancements (those whose gating stage the player lacks) are **hidden
entirely** from the advancements screen — and not merely greyed out. The server
**strips them from every `ClientboundUpdateAdvancementsPacket`** before it reaches
the client ([`ServerAdvancementHidingMixin`](src/main/java/com/enviouse/progressivestages/mixin/ServerAdvancementHidingMixin.java)),
so a vanilla client is **never even told the advancement exists**. There is no
spoiler to reveal in F-key tooltips, tab names, or the advancement tree.

When the player **gains** the gating stage, a **full advancement re-send** is
triggered ([`AdvancementHider.resyncIfNeeded`](src/main/java/com/enviouse/progressivestages/server/enforcement/AdvancementHider.java)
calls `player.getAdvancements().reload(...)`), so the now-reachable advancements
**pop into view without a relog** (and revoked ones disappear again).

Like `[professions]`, matching is **id-only** (`id:` / `mod:` / `name:`; **no
tags**) and the feature is **opt-in** — a cheap `hasAdvancementLocks()` fast-path
short-circuits the packet filter whenever no stage gates an advancement.

The implementations are
[`AdvancementHider`](src/main/java/com/enviouse/progressivestages/server/enforcement/AdvancementHider.java)
and [`ServerAdvancementHidingMixin`](src/main/java/com/enviouse/progressivestages/mixin/ServerAdvancementHidingMixin.java).

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
entry_padding = 3            # New in 2.5 — see below (also accepted directly under [structures])
```

Two parts: the list of locked structure IDs (`locked_entry`) and a single
`[structures.rules]` table whose flags apply **across all listed structures**.

#### `locked_entry`

When a player enters the piece bounding box of any listed structure and lacks
the gating stage, they are **bounced back out**. The check runs every
`enforcement.region_tick_frequency` ticks (default 20 = 1s). The enforcer asks
Minecraft's `StructureManager` for the structure at the player's position and
then verifies the position against the resolved structure start bounding box.
This is server-side and works for registered vanilla and compliant modded
structures.

**Last-safe-position teleport — New in 2.5.** Each tick a player is *outside*
every locked structure, the enforcer records that spot as their **last safe
position**. When they then breach a gated structure they are teleported **back to
that remembered safe spot** (provided it's genuinely still outside the box) rather
than merely shoved to the nearest edge — so they end up where they actually stood,
not pinned against the wall. If no safe position has been recorded yet, the
enforcer falls back to pushing them to the nearest box edge. (The record is dropped
on logout.)

**`entry_padding` — New in 2.5.** An integer buffer **in blocks** that places the
repelled player **well clear of the boundary** rather than right on its edge.
Accepted **either** under `[structures.rules].entry_padding` **or** directly on
`[structures].entry_padding` (the larger of the two wins). It only applies to the
nearest-edge fallback push; defaults to `0`. Set e.g. `entry_padding = 3` to keep
players from clipping the boundary or re-triggering the check immediately.

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

All four rule flags shown above are implemented. “Indestructible” in this
context means player break events are cancelled while the relevant gate is
missing. It does not rewrite world-generation data or make blocks globally
unbreakable for every system and every player.

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

### 4.19 `[[ores.overrides]]` — ore masquerade

```toml
[[ores.overrides]]
target = "id:minecraft:diamond_ore"
display_as = "id:minecraft:stone"
drop_as = "id:minecraft:cobblestone"
```

Until the player owns the stage, matching ore blocks are rewritten to the
`display_as` block for that client and yield `drop_as` through the guarded
harvest/drop path. Unlocking refreshes the affected client view. Per-stage
spoof radius and the 3.0 `[display].encrypt_blocks` shorthand use the same
pipeline.

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

### 4.21 `[enforcement]` — per-stage exemptions + toggle overrides

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

#### Per-stage enforcement *overrides* — **New in 2.1**

Where the `allowed_*` lists exempt **named entries** while a global toggle is on,
the `[enforcement]` section can also **override the global toggle itself** —
but only for the resources **that stage** gates. Each override key uses the
**same name** as the matching toggle in `progressivestages.toml`. **Omit a key**
to inherit the global default; **set it `true`/`false`** to override the global
default just for this stage's gated resources.

Ten override keys are supported, each mapping to the identically-named global
toggle:

| Override key (in stage `[enforcement]`) | Mirrors global toggle | Category it governs for this stage |
|-----------------------------------------|-----------------------|------------------------------------|
| `block_item_use` | `block_item_use` | Using / right-/left-clicking / mining / attacking with this stage's items |
| `block_item_pickup` | `block_item_pickup` | Picking this stage's items up from the ground |
| `block_item_inventory` | `block_item_inventory` | Auto-dropping this stage's items from the inventory |
| `block_block_placement` | `block_block_placement` | Placing this stage's blocks |
| `block_block_interaction` | `block_block_interaction` | Right-clicking this stage's blocks |
| `block_dimension_travel` | `block_dimension_travel` | Traveling to this stage's dimensions |
| `block_entity_attack` | `block_entity_attack` | Attacking this stage's `[entities]` |
| `block_screen_open` | `block_screen_open` | Opening this stage's `[screens]` |
| `block_crop_growth` | `block_crop_growth` | This stage's `[crops]` planting / growth / bonemeal / harvest |
| `block_pet_interact` | `block_pet_interact` | This stage's `[pets]` taming / breeding / commanding / riding |

**Semantics:**

- **Opt-OUT** (global ON, stage sets `false`). The global toggle is on, but the
  stage sets e.g. `block_item_pickup = false` → this stage's gated items **can
  be picked up** even though pickup is blocked everywhere else. Classic use:
  *"this stage hides `mod:create` and stops players USING or placing its items,
  but lets them carry the items around."*
- **Opt-IN** (global OFF, stage sets `true`). The global toggle is off, but the
  stage sets e.g. `block_item_use = true` → use-blocking is enforced **only for
  this stage's resources**, leaving every other stage unaffected.
- **Most-restrictive wins (multi-stage rule).** A resource gated by several
  stages is enforced for a category if **ANY** of its still-missing gating
  stages enforces it. One stage opting out can never free a resource that
  another gating stage locks *and* enforces.
- **No overrides anywhere → identical to pure global toggles.** With no stage
  declaring any of these keys, behaviour is exactly the global config.

**Worked example.** "This stage hides Create items and blocks their *use* and
*placement*, but players may freely pick up and carry the items":

```toml
[items]
locked = ["mod:create"]
[blocks]
locked = ["mod:create"]

[enforcement]
# Global config has all of these ON. Override two of them OFF for THIS stage:
block_item_pickup    = false   # let players carry Create items
block_item_inventory = false   # ...and keep them in their inventory
# block_item_use omitted       → inherits global (use still blocked)
# block_block_placement omitted → inherits global (placement still blocked)
```

### 4.22 `[display]` — per-stage tooltip + icon overrides

```toml
[display]
display_as_unknown_item     = true    # mask the item NAME as "Unknown Item"
obscure_icon                = false   # also replace the ICON with a "?" placeholder
show_tooltip                = true    # show the lock / required-stage tooltip lines
show_description_on_tooltip = false   # append [stage].description to the tooltip
```

Per-stage overrides of the global tooltip / icon defaults. **Every key is
optional** — omit a key and the stage inherits the matching global default
from `progressivestages.toml`. These only affect how this stage's *locked*
items present to a player who hasn't unlocked the stage.

| Key | Type | Global default it overrides | Effect |
|-----|------|-----------------------------|--------|
| `display_as_unknown_item` | bool | `enforcement.mask_locked_item_names` | Mask the item NAME as `"Unknown Item"` (configurable text). |
| `obscure_icon` | bool | `enforcement.obscure_locked_item_icons` (default `false`) | **New in 2.3.** Also replace the item ICON with a `?` placeholder in the player's inventory — the item becomes fully unidentifiable, not just its name. |
| `show_tooltip` | bool | `emi.show_tooltip` | Whether to show the lock / required-stage tooltip lines for this stage's items. |
| `show_description_on_tooltip` | bool | `emi.show_stage_description_on_tooltip` (default `false`) | **New in 2.3.** Append this stage's `[stage].description` to a locked item's tooltip, rendered through the `messages.tooltip_stage_description` template. |
| `encrypt_blocks` / `encrypt_as` | bool / string | *(no global; per-stage)* | **New in 3.0.** Masquerade this stage's exact-id locked **blocks** in the world as a placeholder block until owned — see §4.32. |

**Worked example.** "Make endgame items completely mysterious until unlocked,
and tease the stage's description in the tooltip":

```toml
[stage]
id          = "void_age"
description = "Something stirs beyond the world's edge."

[display]
display_as_unknown_item     = true
obscure_icon                = true
show_description_on_tooltip = true
```

### 4.23 `[[triggers]]` — automatic stage grants

A stage can grant **itself** when the player performs the right actions. The
`[[triggers]]` block is the per-stage trigger schema introduced in 2.1; it
replaces the old global `triggers.toml`. Because it lives in the same file as
the stage's locks and `[display]`, everything about a stage is in one place.

Full reference and worked examples are in **§5**. A minimal taste:

```toml
[[triggers]]
type  = "mine"
block = "minecraft:diamond_ore"
count = 10
```

This single-condition shorthand grants the stage once the player has mined 10
diamond ore.

### 4.24 `[attribute]` — attribute modifiers while a stage is owned

**New in 2.4.** A stage can grant **attribute modifiers** that apply for as long
as the player's team **owns** the stage. Unlike the `locked` categories above —
which take things *away* until a stage is earned — `[attribute]` is a *reward*:
earning the stage buffs the player.

`[attribute]` is an **array of tables** (`[[attribute]]`), one entry per
modifier:

```toml
# +10 maximum health while this stage is owned
[[attribute]]
id        = "minecraft:generic.max_health"
operation = "add"
amount    = 10

# +20% movement speed (multiplicative on the base value)
[[attribute]]
id        = "minecraft:generic.movement_speed"
operation = "multiply_base"
amount    = 0.2
```

| Field | Required? | Notes |
|-------|-----------|-------|
| `id` | Yes | Any **vanilla OR modded** attribute id — e.g. `minecraft:generic.max_health`, `minecraft:generic.scale`, `minecraft:generic.movement_speed`, `minecraft:generic.attack_damage`, or a modded attribute. |
| `operation` | Optional | `add` (flat, default), `multiply_base` (× the base value), or `multiply_total` (× the running total after `add` + `multiply_base`). |
| `amount` | Yes | The modifier value (number). For `add`, a flat amount; for the multiply operations, a fraction (e.g. `0.2` = +20%). |

**How it's applied.** Modifiers are added when the stage is granted (or on login
for stages already owned) and removed when the stage is revoked. They are
implemented as **transient** modifiers (not persisted on the entity), and the
mod **reconciles** the full set against the player's owned stages on each
grant / revoke / login so the live attributes always match the owned-stage set.

**Max-health caveat.** If a modifier that raised a player's max health is removed
(stage revoked) and the player's current health now exceeds the new maximum,
current health is **clamped down** to the new max. Raising max health does not
auto-heal; it just lifts the ceiling.

### 4.25 `[revoke]` + temporary stages — regression

**New in 2.4.** Stages can be **lost** as well as gained. Two independent
mechanisms produce a **regression** (a revoke reported as
`StageCause.REGRESSION` — see §15.3): the `[revoke]` table and a stage
`duration`.

#### `[revoke]` — conditional loss

```toml
[revoke]
on_death  = true     # lose this stage when the player dies
xp_below  = 5000     # XP-MAINTAINED: hold the stage only while total XP ≥ 5000
cascade   = true     # when revoked, also revoke stages that depend on this one
```

| Field | Type | Meaning |
|-------|------|---------|
| `on_death` | bool | When **true**, the stage is revoked the moment the player dies. |
| `xp_below` | int | **XP-maintained stage.** The player keeps the stage only while their **total experience ≥ this number**. Spend XP (enchanting, anvils) and drop below the threshold, and the stage is **revoked until re-earned**. A live, ongoing check — not a one-time gate. |
| `cascade` | bool | When **true**, revoking this stage also revokes every stage that **depends on** it (recursively). When **false** (default), the revoke is **isolated** — dependents keep their stages even though a prerequisite was lost. This is a per-stage choice between *isolated* and *cascading* regression. |

#### `[stage].duration` — temporary stages

```toml
[stage]
id       = "berserker_rush"
duration = "30m"     # this stage auto-expires 30 real-minutes after it is granted
```

Setting `duration` makes a **temporary** stage that **auto-expires** after that
much **real time**. The countdown is wall-clock and **keeps running while the
player is OFFLINE** — log out with 5 minutes left and the stage is gone when you
return an hour later.

**Duration units:** `s` (seconds), `m` (minutes), `h` (hours), `d` (days) —
e.g. `90s`, `30m`, `2h`, `1d`. A **bare number** is interpreted as **minutes**
(`duration = 30` ≡ `"30m"`).

Expiry is a regression like any other: it reports `StageCause.REGRESSION`, and
if the stage's `[revoke].cascade = true`, expiring it also cascades to
dependents.

### 4.26 `[cost]` — skill-tree purchasable stages

**New in 2.4.** A `[cost]` table turns a stage into a **purchasable node** that
players can **buy** directly from the in-game Stage Tree GUI (the skill tree —
open it with `/stage gui` or the "Open Progression Tree" keybind, see §5.6).

```toml
[cost]
xp_levels           = 10                                  # experience LEVELS consumed
items               = ["minecraft:diamond:5", "minecraft:emerald:3"]
bypass_requirements = false
cooldown            = "5m"                                # New in 3.0 — min time between purchases
refund_percent      = 50                                  # New in 3.0 — % of cost returned on revoke
```

| Field | Type | Meaning |
|-------|------|---------|
| `xp_levels` | int | Experience **levels** consumed on purchase. |
| `items` | list | `"item:count"` strings (e.g. `"minecraft:diamond:5"`). The listed items are consumed from the player's inventory on purchase. |
| `bypass_requirements` | bool | See below. Default `false`. |
| `cooldown` / `cooldown_seconds` | string / int | **New in 3.0.** Per-player **rate limit** between skill-tree purchases. Use either `cooldown_seconds = 300` or a friendly `cooldown = "5m"` (units `s`/`m`/`h`/`d`, bare number = minutes). `0` (default) = no cooldown. |
| `refund_percent` | int | **New in 3.0.** Percentage (`0`–`100`) of this purchased stage's **item / XP cost** returned to the player when the stage is later **revoked**. `0` (default) = no refund. |

**`cooldown` (New in 3.0).** A **per-player** minimum interval between
skill-tree purchases, enforced **server-side**. While the cooldown is active the
purchase is rejected and the player is told how many seconds remain. Set it as
`cooldown_seconds = <int>` or with a friendly `cooldown = "5m"` (the string form
wins when both are present). The cooldown timer is **in-memory / transient** (it
resets on server restart) and is shared across all purchasable stages for that
player.

**`refund_percent` (New in 3.0).** When a stage that was **actually purchased**
via its `[cost]` is later **revoked** (by command, regression, cascade, etc.),
this percentage of its `items` + `xp_levels` cost is **returned** to the player.
The mod records each real purchase (persisted) and consumes that record on
refund, so a stage **earned** via a trigger / command / quest reward — or a
temporary purchasable stage that auto-expires — is **never** refunded (no free
items), and each purchase is refunded at most once.

**`bypass_requirements`:**

- `false` (default) — the GUI's **Unlock** button only appears once the stage's
  prerequisites **and** its `[[triggers]]` are all met. Paying the cost is the
  **final confirmation** of an already-earned stage.
- `true` — paying the cost unlocks the stage **immediately even if the
  `[[triggers]]` aren't met**. Prerequisite **stages** (`[stage].dependency`)
  are **still required** — `bypass_requirements` only bypasses the triggers, not
  the dependency graph.

**Validation.** The purchase flow is **fully server-validated**: the server
re-checks dependencies, requirements, and that the player actually has the XP /
items before consuming anything. A tampered or desynced client **cannot**
double-spend or bypass the gate. The GUI shows an **Unlock** button; clicking it
runs the validated purchase and, on success, grants the stage with
`StageCause.PURCHASE` (see §15.3).

### 4.27 `[unlock]` — unlock "juice"

**New in 2.4.** The `[unlock]` table adds **presentation polish** ("juice") that
fires when the stage is unlocked. **Every field is optional**, and an absent /
empty / `false` field simply does nothing.

```toml
[unlock]
toast          = "Diamond Age reached!"          # advancement-style toast
title          = "&bDiamond Age"                  # on-screen title
subtitle       = "You have ascended"              # on-screen subtitle
sound          = "minecraft:ui.toast.challenge_complete"
particle       = "minecraft:totem_of_undying"
progress_nudges = true                            # chat hints at 50% / 75% / 90%
hud_bar        = true                             # blue progress bar above the XP bar
```

| Field | Type | Effect on unlock |
|-------|------|------------------|
| `toast` | string | Shows an **advancement-style toast** with this text. |
| `title` | string | On-screen **title** text (supports `&`-codes). |
| `subtitle` | string | On-screen **subtitle** text (pairs with `title`). |
| `sound` | string | Plays this **sound id** at the moment of unlock. |
| `particle` | string | Sprays this **particle id** on unlock. |
| `progress_nudges` | bool | When `true`, sends **one-time chat hints** at **50% / 75% / 90%** of the stage's trigger progress, nudging the player toward the unlock. |
| `hud_bar` | bool | When `true` **and** this stage is the player's current goal, shows a custom **BLUE "progress to next stage" bar** rendered **above the vanilla XP bar**. |

> Do **not** confuse `[unlock]` (singular — unlock juice, 2.4) with `[unlocks]`
> (plural — per-stage gating carve-outs, §4.20). They are different tables.

### 4.28 `[abilities]` — ability gating

**New in 2.4.** `[abilities]` gates **player movement / action abilities** behind
owning a stage.

```toml
[abilities]
locked = ["elytra", "sprint", "swim", "climb"]   # all blocked until this stage is owned
```

| Field | Type | Meaning |
|-------|------|---------|
| `locked` | list | Ability identifiers blocked until the player owns the stage. |

Each tick, a player who is **missing at least one stage that gates an ability**
is dropped out of that action. The recognised entries are:

| Ability | Effect while the gating stage is missing |
|---------|------------------------------------------|
| `elytra` | Elytra **gliding** is blocked — the player is dropped out of flight each tick. |
| `sprint` | **New in 3.0.** Sprinting is **cancelled** each tick. |
| `swim` | **New in 3.0.** The **swimming** pose is cancelled (also covers fast-swim in water). |
| `climb` | **New in 3.0.** **Climbing up** ladders/vines is blocked — any upward velocity on a climbable is clamped to ≤ 0 (the player can still hold position / descend). |

> `crawl` on land isn't separately enforceable in vanilla (the prone pose doesn't
> set the swim flag and the player is wedged in a 1-block gap), so it is **not** a
> gated ability — listing it does nothing.

> Creative-mode players bypass ability gating when `allow_creative_bypass` is on
> (the same global toggle that exempts other locks). Unknown ability names simply
> do nothing.

> **Guidance.** Most other movement / stat abilities are better expressed through
> `[attribute]` (§4.24 — e.g. movement speed, step height, jump strength via the
> relevant attributes) or through KubeJS (§11). `[abilities]` is for the handful
> of toggle-style abilities — like elytra flight, sprinting, swimming, and
> climbing — that aren't attributes.

### 4.29 `[stage]` new metadata — hidden / color / category / scope / tags

**New in 2.4** (with the GUI behaviour of `hidden` / `color` / `category` fully
**activated in 2.5**; **`tags` New in 3.0**). The `[stage]` table (§4.2) gained
several optional metadata keys, mostly for the Stage Tree GUI, for server-wide
stages, and (3.0) for **bulk tag operations**:

```toml
[stage]
id       = "secret_ending"
hidden   = true            # hide this stage from the GUI tree
color    = "#55FF55"       # GUI tint (hex) or an &-code
category = "Endgame"       # group label in the GUI
scope    = "server"        # SERVER-WIDE stage (default "team")
duration = "2h"            # temporary stage (see §4.25)
tags     = ["combat", "tier2"]   # New in 3.0 — labels for /stage tag ... (§7.1)
slot_group = "beginner_paths"    # New in 3.0.1 — shared ownership pool
slot_limit = 2                    # two members of this group may be active
slot_policy = "deny"             # behavior when the group is full
```

| Field | Type | Meaning |
|-------|------|---------|
| `hidden` | bool | When `true`, the stage is **omitted from the Stage Tree GUI** entirely (its children re-root to the top level). It still functions (locks, triggers, grants) — it's just not drawn in the tree. |
| `color` | string | A GUI **tint** for the stage's name — a `#RRGGBB` hex string (`"#55FF55"`) or an `&`-color code. Applies to the stage's name in both the tree and the detail header (the status colour is the fallback when omitted). |
| `category` | string | A **group label**, shown as a `[category]` tag in the detail-pane header. |
| `scope` | string | `"team"` (default) or `"server"`. A **`"server"`-scoped stage is SERVER-WIDE**: the **first team** to satisfy it unlocks it for the **whole server** (everyone, including future joiners). Use for global milestones ("someone has beaten the dragon → the End gate opens for all"). |
| `duration` | string | Temporary-stage lifetime — see §4.25. |
| `tags` | list | **New in 3.0.** Free-form **labels** for this stage (lower-cased on load). They group stages for the bulk `/stage tag grant\|revoke\|list <tag>` commands (§7.1) — e.g. tag every combat-tier stage `"combat"` and grant them all at once. Tags have **no** gating effect on their own; they're purely an authoring/admin convenience. |
| `dependency_mode` | string | **New in 3.0.** `"all"` (default), `"any"`, or `"at_least"`. This enables alternate branches and quorum progression without scripts. |
| `dependency_count` | integer | Required direct-dependency count for `dependency_mode = "at_least"`; clamped to the declared dependency list. |
| `slot_group` | string | **New in 3.0.1.** Lowercase group identity shared by stages whose simultaneous ownership is controlled together. Letters, numbers, `_`, `.`, and `-` are accepted. Blank means no group. |
| `slot_limit` | integer | **New in 3.0.1.** Maximum active members from this group. `0` means unlimited and lets every member stack. Maximum accepted value is 1024. A positive value requires `slot_group`. |
| `slot_policy` | string | **New in 3.0.1.** `deny`, `replace_oldest`, `replace_lowest_priority`, or `replace_all`. See §4.37. |

> **Activated in 2.5.** Prior to 2.5, `hidden` / `color` / `category` were
> **parsed but inert**. As of 2.5 they take effect in the Stage Tree GUI exactly
> as described above: `hidden` removes the node, `color` (when it's a `#RRGGBB`
> hex) tints the stage name, and `category` shows as a tag in the detail header.
> They are synced to the client via `ClientStageCache`
> ([`StageTreeScreen`](src/main/java/com/enviouse/progressivestages/client/gui/StageTreeScreen.java)).

### 4.30 Datapack-loaded stages

> **New in 2.5.**

Stage TOML files no longer have to live in the config folder — a **datapack** can
ship its own stages at:

```
data/<namespace>/progressivestages/stages/*.toml
```

Each `.toml` uses the **exact same schema** as a config-folder stage file (every
category, `[[triggers]]`, `[unlocks]`, etc.). Datapack stages are loaded by a
**reloadable-resource listener**, so they're (re)read both at **world load** and on
**`/reload`**, and merged with the config-folder stages.

**Merge rule: config always wins.** If a stage id is defined **both** in a datapack
**and** in `config/progressivestages/stages/<id>.toml`, the **config file wins** — the
datapack copy is overridden. This makes datapacks an ideal way to ship
**overridable defaults**: a content/quest datapack can bundle a baseline
progression that a pack author or server admin can then tweak locally **without
editing the datapack** (just drop a same-id file in the config folder).

> A datapack with a stage id that duplicates **another datapack's** stage id logs a
> warning and keeps the last one loaded. Use distinct ids across datapacks.

The implementation is
[`DatapackStageLoader`](src/main/java/com/enviouse/progressivestages/server/loader/DatapackStageLoader.java)
(a `SimplePreparableReloadListener`), which hands its parsed map to
`StageFileLoader.setDatapackStages(...)` for the config-wins merge.

### 4.31 `[rewards]` — items / effects / commands / teleport / xp on grant

> **New in 3.0.**

`[rewards]` is the natural companion to `[cost]` (§4.26): where `[cost]` is what
a stage **takes** to unlock, `[rewards]` is what the stage **hands out the moment
it is granted**. It fires **once per actual grant**, applied to the **single
player who earned / bought the stage** — not once per online team member, and not
to every player on a server-scoped grant (that would duplicate items / commands /
teleports). It does **not** re-fire on login or sync. Every field is optional —
an empty / absent `[rewards]` does nothing.

```toml
[rewards]
items     = ["minecraft:diamond:5", "minecraft:netherite_scrap"]
effects   = ["minecraft:strength:60:1"]            # id : seconds : amplifier
commands  = ["give {player} minecraft:cake 1", "say {player} ascended!"]
teleport  = "minecraft:the_nether 0 70 0"          # "[dim] x y z" — dim optional
xp_levels = 5
xp_points = 100
```

| Field | Type | Meaning |
|-------|------|---------|
| `items` | list | `"item:count"` strings (same syntax as `[cost].items`). Each item stack is **added to the player's inventory**; anything that doesn't fit is **dropped** at their feet. |
| `effects` | list | Status effects to apply, each as `"<effect_id>:<seconds>:<amplifier>"`. `amplifier` is 0-based (`0` = level I, `1` = level II). The seconds and amplifier are optional trailing numbers — `"minecraft:strength"` → 30 s, level I; `"minecraft:strength:60"` → 60 s, level I; `"minecraft:strength:60:1"` → 60 s, level II. |
| `commands` | list | Server commands run **as the player** at **permission level 2** with output suppressed. `{player}` is substituted with the player's name. A command that fails is logged and skipped (the rest still run). |
| `command` | string | Singular convenience alias — a single command added to the `commands` list. |
| `teleport` | string | `"[dimension] x y z"` — teleports the player. The dimension id is **optional**: with four whitespace-separated tokens the first is the target dimension; with three tokens the player stays in their current dimension. Malformed coordinates are ignored (no teleport). |
| `xp_levels` | int | Experience **levels** granted. |
| `xp_points` | int | Experience **points** granted. |

> **Cause.** Rewards fire on **every** real grant regardless of cause —
> command, trigger, purchase, quest reward, etc. — but **only once** per grant
> (a revoke-then-regrant fires them again). Implementation:
> [`StageRewards`](src/main/java/com/enviouse/progressivestages/common/config/StageRewards.java)
> +
> [`StageRewardApplier`](src/main/java/com/enviouse/progressivestages/server/enforcement/StageRewardApplier.java).

### 4.32 `[display].encrypt_blocks` — encrypted-block visual

> **New in 3.0.**

The `[display]` table (§4.22) gained a per-stage **encrypted-block** toggle. When
`encrypt_blocks = true`, this stage's **exact-id locked blocks** (the `id:` entries
under `[blocks].locked`) are **masqueraded** in the world as a placeholder block —
`encrypt_as` (default `minecraft:stone`) — until the player owns the stage. It's
the "you can't even tell what's there" treatment for spoiler-sensitive blocks.

```toml
[display]
encrypt_blocks = true               # mask this stage's locked blocks until owned
encrypt_as     = "minecraft:stone"  # placeholder block (default minecraft:stone)
```

| Field | Type | Meaning |
|-------|------|---------|
| `encrypt_blocks` | bool | When `true`, this stage's **exact-id** locked blocks render as `encrypt_as` (and break/drop like it) until the player owns the stage. Default `false`. Per-stage on/off. |
| `encrypt_as` | string | The placeholder block id to masquerade as. Default `minecraft:stone`. |

**How it works.** Encryption **reuses the entire ore-spoof pipeline**: for each
exact-id (`id:`) entry in `[blocks].locked`, the mod synthesises an
`[[ores.overrides]]`-style entry mapping `target → encrypt_as` (for both the
displayed block **and** its drop) scoped to this stage. That means the same chunk
rewrite, break-speed sync, and drop replacement that powers ore spoofing applies
here — the masked block looks, mines, and drops as the placeholder until the gate
opens. The spoof radius follows `[enforcement].ore_spoof_radius` (default 8 when
unset). Only **exact-id** block locks are encrypted — `mod:` / `tag:` / `name:`
block entries are not synthesised into overrides.

> Implementation: [`LockRegistry`](src/main/java/com/enviouse/progressivestages/common/lock/LockRegistry.java)
> (encrypt → ore-override synthesis) + the existing ore-spoof enforcers.

### 4.33 `[enchants].max_levels`, `[beacon]`, `[brewing]` — finer-grained gates

> **New in 3.0.** Previously listed here as "planned" — **all three now ship.**

Three finer-grained gates that sit alongside the whole-resource categories
above: cap an enchantment's level (rather than locking it outright), withhold an
individual beacon effect, and gate pulling a specific brewed potion out of a
brewing stand. Each is fully opt-in — there is **zero overhead** when no stage
declares the section.

#### `[enchants].max_levels` — per-stage enchant level cap

The whole-enchant lock (`[enchants].locked`, §4.9) is unchanged. **New:**
`max_levels` caps an enchantment at a maximum level until the gating stage is
owned, instead of hiding it entirely.

```toml
[enchants]
locked     = ["id:minecraft:mending"]                 # whole-enchant lock (§4.9)
max_levels = ["minecraft:sharpness:3", "minecraft:protection:2"]
```

Each entry is `<enchant-id>:<level>` (the last `:`-segment is the integer cap;
exact ids only — no prefixes, tags, or `mod:`). A player **missing** the gating
stage has that enchantment clamped to the cap; once they own the stage, the cap
no longer applies. The **effective cap** for an enchantment is the **MIN across
every still-missing capping stage** — if two unowned stages cap `sharpness` at 3
and 2, the player is held to 2.

Enforcement happens in the **periodic inventory scan** (the same pass that strips
whole-locked enchants, §4.9): any over-cap level is reduced to the cap, and a cap
of `0` **removes** the enchant entirely. It applies to both live item
enchantments and stored book enchantments. Creative-bypass players are exempt
(`allow_creative_bypass`).

| Field | Type | Meaning |
|-------|------|---------|
| `max_levels` | list | `["<enchant-id>:<level>", ...]` — cap each enchant at `<level>` until the gating stage is owned. Exact ids only. Effective cap = MIN across every missing capping stage; level `0` removes the enchant. |

> Implementation: [`EnchantEnforcer`](src/main/java/com/enviouse/progressivestages/server/enforcement/EnchantEnforcer.java)
> (`applyEnchantPolicy`), [`LockRegistry`](src/main/java/com/enviouse/progressivestages/common/lock/LockRegistry.java)
> (`effectiveEnchantCap`), [`LockDefinition.EnchantCap`](src/main/java/com/enviouse/progressivestages/common/lock/LockDefinition.java),
> [`StageFileParser`](src/main/java/com/enviouse/progressivestages/server/loader/StageFileParser.java)
> (`parseEnchantCaps`).

#### `[beacon].locked` — gate individual beacon effects

```toml
[beacon]
locked = ["id:minecraft:strength", "id:minecraft:haste"]
```

`[beacon].locked` is a list of **MobEffect ids** (`id:` / `mod:` / `name:`
matching; no tags). A beacon applies its chosen effect to every player in range;
this gate means a player **missing** the gating stage simply **does not receive**
that effect. **Other players in range are unaffected** — the beacon keeps working
for everyone who owns the stage. The block, the beacon item, and the beacon's GUI
are untouched; only the per-player effect application is gated.

> Implementation: [`BeaconBlockEntityMixin`](src/main/java/com/enviouse/progressivestages/mixin/BeaconBlockEntityMixin.java)
> (`@Redirect` on the per-player `addEffect`), [`LockRegistry.isBeaconEffectBlockedFor`](src/main/java/com/enviouse/progressivestages/common/lock/LockRegistry.java).

#### `[brewing].locked` — gate taking a brewed potion

```toml
[brewing]
locked = ["id:minecraft:strength", "id:minecraft:swiftness"]
```

`[brewing].locked` is a list of **Potion ids** (`id:` / `mod:` / `name:`; no
tags). Rather than fighting the brewing-stand fuel/timer loop, this gate makes a
player **missing** the gating stage **unable to TAKE** the brewed potion out of a
brewing stand's potion slots: the potion still brews and **sits there** until the
player unlocks it, at which point they can pull it normally.

> **Both player and hopper extraction are gated.** The player-facing pickup from
> the potion slots is blocked by the slot `mayPickup` check; hopper / funnel
> extraction is blocked by `BrewingStandBlockEntityMixin` (on
> `canTakeItemThroughFace`), gated on the **nearest player** within 16 blocks —
> the same best-effort heuristic the mod uses for automated crafting (hopper
> transfers carry no player). If no player is in range, extraction proceeds. For
> an absolute lock, also gate the brewing stand block itself via `[screens]`.

> Implementation: [`SlotBrewingPickupMixin`](src/main/java/com/enviouse/progressivestages/mixin/SlotBrewingPickupMixin.java)
> (`Slot.mayPickup` on `BrewingStandBlockEntity` containers),
> [`LockRegistry.isBrewingBlockedFor`](src/main/java/com/enviouse/progressivestages/common/lock/LockRegistry.java).

---

### 4.34 Temporary, triggered, and priority-based lock rules

Conditional rules change access without replacing the normal stage lock schema. They are designed
for rules such as swords only inside a Stronghold, no elytra during the End fight, a temporary bow
ban after combat starts, or a later stage that overrides an earlier structure gate.

For a single-purpose tutorial with all requested examples, see
[TEMPORARY_AND_TRIGGERED_LOCKS.md](TEMPORARY_AND_TRIGGERED_LOCKS.md).

#### Do not confuse stage triggers and triggered locks

- `[[triggers]]` grants the stage containing it. Its result is normal persisted stage ownership.
- `[[triggered_locks]]` and `[[triggered_unlocks]]` start a temporary runtime timer.
- `[[temporary_locks]]` and `[[temporary_unlocks]]` continuously evaluate their live `.when`
  context and do not use a timer.

The friendly rule forms are:

| Block | Effect | Activation |
|---|---|---|
| `[[temporary_locks]]` | lock | live context |
| `[[temporary_unlocks]]` | unlock | live context |
| `[[triggered_locks]]` | lock | event, command, or API timer |
| `[[triggered_unlocks]]` | unlock | event, command, or API timer |

The generic `[[conditional_rules]]` form accepts `effect = "lock"` or `"unlock"` and
`activation = "live"` or `"triggered"`.

#### Priority resolution

Normal lock categories behave as a lock at priority `0`. Conditional rules default to priority
`100`. The highest matching priority wins. When a lock and unlock tie at the highest priority, the
lock wins. When effect and priority both match, the first loaded rule remains the winner.

This makes a deliberate three-layer policy possible:

```text
Priority 0. Mage stage normally gates the Stronghold.
Priority 100. End Fight ownership permits the Stronghold.
Priority 200. An emergency event can lock it again.
```

Accepted priority values are `-1000000` through `1000000`. A negative rule cannot defeat a normal
priority-zero gate. An `[except]` target only removes content from that one rule and is not a global
whitelist.

#### Rule fields

```toml
[[triggered_locks]]
id = "dragon_swords_only"       # required and unique
priority = 200                  # default 100
stage_state = "owned"           # owned, missing, or always
trigger = "combat"              # manual, combat, attack, hurt, or kill
trigger_entities = ["minecraft:ender_dragon"]
duration = "15s"                # or duration_seconds = 15
refresh_duration = true

[triggered_locks.targets]
items = ["tag:mypack:weapons"]

[triggered_locks.except]
items = ["tag:minecraft:swords"]
```

Short rule ids are canonicalized beneath the containing stage. A rule named `dragon_swords_only` in
`end_fight.toml` becomes `progressivestages:end_fight/dragon_swords_only`. A fully namespaced rule id
is retained as written. Rule ids must be globally unique after canonicalization.

`stage_state = "owned"` requires ownership of the containing stage. `missing` requires the stage to
be unowned. `always` ignores ownership. `active_when` is an alias for `stage_state`.

`refresh_duration = true` restarts a currently active timer whenever its trigger matches again.
When false, another trigger cannot replace an unexpired timer. Command and API calls can override the
configured duration but still respect `stage_state`, `.when`, and refresh policy.

Timed state is deliberately transient. It clears on logout and server stop. Use `[[triggers]]` to
grant a stage if the result must persist across sessions.

#### Targets and exceptions

Each rule requires a `.targets` table with at least one non-empty list. `.except` is optional.

```toml
[temporary_locks.targets]
items = []
blocks = []
fluids = []
entities = []
recipes = []
dimensions = []
structures = []
abilities = []
```

Items, blocks, fluids, and entities accept `id:`, implicit exact ids, `mod:`, `tag:` or `#`, and
`name:`. Recipes, dimensions, structures, and abilities accept exact ids, `id:`, `mod:`, and
`name:` but not tags. Ability ids enforced by the built-in engine are `jump`, `elytra`, `sprint`,
`swim`, and `climb`.

Conditional item and block decisions continue through the normal global toggles and the containing
stage's `[enforcement]` overrides. For example, a weapons policy can set `block_item_use = true`,
`block_item_pickup = false`, and `block_item_inventory = false` to deny attacks or use without
ejecting carried weapons. Per-stage overrides apply to every matching rule owned by that stage.

A rule may target several categories. This is valid:

```toml
[temporary_locks.targets]
items = ["minecraft:diamond_pickaxe"]
blocks = ["minecraft:diamond_ore"]
fluids = ["minecraft:lava"]
abilities = ["jump", "elytra"]
```

#### Live `.when` context

All fields are optional. Omitted fields impose no requirement.

```toml
[temporary_locks.when]
mode = "all_of"                 # all_of or any_of. all and any are aliases
dimensions = ["minecraft:the_end"]
structures = ["minecraft:stronghold"]
biomes = ["#minecraft:is_forest"]
min_y = 0
max_y = 100
min_health = 1.0
max_health = 20.0
stages = ["end_fight"]          # all listed stages must be owned
missing_stages = ["escaped_end"]
effects = ["minecraft:darkness"]
sneaking = false
sprinting = false
swimming = false
riding = false
on_ground = true
script = "pack_condition"
```

`mode = "all_of"` requires each supplied context group. `any_of` requires at least one supplied
group. Structure context uses the generated structure bounding box rather than the complete chunk.
Context is cached for one game tick per player.

The `script` value invokes the predicate registered through
`ProgressiveStages.condition('pack_condition', player => boolean)`.

#### Mage, Wither, Stronghold, and End example

`mage.toml` establishes ordinary priority-zero gates:

```toml
[stage]
id = "mage"
display_name = "Mage"

[structures]
locked_entry = ["minecraft:stronghold"]

[dimensions]
locked = ["minecraft:the_end"]
```

`end_fight.toml` earns a stage through the Wither kill, uses that stage to override Mage's
Stronghold and End gates at priority `100`, and installs stronger priority `200` battle rules while
inside the End:

```toml
[stage]
id = "end_fight"
display_name = "End Fight"

[[triggers]]
type = "kill"
target = "minecraft:wither"
count = 1

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

#### Structure weapon examples

Lock all weapons except swords while inside a Stronghold:

```toml
[[temporary_locks]]
id = "stronghold_swords_only"

[temporary_locks.when]
structures = ["minecraft:stronghold"]

[temporary_locks.targets]
items = ["tag:mypack:weapons"]

[temporary_locks.except]
items = ["tag:minecraft:swords"]
```

If a normal stage already gates all weapons, temporarily permit them except bows inside an arena:

```toml
[[temporary_unlocks]]
id = "arena_weapon_permission"

[temporary_unlocks.when]
structures = ["mypack:trial_arena"]

[temporary_unlocks.targets]
items = ["tag:mypack:weapons"]

[temporary_unlocks.except]
items = ["minecraft:bow", "minecraft:crossbow"]
```

If weapons are normally permitted, use a temporary lock targeting bow and crossbow instead. A broad
weapon tag should be supplied by the pack because vanilla cannot identify every modded weapon.

#### Commands, KubeJS, and Java

The command root is `/pstages`. The mod does not reserve `/ps`.

```text
/pstages rule info <rule>
/pstages rule list [player]
/pstages rule activate <player> <rule> [seconds]
/pstages rule clear <player> <rule>
/pstages rule clearall <player>
```

```javascript
ProgressiveStages.activateRule(player, 'end_fight/manual_permission')
ProgressiveStages.activateRule(player, 'end_fight/manual_permission', 60)
ProgressiveStages.clearRule(player, 'end_fight/manual_permission')
ProgressiveStages.clearRules(player)
let secondsRemaining = ProgressiveStages.activeRules(player)
let ids = ProgressiveStages.ruleIds()
let definition = ProgressiveStages.ruleInfo('end_fight/manual_permission')
```

```java
ProgressiveStagesAPI.activateConditionalRule(player, ruleId, 60_000L);
ProgressiveStagesAPI.clearConditionalRule(player, ruleId);
ProgressiveStagesAPI.clearConditionalRules(player);
Map<ResourceLocation, Long> remainingMillis =
    ProgressiveStagesAPI.getActiveConditionalRules(player);
Set<ResourceLocation> ruleIds = ProgressiveStagesAPI.getConditionalRuleIds();
Optional<ConditionalRule> definition = ProgressiveStagesAPI.getConditionalRule(ruleId);
```

`/progressivestages validate` checks exact registry ids in conditional item, block, fluid, entity,
effect, and trigger-entity selectors. Candidate activation also rejects cross-file duplicate rule ids
and nonexistent stage ids in `.when.stages` or `.when.missing_stages`.

---

### 4.35 Structure session providers, leased stages, and active locks

3.0.1 exposes a generic exact-instance compatibility layer for assignment, dungeon, arena, and
quest mods. A companion mod registers a `StructureContextProvider`; ProgressiveStages remains the
only authority that grants or revokes stages, rejects actions, repels players, and commits enter,
completion, or leave events. ProgressiveStages does not depend on the companion mod.

Use ordinary `[structures]` for the broad access gate. Use an optional in-progress stage as a
team-safe lease while the participant is inside the provider's exact bounds. Use `[active_locks]`
when an owned in-progress stage should block item use only inside its matching session.

```toml
[stage]
id = "stronghold_active"
display_name = "Stronghold Run Active"

[active_locks]
scope = "structure_session"

[active_locks.items]
locked = ["id:minecraft:bow", "id:minecraft:crossbow"]
always_unlocked = []

[active_locks.enforcement]
block_item_use = true
```

This is intentionally separate from `[items]`:

- `[items]` blocks because its stage is missing.
- `[active_locks.items]` blocks because its stage is present inside the matching live session.
- Active locks do not change inventory holding, pickup, crafting, loot, tooltips, JEI, or EMI.

The event-driven leave trigger grants its owning stage only from a committed session transition:

```toml
[stage]
id = "stronghold_cleared"

[[triggers]]
type = "leave_structure"
structure = "minecraft:stronghold"
provider = "mypack:assignments"
required_session_stage = "stronghold_active"
outcomes = ["completed"]
```

Aliases are `leave_structure`, `leave_structures`, `exit_structure`, and `structure_exit`.
Structure tags are supported. Outcomes are `incomplete`, `completed`, `cancelled`, `death`,
`teleport`, `dimension_change`, `disconnect`, and `recovery`; omit the filter or use `any` to
accept every outcome.

The complete provider contract, copy-ready Java example, arbitration truth table, stage files,
lease rules, team behavior, lifecycle semantics, commands, failure handling, and forty-step
acceptance matrix are in
[STRUCTURE_SESSION_COMPATIBILITY.md](STRUCTURE_SESSION_COMPATIBILITY.md).

### 4.36 `[[drop_modifiers]]` — selector based block output bonuses

A drop modifier changes the count of matching final item entities produced by a broken block. It is
server authoritative and active only when its owning stage is owned unless `with_stages` overrides
that requirement. The normal prefix system works independently for source blocks, output items,
and tools.

```toml
[[drop_modifiers]]
id = "my_pack:diamond_engineer/diamond_fortune"
blocks = ["minecraft:diamond_ore", "minecraft:deepslate_diamond_ore"]
drops = ["minecraft:diamond"]
tools = ["tag:minecraft:pickaxes"]
required_enchantment = "minecraft:fortune"
minimum_enchantment_level = 1
multiply = 2.0
add = 0
minimum = 0
maximum = 64
priority = 600
exclusive = true
```

| Field | Required | Meaning |
|---|---:|---|
| `id` | No | Stable rule ID. A deterministic stage child ID is generated when omitted. |
| `blocks` or `source_blocks` | Yes | Source block selectors. At least one must match the block that was broken. |
| `drops`, `output_items`, or `items` | Yes | Final output item selectors. At least one must match each stack being transformed. |
| `tools` or `tool_items` | No | Tool selectors. Empty means any tool, including an empty tool. |
| `with_stages` | No | Every listed stage must be owned. Defaults to the stage that contains the rule. |
| `without_stages` | No | None of the listed stages may be owned. |
| `condition`, `when`, or `while` | No | Any compiled live condition, such as dimension, biome, structure, or session state. |
| `required_enchantment` | No | Exact enchantment registry ID that the used tool must contain. |
| `minimum_enchantment_level` | No | Minimum level of the required enchantment. Defaults to 1 when an enchantment is configured. |
| `add` | No | Added to the current stack count before multiplication. Defaults to 0. |
| `multiply` | No | Multiplies the count after addition. Defaults to 1. |
| `minimum` | No | Lower bound after transformation. Defaults to 0. |
| `maximum` | No | Upper bound after transformation. Defaults to the Java integer maximum. |
| `priority` | No | Higher priority rules run first. Defaults to the owning stage priority. |
| `exclusive` | No | Stop evaluating lower priority rules after this rule matches. Defaults to false. |

The count formula is `floor((current + add) * multiply)`, then the minimum and maximum bounds are
applied. Several nonexclusive rules intentionally stack in descending priority order. An exclusive
match applies itself and stops lower priority rules. Item stacks larger than their maximum stack
size are split into safe additional item entities rather than creating an oversized stack.

The event order is loot lock filtering, crop filtering, ore masquerade replacement, then drop
modifiers. This prevents a hidden real ore from leaking its multiplied real output. Conditions and
stage ownership are read at break time, so grants, revokes, temporary stage changes, and reloads
take effect without a relog.

The first-launch [Showcase Pack](SHOWCASE_PACK.md) contains Diamond Engineer. It requires Iron
Engineer, costs 32 diamonds, and doubles only diamond item output from diamond or deepslate
diamond ore when the used pickaxe has at least Fortune I. The easy editor exposes the same system
under **Targeted mining bonus**.

### 4.37 Stage slots, class limits, replacements, and stacking

Stage slots control how many related stages may be owned at the same time. They are general-purpose:
the server does not hardcode concepts such as class, job, mode, or specialization. The group name
defines the meaning for your pack.

This example allows two of three beginner paths:

```toml
[stage]
id = "mage"
slot_group = "beginner_paths"
slot_limit = 2
slot_policy = "deny"
```

Put the same three values in Warrior and Ranger. A team may own Mage plus Warrior or Mage plus
Ranger, but a third member is denied. The group remains server authoritative for purchases,
commands, triggers, temporary session grants, Java, and KubeJS.

Use unlimited stacking for upgrade tiers whose bonuses should accumulate:

```toml
[stage]
id = "diamond_engineer"
dependency = "iron_engineer"
slot_group = "engineering_tiers"
slot_limit = 0
slot_policy = "deny"
```

`slot_limit = 0` disables the limit without removing the useful group label. Coal, Iron, Diamond,
and Netherite Engineer can all remain owned, so each stage's rules and modifiers remain active.

Use automatic replacement for mutually exclusive modes:

```toml
[stage]
id = "silk_mode"
slot_group = "mining_modes"
slot_limit = 1
slot_policy = "replace_oldest"
```

| Policy | Exact behavior when a grant would exceed the limit |
|---|---|
| `deny` | Reject the complete grant before changing ownership or charging a purchase. |
| `replace_oldest` | Revoke only as many oldest-owned group members as necessary. Grant time breaks ties before stage ID. |
| `replace_lowest_priority` | Revoke the members with the lowest `[stage].priority` first. Oldest grant and stage ID break ties. |
| `replace_all` | Revoke every currently owned member of the group, then grant the new stage. |

All members of one group must declare the same limit, policy, and ownership scope. `/pstages
validate`, editor review, and reload reject inconsistent groups. This prevents one member from
claiming the group holds one stage while another claims it holds three.

When a policy replaces a stage, the normal revoke event fires with cause `GROUP_POLICY`. Attribute,
ability, rule, recipe-viewer, and client caches reconcile immediately. The in-game stage details
show the group name, active count, limit, and policy. A rejected purchase does not consume its
items or XP. A replaced stage that was purchased receives its normal configured `refund_percent`;
earned or command-granted stages cannot mint a refund.

In `/pstages editor`, select the stage and open **Stage slots and stacking**. The guided form asks
for a group, maximum active count, and full-group policy. It can apply the configuration to every
existing member in one operation. Advanced authors can edit the three keys in `stage.toml`.

The generated [fifty stage showcase](SHOWCASE_PACK.md) contains all three common patterns:

- `beginner_paths` permits any two of Mage, Warrior, and Ranger, then denies a third.
- `engineering_tiers` has no limit, so the six engineering bonuses stack.
- `mining_modes` permits one mode and replaces it when another mode is acquired.

## 5. Triggers — The Per-Stage `[[triggers]]` System

Triggers are declared **per stage**, inside that stage's own TOML, in one or
more `[[triggers]]` blocks. There is no global `triggers.toml` anymore (a
leftover one is ignored). When a stage's triggers fire, the stage grants
itself — no `/stage grant` required.

The system reads Minecraft's own **statistics** wherever it can, which makes
most conditions **retroactive** (a player already past the threshold is
credited the instant the trigger loads) and **save-file-free** for those
condition types.

**Triggers respect dependencies — New in 2.3.** A stage is **not** auto-granted
by its `[[triggers]]` until **all** of the stage's `[stage].dependency`
prerequisites are owned. Counter progress keeps accruing from vanilla stats
while the stage waits on its prerequisites, so the moment the last prerequisite
is granted the next poll completes the unlock — no progress is lost. To make a
trigger fire **freely, regardless of progression**, simply **omit the stage's
`dependency`** (a stage with no dependencies grants the instant its rule is
satisfied).

### 5.1 Rules, conditions, and modes

```toml
# One stage may declare several [[triggers]] blocks.
# Each [[triggers]] block is ONE independent RULE.
# Rules are OR-ed: the stage is granted when ANY rule is fully satisfied.

[[triggers]]
mode = "all_of"                       # default; "any_of" is the alternative

  [[triggers.conditions]]
  type  = "kill"
  entity = "minecraft:enderman"
  count = 10

  [[triggers.conditions]]
  type  = "kill"
  entity = "minecraft:ender_dragon"
  count = 1
```

Anatomy:

- **Rule** — one `[[triggers]]` table. A stage may have several. Rules are
  **OR-ed**: satisfy *any one* rule and the stage is granted.
- **Conditions** — one or more `[[triggers.conditions]]` tables inside a rule.
  How they combine is set by the rule's `mode`:
  - `mode = "all_of"` (default) — every condition must be satisfied.
  - `mode = "any_of"` — the rule is satisfied the moment *any* condition is.
- **Shorthand** — a rule with a **single** condition may put the condition's
  fields **directly on the `[[triggers]]` table** (no nested
  `[[triggers.conditions]]`). The two forms below are identical:

  ```toml
  # shorthand
  [[triggers]]
  type  = "mine"
  block = "minecraft:diamond_ore"
  count = 10
  ```

  ```toml
  # long form
  [[triggers]]
  mode = "all_of"
    [[triggers.conditions]]
    type  = "mine"
    block = "minecraft:diamond_ore"
    count = 10
  ```

### 5.2 Condition types

Every condition has a `type`. The **subject key** (the second column) tells the
condition *what* to count; `count` is the threshold (defaults to `1` if
omitted).

| `type` | Subject key | Meaning | Counter? |
|--------|-------------|---------|----------|
| `kill` | `entity` = `<id\|tag>` | Mob kills | yes |
| `mine` | `block` = `<id\|tag>` | Blocks mined | yes |
| `craft` | `item` = `<id\|tag>` | Items crafted | yes |
| `pickup` | `item` = `<id\|tag>` | Items picked up (cumulative) | yes |
| `use` | `item` = `<id\|tag>` | Items used / right-clicked | yes |
| `drop` | `item` = `<id\|tag>` | Items dropped | yes |
| `break_item` | `item` = `<id\|tag>` | Tools / items broken | yes |
| `distance` | `movement` = `<kind>` | Distance travelled, in **blocks** | yes |
| `stat` | `stat` = `<custom_stat_id>` | Any vanilla custom statistic | yes |
| `play_time` | *(none)* | Time played, in **minutes** | yes |
| `level` | *(none)* | Current experience level | state |
| `xp` | *(none)* | Current total experience points | state |
| `has_item` | `item` = `<id\|tag>` | Count currently in inventory | state |
| `advancement` | `advancement` = `<id>` | Advancement earned | persisted (vanilla) |
| `dimension` | `dimension` = `<id>` | Dimension entered at least once | one-shot |
| `biome` | `biome` = `<id\|tag>` | Biome visited at least once | one-shot |
| `effect` | `effect` = `<id>` | **New in 2.4.** Currently has a status effect (e.g. `effect = "minecraft:strength"`) | state |
| `breed` | `entity`/`animal` = `<id\|tag>` (optional) | **New in 2.4** (target **New in 2.5**). Animals bred — no target = all (retroactive); with a target = that species/tag (event-counted) | yes |
| `day_count` | `count` = `<N>` | **New in 2.4.** Reached world day N | state |
| `world_time` | `count` = `<tick>` | **New in 2.5.** Current time-of-day tick within the day (`0..23999`) — e.g. trigger at night | state |
| `weather` | `weather` = `"rain"\|"thunder"\|"clear"` | **New in 2.4.** Experienced this weather (persisted one-shot) | one-shot |
| `enter_structure` | `structure` = `<id>` | **New in 2.4.** Entered this structure at least once (persisted one-shot) | one-shot |
| `tame` | `entity` = `<id\|tag>` (optional) | **New in 2.4.** Animals tamed | yes (mod counter) |
| `kill_with` | `entity` = `<id\|#tag>`, `with`/`item` = `<id>` | **New in 2.4** (`#tag` victim **New in 2.5**). Killed `entity` while holding `with` | yes (mod counter) |
| `script` | `id` = `<conditionId>` | **New in 2.5.** Custom condition evaluated by a KubeJS-registered predicate (§11) | state |
| `reach_y` | *(none)* — `count` = `<Y>` | **New in 3.0.** Satisfied while the player's current block-Y is **≥ `count`** (aliases `altitude`, `y_level`, `height`) | state |
| `fish` | *(none)* | **New in 3.0.** Fish caught (`Stats.FISH_CAUGHT`) — retroactive (aliases `fishing`, `fish_caught`) | yes |
| `sleep` | *(none)* | **New in 3.0.** Times slept in a bed (`Stats.SLEEP_IN_BED`) — retroactive (aliases `slept`, `sleep_in_bed`) | yes |
| `ride` | *(none)* | **New in 3.0.** Blocks ridden on **any** vehicle (minecart/boat/pig/horse/strider distance stats, summed) — retroactive (aliases `riding`, `ride_distance`) | yes |
| `biome_time` | `biome` = `<id\|#tag>` | **New in 3.0.** **Seconds** spent in the target biome / tag. `count` = seconds, or a friendly `duration = "5m"` | yes (mod counter, event-polled) |
| `stage_held_for` | `stage` = `<stageId>` | **New in 3.0.** Held another stage for **≥ `count` seconds** (`count` = seconds, or `duration = "3d"`) | state (time since grant) |
| `custom_counter` | `counter` = `<name>` | **New in 3.0.** Named counter managed by `/stage counter ...`, KubeJS, or the Java API | yes (mod counter) |
| `scoreboard` | `objective` = `<name>` | **New in 3.0.** Live value of a vanilla scoreboard objective for this player | state/counter |
| `health` | *(none)* | **New in 3.0.** Current health points (`20` at normal full health) | state |
| `food` | *(none)* | **New in 3.0.** Current hunger/food level (`20` at full hunger) | state |
| `stage_count` | *(none)* | **New in 3.0.** Number of effective team/server stages currently owned | state |
| `online_team_size` | *(none)* | **New in 3.0.** Number of team members currently online | state |
| `script_value` | `id` = `<providerId>` | **New in 3.0.** Numeric progress returned by `ProgressiveStages.progressCondition(...)` | state/counter |

**New in 2.4 condition details:**

- **`effect`** — a **live state** check: satisfied while the player *currently
  has* the named status effect, e.g. `effect = "minecraft:strength"`. Like
  `level` / `xp` it can flip back to unsatisfied when the effect wears off, until
  the whole rule fires.
- **`breed`** — counts animals **bred**. **New in 2.5:** the `entity`/`animal`
  target is now **optional**. With **no target** it counts **all** bred animals via
  the vanilla `ANIMALS_BRED` stat (retroactive); with a **target** (`entity = "minecraft:cow"`
  or `entity = "#minecraft:..."`) it counts **that species/tag**, **event-counted**
  (non-retroactive — only accrues from when the trigger loads, matching `tame` /
  `kill_with`). `count` defaults to 1.
- **`day_count`** — satisfied once the world day number reaches `count = N`.
- **`weather`** — a persisted one-shot: once the player has **experienced** the
  named weather (`"rain"`, `"thunder"`, or `"clear"`) it stays satisfied.
- **`enter_structure`** — a persisted one-shot: satisfied once the player has
  entered the named structure (`structure = "minecraft:village_plains"`, etc.).
- **`tame`** — counts animals **tamed**. Optional `entity` narrows it to a
  specific type / tag; omit it to count any tame.
- **`kill_with`** — counts kills of `entity` performed **while holding** the
  `with` item (alias `item`). Example: `entity = "minecraft:ender_dragon"`,
  `with = "minecraft:diamond_sword"`, `count = 1`. **New in 2.5:** the `entity`
  victim may also be a **`#tag`** (e.g. `entity = "#minecraft:skeletons"`), in
  which case the count is **summed over every entity type in the tag** (all sharing
  the same held `with` item).

**New in 2.5 condition details:**

- **`world_time`** — a **live state** check on the **time of day**: satisfied when
  the world's current daytime tick (`getDayTime() % 24000`, in `0..23999`) reaches
  `count`. For example `count = 13000` is roughly nightfall. Aliases:
  `time_of_day`, `daytime`, `clock`. (Note: `day_count` reads the *day number*;
  `world_time` reads the *tick within the current day*.)
- **`script`** — a **fully custom** condition evaluated by a **KubeJS-registered
  predicate**. Reference it with `type = "script"` and `id = "<conditionId>"`; the
  predicate is registered from a server script with `ProgressiveStages.condition('<conditionId>', player => <boolean>)`
  (see §11). It evaluates as a live **state** check (the rule fires once the
  predicate — and the rest of the rule — is satisfied). If no script registered
  that id, it evaluates to `false`. Aliases: `js`, `kubejs`, `custom`.

**New in 3.0 condition details:**

- **`reach_y`** — a **live state** check on **altitude**: satisfied while the
  player's current block-Y position is **≥ `count`**. It takes **no target**; set
  the height with `count`. Example: `type = "reach_y", count = 200` fires while
  the player is at or above Y 200. Like the other state checks it can flip back
  to unsatisfied (descend below the threshold) until the whole rule fires.
  Aliases: `altitude`, `y_level`, `height`.
- **`fish`** — counts **fish caught**, read from the vanilla `FISH_CAUGHT`
  statistic, so it is **retroactive** and restart-proof (no target). Aliases:
  `fishing`, `fish_caught`.
- **`sleep`** — counts **nights slept in a bed**, read from the vanilla
  `SLEEP_IN_BED` statistic — **retroactive**, no target. Aliases: `slept`,
  `sleep_in_bed`.
- **`ride`** — counts **blocks ridden on any vehicle**: the sum of the vanilla
  minecart / boat / pig / horse / strider distance statistics (converted from cm
  to blocks). **Retroactive**, no target. Aliases: `riding`, `ride_distance`.
- **`biome_time`** — accrues **seconds spent inside** the target `biome`
  (`biome = "minecraft:desert"` or a `#tag` like `biome = "#minecraft:is_jungle"`).
  Set the threshold either as raw seconds (`count = 300`) **or** with a friendly
  `duration = "5m"` (the duration wins when present; units `s`/`m`/`h`/`d`, bare
  number = minutes). Time is **event-polled** — accrued at the trigger poll
  cadence while the player is in the biome (so it only counts from when the
  trigger loads, like the other mod-tracked counters), and persisted by the mod.
  Aliases: `time_in_biome`, `biome_seconds`.
- **`stage_held_for`** — satisfied once the player's team has **owned the target
  stage** (`stage = "iron_age"`) for **at least `count` seconds**. Set the
  threshold as raw seconds (`count = 86400`) **or** with a friendly
  `duration = "3d"`. This reads the **grant timestamp** the mod now records for
  **every** stage (in `StageRegressionData`), measuring real elapsed time since
  the grant; if the player's team doesn't own the target stage (or there is no
  recorded grant time) it reports `0`. A natural way to chain "you've lived with
  X for a while → unlock Y". Aliases: `held_stage`, `stage_age`, `owned_for`.

> **Counter source note.** `tame`, `kill_with`, and (new in 3.0) `biome_time` use
> **mod-tracked counters** (the mod increments them itself), so unlike the §5.3
> retroactive types they are **not** derived from vanilla statistics and only count
> events that happen after the trigger exists. `breed` and the other counters read
> live state / vanilla stats as noted above; the new-in-3.0 `fish` / `sleep` /
> `ride` counters **are** vanilla-stat-backed and therefore **retroactive**;
> `reach_y` / `stage_held_for` (and `effect` / `day_count`) are live state and
> `weather` / `enter_structure` are persisted one-shots.

**`distance` movement kinds:** `walk`, `sprint`, `crouch`, `swim`, `fall`,
`climb`, `fly`, `walk_under_water`, `walk_on_water`, `minecart`, `boat`, `pig`,
`horse`, `strider`, `aviate` (alias `elytra`), or **`all`** (the sum of every
kind). All distances are in **blocks** (the mod converts vanilla's cm
statistics for you).

**`stat` custom statistics:** any vanilla custom stat id works, e.g.
`minecraft:jump`, `minecraft:deaths`, `minecraft:damage_dealt`,
`minecraft:mob_kills`, `minecraft:time_since_rest`. Use this when no dedicated
condition type covers what you want to count.

**State vs. one-shot:**

- `level` / `xp` / `has_item` — and the new-in-3.0 `reach_y` (altitude) and
  `stage_held_for` (seconds since the target stage was granted) — are
  **momentary state** checks: they read the player's *current* level, total XP,
  inventory, altitude, or stage-age at poll time. A condition that was satisfied
  can become unsatisfied again (e.g. the player spends a level, or descends below
  the `reach_y` threshold) right up until the whole rule fires and the stage is
  granted.
- `advancement` reads vanilla advancement progress (persisted by vanilla).
- `dimension` / `biome` are **visited** one-shots — once visited, they stay
  satisfied. Their "visited" flag is persisted by the mod per player (see §5.5).

### 5.3 Tags, counts, and retroactivity

- **`count`** defaults to `1` when omitted.
- **Tags.** Write the subject as `#namespace:path` or `tag:namespace:path` to
  count across **all members** of the tag. For example
  `entity = "#minecraft:skeletons"` counts kills of every skeleton variant;
  `block = "tag:c:ores"` counts mining any ore.
- **Retroactive counters.** The counter condition types
  (`kill`, `mine`, `craft`, `pickup`, `use`, `drop`, `break_item`, `distance`,
  `stat`, `play_time`, and the new-in-3.0 `fish` / `sleep` / `ride`) read
  Minecraft's vanilla **statistics**. That means:
  - They are **retroactive** — add a trigger to a server with existing players
    and anyone already past the threshold is credited the instant the trigger
    loads.
  - They **survive restarts** with **no extra save files** (the data already
    lives in each player's vanilla stats).
- **Per-player progress, team grant.** Progress is tracked per **player**. The
  **first team member** to satisfy a whole rule unlocks the stage for the
  **entire team** (in team mode).

### 5.4 Worked examples

**Example 1 — `all_of`: kill 10 endermen AND the ender dragon.**

```toml
[[triggers]]
mode = "all_of"

  [[triggers.conditions]]
  type   = "kill"
  entity = "minecraft:enderman"
  count  = 10

  [[triggers.conditions]]
  type   = "kill"
  entity = "minecraft:ender_dragon"
  count  = 1
```

**Example 2 — single-condition shorthand: travel 100,000 blocks (any way).**

```toml
[[triggers]]
type     = "distance"
movement = "all"
count    = 100000
```

`movement = "all"` sums every travel kind. Want only on-foot distance? Use
`movement = "walk"`.

**Example 3 — `any_of`: visit the Nether OR the End.**

```toml
[[triggers]]
mode = "any_of"

  [[triggers.conditions]]
  type      = "dimension"
  dimension = "minecraft:the_nether"

  [[triggers.conditions]]
  type      = "dimension"
  dimension = "minecraft:the_end"
```

**Example 4 — two independent rules (OR) on one stage.** Either path grants
the stage: finish the smelt-iron advancement, *or* mine 64 iron ore the hard
way.

```toml
# Rule A — the advancement route (single-condition shorthand)
[[triggers]]
type        = "advancement"
advancement = "minecraft:story/smelt_iron"

# Rule B — the grind route (single-condition shorthand, tag subject)
[[triggers]]
type  = "mine"
block = "#c:ores/iron"
count = 64
```

**Example 5 — mixed condition types in one rule.** Reach level 30 AND craft a
diamond pickaxe.

```toml
[[triggers]]
mode = "all_of"

  [[triggers.conditions]]
  type  = "level"
  count = 30

  [[triggers.conditions]]
  type  = "craft"
  item  = "minecraft:diamond_pickaxe"
  count = 1
```

### 5.5 Polling, persistence, and reset

**Poll cadence.** Counter and state conditions are re-checked on a timer set by
`enforcement.trigger_poll_interval` (ticks, default `20` = once per second,
range `5`–`200`). Relevant events — mob kills, advancements earned, dimension
changes, and login — also force an **immediate** re-check, so most triggers
feel instant.

**Dependencies gate the grant.** A satisfied rule only grants the stage once
every `[stage].dependency` prerequisite is owned. While a prerequisite is
missing the rule may be fully satisfied yet **held back**; counters keep
accruing, and the very next poll after the last prerequisite is granted
completes the unlock. Omit the stage's `dependency` to let its triggers fire
freely.

**Persistence.** Most condition types need **no** save file of their own:
counters live in vanilla statistics, `level` / `xp` / `has_item` are read live,
and `advancement` is persisted by vanilla. Only the `dimension` and `biome`
**visited** one-shots are persisted by the mod, per player, in
`world/data/progressivestages_triggers.dat`.

**Reset.** Clear a stage's persisted one-shot (dimension / biome) visited flags
for a player with:

```
/progressivestages trigger reset <player> <stage>
```

This affects only the persisted one-shots; retroactive counters are derived
from vanilla stats and cannot be "un-credited" by the mod (clear the
underlying vanilla statistic if you truly need to).

**Inspecting.** Use `/progressivestages triggers list [player]` to see every
stage that declares `[[triggers]]` rules, with a player's live per-condition
progress, or `/stage progress <stage> [player]` for one stage's full
rule/condition breakdown. See §7.

### 5.6 The Stage Tree viewer + keybind

ProgressiveStages 3.0 ships an in-game progression map built in vanilla's
**advancement-screen visual language**: task/goal/challenge frames, elbow
dependency connectors, a tiled background, hover cards, and a pinned inspector.

- **Open it** with the **"Open Progression Tree"** keybind (category
  *ProgressiveStages* in Controls). It is **UNBOUND by default** — players
  assign a key in Options → Controls. You can also use `/stage`, `/stages`,
  `/pstages`, `/stage gui`, or the lock button immediately to the right of the
  recipe-book button in the survival inventory.

**Navigation.** Hold the left mouse button and drag from empty map space or from
a stage node. A short click opens that node, while movement pans the map like
the vanilla advancement screen. The mouse wheel scrolls vertically;
Shift+wheel scrolls horizontally. WASD and the arrow keys also pan. The header
home button returns to the most relevant available stage. The in-map category
selector cycles with left/right click; `C` and Shift+`C` cycle it from the
keyboard.

**Nodes and details.** Hover a framed node for its name, id, status,
description, category, and trigger completion. Click it to pin an inspector with
its prerequisites, full live `[[triggers]]` condition breakdown grouped into
clearly labeled `all_of` or `any_of` routes, unlock item preview, and purchase
control. The inspector occupies a dedicated layer above every map node and item
icon. Search matches display name, id, description, category, and locked item
ids. The Owned button hides completed nodes.

**Author layout (`[display]`).** Omit coordinates for automatic dependency-DAG
layout, or specify both `x` and `y` (pixels). `frame` is `task`, `goal`, or
`challenge`; `background` is a tiled texture id; `reveal` is `always`,
`dependencies`, or `unlocked`; and `sort_order` stabilizes automatic lane order.

```toml
[display]
x = 168
y = -46
frame = "challenge"
background = "minecraft:block/deepslate_tiles"
reveal = "dependencies"
sort_order = 20
```

The background value is resolved as a client texture resource. The example
above resolves to `assets/minecraft/textures/block/deepslate_tiles.png`. A pack
texture stored at `assets/mypack/textures/gui/progression.png` is selected with:

```toml
[display]
background = "mypack:gui/progression"
```

**Skill-tree Unlock button — New in 2.4.** When a stage declares a `[cost]`
table (§4.26), its detail pane shows an **Unlock** button: the GUI doubles as a
**skill tree** where players *buy* stages. The button appears according to the
stage's `[cost].bypass_requirements` setting, and clicking it runs a
**server-validated purchase** (consuming the `xp_levels` / `items`) that grants
the stage with `StageCause.PURCHASE`. The map is otherwise read-only; the
Unlock button is the **only** way it mutates stages, and only for `[cost]`
stages. There is **no new command** for purchasing — it happens inside
`/stage gui`.

Implementation pointers:

- Data model: [`TriggerRule`](src/main/java/com/enviouse/progressivestages/common/trigger/TriggerRule.java),
  [`TriggerCondition`](src/main/java/com/enviouse/progressivestages/common/trigger/TriggerCondition.java),
  [`TriggerConditionType`](src/main/java/com/enviouse/progressivestages/common/trigger/TriggerConditionType.java),
  [`TriggerMode`](src/main/java/com/enviouse/progressivestages/common/trigger/TriggerMode.java).
- Server evaluator: [`StageTriggerEvaluator`](src/main/java/com/enviouse/progressivestages/server/triggers/StageTriggerEvaluator.java).
- One-shot persistence: [`TriggerPersistence`](src/main/java/com/enviouse/progressivestages/server/triggers/TriggerPersistence.java).
- Cause enum value: `StageCause.TRIGGER`.
- GUI: [`StageTreeScreen`](src/main/java/com/enviouse/progressivestages/client/gui/StageTreeScreen.java);
  keybind registered in
  [`ClientModBusEvents`](src/main/java/com/enviouse/progressivestages/client/ClientModBusEvents.java)
  (`key.progressivestages.open_tree`, category `key.categories.progressivestages`).

---

## 6. Global Configuration — `progressivestages.toml`

The main mod config lives at `config/progressivestages/progressivestages.toml`.
Stage definitions live in its sibling `config/progressivestages/stages/`
directory. Version 3 migrates the legacy root config and `config/ProgressiveStages/*.toml`
layout without overwriting files already present at the new destination. Every
key has an inline comment in the generated file; this section summarizes them.

Stage files may be organized in nested folders under `stages/`. A reload is compiled and
validated as a candidate snapshot first. If parsing, duplicate IDs, or dependency validation
fails, the server keeps the previous valid stage and lock snapshot active and reports the errors.

The authoritative source is
[`StageConfig`](src/main/java/com/enviouse/progressivestages/common/config/StageConfig.java).
Default values are shown below.

### 6.1 `[general]`

| Key | Default | Meaning |
|-----|---------|---------|
| `starting_stages` | `[]` | Stages auto-granted on first join. Empty by default so showcase classes remain player choices. |
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
| `block_trades` | `true` | `[trades]` villager / wandering-trader trade gating (hide + server-side block) |
| `block_crop_growth` | `true` | `[crops]` planting / growth / bonemeal / harvest |
| `block_pet_interact` | `true` | `[pets]` taming / breeding / commanding / riding |
| `block_loot_drops` | `true` | `[loot]` GLM + mob + block drops |
| `block_mob_replacements` | `true` | `[[mobs.replacements]]` |
| `block_region_entry` | `true` | `[[regions]]` push-back + flags |
| `region_tick_frequency` | `20` | Ticks between region + structure entry checks. 20 = 1s. |
| `block_structure_entry` | `true` | `[structures]` entry + chest locking + rule flags |
| `allow_creative_bypass` | `true` | Creative-mode players bypass enforcement |
| `reveal_stage_names_only_to_operators` | `true` | Non-op players see generic lock messages without stage names (spoiler-free progression) |
| `mask_locked_item_names` | `true` | Locked items show as `"Unknown Item"` (configurable text). Global default for the per-stage `[display].display_as_unknown_item`. |
| `obscure_locked_item_icons` | `false` | **New in 2.3.** Also replace a locked item's ICON with a `?` placeholder in the player's inventory (fully unidentifiable). Global default for the per-stage `[display].obscure_icon`. |
| `trigger_poll_interval` | `20` ticks | **New in 2.3.** Cadence for re-checking counter/state `[[triggers]]` conditions (range 5–200). Kills, advancements, dimension changes, and login also force an immediate re-check. |
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
| `show_tooltip` | `true` | Add lock info to item tooltips. Global default for the per-stage `[display].show_tooltip`. |
| `show_stage_description_on_tooltip` | `false` | **New in 2.3.** Append a locked item's gating stage `[stage].description` to its tooltip (rendered via `messages.tooltip_stage_description`). Global default for the per-stage `[display].show_description_on_tooltip`. |
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

**New in 2.1 — `tooltip_stage_description`** (default `&8“&7{description}&8”`,
placeholder `{description}`): the line template used to render a locked item's
gating-stage description on its tooltip when `emi.show_stage_description_on_tooltip`
(or the per-stage `[display].show_description_on_tooltip`) is enabled.

---

## 7. Commands

Player-facing `/stage` queries and the map are public. Mutations require
permission level 2; authoring/reload/validation operations require level 3.

### 7.1 `/stage` — player stage operations

| Command | Description |
|---------|-------------|
| `/stage grant <player> <stage>` | Grant a stage. Prompts for second-confirm if dependencies missing and `linear_progression = false`. |
| `/stage revoke <player> <stage>` | Revoke a stage. |
| `/stage list [player]` | List the player's stages with progression check marks. |
| `/stage check <player> <stage>` | Boolean check. |
| `/stage info <stage>` | Print stage metadata: id, dependencies, description, lock count. |
| `/stage tree` | ASCII dependency tree of every loaded stage. |
| `/stage gui` | Open the in-game Stage Tree / Progression viewer for yourself (see §5.6). |
| `/stage`, `/stages`, `/pstages` | Friendly aliases that open the progression map. |
| `/stage progress` | Shortcut for `/stage progress next` — what the caller can unlock right now. |
| `/stage progress next [player]` | Lists every stage the player can currently unlock (deps met, not yet granted) with the full `[[triggers]]` rule/condition breakdown for each one. Player defaults to the caller. |
| `/stage progress all [player]` | Lists **every** stage the player doesn't yet have — including those still locked behind unmet dependencies — in registration order. Useful for pack-author audits and "show me the whole roadmap" queries. |
| `/stage progress <stage> [player]` | `[[triggers]]` rule/condition breakdown for one specific stage. Player defaults to the caller. |
| `/stage tag grant <players> <tag>` | **New in 3.0.** Grant **every stage tagged `<tag>`** to each selected player. **Bypasses dependencies** and **skips stages already owned** (only un-owned tagged stages are granted). Reports the change count across stages × players. |
| `/stage tag revoke <players> <tag>` | **New in 3.0.** Revoke every stage tagged `<tag>` from each player (skips stages they don't have). |
| `/stage tag list <tag>` | **New in 3.0.** List every stage that declares `<tag>` in its `[stage].tags`. Tab-completes from all declared tags. |
| `/stage category grant\|revoke <players> <category>` | **New in 3.0.** Bulk-change every stage assigned to a GUI category. Quote category names containing spaces. |
| `/stage category list <category>` | **New in 3.0.** List every stage in a category. |
| `/stage bulk grant\|revoke <players>` | **New in 3.0.** Grant or revoke the complete defined/owned stage set. |
| `/stage sync <players>` | **New in 3.0.** Re-send definitions, lock registry, ownership, and bypass state to selected clients. |
| `/stage simulate [player]` | **New in 3.0.** **Dry-run** of what the player can unlock next: lists their **reachable-next** stages (deps met, not yet owned) sorted by completion %, and for each shows exactly which `[[triggers]]` conditions are still **short** (`current/threshold`, "need N more"). Then lists **dependency-blocked** stages with the prerequisites they're still missing. Player defaults to the caller. Read-only. |
| `/stage new <id>` | **New in 3.0.** **Scaffold** a new stage TOML at `config/progressivestages/stages/<id>.toml` (a commented template with `[stage]`, `[items]`, a sample `[[triggers]]`, and pointers to the optional sections). Refuses to overwrite an existing file; run `/progressivestages reload` after editing. |
| `/stage export` | **New in 3.0.** Write a **markdown progression guide** (`progressivestages_guide.md` in the config folder) built from the stage graph — for each stage: description, **Requires** (deps), **Leads to** (dependents), **Unlock by** (`[[triggers]]` conditions), and whether it's purchasable. |
| `/stage counter get <player> <counter>` | Read a named `custom_counter` value (permission 2). |
| `/stage counter add <player> <counter> <amount>` | Add to a counter and immediately re-evaluate triggers (permission 2). |
| `/stage counter set <player> <counter> <value>` | Set a counter and immediately re-evaluate triggers (permission 2). |
| `/stage counter reset <player> <counter>` | Clear a named counter (permission 2). |
| `/pstages rule info <rule>` | Inspect a conditional rule's canonical id, owner stage, effect, activation, priority, stage state, trigger, duration, targets, and exceptions. |
| `/pstages rule list [player]` | List active timed rules and remaining seconds. Querying another player requires permission 2. |
| `/pstages rule activate <player> <rule> [seconds]` | Start a triggered rule. Omit seconds to use its TOML duration. Requires permission 2. |
| `/pstages rule clear <player> <rule>` | Stop one active triggered rule. Requires permission 2. |
| `/pstages rule clearall <player>` | Stop all active triggered rules. Requires permission 2. |
| `/pstages structure providers` | List registered structure context providers and cached session counts. Requires permission 2. |
| `/pstages structure sessions [player]` | Show exact instance, owner, stages, completion, visit, participants, and pending exit. Another player requires permission 2. |
| `/pstages structure reconcile <player>` | Refresh provider data and repair session leases without a fake gameplay enter. Requires permission 2. |
| `/pstages structure close <player> <session> <outcome> confirm` | Explicit operator recovery close. Requires permission 3 and a final confirmation literal. |

> **Using `/stage progress`.** Three views, one rendering:
>
> - **`/stage progress next`** — the "what can I do right now?" view. Walks
>   every stage you don't own, keeps the ones whose declared dependencies are
>   all satisfied, and renders the `[[triggers]]` breakdown for each. If nothing
>   is reachable, it says so and points you at `/stage tree`.
> - **`/stage progress all`** — same renderer, but applied to every stage you
>   don't yet have (including ones still gated by locked prerequisites). The
>   full roadmap; useful when authoring a pack.
> - **`/stage progress <stage>`** — the targeted view. Same `[[triggers]]`
>   breakdown, scoped to one stage.
>
> Every view reports:
>
> - **Dependencies** — ✓/✗ against the player's current stage set.
> - **`[[triggers]]` rules** — each rule is shown with its `all_of` / `any_of`
>   mode and an `n/total` condition summary; each condition shows its live
>   progress (`current/count` for counters and state, ✓/✗ for one-shots and
>   advancements). Rules are OR-ed, so any one rule completing grants the stage.
>
> If a stage has no `[[triggers]]` and is granted only by `/stage grant`, the
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
| `/progressivestages reload` | 3 | Reload stage TOMLs (including each stage's `[[triggers]]`); re-sync every online player. |
| `/progressivestages validate` | 3 | Parse every stage file with detailed error reporting; warn about dependency issues. **Deepened in 2.5** — see below. |
| `/progressivestages ftb status [player]` | 3 | Diagnostics for the FTB Quests integration. |
| `/progressivestages triggers list [player]` | 3 | List every stage that declares `[[triggers]]` rules; with a player, show their live per-condition progress. |
| `/progressivestages trigger reset <player> <stage>` | 3 | Clear the persisted one-shot (`dimension` / `biome` visited) progress for that stage. |
| `/progressivestages no-creative-popup` | any player | Toggle the creative-bypass warning popup for the calling player only. |

**Deeper `validate` — New in 2.5.** Beyond the original syntax-error, invalid-id,
and self-loop / dead-dependency checks, `validate` now also reports:

- **Full multi-node dependency cycles** — not just `a → a` self-loops but
  `a → b → c → a` chains, found via a white/grey/black DFS and reported once each
  (canonicalised so the same cycle reached from different entry points isn't
  double-listed).
- **Transitively-unreachable stages** — a stage whose dependency closure contains a
  cycle member or a non-existent stage, so its prerequisites can never all be
  satisfied (it is permanently un-grantable).
- **Dead trigger targets** — a `[[triggers]]` condition whose **exact-id** subject
  doesn't resolve in the registry: the entity (`kill` / `kill_with` / `tame`),
  block (`mine`), item (`craft` / `pickup` / `use` / `drop` / `break_item` /
  `has_item`), or mob-effect (`effect`), plus the `kill_with` **held item**. Tags
  (`#...`) and data-driven targets (advancement / dimension / biome / structure /
  raw stat) are intentionally skipped (they aren't in the built-in registries).
- **Profession ids** — `[professions].locked` exact ids are validated against the
  villager-profession registry.
- **Conditional rules** — exact item, block, fluid, entity, effect, and trigger-entity ids,
  cross-file duplicate rule ids, and nonexistent context stage ids are rejected before the live
  snapshot changes.

(Cycle / unreachable detection lives in
[`StageOrder.validateDependencies`](src/main/java/com/enviouse/progressivestages/common/stage/StageOrder.java);
dead-target / profession checks in
[`StageFileLoader.validateTriggerTargets`](src/main/java/com/enviouse/progressivestages/server/loader/StageFileLoader.java).)

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
JEI. Locked items and recipes are **hidden** from both viewers and **re-shown
the moment the gating stage is unlocked** — in EMI **and** JEI alike.

**Both EMI and JEI are fully OPTIONAL — neither is a hard dependency.** Both are
declared `type = "optional"` in `neoforge.mods.toml`, and the EMI mixin config is
`"required": false`, so the mod loads and runs identically whether or not either
viewer is installed; plugins are loaded only when the corresponding mod is present.

> **Fixed in 3.0:** the recipe-viewer **reload no longer crashes when EMI is
> absent.** The reload path that pokes EMI to refresh its index now catches
> `Throwable` (not just `Exception`) around the EMI plugin call — when EMI isn't
> installed, class-loading `ProgressiveStagesEMIPlugin` (it implements EMI's
> `EmiPlugin`) raises a `NoClassDefFoundError` (an `Error`, not an `Exception`),
> which previously propagated and made EMI behave like a hard dependency. The
> JEI side already guards `NoClassDefFoundError`. See
> [`ClientLockCache`](src/main/java/com/enviouse/progressivestages/client/ClientLockCache.java).

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

When KubeJS is installed, ProgressiveStages stages are **first-class KubeJS
stages**. KubeJS scripts read, add, and remove them through the **normal
`player.stages` API** — no PS-specific bindings to learn:

```javascript
// server_scripts/stages.js
PlayerEvents.loggedIn(event => {
    if (event.player.stages.has("iron_age")) {
        event.player.tell("Welcome back, Iron-bearer.")
    }
})

// Add / remove a PS stage from a script (e.g. as a quest/event reward)
PlayerEvents.tick(event => {
    let player = event.player
    player.stages.add("diamond_age")      // grants the PS stage
    player.stages.remove("stone_age")     // revokes the PS stage
})
```

> **Correction (2.5).** Earlier docs claimed PS fires KubeJS's native
> `STAGE_ADDED` / `STAGE_REMOVED` events on **every** grant/revoke. **It does
> not** — KubeJS 7.x has **no native stage events**. KubeJS only fires its own
> internal sync when a script itself calls `player.stages.add/remove(...)`; an
> **engine** grant (a `[[triggers]]` unlock, a command, a quest reward, a
> skill-tree purchase, a regression) does **not** route through that path and
> therefore never fired a KubeJS stage event. The reliable lifecycle hook is the
> new `ProgressiveStages.onGranted` / `onRevoked` API below.

### 11.1 The `ProgressiveStages` global object — **New in 2.5**

A dedicated KubeJS plugin
([`ProgressiveStagesKubeJSPlugin`](src/main/java/com/enviouse/progressivestages/compat/kubejs/ProgressiveStagesKubeJSPlugin.java),
discovered via `kubejs.plugins.txt`) binds a global **`ProgressiveStages`** object
([`PSKubeBindings`](src/main/java/com/enviouse/progressivestages/compat/kubejs/PSKubeBindings.java))
into every script context, giving packs deep, server-authoritative control.

**Lifecycle hooks — fire on EVERY engine grant/revoke** (commands, `[[triggers]]`,
quest rewards, skill-tree purchase, regression — all of them):

```javascript
// server_scripts/stages.js
ProgressiveStages.onGranted((player, stage) => {
    player.tell("Unlocked " + stage)
})
ProgressiveStages.onRevoked((player, stage) => {
    player.tell("Lost " + stage)
})
ProgressiveStages.onChanged((player, stage, change, cause, teamId) => {
    console.info(`${stage} ${change}; cause=${cause}; owner=${teamId}`)
})
```

**Custom trigger conditions — the `script` condition type (§5.2):** register a
predicate by id, then reference it from any stage's `[[triggers]]` with
`type = "script", id = "<id>"`:

```javascript
// stage TOML:  [[triggers]]  type = "script"  id = "rich"
ProgressiveStages.condition('rich', player =>
    player.getMainHandItem().id == 'minecraft:diamond')

// stage TOML: type = "script_value", id = "reputation", count = 100
ProgressiveStages.progressCondition('reputation', player => getReputation(player))
```

**Imperative control / queries** from any KubeJS event:

| Call | Returns | Notes |
|------|---------|-------|
| `ProgressiveStages.has(player, 'stage')` | boolean | Does the player (team) own the stage |
| `ProgressiveStages.grant(player, 'stage')` | boolean | Grant with cause `SCRIPT`; true only when ownership changes |
| `ProgressiveStages.revoke(player, 'stage')` | boolean | Revoke with cause `SCRIPT`; true only when ownership changes |
| `ProgressiveStages.toggle(player, 'stage')` | boolean | Toggle and return the new ownership state |
| `ProgressiveStages.grantBypass(player, 'stage')` | boolean | Grant one stage while intentionally ignoring prerequisites |
| `ProgressiveStages.grantMany/revokeMany(player, stages)` | int | Bulk requested-target change count |
| `ProgressiveStages.grantAll/revokeAll(player)` | int | Bulk change every defined/owned stage |
| `ProgressiveStages.exists('stage')` | boolean | Does the definition exist |
| `ProgressiveStages.available(player, 'stage')` | boolean | Exists, unowned, dependencies satisfied, and its slot policy permits the grant |
| `ProgressiveStages.hasAll/hasAny(player, stages)` | boolean | Collection ownership tests |
| `ProgressiveStages.dependencies('stage')` | string[] | Declared dependencies |
| `ProgressiveStages.missingDependencies(player, 'stage')` | string[] | Dependencies still needed |
| `ProgressiveStages.allDependencies/dependents/allDependents('stage')` | string[] | Dependency graph traversal |
| `ProgressiveStages.withTag('tag')` | string[] | All stages with a tag |
| `ProgressiveStages.grantTag/revokeTag(player, 'tag')` | int | Bulk change count |
| `ProgressiveStages.withCategory/categories()` | string[] | Category lookup/discovery |
| `ProgressiveStages.list/owned(player)` | string[] | All owned stage ids |
| `ProgressiveStages.all/locked/availableStages(...)` | string[] | Definition and player-state lists |
| `ProgressiveStages.info('stage')` | object | Script-friendly definition snapshot including slot group, limit, and policy |
| `ProgressiveStages.slot(player, 'stage')` | object | Slot group, limit, policy, active count, allowed state, replacements, and denial explanation |
| `ProgressiveStages.progress(player, 'stage')` | object | Full rule and condition progress snapshot |
| `ProgressiveStages.percent(player, 'stage')` | int `0..100` | The stage's `[[triggers]]` completion % |
| `ProgressiveStages.counter(player, 'name')` | long | Read a named `custom_counter` |
| `ProgressiveStages.addCounter/setCounter(player, 'name', value)` | long | Mutate, evaluate triggers, and return the value |
| `ProgressiveStages.resetCounter(player, 'name')` | void | Clear a named counter |
| `ProgressiveStages.evaluate(player)` | void | Immediately evaluate declarative triggers |
| `ProgressiveStages.sync(player)` | void | Force a complete authoritative client-cache refresh |
| `ProgressiveStages.openGui(player)` | void | Open/refresh the player's stage map |
| `ProgressiveStages.activateRule(player, 'rule'[, seconds])` | boolean | Start a triggered conditional rule with configured or explicit duration |
| `ProgressiveStages.clearRule(player, 'rule')` | boolean | Stop one active triggered rule |
| `ProgressiveStages.clearRules(player)` | int | Stop all active triggered rules and return the count |
| `ProgressiveStages.activeRules(player)` | object | Rule ids mapped to whole seconds remaining |
| `ProgressiveStages.ruleIds()` | string[] | Every loaded canonical conditional rule id |
| `ProgressiveStages.ruleInfo('rule')` | object | Complete rule metadata, targets, exceptions, and context, or an empty object |

```javascript
ServerEvents.tick(e => e.server.players.forEach(p => {
    if (ProgressiveStages.percent(p, 'diamond_age') >= 100)
        ProgressiveStages.grant(p, 'diamond_age')
}))
```

> **Reset per reload.** All lifecycle callbacks and boolean/numeric condition
> registrations are **cleared at the start of each server-script reload**, so a
> `/reload` re-registers them cleanly instead of stacking duplicate handlers
> ([`ScriptHooks.reset()`](src/main/java/com/enviouse/progressivestages/common/compat/ScriptHooks.java)).
> The seam class `ScriptHooks` has **no KubeJS import**, so the engine can fire
> hooks / evaluate `script` conditions whether or not KubeJS is installed (the
> registries are simply empty when nothing registered them).

### 11.2 First-class `player.stages` API

ProgressiveStages also implements KubeJS's own `Stages` interface, so scripts can
read/add/remove PS stages through the **normal `player.stages` API**
([`KubeJSStagesCompat`](src/main/java/com/enviouse/progressivestages/compat/kubejs/KubeJSStagesCompat.java)):

```javascript
PlayerEvents.tick(event => {
    let player = event.player
    if (player.stages.has("stone_age")) player.stages.add("diamond_age")
    player.stages.remove("stone_age")
})
```

A script-initiated `player.stages.add/remove(...)` goes through KubeJS's own
wrappers (which fire KubeJS's internal sync); it **also** flows through the PS
engine, so it still fires `ProgressiveStages.onGranted` / `onRevoked` and the Java
`StageChangeEvent`. For reacting to grants, prefer `ProgressiveStages.onGranted`
over the (non-existent) native stage events.

**Java event bus, too.** Independent of KubeJS, every grant/revoke also fires the
Java [`StageChangeEvent`](src/main/java/com/enviouse/progressivestages/common/api/StageChangeEvent.java)
on the NeoForge event bus, carrying a `StageCause` (`COMMAND`, `TRIGGER`,
`PURCHASE`, `REGRESSION`, …) so other mods can react with full provenance — see
§15.4. (`ProgressiveStages.onGranted` / `onRevoked` are driven by this same event,
so they too see every cause.)

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
- **Jade / WTHIT — in-world lock overlay (New in 3.0).** When the player is
  looking at a **locked block OR a locked mob/entity**, the
  "what am I looking at" overlay appends a red **`🔒 Requires: <stage(s)>`** line
  naming the stage(s) they still need.
  - **Blocks** —
    [`StageLockBlockProvider`](src/main/java/com/enviouse/progressivestages/compat/jade/StageLockBlockProvider.java)
    reads the block's **item form** against the synced client item-lock cache
    (the same data sent for EMI/JEI hiding).
  - **Entities** —
    [`StageLockEntityProvider`](src/main/java/com/enviouse/progressivestages/compat/jade/StageLockEntityProvider.java)
    reads the **client entity-lock cache**. **Entity locks are now synced to the
    client** for this (added to `LockSyncPayload.entityLocks`).
  - Both honor **creative bypass** (the cache returns no gating stages while
    bypassing) and only show stages the player **doesn't already own**. WTHIT
    gets the same overlay via
    [`StageLockWthitProvider`](src/main/java/com/enviouse/progressivestages/compat/wthit/StageLockWthitProvider.java).
  - **Sourced from the Modrinth Maven, not bundled jars.** `build.gradle` adds a
    **Modrinth** repository (`https://api.modrinth.com/maven`, `maven.modrinth`
    group) and pulls Jade and WTHIT as
    `compileOnly` dev jars
    (`compileOnly "maven.modrinth:jade:15.10.5+neoforge"`,
    `compileOnly "maven.modrinth:wthit:neo-12.10.2"`). They are **compile-only**:
    if neither mod is installed at runtime the `@WailaPlugin` classes are never
    scanned, so the integration is simply inert.

The full compat registry is
[`ModCompatRegistry.initializeAll`](src/main/java/com/enviouse/progressivestages/compat/ModCompatRegistry.java),
called from `ServerEventHandler.onServerStarting`.

---

## 15. Public API (Java)

Package: `com.enviouse.progressivestages.common.api`

### 15.1 ProgressiveStagesAPI

Static facade in [`ProgressiveStagesAPI.java`](src/main/java/com/enviouse/progressivestages/common/api/ProgressiveStagesAPI.java).
Mutation methods must run on the logical server thread; queries should normally
be made there because they read live world/team state.

```java
// Existence checks
boolean exists = ProgressiveStagesAPI.stageExists(StageId.parse("iron_age"));
Set<StageId> all = ProgressiveStagesAPI.getAllStageIds();

// Per-player queries
boolean has = ProgressiveStagesAPI.hasStage(player, "diamond_age");
Set<StageId> stages = ProgressiveStagesAPI.getStages(player);
List<StageId> available = ProgressiveStagesAPI.getAvailableStages(player);
StageSlotResolver.Decision slot = ProgressiveStagesAPI.getSlotDecision(player,
    StageId.parse("diamond_engineer"));
int activeEngineers = ProgressiveStagesAPI.getOwnedSlotCount(player, "engineering_tiers");

// Mutations (fire StageChangeEvent / StagesBulkChangedEvent)
ProgressiveStagesAPI.grantStage(player, StageId.parse("iron_age"), StageCause.QUEST_REWARD);
ProgressiveStagesAPI.revokeStage(player, StageId.parse("stone_age"), StageCause.COMMAND);
ProgressiveStagesAPI.grantStages(player, ids, StageCause.API);
ProgressiveStagesAPI.grantStageBypass(player, id, StageCause.API);

// Definitions
Optional<StageDefinition> def = ProgressiveStagesAPI.getDefinition("iron_age");
Collection<StageDefinition> defs = ProgressiveStagesAPI.getAllDefinitions();
List<StageTriggerEvaluator.RuleProgress> progress = ProgressiveStagesAPI.getTriggerProgress(player, id);

// Conditional rule timers
boolean started = ProgressiveStagesAPI.activateConditionalRule(player,
    "progressivestages:end_fight/manual_permission", 60_000L);
boolean stopped = ProgressiveStagesAPI.clearConditionalRule(player,
    "progressivestages:end_fight/manual_permission");
Map<ResourceLocation, Long> remaining = ProgressiveStagesAPI.getActiveConditionalRules(player);
Set<ResourceLocation> conditionalIds = ProgressiveStagesAPI.getConditionalRuleIds();
Optional<ConditionalRule> conditionalDefinition =
    ProgressiveStagesAPI.getConditionalRule("progressivestages:end_fight/manual_permission");

// Generic exact-structure compatibility
ProgressiveStagesAPI.registerStructureContextProvider(
    ResourceLocation.parse("mypack:assignments"), provider);
List<StructureSessionView> sessions =
    ProgressiveStagesAPI.getActiveStructureSessions(player);
ProgressiveStagesAPI.markStructureSessionComplete(player, sessionId);
ProgressiveStagesAPI.reconcileStructureSessions(player);
ProgressiveStagesAPI.unregisterStructureContextProvider(
    ResourceLocation.parse("mypack:assignments"));

// One authoritative result for ordinary and contextual item-use locks
ItemUseDecision itemDecision = ProgressiveStagesAPI.evaluateItemUse(player, stack);
```

### 15.2 StageId

Wrapper around `ResourceLocation` with normalization rules per §2.1. Factory
methods:

- `StageId.parse("iron_age")` — normalizes (case, namespace, whitespace).
- `StageId.of("iron_age")` — alias for `parse`.
- `new StageId(ResourceLocation)` — strict, no normalization.

### 15.3 StageCause

The enum tracks the source of a stage change for events and logging:

`COMMAND`, **`TRIGGER`** (the per-stage `[[triggers]]` system — 2.1),
**`PURCHASE`** (skill-tree `[cost]` buy — 2.4), **`REGRESSION`** (a `[revoke]`
or temporary-stage `duration` loss — 2.4), `ADVANCEMENT`, `ITEM_PICKUP`,
`INVENTORY_CHECK`, `QUEST_REWARD`, `DIMENSION_ENTRY`, `BOSS_KILL`,
`MULTI_TRIGGER` (legacy), `TEAM_SYNC`, `AUTO`, `STARTING_STAGE`, `API`, `SCRIPT`,
`STRUCTURE_ENTER`, `STRUCTURE_LEAVE`, `STRUCTURE_COMPLETE`,
`UNKNOWN`.

(The legacy `*_TRIGGER`/`*_ENTRY` causes are retained for binary compatibility;
all 2.1 auto-grants from `[[triggers]]` report `StageCause.TRIGGER`. **New in
2.4:** purchases via the skill-tree `[cost]` GUI report `StageCause.PURCHASE`,
and every revoke from `[revoke]` / temporary-stage expiry reports
`StageCause.REGRESSION`.)

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

Structure integration events are `StructureSessionEnterEvent`,
`StructureSessionCompletionEvent`, `StructureSessionLeaveEvent`, and
`StructureAccessDeniedEvent`. They are immutable, post-commit, and noncancellable. See the
[structure session compatibility guide](STRUCTURE_SESSION_COMPATIBILITY.md#12-events).

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

### 17.5 A `[[triggers]]` rule isn't granting

Run `/progressivestages triggers list <player>` (or `/stage progress <stage>
<player>`). Each rule prints its `all_of` / `any_of` mode and ✓/✗ (or
`current/count`) per condition. If a condition isn't progressing as expected:

- **Remember rules are OR-ed, conditions are AND/OR-ed.** The stage only grants
  when one whole rule is satisfied. An `all_of` rule needs *every* condition;
  an `any_of` rule needs *one*.
- **Counters (`kill`, `mine`, `craft`, `pickup`, `use`, `drop`, `break_item`,
  `distance`, `stat`, `play_time`)** read vanilla statistics and are
  retroactive — they should reflect existing progress immediately. If a counter
  reads `0`, double-check the subject id/tag and that vanilla actually tracks
  that statistic. `distance` is in blocks; `play_time` in minutes.
- **State (`level`, `xp`, `has_item`)** is momentary — it must hold true at the
  moment a rule's other conditions are also met. Spending a level can un-satisfy
  a `level` condition.
- **One-shots (`dimension`, `biome`)** must be *visited at least once*. If a
  player visited before the trigger existed, the visit may not be recorded —
  re-enter the dimension / biome.
- **Polling.** If nothing seems to update, lower
  `enforcement.trigger_poll_interval` (default 20 ticks) or note that kills,
  advancements, dimension changes, and login force an immediate re-check anyway.
- **Dependencies gate the grant.** If a rule shows **fully satisfied** but the
  stage still isn't granted, the stage is **waiting on a `[stage].dependency`**.
  Triggers only fire once every prerequisite is owned (`/stage check <player>
  <dependency>`). Grant the missing prerequisite — the next poll completes the
  unlock — or omit the stage's `dependency` to let it fire freely.

To clear a stage's one-shot (`dimension` / `biome`) progress and start over:
`/progressivestages trigger reset <player> <stage>`.

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

- Reload every `*.toml` in `config/progressivestages/stages/` (stage files, including
  each stage's `[[triggers]]` rules and `[display]` overrides).
- Re-evaluate `[[triggers]]` against online players — retroactive counter
  conditions can grant a stage immediately on reload.
- Re-sync lock data + stage definitions to every online client.
- Trigger EMI/JEI refresh on every client.

It does NOT:

- Clear the persisted one-shot (`dimension` / `biome`) visited history.
- Reset player stage state.
- Re-fire starting-stage grants (unless `reapply_starting_stages_on_login` is
  on, in which case the next login of each player will catch up).

To reset a stage's persisted one-shot trigger progress:
`/progressivestages trigger reset <player> <stage>`.

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

## 18. File, Package, and Runtime Structure

This section is the compact map. The dedicated
[Architecture and Project Structure Guide](ARCHITECTURE.md) expands it into the
runtime config tree, datapack layout, stage-file anatomy, Minecraft structure
gating flow, startup and transactional reload sequence, ownership storage,
network payload boundaries, trigger indexing, optional integration rules, test
layout, and a table showing exactly where to implement each kind of change.

### 18.1 Runtime files

```text
config/
└── progressivestages/
    ├── progressivestages.toml
    └── stages/
        ├── mage/
        │   ├── stage.toml
        │   ├── rules.toml
        │   └── progression.toml
        ├── diamond_engineer/
        │   ├── stage.toml
        │   ├── rules.toml
        │   └── progression.toml
        └── 28 more showcase stage packages
```

`progressivestages.toml` contains global defaults. Every `.toml` beneath
`stages/`, including files in nested folders, is a stage candidate. The
`[stage].id` value is authoritative; filenames and folders are organizational.
Per-stage `[[triggers]]` live in these files, so a legacy `triggers.toml` is
ignored. Datapack defaults live at
`data/<namespace>/progressivestages/stages/*.toml`, and a config stage wins when
both sources use the same stage ID.

### 18.2 Runtime flow

1. `ConfigPaths` creates or safely migrates the config hierarchy.
2. `StageFileLoader` reads every config and datapack candidate.
3. `StageFileParser` produces immutable `StageDefinition` objects or detailed errors.
4. `StageOrder` validates dependencies and the complete graph.
5. A valid snapshot rebuilds lock, dependency, tag, trigger, and ability indexes.
6. Server events call focused enforcers, which consult those indexes and authoritative ownership.
7. Real grants and revokes converge on `StageManager`, which applies scope, events, rewards, attributes, regressions, team sync, quest rechecks, and client sync.
8. `NetworkHandler` sends presentation state to client caches and accepts only narrow GUI data or purchase requests back from the client.

A reload is transactional. The existing working snapshot stays active until the
entire candidate parses, validates, and applies. A client never becomes the
authority for stage ownership or purchases.

### 18.3 Source package map

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
  compat/
    ScriptHooks.java                 ← KubeJS-free seam: onGranted/onRevoked + script: condition registry (2.5)
  stage/
    StageManager.java                ← grant / revoke / sync core
    StageOrder.java                  ← dependency graph + lookups + deep validate (cycles/unreachable) (2.5)
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
    DropModifierApplier.java         ← selector-aware server-side block drop transforms (3.0.1)
    MobReplacementEnforcer.java      ← [[mobs.replacements]] swap
    MobSpawnEnforcer.java            ← [mobs].locked_spawns
    NearestPlayerCheck.java          ← shared nearest-player utility
    PetEnforcer.java                 ← taming + breeding + commanding + riding
    RecipeEnforcer.java              ← [recipes].locked_ids + locked_items
    RegionEnforcer.java              ← [[regions]] entry + flags
    ScreenEnforcer.java              ← [screens] block + item GUI
    StageLootModifier.java           ← Global Loot Modifier impl
    StructureEnforcer.java           ← [structures] entry + chest locking + entry_padding + last-safe-pos (2.5)
    VillagerProfessionEnforcer.java  ← [professions] trade-GUI gate by villager profession (2.5)
    AdvancementHider.java            ← [advancements] re-send driver (2.5; filter in ServerAdvancementHidingMixin)
  integration/
    FTBTeamsIntegration.java         ← FTB Teams membership wiring
  loader/
    DefaultShowcaseStages.java       ← fifty schema 4 first-launch showcase packages
    DefaultStageTemplates.java       ← legacy one-file documentation and compatibility references
    StageFileLoader.java             ← directory scan + reload; datapack/config merge + deep validate (2.5)
    StageFileParser.java             ← TOML → StageDefinition (parses [[triggers]] + [display])
    DatapackStageLoader.java         ← loads data/<ns>/progressivestages/stages/*.toml (2.5)
  triggers/
    StageTriggerEvaluator.java       ← per-stage [[triggers]] evaluator (poll + event re-checks, 2.1)
    TriggerPersistence.java          ← per-world dimension/biome "visited" one-shot record

common/trigger/                      ← per-stage [[triggers]] data model (2.1)
  TriggerRule.java                   ← one [[triggers]] block (OR-ed rule)
  TriggerCondition.java              ← one [[triggers.conditions]] entry
  TriggerConditionType.java          ← kill / mine / craft / … / dimension / biome / world_time / script enum
  TriggerMode.java                   ← all_of / any_of

client/
  ClientEventHandler.java            ← client-side event subscriptions
  ClientModBusEvents.java            ← mod bus client setup + "Open Progression Tree" keybind
  ClientStageCache.java              ← client-side stage state mirror
  ClientLockCache.java               ← client-side lock registry mirror
  ClientTriggerProgress.java         ← client mirror of live [[triggers]] progress (for GUI + tooltips)
  gui/StageTreeScreen.java           ← Stage Tree / Progression viewer (2.1)
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
  ServerAdvancementHidingMixin.java  ← strips [advancements]-gated entries from the update packet (2.5)
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
  kubejs/
    KubeJSStagesCompat.java          ← player.stages bridge + fires onGranted/onRevoked on engine changes (2.5)
    ProgressiveStagesKubeJSPlugin.java ← binds the global ProgressiveStages object (2.5)
    PSKubeBindings.java              ← ProgressiveStages.onGranted/onRevoked/condition/has/grant/… (2.5)
  lootr/
    LootrCompat.java
    LootrStageFilter.java
    LootrStageFilterProvider.java
  mekanism/MekanismCompat.java
  naturescompass/NaturesCompassCompat.java
  recipeviewer/RecipeViewerModHints.java
  visualworkbench/VisualWorkbenchShim.java
```

The first-launch showcase is generated by
[`DefaultShowcaseStages`](src/main/java/com/enviouse/progressivestages/server/loader/DefaultShowcaseStages.java).
It emits fifty schema 4 packages and one hundred fifty files only when no stages already exist. The complete
tree is documented in [SHOWCASE_PACK.md](SHOWCASE_PACK.md).

[`DefaultStageTemplates`](src/main/java/com/enviouse/progressivestages/server/loader/DefaultStageTemplates.java)
retains the older one-file references for documentation and compatibility tests. Its tracked
[`diamond_stage.toml`](examples/reference/diamond_stage.toml) copy remains the exhaustive commented
legacy schema reference, but it is no longer generated during first launch.

---

## 19. Phase 1 Through Phase 19 Guide

The standalone [Phase 1 Through Phase 19 Guide](PHASES_1_TO_19.md) reorganizes this exhaustive
reference into a practical sequence. It does not replace the schema reference above. It tells a
pack author what to build first, what file to edit, what to copy, what should happen in game, how to
verify it, and which mistake usually explains a failure.

The nineteen phases cover installation and generated folders, stage identity, dependency graphs,
the prefix language, every lock family, ore disguises, every trigger condition, counters and script
providers, the advancement-style map, costs and rewards, attributes and abilities, regression and
scope, commands, KubeJS and Java APIs, every optional integration, datapack stages, migration,
transactional reload, automated tests, multiplayer matrices, JAR inspection, and release evidence.

Start at [Phase 1](PHASES_1_TO_19.md#phase-1-install-the-mod-and-generate-the-correct-folders) even
if the mod is already installed. The early phases establish paths and ownership rules that every
later example assumes.

---

*End of document.*
