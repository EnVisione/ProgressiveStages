# AGENTS.md

## Git & Commit Policy

**Every action the agent takes MUST be committed to GitHub.** After each meaningful change (file creation, code edit, bug fix, feature addition), the agent must:

1. Stage the changed files: `git add <files>`
2. Commit with a **detailed message** — clear title + body explaining *what* changed, *why*, and any trade-offs
3. Push to origin: `git push origin master`

The agent is already authenticated as the user. Use `git` commands directly. **Do not squash or amend published commits — create new ones.** Repository: `https://github.com/EnVisione/ProgressiveStages`

---

## Project Summary

NeoForge mod (Minecraft 1.21.1, Java 21) providing stage-based progression locks for modpack developers. Stages are defined in TOML config files (`config/ProgressiveStages/*.toml`) and control access to items, blocks, entities, fluids, recipes, and dimensions. Integrates with EMI, JEI, FTB Quests, and FTB Teams.

**Mod ID:** `progressivestages` · **Version:** `1.3` (authoritative: `mod_version` in `gradle.properties`) · **Group:** `com.enviouse` · **Java:** 21 · **NeoForge:** 21.1.219

## Build & Run

```sh
./gradlew build              # Produces JAR in build/libs/
./gradlew runClient          # Launch game client with mod
./gradlew runServer          # Launch dedicated server (--nogui)
./gradlew runGameTestServer  # Run automated game tests
./gradlew clean build        # Full rebuild
```

All mod dependencies are **local JARs** in `libs/` — no external Maven repos needed. The only remote dependency is `night-config:toml:3.6.7`.

---

## Architecture (Data Flow)

1. **Startup** → `Progressivestages.java` registers data attachments, config, creates `config/ProgressiveStages/` folder, generates default stage + trigger TOML files if absent
2. **Server start** → `ServerEventHandler.onServerStarting()` initializes: `TeamProvider` → `StageManager` → `TeamStageSync` → `StageFileLoader` → `TriggerConfigLoader` → trigger event handlers → `FTBQuestsCompat`
3. **Lock indexing** → `StageFileLoader`/`StageFileParser` parse TOML → `StageDefinition` objects → `StageOrder` registers dependency graph → `LockRegistry` indexes all lock rules in `ConcurrentHashMap` maps → `StageTagRegistry` builds dynamic item tags
4. **Enforcement** → `server/enforcement/` classes are called from `ServerEventHandler` event handlers; they query `LockRegistry` + `StageManager` to cancel disallowed actions
5. **Client sync** → `NetworkHandler` sends full snapshots on join, deltas on grant/revoke → `ClientStageCache` + `ClientLockCache` hold client state
6. **Recipe viewers** → EMI plugin + mixins hide/overlay locked content; JEI plugin hides locked items/fluids

## Key Singletons

| Class | Role |
|---|---|
| `StageManager` | Grant/revoke/check stages; team-aware; fires `StageChangeEvent` |
| `LockRegistry` | Maps item/block/entity/fluid/recipe/dimension IDs → required `StageId`; thread-safe `ConcurrentHashMap` |
| `TeamProvider` | Abstracts FTB Teams vs solo mode; contains `ITeamIntegration` interface |
| `StageOrder` | Stores all `StageDefinition` objects + validates dependency graph |
| `StageFileLoader` | Loads/reloads TOML stage definitions from `config/ProgressiveStages/` |
| `StageTagRegistry` | Builds runtime item→stage tag groups for EMI search (`#progressivestages:stage_name`) |

---

## Conventions

- **Stage IDs** use `StageId` wrapper (delegates to `ResourceLocation`). Bare strings auto-prefix `progressivestages:`. Always use `StageId.parse()` or `new StageId()`. IDs are trimmed, lowercased, and validated against ResourceLocation charset rules.
- **Lock resolution order:** direct ID → tags → mod ID → name patterns. Whitelists (`unlocked_items`, `unlocked_blocks`, `unlocked_entities`, `unlocked_fluids`) always override all lock types.
- **Lock cascade:** `mods = ["modid"]` locks items + blocks + entities + fluids. `names = ["pattern"]` also locks all four. Type-specific `item_mods`/`block_mods`/`entity_mods`/`fluid_mods` lock only their type.
- **Event-driven, no tick polling** — enforcers use NeoForge event subscribers (`@SubscribeEvent`), triggers fire on advancement/dimension/boss/pickup events. The only exception: `FTBTeamsIntegration` polls team changes every 20 ticks because FTB Teams doesn't fire NeoForge events.
- **Creative bypass** — all enforcers check `StageConfig.isAllowCreativeBypass() && player.isCreative()` before blocking.
- **Message cooldowns** — enforcers track per-player, per-item cooldowns via `ItemEnforcer.messageCooldowns` to avoid chat spam.
- **FTB Quests compat is soft-loaded** — `compat/ftbquests/` uses `compileOnly` dependency + separate mixin config (`progressivestages-ftbquests.mixins.json` with `required: false`). All FTB API calls go through `FtbQuestsHooks` using reflection so compile-time FTB classes are never required at runtime.
- **Constants** — all mod-wide constants (mod ID, packet IDs, directory names) live in `common/util/Constants.java`.
- **Config** — `StageConfig.java` is the authoritative source for all config keys; uses NeoForge `ModConfigSpec`. Runtime config file: `config/progressivestages.toml`.
- **Dependency graph** — stages declare `dependency = "other_stage"` (single) or `dependency = ["a", "b"]` (multiple). `StageOrder` validates on load. Admin `/stage grant` prompts for confirmation when dependencies are missing; confirmation expires after 10 seconds.
- **Trigger persistence** — dimension and boss triggers are one-time per player, persisted in `world/data/progressivestages_triggers.dat` via `TriggerPersistence` (NeoForge `SavedData`). Reset with `/progressivestages trigger reset`.

---

## Complete File Reference

### Root: `com.enviouse.progressivestages`

| File | Purpose |
|------|---------|
| `Progressivestages.java` | `@Mod` entry point. Registers data attachments, config, creates config folder, generates default stage TOML files (stone_age, iron_age, diamond_age) and triggers.toml if absent. Registers client setup event. ~1200 lines because it contains all the default TOML content strings. |

### `common/api/` — Public API

| File | Purpose |
|------|---------|
| `ProgressiveStagesAPI.java` | Static API facade for other mods. Methods: `hasStage()`, `grantStage()`, `revokeStage()`, `getStages()`, `getDefinition()`, `getAllDefinitions()`, `stageExists()`. Delegates to `StageManager` and `StageFileLoader`. |
| `StageId.java` | Immutable wrapper around `ResourceLocation` for stage identifiers. Handles normalization (trim, lowercase, default namespace). Factory methods: `parse()`, `of()`, `fromResourceLocation()`. Implements `Comparable`. |
| `StageCause.java` | Enum of why a stage changed: `COMMAND`, `ADVANCEMENT`, `ITEM_PICKUP`, `INVENTORY_CHECK`, `QUEST_REWARD`, `DIMENSION_ENTRY`, `BOSS_KILL`, `TEAM_SYNC`, `AUTOMATIC`. |
| `StageChangeEvent.java` | NeoForge event fired AFTER a single stage grant/revoke. Contains player, teamId, stageId, changeType, cause. Implements `ICancellableEvent`. |
| `StageChangeType.java` | Enum: `GRANTED` or `REVOKED`. |
| `StagesBulkChangedEvent.java` | NeoForge event for bulk stage changes (login, team join, reload, linear progression). Contains player, teamId, currentStages set, and `Reason` enum. Individual `StageChangeEvent`s are NOT fired when this event is used. |

### `common/config/` — Configuration

| File | Purpose |
|------|---------|
| `StageConfig.java` | NeoForge `ModConfigSpec` definition. All config keys defined here with comments. Sections: general (starting_stages, team_mode, debug_logging, linear_progression), enforcement (block_item_use, block_item_pickup, etc.), emi (enabled, show_lock_icon, etc.), integration.ftbquests (enabled, recheck_budget_per_tick). Exposes static getter methods like `isBlockItemUse()`, `isAllowCreativeBypass()`. |
| `StageDefinition.java` | Immutable data class for a parsed stage. Fields: `StageId id`, `displayName`, `description`, `icon` (ResourceLocation), `unlockMessage`, `LockDefinition locks`, `List<StageId> dependencies`, whitelists (`unlockedItems`, `unlockedBlocks`, `unlockedEntities`, `unlockedFluids`). Uses Builder pattern. |

### `common/data/` — NeoForge Data Attachments

| File | Purpose |
|------|---------|
| `StageAttachments.java` | Registry for NeoForge `AttachmentType`. Registers `TEAM_STAGES` attachment (serialized via Codec, copyOnDeath) which stores `TeamStageData` on the overworld `ServerLevel`. |
| `TeamStageData.java` | Data class storing `Map<UUID, Set<StageId>>` (team→stages). Serialized via `Codec` for world save/load. Methods: `hasStage()`, `grantStage()`, `revokeStage()`, `getStages()`, `getHighestStage()`. |

### `common/lock/` — Lock System

| File | Purpose |
|------|---------|
| `LockRegistry.java` | Central singleton. Contains 15+ `ConcurrentHashMap` maps for every lock type (itemLocks, itemTagLocks, itemModLocks, blockLocks, blockTagLocks, blockModLocks, fluidLocks, fluidTagLocks, fluidModLocks, dimensionLocks, modLocks, nameLocks, entityLocks, entityTagLocks, entityModLocks, interactionLocks) plus whitelist sets (unlockedItems, unlockedBlocks, unlockedEntities, unlockedFluids). Provides `getRequiredStage()` / `getRequiredStageForBlock()` / `getRequiredStageForEntity()` etc. with resolution order: whitelist → direct ID → tags → mod ID → name patterns. Maintains an `itemStageCache` for performance. |
| `LockDefinition.java` | Immutable data class holding all lock lists parsed from a stage TOML `[locks]` section. 21 lists: items, itemTags, itemMods, blocks, blockTags, blockMods, fluids, fluidTags, fluidMods, recipes, recipeTags, dimensions, mods, names, entities, entityTags, entityMods, interactions, unlockedItems, unlockedBlocks, unlockedEntities, unlockedFluids. Builder pattern. |
| `LockEntry.java` | Single lock entry record: `LockType type`, `String target`, `StageId requiredStage`, optional `description`, `heldItem`, `targetBlock`, `interactionType`. Builder pattern. |
| `LockType.java` | Enum: `ITEM`, `ITEM_TAG`, `RECIPE`, `RECIPE_TAG`, `BLOCK`, `BLOCK_TAG`, `DIMENSION`, `MOD`, `NAME`, `INTERACTION`. |
| `NameMatcher.java` | Utility for case-insensitive substring matching against registry IDs. Static methods: `matches(String itemId, String pattern)`, `matches(Item, pattern)`, `matches(Block, pattern)`. Used by the `names = [...]` lock type. |

### `common/network/` — Client-Server Sync

| File | Purpose |
|------|---------|
| `NetworkHandler.java` | Registers all NeoForge payload packets via `@SubscribeEvent RegisterPayloadHandlersEvent`. Packet types: `StageSyncPayload` (full snapshot), `StageUpdatePayload` (delta grant/revoke), `LockSyncPayload` (lock registry), `StageDefinitionsSyncPayload` (definitions for client), `CreativeBypassPayload`. Handles sending to individual players and all team members. Client handlers update `ClientStageCache` and `ClientLockCache`. |

### `common/stage/` — Stage Management

| File | Purpose |
|------|---------|
| `StageManager.java` | Core singleton. `grantStageWithCause()` checks dependencies (or auto-grants them if `linear_progression` is on), stores via `TeamStageData`, fires `StageChangeEvent`, syncs to team members via `NetworkHandler`. `revokeStageWithCause()` does the reverse. `hasStage()` delegates to `TeamStageData`. `getMissingDependencies()` validates dependency satisfaction. |
| `StageOrder.java` | Stores all registered `StageDefinition` objects in insertion order. `registerStage()` checks for duplicates. Provides `getStageDefinition()`, `getOrderedStages()`, `getDependencies()`, `getAllDependencies()` (transitive), `stageExists()`. Validates dependency graph for missing/circular references on load. |

### `common/tags/` — Dynamic Tags

| File | Purpose |
|------|---------|
| `StageTagRegistry.java` | Builds item→stage mappings at runtime from loaded stage definitions. `rebuildFromStages()` resolves all lock definitions to concrete items using registry iteration. Provides `isItemInStage()` and `getStageItems()` for EMI search tag support (`#progressivestages:iron_age`). |
| `DynamicTagProvider.java` | Creates virtual `TagKey<Item>` objects for stages. Provides `isItemInStageTag()` lookup. Listens to `TagsUpdatedEvent` for cache invalidation. These are "virtual" tags — they exist as TagKeys but are populated by `StageTagRegistry`, not Minecraft's datapack tag system. |

### `common/team/` — Team Abstraction

| File | Purpose |
|------|---------|
| `TeamProvider.java` | Singleton abstracting team mode. `initialize()` checks if FTB Teams is available via `Class.forName()`. Creates either `FTBTeamsIntegration` or `SoloIntegration` (inner classes implementing `ITeamIntegration`). `getTeamId()` returns team UUID (or player UUID in solo). `getTeamMembers()` returns online team members. |
| `TeamStageSync.java` | Handles team alignment logic. Tracks which teams are "aligned" (all members have same stages). `checkTeamAlignment()` is called after stage changes. Provides `syncStagesFromTeam()` for new team member stage inheritance. |

### `common/util/` — Utilities

| File | Purpose |
|------|---------|
| `Constants.java` | Mod-wide constants: `MOD_ID`, `MOD_NAME`, `MOD_VERSION` (⚠️ stale — use `gradle.properties` `mod_version`), network packet ResourceLocations, `STAGE_FILES_DIRECTORY`, `TEAM_STAGE_ATTACHMENT`. |
| `StageUtil.java` | Helper methods: `getModId(Item)`, `getModId(ItemStack)`, `getItemId(Item)`, `parseResourceLocation(String)`. |
| `TextUtil.java` | Parses `&`-prefixed color codes (e.g., `&6&lIron Age`) into Minecraft `Component` objects. Maps `&0`–`&f` to `ChatFormatting` colors and `&k`–`&r` to formatting. Used for `unlock_message` display. |

### `client/` — Client-Side

| File | Purpose |
|------|---------|
| `ClientEventHandler.java` | `@EventBusSubscriber(Dist.CLIENT)`. Handles `ItemTooltipEvent` to add lock info to item tooltips (required stage name, current stage, progress). Masks item names to "Unknown Item" if `mask_locked_item_names` is enabled. Checks `ClientLockCache` with `LockRegistry` fallback for integrated server. |
| `ClientStageCache.java` | Static cache of player's stages synced from server. `setStages()` replaces all; `addStage()`/`removeStage()` for deltas. Stores `StageDefinitionData` records for client-side display. Triggers EMI reload when stages change. Provides `hasStage()`, `getProgressString()`, `getCurrentStage()`. |
| `ClientLockCache.java` | Static cache of lock data synced from server. `ConcurrentHashMap` maps for itemLocks, blockLocks, recipeLocks. `creativeBypass` flag suppresses all lock rendering when active. `getRequiredStageForItem()` returns the required stage for client-side checks. Triggers EMI reload when lock data or bypass state changes. |

### `client/emi/` — EMI Integration

| File | Purpose |
|------|---------|
| `ProgressiveStagesEMIPlugin.java` | `@EmiEntrypoint` plugin. `register()` hides locked item stacks and recipes from EMI when `show_locked_recipes = false` and creative bypass is inactive. Uses `removeEmiStacks` with predicates to catch all NBT variants (e.g., Mekanism's multiple stacks per item). Also hides locked fluids. `triggerReload()` method for on-demand EMI refresh with debouncing via `AtomicBoolean`. |

### `client/jei/` — JEI Integration

| File | Purpose |
|------|---------|
| `ProgressiveStagesJEIPlugin.java` | `@JeiPlugin` plugin. `onRuntimeAvailable()` caches `IJeiRuntime` and hides locked items/fluids from JEI's ingredient list. `refreshVisibility()` is called on stage changes to show/hide items dynamically. Creates blacklist of locked `ItemStack`s and `FluidStack`s. |

### `client/renderer/` — Rendering

| File | Purpose |
|------|---------|
| `LockIconRenderer.java` | Renders the lock icon texture (`textures/gui/lock_icon.png`) as an overlay on item slots. Configurable position (`lock_icon_position`) and size (`lock_icon_size`). Uses `ThreadLocal<Boolean> insideSlotWidget` flag to prevent double-rendering when called from both `EmiScreenManagerMixin` and `EmiStackWidgetMixin`. |

### `server/` — Server-Side

| File | Purpose |
|------|---------|
| `ServerEventHandler.java` | `@EventBusSubscriber` for game events. `onServerStarting()` is the initialization entry point (see Architecture). `onPlayerJoin()` grants starting stages, syncs data to client. Also handles: item pickup blocking, item use blocking, block placement/interaction blocking, dimension travel blocking, entity attack blocking, inventory scanning on tick — all by calling the appropriate Enforcer classes. |

### `server/commands/` — Commands

| File | Purpose |
|------|---------|
| `StageCommand.java` | Registers `/stage` and `/progressivestages` command trees via Brigadier. `/stage grant` has dependency bypass confirmation (10-second expiry tracked in `bypassConfirmations` ConcurrentHashMap). `/progressivestages reload` reloads stages + triggers + re-syncs clients. `/progressivestages validate` checks TOML syntax. `/progressivestages ftb status` prints FTB integration diagnostics. `/progressivestages trigger reset` clears one-time trigger history. Tab-completion for stage IDs and player names. |

### `server/enforcement/` — Lock Enforcers

All enforcers follow the same pattern: static `can*()` method → check config toggle → check creative bypass → query `LockRegistry` → check `StageManager.hasStage()`.

| File | Purpose |
|------|---------|
| `ItemEnforcer.java` | `canUseItem()`, `canPickupItem()`, `canHoldItem()`. Tracks `messageCooldowns` (`Map<UUID, Map<String, Long>>`) to prevent chat spam. `notifyLockedWithCooldown()` sends lock message + plays configurable sound. Central notification hub used by other enforcers. |
| `BlockEnforcer.java` | `canPlaceBlock()`, `canInteractWithBlock()`. Delegates notification to `ItemEnforcer.notifyLocked()`. |
| `EntityEnforcer.java` | `canAttackEntity()`. Queries `LockRegistry.getRequiredStageForEntity()`. |
| `DimensionEnforcer.java` | `canTravelToDimension()`. Queries `LockRegistry.getRequiredStageForDimension()`. |
| `RecipeEnforcer.java` | `canCraftRecipe()`. Queries `LockRegistry.getRequiredStageForRecipe()`. |
| `InteractionEnforcer.java` | `canInteract()` for item-on-block, block-right-click, item-on-entity combinations. Supports tag matching for target blocks (`#minecraft:logs`) and held items. Used for Create-style mechanics. |
| `InventoryScanner.java` | `scanAndDropLockedItems()` iterates player inventory (main, armor, offhand), drops locked items as `ItemEntity`s. Called periodically from `ServerEventHandler` based on `inventory_scan_frequency` config. |

### `server/loader/` — TOML Loading

| File | Purpose |
|------|---------|
| `StageFileLoader.java` | Singleton. `initialize()` scans `config/ProgressiveStages/*.toml` (skipping `triggers.toml`), parses each via `StageFileParser`, registers with `StageOrder`, then calls `registerLocksFromStages()` to populate `LockRegistry`. `reload()` clears all caches and re-loads. Also generates default stage files if none exist. |
| `StageFileParser.java` | Stateless parser. `parseStageFile(Path)` returns `ParseResult` (success/syntaxError/validationError). Uses NightConfig `TomlParser`. Extracts `[stage]` section → `StageDefinition`, `[locks]` section → `LockDefinition`. Handles `dependency` field (string or list). Handles all lock arrays including interactions (`[[locks.interactions]]`). |

### `server/triggers/` — Auto-Grant Triggers

| File | Purpose |
|------|---------|
| `TriggerConfigLoader.java` | Loads `config/ProgressiveStages/triggers.toml`. Sections: `[advancements]`, `[items]`, `[dimensions]`, `[bosses]`. Registers mappings with each trigger handler class. Generates default triggers file if absent. |
| `AdvancementStageGrants.java` | `@SubscribeEvent AdvancementEvent.AdvancementEarnEvent`. Maps advancement ID → stage ID. Grants via `ProgressiveStagesAPI.grantStage()` with `StageCause.ADVANCEMENT`. |
| `ItemPickupStageGrants.java` | `@SubscribeEvent ItemEntityPickupEvent`. Maps item ID → stage ID. Also scans inventory on `PlayerEvent.PlayerLoggedInEvent` to catch items already held. |
| `DimensionStageGrants.java` | `@SubscribeEvent PlayerEvent.PlayerChangedDimensionEvent`. Maps dimension ID → stage ID. One-time per player via `TriggerPersistence`. |
| `BossKillStageGrants.java` | `@SubscribeEvent LivingDeathEvent`. Maps entity type ID → stage ID. Checks if killed by a player. One-time per player via `TriggerPersistence`. |
| `TriggerPersistence.java` | NeoForge `SavedData` anchored to overworld. Stores `Map<String, Set<UUID>>` (trigger key → set of player UUIDs that already triggered). Persisted in `world/data/progressivestages_triggers.dat`. `hasTriggered()` / `markTriggered()` / `resetTrigger()`. |

### `server/integration/` — FTB Teams

| File | Purpose |
|------|---------|
| `FTBTeamsIntegration.java` | `@EventBusSubscriber`. Polls FTB Teams API every 20 ticks to detect team changes (FTB Teams doesn't fire NeoForge events). Tracks `lastKnownTeams` per player. On team change, calls `TeamStageSync` to handle stage inheritance. Tracks player login for initial team detection. |

### `compat/ftbquests/` — FTB Quests Integration

| File | Purpose |
|------|---------|
| `FTBQuestsCompat.java` | Integration entry point. Checks `ModList.get().isLoaded("ftbquests")`. Subscribes to `StageChangeEvent` and `StagesBulkChangedEvent` to queue player rechecks. Processes rechecks on `ServerTickEvent` with configurable budget (`recheck_budget_per_tick`). Debounces multiple stage changes into one recheck per player per tick. |
| `FtbQuestsHooks.java` | Isolated reflection adapter for ALL FTB API calls. Registers ProgressiveStages as `StageHelper.INSTANCE.setProvider()` via dynamic proxy. Caches reflection handles. Re-entrancy guard (`recheckInProgress` set) prevents recursive loops (quest reward → stage grant → recheck). Single-warning-per-error pattern. |
| `StageRequirementHelper.java` | Bridge for checking stage requirements in FTB Quests context. `hasStage()` (server-side via `ProgressiveStagesAPI`), `hasStageClient()` (client-side via `ClientStageCache`). Used by `QuestMixin` and `ChapterMixin`. Fails open (returns `true` on error). |

### `mixin/` — Core Mixins (`progressivestages.mixins.json`, `required: true`)

| File | Purpose |
|------|---------|
| `AbstractContainerMenuMixin.java` | `@Mixin(AbstractContainerMenu)`. Injects into `doClick` to prevent moving locked items in containers (chest drag exploit fix). Cancels click if item in slot is locked for the player. |
| `CraftingMenuMixin.java` | `@Mixin(CraftingMenu)`. Injects into `slotChangedCraftingGrid` at TAIL. Clears crafting result slot if the output item is locked. Enforces `hide_locked_recipe_output` config. |

### `mixin/client/` — Client Mixins (in core mixin config, `client` section)

| File | Purpose |
|------|---------|
| `EmiScreenManagerMixin.java` | `@Mixin(EmiScreenManager.ScreenSpace)`. Injects at TAIL of `render` method. Iterates visible EMI stacks and renders lock icon overlay via `LockIconRenderer` on locked items in the EMI side panel. |
| `EmiStackWidgetMixin.java` | `@Mixin(SlotWidget)`. Injects at HEAD/TAIL of `render`. Sets `LockIconRenderer.enterSlotWidget()` flag at HEAD, renders lock overlay on locked items in recipe view slots at TAIL. Prevents double-rendering with the ScreenManager mixin. |

### `mixin/ftbquests/` — FTB Quests Mixins (`progressivestages-ftbquests.mixins.json`, `required: false`)

| File | Purpose |
|------|---------|
| `QuestMixin.java` | `@Mixin(Quest)`. Adds `progressivestages$requiredStage` field. Injects into `fillConfigGroup` to add "Stage Required" text field in quest properties UI. Injects into `isVisible` to hide quest if player lacks the stage. Injects into `writeData`/`readData`/`writeNetData`/`readNetData` for save/sync. |
| `ChapterMixin.java` | `@Mixin(Chapter)`. Identical pattern to QuestMixin but for chapters. Adds "Stage Required" field to chapter properties. Gates entire chapters behind stages. |

---

## Lock Cascade Table

| Lock Type | Items | Blocks | Entities | Fluids (EMI/JEI) |
|-----------|:-----:|:------:|:--------:|:----------------:|
| `mods = ["modid"]` | ✅ | ✅ | ✅ | ✅ |
| `item_mods = ["modid"]` | ✅ | ❌ | ❌ | ❌ |
| `block_mods = ["modid"]` | ❌ | ✅ | ❌ | ❌ |
| `entity_mods = ["modid"]` | ❌ | ❌ | ✅ | ❌ |
| `fluid_mods = ["modid"]` | ❌ | ❌ | ❌ | ✅ |
| `names = ["pattern"]` | ✅ | ✅ | ✅ | ✅ |
| `items = [...]` | ✅ | ❌ | ❌ | ❌ |
| `blocks = [...]` | ❌ | ✅ | ❌ | ❌ |
| `entities = [...]` | ❌ | ❌ | ✅ | ❌ |
| `fluids = [...]` | ❌ | ❌ | ❌ | ✅ |

Whitelists (`unlocked_items`, `unlocked_blocks`, `unlocked_entities`, `unlocked_fluids`) take priority over ALL lock types.

---

## Commands

| Command | Permission | Description |
|---------|------------|-------------|
| `/stage grant <player> <stage>` | OP | Grant a stage (prompts to bypass if dependencies unmet) |
| `/stage revoke <player> <stage>` | OP | Revoke a stage |
| `/stage list [player]` | OP | List stages for a player |
| `/stage check <player> <stage>` | OP | Check if player has a stage |
| `/stage info <stage>` | OP | Show full stage definition |
| `/progressivestages reload` | OP | Reload stage files + triggers.toml; re-syncs clients |
| `/progressivestages validate` | OP | Validate stage TOML syntax and IDs |
| `/progressivestages ftb status [player]` | OP | Debug FTB Teams/Quests integration |
| `/progressivestages trigger reset <player> <dimension\|boss> <key>` | OP | Reset a one-time trigger |

---

## Adding New Features

- **New lock type** → add maps to `LockRegistry`, parse in `StageFileParser`, add enforcer in `server/enforcement/`, sync in `NetworkHandler`, update `LockDefinition`
- **New enforcer** → create static class in `server/enforcement/`, follow pattern: check config toggle → creative bypass → `LockRegistry` query → `StageManager.hasStage()` → cancel event. Wire from `ServerEventHandler`.
- **New trigger** → add class in `server/triggers/` with `@SubscribeEvent`, register in `ServerEventHandler.onServerStarting()`, parse config section in `TriggerConfigLoader`
- **New mixin** → add to `progressivestages.mixins.json` (core) or `progressivestages-ftbquests.mixins.json` (FTB-only, `required: false`)
- **New config key** → add to `StageConfig.java` with `ModConfigSpec.Builder`, add static getter method
- **New network packet** → add payload record + stream codec in `NetworkHandler.java`, register in `registerPayloads()`

## Key Runtime Files

| File | Purpose |
|------|---------|
| `config/progressivestages.toml` | Main mod settings (enforcement, EMI, FTB integration) |
| `config/ProgressiveStages/*.toml` | Stage definitions (one file per stage or grouped) |
| `config/ProgressiveStages/triggers.toml` | Trigger mappings (advancements, items, dimensions, bosses) |
| `world/data/progressivestages_triggers.dat` | One-time trigger history (dimension/boss triggers) |

## Gotchas

- `names = ["pattern"]` locks iterate ALL registered items/blocks/entities/fluids — prefer exact IDs for performance in large modpacks (~50–500ms at startup with 10k+ items)
- Fluid locks only affect EMI/JEI visibility, NOT fluid transport in machines/pipes
- `Constants.MOD_VERSION` is stale (`1.1.0`) — authoritative version is `mod_version` in `gradle.properties` (`1.3`)
- Two mixin configs exist: core (`required: true`) and FTB Quests (`required: false`)
- `Progressivestages.java` is ~1200 lines because it contains embedded TOML template strings for default file generation
- `FTBTeamsIntegration` is the one exception to "no tick polling" — it polls every 20 ticks because FTB Teams doesn't fire NeoForge events
- Stage files are global (`config/ProgressiveStages/`), NOT per-world — stages apply to all worlds on the server
- `StagesBulkChangedEvent` does NOT fire individual `StageChangeEvent`s — listeners must handle both event types
