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
6. Ten additional engine features and five UI-only features, all designed around author control.

The goal is not to create more unrelated switches. The goal is one coherent engine with reusable
prefix entries, conditions, actions, contexts, policies, and diagnostics.

### The KISS authoring decision

- A new stage is normally `stages/<name>/stage.toml`.
- Everything can be configured inside that one file.
- Existing category names and simple string lists remain the primary syntax.
- Extra files are optional includes that use the exact same sections as `stage.toml`.
- The generic compiled rule model is an engine implementation detail, not required knowledge.
- Advanced syntax adds capability without replacing or complicating the simple syntax.

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

- The normal package is one `stage.toml` file and uses the familiar category sections from 3.0.1.
- A one-file legacy stage remains a supported beginner path.
- Validation errors name the stage, included file, section, field, invalid value, and a suggested fix.
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
| Reload safety | Candidate definitions validate before activation and rollback to the previous snapshot on failure. | This strength must be retained while adding packages, translation, compilation, and extension registries. |
| UI | The stage tree already has advancement-style foundations. | It cannot yet inspect priority conflicts, lifecycle conditions, contextual modifiers, challenge budgets, or history. |

The rehaul should replace these separate runtime paths gradually. It must not remove working
enforcement handlers before their equivalent compiled rule path is tested.

## 4. KISS stage-package layout

### 4.1 The normal layout is one file

The default generated stage is one folder containing one familiar file:

```text
config/
└── progressivestages/
    ├── progressivestages.toml
    └── stages/
        ├── iron_age/
        │   └── stage.toml
        └── end_fight/
            └── stage.toml
```

`stage.toml` contains everything for that stage. It uses the existing recognizable sections such
as `[items]`, `[blocks]`, `[fluids]`, `[structures]`, `[[triggers]]`, `[revoke]`, `[rewards]`, and
`[display]`, plus equally direct new sections such as `[[revokes]]`, `[[item_modifiers]]`, and
`[[challenges]]`. A teenager should be able to copy a small example, replace the IDs, and play.

The folder solves discovery and organization without forcing authors to learn a multi-file module
system:

- A directory is a stage only when it contains `stage.toml`.
- `stage.toml` is a complete stage by itself.
- The stage ID still comes from `[stage].id`, so renaming a folder does not corrupt saved ownership.
- Random TOMLs are not guessed to be stages.
- No `rules.toml`, `lifecycle.toml`, module declaration, fragment kind, or generic selector object
  is required.

### 4.2 Splitting a large stage is optional

An author who likes one file never has to split it. An unusually large stage may opt into simple
includes:

```text
stages/
└── applied_energistics/
    ├── stage.toml
    ├── items.toml
    ├── crafting.toml
    └── boss_trial.toml
```

`stage.toml` names the extra files explicitly:

```toml
[stage]
id = "pack:applied_energistics"
name = "Applied Energistics"
priority = 100

[files]
include = ["items.toml", "crafting.toml", "boss_trial.toml"]
```

An included file contains the same ordinary sections that could have been pasted into
`stage.toml`. It has no stage identity and no module header:

```toml
[items]
locked = ["mod:ae2"]
always_unlocked = ["id:ae2:certus_quartz_crystal"]
```

This is a filing-cabinet feature, not a second schema. The loader merges included sections into the
owning stage before validation. The files may be named and arranged however the author finds
clear. Nested paths such as `combat/boss_trial.toml` are allowed, but there are no required
subfolders.

Include behavior is intentionally simple:

- Files load in the exact order listed.
- Lists append and remove exact duplicates while preserving the first occurrence.
- Array entries such as triggers, revokes, modifiers, and challenges append and require unique IDs.
- A scalar written twice is an error unless the include entry explicitly chooses `override`.
- An included file cannot declare `[stage]` or include more files by default.
- Missing files, path escape attempts, duplicate IDs, and ambiguous overrides reject the reload.

### 4.3 Datapack equivalent

```text
data/<namespace>/progressivestages/stages/<package_path>/stage.toml
data/<namespace>/progressivestages/stages/<package_path>/<optional_included_file>.toml
```

The datapack and config loaders share the same parser. Config stages override datapack stages with
the same ID. A local config may replace the whole datapack stage safely without accidentally
merging unrelated files.

### 4.4 Existing one-file stages stay easy

These current files remain valid without being moved:

```text
stages/diamond_stage.toml
stages/technology/ae2.toml
```

A TOML outside a `stage.toml` package is a legacy monolithic stage only when it contains `[stage]`.
It is translated in memory. The migration command may wrap it in a folder as `stage.toml`, but it
does not split the contents unless the author explicitly asks it to.

## 5. KISS authoring schema

### 5.1 A complete beginner stage

The common path stays category based. Authors do not need to understand the compiled rule engine:

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

[items]
locked = [
  "name:iron",
  "mod:ae2"
]
always_unlocked = ["id:minecraft:iron_nugget"]

[blocks]
locked = ["name:iron"]

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

The existing `[[triggers]]` name remains the simple grant spelling. `[[grants]]` is an equally
valid clearer alias. Both compile to the same lifecycle engine. `[revoke]` remains valid for the
old simple death, XP, and duration cases, while `[[revokes]]` adds full trigger-condition parity.

### 5.2 Priority without selector objects

Most authors set one priority on the stage and stop there. Every category entry inherits it.
Categories may set a different default with one obvious field:

```toml
[stage]
id = "pack:technology"
priority = 100

[items]
priority = 150
locked = ["mod:ae2", "id:ae2:wireless_terminal"]
```

Individual exceptions use a readable lookup table instead of turning every list entry into an
object:

```toml
[items.priorities]
"mod:ae2" = 150
"id:ae2:wireless_terminal" = 500
```

The requested compact spelling is also accepted for authors who prefer it:

```toml
[items]
locked = ["mod:ae2,priority:150", "id:ae2:wireless_terminal,priority:500"]
```

Plain entries remain the recommended beginner form. The documentation introduces per-entry
priority only in the advanced conflict section.

### 5.3 Direct contextual locks and unlocks

Common temporary behavior gets named sections with human fields rather than a generic rule object:

```toml
[[temporary_locks]]
id = "pack:end_no_elytra"
abilities = ["elytra", "jump"]
items = ["id:minecraft:diamond_pickaxe"]
in_dimensions = ["minecraft:the_end"]
priority = 200

[[temporary_unlocks]]
id = "pack:allow_stronghold_after_wither"
structures = ["minecraft:stronghold"]
with_stages = ["pack:end_fight"]
priority = 200
```

Advanced nested conditions remain possible, but simple dimension, biome, structure, stage,
health, item, effect, and movement fields cover the normal use cases.

### 5.4 Direct item buffs and debuffs

The normal modifier syntax names what it affects and where it must be:

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

[[item_modifiers.attributes]]
id = "minecraft:generic.attack_speed"
amount = -0.25
operation = "multiply_total"
```

Aliases such as `while_in_hotbar`, `while_in_inventory`, `while_wearing`, and `while_using` avoid
requiring context registry IDs in common files. Advanced authors can use registered context IDs
when an integration adds a new location or activity.

### 5.5 The generic rule language is an escape hatch

Internally, every category compiles into a generic rule model. A public `[[rules]]` section may be
available for extension authors and unusually complex combinations, but:

- It is never required for a normal stage.
- It is not generated by default.
- It is documented after all category-based examples.
- The UI and migration tools write familiar category sections whenever the behavior fits them.
- Validation suggests the simpler equivalent when an advanced rule can be represented directly.

The engine can be powerful without making every pack author speak the engine's internal language.

### 5.6 KISS acceptance rules

- The generated starter stage contains one file and fewer than 40 active configuration lines.
- A first stage can be made by changing the stage ID, name, icon, and one `locked` list.
- Common grants and revokes do not require explicit rule IDs, condition trees, action arrays, or
  registry-provider knowledge.
- Common item modifiers use `items`, `while_holding`, `with_stages`, and `attributes` rather than
  generic target, context, predicate, and action objects.
- The beginner guide teaches `[stage]`, one lock category, one trigger, and one revoke before it
  mentions priority or advanced rules.
- Generated comments use everyday language. Terms such as AST, DTO, codec, provenance, and event
  index stay in developer documentation.
- Every advanced feature has a short-form example before its complete expert form.
- Validation recommends a direct category field when an author writes an unnecessarily generic
  rule.
- The complete Diamond reference remains separate from the tiny generated starter so new users are
  not greeted by hundreds of options.
- A teenager unfamiliar with the codebase must be able to complete the documented first-stage and
  item-debuff walkthrough during manual usability testing without developer assistance.

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

## 11. Ten additional engine features

The package schema, legacy translation, symmetric grant and revoke engine, prefix-entry priority,
health and death conditions, contextual item modifiers, and hit-limit requirement are mandatory
foundations. The following are ten additional engine feature groups.

### Engine feature 1. Ordered and timed condition sequences

Authors can require ordered actions such as enter a structure, activate three blocks in order,
kill a guardian, then leave successfully within five minutes. Steps may allow, forbid, or reset on
intervening events. Progress persists according to author-selected scope and reset policy.

### Engine feature 2. Reusable challenge and budget framework

The hit-limit implementation becomes a generic budget engine for hits, damage, healing, deaths,
consumables, item uses, blocks broken, time, movement, summons, commands, or custom numeric
providers. Budgets support minimums, maximums, ranges, shared pools, regeneration, checkpoints,
and multiple failure tiers.

### Engine feature 3. Affinity, proficiency, and mastery profiles

Authors define role or class affinity for item or content groups. A profile may deny, weaken,
strengthen,
increase cost, change cooldown, or replace behavior at proficiency levels. Proficiency can be a
stage, variable, scoreboard, custom counter, currency, or formula. Mage and knight cross-weapon
penalties are one configuration, not a built-in class system.

### Engine feature 4. Templates, bundles, parameters, and inheritance

Shared rule bundles eliminate copy and paste. A template accepts typed parameters such as stage
ID, item tag, priority, damage multiplier, message, and icon. Packages can include and override
bundles with explicit merge policies. Cycles, missing parameters, type mismatches, and conflicting
IDs fail validation with a complete include trace.

### Engine feature 5. Multi-state stages

Stages may optionally use states beyond owned or missing: unavailable, available, active,
suspended, completed, failed, expired, and a registered custom state. Authors define allowed
transitions and which state counts as ownership for compatibility. This enables a trial to be
available, active during a boss, failed after too many hits, and completed after success without
creating several duplicate stages.

### Engine feature 6. Declarative action pipelines

Every transition and challenge may run validated action chains: grant, revoke, suspend, set state,
give or take items, apply effects, change variables, execute commands, teleport, play sounds,
spawn particles, send messages, set cooldowns, start challenges, call registered services, or run
KubeJS callbacks. Actions define failure handling as rollback, continue, retry, or compensate.

### Engine feature 7. Variables, currencies, counters, and formulas

Packages may declare typed player, team, or server values. Values have defaults, bounds,
persistence, sync visibility, mutation permissions, and reset policies. A safe compiled formula
language can calculate thresholds and modifier amounts from declared values. Formula dependencies
join the dirty-event index and are not evaluated every tick.

### Engine feature 8. Transition stability controls

Every live condition can use cooldown, debounce, grace period, hysteresis, minimum-active duration,
minimum-inactive duration, schedule, and offline-time policy. This prevents flicker near health,
position, population, or inventory thresholds and lets authors build daily or seasonal rules.

### Engine feature 9. Public registries and service interfaces

Conditions, selectors, contexts, policies, actions, modifiers, aggregators, structure providers,
challenge measures, formula functions, UI detail providers, and migration adapters are registered
by namespaced ID. Java services and KubeJS registration expose codecs, validation, event interests,
explanation text, and capability discovery. Missing optional mods never stop core startup unless a
package explicitly marks that provider as required.

### Engine feature 10. Explain, simulate, history, and rollback tooling

The engine records bounded structured decision history. Operators can ask why an item is locked,
why a stage granted or revoked, what a reload changes, which priority won, and what would happen to
a selected player without making changes. Stage transactions may be rolled back by transaction ID
when all actions declare compensation support.

## 12. Five UI-only features

These features present existing server data. They do not create a separate client authority or a
hidden configuration format.

### UI feature 1. Stage rule inspector

The stage details panel gains tabs for overview, requirements, grants, revokes, locks, modifiers,
rewards, and dependencies. Each entry has an author-controlled icon, title, description, progress,
visibility, and tooltip. Players see only permitted information; operators may reveal canonical
IDs and source files.

### UI feature 2. Why panel and priority stack

When content is blocked or modified, a vanilla-style expandable panel shows the winning decision
and, when allowed, the other candidates it defeated. It displays effective priority, inherited
priority source, prefix specificity, current conditions, remaining duration, and the applicable
allow or deny policy.

### UI feature 3. Challenge HUD

A configurable HUD presents active challenge name, boss or session, hit budget, damage budget,
timer, step sequence, success criteria, and failure warning. Authors choose placement, scale,
colors, icons, animation, compact mode, automatic hiding, and which values are secret.

### UI feature 4. Equipment and affinity preview

Hovering an item or opening a stage detail can show how that item behaves for the current player:
normal, locked, weakened, strengthened, increased cost, changed cooldown, or conditionally allowed.
The panel previews exact attribute deltas and names the stage or proficiency needed to improve it.

### UI feature 5. Progress history timeline

A scrollable vanilla-styled timeline shows stage grants, revokes, suspensions, challenge attempts,
successes, failures, expirations, purchases, respecs, and important rule transitions. Visibility,
retention, timestamps, grouping, and operator detail are server-configured.

## 13. UI and network contract

### 13.1 Vanilla standard

- Use vanilla advancement textures, nine-slice panels, buttons, frames, narration, focus order,
  tooltip behavior, drag thresholds, scroll inertia, and GUI-scale handling where appropriate.
- The map remains draggable with the mouse, scrollable with the wheel, keyboard navigable, and
  bounded without trapping nodes offscreen.
- Background textures are author-configured per category or stage package with a global fallback.
- Modal details always render above nodes and tooltips. Background blur must render behind the
  stage screen rather than above it.
- The inventory recipe-book-style button remains configurable by side, offset, screen allowlist,
  visibility condition, and texture.

### 13.2 Server payloads

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

### 13.3 Visibility and spoilers

Every UI field supports one of always, when available, when dependencies are met, when owned, when
completed, operator only, condition-controlled, or never. Hidden data is not sent to an
unauthorized client rather than merely drawn invisibly.

## 14. Main configuration defaults

The exact key names may be refined during schema implementation, but the plan requires controls
for at least the following policies:

```toml
[schema]
active_version = 4
legacy_mode = "translate"
unknown_field_policy = "preserve_and_warn"
unlisted_file_policy = "ignore"
config_overrides_datapack = true
package_merge_policy = "replace"

[priority]
global_default = 0
conditional_default = 100
minimum = -1000000
maximum = 1000000
tie_policy = "lock_wins"
specificity_order = ["id", "tag", "name", "mod"]
error_on_ambiguous_tie = false

[lifecycle]
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

## 15. Legacy translation layer

### 15.1 Translation modes

- `translate` loads legacy files into the new runtime without writing to disk. This is the default.
- `strict` rejects legacy files and is intended only for authors validating a fully migrated pack.
- `legacy` runs the old parser path during the transition period for regression comparison.
- `compare` compiles both paths in development builds and reports semantic differences.

### 15.2 Legacy mapping

| Current 3.0.1 section | New destination | Translation rule |
|---|---|---|
| `[stage]` identity and metadata | `stage.toml` `[stage]` | Preserve ID, name, description, icon, dependencies, scope, tags, hidden, category, color, duration metadata, and source provenance. Missing stage priority becomes the compatible global static priority, normally zero. |
| `[items]` | `stage.toml` `[items]` | Keep the familiar section and preserve `locked`, `always_unlocked`, and enforcement behavior. It compiles internally into item-context decisions. |
| `[blocks]` | `stage.toml` `[blocks]` | Keep the familiar section and preserve placement, break, interaction, and presentation behavior. |
| `[fluids]` | `stage.toml` `[fluids]` | Keep the familiar section and preserve pickup, placement, flow, submersion, and recipe-viewer behavior. |
| `[recipes]` | `stage.toml` `[recipes]` | Keep recipe-ID and output-item gates as the same author-facing fields. |
| `[crops]` | `stage.toml` `[crops]` | Keep planting, growth, bonemeal, and harvest settings. |
| `[dimensions]` | `stage.toml` `[dimensions]` | Keep portal and teleport settings. |
| `[enchants]` and max levels | `stage.toml` `[enchants]` | Keep enchant access, stripping, viewer hiding, and maximum level fields. |
| `[entities]` | `stage.toml` `[entities]` | Keep attack and interaction fields. |
| `[[interactions]]` | `stage.toml` `[[interactions]]` | Preserve the existing held-item and target fields. |
| `[loot]` | `stage.toml` `[loot]` | Preserve loot filtering and existing owner semantics. |
| `[mobs]` and replacements | `stage.toml` `[mobs]` | Preserve spawn denial and replacement entries. |
| `[pets]` | `stage.toml` `[pets]` | Preserve tame, breed, command, and ride fields. |
| `[screens]` | `stage.toml` `[screens]` | Preserve block and held-item screen gates. |
| `[trades]` | `stage.toml` `[trades]` | Preserve offer filtering and completion enforcement. |
| `[professions]` | `stage.toml` `[professions]` | Preserve profession entries. |
| `[advancements]` | `stage.toml` `[advancements]` | Preserve advancement visibility entries. |
| `[structures]` | `stage.toml` `[structures]` | Preserve entry, chest, block, explosion, and spawn settings. |
| `[[regions]]` | `stage.toml` `[[regions]]` | Preserve boxes, dimensions, flags, and debuffs. |
| `[curios]` | `stage.toml` `[curios]` | Preserve optional Curios slot settings. |
| `[[ores.overrides]]` | `stage.toml` `[[ores.overrides]]` | Preserve visual substitutes and guarded drops. |
| `[unlocks]` | `stage.toml` `[unlocks]` | Keep the simple carve-out section and compile its entries as compatible higher-priority allows internally. |
| `[enforcement]` | `stage.toml` `[enforcement]` | Keep exemptions and toggle overrides as direct fields. |
| `[display]` | `stage.toml` `[display]` | Preserve map layout, reveal, tooltip, masking, icon, background, and encrypted-block behavior. |
| `[[triggers]]` | `stage.toml` `[[triggers]]` | Keep the existing spelling and behavior. Also accept `[[grants]]` as an alias for new files. |
| `[[attribute]]` or `[attribute]` | `stage.toml` existing attribute section | Preserve stage-owned modifiers. New contextual behavior uses the direct `[[item_modifiers]]` section. |
| `[revoke]` | `stage.toml` `[revoke]` | Keep simple death and maintained-XP fields. Compile them into the shared revoke engine. |
| `[stage].duration` | `stage.toml` `[stage].duration` | Keep the field and compile it into a stage-held-duration revoke condition. |
| `[cost]` | `stage.toml` `[cost]` | Preserve purchase items, XP, cooldown, and refund fields. |
| `[unlock]` | `stage.toml` `[unlock]` | Preserve sounds, particles, titles, and messages. |
| `[abilities]` | `stage.toml` `[abilities]` | Preserve sprint, swim, climb, elytra, jump, and registered ability entries. |
| `[rewards]` | `stage.toml` `[rewards]` | Preserve items, effects, commands, teleport, and XP. |
| Temporary and triggered rules | Same named sections in `stage.toml` | Preserve effect, activation, priority, owner state, targets, exceptions, contexts, filters, duration, and refresh behavior. Add simpler direct-field aliases where possible. |
| Structure active locks and session stages | Same named sections in `stage.toml` | Preserve provider IDs, exact sessions, leases, present-stage entries, leave outcomes, and team-safe ownership. |

Migration is therefore mostly organizational, not a forced language rewrite. The normal migrated
result is the old file copied safely to `<stage_folder>/stage.toml` with a schema marker and only
the minimum necessary field normalization. Automatic migration does not produce `rules.toml`,
`lifecycle.toml`, selector objects, or a collection of fragments. Optional splitting is a separate
author-requested operation.

### 15.3 Migration commands

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

### 15.4 Comment and formatting policy

TOML libraries may not preserve comments and exact formatting reliably. Automatic migration must
never pretend otherwise. Originals are retained in a backup, migrated files receive detailed
generated comments, and a migration report maps every old field to its new path. Unknown fields
are copied into a namespaced extension table or reported for manual placement.

## 16. Commands, Java API, and KubeJS

### 16.1 Commands

All commands remain under `/pstages`. New command groups:

```text
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

### 16.2 Java API

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
- Preflight, progress, commit, reject, rollback, challenge, and reload events.

APIs use immutable DTOs, explicit subject scope, namespaced IDs, documented thread expectations,
and semantic capability versions. Callers never mutate internal collections.

### 16.3 KubeJS parity

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

## 17. Persistence and scope

### 17.1 Stored data

- Stage ownership and optional stage state.
- Lifecycle progress and repeat markers.
- Rolling-window counters.
- Grant and revoke timestamps.
- Challenge sessions and budgets.
- Variables, currencies, and custom counters.
- Action compensation records for rollback-capable transactions.
- Bounded decision and transition history.
- Schema and migration metadata.

### 17.2 Scope rules

Every stateful object declares player, team, server, or custom registered scope. A team-owned stage
may still have per-player challenge progress. A server-owned stage may be granted by one player's
event without copying timers to every team. Scope conversion is an explicit transaction and never
an implicit UUID substitution.

### 17.3 Offline and restart behavior

Timers declare real time, server online time, subject online time, world game time, or active
session time. Challenge sessions declare pause, fail, persist, or complete behavior on disconnect,
shutdown, dimension change, provider loss, or boss unload. Defaults are visible in validation and
may be overridden per rule.

## 18. Performance plan

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
- Provide debug metrics for rule count, selector count, event fanout, evaluation time, cache hits,
  reconciliation time, script time, payload size, and rejected transitions.
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

## 19. Security and safety

- Normalize and contain include paths so files cannot escape the stage package or approved shared
  template roots.
- Commands and action providers declare permission requirements.
- Client packets never choose ownership, priority winners, modifier amounts, or challenge results.
- Command actions use explicit execution identity and configurable allow or deny filters.
- Script providers have time budgets, exception isolation, and circuit breakers.
- Formulas do not permit arbitrary Java reflection or code execution.
- Migration writes are atomic and checksummed.
- Package downloads or remote includes are out of scope for the core loader.
- Sensitive operator diagnostics are filtered from normal players.
- A malformed provider result cannot produce NaN or infinite attributes, damage, healing, timers,
  priorities, or coordinates.

## 20. Implementation phases

Each phase ends with code, tests, generated examples, documentation, a clean build, and an updated
verification record. A phase is not complete because the classes compile.

### Phase 1. Freeze the 3.0.1 compatibility baseline

- Capture golden tests for all current stage sections and generated defaults.
- Capture effective lock, trigger, revoke, attribute, reward, and structure-session behavior.
- Add snapshot fixtures for representative legacy stages.
- Record current saved-data formats and network payload versions.
- Gate: no current behavior changes and the complete existing suite passes.

### Phase 2. Define schema 4 and provenance

- Implement source DTOs for the one-file authoring sections, optional includes, compiled rules,
  lifecycle entries, prefix entries, and extensions.
- Attach file and field provenance to every source value.
- Publish machine-readable schema descriptions and one-file examples.
- Gate: malformed package fixtures produce precise errors without touching runtime state.

### Phase 3. Build the immutable compiled model

- Introduce compiled stages, rules, lifecycle rules, selectors, condition nodes, action chains, and
  decision traces.
- Keep adapters from the compiled model to current enforcement APIs.
- Gate: compiled objects are immutable and cannot expose mutable internal collections.

### Phase 4. Implement package discovery

- Change config and datapack discovery to use `stage.toml` markers.
- Add the optional explicit `[files].include` list and deterministic section merging.
- Retain monolithic legacy discovery only for files with `[stage]` outside a package.
- Gate: helper TOMLs are never mistaken for stages, include order is deterministic, and a complete
  stage needs only `stage.toml`.

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
- Add backups, checksums, semantic comparison, and reports.
- Gate: interrupted writes leave original files intact and migrated output either fully valid or
  absent.

### Phase 17. Implement the five UI features

- Expand stage details, why panel, challenge HUD, equipment preview, and history timeline.
- Add versioned permission-filtered network DTOs.
- Gate: GUI scale, drag, scroll, hover, modal layering, blur ordering, keyboard, narrator, and
  multiplayer permission tests pass.

### Phase 18. Documentation, generated examples, and pack tests

- Rewrite complete documentation around package schema 4.
- Generate a tiny beginner stage, complete Diamond reference package, mage and knight example,
  structure weapon example, End temporary lock example, Wither hit-limit example, and migration
  tutorial.
- Add machine-verifiable examples and a pack validation Gradle task.
- Gate: every documented snippet parses, compiles, and is exercised by tests.

### Phase 19. Performance, release, and compatibility gate

- Run large synthetic pack benchmarks, dedicated-server tests, client UI tests, two-client team
  tests, restart tests, optional-mod matrix, legacy translation matrix, and artifact inspection.
- Confirm generated config hierarchy and JAR resources.
- Produce migration notes, compatibility guarantees, known limits, changelog, and rollback steps.
- Gate: Gradle build passes, all forced tests report zero failures, the server reaches ready state,
  the client reaches the tested UI, and release artifacts match documented checksums.

## 21. Test plan

### 21.1 Unit tests

- Package marker, explicit include path, include order, and section merge behavior.
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

### 21.2 Game tests

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

### 21.3 Migration tests

- Every 3.0.1 section in the mapping table.
- Mixed legacy and package stages.
- Same ID in datapack and config.
- Duplicate stage and rule IDs.
- Unknown legacy fields.
- Invalid optional provider.
- Existing saved ownership, trigger counters, grant timestamps, and session leases.
- Write migration, restart, semantic verify, and rollback.
- Linux case-sensitive and Windows-style path behavior.

### 21.4 UI tests

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

### 21.5 Performance and soak tests

- Large package discovery and reload.
- Dense condition event fanout.
- Inventory churn with many contextual modifiers.
- Combat with damage and healing rolling windows.
- One hundred simulated subjects with team and server stages.
- Long-running history retention and cleanup.
- Repeated script failures and circuit breaking.
- Reload while structure and challenge sessions are active.

## 22. Documentation deliverables

- A schema 4 overview for beginners.
- A one-file first-stage tutorial.
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
- UI authoring and spoiler guide.
- Performance tuning and diagnostics guide.
- Troubleshooting guide based on exact validation codes.
- A generated Diamond reference package linked from the main documentation and from the default
  generated stage.

Every documentation example must be stored as a test resource or generated from a tested source.
Examples that merely look correct are not enough.

## 23. Acceptance scenarios

### 23.1 Structure weapon rules

- Inside a configured structure, all weapons except swords are denied.
- A second higher-priority prefix entry allows one named bow.
- On structure exit, the temporary rule ends without changing permanent stage ownership.
- The why panel identifies both the broad lock and the winning exception.

### 23.2 Stronghold and End progression

- Mage blocks Stronghold entry through a normal rule.
- Killing the Wither grants End Fight through a lifecycle grant.
- End Fight's higher-priority allow rule permits Stronghold entry.
- Entering the End activates temporary rules that deny jump, elytra, and a diamond pickaxe.
- Leaving the End or ending its session removes those temporary rules.

### 23.3 Death and health regression

- A trial stage grants after the configured requirements.
- Death revokes it immediately when the cause filter matches.
- Losing 40 health within 30 seconds also revokes it.
- Healing does not reduce accumulated health lost unless the author configures net-health mode.
- Cooldown prevents immediate regrant oscillation.

### 23.4 Mage and knight cross-use

- Both classes can hold and use the other's equipment.
- Foreign equipment receives configured damage, speed, spell-power, mana, cooldown, or durability
  changes.
- Training or mastery stages reduce or remove the penalty through higher-priority modifiers.
- Tooltips preview the effective penalty before combat.

### 23.5 Boss hit challenge

- Combat with the selected boss opens a challenge session.
- Only configured hit sources consume the hit budget.
- Dodged or zero-damage hits follow the authored policy.
- Failure runs configured revoke and message actions.
- Success grants the configured stage and records history.
- Disconnect and boss despawn follow the configured session policy.

### 23.6 Legacy pack

- An untouched 3.0.1 config folder loads.
- Effective locks, triggers, revokes, attributes, rewards, and UI match the compatibility fixtures.
- The migration plan shows exact output without writing.
- Written packages validate semantically against their originals.
- Rollback restores the original checksummed files.

## 24. Definition of done

The rehaul is complete only when all of the following are true:

- Package stages and untouched 3.0.1 stages can coexist.
- Random helper TOMLs are not parsed as stages.
- The normal package needs only `stage.toml`.
- Grant and revoke use the same condition registry and complete condition tree.
- Death, health gained, health lost, damage, and hit conditions work with documented persistence.
- Entry priority works through the simple category lookup table and the requested compact
  shorthand without changing ordinary prefix lists.
- Missing entry priority correctly inherits category, stage, then global priority through the
  documented cascade.
- Contextual item attributes, buffs, debuffs, and transforms reconcile without visual or position
  snapping.
- The generic budget engine supports the boss hit-limit scenario.
- All ten additional engine feature groups and all five UI-only feature groups pass their gates.
- Config, datapack, Java, KubeJS, command, and UI surfaces describe the same canonical rules.
- Every old schema section has a tested translation.
- Failed parsing, compilation, migration, or reload never replaces a valid runtime snapshot.
- Documentation examples are executable test fixtures.
- The forced test report has zero failures and zero skipped required tests.
- Gradle build, dedicated server, client UI, multiplayer, restart, optional integration, migration,
  performance, and JAR inspection gates pass.

## 25. Recommended first implementation step after approval

Begin with Phase 1 and Phase 2 only. Freeze the current behavior in golden fixtures, define schema
4 source records with provenance, and publish tested examples for the one-file package. Do not
replace enforcement, triggers, revokes, or attributes until the translator and compiled model can
prove equivalent behavior. This preserves the working 3.0.1 branch while giving every later phase
a stable contract.
