# ProgressiveStages 3.0.1 Schema 4 and Editor Guide

This is the practical guide for the rehaul described in `ProgressiveStagesREAHUL.md`. It explains
the three file package, localhost editor, selectors, priorities, temporary rules, conditions,
lifecycle rules, modifiers, challenges, extension APIs, migration, synchronization, persistence,
security, testing, and recovery.

If you are brand new, use the editor tutorial first. You do not need to edit TOML to make a stage.
If you prefer files, copy the tested [starter package](examples/rehaul/starter).

## 1. The simple mental model

A stage package is one folder with three files:

```text
config/
└── progressivestages/
    ├── progressivestages.toml
    └── stages/
        └── my_pack/
            └── iron_age/
                ├── stage.toml
                ├── rules.toml
                └── progression.toml
```

- `stage.toml` answers what the stage is and how it looks.
- `rules.toml` answers what is locked, allowed, changed, or temporarily restricted.
- `progression.toml` answers how the stage is granted, revoked, challenged, rewarded, and stored.

The presence of `stage.toml` marks the folder as a package. A random helper TOML is not treated as
a stage. Old one file stages still load. Schema 4 packages and old 3.0.1 stages may coexist.

## 2. Make a stage without touching a file

This workflow works in an integrated single-player world and on a dedicated server.

1. Join with the normal ProgressiveStages client installed.
2. Make sure your account has operator permission level 3 or higher.
3. Run `/pstages editor`.
4. Your Minecraft client starts a temporary web server on `127.0.0.1` and opens your browser.
5. Click the gold plus button beside `Stages`.
6. Enter the friendly name `Iron Age`. Do not type `pack:` or write a file name. The editor turns
   it into `pack:iron_age` and creates the complete three-file package. Expand `Pack name` only when
   you want a namespace other than `pack`.
7. Select the new stage card. The easy builder shows `Stage details`, `Rules`, `Progression`, and
   `More stage features`. It never makes the three backing files part of the normal workflow.
8. Fill in the name, description, icon, required stages, ownership scope, map category, color,
   frame, reveal policy, and advancement background. Use `Browse` to choose an icon from the live
   item registry.
9. Click `Add rule`. Choose Items, Blocks, Fluids, Entities, Abilities, or any other supported
   category. The next menu contains only actions valid for that category. Choose exact ID, whole
   mod, tag, or name matching. Search results contain only the selected registry type, and `Only
   show this mod` narrows a large modpack catalog before a target is selected.
10. Choose Lock, Deny, Allow, Unlock, Replace, or viewer-only presentation. Set priority, JEI and
    EMI visibility, an optional higher-priority exception, and an optional live, timed, session,
    latched, or scheduled condition. The editor translates the card to `[[rules]]` or
    `[[temporary_rules]]` TOML. Drag rule cards to keep the advanced rule order readable.
11. Click `Add progression` to grant or revoke the stage from a kill, mined block, crafted item,
    advancement, dimension, structure, KubeJS event, or another listed condition. Select count,
    repeat policy, player/team/server scope, priority, and cooldown. Rewards and costs have their
    own guided forms.
12. Use `Stage graph` to drag nodes around the same dependency map players see. The graph is
    contained in a scrollable canvas and grows with distant nodes instead of overflowing the page.
13. Use `TOML source` only when you want direct control. `stage.toml`, `rules.toml`, and
    `progression.toml` remain available as separate advanced tabs, and unknown extension fields are
    preserved.
14. Click `Check my work`. Fix every red error.
15. Click `Review and apply`. Read the semantic file diff.
16. Confirm apply. The server writes one transaction, reloads once, and synchronizes connected
    clients. A later player receives the same compiled revision during login.

Nothing is live before step 16. The editor owns a server-side draft with a revision number. Undo,
redo, source mode, validation, graph view, registry search, stage create, duplicate, rename, move,
archive, restore, import, export, delete, collaboration, simulation, apply, backup, audit, and
rollback all operate on that draft. Archived and migration-backup folders are ignored by package
discovery, so a saved copy can never silently become a second live stage.

The gold interface is stage-first rather than file-first. The left side contains one card per
stage, not one row per TOML file. Empty sections say that nothing is currently active and provide a
single plus button. Create, duplicate, rename, move, import, collaborator, rule, and progression
forms all open inside the editor page rather than using browser prompt boxes.

The Settings workspace is generated from the running server's entire main configuration spec. Each
control includes its type, default, help text, and restart requirement. Stage forms are generated
from the same schema registry used by validation. A Java or KubeJS extension with metadata appears
in the extension inspector with its registered ID and typed arguments, so an extension does not
need a hardcoded editor panel.

Useful recovery commands:

```text
/pstages editor status
/pstages editor sessions
/pstages editor drafts
/pstages editor resume <draft_uuid>
/pstages editor discard <draft_uuid> confirm
/pstages editor revoke <session_uuid>
```

In single player, the world owner must have operator permission. If commands are disabled, open the
world to LAN with cheats enabled for that session or enable commands through the normal Minecraft
world settings. The HTTP bridge still binds only to `127.0.0.1` on the computer running the client.

## 3. Make a stage with three files

Run this as an operator:

```text
/pstages package scaffold my_pack:iron_age
```

It creates the three files under `config/progressivestages/stages/my_pack/iron_age/`. Edit them,
then run:

```text
/pstages validate my_pack:iron_age
/pstages reload diff
/pstages reload
```

A minimal `stage.toml` is:

```toml
[schema]
version = 4

[stage]
id = "my_pack:iron_age"
display_name = "Iron Age"
description = "Iron tools and basic machines"
icon = "minecraft:iron_pickaxe"
dependencies = []
priority = 0
scope = "team"
```

A minimal `rules.toml` is:

```toml
[items]
locked = ["tag:c:ingots/iron", "name:*iron_pickaxe"]
allowed = []
```

A minimal `progression.toml` is:

```toml
[[grants]]
id = "my_pack:mine_iron"
repeat = "once"
condition = { type = "mine", id = "minecraft:iron_ore", count = 1 }
```

## 4. Selectors and autocomplete

Every compatible category uses the same small selector vocabulary:

| Form | Meaning | Example |
|---|---|---|
| Exact ID | One registry entry | `minecraft:diamond` |
| `id:` | Explicit exact ID | `id:minecraft:diamond` |
| `mod:` | Every matching namespace or loaded mod entry | `mod:create` |
| `tag:` | Every entry in a registry tag | `tag:c:ingots` |
| `#` | Short tag spelling | `#c:ingots` |
| `name:` | Case insensitive wildcard or name match | `name:*sword` |
| Extension | A registered selector matcher | Defined by Java or KubeJS metadata |

The editor searches catalogs built by the running server. It pages results instead of sending the
entire modpack registry. Catalogs include loaded mods, static and dynamic registries, registry
tags, recipes, advancements, loot tables, predicates, functions, scoreboard objectives, stages,
KubeJS registrations, and extension metadata. A catalog revision changes after a complete reload;
stale cursors are rejected rather than mixing old and new results.

`mod:create` is about a loaded mod or namespace. `minecraft:diamond` is an exact item. They are not
interchangeable. Use the editor preview or `/pstages explain target items.use minecraft:diamond`
when a broad selector behaves differently than expected.

## 5. Priority, exclusions, global allows, and viewer policy

The priority cascade is:

```text
entry priority
then advanced rule priority
then category priority
then stage priority
then global priority
```

Higher priority wins. Equal priority follows the configured deterministic tie policy. The default
safe behavior favors a denial rather than accidentally opening gated content. Stable rule ID and
specificity complete arbitration so a reload cannot randomly change the winner.

You may put priority beside a selector:

```toml
[items]
locked = ["mod:create", "minecraft:diamond|priority=600"]
priority = 100

[items.priorities]
"mod:create" = 125
```

A local exclusion removes only its parent broad lock. It does not erase another stage's lock:

```toml
[[rules]]
id = "my_pack:all_weapons"
effect = "deny"
priority = 500
targets.items = ["tag:c:weapons"]

[[rules.exceptions]]
effect = "exclude"
priority = 600
targets.items = ["tag:c:swords"]
```

A global allow may defeat every lower-priority lock in the same action context:

```toml
[[rules.exceptions]]
effect = "allow"
priority = 700
targets.items = ["minecraft:bow"]
```

EMI and JEI presentation is resolved separately from enforcement. Showing an item in JEI does not
make it usable on the server:

```toml
[items.presentation."minecraft:diamond"]
viewer = "show"
priority = 700
```

Viewer choices are inherit, show, hide, and overlay where supported. Broad mod hiding and exact
higher-priority showing use the same resolver as locks.

## 6. Rule categories

Schema 4 exposes these categories through the editor and server schemas:

- Items: use, pickup, mouse pickup, hotbar, inventory, attack, and drop.
- Blocks: place, break, and interact.
- Fluids: pickup, placement, flow, submersion, and viewer presentation.
- Recipes: manual crafting, automation, result items, and viewer presentation.
- Crops: plant, grow, bonemeal, and harvest.
- Dimensions: portals, entry, and teleport.
- Enchantments: table, anvil, trades, inventory stripping, and maximum levels.
- Entities: attack, interact, mount, and replacement targets.
- Loot: chest, fishing, archaeology, mob, block, and Lootr-backed rolls.
- Mobs: spawn denial and replacement.
- Pets: tame, breed, command, and ride.
- Screens: block menus and held-item menus.
- Trades and professions: display, purchase, and profession access.
- Advancements: screen visibility and toast presentation.
- Structures and regions: entry, chest, break, place, explosion, and spawn behavior.
- Curios: equip and retain by slot.
- Ores: visual substitution and guarded drops.
- Beacon and brewing: effect application, brewing, and result taking.
- Abilities: jump, elytra, sprint, swim, climb, and registered abilities.
- Interactions: item on block, item on entity, and block right click.

The ordinary `[items]`, `[blocks]`, and similar lists remain the recommended easy syntax. Use
`[[rules]]` only when you need explicit actions, parent relationships, conditions, or mixed targets.

## 7. One condition language

Grant, revoke, temporary rule, modifier, challenge, action guard, and simulation use the same
condition compiler. Leaf conditions may be nested with all, any, not, comparison, reference,
count, and ordered sequence nodes.

Common built-ins include stage ownership, dimension, biome, structure, weather, health, food,
altitude, XP, level, play time, team size, scoreboard, counters, death, respawn, health gained,
health lost, damage taken, damage dealt, hits taken, hits dealt, no damage duration, kill, mine,
craft, pickup, item possession, advancement, effect, breed, fish, sleep, tame, ride, structure
leave, combat session, boss session, region session, custom session, script, and KubeJS.

One leaf:

```toml
condition = { type = "health_lost", amount = 40 }
```

All conditions:

```toml
condition = { all = [
  { type = "stage_owned", id = "my_pack:mage" },
  { type = "dimension", id = "minecraft:the_end" },
  { type = "scoreboard", id = "trial_score", minimum = 10 }
] }
```

Any condition:

```toml
condition = { any = [
  { type = "kill", id = "minecraft:wither", count = 1 },
  { type = "kubejs", id = "my_pack:admin_approved" }
] }
```

Conditions declare event interests. The runtime evaluates affected rules after relevant events or
explicit invalidation rather than evaluating arbitrary script expressions every tick.

## 8. Temporary rules from any condition

Temporary rule lifetimes are:

- `live`: active while the condition is true.
- `duration`: starts for a configured duration after a matching edge.
- `latched`: stays active until its reset condition matches.
- `session`: follows a combat, structure, region, boss, or custom session.
- `schedule`: follows its authored time condition.
- `permanent`: active while the owning stage is effective.

Example:

```toml
[[temporary_rules]]
id = "my_pack:end_tools"
effect = "deny"
lifetime = "live"
priority = 1000
action = "use"
targets.items = ["minecraft:elytra", "minecraft:diamond_pickaxe"]
while = { type = "dimension", id = "minecraft:the_end" }
```

The same form can use health, damage, stage, scoreboard, structure, combat, boss, KubeJS, or any
registered condition. There is no dimension-only temporary lock path.

## 9. Grants, revokes, and stable transitions

Grants and revokes are symmetric:

```toml
[[grants]]
id = "my_pack:grant_trial"
repeat = "once"
cooldown = "30s"
debounce = "1s"
condition = { type = "kill", id = "minecraft:wither", count = 1 }

[[revokes]]
id = "my_pack:revoke_trial"
repeat = "edge"
grace = "2s"
condition = { type = "death", count = 1 }
```

Repeat modes include once, edge, repeat, and manual. Cooldown, debounce, grace, minimum active,
minimum inactive, transition budgets, dependency checks, and cycle detection prevent oscillation.
Each evaluation is a transaction. A failed required action restores the subject snapshot and runs
the configured failure or compensation policy.

Inspect and control lifecycle rules with:

```text
/pstages lifecycle progress my_pack:trial
/pstages lifecycle arm my_pack:manual_rule
/pstages lifecycle reset my_pack:manual_rule
/pstages history
```

## 10. Contextual modifiers, affinity, and equipment preview

Modifiers may affect attributes, effects, and numeric behavior only in selected item contexts:
main hand, off hand, either hand, selected hotbar, hotbar, inventory, armor equipment, Curios,
use, or attack.

```toml
[[item_modifiers]]
id = "my_pack:mage_heavy_weapon_penalty"
items = ["tag:c:tools", "name:*hammer"]
while_holding = true
with_stages = ["my_pack:mage"]
aggregation = "once"
priority = 400

[[item_modifiers.attributes]]
id = "minecraft:generic.attack_speed"
amount = -0.35
operation = "add_multiplied_total"

[[item_modifiers.transforms]]
type = "progressivestages:outgoing_damage"
multiply = 0.75
```

Aggregation and stacking are explicit and bounded. Stable modifier IDs prevent duplicate stacking.
Reconciliation clamps health safely when maximum health falls. It never moves the player, changes
camera position, or recreates the player entity, which prevents the old ground-clipping snap.

Profiles add proficiency thresholds so training stages or variables can reduce a penalty. The
details screen evaluates the item in the player's main hand and reports its effective lock,
modifier, multiplier, attribute deltas, transforms, affinity level, and required stage. See the
tested [mage package](examples/rehaul/mage) and [knight package](examples/rehaul/knight).

## 11. Generic challenges and budgets

Challenges contain a start condition, optional ordered steps, measures and budgets, success and
failure conditions, retry policy, timeout, actions, persistence policy, and HUD presentation.

```toml
[[challenges]]
id = "my_pack:wither_hit_trial"
title = "Wither hit trial"
start_when = { type = "boss_session", id = "minecraft:wither" }
success_when = { type = "kill", id = "minecraft:wither" }
max_hits = 2
boss = "minecraft:wither"
timeout = "10m"
retries = 3

[challenges.hud]
enabled = true
placement = "top_right"
scale = 1.0
color = "#ffcc55"
icon = "minecraft:nether_star"
animation = "pulse"
compact = false
hide_when_inactive = true
values_secret = false
```

`max_hits` is convenient syntax compiled into the generic budget engine. The engine is not
hardcoded to the Wither. Registered measures may budget hits, damage, healing, time, deaths,
movement, items, currency, or extension events. Inspect sessions with `/pstages challenge list`
and `/pstages challenge inspect <id>`.

The live HUD is server driven and shows the active title, ordered step, attempt, budget values,
failure state, and a local countdown derived from the server start time. Placement supports top,
top left, top right, center, bottom, bottom left, and bottom right. `pulse` and `flash` animate the
accent; `none` is still. Secret values are replaced before they leave the server. Identical HUD
states are fingerprinted and not resent, while the countdown advances client side without packet
spam or visual flicker.

## 12. Variables, formulas, templates, and stage states

Variables declare type, scope, bounds, default, persistence, client visibility, mutation
permissions, and reset behavior. Types include integer, decimal, currency, counter, boolean, and
string. Formulas compile into a bounded AST and reject dependency cycles or non-finite results.

Templates provide typed parameters, includes, fragments, and merge policies. Include paths are
normalized and contained to approved roots. Template include cycles and unknown parameters are
validation errors.

Multi-state stages declare allowed values, ownership values, initial state, and legal transitions:

```toml
[states]
values = ["missing", "training", "owned", "completed"]
ownership_states = ["owned", "completed"]
initial = "missing"

[states.transitions]
missing = ["training"]
training = ["owned"]
owned = ["completed"]
```

## 13. Actions and failure handling

Actions are registered providers with typed arguments. Chains are atomic by default. Each action
may declare rollback, continue, retry, abort, or compensation behavior. Providers validate their
arguments during compilation. Commands and scripts never receive permission merely because their
ID appears in a catalog.

```toml
actions = [
  { type = "progressivestages:message", message = "Trial complete" },
  { type = "progressivestages:add_variable", variable = "my_pack:tokens", amount = 1 }
]
```

KubeJS action IDs are stored as IDs and arguments. Browser-authored script source is never run.

## 14. Java API

`ProgressiveStagesRehaulAPI` provides immutable compiled snapshots, catalogs, catalog paging,
condition, action, selector, challenge measure and editor schema registration, extension metadata,
selector parsing, condition compilation, decision traces, transition history, challenge access,
variables, formula evaluation, template expansion, provider discovery, and capability discovery.

```java
ProgressiveStagesRehaulAPI.registerCondition(myConditionProvider);
ProgressiveStagesRehaulAPI.registerAction(myActionProvider);
ProgressiveStagesRehaulAPI.registerSelector(mySelectorMatcher);
ProgressiveStagesRehaulAPI.registerCatalogContributor(myCatalogContributor);

var decision = ProgressiveStagesRehaulAPI.decide(
    player, "items", "use", ResourceLocation.parse("minecraft:diamond"));

double cost = ProgressiveStagesRehaulAPI.evaluateFormula("pack:spell_cost", values);
Map<String, Object> expanded = ProgressiveStagesRehaulAPI.expandTemplate(
    ResourceLocation.parse("pack:arena_rule"), arguments);
```

Registration uses namespaced IDs. Returned maps, lists, snapshots, traces, and pages are immutable.
Check `hasCapability` before using optional services. The complete typed condition and editor
metadata example is [ExampleProgressiveStagesPlugin.java](examples/rehaul/java/ExampleProgressiveStagesPlugin.java).

## 15. KubeJS API and editor metadata

KubeJS retains the original grant, revoke, query, counter, GUI, rule, trigger, tag, and category
helpers and adds condition, value, action, selector, and challenge measure registrations with
metadata. `evaluateFormula(id, values)` and `expandTemplate(id, arguments)` expose the same compiled
formula and template engines used by Java. The complete tested registration example is
[progressivestages.js](examples/rehaul/kubejs/server_scripts/progressivestages.js).

Metadata should include title, description, typed arguments, defaults, scopes, event interests,
capabilities, examples, and missing callback policy. Registrations are frozen into one extension
catalog revision after startup and reopened only at a supported script reload boundary. A missing
legacy callback follows its declared false, zero, no-op, or reject policy instead of crashing the
server.

## 16. Client authority and automatic synchronization

Stage TOML lives on the server. Clients receive a permission-filtered compiled display snapshot:

1. Server compiles a configuration revision.
2. Client-visible data is encoded once, compressed, split into bounded chunks, and checksummed.
3. The server sends a manifest and chunks.
4. The client assembles a temporary candidate.
5. Checksum and protocol must match before one atomic activation.
6. The client acknowledges the active revision.

Apply sends the current revision to connected clients. Login sends the full current revision to a
future client. Enforcement never trusts the client snapshot. Missing or delayed display packets do
not unlock content.

After a client acknowledges a base revision, the server may send a bounded prefix and suffix delta
when that compressed delta is smaller than a full snapshot. The client requires the exact base,
checks every length and chunk boundary, rejects decompression beyond sixteen MiB, reconstructs the
candidate, verifies the final checksum, and only then swaps caches. A missing or wrong base asks for
a full snapshot instead of trying to apply unsafe data.

## 17. Player stage screen

Open the screen with `/pstages`, `/stage`, `/stages`, the keybind, or the lock button beside the
inventory recipe-book button. It follows the vanilla advancement screen interaction model:

- Drag the canvas with the mouse.
- Scroll with the wheel.
- Hover nodes for descriptions and tooltips.
- Click a node for a modal inspector.
- Inspect trigger progress, active challenges, modifier and affinity preview, recent winning rules,
  and transition history.
- Use the stage-defined background texture or the vanilla fallback.

The stage layer renders above menu background blur. The modal details layer renders above every
stage icon, so neighboring icons cannot overlap it.

## 18. Migration

Nothing is rewritten automatically. Use:

```text
/pstages migrate scan
/pstages migrate plan all
/pstages migrate write my_pack:old_stage confirm
/pstages migrate verify
/pstages migrate rollback <migration_id> confirm
```

Scan is read only. Plan shows the target files. Write creates checksummed backups, writes a
temporary package, validates it, atomically moves it, and compares the compiled semantics. A failed
or interrupted write leaves the original active. Rollback verifies and restores the backup.

## 19. Persistence and scopes

Stage ownership keeps its established storage. Schema 4 persistence additionally stores lifecycle
progress and cooldowns, sequence state, rolling counters, temporary rule state, challenge sessions
and budgets, persistent variables, multi-state stages, transaction IDs, and bounded transition
history in overworld saved data.

State objects declare player, team, server, or custom scope. Scope is part of a key, not an implied
UUID conversion. Runtime state is captured periodically, on logout, before shutdown, and before a
runtime reset. Session-only policies may intentionally end on their configured boundary.

## 20. Localhost editor security

- Only permission level 3 operators may open a session in single player or on a dedicated server.
- The HTTP listener binds to `127.0.0.1` on a random port on the operator's own client.
- The dedicated server does not expose an HTTP port.
- A 256-bit secret travels through the URL fragment and is immediately removed from browser
  history. Only its hash is stored server-side.
- Host, Origin, method, authorization, rate, body size, session owner, permission, expiry, draft
  revision, and configuration revision are checked.
- Assets use CSP, frame denial, MIME sniffing denial, no referrer, no store, restricted browser
  permissions, and no external runtime resources.
- Server paths, secrets, live registry objects, reflection handles, and arbitrary HTML never enter
  the browser protocol.
- Raw TOML paths are normalized, contained, and limited to supported TOML locations.
- Logout, permission loss, revocation, server switch, and shutdown invalidate access.

## 21. Commands

```text
/pstages editor [status|sessions|drafts|resume|discard|revoke]
/pstages package list
/pstages package inspect <stage>
/pstages package scaffold <stage_id>
/pstages validate [stage|all]
/pstages reload diff
/pstages explain stage <stage> [player]
/pstages explain target <context> <target> [player]
/pstages simulate event <event> [player]
/pstages lifecycle progress <stage> [player]
/pstages lifecycle arm <rule> [player]
/pstages lifecycle reset <rule> [player]
/pstages challenge list [player]
/pstages challenge inspect <challenge> [player]
/pstages challenge reset <challenge> <player>
/pstages history [player]
/pstages rollback <transaction_id> confirm
/pstages migrate scan|plan|write|verify|rollback
```

Mutation commands require permission and explicit confirmation where data may be written or
restored. Simulation does not change stages, counters, or live rules.

## 22. Performance and diagnostics

Selectors compile to exact, namespace, tag, token, and bounded fuzzy indexes. Catalog search keeps
a bounded immutable index cache and has an automated one hundred thousand entry query gate.
Conditions publish event interests. Catalog queries are paged and revisioned. Inventory reconciliation is coalesced.
Counter windows retain bounded buckets. Decision and transition histories are bounded. Client
snapshots are compressed, chunked, and reused by revision. Provider failures are isolated and
reported instead of publishing a partial catalog.

Use `/pstages validate`, `/pstages explain`, `/pstages history`, `/pstages package inspect`, editor
validation, and the priority analyzer before increasing limits. Slow rules are reported, not
silently disabled.

## 23. Troubleshooting by error code

| Code or message | Meaning | Fix |
|---|---|---|
| `draft_conflict` | Another operation changed the draft revision | Reload the editor view and repeat the intended edit |
| `configuration_conflict` | Live config changed after the draft opened | Review live changes, resume or create a fresh draft, and merge intentionally |
| `validation_failed` | Candidate TOML or graph is invalid | Open Validate and fix every listed path |
| `confirmation_required` | A write or rollback was not confirmed | Review first, then use the explicit confirmation control |
| `unauthorized` | Session, owner, permission, secret, or expiry check failed | Run `/pstages editor` again as an operator |
| `request_too_large` | Browser mutation exceeded the bounded request | Split the source edit into smaller changes |
| `reload_failed` | Candidate failed live compilation | Read exact reload errors. The prior snapshot remains active |
| `stale catalog revision` | Datapack, KubeJS, or config reload replaced search data | Repeat the search against the new revision |
| `transition_budget` | One lifecycle pass tried too many changes | Break the cycle or split the transition chain |
| `dependency_cycle` | Stage or formula dependency loops back | Remove one edge in the reported cycle |

## 24. Tested examples

- [Starter](examples/rehaul/starter): smallest package.
- [Diamond](examples/rehaul/diamond): priority, presentation, unlock, temporary rule, modifier,
  grant, revoke, variable, and stage state reference.
- [Mage](examples/rehaul/mage): cross-equipment penalty and affinity profile.
- [Knight](examples/rehaul/knight): registered magic-item spell power and cooldown penalties with a
  proficiency escape hatch.
- [Structure weapons](examples/rehaul/structure_weapons): session lock, sword exclusion, and bow
  global allow.
- [End Fight](examples/rehaul/end_fight): Wither grant, Stronghold allow, and End restrictions.
- [Wither trial](examples/rehaul/wither_trial): generic hit budget and challenge HUD.
- [KubeJS](examples/rehaul/kubejs): typed callback metadata, lifecycle callback, temporary allow,
  and script action.
- [Java extension](examples/rehaul/java/ExampleProgressiveStagesPlugin.java): typed condition
  provider and generated editor metadata.
- [Migration](examples/migration): machine-compared legacy before and schema 4 after packages.
- [Legacy Diamond file](examples/reference/diamond_stage.toml): exhaustive old one-file schema.

`RehaulExamplesTest` discovers, parses, compiles, and asserts the engine behavior of every published
schema 4 package. `LegacyCompatibilityBaselineTest` protects the old examples.

## 25. The thirty feature groups

1. Ordered and timed condition sequences use persistent sequence state.
2. Reusable challenges use registered measures and generic budgets.
3. Affinity, proficiency, and mastery use profiles and thresholds.
4. Templates use typed parameters, inheritance, includes, and merge policy.
5. Multi-state stages use explicit transitions and ownership states.
6. Declarative actions use atomic chains, retries, failure, and compensation.
7. Variables, currencies, counters, and formulas use bounded typed storage and AST evaluation.
8. Stability controls use cooldown, debounce, grace, minimum durations, and transition budgets.
9. Public registries provide conditions, actions, selectors, catalogs, measures, and schemas.
10. Explain, simulation, history, and rollback exist in commands, API, editor, and player UI.
11. The private loopback editor bridge binds only to the operator client.
12. Stage CRUD creates, duplicates, renames, moves, archives, restores, imports, exports, and
    deletes complete packages in drafts.
13. Schema metadata generates a fallback form for every registered field.
14. The registry explorer pages the running server's authoritative catalogs.
15. The stage graph provides a visual package and dependency workspace.
16. Grant and revoke use the same visual and compiled condition model.
17. The lock matrix exposes every established lock category and action.
18. The modifier designer covers attributes, effects, transforms, contexts, affinity, and stacking.
19. The challenge designer covers measures, budgets, sequences, retries, actions, and HUD.
20. The priority analyzer distinguishes local exclusion from global allow.
21. Live validation and simulation run against a complete server candidate.
22. Server drafts provide revisions, undo, redo, autosave, and restart recovery.
23. Review, semantic diff, atomic apply, checksummed backup, audit, and rollback form one transaction.
24. Draft owners may explicitly add or remove collaborators without sharing a secret URL.
25. Templates, migration, import, export through draft files, and source mode preserve power-user use.
26. The player inspector shows effective stage and rule data subject to visibility.
27. The why panel shows candidates, priority, selected winner, and explanation.
28. The live challenge HUD synchronizes active sessions and honors placement, scale, color, icon,
    animation, compact, hiding, and secret-value policy.
29. Equipment and affinity preview evaluates the held item and shows effective locks, modifiers,
    attributes, transforms, proficiency, and priority before use.
30. The progress history timeline shows bounded grant, revoke, state, and challenge history.

## 26. Release verification

Run:

```bash
cd editor-ui
npm ci --ignore-scripts
npm run check
npm run build
npm audit --audit-level=high
cd ..
./gradlew test --rerun-tasks --no-daemon
./gradlew clean build --no-daemon
jar tf build/libs/progressivestages-3.0.1.jar
```

The JAR must contain the mod logo, NeoForge metadata, language data, generated editor `index.html`,
`app.css`, `app.js`, and `legacy.js`. The editor dependency audit must report zero vulnerabilities.
The Gradle suite must report zero failures. The final manual gate is a dedicated server with two
clients: open the editor, apply a stage, confirm both current clients activate one revision, join a
third client, confirm it receives the same revision, then roll the transaction back.
