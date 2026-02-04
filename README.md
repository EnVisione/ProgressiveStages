# ProgressiveStages – Documentation

This document describes how ProgressiveStages works, how to configure it, how stage files are loaded, and how item/recipe locking integrates with EMI/JEI.

---

## 1) What this mod does

ProgressiveStages adds **stage-based progression locks** for:

- **Items** (use/pickup/interactions)
- **Blocks** (place/use/break where applicable)
- **Recipes / viewing in recipe browsers** (EMI/JEI visibility depending on config)

Stages are defined in per-world stage files and synced to clients.

---

## 2) Key concepts

### Stage
A stage is an ID like:

- `stone_age`
- `iron_age`
- `diamond_age`

Players must have the required stage to interact with locked content.

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
Stage files are stored **inside the world save** under:

```
<world>/ProgressiveStages/*.toml
```

On first run the mod will generate defaults:

- `stone_age.toml`
- `iron_age.toml`
- `diamond_age.toml`

### File format
These are TOML files.

Common fields:

- `id` – stage ID (e.g., `"iron_age"`)
- `display_name` – name shown in tooltips/UI
- `order` – progression order (lower = earlier)
- `items` – list of locked item IDs
- `item_tags` – list of locked item tags (e.g., `"#forge:ingots/iron"`)
- `blocks` – list of locked block IDs
- `block_tags` – list of locked block tags

Example:
```toml
[stage]
id = "iron_age"
display_name = "Iron Age"
order = 2

[locks]
items = [
    "minecraft:iron_pickaxe",
    "minecraft:iron_sword",
    "minecraft:iron_helmet"
]
item_tags = [
    "#forge:ingots/iron"
]
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
- `NetworkHandler` – payload registration + sync sending
- `ProgressiveStagesEMIPlugin` – hides stacks and triggers EMI reload
- `EmiStackWidgetMixin` – recipe viewer overlays
- `EmiItemStackMixin` – index/favorites lock icon only

---

## 12) Workflow for pack devs

1. Start a new world once to generate defaults
2. Edit the TOML stage files in `<world>/ProgressiveStages/`
3. Run `/progressivestages reload`
4. Use `/stage grant` and `/stage revoke` to test lock states
5. Search in EMI with `#progressivestages:<stage>` to browse stage items
6. Validate visibility and overlays via EMI
