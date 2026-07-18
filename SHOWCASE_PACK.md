# ProgressiveStages 3.0.1 Showcase Pack

ProgressiveStages creates a fifty stage showcase when `config/progressivestages/stages` is empty.
The showcase replaces the old Stone Age, Iron Age, and Diamond Age first launch examples. It never
deletes, replaces, or adds files when an installation already has a stage.

Every showcase stage is a schema 4 package:

```text
config/progressivestages/stages/<stage_name>/
├── stage.toml
├── rules.toml
└── progression.toml
```

That produces fifty stage folders and one hundred fifty TOML files. Open them through
`/pstages editor`; touching the files is optional. The pack is intentionally larger than a normal
starter pack because it demonstrates class paths, merged paths, specialist upgrades, temporary
power, world access, stage limits, automatic replacement, stacking, purchases, triggers, KubeJS,
rewards, priorities, exclusions, recipe-viewer policy, abilities, attributes, effects, drop
modifiers, challenges, variables, formulas, and custom states.

## The three beginner paths

The class tree begins with exactly three choices:

```text
Mage       Warrior       Ranger
```

They share this policy:

```toml
[stage]
slot_group = "beginner_paths"
slot_limit = 2
slot_policy = "deny"
```

`slot_group` gives related stages one shared ownership pool. `slot_limit = 2` means a team can own
any two of Mage, Warrior, and Ranger. A third beginner purchase is rejected. Change the value to
`1` for one permanent class choice, `3` for all three beginner paths, or `0` for unlimited stacking.
The editor applies the same setting to every member of a group so the group cannot become
internally contradictory.

Each beginner has three independent evolutions and three direct masteries:

```text
Mage                  Warrior                Ranger
├── Wizard            ├── Knight             ├── Marksman
│   └── Archmage      │   └── Vanguard       │   └── Deadeye
├── Warlock           ├── Berserker          ├── Beastmaster
│   └── Necromancer   │   └── Warlord        │   └── Wildspeaker
└── Cleric            └── Paladin            └── Scout
    └── Oracle            └── Templar            └── Pathfinder
```

Owning a beginner does not force one evolution. A Mage may earn Wizard, Warlock, and Cleric. The
graph also contains hybrid stages that require two branches. Spellblade, for example, requires both
Wizard and Knight, so the line visibly joins the Mage and Warrior families.

## Slot policies and buff stacking

The generated pack demonstrates three different configurations.

| Group | Members | Limit | Policy | Result |
|---|---:|---:|---|---|
| `beginner_paths` | 3 | 2 | `deny` | Keep any two beginner paths. A third is refused. |
| `engineering_tiers` | 6 | 0 | `deny` | Every owned engineering buff remains active and stacks. |
| `mining_modes` | 4 | 1 | `replace_oldest` | Buying a new mode removes the current mode automatically. |

Available full-group policies are:

- `deny` keeps current stages and refuses the new one.
- `replace_oldest` removes only as many oldest-owned group members as needed.
- `replace_lowest_priority` removes the lowest `[stage].priority` members first.
- `replace_all` clears every currently owned member before granting the new stage.

The server applies the policy to commands, automatic triggers, purchases, APIs, KubeJS grants, and
temporary session grants. The server sends the group, current usage, limit, and policy to the
in-game progression screen. A client cannot bypass it by sending a purchase packet manually.

In the editor, select a stage and press **Configure slots** under **Stage slots and stacking**. Enter
a plain group name, the maximum active count, and the full-group behavior. Keep **Apply these
settings to every stage already using this group** checked unless you are removing a stage from its
old group.

## Complete fifty stage tree

### Core classes and evolutions

| Stage | Requires | How it is obtained | Feature demonstrated |
|---|---|---|---|
| Mage | Nothing | 16 lapis lazuli | Beginner limit, enchantment gate, reward |
| Warrior | Nothing | 16 iron ingots | Beginner limit, tag selector, local wooden-sword exclusion, JEI and EMI hiding |
| Ranger | Nothing | 32 arrows | Beginner limit, bow gate, item reward |
| Wizard | Mage | Lapis, books, and XP | Block and recipe gates |
| Warlock | Mage | Kill an evoker | Dimension-conditioned temporary allowance |
| Cleric | Mage | Emeralds and XP | Potion brewing gate |
| Knight | Warrior | Iron and XP | Equipped armor attribute and Curios slot rule |
| Berserker | Warrior | Kill a ravager | Axe attribute and effect reward |
| Paladin | Warrior | Gold and XP | Beacon effect gate |
| Marksman | Ranger | Kill skeletons with an item or buy arrows | Item-specific movement attribute |
| Beastmaster | Ranger | Tame three wolves | Pet taming rule and reward |
| Scout | Ranger | Adventure advancement | Screen gate and navigation reward |

### Direct masteries

| Stage | Requires | How it is obtained | Feature demonstrated |
|---|---|---|---|
| Archmage | Wizard | Enderman research and item cost | Advancement display rule |
| Necromancer | Warlock | Kill wither skeletons | Mob spawn rule |
| Oracle | Cleric | Adventuring Time | Trade result rule and advancement trigger |
| Vanguard | Knight | Raid combat and diamond cost | Equipment maximum-health modifier |
| Warlord | Berserker | Netherite and XP | Timed boss challenge, hits, retries, and HUD |
| Templar | Paladin | Golden apples | Ability rule and shield health modifier |
| Deadeye | Marksman | Kill strays with an item | Item-on-entity interaction gate |
| Wildspeaker | Beastmaster | Tame cats | Crop growth rule |
| Pathfinder | Scout | Enter an ancient city | Structure and named-region entry rules |

### Merged class paths

| Stage | Requires | How it is obtained | Feature demonstrated |
|---|---|---|---|
| Spellblade | Wizard and Knight | Sword, lapis, and XP | Two-family merged path and sword attribute |
| Dark Paladin | Warlock and Paladin | Kill the Wither | Nether-only allowance and reward |
| Battle Medic | Vanguard and Cleric | Golden apples, diamonds, and XP | Held shield regeneration |
| Arcane Archer | Wizard and Marksman | Arrows, lapis, and XP | Elytra ability and bow speed |
| Monster Hunter | Berserker and Beastmaster | Kill elder guardians | Entity attack gate |
| Holy Ranger | Templar and Scout | Gold, compass, and XP | Held spyglass night vision |
| Shadow Scout | Warlock and Scout | Kill shulkers | Dimension gate and reward |
| Nature Mage | Cleric and Wildspeaker | Emeralds and bone meal | Loot-table rule |
| Siege Master | Warlord and Deadeye | Craft TNT | Block placement gate and crafting grant |

### Stackable engineers

| Stage | Requires | How it is obtained | Active effect |
|---|---|---|---|
| Coal Engineer | Nothing | 32 coal | Adds a coal output multiplier |
| Iron Engineer | Coal Engineer | 32 iron and XP | Keeps Coal Engineer and adds an iron multiplier |
| Diamond Engineer | Iron Engineer | 32 diamonds | Keeps prior buffs and doubles Fortune diamond output only |
| Netherite Engineer | Diamond Engineer | Netherite and XP | Keeps prior buffs and doubles ancient debris output |
| Redstone Engineer | Iron Engineer | Comparator work and redstone | Adds a redstone output multiplier |
| Quantum Engineer | Diamond and Redstone Engineer | Nether star, diamonds, and XP or KubeJS condition | Merged engineer path and lower all-ore multiplier |

These six use `slot_limit = 0`, so every earned tier remains owned. Drop rules are sorted by
priority. Nonexclusive matches compose with previous matches; an exclusive rule stops remaining
lower-priority matches.

### Mutually exclusive mining modes

| Stage | Requires | Main behavior |
|---|---|---|
| Fortune Mode | Coal Engineer | Lapis output bonus |
| Silk Mode | Coal Engineer | Precision tool movement buff |
| Excavation Mode | Iron Engineer | Shovel movement buff |
| Precision Mode | Iron Engineer | High-priority exclusive quartz rule |

All four use `slot_group = "mining_modes"`, `slot_limit = 1`, and
`slot_policy = "replace_oldest"`. The player may switch modes without an operator revoking the old
one. The generated costs use a full revoke refund, so the replaced purchased mode returns its
configured payment. Change the limit to `2` if a pack should allow two modes together.

### Temporary power

| Stage | Requires | Duration | Feature demonstrated |
|---|---|---:|---|
| Battle Fury | Warrior | 10 minutes | Sword damage and revoke on death |
| Miners Focus | Coal Engineer | 15 minutes | Pickaxe movement buff |
| Aquatic Blessing | Ranger | 20 minutes | Swim ability and held water breathing |
| Village Hero | Cleric | 30 minutes | Profession rule, kill grant, effect reward |
| End Resolve | Archmage | 12 minutes | End-conditioned ability allowance and dragon reward |

Duration is real time. It continues while the player is offline. A permanent stage and a temporary
rule are different: `[stage].duration` expires ownership, while `[[temporary_rules]]` changes a rule
only while its condition or session is active.

### World access and finale

| Stage | Requires | Feature demonstrated |
|---|---|---|
| Stronghold Key | Pathfinder | Structure entry and purchase |
| Nether License | Warlock | Portal, dimension, fluid, and submersion control |
| Wither Protocol | Necromancer and Warlord | Boss challenge, loot, and kill grants |
| End Protocol | Stronghold Key and Archmage | Ability and End-only item allowance |
| Grandmaster | Any 3 of 20 selected end paths | `at_least` dependency policy, cost, rewards, variable, formula, and custom states |

## Using the repaired Stage graph

Open `/pstages editor`, then choose **Stage graph**.

- **Category** opens a direct list. Use it to isolate Core paths, Hybrid paths, Engineering, Mining
  modes, Temporary power, World rules, or Finale.
- **Find a stage** keeps matching stages and automatically includes their prerequisite ancestry.
- **Fit graph** zooms the complete result into the visible panel.
- **Minus**, **percentage**, and **plus** control zoom without rewriting stage files.
- **Arrange paths upward** clears saved manual coordinates and recalculates dependency lanes.
- Dragging a node saves its position in the server draft. Curved connectors follow while dragging,
  including while zoomed out.

The layout orders related parent and child lanes repeatedly to reduce crossings. Invalid, negative,
zero-pair, or wildly out-of-bounds saved coordinates fall back to automatic positions. The full
tree opens fitted to the panel instead of rendering most nodes beyond the right edge.

## Buying a class with items

Select a stage in `/pstages editor`, find **How players obtain this stage**, and choose **Set up
purchase**. Search the live server item registry, click each payment item, set its amount, then set
optional XP, cooldown, and refund values. The editor writes `[cost]`.

The server confirms ownership, dependencies, triggers, slot availability, payment, and cooldown
before changing anything. Payment is atomic. If the grant fails, consumed items and XP are restored.
A purchased team stage is recorded so a later revoke can apply the configured refund once.

Diamond Engineer generates:

```toml
[cost]
items = ["minecraft:diamond:32"]
xp_levels = 0
cooldown = "2s"
refund_percent = 100
bypass_requirements = false
```

## Diamond Engineer targeted bonus

Diamond Engineer also generates:

```toml
[[drop_modifiers]]
id = "showcase:diamond_engineer/diamond_fortune"
blocks = ["minecraft:diamond_ore", "minecraft:deepslate_diamond_ore"]
drops = ["minecraft:diamond"]
tools = ["tag:minecraft:pickaxes"]
required_enchantment = "minecraft:fortune"
minimum_enchantment_level = 1
multiply = 2.0
priority = 600
exclusive = true
```

The multiplier is applied after vanilla Fortune calculates the drop. It does not change coal,
non-diamond output, non-pickaxe tools, non-Fortune mining, or a player who lacks Diamond Engineer.
Ore masquerade resolves first so a visual substitute cannot leak multiplied real drops.

## First launch and existing worlds

The showcase is generated only when the stages directory has no discoverable package and no legacy
stage file. Updating an existing installation does not inject fifty stages into its pack. The main
config keeps `general.starting_stages` empty, so no beginner class is silently selected.

To intentionally generate it in a test instance:

1. Back up `config/progressivestages/stages`.
2. Move the current contents outside that directory.
3. Start Minecraft or the server.
4. Run `/pstages editor` as a permission level 3 operator.
5. Open **Stage graph** and press **Fit graph**.

The fully commented one-file reference remains at
[`examples/reference/diamond_stage.toml`](examples/reference/diamond_stage.toml). It is
documentation, not a generated first-launch stage.

## Verification checklist

1. Confirm the editor lists fifty active stages.
2. Open the graph and confirm the complete overview fits inside the panel.
3. Choose **Core paths** and confirm Mage, Warrior, and Ranger are the three beginner nodes.
4. Confirm Wizard and Warlock connect only through Mage.
5. Confirm Spellblade joins Wizard and Knight.
6. Confirm Grandmaster says any three of twenty paths.
7. Buy Mage and Warrior, then confirm Ranger is refused while the beginner limit is two.
8. Change the beginner limit to three in **Configure slots**, review, apply, and confirm Ranger can
   now be granted.
9. Buy Fortune Mode, then Silk Mode, and confirm Silk replaces Fortune.
10. Grant Coal Engineer through Diamond Engineer and confirm every engineering tier remains owned.
11. Confirm Diamond Engineer costs 32 diamonds.
12. Mine diamond ore without Fortune and record the normal result.
13. Mine it with Fortune and confirm the final diamond count is doubled.
14. Mine coal with the same pickaxe and confirm Diamond Engineer does not alter it.
15. Revoke Diamond Engineer and confirm the targeted bonus stops immediately.
