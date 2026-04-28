# mindevis/stagesqx — Implementation Analysis (master @ fetched)

This report analyses the StagesQX NeoForge mod (1.21.1, NeoForge 21.1.226+) so its features can be ported to ProgressiveStages 2.0. It is intended as the authoritative reference: feature-by-feature behaviour, exact code for non-obvious paths, and a port priority table at the end.

---

## 1. Architecture & high-level model

StagesQX is a **stage-locked progression mod**. Every progression "stage" is a TOML file under `config/stagesqx/<stage_id>.toml`. Each stage declares two parallel resource lists:

- `[locks]` — content that **requires** this stage id to be accessible. While the player has the stage id, the locks apply (this is the key v2.0 inversion described below).
- `[unlocks]` — exception whitelists for this stage's own requirements.

Both `locks` and `unlocks` use the same shape: `items`, `mods`, `fluids`, `dimensions`, `entities`. A boolean `minecraft = true` on the stage (root or under `[locks]`) extends locks to the `minecraft:` namespace with parent-stage inheritance semantics.

Player ownership is stored in a per-player NBT compound `stagesqx.granted_stages`. Stages have `dependency = [...]` for prerequisite progression — when a player owns a leaf stage, its prerequisites are treated as satisfied for gating but lock semantics still apply.

A single `StageCatalog` snapshot holds:
- `Map<String, StageDefinition>` keyed by id (insertion order = load order = "ordered stage ids" used as deterministic tie-break).
- Reverse indexes (id→stages, modNs→stages, fluidId→stages, dimId→stages, entityId→stages) precomputed for O(1) gating lookups.

The catalog is rebuilt from disk on `ServerAboutToStartEvent`, on `/stagesqx reload`, and on stage create/delete. It is then synced to clients via two custom payloads.

### v2.0 INVERSION — "require stage to unlock"

The fork's central design choice (relative to "classic GameStages"):

> A stage's `[locks]` section lists content that the player **must hold the stage id** to use. Owning the stage is the unlock; not owning it is the block.

The exact compare site is `StageAccess.blockedByMissingRequiredStages`:

```java
private static boolean blockedByMissingRequiredStages(Set<String> gatingStages, Set<String> effectiveAccessStages) {
    return !gatingStages.isEmpty() && !effectiveAccessStages.containsAll(gatingStages);
}
```

`gatingStages` = which stages list this content (or its mod namespace) under `[locks]`.
`effectiveAccessStages` = the player's owned stages + transitive dependencies.

Block iff some stage gates the content AND the player does not own all required stages.

This means **revoking** a stage drops its locks; **granting** it activates them. `STARTING_STAGES` config and `ftbquests` Stage Reward both do this on login / quest completion.

---

## 2. File-by-file walkthrough

### 2.1 com/stagesqx/StagesQXConstants.java
- Mod id `"stagesqx"`. `id(path) -> ResourceLocation.fromNamespaceAndPath(MOD_ID, path)`.

### 2.2 com/stagesqx/stage/ — domain layer

#### StageGateLists.java (record)
Holds the five locked lists:
```java
public record StageGateLists(
    Set<ResourceLocation> items,
    Set<String> mods,
    Set<ResourceLocation> fluids,
    Set<ResourceLocation> dimensions,
    Set<ResourceLocation> entities
) { ... }
```
Empty singleton via `empty()`. Same shape used for both `[locks]` and `[unlocks]`.

#### StageDefinition.java (record)
```java
public record StageDefinition(
    String id, String displayName, String description, String icon, String unlockMessage,
    List<String> dependency,
    boolean minecraft,
    StageGateLists locks,
    StageGateLists unlocks
) { ... }
```
- `effectiveDisplayName()` falls back to id when displayName blank.
- Builder helpers `withLocksAndUnlocks`, `withMeta`, `mergeFileId`.
- `dependency()` is logical prerequisites — not lock content.

#### StageCatalog.java
Central snapshot with reverse indexes. Build path constructs five maps in parallel for items/mods/fluids/dims/entities. The `mods` index also records `"minecraft"` when the stage's `minecraft=true`:

```java
private static Map<String, Set<String>> buildModIndex(Map<String, StageDefinition> byId) {
    Map<String, Set<String>> map = new HashMap<>();
    for (StageDefinition def : byId.values()) {
        for (String mod : def.locks().mods()) {
            map.computeIfAbsent(mod, k -> new HashSet<>()).add(def.id());
        }
        if (def.minecraft()) {
            map.computeIfAbsent("minecraft", k -> new HashSet<>()).add(def.id());
        }
    }
    return freezeNested(map);
}
```

The catalog provides `stagesGatingX(...)` (raw — gating from item id + namespace) and `effectiveStagesGatingX(...)` (after applying that stage's own `[unlocks]` whitelist). Notably, `effectiveStagesGatingItem`:

```java
public Set<String> effectiveStagesGatingItem(ResourceLocation itemId, String modNamespace) {
    Set<String> gating = new HashSet<>(stagesGatingItem(itemId, modNamespace));
    gating.removeIf(sid -> itemUnlockedByStage(sid, itemId, modNamespace));
    return gating.isEmpty() ? Set.of() : Set.copyOf(gating);
}

private boolean itemUnlockedByStage(String stageId, ResourceLocation itemId, String modNamespace) {
    StageDefinition def = byId.get(stageId);
    if (def == null) return false;
    StageGateLists u = def.unlocks();
    return u.items().contains(itemId) || u.mods().contains(modNamespace);
}
```

So a stage that locks an entire mod can carve out specific items via `[unlocks].items`.

`namespacesUnderStageModLocks()` returns every namespace that any stage's `locks.mods` references (plus `"minecraft"` if any stage has `minecraft=true`) — used by JEI/EMI debug to limit log scope.

#### StageAccess.java — gating logic (heart of the mod)

Key operations — copy these verbatim:

```java
public static boolean bypassesLocks(Player player) {
    return player == null || player.isCreative() || player.isSpectator();
}

public static Set<String> effectiveAccessStages(StageCatalog catalog, Set<String> playerStagesOwned) {
    if (playerStagesOwned.isEmpty()) return Set.of();
    if (catalog.isEmpty()) return Set.copyOf(playerStagesOwned);
    Set<String> acc = new HashSet<>(playerStagesOwned);
    for (String s : playerStagesOwned) acc.addAll(transitiveDependencyIds(catalog, s));
    return Set.copyOf(acc);
}
```

`transitiveDependencyIds` is a BFS over `dependency()` lists.

**Vanilla namespace inheritance** (the key minecraft=true detail): when computing lock stages for a `minecraft:` resource, we extend `effectiveLockStages` with any prerequisite stage that has `minecraft=true`:

```java
public static Set<String> effectiveLockStagesForVanillaNamespace(
    StageCatalog catalog, Set<String> playerStagesOwned
) {
    Set<String> eff = effectiveLockStages(catalog, playerStagesOwned);
    if (eff.isEmpty() || playerStagesOwned.isEmpty()) return eff;
    Set<String> active = new HashSet<>(eff);
    for (String t : eff) {
        for (String s : transitiveDependencyIds(catalog, t)) {
            if (!playerStagesOwned.contains(s)) continue;
            StageDefinition d = catalog.get(s);
            if (d != null && d.minecraft()) active.add(s);
        }
    }
    return Set.copyOf(active);
}
```

This stops `minecraft:water` (or any "vanilla-linked" stack) from leaking through after granting a child stage that doesn't itself set `minecraft=true`.

`isItemBlocked`, `isAbstractIngredientBlocked` (used for fluids and block ids), `isModIdBlocked`, `isFluidBlocked`, `isDimensionBlocked`, `isEntityBlocked` all funnel into `blockedByMissingRequiredStages` after combining catalogue gating (id + ns) and computing access stages with the resource-namespace–aware path.

There is also a Visual Workbench shim: when checking a block state, if it isn't blocked by its own id, try Visual Workbench's reflection-only `BlockConversionHandler.convertToVanillaBlock(state)` and recheck against the vanilla id so VW-replaced crafting tables still respect locks.

`primaryRestrictingStage(...)` returns the **first** missing required stage in catalog order — used for tooltip and chat messaging.

#### StageRegistry.java
- Process-wide volatile `StageCatalog` reference; `LOCK` for directory paths.
- `discoverStageFiles(Path)` lists `*.toml` files, excluding `stagesqx.toml`, and runs `StageTomlIo.looksLikeStageFile` to filter (so trigger files in the same dir are skipped).
- `loadFromDisk(Path)` parses each into a `StageDefinition`, building an ordered Map.
- `reload(server)` and `bootstrapFromDisk()` swap the volatile catalog.
- `writeTemplateFile(file, stageId, content)` writes a template comprising **commented-out** keys including the full `[locks]` and `[unlocks]` shape, with a comment explaining legacy root-level locks. Important detail: every key is prefixed `# ` so a freshly-created stage is inert until the user uncomments. The collected catalog (mods/items/fluids/dims/entities) is dumped as a TOML reference.

#### StageTomlIo.java
- `looksLikeStageFile(Path)`: opens the toml, returns true if it contains any of `locks`, `unlocks`, `items`, `mods`, `fluids`, `dimensions`, `entities`, `minecraft`, `dependency`. (Heuristic to coexist with trigger files.)
- `parse(fileId, config)`:
  - Reads `display_name`, `description`, `icon`, `unlock_message`, `dependency` (string list).
  - Looks for `[locks]` subtable — if present, parses both `[locks]` and (optional) `[unlocks]` strictly.
  - **Legacy fallback**: when no `[locks]` table exists, reads root-level `items`, `mods`, `fluids`, `dimensions`, `entities` as locks, and unlocks must still be inside `[unlocks]`.
  - `minecraft` is read from root and from `[locks].minecraft`; either being true triggers vanilla-namespace gating.
  - `id` in file is honored only if it equals the file id; otherwise file id wins (with warn).
- `isValidStageName(name)` regex `^[a-z0-9._-]+$`.

#### StageValidator.java
- `validateMainConfig(path)` opens `stagesqx.toml`.
- `validateAll(stagesDir, server)` parses every stage, runs `validateResources` against live registries (item/fluid/entity/dimension/level keys) and `namespaceAppearsInRegistries(modId)` — warning-only entries don't fail. Mod namespace check tries items/blocks/fluids registry namespaces.
- `detectCycles(defs)` is a 3-color DFS; failing cycles reported as one summary message.

#### StageCatalogSerialization.java
NBT codec for the catalog (stages list with id, display, dep, minecraft, locks gateLists, unlocks gateLists). `writeStringList` and `writeRlList` are sorted for deterministic serialization. `readStage` falls back to legacy schema when no `locks` compound is present.

### 2.3 com/stagesqx/neoforge/ — runtime layer

#### StagesQXNeoForge.java
- `@Mod` constructor:
  - Registers `StagesQXModConfig.SPEC` as `Type.COMMON` at `stagesqx/stagesqx.toml`.
  - `PathSetup.init()` creates `config/stagesqx/`, sets `setStagesDirectory` and `setTriggersDirectory` (same dir).
  - Subscribes mod-bus listeners: `commonSetup`, `StageNetwork::register`.
  - Forge bus: `RegisterCommandsEvent`, `ServerAboutToStartEvent`.
  - On client, calls `StagesQXNeoForgeClient.init(modBus)`.
- `commonSetup` enqueues `StageRegistry.bootstrapFromDisk()`, then loads optional integrations via reflection: Curios via `Class.forName(...).getMethod("register").invoke(null)`; FTB Library/Quests via `StagesQXFtbLibraryStageProvider.tryRegister()`.
- `serverAboutToStart` triggers `StageRegistry.reload(server)`.

#### StagesQXModConfig.java — every config key

| Key (TOML name) | Type | Default | Meaning |
|---|---|---|---|
| `logOperations` | bool | `true` | Log create/validate/reload to server log. |
| `debug` | bool | `false` | Enable JEI/EMI diagnostics loggers. |
| `debug_jei_max_log_lines` | int [0, 50000] | `500` | Max debug lines per JEI refresh. |
| `debug_jei_log_vanilla` | bool | `false` | Include `minecraft:` entries in JEI debug. |
| `debug_emi_max_log_lines` | int [0, 50000] | `2000` | Max debug lines per EMI reload. |
| `debug_emi_log_vanilla` | bool | `true` | Include vanilla in EMI debug. |
| `ftbquests_team_mode` | bool | `false` | When true, FTBQ stage checks read FTB Teams; when false, per-player StagesQX. |
| `starting_stages` | List<String> (allowEmpty) | `[]` | Stage ids granted on login. **Validated by `isValidStartingStageEntry`**. |
| `play_lock_sound` | bool | `true` | Play denial sound. |
| `lock_sound` | String | `"minecraft:block.note_block.pling"` | Sound event id. |
| `lock_sound_volume` | double [0.0, 1.0] | `1.0` | |
| `lock_sound_pitch` | double [0.5, 2.0] | `1.0` | |
| `reveal_stage_names_only_to_operators` | bool | `true` | If true, non-ops see generic chat/tooltip wording — server forwards this flag in owned-stages payload. |
| `eject_blocked_inventory_items` | bool | `false` | Periodically eject locked stacks. |
| `eject_blocked_inventory_interval_ticks` | int [5, 1200] | `40` | Interval for ejection scan. |

There is **no** `&` color-code parser in the fork — chat messages are `Component.translatable(...)` keys (see `lang/en_us.json`). All "configurable text" is via translation overrides; users edit the resource pack lang. (This contradicts the spec's hint about `&` color codes — that feature does NOT exist in stagesqx; report it as not present.)

#### StagesQXNeoForgeClient.java — client init + lock overlay
Item decorator drawing a lock above each blocked stack:

```java
private static final class LockedItemDecorator implements IItemDecorator {
    @Override
    public boolean render(GuiGraphics graphics, Font font, ItemStack stack, int x, int y) {
        if (!StageClientView.isBlocked(stack)) return false;
        if (stackWalkAny(JEI_RENDER_STACK, f -> {
            String n = f.getClassName();
            return n.startsWith("mezz.jei.");
        })) {
            return false;  // skip when JEI is the caller — JEI renders its own greying
        }
        // Same Z lift as vanilla stack count in renderItemDecorations
        // — item mesh is drawn at ~150, we must draw above it.
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 200.0F);
        graphics.fill(RenderType.guiOverlay(), x, y, x + 16, y + 16, 0x80484848);
        String lock = "🔒";  // 🔒
        int tw = font.width(lock);
        int lockX = x + (16 - tw) / 2;
        int lockY = y + (16 - font.lineHeight) / 2;
        graphics.drawString(font, lock, lockX, lockY, 0xFFFFFFFF, true);
        graphics.pose().popPose();
        return true;
    }
}
```
Notes:
- `RegisterItemDecorationsEvent` registers the same decorator instance for every Item via `BuiltInRegistries.ITEM.forEach`.
- The `+200.0F` Z translation is the load-bearing trick — vanilla item mesh sits around Z=150, vanilla stack count uses ~200.
- A JEI stack walk avoids painting on top of JEI's own rendering pipeline (which already greys hidden ingredients via list rebuild).

Tooltip injection adds `tooltip.stagesqx.blocked.title` plus either `.stage` (with display name) or `.hint_generic` based on `mayShowRestrictingStageNameClient`.

#### ClientStageData.java
Holds two volatile fields: `CATALOG` (AtomicReference<StageCatalog>) and `OWNED` (`Set<String>`), plus `hideStageNamesFromNonOps` mirrored from server. `acceptCatalog`/`acceptOwned` both call `StagesQXClientHooks.onCatalogUpdated()` which schedules JEI + EMI refreshes via `Minecraft.getInstance().execute(...)`.

#### StageNetwork.java
Two custom payloads:
- `TYPE_CATALOG = "sync_catalog"` — full StageCatalogSerialization NBT.
- `TYPE_OWNED = "sync_owned"` — `{ stages: ListTag<String>, hide_stage_names: byte }`.

Both use `ByteBufCodecs.COMPOUND_TAG`. Methods: `syncCatalog(player)`, `syncPlayerStages(player)`, `syncAllTo(player)`, `broadcastCatalog(server)`. The owned payload includes `REVEAL_STAGE_NAMES_ONLY_TO_OPERATORS.get()` so the client knows whether to hide names.

#### PlayerStageStore.java
Persistent NBT lives at `player.getPersistentData().getCompound("stagesqx").get("granted_stages")` (a sorted ListTag<String>). Mutators: `set(player, stages)`, `grant`, `revoke`, `replaceAll` — each calls `StageNetwork.syncPlayerStages(player)` after writing.

`removeStageFromAll(server, stageId)` clears the id from every online player's set — used by `/stagesqx delete`.

`dependenciesMet` and `missingDependencies` walk a stage's `dependency()`. `applyStartingStagesFromConfig(player)` is called on `PlayerLoggedInEvent`:

```java
public static void applyStartingStagesFromConfig(ServerPlayer player) {
    List<? extends String> raw = StagesQXModConfig.STARTING_STAGES.get();
    if (raw == null || raw.isEmpty()) return;
    Set<String> next = new LinkedHashSet<>(get(player));
    boolean changed = false;
    for (String s : raw) {
        if (s == null) continue;
        String t = s.trim();
        if (t.isEmpty() || !StageTomlIo.isValidStageName(t)) continue;
        if (next.add(t)) changed = true;
    }
    if (changed) replaceAll(player, next);
}
```
Called from `StageServerEvents.onLogin` immediately before `StageNetwork.syncAllTo(sp)`. There is no team-sync interaction in solo mode; team-sync happens through the FTB Library provider proxy when team-mode is on.

#### StageDisclosure.java
Single chokepoint for "may we name the restricting stage?" — server side checks `player.hasPermissions(2)` when the config is true; client side uses `Minecraft.getInstance().player.hasPermissions(2)` against the mirrored flag.

#### StageFeedback.java
Builds chat messages with prefix `message.stagesqx.prefix`. Branches:
- `notifyBlockedForBlock` first tries the block as an item stack, falls back to `primaryRestrictingStageForAbstractId(blockId)`. Sends `block_locked_active_stage` or `block_locked_generic`.
- `notifyBlocked(stack)` uses `primaryRestrictingStage` and `item_locked_active_stage|generic`.
- `notifyBlockedFluid(fluidId)` and `notifyBlockedDimension(dimId)` always include the resource id when allowed.
- After every send, calls `LockSounds.tryPlayFor(player)`.

#### LockSounds.java
- 4-tick per-player cooldown (`COOLDOWN_TICKS = 4L`) keyed on UUID, `ConcurrentHashMap<UUID, Long>`.
- Resolves `lock_sound` config string via `BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.parse(...))`.
- Uses `player.playNotifySound(sound, SoundSource.MASTER, volume, pitch)`.

#### StageInventoryEject.java — eject blocked inventory
```java
public static void ejectBlockedStacks(ServerPlayer sp) {
    StageCatalog cat = StageRegistry.getCatalog();
    if (cat.isEmpty()) return;
    Set<String> owned = PlayerStageStore.get(sp);
    Inventory inv = sp.getInventory();
    ejectFromList(sp, cat, owned, inv.items);
    ejectFromList(sp, cat, owned, inv.armor);
    ejectFromList(sp, cat, owned, inv.offhand);
}

private static void ejectFromList(ServerPlayer sp, StageCatalog cat, Set<String> owned,
    NonNullList<ItemStack> list) {
    for (int i = 0; i < list.size(); i++) {
        ItemStack st = list.get(i);
        if (st.isEmpty()) continue;
        if (!StageAccess.isItemBlocked(cat, owned, st)) continue;
        sp.drop(st.copy(), true);
        list.set(i, ItemStack.EMPTY);
    }
}
```
- **Event**: `PlayerTickEvent.Post` (server side) gated by `EJECT_BLOCKED_INVENTORY_ITEMS` and `tickCount % interval == 0`.
- **Scan**: only main inventory (`items`), armor, offhand. Does NOT touch curios/baubles, ender chest, or external mod containers.
- **Destination**: dropped at player's feet via `sp.drop(stack, true)` — second arg traceItem=true so it's traceable to the player.
- Skipped when player is creative/spectator (`bypassesLocks`).

#### StageServerEvents.java
Single event handler class with `@EventBusSubscriber(modid = StagesQXConstants.MOD_ID)`. Hooks:
- `ItemEntityPickupEvent.Pre` → cancel pickup of blocked stacks (`setCanPickup(TriState.FALSE)`).
- `ItemTossEvent` → cancel drop of blocked stacks (so a player can't fling banned items out for a friend pre-stage).
- `EntityTravelToDimensionEvent` → cancel dimension travel.
- `LivingIncomingDamageEvent` → cancel attack if weapon is blocked OR target entity is blocked.
- `RightClickItem`, `RightClickBlock`, `EntityInteract`, `EntityInteractSpecific`, `LeftClickBlock` (START only) → cancel for blocked held item, contained fluid, or right-click target block.
- `PlayerLoggedInEvent` → `applyStartingStagesFromConfig` then `syncAllTo`.
- `PlayerTickEvent.Post` → run inventory eject every N ticks.

`shouldCancelItemOrFluidUse` combines item lock check + `FluidUtil.getFluidContained(stack)` + fluid registry id. This catches buckets, modded fluid containers etc.

#### StageCommands.java
`/stagesqx` requires permission level 2. Subcommands: `create <name>`, `delete <name>`, `validate [<name>|config]`, `reload`, `grant <player> <stage>`, `revoke <player> <stage>`, `list stages [<player>]`. `delete` removes the stage from every player and reloads + syncs. `grant` rejects if dependencies missing.

#### StageTemplateFactory.java
Collects the entire registry universe — all mods, all items, all fluids, all dimension keys (from `server.levelKeys()`), all entity types — and returns a `StageRegistry.TemplateContent` record. Used by `/stagesqx create` so a new stage's template lists every modid/itemid as commented examples for copy-paste.

### 2.4 Mixin layer (com/stagesqx/neoforge/mixin)

#### stagesqx.mixins.json
```json
{
  "required": true,
  "package": "com.stagesqx.neoforge.mixin",
  "compatibilityLevel": "JAVA_21",
  "minVersion": "0.8",
  "plugin": "com.stagesqx.neoforge.mixin.StagesQXMixinPlugin",
  "mixins": [
    "SlotMixin",
    "AbstractContainerMenuMixin",
    "EnchantmentMenuMixin",
    "ItemCombinerMenuAccessor",
    "AnvilMenuMixin",
    "ftbquests.ChapterStageGateMixin",
    "ftbquests.QuestStageGateMixin",
    "ftbquests.TeamDataStageGateMixin"
  ],
  "injectors": { "defaultRequire": 1 }
}
```

`StagesQXMixinPlugin.shouldApplyMixin` skips `ftbquests.*` mixins when FTB Quests is not loaded (checked once in `onLoad`).

#### SlotMixin → Slot.mayPickup
HEAD-injection cancellable. If owner is a non-creative ServerPlayer and the slot's stack is blocked, send feedback and return `false`. This prevents pickup of a locked stack from any container slot. (Feedback is sent server-side per attempt — relies on `LockSounds` cooldown for spam control.)

#### AbstractContainerMenuMixin → AbstractContainerMenu.clicked
HEAD-injection cancellable. Reads `getCarried()`. Cancels for `PICKUP|QUICK_MOVE|SWAP|THROW` click types when carried stack is blocked.

#### EnchantmentMenuMixin → EnchantmentMenu.clickMenuButton
HEAD CIR returnable false. Blocks enchanting a locked input item.

#### AnvilMenuMixin → AnvilMenu.createResult (TAIL)
After result computed, if INPUT/ADDITIONAL/RESULT slot has a blocked stack, calls `getSlot(RESULT_SLOT).set(ItemStack.EMPTY)` and notifies. Uses `ItemCombinerMenuAccessor` to fetch the menu's player.

#### ItemCombinerMenuAccessor → ItemCombinerMenu.player accessor
Just exposes the protected `player` field as `stagesqx$getPlayer()`.

#### ftbquests/QuestStageGateMixin → Quest
Implements `StagesQXFtbQuestsRequiredStageHolder`. `@Unique` field `stagesqx$requiredStageId = ""`. Adds:
- `writeData/readData(CompoundTag, HolderLookup.Provider)` → save/load NBT key `stagesqx_required_stage`.
- `writeNetData/readNetData(RegistryFriendlyByteBuf)` → sync over wire.
- `fillConfigGroup(@Coerce Object config)` → call `StagesQXFtbConfigCompat.tryAddRequiredStageString(config, current, setter)` to add `required_stage` to the editor config group.
- `isVisible(TeamData data)` HEAD-cancel returning false when stage isn't met.

#### ftbquests/ChapterStageGateMixin → Chapter
Same pattern as Quest mixin but on Chapter — gates whole chapters.

#### ftbquests/TeamDataStageGateMixin → TeamData.canStartTasks
HEAD-cancel: if quest implements `StagesQXFtbQuestsRequiredStageHolder` and required stage not met server-side, return false. This is what stops players from progressing tasks of a stage-gated quest.

### 2.5 Integration layer

#### integration/jei/JeiStagesSupport.java  — JEI deep integration

This file implements the JEI ingredient hiding. The strategy is:

1. Register an `IIngredientManager.IIngredientListener` on `setRuntime(runtime)`. Both add and remove notifications schedule a coalesced refresh via `scheduleRefresh()` (uses an `AtomicBoolean refreshQueued` and a single `mc.execute(...)` followed by a second `mc.execute` to reapply blacklist after JEI clears API blacklist on add).

2. `refresh()` re-evaluates everything. For items, fluids, and "other" ingredient types. Picks the API blacklist via reflection:

```java
private static IngredientBlacklistInternal jeiApiBlacklist(IJeiRuntime r) {
    IIngredientVisibility vis = r.getJeiHelpers().getIngredientVisibility();
    if (vis instanceof IngredientVisibility iv) {
        try {
            if (jeiIngredientVisibilityBlacklistField == null) {
                jeiIngredientVisibilityBlacklistField = IngredientVisibility.class.getDeclaredField("blacklist");
                jeiIngredientVisibilityBlacklistField.setAccessible(true);
            }
            IngredientBlacklistInternal bl = (IngredientBlacklistInternal) jeiIngredientVisibilityBlacklistField.get(iv);
            if (bl != null) return bl;
        } catch (ReflectiveOperationException ignored) {}
    }
    IngredientBlacklistInternal bl = scanBlacklistField(vis);
    if (bl != null) return bl;
    return blacklistFromIngredientFilter(r);
}
```

`scanBlacklistField` walks the class hierarchy of `IngredientVisibility` looking for any `IngredientBlacklistInternal` field — survives JEI subclassing.

3. Items pass: build prev/next sets, call `im.removeIngredientsAtRuntime(VanillaTypes.ITEM_STACK, removeStacks)`, then re-add restored items via `im.addIngredientsAtRuntime`, finally walk all typed ingredients to push `apiBlacklist.addIngredientToBlacklist`/`removeIngredientFromBlacklist` so the API blacklist stays in sync.

4. **All FluidStack ingredient types** — JEI may register multiple types whose ingredient class is `FluidStack` (NeoForgeTypes vs vanilla fluids). The fork iterates **every registered** type whose ingredient class assignable to FluidStack:

```java
for (IIngredientType<?> raw : im.getRegisteredIngredientTypes()) {
    if (isFluidStackIngredientType(raw)) {
        fluidTypes.add((IIngredientType<FluidStack>) raw);
    }
}
```
Each goes through the same prev/next add/remove cycle.

5. Fluid identity uses helper-derived RL:
```java
private static ResourceLocation fluidIngredientId(IIngredientHelper<FluidStack> helper, FluidStack fs) {
    if (fs == null || fs.isEmpty()) return null;
    try {
        ResourceLocation rl = helper.getResourceLocation(fs);
        if (rl != null) return rl;
    } catch (Throwable ignored) {}
    Fluid fluid = fs.getFluid();
    if (fluid == null || fluid == Fluids.EMPTY) return null;
    return BuiltInRegistries.FLUID.getKey(fluid);
}
```
Catches Create potions, mod elixirs whose identity differs from the underlying registry fluid.

6. Fluid block check combines: helper RL gating + display-mod gating + UID embedding scan:
```java
return jeiUidDeclaresBlockedContent(helper, fs, cat, lockStagesLeaf, lockStagesVanilla);
```

7. `jeiUidDeclaresBlockedContent` parses the JEI uid string for embedded `namespace:path` segments via regex `Pattern.compile("([a-z0-9_.-]+):([a-z0-9_./-]+)")` — strips a leading `fluid:` prefix first. Each match is checked as both `isModIdBlocked` (namespace) and `isAbstractIngredientBlocked` (full id). This is what catches "modded fluid whose `getFluid()` is `minecraft:water` but whose JEI uid embeds `create:potion`".

8. Generic ingredient types (everything that isn't ITEM_STACK or a FluidStack type — Mekanism gases/pigments, etc.) use `genericStableKey` ("mod:<modId>|<uid>" or "rl:<rl>") so the previously-hidden set survives across reloads even if JEI doesn't remember the original ingredient object. Block check: display-mod, helper RL, UID-embedded scan.

9. **After bulk-add stabilization** — the `scheduleRefresh()` second pass:
```java
mc.execute(() -> {
    refreshQueued.set(false);
    refresh();
    // IngredientBlacklistInternal.onIngredientsAdded clears API blacklist for added stacks; second pass reapplies hides.
    mc.execute(JeiStagesSupport::refresh);
});
```
Two passes because JEI's `onIngredientsAdded` un-blacklists added items.

10. **Ingredient-filter rebuild** — even with `removeIngredientsAtRuntime`, JEI's `IngredientFilter.onIngredientsRemoved` is a no-op; stale `IListElement` rows linger. Forced rebuild:
```java
private static void notifyJeiIngredientFilter(IJeiRuntime r) {
    try {
        if (r.getIngredientFilter() instanceof IngredientFilter filter) {
            filter.rebuildItemFilter();
            filter.updateHidden();
        }
    } catch (Throwable ignored) {}
}
```

Plugin entry: `@JeiPlugin StagesQXJeiRuntimePlugin.onRuntimeAvailable(runtime)` calls `setRuntime` then `refresh()` plus a deferred `mc.execute(JeiStagesSupport::refresh)` for post-load stabilization.

#### integration/emi/StagesQXEmiPlugin.java — EMI hiding

EMI uses a single `EmiRegistry.removeEmiStacks(predicate)` call:
```java
@Override
public void register(EmiRegistry registry) {
    registry.removeEmiStacks(StagesQXEmiPlugin::shouldHide);
}
```

`shouldHide(stack)` first walks `stack.getEmiStacks()` variants then checks the parent. `shouldHideFlat` checks in order:
1. Item stack: `StageClientView.isBlocked(item)`; also checks `FluidUtil.getFluidContained(item)` for buckets.
2. `stack.getId()` is a fluid registry id and that fluid is blocked.
3. `stack.getKey()` is `FluidStack` and its fluid is blocked.
4. `stack.getKey()` is `Fluid` (raw) and blocked.
5. `RecipeViewerModHints.isEmiStackFromBlockedMod(stack, ...)` — uses `stack.getClass().getModule()` to identify the originating mod (catches Mekanism gas stacks whose registry id is in `mekanism:` namespace and class lives in the mekanism module).
6. `resourceIdForEmiStack(stack)` (id, key as RL, key as ResourceKey, key as Holder) → `isAbstractIngredientBlocked`.

`StagesQXEmiHooks.refreshHidden()` calls `dev.emi.emi.runtime.EmiReloadManager.reload()` via reflection, then `scheduleEmiDebugWhenReady` polls `EmiReloadManager.isLoaded()` (cap 400 ticks) before logging the snapshot.

#### integration/RecipeViewerModHints.java
```java
public static Optional<String> owningModIdForClass(Class<?> clazz) {
    if (clazz == null) return Optional.empty();
    Module module = clazz.getModule();
    if (module != null && module.isNamed()) {
        String name = module.getName();
        if (name != null && !name.isEmpty() && ModList.get().isLoaded(name)) {
            return Optional.of(name);
        }
    }
    return Optional.empty();
}
```
Used to discover that an `EmiStack` instance class lives inside a mod jar even when its registry id is `minecraft:` (the "hide by display mod" case). `isEmiStackFromBlockedMod` returns true if either the stack's class or its key's class belongs to a blocked mod namespace.

#### integration/RecipeViewerDebugExplain.java
Pure formatting helpers used in JEI/EMI debug logs; produce strings like `gating=[stage_2] accessStages=[stage_1] → missingRequiredStages=[stage_2]`.

#### integration/curios/CuriosStages.java
On `CurioCanEquipEvent`, if blocked, set `equipResult(TriState.FALSE)` and notify. Loaded by reflection only when curios is present (see `commonSetup`).

#### integration/ftbquests/StagesQXFtbLibraryStageProvider.java
Creates a JDK `Proxy` implementing `dev.ftb.mods.ftblibrary.integration.stages.StageProvider` and installs it via `StageHelper.INSTANCE.setProviderImpl(provider)`. Methods:
- `has(player, stage)` — team mode delegates to `dev.ftb.mods.ftbteams.api.TeamStagesHelper.hasTeamStage`; solo mode reads `PlayerStageStore.get(sp)`.
- `add/remove(player, stage)` — symmetric.
- `sync(player)` → `StageNetwork.syncPlayerStages(sp)`.
- `getName/toString` → `"StagesQXStageProvider"`.

This makes FTB Quests' native Stage Required field, Stage Task, and Stage Reward all flow through StagesQX. Team-sync interaction therefore happens entirely through this proxy — when ftbquests_team_mode=true, login still grants `STARTING_STAGES` via per-player NBT, but FTBQuests questing reads team stages.

#### integration/ftbquests/StagesQXFtbConfigCompat.java
Reflection-only adapter that adds the `required_stage` string entry to FTB's config UI for Chapter/Quest editor:
```java
public static void tryAddRequiredStageString(Object configGroup, String currentValue, Consumer<String> setter) {
    Object qx = getOrCreateSubgroup(configGroup, "stagesqx");
    if (qx == null) return;
    tryInvoke(qx, "setNameKey", new Class<?>[]{String.class}, new Object[]{"StagesQX"});
    addStringEntry(qx, "required_stage", currentValue, setter, "");
}
```
`addStringEntry` tries multiple FTB Library signature variants (`addString(String, String, Consumer, String)` and `addString(String, Supplier, Consumer, String)`), and as a last resort scans all `addString` methods reflectively. Result: a "StagesQX > required_stage" field shows up in the FTB Quests editor's Quest/Chapter config.

#### integration/ftbquests/StagesQXFtbQuestsStageGate.java
- `requiredStageMetForVisibility(stageId)` (client-facing): team mode calls FTB Teams via reflection from local player; solo reads `ClientStageData.getOwnedStages()`.
- `requiredStageMetForServerLogic(stageId)`: team mode tries FTB Teams; solo reads `PlayerStageStore` of `ServerQuestFile.getInstance().getCurrentPlayer()` (reflection-resolved).
- `currentClientPlayer` and `currentFtbQuestsServerPlayer` are reflection helpers so the entire FTBQ gate compiles even when FTB jars are absent.

### 2.6 Resources

#### lang/en_us.json — every translation key (verbatim list)
- `message.stagesqx.prefix` → "[StagesQX] "
- `message.stagesqx.item_locked_active_stage` → "Item locked until stage is obtained: %s"
- `message.stagesqx.item_locked_generic` → "This item is locked by progression."
- `message.stagesqx.block_locked_active_stage` → "This block is locked until stage is obtained: %s"
- `message.stagesqx.block_locked_generic` → "This block is locked by progression."
- `message.stagesqx.fluid_blocked` → "Fluid locked: %s"
- `message.stagesqx.fluid_blocked_generic` → "This fluid is locked by progression."
- `message.stagesqx.dimension_blocked` → "Dimension locked: %s"
- `message.stagesqx.dimension_blocked_generic` → "This dimension is locked by progression."
- `tooltip.stagesqx.blocked.title` → "Item locked"
- `tooltip.stagesqx.blocked.stage` → "Required stage: %s"
- `tooltip.stagesqx.blocked.hint_generic` → "Progress further to use this item."
- `commands.stagesqx.create.invalid_name`, `.create.exists`, `.create.ok`, `.delete.missing`, `.delete.ok`, `.delete.reload_hint`, `.reload.ok`, `.player_not_found`, `.unknown_stage`, `.grant.deps`, `.grant.already`, `.grant.ok`, `.revoke.ok`, `.list.empty`.

There is no `&` color-code parser or "configurable text" subsystem — colors come from `ChatFormatting.RED` applied to the Component before the prefix is prepended.

#### META-INF/neoforge.mods.toml
- Required deps: neoforge ≥ 21.1.226, minecraft ≥ 1.21.1.
- Optional deps: jei, emi, curios, ftbquests, ftblibrary, ftbteams, ftbxmodcompat.
- Mixin config: `stagesqx.mixins.json`.

---

## 3. Feature deep-dives requested

### 3.1 JEI/EMI ingredient hiding (recap of the load-bearing details)
- **Items**: registry-id + namespace gating; explicit `removeIngredientsAtRuntime` + `addIngredientsAtRuntime` symmetric loop; per-typed-ingredient `IngredientBlacklistInternal` synchronization.
- **All FluidStack types**: enumerate `im.getRegisteredIngredientTypes()` and treat each whose ingredient class is assignable to FluidStack — never just `NeoForgeTypes.FLUID_STACK`.
- **Library / generic ingredients**: stable key uses `"mod:<displayMod>|<uid>"` (never `null`) so previously-hidden ingredients can be restored; gating is display-mod + helper RL + uid-embedded `[a-z0-9_.-]+:[a-z0-9_./-]+` regex scan.
- **Mekanism gases/pigments**: handled via either (a) registry id matching `mekanism:gas/<x>` or (b) `RecipeViewerModHints.owningModIdForClass(stack.getClass())` matching the mekanism module name.
- **Hide by display mod**: `IIngredientHelper.getDisplayModId(ing)` is consulted before falling back to RL. Same pattern in EMI hook.
- **Post-load and after bulk-add stabilization**: `mc.execute(() -> { refresh(); mc.execute(JeiStagesSupport::refresh); })` — the second pass exists because JEI clears blacklist on add.
- **Ingredient-filter rebuild**: explicit `IngredientFilter.rebuildItemFilter()` and `updateHidden()` after every refresh; without these the visible row list keeps stale `IListElement`s.

### 3.2 Lock overlay above slot icon
See `LockedItemDecorator.render` quoted above. Z-order is `+200.0F` translation in pose stack inside `pushPose()/popPose()`. Skipped when stack walk shows JEI is the renderer (avoids fighting with JEI's own greying). No mixin needed — uses the standard `RegisterItemDecorationsEvent`.

### 3.3 starting_stages auto-grant on login + team-sync
On `PlayerEvent.PlayerLoggedInEvent`:
```java
PlayerStageStore.applyStartingStagesFromConfig(sp);
StageNetwork.syncAllTo(sp);
```
Adds every config entry to the player's NBT set if not present. Each grant runs through `replaceAll → set + StageNetwork.syncPlayerStages`. **Team interaction**: in `ftbquests_team_mode = true`, FTBQuests reads team stages via the proxy provider — but `STARTING_STAGES` always grants per-player ids (the mod doesn't push starting stages into team data; you must use `/ftbteams` or the FTB Library proxy `add` for that).

### 3.4 Eject blocked inventory
- Event: `PlayerTickEvent.Post`.
- Trigger: every `EJECT_BLOCKED_INVENTORY_INTERVAL_TICKS.get()` ticks, configurable [5, 1200].
- Scan: `Inventory.items` (main) + `Inventory.armor` + `Inventory.offhand`. **Not** ender chest, **not** curios, **not** modded inventories.
- Destination: `sp.drop(stack.copy(), true)` — dropped at player position, traceable.
- Bypass: skipped when creative or spectator.

### 3.5 minecraft=true vanilla namespace gate with child-stage inheritance
See `effectiveLockStagesForVanillaNamespace` quoted in §2.2. The trick: when checking a `minecraft:` resource's gating for an owned-leaf player, sweep transitively-owned prerequisite stages and pull any `minecraft=true` ones back into the active lock set, even though they would normally be "superseded" by the leaf.

Effect: if `stage_1` has `minecraft = true` and `stage_2` does not, owning `stage_2` (which depends on `stage_1`) keeps `stage_1`'s vanilla gating active. Required to stop water/wood leaking after granting a child stage.

### 3.6 Configurable text with `&` color codes
**Not present**. The mod uses `Component.translatable("...")` keys and applies `ChatFormatting.RED` programmatically. Localisation = lang JSON. There is no `&`-code parser. Recommend skipping this requested feature or treating it as "use translation overrides".

### 3.7 Hide restricting stage names from non-operator players
- Server config: `REVEAL_STAGE_NAMES_ONLY_TO_OPERATORS` (default true).
- Server-side: `StageDisclosure.mayShowRestrictingStageName(player)` checks `player.hasPermissions(2)`.
- Client-side: server forwards the flag in the owned-stages payload as `hide_stage_names`; `mayShowRestrictingStageNameClient` checks `Minecraft.getInstance().player.hasPermissions(2)` against the mirrored flag. When false, all chat/tooltip messages branch to the `*_generic` translation keys.

### 3.8 FTB Quests stage-gated chapters/quests + required_stage in editor
Three mixins (`Quest`, `Chapter`, `TeamData`) plus the `StagesQXFtbConfigCompat` reflection adapter.
- Per-quest/chapter: `@Unique String stagesqx$requiredStageId = ""` field.
- NBT serialization key: `"stagesqx_required_stage"` (only when non-blank).
- Net serialization: writeUtf/readUtf at `Short.MAX_VALUE` length cap.
- Editor: `fillConfigGroup` adds a `required_stage` string entry inside a `stagesqx` subgroup labeled "StagesQX".
- Visibility: `isVisible(TeamData) HEAD cancel false` when stage missing (client and server reuse the same gate via `StagesQXFtbQuestsStageGate.requiredStageMetForVisibility`).
- Task-start: `TeamData.canStartTasks(Quest) HEAD cancel false` on missing stage.
- The `StagesQXFtbQuestsRequiredStageHolder` interface lets `TeamDataStageGateMixin` read `stagesqx$requiredStageId` from any `Quest` instance regardless of which mixin populated it.

### 3.9 Per-stage enforcement exceptions
The TOML keys are the `[unlocks]` table — same shape as `[locks]`:
```
[unlocks]
mods = []
items = []
fluids = []
dimensions = []
entities = []
```
Check site is `StageCatalog.effectiveStagesGatingItem` (and the fluid/dim/entity/mod variants) which removes `sid` from the gating set when `def.unlocks().items().contains(itemId) || def.unlocks().mods().contains(modNamespace)`. There is no per-stage feature toggle (e.g. "this stage doesn't enforce drops") — exceptions are only the whitelist of resources that escape the lock.

### 3.10 Recipe-only locks — gating result slot without blocking pickup
This is **not** a generic feature in stagesqx. The closest equivalent is `AnvilMenuMixin`: it clears the result slot when any of inputs/result is blocked, but pickup is also blocked separately by `SlotMixin`. There is no separate "recipes = [...]" or "result-only" lock list — the fork doesn't have ProgressiveStages' per-recipe-id locks.

### 3.11 config/stagesqx/stages layout and auto-generated templates
- Layout: `config/stagesqx/<stage_id>.toml` for each stage; `config/stagesqx/stagesqx.toml` for the mod config. Both live in the same directory, separated by the `looksLikeStageFile` heuristic.
- `PathSetup.init()` creates the directory at mod construction.
- `/stagesqx create <name>` writes a fully-commented template with every config key, every mod id, every item/fluid/dim/entity in the registry (via `StageTemplateFactory.collect`). Template body shown in `StageRegistry.writeTemplateFile`. **First-run** templates are NOT created automatically — only on explicit `create` command. (If you want auto-templates on first launch, port that separately.)

### 3.12 v2.0 inversion compare site
The exact compare is `StageAccess.blockedByMissingRequiredStages`:
```java
private static boolean blockedByMissingRequiredStages(Set<String> gatingStages, Set<String> effectiveAccessStages) {
    return !gatingStages.isEmpty() && !effectiveAccessStages.containsAll(gatingStages);
}
```
Called from `isItemBlockedForEffectiveStages`, `isAbstractIngredientBlockedForEffectiveStages`, `isModIdBlockedForEffectiveStages`, `isFluidBlockedForEffectiveStages`, `isDimensionBlockedForEffectiveStages`, `isEntityBlockedForEffectiveStages`. All public `is*Blocked(catalog, ownedStages, ...)` methods funnel here after computing `playerAccessStagesForResourceNamespace` (which dispatches between `effectiveAccessStages` and `effectiveLockStagesForVanillaNamespace` based on namespace).

---

## 4. PORT PRIORITY LIST

| Feature | Priority | Justification |
|---|---|---|
| v2.0 compare-site inversion (`blockedByMissingRequiredStages`) | HIGH | Core semantic — port verbatim. Already in PS 2.0 in spirit; double-check this exact predicate and its pre-condition `!gatingStages.isEmpty()`. |
| `effectiveLockStagesForVanillaNamespace` (minecraft=true child inheritance) | HIGH | Subtle bug-fix: without it, granting child stage leaks `minecraft:water` etc. Tiny code, high value. |
| `StageAccess.transitiveDependencyIds` BFS + `effectiveAccessStages` | HIGH | Backbone of "leaf grant implies prerequisites satisfied". Port if PS does dependency progression. |
| `StageCatalog` reverse indexes + `effectiveStagesGating*` (with `[unlocks]` whitelist removal) | HIGH | Performance + correct exception handling. |
| `[unlocks]` TOML section parser | HIGH | Required for the exception-whitelist feature. Already handled in `StageTomlIo.parseGateLists`. |
| JEI hiding: multi-FluidStack-type loop, blacklist sync, two-pass refresh, ingredient-filter rebuild | HIGH | These are the load-bearing tricks; without them, fluids/Mekanism gases/Create potions leak. |
| EMI hiding via `EmiRegistry.removeEmiStacks(predicate)` covering items, FluidStack key, raw Fluid key, abstract id, owning-mod-of-class | HIGH | Same scope as JEI; especially `RecipeViewerModHints.owningModIdForClass` for Mekanism. |
| JEI/EMI refresh on stage-data sync (`StagesQXClientHooks.onCatalogUpdated`) | HIGH | Recipe viewers must update on `/stagesqx grant`; otherwise visible hidden stacks. |
| `LockedItemDecorator` lock overlay (Z+200, JEI stack-walk skip) | HIGH | User-visible UI; the JEI stack-walk skip is the trick that prevents double-rendering. |
| Hide restricting stage names from non-ops (`StageDisclosure` + payload flag) | HIGH | Privacy/spoiler control; small change. |
| Eject blocked inventory (`PlayerTickEvent.Post` + main/armor/offhand only) | HIGH | Common modpack request; clean implementation. |
| `STARTING_STAGES` config + `applyStartingStagesFromConfig` on login | HIGH | Most modpacks rely on this. Trivial to port. |
| `StageServerEvents` interaction hooks (pickup, toss, dimension travel, attack, right-click item/block, entity interact, left-click block) | HIGH | Comprehensive coverage; copy each handler. |
| `SlotMixin.mayPickup`, `AbstractContainerMenuMixin.clicked`, `AnvilMenuMixin`, `EnchantmentMenuMixin`, `ItemCombinerMenuAccessor` | HIGH | Container interaction — required for blocking moves of locked stacks already in inventory. |
| `LockSounds` with per-player 4-tick cooldown | HIGH | Quality-of-life; small file. |
| FTB Quests Quest/Chapter `required_stage` mixins + `StagesQXFtbConfigCompat` editor entry | HIGH | This is the killer FTB feature. Port `Quest`/`Chapter`/`TeamData` mixins and the reflection compat adapter. |
| FTB Library `StageProvider` Proxy registration (`StagesQXFtbLibraryStageProvider`) | HIGH | Hooks Stage Required field, Stage Task, Stage Reward straight into StagesQX. Without it, FTBQ stage UI doesn't talk to your store. |
| `ftbquests_team_mode` config + `TeamStagesHelper` reflection paths | HIGH | If you want team-shared progression. |
| `Visual Workbench` substitute reflection in `StageAccess.isBlockBlocked` | MEDIUM | Niche but pure reflection — drop in if you support VW. |
| Curios `CurioCanEquipEvent` integration | MEDIUM | Reflection-loaded; trivial port. |
| `/stagesqx` commands (create/delete/validate/reload/grant/revoke/list) | MEDIUM | PS already has commands; align names if needed. |
| `StageValidator` (resource existence, mod-namespace-in-registries, cycle detection) | MEDIUM | Nice-to-have; helps modpack authors find typos. |
| `StageTemplateFactory` dump-everything template | MEDIUM | Useful but verbose; consider shrinking. |
| Network sync via two custom payloads (`sync_catalog`, `sync_owned`) | MEDIUM | PS likely already has equivalent; align packet shape if catalog mirroring matters for client gating. |
| Generic JEI ingredient stable-key recovery (`genericIngredientRecovery` cache) | MEDIUM | Only matters if your mod ecosystem has many non-Item, non-Fluid types. |
| Debug logger ladder (`debug_jei_max_log_lines`, etc., `RecipeViewerDebugExplain`) | LOW | Author-grade diagnostics; port last if at all. |
| `EmiStagesDebug.maybeLogSnapshot` + `EmiReloadManager` poll | LOW | Diagnostic only. |
| Trigger directory plumbing (`setTriggersDirectory` is identical to stages dir, no consumers in this fork) | SKIP | Vestigial; nothing uses it currently. |
| Configurable text via `&` color codes | SKIP | Doesn't exist in this fork. Use translation files instead. |
| Recipe-only locks (gating only result slot) | SKIP | Not a feature here; PS already has it. |
| First-run auto template generation | SKIP | The fork only generates on `/stagesqx create` — PS's existing first-run templates are richer. |

---

## 5. Closing notes for porting

1. The simplest semantic gain is the `minecraft=true` child-inheritance (`effectiveLockStagesForVanillaNamespace`). It's ~12 lines and fixes a genuine bug.

2. The biggest UX gain is the JEI two-pass refresh + ingredient-filter rebuild — these are the "stale entries" fixes that JEI users hit constantly.

3. Mod-by-class detection (`Class.getModule().getName()`) is novel and worth adopting for hide-by-display-mod. Only requires that target mod be loaded as a named module, which all NeoForge mods are.

4. The FTB Library `StageProvider` Proxy is the cleanest way to integrate with FTB Quests' native stage UI — much less brittle than mixing in for every quest event. Strongly recommended.

5. The `stagesqx_required_stage` NBT key on Quest/Chapter is portable: any prior pack data migrates automatically because the field defaults to "" when absent.

6. The `LockSounds` per-player UUID cooldown map should be cleared on logout to avoid leaks (the fork doesn't currently do this — small bug).

7. The lang keys are stable and worth reusing as-is so existing translations port.
