# Temporary and Triggered Locks Guide

This guide explains the conditional lock system in ProgressiveStages 3.0. Use it when a normal
stage gate is not enough and the answer must change because of a location, a fight, a timer, a
command, or another stage.

The complete schema reference remains in [DOCUMENTATION.md](DOCUMENTATION.md). The fully commented
stage reference is [examples/reference/diamond_stage.toml](examples/reference/diamond_stage.toml).

## The two systems that work together

There are two different meanings of trigger in ProgressiveStages.

- `[[triggers]]` earns and grants the stage that contains it. A Wither kill can permanently grant
  `end_fight`.
- `[[temporary_locks]]`, `[[temporary_unlocks]]`, `[[triggered_locks]]`, and
  `[[triggered_unlocks]]` decide whether selected content is allowed right now.

Use `[[triggers]]` for progression. Use conditional lock rules for temporary permissions and
restrictions.

## Four easy rule names

| Rule block | Effect | Active for |
|---|---|---|
| `[[temporary_locks]]` | blocks its targets | as long as its `[temporary_locks.when]` context matches |
| `[[temporary_unlocks]]` | permits its targets | as long as its `[temporary_unlocks.when]` context matches |
| `[[triggered_locks]]` | blocks its targets | a timer started by combat, attack, hurt, kill, command, or API |
| `[[triggered_unlocks]]` | permits its targets | a timer started by combat, attack, hurt, kill, command, or API |

`[[conditional_rules]]` is the advanced form. It accepts `effect = "lock"` or `"unlock"` and
`activation = "live"` or `"triggered"`. It does not add behavior beyond the four friendly names.

## Priority rules

Every matching lock and unlock enters one priority contest.

1. A normal `[items]`, `[structures]`, `[dimensions]`, or other stage gate has priority `0`.
2. A conditional rule defaults to priority `100`.
3. The matching rule with the highest number wins.
4. If a lock and unlock have the same priority, the lock wins.
5. Negative priorities are allowed. The accepted range is `-1000000` through `1000000`.

This gives a predictable chain:

```text
Normal Mage stronghold gate. Priority 0.
End Fight stronghold permission. Priority 100. Permission wins.
Special emergency stronghold lock. Priority 200. Lock wins.
```

An `except` entry only removes a target from that one rule. It does not globally whitelist the
content and it does not defeat a separate rule.

## Complete field reference

Every rule requires:

```toml
[[temporary_locks]]
id = "unique_name"

[temporary_locks.targets]
items = ["minecraft:bow"]
```

The fields on the rule table are:

| Field | Default | Meaning |
|---|---:|---|
| `id` | required | unique rule name. A short id becomes `<stage namespace>:<stage path>/<id>` |
| `priority` | `100` | higher matching number wins |
| `stage_state` | `"owned"` | whether the file's stage must be `owned`, `missing`, or can be `always` |
| `trigger` | `"manual"` | only for triggered rules. `manual`, `combat`, `attack`, `hurt`, or `kill` |
| `trigger_entities` | `[]` | optional entity selectors that narrow the combat event |
| `duration` | `"10s"` | friendly timed duration such as `15s`, `2m`, or `1h` |
| `duration_seconds` | `10` | numeric alternative to `duration` |
| `refresh_duration` | `true` | matching events restart the timer when true |

`active_when` is accepted as an alias for `stage_state`.

### Stage state

- `stage_state = "owned"` means the rule can act only while the player owns the stage containing
  the rule. This is the normal choice.
- `stage_state = "missing"` means the rule acts only while the player does not own that stage.
- `stage_state = "always"` means ownership of the containing stage is ignored. Context and timer
  requirements still apply.

### Target categories

Put target lists in the rule's `.targets` table. Put exclusions in its `.except` table.

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

[temporary_locks.except]
items = []
blocks = []
fluids = []
entities = []
recipes = []
dimensions = []
structures = []
abilities = []
```

Items, blocks, fluids, and entities accept the complete selector language:

- `all:*` for every registered entry in that category.
- `id:minecraft:bow` or `minecraft:bow` for one exact id.
- `mod:examplemod` for a namespace.
- `tag:mypack:weapons` or `#mypack:weapons` for a tag.
- `name:sword` for ids containing a word.

Recipes, dimensions, structures, and abilities accept `all:*`, `id:`, an exact id without the
prefix, `mod:`, and `name:`. Tags are not accepted for those four categories.

`all:*` never crosses category boundaries. In an entity target it means every registered entity.
In a structure target it means every registered structure. Put narrower entries in the rule's
`.except` table or create a higher priority allow rule to keep selected targets available.

Supported ability ids are `jump`, `elytra`, `sprint`, `swim`, and `climb`.

Conditional item and block targets still obey the global enforcement toggles and the containing
stage's `[enforcement]` overrides. For a weapons rule that should block use without ejecting carried
weapons, keep item use enabled and disable pickup or inventory enforcement for that stage:

```toml
[enforcement]
block_item_use = true
block_item_pickup = false
block_item_inventory = false
```

Those overrides affect every item rule owned by that stage, so split policies into separate stage
files when they need different enforcement surfaces.

### Live context

The optional `.when` table can contain:

```toml
[temporary_locks.when]
mode = "all_of"
dimensions = ["minecraft:the_end"]
structures = ["minecraft:stronghold"]
biomes = ["#minecraft:is_forest"]
min_y = 0
max_y = 100
min_health = 1.0
max_health = 20.0
stages = ["end_fight"]
missing_stages = ["escaped_end"]
effects = ["minecraft:darkness"]
sneaking = false
sprinting = false
swimming = false
riding = false
on_ground = true
script = "my_kube_condition"
```

Do not copy fields you do not need. `mode = "all_of"` requires every supplied context group.
`mode = "any_of"` activates when at least one supplied context group matches. `all` and `any` are
accepted aliases.

Structure checks use the generated structure's bounding box. A structure id in `when.structures`
does not mean the whole chunk.

The `script` field calls a KubeJS predicate registered with
`ProgressiveStages.condition('my_kube_condition', player => trueOrFalse)`.

## Full requested progression example

This is the Mage, Wither, Stronghold, and End chain. It uses two files.

### File one. `mage.toml`

The player cannot enter a Stronghold until a later rule overrides this normal priority zero gate.

```toml
[stage]
id = "mage"
display_name = "Mage"
description = "The stage that normally guards Strongholds."

[structures]
locked_entry = ["minecraft:stronghold"]
```

### File two. `end_fight.toml`

Killing the Wither grants End Fight. Owning End Fight overrides the Mage Stronghold gate. While the
player is in the End, a higher-priority rule blocks jumping, elytra flight, and the diamond pickaxe.

```toml
[stage]
id = "end_fight"
display_name = "End Fight"
description = "Defeat the Wither to prepare for the End."

[[triggers]]
type = "kill"
target = "minecraft:wither"
count = 1

[[temporary_unlocks]]
id = "stronghold_access"
priority = 100
stage_state = "owned"

[temporary_unlocks.targets]
structures = ["minecraft:stronghold"]

[[temporary_locks]]
id = "end_battle_rules"
priority = 200
stage_state = "owned"

[temporary_locks.when]
dimensions = ["minecraft:the_end"]

[temporary_locks.targets]
items = ["minecraft:diamond_pickaxe"]
abilities = ["jump", "elytra"]
```

The End lock does not require a timer. It starts immediately when an End Fight player is in the End
and stops immediately when the player leaves the End.

To gate End dimension travel too, put the normal gate in `mage.toml`:

```toml
[dimensions]
locked = ["minecraft:the_end"]
```

Then extend `stronghold_access`:

```toml
[temporary_unlocks.targets]
structures = ["minecraft:stronghold"]
dimensions = ["minecraft:the_end"]
```

## Lock all weapons except swords inside a structure

Minecraft does not have one universal tag containing every modded weapon. Create a datapack item tag
named `mypack:weapons`, and let other mods or your pack add bows, guns, axes, wands, and other weapons
to it. Then use:

```toml
[[temporary_locks]]
id = "stronghold_swords_only"
priority = 100
stage_state = "owned"

[temporary_locks.when]
structures = ["minecraft:stronghold"]

[temporary_locks.targets]
items = ["tag:mypack:weapons"]

[temporary_locks.except]
items = ["tag:minecraft:swords"]
```

This rule blocks every tagged weapon except a tagged sword only while the player is inside a
Stronghold.

## Permit all weapons except bows inside a structure

If a normal stage already locks every item in `mypack:weapons`, this rule temporarily overrides that
gate for everything except bows:

```toml
[[temporary_unlocks]]
id = "arena_weapon_permission"
priority = 100

[temporary_unlocks.when]
structures = ["mypack:trial_arena"]

[temporary_unlocks.targets]
items = ["tag:mypack:weapons"]

[temporary_unlocks.except]
items = ["tag:minecraft:arrows", "minecraft:bow", "minecraft:crossbow"]
```

The bow remains controlled by the original lock because this permission rule excludes it. If weapons
are normally allowed everywhere, use a `temporary_locks` rule that targets only bow and crossbow
instead.

## Apply a restriction during combat with a mob

This rule begins when the player damages or is damaged by the Ender Dragon. Every matching combat
event refreshes its fifteen second timer. During that timer the player may use swords but no other
tagged weapon.

```toml
[[triggered_locks]]
id = "dragon_swords_only"
priority = 200
trigger = "combat"
trigger_entities = ["minecraft:ender_dragon"]
duration = "15s"
refresh_duration = true

[triggered_locks.targets]
items = ["tag:mypack:weapons"]

[triggered_locks.except]
items = ["tag:minecraft:swords"]
```

Choose `attack` when only damage dealt by the player should start the timer. Choose `hurt` when only
damage received should start it. Choose `kill` when the timer should start after the entity dies.

Timed rule state is runtime state. It expires normally and is cleared when the player logs out or the
server stops. Use a real stage and `[[triggers]]` when the permission must survive a restart.

## Command controls

`/pstages` is the supported short command root. ProgressiveStages does not register `/ps`.

```text
/pstages rule info <rule>
/pstages rule list
/pstages rule list <player>
/pstages rule activate <player> <rule>
/pstages rule activate <player> <rule> <seconds>
/pstages rule clear <player> <rule>
/pstages rule clearall <player>
```

`activate` only starts a triggered rule. Omitting seconds uses the rule's configured duration. The
rule still must satisfy its `stage_state` and current `.when` context. Rule suggestions show complete
ids. A short id also works when its suffix is unique across the loaded pack.

## KubeJS controls

```javascript
// Use the duration from the TOML rule.
ProgressiveStages.activateRule(player, 'end_fight/manual_permission')

// Override the duration with sixty seconds.
ProgressiveStages.activateRule(player, 'end_fight/manual_permission', 60)

ProgressiveStages.clearRule(player, 'end_fight/manual_permission')
ProgressiveStages.clearRules(player)

const secondsByRule = ProgressiveStages.activeRules(player)
const everyRuleId = ProgressiveStages.ruleIds()
const definition = ProgressiveStages.ruleInfo('end_fight/manual_permission')
```

All mutations must run on the logical server. `activeRules` returns rule ids mapped to whole seconds
remaining.

## Java API controls

```java
boolean started = ProgressiveStagesAPI.activateConditionalRule(player,
    "progressivestages:end_fight/manual_permission", 60_000L);

boolean cleared = ProgressiveStagesAPI.clearConditionalRule(player,
    "progressivestages:end_fight/manual_permission");

int clearedCount = ProgressiveStagesAPI.clearConditionalRules(player);

Map<ResourceLocation, Long> millisecondsRemaining =
    ProgressiveStagesAPI.getActiveConditionalRules(player);

Set<ResourceLocation> availableRules = ProgressiveStagesAPI.getConditionalRuleIds();

Optional<ConditionalRule> definition =
    ProgressiveStagesAPI.getConditionalRule("progressivestages:end_fight/manual_permission");
```

## Safe authoring checklist

1. Give every rule a unique `id`.
2. Put every list in brackets, even when it contains one value.
3. Start with priority `100`. Use `200` only for a deliberate override.
4. Use one `when` condition first. Add more after the first test works.
5. Run `/progressivestages validate`.
6. Run `/progressivestages reload`.
7. Inspect the rule with `/pstages rule info <rule>`.
8. Test both sides of the boundary. Enter and leave the structure or dimension.
9. Test a player who owns the containing stage and one who does not.
10. For triggered rules, verify activation, refresh, expiry, clear, logout, and reconnect.

## Common mistakes

- `[[temporary_locks]]` is an array of tables. Do not write `[temporary_locks]` when several rules
  are needed.
- A `.when` table belongs to the most recent rule block of the same name.
- `stage_state = "owned"` refers to the stage in the current TOML file.
- `except` only modifies its own rule.
- `[[triggers]]` grants stages. `[[triggered_locks]]` starts timed access rules. They are not
  interchangeable.
- A lower-priority unlock cannot override a higher-priority lock.
- Equal priority is intentionally safe. The lock wins.
- A triggered rule with `trigger = "manual"` does nothing until command, KubeJS, or Java activates
  it.
- A rule may parse but never match if the target tag is empty. Verify pack tags in game.
