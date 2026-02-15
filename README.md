# ProgressiveStages – Documentation

This document describes how ProgressiveStages works, how to configure it, how stage files are loaded, and how item/recipe locking integrates with EMI/JEI.

---

## 1) What this mod does

ProgressiveStages adds **stage-based progression locks** for:

- **Items** (use/pickup/interactions)
- **Blocks** (place/use/break where applicable)
- **Recipes / viewing in recipe browsers** (EMI/JEI visibility depending on config)
- **FTB Quests integration** (stages as task conditions and rewards)

Stages are defined in global config files and synced to clients.

### Key Features
- **Event-driven**: No tick-based polling, stages update instantly
- **Dependency graph**: v1.3 uses explicit dependencies instead of linear order
- **FTB Quests native**: ProgressiveStages is the stage backend for FTB Quests
- **Automatic triggers**: Grant stages from advancements, item pickups, dimension entry, or boss kills
- **Team support**: Stages shared via FTB Teams
- **Whitelist exceptions**: Always allow specific items even with broad locks

---

## 2) Key concepts

### Stage
A stage is an ID like:

- `stone_age`
- `iron_age`
- `diamond_age`

Players must have the required stage to interact with locked content.

### Dependencies (v1.3)
Stages can require other stages to be unlocked first:
```toml
[stage]
id = "diamond_age"
dependency = "iron_age"  # Must have iron_age first

# Or multiple dependencies:
dependency = ["iron_age", "stone_age"]
```

### Lock
A lock maps a target to a required stage.

Examples:

- Item lock: `minecraft:diamond_pickaxe` → requires `diamond_age`
- Block lock: `minecraft:enchanting_table` → requires `diamond_age`

Locks are built server-side from stage definitions and synced to the client.

### Dynamic Tags
When stages are loaded, ProgressiveStages builds **dynamic item tags** for each stage:

- `#progressivestages:stone_age`
- `#progressivestages:iron_age`
- `#progressivestages:diamond_age`

These tags are generated at runtime from your TOML stage files - no static JSON tag files needed.

---

## 3) Stage files

### Location
Stage files are stored in the **config directory**, making them global across all worlds:

```
config/ProgressiveStages/*.toml
```

On first run the mod will generate defaults:

- `stone_age.toml`
- `iron_age.toml`
- `diamond_age.toml`

### File format (v1.3)
These are TOML files.

Common fields:

- `id` – stage ID (e.g., `"iron_age"`)
- `display_name` – name shown in tooltips/UI
- `dependency` – (v1.3) stage(s) required before this one can be granted
- `items` – list of locked item IDs
- `item_tags` – list of locked item tags (e.g., `"#forge:ingots/iron"`)
- `blocks` – list of locked block IDs
- `block_tags` – list of locked block tags

Example:
```toml
[stage]
id = "iron_age"
display_name = "Iron Age"
dependency = "stone_age"  # v1.3: Must have stone_age first

[locks]
items = [
    "minecraft:iron_pickaxe",
    "minecraft:iron_sword",
    "minecraft:iron_helmet"
]
item_tags = [
    "#c:ingots/iron"
]

# v1.3: Whitelist exceptions (always accessible)
unlocked_items = []
```

---

## 4) Commands

ProgressiveStages adds stage management commands:

- Grant a stage:
  - `/stage grant <player> <stage>`
- Revoke a stage:
  - `/stage revoke <player> <stage>`
- List stages:
  - `/stage list [player]`
- Check stage:
  - `/stage check <player> <stage>`
- Reload stage files:
  - `/progressivestages reload`

After reload, the server will resync stage+lock data to clients.

---

## 5) Configuration

Config file (generated after first run):

```
config/progressivestages-common.toml
```

### General options

#### Linear progression
```toml
[general]
linear_progression = false
```
When `true`, granting a stage also grants all missing dependency stages automatically.
When `false`, stages require explicit dependency satisfaction (admin can bypass with double-confirm command).

**Example:**
- With `linear_progression = true`: granting `diamond_age` auto-grants `iron_age` and `stone_age` (its dependencies)
- With `linear_progression = false`: granting `diamond_age` only grants `diamond_age` (dependencies must be met first)

### Enforcement options

#### Creative bypass
```toml
[enforcement]
allow_creative_bypass = true
```
When `true`, creative players can use/place locked items.

#### Mask locked item names
```toml
[enforcement]
mask_locked_item_names = true
```
When `true`, locked items show as **"Unknown Item"** in tooltips.

#### Pickup message cooldown
```toml
[enforcement]
notification_cooldown = 3000
```
Prevents chat spam when repeatedly touching locked items.

### EMI integration options

#### Enable EMI integration
```toml
[emi]
enabled = true
```

#### Show locked recipes / items in EMI
```toml
[emi]
show_locked_recipes = false
```

Behavior:
- `false`: locked items/recipes are **hidden** from EMI list/index
- `true`: locked items are visible and rendered with lock UI

---

## 6) EMI Integration

### Dynamic Stage Tags
ProgressiveStages generates item tags dynamically from your stage TOML files.

In EMI, you can search by stage using the tag prefix `#`:
- `#progressivestages:stone_age` – all stone age items
- `#progressivestages:iron_age` – all iron age items
- `#progressivestages:diamond_age` – all diamond age items

This works because:
1. When the server loads stage files, it builds `StageTagRegistry`
2. Items defined in each stage's `items` and `item_tags` are grouped
3. EMI reads these as virtual tags

### Lock overlays
ProgressiveStages draws lock UI in EMI via mixins:

- **Recipe viewer slots**: orange highlight + lock icon
- **Search list / favorites**: lock icon only (no orange overlay)

### Vanilla creative menu
Vanilla creative inventory is **not modified**. The EMI mixin skips rendering on `CreativeModeInventoryScreen`.

---

## 7) Client sync + caches

### Stage sync
Server sends the client a stage snapshot and delta updates.
Client stores stages in: `ClientStageCache`

### Lock sync
Server sends lock registry data to the client.
Client stores locks in: `ClientLockCache`

### Tag sync
Dynamic stage tags are built server-side in `StageTagRegistry`.

---

## 8) EMI refresh when stages change

When a stage is granted/revoked:

1. Client stage cache updates
2. A delayed EMI reload is scheduled
3. EMI calls `EmiReloadManager.reloadRecipes()`
4. EMI re-runs plugin registration and rebuilds its item/recipe index

This ensures that:
- Newly unlocked content becomes visible
- Newly locked content disappears

---

## 9) Debugging

Enable debug logging:
```toml
[general]
debug_logging = true
```

Useful log messages:
- Stage sync received (client)
- Lock sync received (client)
- EMI reload scheduled + completed
- Dynamic stage tags built: X stages, Y total items

---

## 10) Troubleshooting

### "I can't see locked items in EMI search"
Expected if `emi.show_locked_recipes = false`. Set to `true` if you want locked items visible with lock indicator.

### "I granted a stage but items still don't show in EMI"
- EMI must receive the stage update
- EMI reload must run afterward
- Re-open EMI or inventory after a grant if needed

### "Tag search doesn't show my stage items"
- Ensure the stage TOML file has items listed in `[locks]` section
- Run `/progressivestages reload` after editing TOML files
- Check logs for "Built dynamic stage tags" message

---

## 11) Developer notes

Important classes:

- `ClientStageCache` – client stage snapshot/deltas
- `ClientLockCache` – client lock snapshot
- `LockRegistry` – server lock registry
- `StageTagRegistry` – dynamic stage→items mapping for EMI tags
- `DynamicTagProvider` – provides TagKey lookup for dynamic stage tags
- `NetworkHandler` – payload registration + sync sending
- `ProgressiveStagesEMIPlugin` – registers dynamic tags, hides stacks, triggers EMI reload
- `EmiStackWidgetMixin` – recipe viewer overlays (lock icon + highlight)

---

## 12) Workflow for pack devs

1. Start a new world once to generate defaults
2. Edit the TOML stage files in `<world>/ProgressiveStages/`
3. Run `/progressivestages reload`
4. Use `/stage grant` and `/stage revoke` to test lock states
5. Search in EMI with `#progressivestages:<stage>` to browse stage items
6. Validate visibility and overlays via EMI
