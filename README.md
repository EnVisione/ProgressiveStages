# ProgressiveStages

A NeoForge mod for Minecraft 1.21.1 that gives modpack developers complete control over stage-based progression locks. Define stages in simple TOML files — ProgressiveStages handles locking items, blocks, entities, fluids, dimensions, and recipes until players earn the right to use them.

---

## Features

- **Lock anything** — items, blocks, entities, fluids, dimensions, recipes
- **Granular mod locks** — lock all items from a mod, or just its blocks, or just its entities — independently
- **Name pattern matching** — `names = ["diamond"]` locks every item, block, entity, and fluid with "diamond" in its ID across all mods
- **Whitelist exceptions** — `unlocked_items`, `unlocked_blocks`, `unlocked_entities`, `unlocked_fluids` let you carve out exceptions from broad locks
- **EMI + JEI integration** — lock icons in recipe viewer and search index, hidden items when locked, full creative bypass
- **FTB Quests integration** — Stage Tasks, Stage Rewards, and a native "Stage Required" field on quests and chapters
- **Trigger system** — grant stages automatically on advancement, item pickup, dimension entry, or boss kill
- **Dependency graph** — stages can require other stages before they can be granted
- **Team support** — FTB Teams mode or solo per-player mode
- **Event-driven** — no tick polling, stages update instantly

---

## Installation

1. Install [NeoForge](https://neoforged.net/) for Minecraft 1.21.1
2. Drop `progressivestages-x.x.x.jar` into your `mods/` folder
3. Optional: install EMI or JEI for recipe viewer integration
4. Optional: install FTB Quests + FTB Library for quest integration
5. Launch and configure stages in `config/ProgressiveStages/*.toml`

---

## Quick Start

Create `config/ProgressiveStages/iron_age.toml`:

```toml
[stage]
id = "iron_age"
display_name = "Iron Age"
dependency = "stone_age"
unlock_message = "&6Iron Age Unlocked!"

[locks]
items = ["minecraft:iron_pickaxe", "minecraft:iron_ingot"]
mods = ["create"]
unlocked_items = ["create:wrench"]
```

Grant the stage in-game:

```
/stage grant YourName iron_age
```

---

## Lock Cascade Table

| Lock Type | Items | Blocks | Entities | Fluids (EMI/JEI) |
|-----------|:-----:|:------:|:--------:|:----------------:|
| `mods = ["modid"]` | ✅ | ✅ | ✅ | ✅ |
| `item_mods` | ✅ | ❌ | ❌ | ❌ |
| `block_mods` | ❌ | ✅ | ❌ | ❌ |
| `entity_mods` | ❌ | ❌ | ✅ | ❌ |
| `fluid_mods` | ❌ | ❌ | ❌ | ✅ |
| `names = ["pattern"]` | ✅ | ✅ | ✅ | ✅ |
| `items = [...]` | ✅ | ❌ | ❌ | ❌ |
| `blocks = [...]` | ❌ | ✅ | ❌ | ❌ |
| `entities = [...]` | ❌ | ❌ | ✅ | ❌ |
| `fluids = [...]` | ❌ | ❌ | ❌ | ✅ |

Whitelists (`unlocked_items`, `unlocked_blocks`, `unlocked_entities`, `unlocked_fluids`) take priority over ALL lock types.

> ⚠️ **Fluid locks only affect EMI/JEI visibility.** They do NOT prevent players from piping, pumping, or using fluids in machines. To block fluid transport, lock the machines and pipes themselves.

---

## Commands

| Command | Permission | Description |
|---------|------------|-------------|
| `/stage grant <player> <stage>` | OP | Grant a stage |
| `/stage revoke <player> <stage>` | OP | Revoke a stage |
| `/stage list [player]` | OP | List stages |
| `/stage check <player> <stage>` | OP | Check if player has stage |
| `/stage info <stage>` | OP | Show stage definition |
| `/progressivestages reload` | OP | Reload stage + trigger configs |
| `/progressivestages validate` | OP | Validate stage files |
| `/progressivestages ftb status [player]` | OP | Debug FTB integration |

---

## Configuration

Config file: `config/progressivestages-common.toml`

```toml
[general]
starting_stages = ["stone_age"]
team_mode = "ftb_teams"  # or "solo"
debug_logging = false
linear_progression = false

[enforcement]
block_item_use = true
block_item_pickup = true
block_item_inventory = true
block_crafting = true
block_block_placement = true
block_block_interaction = true
block_dimension_travel = true
block_entity_attack = true
allow_creative_bypass = true
mask_locked_item_names = true
notification_cooldown = 3000

[emi]
enabled = true
show_lock_icon = true
lock_icon_position = "top_left"
lock_icon_size = 8
show_highlight = true
highlight_color = "0x50FFAA40"
show_locked_recipes = false
```

---

## Compatibility

| Mod | Status |
|-----|--------|
| EMI | ✅ Full integration |
| JEI | ✅ Full integration |
| FTB Quests | ✅ Full integration |
| FTB Teams | ✅ Supported |
| NeoForge 1.21.1 | ✅ Required |

---

## Changelog

### v1.2
- Entity locks — `entities`, `entity_tags`, `entity_mods`, `unlocked_entities`
- `mods` cascade now includes entities
- `block_entity_attack` config toggle
- Fixed creative bypass missing from block placement, crafting, and dimension travel
- Fixed EMI/JEI not refreshing after `/stage grant`
- Dependency graph replaces linear `order` field
- `item_mods` and `block_mods` for granular mod locking
- Multiple `starting_stages` support
- Admin bypass confirmation for missing dependencies
- `unlocked_blocks` and `unlocked_entities` whitelist fields
- Fluid locks — `fluids`, `fluid_tags`, `fluid_mods`, `unlocked_fluids`
- `names` pattern now matches items, blocks, entities, AND fluids
- `mods` cascade now includes fluids
- NBT-aware EMI/JEI hiding — catches all stack variants (Mekanism Chemical Tanks, Meka-Suit, etc.)
- EMI search index lock icons via `EmiScreenManagerMixin`
- Creative mode toggle instantly refreshes EMI/JEI index without relog
- `unlocked_blocks` whitelist field

### v1.1
- FTB Quests integration
- EMI handling improvements
- Expanded trigger system
- Expanded default stages

---

## Documentation

Full documentation is in [DOCUMENTATION.md](DOCUMENTATION.md).

## License

MIT
