# ProgressiveStages 3.0.1 Showcase Pack

ProgressiveStages now creates a thirty stage showcase tree when `config/progressivestages/stages`
is empty. It replaces the old Stone Age, Iron Age, and Diamond Age first launch examples. Existing
server files are never deleted or replaced during an update.

Each showcase stage is a schema 4 package:

```text
config/progressivestages/stages/<stage_name>/
├── stage.toml
├── rules.toml
└── progression.toml
```

The pack is meant to be opened in `/pstages editor`. It demonstrates independent starting classes,
single path evolutions, hybrid classes, an at least N finale, item purchases, automatic grants,
rewards, temporary rules, abilities, attributes, effects, a challenge, variables, formulas, custom
states, and selector based mining bonuses.

## The complete tree

| Stage | Requires | How it is obtained | Main example |
|---|---|---|---|
| Mage | Nothing | 16 lapis lazuli | Independent purchasable root |
| Warrior | Nothing | 16 iron ingots | Item tag lock and starter reward |
| Paladin | Nothing | 12 gold ingots | Shield lock and reward |
| Healer | Nothing | 16 glistering melon slices | Consumable lock and reward |
| Ranger | Nothing | 32 arrows | Bow lock and reward |
| Engineer | Nothing | 32 redstone | Redstone interaction lock |
| Wizard | Mage | 24 lapis, 8 books, and 8 XP levels | Held focus attribute |
| Warlock | Mage | Kill an evoker | Live Nether allowance |
| Knight | Warrior | 32 iron ingots and 5 XP levels | Armor attribute modifier |
| Berserker | Warrior | Kill a ravager | Axe modifier and effect reward |
| Templar | Paladin | 24 gold ingots and 6 XP levels | Shield health modifier |
| Crusader | Paladin | Kill 20 pillagers | Entity rule and item reward |
| Cleric | Healer | 16 emeralds and 8 XP levels | Held item regeneration effect |
| Alchemist | Healer | Craft a brewing stand | Block interaction rule |
| Beastmaster | Ranger | Tame 3 wolves | Entity interaction rule |
| Marksman | Ranger | Kill 20 skeletons with the configured event | Ranged mobility modifier and alternate purchase |
| Mechanist | Engineer | Craft 8 pistons | Craft trigger and machine reward |
| Miner | Engineer | Mine 64 iron ore | Tool tag modifier and mining trigger |
| Archmage | Wizard | Arcane kills or item purchase | End crystal gate and challenge frame |
| Necromancer | Warlock | Kill 25 wither skeletons | Contextual Nether allowance |
| Vanguard | Knight | Combat grant or diamond purchase | Armor and maximum health modifier |
| Warlord | Berserker | 2 netherite ingots and 25 XP levels | Challenge budget, timeout, retries, and HUD |
| Oracle | Cleric | Adventuring Time advancement | Advancement grant and held effect |
| Artificer | Mechanist | Craft comparators or buy with quartz | Temporary End rule |
| Diamond Engineer | Miner and Mechanist | 32 diamonds | Doubles final Fortune diamond drops only |
| Spellblade | Wizard and Knight | Diamond sword, lapis, and XP | Two path hybrid |
| Dark Paladin | Warlock and Crusader | Kill the Wither | Two path hybrid and live rule |
| Battle Medic | Vanguard and Cleric | Golden apples, diamonds, and XP | Two path hybrid and support effect |
| Arcane Archer | Wizard and Marksman | Spectral arrows, lapis, and XP | Two path hybrid and ability gate |
| Grandmaster | Any 3 of 10 mastery or hybrid stages | 16 dragon breath and 30 XP levels | At least N graph, rewards, variable, formula, and states |

## Buying a class with items

Select a stage in `/pstages editor`, find **How players obtain this stage**, and choose **Set up
purchase**. Search the live server item registry, click each payment item, set its amount, then set
optional XP, cooldown, and refund values. The editor writes `[cost]` for you.

The in game progression screen displays the payment and enables its purchase button only when the
server confirms all of the following:

1. The player does not already own the stage.
2. Every required stage policy is satisfied.
3. Trigger requirements are satisfied unless the author enabled their bypass.
4. Every item and XP level is present.
5. The purchase cooldown has expired.

Payment is atomic. If the grant fails, consumed items and XP are restored. A purchased team stage
is recorded so a later revoke can apply the configured refund once.

The generated Diamond Engineer payment is:

```toml
[cost]
items = ["minecraft:diamond:32"]
xp_levels = 0
cooldown = "2s"
refund_percent = 100
bypass_requirements = false
```

## Targeted mining and block drop bonuses

Select **More stage features**, then **Targeted mining bonus**. Choose the source block, output item,
optional tool selector, optional enchantment and minimum level, multiplier, addition, priority, and
whether a successful rule stops lower priority rules.

Diamond Engineer generates this reusable rule:

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

The rule is active only while its owning stage is owned. The server evaluates the real broken block,
the final output stack, the tool, the enchantment level, required and forbidden stages, and an
optional condition. It sorts matching rules from highest to lowest priority. Each nonexclusive rule
transforms the previous count. An exclusive match stops the remaining lower priority rules.

The multiplier is applied after vanilla Fortune calculates the drop. Breaking diamond ore with no
Fortune does not match this example. Breaking coal ore, receiving a non-diamond output, using a tool
outside the selected tool tag, or missing Diamond Engineer also does not match. Ore masquerade is
resolved first, so a visually substituted locked ore cannot leak multiplied real drops.

Advanced source authors can also use:

```toml
[[drop_modifiers]]
id = "my_pack:lucky_copper"
blocks = ["tag:c:ores/copper"]
drops = ["tag:c:raw_materials/copper"]
tools = ["mod:my_tool_mod"]
with_stages = ["my_pack:prospector"]
without_stages = ["my_pack:precision_miner"]
condition = { type = "dimension", id = "minecraft:the_nether" }
required_enchantment = "minecraft:fortune"
minimum_enchantment_level = 2
add = 1
multiply = 1.5
minimum = 0
maximum = 64
priority = 450
exclusive = false
```

`blocks`, `drops`, and `tools` use the normal `id:`, `mod:`, `tag:`, `name:`, and registered custom
selector grammar. `blocks` and `drops` are required. `tools`, `required_enchantment`,
`with_stages`, `without_stages`, and `condition` are optional.

## First launch and existing worlds

The showcase is created only when the stages directory has no discoverable stage package and no
legacy stage file. Updating an existing installation does not inject thirty stages into its pack and
does not remove Stone Age, Iron Age, Diamond Age, or any custom file that already exists.

The generated main config defaults `general.starting_stages` to an empty list. This is intentional:
none of the six root classes is silently chosen for a new player. A pack author may add one or more
root IDs to that list when a class should be free or mandatory.

To intentionally generate the showcase in a test instance:

1. Back up `config/progressivestages/stages`.
2. Move its contents somewhere outside that directory.
3. Start Minecraft or the server.
4. Run `/pstages editor` as a permission level 3 operator.
5. Inspect the Stage graph and use **Arrange paths upward** if you previously saved manual positions.

The older fully commented Diamond Age reference remains available at
[`examples/reference/diamond_stage.toml`](examples/reference/diamond_stage.toml). It is documentation
and a legacy one file example, not a first launch generated stage.

## Verification checklist

1. Open `/pstages editor` and confirm thirty active stages are listed.
2. Open **Stage graph** and confirm six independent roots appear at the bottom.
3. Confirm Wizard and Warlock connect only to Mage.
4. Confirm Spellblade connects to both Wizard and Knight.
5. Confirm Grandmaster says any 3 of its selected mastery and hybrid paths.
6. Open Diamond Engineer and confirm its purchase summary says 32 Diamonds.
7. Open its TOML source and confirm the targeted drop modifier shown above exists.
8. In game, grant Miner and Mechanist, obtain 32 diamonds, and buy Diamond Engineer from the tree.
9. Break diamond ore with a non-Fortune pickaxe and record the normal result.
10. Break diamond ore with a Fortune pickaxe and confirm the final diamond count is doubled.
11. Break coal ore with the same Fortune pickaxe and confirm Diamond Engineer does not change it.
12. Revoke Diamond Engineer and confirm the targeted bonus stops immediately.
