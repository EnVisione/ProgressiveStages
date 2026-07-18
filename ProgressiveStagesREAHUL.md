# ProgressiveStages Rehaul Plan

> The filename intentionally uses the requested `REAHUL` spelling. This document is the working
> implementation contract. No rehaul runtime code should be written until this plan is reviewed.

## 1. Purpose

ProgressiveStages must stop treating a stage as one ever-growing TOML document and become a
modular, data-driven progression engine. A pack author should be able to describe who a rule
affects, what the rule matches, when it applies, what it does, how it competes with other rules,
how it appears in the UI, and how it migrates between schema versions without asking for a new
hardcoded special case.

This rehaul has six mandatory foundations:

1. A compact stage-package layout that is easy for a beginner and scalable for a large modpack.
2. A translation layer that keeps every current 3.0.1 monolithic stage working.
3. Symmetric grant and revoke lifecycle rules that use the same complete condition language.
4. Stage, category, advanced-rule, and individual prefix-entry priority with deterministic lock
   and unlock arbitration.
5. Contextual item buffs, debuffs, attributes, effects, and ability modifiers.
6. Thirty numbered feature groups across the progression engine, localhost editor, authoring tools,
   synchronization, and player UI, all designed around author control.

The goal is not to create more unrelated switches. The goal is one coherent engine with reusable
prefix entries, conditions, actions, contexts, policies, and diagnostics.

### The compromise authoring decision

- A stage package uses three clearly named files: `stage.toml`, `rules.toml`, and
  `progression.toml`.
- `stage.toml` says what the stage is and how it looks.
- `rules.toml` says what the stage locks, unlocks, changes, buffs, or debuffs.
- `progression.toml` says how the stage is earned, lost, purchased, rewarded, or challenged.
- Existing category names and plain prefix lists remain the primary manual syntax.
- `/pstages editor` is the primary beginner experience and can create, edit, duplicate, validate,
  and delete every part of all three files without requiring TOML knowledge.
- The generic compiled rule model remains an engine implementation detail.

## 2. Non-negotiable product rules

### 2.1 Customizability

- No gameplay rule is special-cased to one item, stage, mod, structure, boss, dimension, class, or
  pack.
- Built-in behavior is exposed through namespaced registries. Integrations register providers
  through service interfaces instead of adding core dependencies.
- Defaults exist so a new author can begin quickly, but every gameplay default is overridable in
  the main config, at the stage level, at the category level, at the advanced-rule level, or at the
  individual entry level where relevant.
- Unknown extension fields survive parsing, migration, inspection, and future authoring tools.
- Every denial message, sound, icon, color, background, threshold, timer, priority, tie policy,
  aggregation policy, and failure action is configurable.
- KubeJS and the Java API receive the same public concepts as TOML. Scripts must not be a weaker
  second system.

Literal zero hardcoded values is not technically possible. Schema field names, protocol versions,
registry identifiers, file safety checks, and absolute anti-crash limits must exist in code. The
rule is therefore: no pack-specific or gameplay-policy decision is hardcoded. Any safety limit
that cannot be disabled must be documented, intentionally generous, and visible in validation.

### 2.2 Compatibility

- Existing 3.0.1 stage files continue to load without manual edits.
- The legacy global `triggers.toml` remains retired. Existing per-stage `[[triggers]]` translate
  into new grant lifecycle rules.
- Existing `[revoke]`, stage duration, attributes, costs, rewards, conditional rules, structure
  sessions, KubeJS calls, Java API calls, commands, and saved stage ownership remain valid.
- Migration never destroys or silently overwrites an original file.
- Config packages override datapack packages with the same stage ID, matching current behavior.
- A failed reload leaves the last known good compiled snapshot active.

### 2.3 Runtime correctness

- The server is authoritative for ownership, lifecycle transitions, attributes, effects, damage,
  access decisions, and challenge progress.
- A reload is parse, translate, validate, compile, then atomic swap. Partially loaded stages never
  become active.
- Conditions compile into an immutable tree and subscribe to relevant events. Arbitrary script
  expressions are not polled every tick.
- Every stage mutation runs through one transaction engine with cycle detection and rollback.
- Rule resolution is deterministic across operating systems, reloads, and server restarts.
- Team and server scope are explicit. Per-player counters do not accidentally become team-wide,
  and team-owned stages do not lose the identity of the player who caused an event.

### 2.4 User experience

- The normal package uses three predictable files, while the editor presents one unified stage
  workspace and handles file placement automatically.
- A one-file legacy stage remains a supported compatibility path.
- Validation errors name the stage, file, section, field, invalid value, and a suggested fix.
- The player UI follows vanilla advancement screen behavior and accessibility conventions.
- Operator diagnostics explain the actual winning rule rather than merely saying that content is
  locked.

## 3. Current 3.0.1 architecture audit

The rehaul must start from the system that exists, not an imagined clean rewrite.

| Current area | Current behavior | Limitation to remove |
|---|---|---|
| Config discovery | `StageFileLoader` recursively discovers nearly every `.toml` under `stages/` and parses each file as a complete stage. | Helper or fragment TOMLs are mistaken for stages. One stage cannot naturally own several files. |
| Datapacks | `DatapackStageLoader` loads every TOML below `data/<namespace>/progressivestages/stages/`. | It has the same one-file-one-stage assumption and no package manifest. |
| Parsed model | `StageDefinition` owns identity, presentation, all lock categories, triggers, attributes, regression, costs, rewards, abilities, conditional rules, and active locks. | The class is a monolith and unrelated features cannot be composed or extended cleanly. |
| Prefix selectors | `PrefixEntry` supports `id:`, `mod:`, `tag:`, `name:`, and `#tag`. | A selector cannot own priority, context, policy, message, or other metadata. |
| Normal locks | Static category locks are treated as priority zero. | There is no configured stage priority or selector priority. |
| Conditional rules | Temporary and triggered lock or unlock rules use a rule priority, default 100. Lock wins an equal-priority tie. | Priority exists only at the conditional-rule level, and the context grammar differs from stage triggers. |
| Grants | `StageTriggerEvaluator` owns per-stage `[[triggers]]` and a large condition type switch. | Grant rules are separate from conditional locks and revocation. Extension requires core edits. |
| Revocation | `StageRegressionHandler` supports death, maintained XP, and temporary-stage expiry. | Revokes do not have full trigger condition parity, nested logic, counters, or per-rule priority. |
| Attributes | `StageAttributeApplier` applies attributes while a stage is owned. | Attributes cannot depend on a selected item, inventory location, combat context, structure session, or another condition. |
| Exact structures | Structure providers and session leases are available in 3.0.1. | The source is valuable but is not yet a general context and challenge provider for all rule types. |
| Client synchronization | Ownership, definitions, locks, GUI progress, and ore deltas already travel from server to client through separate payloads. | Replace scattered caches with one revisioned compiled client snapshot and safe deltas. Raw stage files are unnecessary on clients. |
| Ore masquerading | The server rewrites outgoing chunk data per player and sends spoof updates. | Preserve server authority and add revision-safe refresh after editor apply. Do not move ore rules into client config. |
| Reload safety | Candidate definitions validate before activation and rollback to the previous snapshot on failure. | This strength must be retained while adding packages, translation, compilation, and extension registries. |
| UI | The stage tree already has advancement-style foundations. | It cannot yet inspect priority conflicts, lifecycle conditions, contextual modifiers, challenge budgets, or history. |

The rehaul should replace these separate runtime paths gradually. It must not remove working
enforcement handlers before their equivalent compiled rule path is tested.

## 4. Three-file stage packages

### 4.1 The normal layout

```text
config/
└── progressivestages/
    ├── progressivestages.toml
    └── stages/
        ├── iron_age/
        │   ├── stage.toml
        │   ├── rules.toml
        │   └── progression.toml
        └── end_fight/
            ├── stage.toml
            ├── rules.toml
            └── progression.toml
```

- `stage.toml` is required and marks the directory as one stage package.
- `rules.toml` is optional on disk until the stage has a rule. It contains locks, unlocks,
  enforcement, abilities, temporary rules, contextual modifiers, buffs, and debuffs.
- `progression.toml` is optional on disk until needed. It contains triggers or grants, revokes,
  duration, costs, rewards, transition effects, challenges, and stage-state transitions.
- The editor shows these as understandable screens rather than asking the user to choose files.
- The stage ID comes from `[stage].id`, never the folder name.
- Random TOMLs are not treated as stages.

The names answer three beginner questions: what is the stage, what does it control, and how does
progression change it. `progression.toml` replaces the more technical `lifecycle.toml` name.

### 4.2 What belongs in each file

| File | Author question | Main sections |
|---|---|---|
| `stage.toml` | What is this stage and how does it look? | `[stage]`, dependencies, `[display]`, metadata, category, tags, scope, stage priority. |
| `rules.toml` | What is locked, allowed, changed, buffed, or debuffed? | Items, blocks, fluids, recipes, crops, dimensions, enchants, entities, interactions, loot, mobs, pets, screens, trades, professions, advancements, structures, regions, Curios, ores, unlock carve-outs, enforcement, abilities, beacon, brewing, temporary and triggered rules, active locks, contextual modifiers. |
| `progression.toml` | How is this stage earned, removed, purchased, rewarded, or challenged? | Triggers or grants, revokes, duration, costs, rewards, unlock effects, challenges, sequences, variables, formulas, action chains, and stage states. |

Authors may still edit the files directly, but the web editor owns the routing. Moving a field
between files by hand produces a plain-language validation message naming its correct location.

### 4.3 Optional advanced splitting

Very large stages may declare additional rule or progression includes, but the editor manages them
and the normal generated package stays at three files. Includes use the same sections and cannot
declare a second stage identity. Paths are explicit, contained inside the package, and validated
before reload.

### 4.4 Datapack equivalent

```text
data/<namespace>/progressivestages/stages/<package_path>/stage.toml
data/<namespace>/progressivestages/stages/<package_path>/rules.toml
data/<namespace>/progressivestages/stages/<package_path>/progression.toml
```

The config and datapack loaders share one package compiler. A config package replaces a datapack
package with the same stage ID by default.

### 4.5 Existing one-file stages

Existing monolithic files remain valid:

```text
stages/diamond_stage.toml
stages/technology/ae2.toml
```

They translate in memory without changing behavior. The migration tool can split them into the
three-file layout with backups, semantic verification, and a report showing where each section
moved.

## 5. Manual authoring remains KISS

### 5.1 `stage.toml`

```toml
[schema]
version = 4

[stage]
id = "pack:iron_age"
name = "Iron Age"
description = "Mine iron and smelt an ingot."
icon = "minecraft:iron_ingot"
dependency = "pack:stone_age"
priority = 100

[display]
frame = "task"
reveal = "dependencies"
```

### 5.2 `rules.toml`

Rules keep the current category sections and plain prefix lists:

```toml
[items]
locked = ["name:iron", "mod:ae2"]
always_unlocked = ["id:minecraft:iron_nugget"]

[blocks]
locked = ["name:iron"]

[[temporary_locks]]
id = "pack:end_no_elytra"
abilities = ["elytra", "jump"]
items = ["id:minecraft:diamond_pickaxe"]
in_dimensions = ["minecraft:the_end"]
priority = 200
```

### 5.3 `progression.toml`

```toml
[[triggers]]
mode = "all_of"
description = "Mine iron and smelt one ingot."

[[triggers.conditions]]
type = "mine"
target = "id:minecraft:iron_ore"
count = 1

[[triggers.conditions]]
type = "has_item"
target = "id:minecraft:iron_ingot"
count = 1

[[revokes]]
mode = "any_of"
description = "The temporary trial is lost on death."

[[revokes.conditions]]
type = "death"
count = 1
```

`[[triggers]]` remains the simple grant spelling. `[[grants]]` is an alias. `[revoke]` remains for
simple legacy regression, while `[[revokes]]` supports every grant condition in reverse.

### 5.4 Priority stays optional

Most users set `[stage].priority` and never touch priority again. Category and individual entry
priority remain optional:

```toml
[items]
priority = 150
locked = ["mod:ae2", "id:ae2:wireless_terminal"]

[items.priorities]
"id:ae2:wireless_terminal" = 500
```

The compact `"mod:ae2,priority:150"` spelling remains supported. The editor presents priority as
an Advanced field and visualizes conflicts before apply.

### 5.5 Item buffs and debuffs stay direct

```toml
[[item_modifiers]]
id = "pack:mage_using_sword"
items = ["tag:pack:knight_weapons"]
while_holding = true
with_stages = ["pack:mage"]
priority = 300

[[item_modifiers.attributes]]
id = "minecraft:generic.attack_damage"
amount = -0.59
operation = "multiply_total"
```

### 5.6 Advanced rules remain an escape hatch

The compiler uses a generic rule model internally. Public `[[rules]]` remains available for
unusually complex extensions, but the editor writes category sections whenever possible. A normal
author never has to understand selector objects, codecs, ASTs, or action registries.

### 5.7 KISS acceptance rules

- A stage is created from one editor wizard and saved into three predictable files.
- A manual first stage requires only `[stage]` and one category `locked` list.
- Common grants, revokes, modifiers, and challenges use short fields before advanced condition
  trees or action arrays.
- The editor hides advanced fields until requested and supplies searchable registry pickers.
- Every field has an example, default, validation, and plain-language help.
- The complete Diamond reference remains separate from the tiny starter template.
- A teenager unfamiliar with TOML must be able to create, test, apply, and delete a stage through
  `/pstages editor` during manual usability testing without developer assistance.

## 6. Canonical compiled model

The parser should not construct a larger `StageDefinition`. It should compile source files into a
small set of immutable public concepts.

### 6.1 Source model

- `StagePackageSource` records config or datapack origin, package root, schema version, explicitly
  included files, load order, namespace, and override policy.
- `StageManifestSource` records raw stage identity, dependencies, presentation, defaults, and
  preserved extension fields.
- `RuleSource` records rule-level selectors, contexts, conditions, actions, policies, and source
  locations.
- `LifecycleSource` records grant or revoke direction and the shared lifecycle fields.
- `LegacyStageSource` captures a parsed 3.0.1 file without losing which old section produced a
  value.

### 6.2 Normalized intermediate representation

- `CompiledStage` replaces the monolithic runtime definition.
- `SelectorSpec` replaces plain `PrefixEntry` at the new boundary. It contains matcher ID,
  matcher configuration, explicit priority, context overrides, policy overrides, labels, and
  provenance.
- `CompiledRule` represents lock, unlock, allow, deny, transform, modifier, visibility, or custom
  registered effects.
- `ConditionNode` represents `all`, `any`, `not`, `at_least`, sequence, comparison, or a registered
  leaf predicate.
- `CompiledLifecycleRule` represents grant or revoke direction using the same condition root.
- `ActionChain` represents success, failure, enter, leave, grant, revoke, expiry, and rollback
  actions.
- `DecisionTrace` records every matching candidate, its effective priority, why it matched or
  failed, and the selected winner.
- `ConfigProvenance` records package, included file, section, field, schema version, translated
  legacy field, and extension owner.

### 6.3 Compilation pipeline

```text
discover package markers
        ↓
parse files without runtime side effects
        ↓
translate legacy sources into schema 4 sources
        ↓
expand templates, variables, and includes
        ↓
normalize selectors, IDs, defaults, and priorities
        ↓
validate references, registries, scopes, cycles, and providers
        ↓
compile condition trees and event subscription indexes
        ↓
build immutable candidate snapshot
        ↓
atomically replace the active snapshot
```

Every error before the final step rejects the entire candidate and preserves the current snapshot.

## 7. Prefix entries and priority

### 7.1 Supported prefix forms

All current prefix entries remain valid:

- `id:minecraft:diamond`
- `minecraft:diamond`
- `mod:ae2`
- `tag:c:gems/diamond`
- `#c:gems/diamond`
- `name:diamond`

Internally, a matcher registry allows extension mods to add more prefix types without editing a
central enum. Beginner documentation calls these list entries or prefixes, not selector objects.
Advanced built-ins may later include component, data-component value, recipe type, rarity,
creative tab, equipment slot, entity tag, block state, fluid property, enchantment, structure tag,
dimension tag, and a KubeJS predicate.

### 7.2 Simple entry metadata

The normal author continues writing plain prefix entries inside familiar category lists. Optional
per-entry settings live beside the list in a lookup table:

```toml
[items]
locked = ["mod:ae2", "tag:c:tools", "id:minecraft:bow"]

[items.priorities]
"tag:c:tools" = 125
"id:minecraft:bow" = 600
```

The same key may appear in an optional category table for a message, policy, or context override
when needed, but the list itself stays readable. Advanced generic rules may use structured
selector objects internally or explicitly. Omitted entry settings always inherit.

### 7.3 Priority cascade

Effective priority is resolved in this order:

1. Individual prefix-entry priority from a category lookup table or compact suffix.
2. Explicit advanced-rule priority when the optional generic rule language is used.
3. Category priority such as `[items].priority`.
4. Stage priority from `[stage].priority`.
5. Global default priority from `progressivestages.toml`.

The priority range is configurable within a documented absolute safety range. Higher priority
wins. Negative priority remains valid for fallback rules.

### 7.4 Equal-priority arbitration

The default tie behavior remains safe and compatible:

1. Deny or lock beats allow or unlock.
2. An exact ID beats a tag, a tag beats a name match, and a name match beats a mod-wide match.
3. Explicit prefix-entry settings beat inherited settings.
4. Canonical rule ID provides the final stable ordering.

Every layer is configurable except the final deterministic ordering. Authors may choose
`lock_wins`, `unlock_wins`, `most_specific`, `first_declared`, or `error_on_tie`. Specificity
weights are configurable. A validation mode can reject accidental equal-priority conflicts before
the server applies them.

### 7.5 Decision example

```toml
[stage]
id = "pack:technology"
priority = 100

[items]
locked = ["mod:ae2"]

[unlocks]
items = ["id:ae2:wireless_terminal"]
priority = 200

[unlocks.priorities]
"id:ae2:wireless_terminal" = 500
```

The mod-wide lock inherits 100. The unlock section normally uses 200. The terminal entry uses 500,
so it wins for that exact item without changing any other AE2 item.

## 8. One condition language for everything

### 8.1 Boolean composition

The condition compiler supports:

- `all` or `all_of`.
- `any` or `any_of`.
- `not`.
- `at_least` with a required child count.
- `exactly` with an exact child count.
- `sequence` with ordered steps and optional time limits.
- Numeric and string comparisons.
- Inclusive and exclusive ranges.
- Named reusable condition references.
- Optional provider conditions that warn instead of failing a whole pack when an integration is
  absent.

The same tree is usable by grants, revokes, locks, unlocks, visibility, costs, rewards, modifiers,
challenge success, challenge failure, and action chains.

### 8.2 Condition value model

Every registered condition declares:

- A namespaced type ID.
- Its configuration codec and validation schema.
- Its value type: boolean, integer, decimal, string, ID, set, or duration.
- Events and state keys that can change its result.
- Its persistence needs.
- Supported subject scopes.
- A human-readable progress and failure explanation.
- Whether it is retroactive, event-edge, live-state, rolling-window, or session-based.

### 8.3 Existing conditions retained

The translator and built-in condition providers retain kill, mine, craft, pickup, use, drop,
break item, distance, stat, play time, level, XP, advancement, dimension, biome, item possession,
effect, breed, day, world time, weather, structure entry, tame, kill with item, script, altitude,
fish, sleep, ride, biome time, stage held duration, custom counter, scoreboard, current health,
food, stage count, online team size, script value, and exact structure leave outcome.

### 8.4 New health, damage, and death conditions

Required new condition providers:

| Condition | Meaning | Important settings |
|---|---|---|
| `death` | The subject died. | count, cause selector, killer selector, dimension, session, rolling window, reset policy. |
| `respawn` | The subject completed respawn. | count, dimension, after-death cause, window. |
| `health_gained` | Health restored during the selected window. | amount, healing source, natural or external, overheal policy, source entity, session. |
| `health_lost` | Health removed during the selected window. | amount, damage type, attacker, direct entity, blocked damage policy, absorption policy, session. |
| `damage_taken` | Raw or final incoming damage. | raw or final value, damage type or tag, attacker selector, item held by attacker, blocked, critical, session. |
| `damage_dealt` | Raw or final outgoing damage. | victim selector, weapon selector, damage type, critical, projectile, session. |
| `hits_taken` | Count of qualifying incoming hit events. | maximum or minimum, invulnerability-frame policy, source filters, session. |
| `hits_dealt` | Count of qualifying outgoing hit events. | victim, weapon, successful-damage requirement, session. |
| `health_threshold_crossed` | Health crossed upward or downward through a configured threshold. | threshold, direction, percent or points, debounce. |
| `no_damage_for` | No qualifying damage for a duration. | duration, source filters, reset events, session. |

`current_health` remains a live state check. `health_lost` is an accumulated event value. They must
not be aliases because they answer different design questions.

### 8.5 Event windows and reset policies

Counter conditions accept a window:

- `lifetime`.
- `since_stage_grant`.
- `since_stage_revoke`.
- `current_life`.
- `current_login`.
- `current_structure_session`.
- `current_combat_session`.
- A rolling duration such as `10s`, `5m`, or `1h`.
- A named custom session provided by another mod.

Reset policies include never, on grant, on revoke, on death, on respawn, on login, on dimension
change, on session entry, on session exit, on success, on failure, and explicit API reset. The
author chooses them per rule.

### 8.6 Grant and revoke symmetry

- A grant evaluates while its target stage is absent unless `evaluate_while_owned` is enabled.
- A revoke evaluates while its target stage is owned unless `evaluate_while_missing` is enabled.
- Both may be once, repeatable, edge-triggered, level-triggered, scheduled, or manually armed.
- Both may have priority, cooldown, debounce, grace period, and action chains.
- Both may store progress per player, team, or server independently from stage ownership scope.
- Both emit the same preflight, committed, rejected, rolled-back, and progress events.
- Both expose progress and explanation through commands, Java, KubeJS, and the UI.

### 8.7 Thrash and cycle prevention

Opposing grants and revokes can otherwise fight forever. The lifecycle transaction engine must:

1. Collect all candidate transitions caused by one source event.
2. Resolve priority before making a mutation.
3. Apply cooldown, grace, and hysteresis.
4. Build one dependency-aware transaction.
5. Detect repeated stage and rule pairs in the same transaction.
6. Reject cycles with a complete trace.
7. Enforce a configurable transition budget per event with a non-configurable emergency ceiling.
8. Commit all ownership and progress changes atomically.

## 9. Contextual buffs, debuffs, attributes, and item behavior

### 9.1 A direct item modifier section

Item attributes should not require authors to learn a generic rule language. The direct
`[[item_modifiers]]` section uses ordinary item lists and plain location fields while compiling to
the same internal condition and priority engine.

```toml
[[item_modifiers]]
id = "pack:iron_sword_training"
items = ["id:minecraft:iron_sword"]
while_holding = true
with_stages = ["pack:knight"]
aggregation = "once"
priority = 250

[[item_modifiers.attributes]]
id = "minecraft:generic.max_health"
amount = 10.0
operation = "add_value"

[[item_modifiers.attributes]]
id = "minecraft:generic.attack_speed"
amount = 0.15
operation = "multiply_total"
```

### 9.2 Supported inventory contexts

Simple author-facing location fields include `while_holding`, `while_in_main_hand`,
`while_in_off_hand`, `while_in_hotbar`, `while_selected`, `while_in_inventory`, `while_wearing`,
`while_in_curios`, `while_using`, and `while_attacking`. Advanced registered context IDs include:

- `item.main_hand`.
- `item.off_hand`.
- `item.either_hand`.
- `item.hotbar`.
- `item.selected_hotbar`.
- `item.inventory`.
- `item.armor.<slot>`.
- `item.equipment.<slot>`.
- `item.curios.<slot>` when Curios is installed.
- `item.container` for a currently open container when explicitly enabled.
- `item.use`, `item.attack`, `item.break`, `item.place`, `item.pickup`, and `item.drop`.

Extension mods can register more context sources. Most authors never need their IDs. Advanced
authors may combine contexts using all, any, not, and minimum-count logic.

### 9.3 Aggregation and stacking

Inventory modifiers must define how multiple matching stacks behave:

- `once` applies one modifier if any stack matches.
- `per_stack` applies once per matching stack up to a configured cap.
- `per_item` multiplies by item count up to a configured cap.
- `highest` applies the strongest matching prefix entry.
- `lowest` applies the weakest matching prefix entry.
- `sum` adds every compatible result.
- `exclusive` selects the priority winner only.

Attribute stacking policy may be replace, add, multiply, highest, lowest, deny-on-conflict, or a
registered custom policy. Stable modifier IDs derive from modifier, prefix entry, attribute,
subject, and
aggregation bucket. Reconciliation happens only when relevant inventory, equipment, stage,
context, or rule state becomes dirty.

### 9.4 Mage and knight affinity example

Both roles may use the other role's weapons, but the foreign weapon is weaker rather than denied:

```toml
[[item_modifiers]]
id = "pack:mage_using_knight_weapon"
items = ["tag:pack:knight_weapons"]
while_holding = true
while_attacking = true
with_stages = ["pack:mage"]
without_stages = ["pack:knight_training"]
priority = 300

[[item_modifiers.attributes]]
id = "minecraft:generic.attack_damage"
amount = -0.59
operation = "multiply_total"

[[item_modifiers.attributes]]
id = "minecraft:generic.attack_speed"
amount = -0.25
operation = "multiply_total"
```

The same mechanism can reduce spell power for knights when a magic mod exposes an attribute. If a
mod does not expose an attribute, an integration can register a damage, mana-cost, cooldown, or
spell-power transform without core hardcoding.

### 9.5 Status effects and action modifiers

Modifier rules may also apply:

- Potion effects with amplifier, duration, particles, icon, and refresh policy.
- Attribute modifiers.
- Incoming and outgoing damage transforms.
- Healing transforms.
- Attack speed, mining speed, movement, reach, gravity, step height, scale, and knockback.
- Item durability consumption.
- Vanilla cooldowns and registered mod cooldowns.
- Hunger, exhaustion, air, fall distance, and jump policies.
- Registered ability values such as mana cost, spell power, dodge cooldown, or stamina.
- Visual-only overlays and tooltips that mirror server-authoritative effects.

An unavailable optional attribute or transform follows a configurable error policy: fail reload,
warn and disable the modifier, or ignore it.

## 10. Required boss hit-limit design

The conversation's boss idea is implemented as a reusable challenge budget, not a boss-specific
hardcoded check. The common boss trial has a short form:

```toml
[[challenges]]
id = "pack:wither_no_hit_trial"
boss = "id:minecraft:wither"
maximum_hits_taken = 3
count_zero_damage_hits = false
count_blocked_hits = false
scope = "player"
grant_on_success = "pack:wither_mastery"
revoke_on_failure = "pack:wither_trial"
failure_message = "Trial failed. You were hit {current} times."
```

Advanced authors may add several `[[challenges.budgets]]`, custom start and end conditions, and
full action lists. The short fields compile into those advanced objects internally.

Configurable challenge decisions include:

- What begins and ends the session.
- Whether multiple bosses share or split a session.
- Player, party, team, or server budget ownership.
- Whether blocked, absorbed, zero-damage, environmental, minion, projectile, or friendly-fire hits
  count.
- Maximum hits, total damage, deaths, healing, consumables, time, distance, item uses, or custom
  provider values.
- Disconnect, death, dimension-change, boss-despawn, chunk-unload, and server-restart policy.
- Success and failure actions.
- Retry cooldown and attempt limits.
- HUD visibility and spoiler policy.

Dodge-roll support is indirect and compatible: if a dodge prevents the qualifying damage event,
it does not consume a hit unless the author chooses an attempted-hit provider registered by that
mod.

## 11. Thirty new feature groups

The package schema, legacy translation, symmetric grant and revoke engine, prefix-entry priority,
health and death conditions, contextual item modifiers, and hit-limit requirement are mandatory
foundations. The rehaul adds exactly thirty feature groups: ten progression-engine features,
fifteen editor and authoring features, and five player-facing UI features.

### Feature 1. Ordered and timed condition sequences

Authors can require ordered actions such as enter a structure, activate three blocks in order,
kill a guardian, then leave successfully within five minutes. Steps may allow, forbid, or reset on
intervening events. Progress persists according to author-selected scope and reset policy.

### Feature 2. Reusable challenge and budget framework

The hit-limit implementation becomes a generic budget engine for hits, damage, healing, deaths,
consumables, item uses, blocks broken, time, movement, summons, commands, or custom numeric
providers. Budgets support minimums, maximums, ranges, shared pools, regeneration, checkpoints,
and multiple failure tiers.

### Feature 3. Affinity, proficiency, and mastery profiles

Authors define role or class affinity for item or content groups. A profile may deny, weaken,
strengthen, increase cost, change cooldown, or replace behavior at proficiency levels. Proficiency
can be a stage, variable, scoreboard, custom counter, currency, or formula. Mage and knight cross-weapon
penalties are one configuration, not a built-in class system.

### Feature 4. Templates, bundles, parameters, and inheritance

Shared rule bundles eliminate copy and paste. A template accepts typed parameters such as stage
ID, item tag, priority, damage multiplier, message, and icon. Packages can include and override
bundles with explicit merge policies. Cycles, missing parameters, type mismatches, and conflicting
IDs fail validation with a complete include trace.

### Feature 5. Multi-state stages

Stages may optionally use states beyond owned or missing: unavailable, available, active,
suspended, completed, failed, expired, and a registered custom state. Authors define allowed
transitions and which state counts as ownership for compatibility. This enables a trial to be
available, active during a boss, failed after too many hits, and completed after success without
creating several duplicate stages.

### Feature 6. Declarative action pipelines

Every transition and challenge may run validated action chains: grant, revoke, suspend, set state,
give or take items, apply effects, change variables, execute commands, teleport, play sounds,
spawn particles, send messages, set cooldowns, start challenges, call registered services, or run
KubeJS callbacks. Actions define failure handling as rollback, continue, retry, or compensate.

### Feature 7. Variables, currencies, counters, and formulas

Packages may declare typed player, team, or server values. Values have defaults, bounds,
persistence, sync visibility, mutation permissions, and reset policies. A safe compiled formula
language can calculate thresholds and modifier amounts from declared values. Formula dependencies
join the dirty-event index and are not evaluated every tick.

### Feature 8. Transition stability controls

Every live condition can use cooldown, debounce, grace period, hysteresis, minimum-active duration,
minimum-inactive duration, schedule, and offline-time policy. This prevents flicker near health,
position, population, or inventory thresholds and lets authors build daily or seasonal rules.

### Feature 9. Public registries and service interfaces

Conditions, selectors, contexts, policies, actions, modifiers, aggregators, structure providers,
challenge measures, formula functions, UI detail providers, and migration adapters are registered
by namespaced ID. Java services and KubeJS registration expose codecs, validation, event interests,
explanation text, and capability discovery. Missing optional mods never stop core startup unless a
package explicitly marks that provider as required.

### Feature 10. Explain, simulate, history, and rollback tooling

The engine records bounded structured decision history. Operators can ask why an item is locked,
why a stage granted or revoked, what a reload changes, which priority won, and what would happen to
a selected player without making changes. Stage transactions may be rolled back by transaction ID
when all actions declare compensation support.

### Feature 11. Private localhost editor bridge

`/pstages editor` creates an operator-bound session. The connected operator's mod client starts a
temporary HTTP server on loopback only and serves the editor assets from the mod JAR. The browser
talks to that local client bridge, and the bridge carries authenticated editor requests through the
existing Minecraft connection to the dedicated server. No public website, cloud payload service,
public listening port, domain, TLS certificate, or external internet connection is required.

### Feature 12. Complete stage CRUD workspace

Create, rename, duplicate, move, archive, restore, and delete stages. The editor manages
`stage.toml`, `rules.toml`, and `progression.toml` automatically, previews affected saved stage IDs,
and prevents destructive rename or deletion until dependency and ownership consequences are
reviewed.

### Feature 13. Complete feature coverage forms

Every current and new configuration surface has a first-class form. No supported field is relegated
to raw TOML only. Extension fields receive generated forms from registered schemas, with raw source
editing retained as an expert fallback.

### Feature 14. Registry explorer and smart pickers

Search items, blocks, fluids, entities, recipes, dimensions, structures, biomes, enchantments,
effects, attributes, sounds, particles, advancements, professions, abilities, tags, mods, and
extension registries. Pickers show icons, IDs, namespaces, tags, installed-provider status, usage
counts, and live server results rather than a hardcoded client catalog.

### Feature 15. Visual dependency and stage graph editor

Create and remove dependency edges, choose all, any, or at-least modes, drag stage positions,
organize categories, preview reveal policies, detect cycles, and inspect the result using the same
layout data as the in-game advancement-style stage screen.

### Feature 16. Visual grant and revoke condition builder

Build grants and revokes with identical condition palettes. Nested all, any, not, at-least,
sequence, comparisons, rolling windows, reset policies, death, healing, damage, structure sessions,
scripts, and extension conditions are edited as readable cards. The editor always shows a sentence
preview and example progress.

### Feature 17. Complete lock and enforcement matrix

Edit every content category from one searchable matrix. Each row exposes locked entries, carve-outs,
contexts, actions, enforcement toggles, messages, sounds, priority, and conditional activation.
Bulk selection, multi-edit, copy, paste, tag expansion preview, and mod-wide operations reduce
repetition.

### Feature 18. Buff, debuff, affinity, and modifier designer

Build contextual item attributes, status effects, damage transforms, healing transforms, cooldowns,
durability costs, ability values, aggregation, stacking, class affinities, and proficiency scaling.
The editor previews the effective before-and-after values for a selected player and item.

### Feature 19. Challenge and budget designer

Create boss trials, hit limits, damage limits, healing limits, timers, death limits, item-use limits,
ordered objectives, session start and end conditions, retry rules, success actions, failure actions,
and HUD settings. Common boss trials use a short wizard; the complete budget engine is available in
Advanced mode.

### Feature 20. Priority and conflict analyzer

Show the complete priority cascade and every competing lock, unlock, allow, modifier, and policy.
Conflicts are detected before apply. Authors can inspect hypothetical targets, use automatic safe
priority suggestions, or deliberately select a documented tie policy.

### Feature 21. Live validation, explain, and simulation

Validation runs as fields change. The editor can simulate a stage grant, revoke, item use,
structure entry, boss hit, death, heal, inventory state, or arbitrary registered event against a
selected player without changing live data. Errors link directly to the responsible control.

### Feature 22. Drafts, undo, redo, and crash recovery

Every edit is a server-side draft with an immutable base revision. The editor supports undo, redo,
autosave, named drafts, discard, resume after browser close, and configurable recovery after player
disconnect or server restart. Drafts never affect live gameplay.

### Feature 23. Review, diff, atomic apply, backup, and rollback

Before apply, the editor shows semantic and TOML diffs, validation results, affected players,
client payload changes, and warnings. Apply writes temporary files, recompiles the entire candidate,
creates checksummed backups, atomically swaps files and runtime state, records an audit entry, and
can roll back to a known-good revision.

### Feature 24. Multi-operator collaboration and permissions

Multiple authorized operators may view a draft. Configurable exclusive locking or collaborative
mode controls editing. Presence, field ownership, comments, revision conflicts, approval rules,
session revocation, and a complete actor audit trail prevent silent overwrites. Every request is
re-authorized by the dedicated server.

### Feature 25. Templates, migration, import, export, and source mode

Create from beginner, age, class, technology, structure, temporary-stage, boss-trial, and custom
templates. Import legacy files, export a whole package, copy between servers, generate migration
reports, edit raw TOML with schema completion, and round-trip unknown extension data without loss.

### Feature 26. Stage rule inspector

The stage details panel gains tabs for overview, requirements, grants, revokes, locks, modifiers,
rewards, and dependencies. Each entry has an author-controlled icon, title, description, progress,
visibility, and tooltip. Players see only permitted information; operators may reveal canonical
IDs and source files.

### Feature 27. Why panel and priority stack

When content is blocked or modified, a vanilla-style expandable panel shows the winning decision
and, when allowed, the other candidates it defeated. It displays effective priority, inherited
priority source, prefix specificity, current conditions, remaining duration, and the applicable
allow or deny policy.

### Feature 28. Challenge HUD

A configurable HUD presents active challenge name, boss or session, hit budget, damage budget,
timer, step sequence, success criteria, and failure warning. Authors choose placement, scale,
colors, icons, animation, compact mode, automatic hiding, and which values are secret.

### Feature 29. Equipment and affinity preview

Hovering an item or opening a stage detail can show how that item behaves for the current player:
normal, locked, weakened, strengthened, increased cost, changed cooldown, or conditionally allowed.
The panel previews exact attribute deltas and names the stage or proficiency needed to improve it.

### Feature 30. Progress history timeline

A scrollable vanilla-styled timeline shows stage grants, revokes, suspensions, challenge attempts,
successes, failures, expirations, purchases, respecs, and important rule transitions. Visibility,
retention, timestamps, grouping, and operator detail are server-configured.

## 12. Dedicated server localhost editor architecture

### 12.1 Final authority decision

Stage TOML files live only on the dedicated server. There is no optional client publication mode
and no checkbox that can leave clients stale. Client synchronization is an automatic part of every
successful apply and every login.

This is feasible with the current mod direction:

- Stage ownership already synchronizes from server to client.
- Stage presentation and dependency definitions already synchronize at login.
- Client lock caches already receive server-built lock mappings.
- Ore masquerading is already decided from server-owned rules. Chunk data is rewritten for the
  target player and spoof deltas are sent to the client.
- Server enforcement remains authoritative even if a malicious client ignores presentation data.

The rehaul replaces several separate payloads with a versioned compiled client snapshot, but it
does not send raw TOML, server commands, secret conditions, or unrestricted reward actions.

### 12.2 Why the web server runs on the operator client

A web server bound to 127.0.0.1 on a remote dedicated server cannot be reached by an operator's
browser because localhost in that browser means the operator's computer. Requiring SSH tunnels
would defeat the easy-editor goal.

The solution is a local bridge in the connected operator's mod client:

    Browser
      ↕ HTTP on 127.0.0.1 with a one-time token
    Operator Minecraft client
      ↕ authenticated ProgressiveStages editor packets
    Dedicated Minecraft server
      ↕ draft service, validation, compiler, atomic files, runtime snapshot
    config/progressivestages/stages/

The browser and temporary HTTP listener exist on the operator's computer. All authoritative data,
draft revisions, validation, file writes, reloads, and permissions remain on the dedicated server.
No dedicated-server port is opened.

### 12.3 The editor command workflow

1. A connected player runs /pstages editor.
2. The dedicated server verifies operator level or the configured editor permission.
3. The server verifies that the client supports the exact editor protocol.
4. The server creates a random session ID, a 256-bit secret, an expiry, a base configuration
   revision, and a draft owned by that player.
5. The server sends an editor-open payload through the player's authenticated Minecraft connection.
6. The client starts a loopback-only HTTP listener on a random operating-system-assigned port.
7. Static HTML, CSS, JavaScript, icons, and schemas are served from the ProgressiveStages JAR.
8. The client asks the operating system to open the browser. If that fails, Minecraft shows a
   clickable and copyable localhost link.
9. The browser proves possession of the one-time secret to the local bridge.
10. Editor requests travel through the Minecraft connection to the server draft service.
11. Closing the editor, logging out, losing permission, changing server, timing out, or manually
    revoking the session disables the bridge and invalidates its secret.

The command is available only while connected to a dedicated server. Integrated-server support may
exist behind a development setting, but it is not part of the supported operator workflow.

### 12.4 Browser and loopback security

- Bind only to IPv4 127.0.0.1 and IPv6 ::1. Never bind 0.0.0.0.
- Ask the operating system for a random free port rather than using one predictable port.
- Keep the secret in the URL fragment, not the query string, server log, chat log, or HTTP referrer.
- Require an authorization header for every editor API request.
- Restrict Origin and Host to the exact active loopback origin.
- Send a strict content security policy, deny framing, disable MIME sniffing, and use same-origin
  resources only.
- Package every asset in the mod JAR. No CDN, analytics, remote fonts, remote scripts, or telemetry.
- Rate-limit requests, cap message and upload sizes, validate JSON schemas, and reject unknown
  packet types.
- Recheck server permission, player UUID, connection identity, session secret, revision, and expiry
  on every server operation.
- A browser can edit only its server-side draft. It can never name an arbitrary filesystem path.
- The local listener stops when the session ends even if a browser tab remains open.
- Session secrets are memory-only and are never stored in stage files or player data.

### 12.5 Draft and concurrency model

The server owns each draft. A draft contains its base revision, author, collaborators, semantic
operations, generated source view, validation results, autosave time, and expiry.

- Read operations may run asynchronously on immutable snapshots.
- Mutations are serialized per draft and return a new draft revision.
- Apply uses optimistic concurrency against the current live revision.
- If another operator applied first, the stale draft is never silently rebased. The editor shows a
  three-way semantic conflict view.
- Exclusive mode permits one editor. Collaborative mode permits several editors with presence and
  operation ordering.
- Disconnect behavior is configurable as discard, retain until expiry, or persist as a named draft.
- A server restart can restore persisted drafts without applying them.
- Undo and redo operate on semantic editor operations, not brittle text replacement.

### 12.6 Validation and apply transaction

Pressing Save stores a draft. Pressing Apply performs the authoritative transaction:

1. Verify session permission and live base revision.
2. Materialize stage.toml, rules.toml, and progression.toml in a temporary package tree.
3. Parse, translate, expand, validate, and compile the complete candidate configuration.
4. Run reference, dependency, cycle, priority, provider, registry, security, payload-size, and
   performance-budget checks.
5. Produce a semantic diff, affected-stage list, affected-player estimate, and client-sync preview.
6. Require confirmation for warnings or destructive effects according to server policy.
7. Create a checksummed backup and sync temporary files to disk where supported.
8. Atomically replace the changed packages.
9. Atomically swap the compiled server runtime snapshot.
10. Reconcile ownership, modifiers, challenge sessions, integrations, and ore-spoof state.
11. Broadcast the new client snapshot revision.
12. Record actor, timestamp, before and after revision, changed stages, warnings, and result.
13. If any pre-commit step fails, change nothing. If post-swap reconciliation fails, restore the
    previous files and runtime snapshot, then resynchronize clients.

### 12.7 Automatic current and future client synchronization

Every connected compatible client receives a permission-filtered compiled snapshot after a
successful apply. Every future client receives the current full snapshot during login. There is no
manual publication choice.

The client snapshot contains only data required on that client:

- Stage IDs, display names, descriptions, icons, categories, dependency graph, layout, reveal
  policies, backgrounds, frames, and permitted tooltips.
- Effective item, block, fluid, recipe, entity, advancement, profession, and viewer presentation
  rules.
- Client-visible temporary decisions and modifier summaries needed for UI feedback.
- Challenge HUD state and progress.
- Ore-spoof position deltas and display states where needed. The actual ore decision and chunk
  rewriting remain server-side.
- A protocol version, schema capability set, configuration revision, content checksum, and chunk
  sequence.

Snapshots are compressed, chunked, bounded, and acknowledged. Deltas are used only when the client
acknowledges the expected base revision. Otherwise the server sends a full snapshot. A client never
partially activates a revision. It assembles, verifies, then atomically replaces its caches.

### 12.8 Client synchronization edge cases

- A player joining during apply continues using the old complete revision or waits for the new
  complete revision. They never receive half of each.
- A client that misses a delta requests a full snapshot.
- A packet checksum or sequence failure discards the candidate client snapshot.
- A client with an incompatible protocol is rejected with a specific version message before play,
  rather than joining with unsafe visuals.
- A client still loading chunks queues the snapshot and ore refresh in safe order.
- A stage deletion removes stale definitions, lock caches, HUD data, and spoof state.
- A stage rename requires an explicit saved-data migration or alias. It is never inferred from a
  folder rename.
- Resource IDs unavailable on a client follow the stage's configured required or optional provider
  policy.
- Reloading EMI or JEI occurs once after the new snapshot activates, not once per changed entry.
- Large edits coalesce block and recipe viewer refreshes and apply per-tick work budgets.
- A player changing team or gamemode receives the relevant ownership delta after the configuration
  snapshot is current.
- Reconnect always requests the authoritative server revision and clears caches belonging to the
  previous server.

### 12.9 Sleek modern web interface

The editor frontend uses TypeScript, Preact, and a pinned reproducible Vite build. Gradle packages
the hashed static assets into the mod JAR. Runtime use requires no Node installation and no internet
connection.

Visual direction:

- A restrained Minecraft-compatible dark and light palette rather than a generic neon dashboard.
- Rounded cards and controls using a consistent 10 to 14 pixel radius.
- Clear stage color and icon accents.
- A left stage navigator, central visual workspace, right inspector, and collapsible validation
  drawer.
- Smooth 160 to 220 millisecond transitions for panels, rows, dialogs, graph edges, and validation
  state.
- Skeleton loading, optimistic draft feedback, and deliberate success or error animation.
- Responsive layouts for common laptop and desktop widths.
- Full keyboard navigation, visible focus, screen-reader labels, high contrast, color-blind-safe
  status indicators, and reduced-motion support.
- Virtualized large lists and debounced server search so a heavily modded registry remains fast.
- No visual effect may delay Apply, hide an error, or cause accidental destructive input.

### 12.10 LuckPerms inspiration and deliberate differences

The workflow borrows the useful part of the
[LuckPerms Web Editor](https://luckperms.net/wiki/Web-Editor): a command creates a scoped editing
session, the operator receives a browser experience, and changes are reviewed before application.
LuckPerms' official technical documentation describes a centrally hosted app with intermediary data
and WebSocket services. ProgressiveStages deliberately does not copy that network architecture.
Its assets are packaged in the mod, its browser endpoint is client-local, and all editor traffic
uses the already authenticated Minecraft connection. See the
[LuckPerms technical overview](https://luckperms.net/wiki/Web-Editor-Technical-Details) for the
inspiration being adapted rather than embedded.

### 12.11 Stage rename and deletion edge cases

Changing a display name is harmless. Changing a stage ID or deleting a stage is a saved-data
migration and receives a separate destructive review.

- ID rename requires a declared old-to-new alias and migrates ownership, progression counters,
  dependencies, references, challenge state, editor history, KubeJS references where statically
  known, and server-scoped leases in one transaction.
- Delete first reports dependent stages, owning subjects, active sessions, modifiers, costs,
  rewards, templates, variables, integrations, and unresolved script references.
- Configurable delete choices are cancel, replace with another stage, revoke from all subjects,
  cascade through dependents, or retain a hidden tombstone for compatibility.
- The editor defaults to cancel and never silently chooses cascade or mass revoke.
- Datapack-owned stages are read-only. The operator may create a config override or tombstone but
  cannot mutate the datapack archive.
- An applied rename or deletion increments the server revision, clears stale client state, refreshes
  recipe viewers and ore presentation, and is included in backup and rollback history.
- Rollback restores both package files and the saved-data migration when the transaction declares
  complete compensation support. Otherwise the editor blocks Apply and explains the manual step.

## 13. Editor coverage matrix

Every row below receives create, edit, duplicate, delete, search, validation, help, source preview,
and capability-state support. Registry-backed fields receive server registry pickers.

| Editor area | Complete coverage |
|---|---|
| Prefix entries | Exact IDs, mod, tag, hash-tag, name, priority tables, exclusions, and extension matchers. |
| Stage identity | ID, name, description, icon, dependencies, modes, scope, tags, hidden, color, category, priority, aliases, and deprecation. |
| Items | Use, pickup, inventory, hotbar, mouse pickup, holding, drop policy, masks, icons, tooltips, and entry priority. |
| Blocks | Placement, breaking, interaction, encryption, display substitute, and notifications. |
| Fluids | Bucket pickup, placement, flow, submersion, debuffs, and recipe-viewer behavior. |
| Recipes | Recipe IDs, output items, recipe types, crafting result behavior, EMI, and JEI presentation. |
| Crops | Planting, growth, bonemeal, harvest, automation policy, and replacement actions. |
| Dimensions | Portal, teleport, command travel, entry action, exit action, and fallback destination. |
| Enchantments | Table, anvil, villager, inventory strip, maximum levels, tags, and viewer filtering. |
| Entities | Attack, interact, use-item-on-entity, mount, and target filters. |
| Interactions | Block right click, item on block, item on entity, held item, target, hand, result, and exceptions. |
| Loot | Chest, fishing, archaeology, mob, block, Lootr, filters, substitutions, and guarded drops. |
| Mobs | Spawn denial, spawn reason, nearest-subject policy, replacements, validation, and session rules. |
| Pets | Taming, breeding, commanding, riding, owner rules, and ejection policy. |
| Screens | Block screens, held-item screens, menu types, close behavior, and portable containers. |
| Trades | Offer visibility, purchase enforcement, result, input, merchant type, level, and restock. |
| Professions | Vanilla and modded profession entries, trade behavior, and presentation. |
| Advancements | Tree visibility, toast policy, prerequisite concealment, and reveal conditions. |
| Structures | Entry, exit, exact providers, boxes, chests, block actions, explosions, spawns, leases, and outcomes. |
| Regions | Dimension, coordinates, shapes, entry, exit, break, place, explosions, spawns, effects, and messages. |
| Curios | Slot identifiers, equip, retain, eject, modifiers, and missing-mod behavior. |
| Ores | Display substitute, guarded drops, radius, light handling, refresh, and encrypted blocks. |
| Unlock carve-outs | Items, blocks, fluids, dimensions, entities, mods, category priority, and exact exceptions. |
| Enforcement | Every global override, allow list, policy, inventory behavior, message, sound, and cooldown. |
| Display | Map position, frame, background, reveal, tooltip, masks, encryption, and spoiler permissions. |
| Triggers and grants | Every condition, full trees, modes, counts, progress, persistence, reset, repeat, and actions. |
| Revokes | Grant-condition parity, death, health, damage, timers, XP, causes, cascades, and actions. |
| Attributes | Stage and contextual attributes, operations, stable IDs, stacking, aggregation, caps, and preview. |
| Cost and purchase | XP, items, variables, currencies, formulas, cooldowns, refunds, bypass, and simulation. |
| Unlock effects | Messages, titles, sounds, particles, fireworks, camera-safe presentation, and accessibility. |
| Abilities | Elytra, jump, sprint, swim, climb, reach, registered abilities, temporary policies, and reasons. |
| Rewards | Items, effects, commands, teleport, XP, variables, stages, action chains, failures, and rollback. |
| Beacon and brewing | Effect gates, level caps, potion results, ingredient rules, and UI filtering. |
| Temporary and triggered rules | Locks, unlocks, contexts, events, duration, refresh, stage state, priority, exceptions, and timers. |
| Item modifiers | Held, selected, hotbar, inventory, armor, Curios, use, attack, attributes, effects, transforms, affinity, and proficiency. |
| Challenges | Bosses, sessions, hit, damage, healing, death, time, consumables, steps, success, failure, retry, and HUD. |
| Variables and formulas | Types, scope, defaults, bounds, persistence, permissions, formulas, dependencies, and preview. |
| Stage states | Available, active, suspended, completed, failed, expired, custom states, guards, and transitions. |
| Integrations | FTB Quests, FTB Teams, EMI, JEI, Lootr, Curios, Jade, WTHIT, KubeJS, structures, and extension status. |
| Datapack stages | Read-only inspection plus one-click copy into a config override package. Datapack archives are never modified. |
| Raw source | Schema-aware TOML, navigation, formatting, validation, diff, and lossless unknown extension fields. |

New registered providers supply a schema, labels, help, defaults, validation, editor control hints,
and preview data. Until an extension provides a custom panel, the editor generates a complete form
from that schema rather than hiding the feature.

## 14. UI and network contract

### 14.1 Vanilla standard

- Use vanilla advancement textures, nine-slice panels, buttons, frames, narration, focus order,
  tooltip behavior, drag thresholds, scroll inertia, and GUI-scale handling where appropriate.
- The map remains draggable with the mouse, scrollable with the wheel, keyboard navigable, and
  bounded without trapping nodes offscreen.
- Background textures are author-configured per category or stage package with a global fallback.
- Modal details always render above nodes and tooltips. Background blur must render behind the
  stage screen rather than above it.
- The inventory recipe-book-style button remains configurable by side, offset, screen allowlist,
  visibility condition, and texture.

### 14.2 Server payloads

The server sends immutable, permission-filtered DTOs:

- Stage summaries for map layout.
- Detail payloads requested on demand.
- Condition progress snapshots and dirty updates.
- Current modifier and affinity summaries.
- Active challenge budgets and timers.
- Decision explanation payloads.
- History pages using cursors.
- Registry display metadata needed by extensions.

Payloads are versioned, size-limited, compressed where useful, and split safely. A client with an
older compatible protocol sees only supported fields.

### 14.3 Visibility and spoilers

Every UI field supports one of always, when available, when dependencies are met, when owned, when
completed, operator only, condition-controlled, or never. Hidden data is not sent to an
unauthorized client rather than merely drawn invisibly.

## 15. Main configuration defaults

The exact key names may be refined during schema implementation, but the plan requires controls
for at least the following policies:

```toml
[schema]
active_version = 4
legacy_mode = "translate"
unknown_field_policy = "preserve_and_warn"
unlisted_file_policy = "warn"
config_overrides_datapack = true
package_merge_policy = "replace"
normal_stage_files = ["stage.toml", "rules.toml", "progression.toml"]

[editor]
enabled = true
dedicated_server_only = true
required_permission_level = 4
session_duration = "30m"
draft_retention = "24h"
persist_named_drafts = true
collaboration_mode = "exclusive"
require_review_before_apply = true
require_confirmation_for_warnings = true
open_browser_automatically = true

[editor.security]
loopback_only = true
random_port = true
request_rate_limit_per_second = 30
maximum_request_bytes = 1048576
maximum_draft_bytes = 16777216
allow_remote_assets = false
allow_telemetry = false

[client_sync]
automatic = true
compression = "automatic"
delta_sync = true
require_acknowledgement = true
maximum_chunk_bytes = 1048576
join_during_apply = "wait_for_complete_revision"
incompatible_client_policy = "reject_with_message"

[priority]
global_default = 0
conditional_default = 100
minimum = -1000000
maximum = 1000000
tie_policy = "lock_wins"
specificity_order = ["id", "tag", "name", "mod"]
error_on_ambiguous_tie = false

[progression]
default_scope = "team"
default_repeat = "once"
default_cooldown = "0s"
default_debounce = "0s"
offline_time_policy = "real_time"
transition_budget_per_event = 128
cycle_policy = "reject_transaction"

[conditions]
poll_fallback = "20t"
script_timeout = "5ms"
unavailable_optional_provider = "warn_and_false"
counter_retention = "90d"

[modifiers]
default_aggregation = "exclusive"
default_stacking = "replace_by_priority"
maximum_per_item_multiplier = 64
unknown_attribute_policy = "warn_and_disable_rule"

[history]
enabled = true
retention = "30d"
maximum_entries_per_subject = 10000
include_failed_candidates = false

[ui]
background = "minecraft:textures/gui/advancements/backgrounds/stone.png"
show_rule_inspector = true
show_priority_to_players = false
show_priority_to_operators = true
show_challenge_hud = true
show_history = true
```

All defaults must be documented in the generated config. Package, stage, category, advanced-rule,
and individual-entry overrides must state whether they replace, merge, append, or inherit.

## 16. Legacy translation layer

### 16.1 Translation modes

- `translate` loads legacy files into the new runtime without writing to disk. This is the default.
- `strict` rejects legacy files and is intended only for authors validating a fully migrated pack.
- `legacy` runs the old parser path during the transition period for regression comparison.
- `compare` compiles both paths in development builds and reports semantic differences.

### 16.2 Legacy mapping

| Current 3.0.1 section | New destination | Translation rule |
|---|---|---|
| `[stage]` identity and metadata | `stage.toml` `[stage]` | Preserve ID, name, description, icon, dependencies, scope, tags, hidden, category, color, duration metadata, and source provenance. Missing stage priority becomes the compatible global static priority, normally zero. |
| `[items]` | `rules.toml` `[items]` | Keep the familiar section and preserve `locked`, `always_unlocked`, and enforcement behavior. |
| `[blocks]` | `rules.toml` `[blocks]` | Preserve placement, break, interaction, and presentation behavior. |
| `[fluids]` | `rules.toml` `[fluids]` | Preserve pickup, placement, flow, submersion, and recipe-viewer behavior. |
| `[recipes]` | `rules.toml` `[recipes]` | Preserve recipe-ID and output-item gates. |
| `[crops]` | `rules.toml` `[crops]` | Preserve planting, growth, bonemeal, and harvest settings. |
| `[dimensions]` | `rules.toml` `[dimensions]` | Preserve portal and teleport settings. |
| `[enchants]` and max levels | `rules.toml` `[enchants]` | Preserve enchant access, stripping, viewer hiding, and maximum levels. |
| `[entities]` | `rules.toml` `[entities]` | Preserve attack and interaction fields. |
| `[[interactions]]` | `rules.toml` `[[interactions]]` | Preserve held-item and target fields. |
| `[loot]` | `rules.toml` `[loot]` | Preserve loot filtering and owner semantics. |
| `[mobs]` and replacements | `rules.toml` `[mobs]` | Preserve spawn denial and replacement entries. |
| `[pets]` | `rules.toml` `[pets]` | Preserve tame, breed, command, and ride fields. |
| `[screens]` | `rules.toml` `[screens]` | Preserve block and held-item screen gates. |
| `[trades]` | `rules.toml` `[trades]` | Preserve offer filtering and completion enforcement. |
| `[professions]` | `rules.toml` `[professions]` | Preserve profession entries. |
| `[advancements]` | `rules.toml` `[advancements]` | Preserve advancement visibility entries. |
| `[structures]` | `rules.toml` `[structures]` | Preserve entry, chest, block, explosion, and spawn settings. |
| `[[regions]]` | `rules.toml` `[[regions]]` | Preserve boxes, dimensions, flags, and debuffs. |
| `[curios]` | `rules.toml` `[curios]` | Preserve optional Curios slot settings. |
| `[[ores.overrides]]` | `rules.toml` `[[ores.overrides]]` | Preserve visual substitutes and guarded drops. |
| `[unlocks]` | `rules.toml` `[unlocks]` | Preserve simple carve-outs and compatible higher-priority allows. |
| `[enforcement]` | `rules.toml` `[enforcement]` | Preserve exemptions and toggle overrides. |
| `[display]` | `stage.toml` `[display]` | Preserve map layout, reveal, tooltip, masking, icon, background, and encrypted-block behavior. |
| `[[triggers]]` | `progression.toml` `[[triggers]]` | Preserve spelling and behavior. Also accept `[[grants]]` as an alias. |
| `[[attribute]]` or `[attribute]` | `rules.toml` attribute section | Preserve stage-owned modifiers. New contextual behavior uses `[[item_modifiers]]`. |
| `[revoke]` | `progression.toml` `[revoke]` | Preserve death and maintained-XP fields and compile them into the shared revoke engine. |
| `[stage].duration` | `progression.toml` `[duration]` | Preserve real-time behavior through a stage-held-duration revoke condition. |
| `[cost]` | `progression.toml` `[cost]` | Preserve purchase items, XP, cooldown, and refunds. |
| `[unlock]` | `progression.toml` `[unlock]` | Preserve sounds, particles, titles, and messages. |
| `[abilities]` | `rules.toml` `[abilities]` | Preserve sprint, swim, climb, elytra, jump, and registered abilities. |
| `[rewards]` | `progression.toml` `[rewards]` | Preserve items, effects, commands, teleport, and XP. |
| Temporary and triggered rules | Same named sections in `rules.toml` | Preserve activation, priority, state, targets, exceptions, contexts, filters, duration, and refresh. |
| Structure active locks and session stages | `rules.toml` plus `progression.toml` | Preserve providers, exact sessions, active locks, leases, present-stage entries, leave outcomes, and ownership. |

Migration is organizational rather than a forced language rewrite. It places identity and display
in `stage.toml`, lock and modifier categories in `rules.toml`, and grant, revoke, reward, cost, and
challenge behavior in `progression.toml`. Familiar section names and plain prefix lists remain.
The original monolithic file is retained in a checksummed backup and semantic verification proves
that the compiled behavior did not change.

### 16.3 Migration commands

```text
/pstages migrate scan
/pstages migrate plan [stage]
/pstages migrate write [stage|all]
/pstages migrate verify [stage|all]
/pstages migrate rollback <migration_id>
```

- `scan` is read-only and reports schemas, conflicts, unknown fields, and required providers.
- `plan` shows exact output paths and a semantic before-and-after summary.
- `write` requires operator confirmation, creates a timestamped backup, writes into a temporary
  directory, validates the result, then atomically renames it.
- `verify` compiles legacy and migrated sources and compares effective stages, selectors,
  priorities, conditions, rewards, modifiers, and policies.
- `rollback` restores the untouched backup after verifying its checksum.

The CLI or server console equivalent must be available for headless pack development.

### 16.4 Comment and formatting policy

TOML libraries may not preserve comments and exact formatting reliably. Automatic migration must
never pretend otherwise. Originals are retained in a backup, migrated files receive detailed
generated comments, and a migration report maps every old field to its new path. Unknown fields
are copied into a namespaced extension table or reported for manual placement.

## 17. Commands, Java API, and KubeJS

### 17.1 Commands

All commands remain under `/pstages`. New command groups:

```text
/pstages editor
/pstages editor status
/pstages editor sessions
/pstages editor revoke <session>
/pstages editor drafts
/pstages editor resume <draft>
/pstages editor discard <draft>
/pstages package list
/pstages package inspect <stage>
/pstages package scaffold <stage_id>
/pstages validate [stage|all]
/pstages reload diff
/pstages explain stage <stage> [player]
/pstages explain target <context> <target> [player]
/pstages simulate event <event> [player] [parameters]
/pstages lifecycle progress <stage> [player]
/pstages lifecycle arm <rule> [player]
/pstages lifecycle reset <rule> [player]
/pstages challenge list [player]
/pstages challenge inspect <challenge> [player]
/pstages challenge reset <challenge> [player]
/pstages history [player]
/pstages rollback <transaction_id>
/pstages migrate ...
```

Mutation commands support dry run and return structured success or failure results. Permission
nodes, player visibility, console use, and audit logging are configurable.

### 17.2 Java API

Public interfaces include:

- Package and compiled snapshot inspection.
- Registry registration and capability lookup.
- Selector parsing and matching.
- Condition compilation, progress, and explanation.
- Rule decisions and complete decision traces.
- Lifecycle transaction builders.
- Challenge session and budget access.
- Variable and counter access.
- Simulation contexts.
- Migration adapters.
- Editor schemas, draft inspection, validation, semantic diffs, apply results, and audit events.
- Compiled client snapshot inspection and revision listeners.
- Preflight, progress, commit, reject, rollback, challenge, and reload events.

APIs use immutable DTOs, explicit subject scope, namespaced IDs, documented thread expectations,
and semantic capability versions. Callers never mutate internal collections.

### 17.3 KubeJS parity

KubeJS must be able to:

- Register conditions, numeric providers, actions, selector matchers, contexts, policies,
  modifiers, challenge measures, and display metadata.
- Build and submit grant, revoke, state, counter, variable, challenge, and rollback transactions.
- Query effective rules and decision traces.
- Add package fragments during startup.
- Receive preflight, progress, transition, challenge, and reload events.
- Return structured results with error codes and explanations.
- Discover whether optional capabilities are available before using them.

Script callbacks declare their event interests. State callbacks are cached until an associated
dirty key changes. Time budgets, failure policy, and debug timing are configurable.

## 18. Persistence and scope

### 18.1 Stored data

- Stage ownership and optional stage state.
- Lifecycle progress and repeat markers.
- Rolling-window counters.
- Grant and revoke timestamps.
- Challenge sessions and budgets.
- Variables, currencies, and custom counters.
- Action compensation records for rollback-capable transactions.
- Bounded decision and transition history.
- Schema and migration metadata.
- Named editor drafts, draft base revisions, and semantic operations when persistence is enabled.
- Configuration revision history, checksums, actor audit records, and rollback metadata.

Live editor session secrets, browser tokens, HTTP ports, and connection authorization are never
persisted.

### 18.2 Scope rules

Every stateful object declares player, team, server, or custom registered scope. A team-owned stage
may still have per-player challenge progress. A server-owned stage may be granted by one player's
event without copying timers to every team. Scope conversion is an explicit transaction and never
an implicit UUID substitution.

### 18.3 Offline and restart behavior

Timers declare real time, server online time, subject online time, world game time, or active
session time. Challenge sessions declare pause, fail, persist, or complete behavior on disconnect,
shutdown, dimension change, provider loss, or boss unload. Defaults are visible in validation and
may be overridden per rule.

## 19. Performance plan

- Compile selectors into registry indexes, namespace buckets, tag indexes, and context-specific
  decision tables.
- Compile conditions into an event-interest graph.
- Evaluate only rules made dirty by an event, stage transition, inventory change, equipment
  change, provider update, time bucket, or explicit API invalidation.
- Use the existing structure session seam as the exact structure context source. Do not rescan all
  structures independently for every rule.
- Cache immutable per-subject effective stage sets with revision numbers.
- Coalesce several inventory slot changes in one tick into one reconciliation.
- Use rolling counter buckets instead of retaining every damage or healing event forever.
- Bound history, explanation candidates, transition chains, network pages, and script execution.
- Page and virtualize editor registry results. Never send every modded registry object eagerly.
- Compile client snapshots once per configuration revision, then permission-filter and reuse safe
  sections instead of rebuilding the full payload for every player.
- Coalesce draft validation requests and cancel obsolete validation work when a newer draft
  revision arrives.
- Provide debug metrics for rule count, selector count, event fanout, evaluation time, cache hits,
  reconciliation time, script time, editor validation time, client snapshot time, payload size, and
  rejected transitions.
- Add configurable warning budgets and an operator report. Do not silently disable a slow rule.

Performance acceptance targets must be recorded against a synthetic large pack before release:

- 1,000 stages.
- 25,000 rules.
- 100,000 selectors.
- 100 simultaneous players in simulation.
- Dense inventory changes.
- Repeated combat and structure sessions.
- Reload with both legacy and package sources.

Exact millisecond budgets will be established on the project's test hardware and checked for
regression rather than guessed in this design document.

## 20. Security and safety

- Normalize and contain include paths so files cannot escape the stage package or approved shared
  template roots.
- Commands and action providers declare permission requirements.
- Client packets never choose ownership, priority winners, modifier amounts, or challenge results.
- Command actions use explicit execution identity and configurable allow or deny filters.
- Script providers have time budgets, exception isolation, and circuit breakers.
- Formulas do not permit arbitrary Java reflection or code execution.
- Migration writes are atomic and checksummed.
- Package downloads or remote includes are out of scope for the core loader.
- The editor listener is loopback-only, temporary, authenticated, rate-limited, and closed on
  session termination. Server file paths never cross the editor protocol.
- Editor assets use a restrictive content security policy and no external runtime resources.
- Apply always rechecks operator permission and base revision on the dedicated server.
- Raw TOML editing cannot escape the stage root, introduce remote includes, or bypass schema and
  action security validation.
- Sensitive operator diagnostics are filtered from normal players.
- A malformed provider result cannot produce NaN or infinite attributes, damage, healing, timers,
  priorities, or coordinates.

## 21. Implementation phases

Each phase ends with code, tests, generated examples, documentation, a clean build, and an updated
verification record. A phase is not complete because the classes compile.

### Phase 1. Freeze the 3.0.1 compatibility baseline

- Capture golden tests for all current stage sections and generated defaults.
- Capture effective lock, trigger, revoke, attribute, reward, and structure-session behavior.
- Add snapshot fixtures for representative legacy stages.
- Record current saved-data formats and network payload versions.
- Gate: no current behavior changes and the complete existing suite passes.

### Phase 2. Define schema 4 and provenance

- Implement source DTOs for `stage.toml`, `rules.toml`, `progression.toml`, optional includes,
  compiled rules, progression entries, prefix entries, and extensions.
- Attach file and field provenance to every source value.
- Publish machine-readable schemas with editor labels, help, defaults, validation, and control
  hints for every field.
- Gate: malformed package fixtures produce precise errors without touching runtime state.

### Phase 3. Build the immutable compiled model

- Introduce compiled stages, rules, lifecycle rules, selectors, condition nodes, action chains, and
  decision traces.
- Keep adapters from the compiled model to current enforcement APIs.
- Gate: compiled objects are immutable and cannot expose mutable internal collections.

### Phase 4. Implement package discovery

- Change config and datapack discovery to use `stage.toml` markers.
- Load `rules.toml` and `progression.toml` from the marked package and support optional explicit
  advanced includes with deterministic merging.
- Retain monolithic legacy discovery only for files with `[stage]` outside a package.
- Gate: helper TOMLs are never mistaken for stages, each field validates against its correct file,
  and packages compile identically across config and datapack sources.

### Phase 5. Implement in-memory legacy translation

- Map every current section through the table in this plan.
- Preserve provenance and generate compatibility IDs.
- Add legacy versus compiled semantic comparison tests.
- Gate: every golden legacy stage has the same effective behavior after translation.

### Phase 6. Upgrade selectors

- Add plain prefix-entry priority lookup tables, compact metadata shorthand, the internal
  `SelectorSpec`, registry matchers, and validation.
- Keep plain `PrefixEntry` adapters while old handlers remain.
- Gate: all current prefix lists remain the recommended syntax, match identically, and inherit
  category, stage, and global priority correctly.

### Phase 7. Unify decision priority

- Route static, conditional, temporary, triggered, session, modifier, and extension candidates
  through one resolver.
- Implement cascade, tie policies, specificity, and explain traces.
- Gate: conflict matrices pass for every lock and unlock pairing and every priority source.

### Phase 8. Build the condition registry and compiler

- Move existing conditions behind registered providers.
- Add boolean trees, comparisons, reusable references, event interests, and explanation output.
- Gate: no central switch is required to register an extension condition.

### Phase 9. Unify grants and revokes

- Compile `[[grants]]` and `[[revokes]]` into symmetric lifecycle rules.
- Add transactions, repeat modes, cooldown, debounce, grace, cycle detection, and atomic commit.
- Translate old triggers, revoke rules, and duration.
- Gate: grant and revoke parity tests cover every condition provider and subject scope.

### Phase 10. Add health, damage, death, and window counters

- Implement the new event providers and persistence buckets.
- Add filters, absorption policy, raw or final values, combat sessions, and reset policies.
- Gate: death, healing, damage, hit, respawn, health-crossing, and no-damage tests survive restart.

### Phase 11. Implement contextual modifiers

- Add item contexts, attributes, effects, transforms, aggregation, stacking, stable IDs, and dirty
  reconciliation.
- Translate current stage attributes into unconditional stage-owned modifiers.
- Gate: no camera or player-position snap occurs during grant, revoke, gamemode change, equipment
  change, or reload, and health is safely clamped when maximum health falls.

### Phase 12. Implement challenge budgets and sequences

- Add generic sessions, measures, budgets, ordered steps, success, failure, retry, and persistence.
- Integrate exact structure sessions and combat sessions as sources.
- Gate: the Wither hit-limit example works without a Wither-specific engine branch.

### Phase 13. Add profiles, templates, variables, and stage states

- Implement affinity and proficiency profiles.
- Implement templates, typed parameters, include merging, variables, formulas, and optional
  multi-state stages.
- Gate: mage and knight examples, template cycles, formula dependency cycles, and state transition
  guards are tested.

### Phase 14. Implement action pipelines and extension services

- Register actions, policies, modifiers, aggregators, contexts, measures, and migration adapters.
- Add failure and compensation policy.
- Gate: optional integrations load and unload safely, and action rollback is atomic where declared.

### Phase 15. Expand Java and KubeJS APIs

- Publish immutable APIs, transaction builders, registry hooks, events, simulation, and capability
  discovery.
- Provide script examples and type information where KubeJS supports it.
- Gate: TOML, Java, and KubeJS can express the same reference scenarios.

### Phase 16. Implement migration tooling

- Add scan, plan, write, verify, and rollback commands plus headless equivalents.
- Split legacy sections into the three-file package, with backups, checksums, semantic comparison,
  and reports.
- Gate: interrupted writes leave original files intact and migrated output either fully valid or
  absent.

### Phase 17. Implement compiled client snapshots and automatic sync

- Replace scattered full-definition and lock sync behavior with one revisioned, permission-filtered,
  chunked, compressed, acknowledged client snapshot plus safe deltas.
- Refresh connected clients after apply and send the full current snapshot to future clients during
  login. Keep ore decisions server-side and synchronize only required display deltas.
- Gate: join-during-apply, missed delta, checksum failure, incompatible protocol, stage deletion,
  team change, gamemode change, and EMI or JEI refresh tests pass.

### Phase 18. Implement editor sessions and the loopback bridge

- Add dedicated-server authorization, editor sessions, secrets, expiry, client payloads, the
  loopback HTTP service, packaged assets, browser launch, strict origin and host checks, CSP, rate
  limits, and cleanup.
- Gate: the editor is unreachable off-host, unauthorized players cannot create or use sessions,
  secrets never enter logs, and logout or revocation immediately disables access.

### Phase 19. Implement drafts, concurrency, and atomic apply

- Add server drafts, revisions, undo, redo, autosave, named recovery, semantic diff, conflict
  detection, multi-operator policies, full candidate validation, temporary writes, checksummed
  backups, atomic runtime swap, audit history, and rollback.
- Gate: concurrent operators cannot silently overwrite one another and every injected failure
  leaves either the old complete revision or the new complete revision active.

### Phase 20. Build the complete modern editor frontend

- Implement the Preact and TypeScript shell, stage CRUD, all coverage-matrix panels, registry
  pickers, graph editor, condition builder, modifier designer, challenge designer, conflict
  analyzer, simulation, templates, migration, source mode, accessibility, and responsive design.
- Gate: every schema field is editable, every extension field has a generated fallback control,
  all thirty feature groups are traceable to a tested screen or engine surface, and no external
  network request occurs at runtime.

### Phase 21. Player UI, documentation, examples, and usability

- Implement the stage inspector, why panel, challenge HUD, equipment preview, and history timeline.
- Rewrite documentation around the three-file package and editor workflow.
- Generate starter, complete Diamond, mage and knight, structure weapon, End lock, Wither trial,
  migration, KubeJS, and Java examples as machine-tested fixtures.
- Gate: GUI scale, modal layering, blur ordering, keyboard, narrator, web accessibility, reduced
  motion, and teenager usability sessions pass without developer assistance.

### Phase 22. Performance, release, and compatibility gate

- Run large synthetic pack benchmarks, dedicated-server tests, client UI tests, two-client team
  tests, editor concurrency tests, browser bridge tests, restart tests, optional-mod matrix, legacy
  translation matrix, snapshot sync tests, and artifact inspection.
- Confirm generated config hierarchy, all three stage files, and packaged editor assets in the JAR.
- Produce migration notes, compatibility guarantees, known limits, changelog, and rollback steps.
- Gate: Gradle build passes, all forced tests report zero failures, the server reaches ready state,
  the client and editor reach their tested interfaces, connected and future clients match the
  server revision, and release artifacts match documented checksums.

## 22. Test plan

### 22.1 Unit tests

- Package marker, three-file routing, explicit include path, include order, and section merge
  behavior.
- Legacy detection.
- Plain prefix lists, priority lookup tables, and compact priority suffix parsing.
- Prefix compatibility and selector metadata.
- Priority inheritance and every tie policy.
- Boolean condition truth tables.
- Sequences, minimum counts, comparisons, and references.
- Time parsing, rolling windows, reset policies, and offline policies.
- Grant and revoke symmetry.
- Cycle detection and transition budgets.
- Attribute operations, aggregation, stacking, stable IDs, and clamping.
- Template expansion, parameter typing, merge modes, variables, formulas, and stage states.
- Challenge budget arithmetic and boundary conditions.
- Migration mapping and checksums.
- DTO version and permission filtering.
- Editor token entropy, expiry, permission checks, origin validation, host validation, rate limits,
  size limits, path containment, and session cleanup.
- Draft revision, undo, redo, autosave, three-way conflict, semantic diff, and atomic apply state
  machines.
- Client snapshot chunking, checksum, acknowledgement, delta base revision, and atomic activation.

### 22.2 Game tests

- Item use, pickup, mouse pickup, hotbar, inventory, attack, place, break, and drop contexts.
- Block, fluid, recipe, crop, dimension, enchant, entity, interaction, loot, mob, pet, screen, trade,
  profession, advancement, structure, region, Curios, ore, beacon, brewing, and ability rules.
- Per-entry priority overrides a stage-wide mod lock.
- A missing entry priority inherits advanced rule when applicable, category, stage, then global
  priority.
- Grant on health gained and revoke on health lost.
- Revoke on death with configured cause filters.
- Repeating grant and revoke rules do not oscillate.
- Mage using a knight weapon receives configured damage and speed penalties.
- Knight using a registered magic item receives configured spell or cooldown penalties.
- Wither trial fails on the configured hit and succeeds under budget.
- Exact structure entry, exit, lease, and leave-outcome conditions.
- Modifier reconciliation never changes player position or camera state.
- Reload failure retains the previous working rules.

### 22.3 Migration tests

- Every 3.0.1 section in the mapping table.
- Mixed legacy and package stages.
- Same ID in datapack and config.
- Duplicate stage and rule IDs.
- Unknown legacy fields.
- Invalid optional provider.
- Existing saved ownership, trigger counters, grant timestamps, and session leases.
- Write migration, restart, semantic verify, and rollback.
- Linux case-sensitive and Windows-style path behavior.

### 22.4 UI tests

- Stage nodes drag and scroll like advancements.
- Details and tooltips always render above icons.
- Menu background blur stays behind the stage screen.
- Custom background textures load or fall back cleanly.
- Challenge HUD updates without flicker.
- Rule inspector respects spoilers and operator permissions.
- Inventory button placement works with recipe book open and closed.
- Keyboard, mouse, controller where available, narrator, and all supported GUI scales.
- Narrow and ultrawide screens.
- Disconnect and stale payload handling.
- Modern editor layout, rounded components, theme switching, responsive breakpoints, keyboard
  navigation, focus management, screen-reader names, reduced motion, and no external requests.
- Every coverage-matrix panel can add, edit, duplicate, delete, validate, diff, and save its data.

### 22.5 Performance and soak tests

- Large package discovery and reload.
- Dense condition event fanout.
- Inventory churn with many contextual modifiers.
- Combat with damage and healing rolling windows.
- One hundred simulated subjects with team and server stages.
- Long-running history retention and cleanup.
- Repeated script failures and circuit breaking.
- Reload while structure and challenge sessions are active.
- Long editor sessions with large registries, repeated autosave, several collaborators, repeated
  validation, and large semantic diffs.
- Connected-client broadcast and future-client login after hundreds of applied revisions.

### 22.6 Editor and synchronization integration tests

- `/pstages editor` refuses non-operators, console-only invocations without a target client,
  integrated servers, unsupported clients, expired sessions, and revoked sessions.
- The HTTP listener binds only to loopback and uses a random port.
- Browser API calls without the session secret, from the wrong origin, with the wrong host, above
  the rate limit, or above the size limit fail without reaching draft mutation.
- A remote operator can use the client-local editor without opening a port on the dedicated server.
- Browser close, player logout, permission loss, server switch, client crash, and server shutdown
  release resources and invalidate access.
- Two operators editing the same base revision receive the configured lock or collaboration
  behavior and never silently overwrite changes.
- Apply success writes all three files, swaps one runtime revision, broadcasts one complete client
  revision, refreshes ore state safely, and records one audit transaction.
- Injected parse, validation, disk, compile, reconciliation, packet, and acknowledgement failures
  retain or restore the last known good server and client revisions.
- A player online during apply and a player joining afterward receive identical effective stage,
  lock, display, modifier, challenge, and ore presentation state.
- Automated browser tests use the packaged production assets rather than a development server.

## 23. Documentation deliverables

- A schema 4 overview for beginners.
- A three-file package map explaining `stage.toml`, `rules.toml`, and `progression.toml` in one page.
- A `/pstages editor` first-stage tutorial requiring no TOML editing.
- A manual three-file first-stage tutorial for power users.
- A complete field reference generated from codecs where possible.
- Selector and priority reference with conflict diagrams.
- Condition reference with value type, events, scope, persistence, and examples.
- Grant and revoke cookbook.
- Contextual buff and debuff cookbook.
- Challenge and boss-trial cookbook.
- Mage and knight affinity example.
- Structure-session and temporary-lock examples.
- Legacy migration guide with before and after files.
- Java API reference.
- KubeJS reference with executable scripts.
- Command and permissions reference.
- Editor session, draft, collaboration, apply, backup, rollback, and audit reference.
- Localhost bridge security model, privacy guarantees, troubleshooting, and threat model.
- Automatic client snapshot protocol, current-client apply behavior, and future-client login
  behavior.
- Complete editor coverage checklist mapping every config field to its screen.
- UI authoring and spoiler guide.
- Performance tuning and diagnostics guide.
- Troubleshooting guide based on exact validation codes.
- A generated Diamond reference package linked from the main documentation and from the default
  generated stage.

Every documentation example must be stored as a test resource or generated from a tested source.
Examples that merely look correct are not enough.

## 24. Acceptance scenarios

### 24.1 Structure weapon rules

- Inside a configured structure, all weapons except swords are denied.
- A second higher-priority prefix entry allows one named bow.
- On structure exit, the temporary rule ends without changing permanent stage ownership.
- The why panel identifies both the broad lock and the winning exception.

### 24.2 Stronghold and End progression

- Mage blocks Stronghold entry through a normal rule.
- Killing the Wither grants End Fight through a lifecycle grant.
- End Fight's higher-priority allow rule permits Stronghold entry.
- Entering the End activates temporary rules that deny jump, elytra, and a diamond pickaxe.
- Leaving the End or ending its session removes those temporary rules.

### 24.3 Death and health regression

- A trial stage grants after the configured requirements.
- Death revokes it immediately when the cause filter matches.
- Losing 40 health within 30 seconds also revokes it.
- Healing does not reduce accumulated health lost unless the author configures net-health mode.
- Cooldown prevents immediate regrant oscillation.

### 24.4 Mage and knight cross-use

- Both classes can hold and use the other's equipment.
- Foreign equipment receives configured damage, speed, spell-power, mana, cooldown, or durability
  changes.
- Training or mastery stages reduce or remove the penalty through higher-priority modifiers.
- Tooltips preview the effective penalty before combat.

### 24.5 Boss hit challenge

- Combat with the selected boss opens a challenge session.
- Only configured hit sources consume the hit budget.
- Dodged or zero-damage hits follow the authored policy.
- Failure runs configured revoke and message actions.
- Success grants the configured stage and records history.
- Disconnect and boss despawn follow the configured session policy.

### 24.6 Legacy pack

- An untouched 3.0.1 config folder loads.
- Effective locks, triggers, revokes, attributes, rewards, and UI match the compatibility fixtures.
- The migration plan shows exact output without writing.
- Written packages validate semantically against their originals.
- Rollback restores the original checksummed files.

### 24.7 Remote dedicated-server editor

- A remote operator runs `/pstages editor` without server shell access or an exposed web port.
- The operator's client opens the loopback editor in their local browser.
- They create `pack:end_fight`, configure its appearance, locks, grants, revokes, item modifiers,
  and boss challenge using visual forms.
- Live validation catches an invalid item and dependency cycle before apply.
- Review shows the three generated files and semantic effects.
- Apply writes one complete revision, reloads the server, closes the draft, and records the actor.
- Another operator cannot use the URL or expired session secret.

### 24.8 Automatic current and future client synchronization

- Several players are online while an operator adds `pack:end_fight` and changes ore presentation.
- Apply broadcasts the new compiled client revision automatically with no publication checkbox.
- Every online player atomically sees the same stage map, tooltips, recipe-viewer state, HUD data,
  and ore presentation after acknowledgement.
- A player joining later receives the same full revision during login despite having no stage TOML
  files in their client config.
- Server enforcement remains correct when presentation packets are delayed or a modified client
  ignores them.

## 25. Definition of done

The rehaul is complete only when all of the following are true:

- Package stages and untouched 3.0.1 stages can coexist.
- Random helper TOMLs are not parsed as stages.
- The normal package uses `stage.toml`, `rules.toml`, and `progression.toml` with documented section
  ownership and no required subfolders.
- Grant and revoke use the same condition registry and complete condition tree.
- Death, health gained, health lost, damage, and hit conditions work with documented persistence.
- Entry priority works through the simple category lookup table and the requested compact
  shorthand without changing ordinary prefix lists.
- Missing entry priority correctly inherits category, stage, then global priority through the
  documented cascade.
- Contextual item attributes, buffs, debuffs, and transforms reconcile without visual or position
  snapping.
- The generic budget engine supports the boss hit-limit scenario.
- All thirty numbered feature groups pass their implementation and verification gates.
- Every row in the editor coverage matrix has a complete tested control surface.
- `/pstages editor` is dedicated-server-only, operator-authorized, loopback-only, self-contained,
  and usable remotely through the connected operator client without opening a server web port.
- Stage TOML remains server-only. Connected and future clients automatically activate the same
  verified compiled client revision.
- The editor supports drafts, undo, redo, validation, simulation, conflict handling, atomic apply,
  backups, audit history, and rollback.
- Config, datapack, Java, KubeJS, command, and UI surfaces describe the same canonical rules.
- Every old schema section has a tested translation.
- Failed parsing, compilation, migration, or reload never replaces a valid runtime snapshot.
- Documentation examples are executable test fixtures.
- The forced test report has zero failures and zero skipped required tests.
- Gradle build, dedicated server, client UI, multiplayer, restart, optional integration, migration,
  web editor, client synchronization, performance, and JAR inspection gates pass.

## 26. Recommended first implementation step after approval

Begin with Phase 1 and Phase 2 only. Freeze the current behavior in golden fixtures, define schema
4 source records with provenance and editor metadata, and publish tested examples for the
three-file package. Do not
replace enforcement, triggers, revokes, or attributes until the translator and compiled model can
prove equivalent behavior. This preserves the working 3.0.1 branch while giving every later phase
a stable contract.
