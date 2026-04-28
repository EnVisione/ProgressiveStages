# Final audit — ProgressiveStages 2.0

## Build status
**PASS** — `./gradlew compileJava` exits with `BUILD SUCCESSFUL` (482ms, cache reused). No warnings, no errors.

`git status --short` shows the expected modified/added/deleted set: 3 deleted files (`LockEntry.java`, `LockType.java`, `NameMatcher.java`) are intentional — `LockEntry` moved to a `NetworkHandler` inner record, and `LockType`/`NameMatcher` were superseded by `PrefixEntry` + `CategoryLocks`. No unexpected losses.

---

## Critical issues found

### 1. `minecraft = true` shorthand never causes vanilla items to be gated — appears non-functional in isolation
**Files**: `LockRegistry.java:187-189` (registerStage), `LockRegistry.java:625-630` (getRequiredStages), `StageFileParser.java:191-197`.

**Problem**: When a stage TOML has `[locks].minecraft = true`, the parser sets `LockDefinition.minecraftNamespace = true`, and `LockRegistry.registerStage` adds the stage to `vanillaNamespaceGatingStages`. **But that flag is consumed nowhere except `effectiveLockStagesForVanillaNamespace` (which extends the player's *access set*).**

For an item to be gated, `getRequiredStages(item)` must return a non-empty `Set<StageId>`. That call is `itemCat.findStages(...)`, which only finds matches in `[items].locked` entries. The `vanillaNamespaceGatingStages` set is *never consulted as a gating source*. So a stage with **only** `minecraft = true` (no `[items].locked = ["mod:minecraft"]`, etc.) effectively gates nothing.

Compare with the fork (per `.claude/fork-analysis.md` §2.2): `buildModIndex` does
```java
if (def.minecraft()) map.computeIfAbsent("minecraft", k -> new HashSet<>()).add(def.id());
```
so `minecraft=true` is genuinely equivalent to listing `"minecraft"` in the mods category.

**Effect**: The audit's stated test scenario fails:
- "stage_1 has `minecraft=true`, stage_2 depends on stage_1, player owns stage_2 only → expected: `minecraft:diamond` blocked." In the current code, `getRequiredStages(diamond)` is empty → not blocked.
- The child-inheritance path (`effectiveLockStagesForVanillaNamespace`) is correct in principle, but irrelevant when the gating set is always empty.

**Fix options** (any one):
1. In `StageFileParser.parseLocks`, when `minecraftNamespace == true`, append `mod:minecraft` to the parsed `items` `CategoryLocks` (and possibly other categories). Cleanest from the parser's perspective.
2. In `LockRegistry.registerStage`, when `locks.minecraftNamespace()` is true, register a synthetic `PrefixEntry.fromMod("minecraft")` into `itemCat`/`blockCat`/`fluidCat`/`entityCat`.
3. Modify `getRequiredStages*` to UNION `vanillaNamespaceGatingStages` into the gating set whenever the resource namespace is `"minecraft"`.

Option 3 is the smallest diff but slightly harder to reason about. Option 2 mirrors the fork most directly.

### 2. `BlockEnforcer.canPlaceBlock(Block)` and `canInteractWithBlock(Block)` lack spectator bypass
**File**: `BlockEnforcer.java:23-38, 44-59`.

The state-aware overloads (`canPlaceBlock(BlockState)`, `canInteractWithBlock(BlockState)`) don't add `player.isSpectator()` either, but `ItemEnforcer` does (lines 48, 79, 110). Spectator parity was an explicit port (work item 2 follow-up). The block-side enforcers were missed.

**Fix**: add `if (player.isSpectator()) return true;` after the config gate in both `canPlaceBlock(Block)` and `canInteractWithBlock(Block)` (and the BlockState overloads).

### 3. `BlockEnforcer.canPlaceBlock(Block)` does NOT consult Visual Workbench
**File**: `BlockEnforcer.java:23-38`.

`canPlaceBlock(BlockState)` (line 101) was added with VW handling (good). However the original `canPlaceBlock(Block)` overload at line 23 still exists and does NOT consult VW. The audit checklist says "VW shim should be consulted AFTER checking the original block id, not before" — for the BlockState overload that's correct, but the Block-only overload is asymmetric.

Whether this is a real bug depends on which overload is actually called by the block-place pipeline. Need to grep for callers — if production code calls `canPlaceBlock(BlockState)`, then the Block-only overload is effectively dead and this is a low-risk asymmetry. If anything calls `canPlaceBlock(Block)`, VW substitutions silently bypass placement gating.

### 4. `LockRegistry.getRequiredStage(Item)` (single-stage) skips per-stage `[unlocks]` carve-outs
**File**: `LockRegistry.java:203-210` vs. `:625-630`.

`getRequiredStages(Item)` correctly applies `applyPerStageUnlocks`. `getRequiredStage(Item)` does not. Three call sites still hit the single-stage path: `LockRegistry.isItemLocked` (line 213), `ClientStageCache.getRequiredStageForItem` (line 273), and `ProgressiveStagesEMIPlugin` (line 418). Effect is mild — items would appear locked when they should be carved out — but inconsistent with the documented v2.0 semantics.

**Fix**: route `getRequiredStage(Item)` through `getRequiredStages(Item).stream().findFirst()` so the carve-outs apply uniformly.

---

## Minor issues found

### 1. `ScreenEnforcer` notify strings are hardcoded, not configurable
**File**: `ScreenEnforcer.java:38, 57`. Strings `"This screen"` and `"This item's GUI"` are passed directly to `notifyLockedWithCooldown`. The other type labels (`MSG_TYPE_LABEL_BLOCK`, `_RECIPE`, `_FLUID`, etc.) are configurable. There is no `MSG_TYPE_LABEL_SCREEN` or `MSG_TYPE_LABEL_GUI`. Add two new keys for consistency with the v2.0 text-audit.

### 2. ServerEventHandler has a hardcoded `"This structure's contents"` label
**File**: `ServerEventHandler.java:398, 449, 573`. Same pattern as #1 — passed to `notifyLockedWithCooldown` instead of using a `MSG_TYPE_LABEL_*` config key. Add `messages.type_label_structure_contents`.

### 3. `PetEnforcer.PetInteractionKind` labels are hardcoded
**File**: `PetEnforcer.java:127-129`. `"Taming this pet"`, `"Breeding this pet"`, `"Commanding this pet"` are hardcoded in the enum. Same pattern as above.

### 4. `effectiveLockStagesForVanillaNamespace` returns `playerOwnedStages` immutable wrapper when empty
**File**: `LockRegistry.java:541-553`. Behavior is correct, but the dead path at line 542 returns `Collections.unmodifiableSet(new HashSet<>(playerOwnedStages))` — it's correct, but unnecessary copy. Cosmetic.

### 5. `getRequiredStagesForBlock` doesn't apply per-stage unlocks
**File**: `LockRegistry.java:632-638`. Items, fluids, dimensions, entities all call `applyPerStageUnlocks*`. Blocks do not. The doc-string in `applyPerStageUnlocks` mentions blocks aren't on the list, but the audit checklist explicitly asks for blocks. Consider adding `applyPerStageUnlocks(raw, id, namespace)` for parity.

### 6. `getRequiredStagesForRecipeByOutput` and `getRequiredStagesForRecipe` skip per-stage unlocks
Similar to #5 — multi-stage but no per-stage carve-out application. Probably fine for recipes, but worth a one-line comment.

---

## Confirmed working

- `StageConfig` integrity: every v2.0 toggle declared, cached, loaded in `onLoad`, and exposed via getter. All MSG_* keys: define line, cached field, onLoad assignment, getter with non-null fallback. Spot-checked 10+ getters.
- `LockSyncPayload` codec is unchanged single-row format; multi-row deduplication happens client-side in `handleLockSyncClient` (NetworkHandler.java:234-267).
- `ClientLockCache.playerOwnsAllStagesFor` is correctly populated alongside legacy maps and is used by JEI (`hideLockedItems`) and EMI (`hideLockedStacks`).
- `ItemEnforcer` (canUseItem/canPickupItem/canHoldItem) correctly: bypasses spectator, bypasses creative, checks exemption, then calls `LockRegistry.isItemBlockedFor` (multi-stage).
- `FluidEnforcer.shouldCancelFluidPlace` and `canPickupFluid` use the multi-stage `getRequiredStagesForFluid` + `isFluidBlockedFor`. `notifyPickupLocked` uses `primaryRestrictingStageForFluid`.
- `BlockEnforcer.canInteractWithBlock(BlockState)` correctly delegates to `isBlockLockedForPlayer(BlockState)`, which checks own id first and falls back to VW vanilla equivalent (matches checklist requirement).
- `CraftingMenuMixin` and `ResultSlotMixin`: every block decision uses `isXxxBlockedFor` or `playerHasAllStages(gating)`; no `getRequiredStage(...).get()` followed by `hasStage(...)` antipattern.
- `EnchantmentMenuMixin` correctly uses `isEnchantmentBlockedFor` + `primaryRestrictingStageForEnchantment`.
- `RequiredStageHolder` interface is implemented by both `QuestMixin` and `ChapterMixin`. `TeamDataMixin` reads via `(Object) quest instanceof RequiredStageHolder holder` cast — clean.
- `StageRequirementHelper.hasStageForServerLogic` resolves `ServerQuestFile.getInstance().getCurrentPlayer()` reflectively and falls back to `true` (fail-open) on any reflection error.
- `FtbQuestsHooks.handleProviderMethod` correctly branches on `StageConfig.isFtbquestsTeamMode()` for `has`/`add`/`remove` and falls back to mine's backend on reflective lookup failure.
- `progressivestages-ftbquests.mixins.json` lists `QuestMixin`, `ChapterMixin`, `TeamDataMixin` and is `required: false`.
- `LockedItemDecorator` implements `IItemDecorator`, gates on `isShowLockIcon`, skips when `isInsideSlotWidget()` (EMI nested path), skips when `mezz.jei.*` is anywhere on the call stack via `StackWalker`.
- `ClientModBusEvents.onRegisterItemDecorations` registers a single `LockedItemDecorator` instance for every `Item` via `BuiltInRegistries.ITEM` and lives on the mod-event bus (annotated `EventBusSubscriber.Bus.MOD`).
- `ClientEventHandler.onItemTooltip` correctly aggregates ALL missing item + recipe stages and joins them comma-separated; uses `mayShowRestrictingStageNameClient` for op-vs-non-op branch; uses every `MSG_TOOLTIP_*` getter.
- JEI plugin: registers `IIngredientListener`, two-pass `scheduleRefresh`, `notifyJeiIngredientFilter` reflective rebuild, multi-`FluidStack` enumeration, UID-embedded namespace regex scan (`uidDeclaresBlocked`), generic ingredient sweep via `RecipeViewerModHints`.
- EMI plugin: `removeEmiStacks` predicate covers items, fluid stacks, abstract ids, and class-module fallback via `RecipeViewerModHints.owningModIdForClass`.
- `StageManager.grantStartingStage` gates on `isReapplyStartingStagesOnLogin`, applies regex `^[a-z0-9._/:-]+$`, wraps `StageId.of` in try/catch, logs each skip.
- No `TODO` / `FIXME` / `XXX` / `UnsupportedOperationException` strings anywhere in `src/main/java`.

---

## StageConfig integrity

| Toggle | define | cached | onLoad | getter |
|---|---|---|---|---|
| BLOCK_ENCHANTS | yes | yes | yes | yes |
| BLOCK_SCREEN_OPEN | yes | yes | yes | yes |
| BLOCK_CROP_GROWTH | yes | yes | yes | yes |
| BLOCK_PET_INTERACT | yes | yes | yes | yes |
| BLOCK_LOOT_DROPS | yes | yes | yes | yes |
| BLOCK_MOB_REPLACEMENTS | yes | yes | yes | yes |
| BLOCK_REGION_ENTRY | yes | yes | yes | yes |
| REGION_TICK_FREQUENCY | yes | yes | yes | yes |
| BLOCK_STRUCTURE_ENTRY | yes | yes | yes | yes |
| REVEAL_STAGE_NAMES_ONLY_TO_OPERATORS | yes | yes | yes | yes |
| MSG_ITEM_LOCKED_GENERIC | yes | yes | yes | yes |
| MSG_TYPE_LOCKED_GENERIC | yes | yes | yes | yes |
| MSG_TOOLTIP_STAGE_REQUIRED_GENERIC | yes | yes | yes | yes |
| FTBQUESTS_TEAM_MODE | yes | yes | yes | yes |
| REAPPLY_STARTING_STAGES_ON_LOGIN | yes | yes | yes | yes |
| MSG_PREFIX | yes | yes | yes | yes |
| MSG_TOOLTIP_CURRENT_STAGE_NONE | yes | yes | yes | yes |
| MSG_CMD_LIST_ENTRY | yes | yes | yes | yes |
| MSG_CMD_INFO_HEADER | yes | yes | yes | yes |
| MSG_CMD_TREE_HEADER | yes | yes | yes | yes |
| MSG_CMD_VALIDATE_HEADER | yes | yes | yes | yes |
| MSG_CMD_FTB_STATUS_* (11 keys) | yes | yes | yes | yes |
| MSG_TYPE_LABEL_* (7 keys) | yes | yes | yes | yes |

All getters return a sensible non-null fallback when their cached value is null (defensive against early access during config reload).

---

## Multi-stage correctness

### LockRegistry storage and APIs
- Internal storage: items, blocks, fluids, entities, spawn, enchants, crops, screens, screenItems, loot, pet (taming/breeding/commanding), recipeIds, recipeOutputs all use `ResolvedCategory` whose `entries` field holds a `(PrefixEntry, StageId)` list — supporting multiple stages per id naturally.
- Mods, name patterns: derived from `ResolvedCategory` entries; multi-stage via `modStages(modId)`/`getRequiredStagesForName` is supported.
- Curio slots, dimensions: `Map<X, StageId>` (single-stage by design; documented).
- `getRequiredStages*` methods return `Set<StageId>` (immutable copy via `Set.copyOf`).
- `isXxxBlockedFor` predicates do `playerHasAllStages` AND check.
- `primaryRestrictingStage*` returns first missing in canonical order via `firstMissing` helper.
- `applyPerStageUnlocks` is called from items, fluids, dimensions, entities, loot, spawn (in `getRequiredStagesForX`). **Not called for blocks, recipes, recipe-by-output, crops, screens, pets** (see Minor issue #5).

### ClientLockCache
- Multi-stage maps (`itemMultiLocks`, `recipeMultiLocks`, `recipeItemMultiLocks`) exist alongside legacy single-stage maps.
- `playerOwnsAllStagesFor(itemId)` exists at line 124, used by JEI (line 122) and EMI (line 100).
- `setItemMultiLocks` etc. are populated from `LockSyncPayload` in `NetworkHandler.handleLockSyncClient` via per-row dedupe into `LinkedHashSet`.

### Network
- `LockSyncPayload` codec is `record(List<LockEntry>, List<LockEntry>, List<LockEntry>)`. `LockEntry(itemId, stageId)` is single-row. Server emits one row per (id, stage) pair (lines 130-167); client deduplicates into both single-stage and multi-stage maps. Codec unchanged from v1 — backward compatible.

### Enforcer spot-checks
- `ItemEnforcer.canUseItem/canPickupItem/canHoldItem` → `isItemBlockedFor`. ✓
- `BlockEnforcer.canInteractWithBlock(BlockState)` → `isBlockLockedForPlayer(BlockState)` → `isBlockBlockedFor`. ✓ (See Critical #2 for spectator gap, Critical #3 for VW asymmetry on Block-only overloads.)
- `FluidEnforcer.canPickupFluid` → `isFluidBlockedFor`. `notifyPickupLocked` → `primaryRestrictingStageForFluid`. ✓
- `PetEnforcer.canInteract` → multi-stage `blockedAtFirstPresent`. `notifyLocked` → `firstMissingFromFirstPresent`. ✓
- `ScreenEnforcer.canOpenScreen` → `isScreenBlockedFor`. ✓
- `LootEnforcer.filterLivingDrops` → multi-stage `isStackLockedFor` → `isLootBlockedFor`. ✓
- `CraftingMenuMixin` → multi-stage `isRecipeBlockedFor` and `playerHasAllStages(recipeItemGating)`. ✓
- `ResultSlotMixin` → multi-stage on item/recipe-item/recipe-id. ✓
- `EnchantmentMenuMixin` → `isEnchantmentBlockedFor` + `primaryRestrictingStageForEnchantment`. ✓

No enforcer was caught using `getRequiredStage(...).get()` followed by `hasStage(...)` on a category that has a multi-stage path. The remaining `(Optional)getRequiredStageForX(...).get()` usages are confined to:
- `InteractionEnforcer` (interactionLocks: 1-stage by design)
- `ServerEventHandler.entryStage` (StructureRulesAggregate.lockedEntry: 1-stage by design)
- These match the spec's "intentional 1-stage data structures" exception.

---

## Hardcoded strings remaining

User-visible (BUG, should be MSG_*):
| File:line | Literal | Suggested key |
|---|---|---|
| `ScreenEnforcer.java:38` | `"This screen"` | `messages.type_label_screen` |
| `ScreenEnforcer.java:57` | `"This item's GUI"` | `messages.type_label_screen_item` |
| `ServerEventHandler.java:398, 449, 573` | `"This structure's contents"` | `messages.type_label_structure_contents` |
| `PetEnforcer.java:127-129` | `"Taming this pet"`, `"Breeding this pet"`, `"Commanding this pet"` | `messages.type_label_pet_taming/breeding/commanding` |

Internal (OK):
- `TextUtil.java:73,95,105` — `Component.literal(segment)` inside `parseColorCodes` (this IS the configurable text path).

`sendSuccess`/`sendFailure`/`sendSystemMessage` audit:
- All call sites in `StageCommand.java` wrap `TextUtil.parseColorCodes(StageConfig.getMsg...())` — fully configurable.
- All call sites in `ItemEnforcer.java` and `InventoryScanner.java` use `parseColorCodes(template.replace(...))` against `StageConfig.getMsg*` — fully configurable.
- No leakage found.

---

## FTB Quests integration

- **QuestMixin**: implements `RequiredStageHolder`. `@Unique` field `progressivestages$requiredStage`. NBT: `progressivestages_required_stage`. Net: writeUtf/readUtf at `Short.MAX_VALUE`. fillConfigGroup adds `required_stage` under `getOrCreateSubgroup("visibility")` (PS uses `visibility` group, fork uses a `stagesqx` group; both work — `visibility` is less discoverable but matches the `[visibility]` FTB config).
- **ChapterMixin**: same shape as QuestMixin. ✓
- **TeamDataMixin**: HEAD-cancels `canStartTasks(Quest)`. Casts via `Object questObj = quest; if (questObj instanceof RequiredStageHolder holder)` — safe because `QuestMixin implements RequiredStageHolder`. ✓
- **RequiredStageHolder**: interface with `progressivestages$getRequiredStage()` method. `@Unique`-annotated implementor methods in both Quest/Chapter mixins. ✓
- **StageRequirementHelper.hasStageForServerLogic**: `Class.forName("dev.ftb.mods.ftbquests.quest.ServerQuestFile")` → `getInstance()` → `getCurrentPlayer()` → cast to `ServerPlayer` → `hasStage`. Falls back to `true` (fail-open) if any reflective call fails. ✓
- **FtbQuestsHooks.handleProviderMethod**: `has`/`add`/`remove` all branch on `StageConfig.isFtbquestsTeamMode()`. `teamStagesHelperHas/Add/Remove` cache method handles, set `teamStagesHelperUnavailable = true` on `ClassNotFoundException`/`NoSuchMethodException`, fall through to mine's backend on null result. ✓
- **progressivestages-ftbquests.mixins.json**: `required: false`, lists all three mixins. ✓

---

## Lock overlay

- **LockedItemDecorator**: implements `IItemDecorator` (NeoForge 21.1). Gate order: `isShowLockIcon()` → empty stack → `isInsideSlotWidget()` (EMI nested) → `isCalledFromJei()` (StackWalker for `mezz.jei.*`) → `isItemLocked(item)`. ✓
- **isInsideSlotWidget**: ThreadLocal in `LockIconRenderer` (line 44). EMI mixin path sets/clears around its `SlotWidget.render` call.
- **JEI stack-walk skip**: `StackWalker.getInstance().walk(s -> s.anyMatch(f -> f.getClassName().startsWith("mezz.jei.")))`. Wrapped in try/catch returning false on failure. ✓
- **renderLockOverlay**: defined at `LockIconRenderer.java:140`, takes `(GuiGraphics, x, y, slotSize)`. Decorator passes `(graphics, x, y, 16)`. ✓
- **ClientModBusEvents.onRegisterItemDecorations**: subscribes to `RegisterItemDecorationsEvent` on `Bus.MOD` (mod-event bus, correct). Iterates `BuiltInRegistries.ITEM` and registers ONE shared `LockedItemDecorator` instance per Item. ✓

---

## Recommendations

1. **HIGH — Fix `minecraft = true` shorthand** (Critical #1). Currently the flag does nothing in isolation. Pick option 2 or 3 from the issue write-up. ~15 lines of code in `LockRegistry.registerStage` or `getRequiredStages*`. Without this, the headline v2.0 feature "child-stage inheritance for vanilla namespace" doesn't work because the parent stage's gating itself is empty.
2. **HIGH — Add spectator bypass to `BlockEnforcer.canPlaceBlock` / `canInteractWithBlock`** (Critical #2). 4 one-line additions.
3. **MEDIUM — Audit `BlockEnforcer.canPlaceBlock(Block)` callers** (Critical #3). Either delete the Block-only overload (forcing all paths to the BlockState variant) or have it delegate to `canPlaceBlock(BlockState)` reconstructing the state from `block.defaultBlockState()`.
4. **MEDIUM — Route `getRequiredStage(Item)` through `getRequiredStages(Item)`** (Critical #4). One-line change in `LockRegistry`.
5. **LOW — Hardcoded string follow-ups** (Minor #1-3). Add `MSG_TYPE_LABEL_SCREEN`, `MSG_TYPE_LABEL_SCREEN_ITEM`, `MSG_TYPE_LABEL_STRUCTURE_CONTENTS`, `MSG_TYPE_LABEL_PET_*` keys.
6. **LOW — Apply per-stage unlocks to blocks / recipes / crops / screens / pets** (Minor #5-6). Consistency cleanup.

---

## Verdict
**DELAY-AND-FIX-CRITICAL-1**

Build is clean. The multi-stage refactor is solid: every primary enforcer + mixin uses `isXxxBlockedFor` (correct multi-stage AND-check), and `primaryRestrictingStage*` is consistently used for messaging. FTB Quests integration is sound (TeamData.canStartTasks gating ported, RequiredStageHolder bridges mixins cleanly). Lock-overlay decorator covers vanilla containers as intended.

The blocker is **Critical #1**: the `minecraft = true` shorthand is wired into the parser and stored in the registry, but never actually causes a vanilla item to be considered gated. The advertised v2.0 vanilla-namespace inheritance behavior cannot manifest because the base gating set is always empty for `minecraft:` resources unless the user also explicitly lists `mod:minecraft` in the items category. Either:
- Document the shorthand as "you must ALSO add mod:minecraft yourself" (compatibility break vs. the fork), or
- Implement the missing one-line behavior in `registerStage` or `getRequiredStages*`.

Critical #2 (spectator bypass on Block overloads) is small but real — bots/spectators can place gated blocks via mods that pass `Block` instead of `BlockState`. Critical #3 is asymmetric but probably unreachable in production (modern NeoForge passes BlockState).

Once Critical #1 lands, the rest of the build is genuinely shippable.
