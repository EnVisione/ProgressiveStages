# Head-to-head: ProgressiveStages 2.0 (mine) vs StagesQX (fork)

This is a paranoid feature-by-feature audit. Where mine and fork are equivalent, I say so. Where the fork has logic mine lacks, I call it out concretely.

---

## Feature 1: starting_stages auto-grant on login

### Files
- mine: `src/main/java/com/enviouse/progressivestages/common/stage/StageManager.java` (`grantStartingStage`, lines 315-339), `StageConfig.java` (`STARTING_STAGES`, line 24-29), `StageManager.syncStagesOnLogin` (lines 434-440).
- fork: `com/stagesqx/neoforge/PlayerStageStore.java` (`applyStartingStagesFromConfig`), `com/stagesqx/neoforge/StagesQXModConfig.java` (`STARTING_STAGES`), `StageServerEvents.onLogin`.

### Side-by-side
| Capability | Mine | Fork |
|---|---|---|
| Multiple starting stages | yes (List<String>) | yes (List<String>) |
| Validates entry against `isValidStageName` regex | NO — just calls `StageId.of(s)` | YES — `StageTomlIo.isValidStageName(t)` regex `^[a-z0-9._-]+$` per entry, silently skips invalid |
| Skips empty / blank entries | partial — relies on `StageOrder.stageExists` | yes (`t.isEmpty()` early continue) |
| Skips non-existent stages | yes (`stageExists` check + warn log) | NO — fork blindly adds the stage id whether it exists or not |
| Re-grants on relog | NO — guarded by `if (!currentStages.isEmpty()) return;` (only granted once, then never again) | YES — applies on every login (sets idempotently) |
| Bypasses dependency checks | yes (`grantStageBypassDependencies`) | implicitly yes (no dependency model on starting stages) |
| Granted via team (FTB Teams) when team mode is on | yes via `TeamProvider.getTeamId(player)` then team grant | NO — starting stages are always per-player NBT regardless of `ftbquests_team_mode` |

### Verdict
**DIFFERENT (each has merits)**

Mine is more conservative (only applies to brand-new players with empty stage set), validates stage existence and emits a warn. Fork applies every login (idempotently), validates the regex but not existence.

### What fork has that mine doesn't
- `StageTomlIo.isValidStageName(t)` regex pre-filter on every entry: gracefully drops malformed config without throwing.
- Login behavior is always idempotent — pack devs can add a starting stage to an existing modpack save and have all online players pick it up. Mine intentionally only grants starting stages to brand-new players (`if (!currentStages.isEmpty()) return;`).

### What mine has that fork doesn't
- Validates stage existence (`StageOrder.stageExists`) and logs a warning for typos — fork silently grants a non-existent stage id.
- Team-shared grant via `TeamProvider`.
- Fires `StageChangeEvent` per stage with cause `STARTING_STAGE` for downstream FTB recheck.

### Recommended ports
- Add an `isValidStageName(String)` regex check to the starting-stages loop in `grantStartingStage` to gracefully skip malformed config entries.
- Reconsider the `if (!currentStages.isEmpty()) return;` guard — it prevents new starting stages added later from reaching existing players. Could be opt-in via a new boolean.

---

## Feature 2: Inventory eject

### Files
- mine: `src/main/java/com/enviouse/progressivestages/server/enforcement/InventoryScanner.java` (`scanAndDropLockedItems` + `scanAndMoveLockedItemsFromHotbar`); triggered from `ServerEventHandler.java` lines 320-346 every `inventory_scan_frequency` ticks (default 20).
- fork: `com/stagesqx/neoforge/StageInventoryEject.java` (`ejectBlockedStacks`); triggered from `StageServerEvents` `PlayerTickEvent.Post` every `eject_blocked_inventory_interval_ticks` (default 40, range 5..1200).

### Side-by-side
| Capability | Mine | Fork |
|---|---|---|
| Scans main inventory | yes | yes |
| Scans armor slots | yes | yes |
| Scans offhand | yes | yes |
| Scans curios / baubles | NO | NO |
| Scans ender chest | NO | NO |
| Drop method | `new ItemEntity(level, x, y, z, stack)` + `setPickUpDelay(40)` (~2s anti-pickup) — better UX | `sp.drop(stack.copy(), true)` (no extra pickup delay) |
| Soft hotbar-only mode (move-to-main, no drop) | YES — `scanAndMoveLockedItemsFromHotbar` (mine's distinct `block_item_hotbar` toggle when `block_item_inventory=false`) | NO — only the drop mode |
| Per-stage hotbar exemption (`allowed_hotbar`) | YES — `LockRegistry.isExemptFromHotbar(item)` | NO |
| Per-stage inventory exemption (`allowed_inventory`) | YES — `LockRegistry.isExemptFromInventory(item)` (via `ItemEnforcer.canHoldItem`) | NO |
| Creative bypass | yes (`isAllowCreativeBypass()` and `isCreative()`) | yes (`bypassesLocks` checks `isCreative` or `isSpectator`) |
| Spectator bypass | NO (only creative bypass is checked) | YES (`isSpectator()`) |
| Notifies player on drop | yes — `getMsgItemsDropped().replace("{count}", ...)` | NO direct chat for drops; relies on `LockSounds` for blocked actions |
| Configurable scan frequency | yes (range 0..200) | yes (range 5..1200) |
| Disable via 0 | YES (`if (scanFrequency <= 0) return;`) | NO — fork uses `tickCount % interval == 0`; cannot disable just by interval (uses separate `EJECT_BLOCKED_INVENTORY_ITEMS` boolean) |

### Verdict
**MINE BETTER**

Mine has the soft "move-to-main-inventory" hotbar mode which the fork lacks entirely; per-stage hotbar/inventory exemptions; configurable count message; explicit 40-tick pickup-delay protection.

### What fork has that mine doesn't
- Spectator bypass in addition to creative bypass.

### What mine has that fork doesn't
- `scanAndMoveLockedItemsFromHotbar` — soft mode where locked items are moved out of slots 0-8 to the main inventory rather than dropped.
- Per-stage `allowed_hotbar` / `allowed_inventory` exemption lists.
- Custom drop with `ItemEntity.setPickUpDelay(40)` to prevent immediate re-pickup.
- Configurable chat message with `{count}` placeholder via `MSG_ITEMS_DROPPED` / `MSG_ITEMS_MOVED_HOTBAR`.

### Recommended ports
- Add `isSpectator()` to mine's bypass check in `ItemEnforcer.canHoldItem` for parity.

---

## Feature 3: Configurable text with `&` color codes

### Files
- mine: `StageConfig.java` lines 262-426 (every `MSG_*` key) + `common/util/TextUtil.java` (`parseColorCodes`) + getters at lines 723-754.
- fork: `src/main/resources/lang/en_us.json` only — no `&` parser.

### Side-by-side
| Capability | Mine | Fork |
|---|---|---|
| `&0..&f` color codes | YES (TextUtil.parseColorCodes) | NO |
| `&l/m/n/o/k/r` formatting codes | YES | NO |
| Config-file customization (no resource pack required) | YES (~30 message keys) | NO (lang JSON via resource pack) |
| Placeholder substitution (`{stage}`, `{type}`, `{count}`, `{player}`, `{progress}`, `{dependencies}`, `{type}`, `{key}`) | YES | partial — uses `Component.translatable("key", arg)` which substitutes `%s` |
| Translation keys | minimal (no en_us.json shipped except mod display name) | yes (full en_us.json) |
| Per-message generic-vs-named branch (op disclosure) | YES (`MSG_*_GENERIC` companion keys) | YES (`*_active_stage` vs `*_generic` translation keys) |

### Verdict
**MINE BETTER (architecturally)** — but with a sizable text audit gap. See "TEXT AUDIT" section below.

### What fork has that mine doesn't
- Comprehensive `lang/en_us.json` with every player-visible string in one place — useful for translators.

### What mine has that fork doesn't
- `&` color code parser inside config strings (huge UX win for pack devs).
- Far more granular configurability — each command output line can be re-themed without modifying lang files.

### Recommended ports
- (See TEXT AUDIT below — mine's audit gaps are listed; the user can decide which to make configurable.)

---

## Feature 4: FTB Library StageProvider integration

### Files
- mine: `src/main/java/com/enviouse/progressivestages/compat/ftbquests/FtbQuestsHooks.java` (`registerStageProvider` + `handleProviderMethod`), `FTBQuestsCompat.java` (lifecycle + recheck queue).
- fork: `com/stagesqx/neoforge/integration/ftbquests/StagesQXFtbLibraryStageProvider.java` (`tryRegister`, JDK Proxy).

### Side-by-side
| Capability | Mine | Fork |
|---|---|---|
| JDK Proxy implementation | YES | YES |
| Idempotent registration | YES (`providerRegistered` flag) | YES (`registered` volatile flag) |
| Late registration retry | YES (`attemptLateRegistrationIfNeeded` on stage-change event) | NO (only `tryRegister` once at common setup) |
| Save-and-restore previous provider | YES (`previousProvider` cache + `restorePreviousProvider`) | NO |
| Re-entrancy guard on recheck | YES (`recheckInProgress` set, re-queues for next tick) | NO |
| Coalesced rechecks per tick | YES (`pendingRechecks` set + ServerTick) | NO (no recheck queue at all) |
| Recheck budget (max N players/tick) | YES (`getFtbRecheckBudget`, default 10) | NO |
| Calls `StageTask.checkStages(player)` | YES | NO — relies on FTB's own 20-tick poll |
| Soft-disable via config (`integration.ftbquests.enabled`) | YES | partial — only `ftbquests_team_mode` (does not disable provider entirely) |
| `has(Player, String)` | YES (server-aware: ServerPlayer→server check, Player→client cache) | YES (server: store; client: returns `false` because solo mode can't answer) |
| `add(ServerPlayer, String)` | YES — `grantStageBypassDependencies` with `StageCause.QUEST_REWARD` | YES — `PlayerStageStore.grant` |
| `remove(ServerPlayer, String)` | YES — `revokeStage` with `QUEST_REWARD` cause | YES — `PlayerStageStore.revoke` |
| `sync(ServerPlayer)` | YES — `NetworkHandler.sendStageSync` | YES — `StageNetwork.syncPlayerStages` |
| `getName()` / `toString()` returning provider id | YES (`"ProgressiveStagesStageProvider"`) | YES (`"StagesQXStageProvider"`) |
| `equals` / `hashCode` overrides for proxy | YES | YES |
| Team-mode delegation (FTB Teams `TeamStagesHelper`) | NO — mine ALWAYS reads its own team data via `TeamProvider`. There's no `team_mode` toggle that delegates to FTB Teams' `TeamStagesHelper.hasTeamStage` | YES — `if (FTBQUESTS_TEAM_MODE.get()) hasTeamStage / addTeamStage / removeTeamStage` reflectively |
| Validates stage exists before grant | YES (`stageExists` check on add) | NO |
| Trims input | YES (`stage.trim()`) | YES (`stage.trim()`) |
| Logs at info on grant | YES (verbose) | minimal logging |

### Verdict
**MINE BETTER (mostly), with one specific fork advantage**

Mine has more sophisticated lifecycle handling (re-entrancy, recheck budget, late registration, restore-previous-provider). The fork has one feature mine lacks: **team-mode delegation to FTB Teams' `TeamStagesHelper`**.

### What fork has that mine doesn't
- `ftbquests_team_mode` config that, when true, delegates `has/add/remove` to `dev.ftb.mods.ftbteams.api.TeamStagesHelper` reflectively. This means an FTB Quests pack can choose to read team stages directly from FTB Teams rather than from the mod's own store, even when there's no team-aware lookup in the mod's stage backend. (`StagesQXFtbLibraryStageProvider.java` lines 92-100, 107-125).

### What mine has that fork doesn't
- Re-entrancy guard (`recheckInProgress` UUID set in `FtbQuestsHooks`).
- Recheck queue debouncing (`FTBQuestsCompat.pendingRechecks` + `ServerTickEvent.Post`).
- Recheck budget per tick (config `ftbquests.recheck_budget_per_tick`).
- Calls `StageTask.checkStages(player)` directly for instant updates rather than letting FTB's 20-tick poll catch up.
- Late provider registration retry on stage-change event.
- Restore-previous-provider on shutdown.
- Stage-existence validation on `add`.

### Recommended ports
- Add an optional `ftbquests_team_mode` toggle to mine's `FtbQuestsHooks.handleProviderMethod` that, when true, delegates to `TeamStagesHelper.hasTeamStage` / `addTeamStage` / `removeTeamStage` reflectively. Useful for packs where the user wants team-shared questing without committing the mod's `team_mode` config to `ftb_teams` (which has wider implications).

---

## Feature 5: FTB Quests Quest/Chapter integration

### Files
- mine: `mixin/ftbquests/QuestMixin.java`, `mixin/ftbquests/ChapterMixin.java`, `compat/ftbquests/StageRequirementHelper.java`.
- fork: `mixin/ftbquests/QuestStageGateMixin.java`, `ChapterStageGateMixin.java`, `TeamDataStageGateMixin.java`, `integration/ftbquests/StagesQXFtbConfigCompat.java`, `integration/ftbquests/StagesQXFtbQuestsStageGate.java`, `integration/ftbquests/StagesQXFtbQuestsRequiredStageHolder.java`.

### Side-by-side
| Capability | Mine | Fork |
|---|---|---|
| `@Unique String requiredStageId` on Quest | YES (`progressivestages$requiredStage`) | YES (`stagesqx$requiredStageId`) via interface holder |
| Same on Chapter | YES | YES |
| `writeData/readData` NBT save/load on Quest | YES (key `progressivestages_required_stage`) | YES (key `stagesqx_required_stage`) |
| Same on Chapter | YES | YES |
| `writeNetData/readNetData` network sync | YES (Short.MAX_VALUE cap) | YES (Short.MAX_VALUE cap) |
| `fillConfigGroup` editor-config entry | YES (`config.getOrCreateSubgroup("visibility").addString("required_stage", ...)`) — uses direct compile-time FTB API | YES (`StagesQXFtbConfigCompat.tryAddRequiredStageString` reflectively, with fallback method scanning) — uses `stagesqx` subgroup labeled "StagesQX" |
| Subgroup name | `visibility` | `stagesqx` (with display name "StagesQX") |
| `isVisible(TeamData) HEAD cancel` | YES (returns false when stage missing) | YES |
| `TeamData.canStartTasks(Quest) HEAD cancel` | **NO — mine has no equivalent mixin** | YES (`TeamDataStageGateMixin`) — prevents quest progression when stage missing |
| Holder interface to read field across mixin boundaries | NO — mine reads field inside the mixin (chapter and quest mixins don't share a holder type) | YES (`StagesQXFtbQuestsRequiredStageHolder`) — TeamDataStageGateMixin can cast `Quest` to the holder and read `stagesqx$getRequiredStageId()` |
| Server-side stage check helper | YES (`StageRequirementHelper.hasStage(player, stage)`) | YES (`StagesQXFtbQuestsStageGate.requiredStageMetForServerLogic`) — reflectively resolves `ServerQuestFile.getInstance().getCurrentPlayer()` |
| Client-side stage check helper | YES (`StageRequirementHelper.hasStageClient(stage)` reads `ClientStageCache`) | YES (`requiredStageMetForVisibility`) |
| Reflective FTB Teams team-stage check | NO (mine uses its own backend) | YES (in solo/team mode the gate respects FTB Teams when `ftbquests_team_mode=true`) |

### Verdict
**FORK BETTER on one specific axis: TeamData.canStartTasks gating**

The fork's `TeamDataStageGateMixin` blocks task progression when the required stage is missing. Mine's `isVisible` mixin only hides the quest in the UI; if the player can still see/click it (e.g., visibility is overridden), they can still start tasks because the canStartTasks path isn't gated.

### What fork has that mine doesn't
- `TeamDataStageGateMixin` on `TeamData.canStartTasks(Quest)` — HEAD-cancels with `false` if the quest's required_stage is missing. Without this, a UI bug or incorrect visibility override could let players progress on stage-locked quests.
- `StagesQXFtbQuestsRequiredStageHolder` interface — clean way for `TeamDataStageGateMixin` to read the field from any `Quest` instance.
- Editor entry in `stagesqx` named subgroup ("StagesQX > required_stage" header) is more discoverable than placing it inside the existing `visibility` subgroup. Pack devs report finding mine's entry in the `visibility` group is non-obvious.

### What mine has that fork doesn't
- Direct compile-time `addString(...)` call rather than reflection — simpler, but more brittle if FTB changes the API. Mine compiles against a specific FTB Library version.

### Recommended ports
- Port `TeamDataStageGateMixin` — small mixin that prevents task progression on stage-gated quests. This is a real correctness gap.
- Optionally add a `RequiredStageHolder` interface and have `QuestMixin`/`ChapterMixin` implement it so future cross-mixin reads are clean.

---

## Feature 6: Lock overlay + z-order

### Files
- mine: `src/main/java/com/enviouse/progressivestages/client/renderer/LockIconRenderer.java` (z=1000 icon, z=199 highlight), `mixin/client/EmiStackWidgetMixin.java`, `mixin/client/EmiScreenManagerMixin.java`.
- fork: `com/stagesqx/neoforge/StagesQXNeoForgeClient.java` (`LockedItemDecorator implements IItemDecorator` registered via `RegisterItemDecorationsEvent`).

### Side-by-side
| Capability | Mine | Fork |
|---|---|---|
| Renders in vanilla inventory slots | NO — mine has no `IItemDecorator` registration | YES — `RegisterItemDecorationsEvent` registers for every Item, covering ALL containers (vanilla, modded chests, hoppers, shulker boxes opened in any GUI) |
| Renders in EMI panel | YES (via `EmiStackWidgetMixin`) | YES (decorator runs in EMI's slot rendering too — but EMI may opt out by drawing without `renderItemDecorations`; uncertain) |
| Renders in JEI panel | partial — JEI handles its own greying; mine's decorator does not paint there | YES, but skips JEI via class stack-walk (`stackWalkAny ... f.getClassName().startsWith("mezz.jei.")`) so JEI's own greying owns the visuals |
| Z-order | z=1000 (icon), z=199 (highlight); also `RenderSystem.disableDepthTest()` | z=+200 (relative pose-stack translate) |
| `insideSlotWidget` ThreadLocal | YES — mine uses it to suppress double-rendering when EMI's `ItemEmiStack` triggers a nested `renderItem` call inside `SlotWidget.render` (mine's `EmiScreenManagerMixin` checks the flag) | NO equivalent — fork's stack-walk for JEI plays the same role for that vendor only |
| Configurable position (top_left/top_right/bottom_left/bottom_right/center) | YES | NO (centered by default) |
| Configurable icon size | YES (`emi.lock_icon_size`) | NO (16x16 fixed) |
| Configurable highlight color | YES (`emi.highlight_color` ARGB) | NO |
| Texture-based icon | YES (`textures/gui/lock_icon.png`) | NO — uses U+1F512 padlock glyph rendered via `Font` |
| Disable highlight separately from icon | YES (`emi.show_highlight` vs `emi.show_lock_icon`) | NO |
| Vanilla GUI coverage (chests, etc.) | **NO** — mine only renders inside EMI widgets | YES (the killer feature of the IItemDecorator approach) |

### Verdict
**FORK BETTER on coverage; MINE BETTER on configurability and EMI integration depth**

The fork's `IItemDecorator` registration covers every vanilla and modded inventory slot for free — the user's hotbar, chests, shulker boxes, double chests, modded backpacks, every `Slot` rendered through `Screen.renderSlot` → `renderItemDecorations`. **Mine does not render lock icons in vanilla containers at all** because mine's lock-overlay path is exclusively the EMI mixin.

### What fork has that mine doesn't
- `RegisterItemDecorationsEvent` registration on every Item — covers ALL slots. (`StagesQXNeoForgeClient.LockedItemDecorator`).
- JEI stack-walk skip (`stackWalkAny(JEI_RENDER_STACK, f -> f.getClassName().startsWith("mezz.jei."))`) so JEI's own greying owns the visuals.

### What mine has that fork doesn't
- Configurable position (5 anchors) and size.
- Configurable highlight color.
- Texture-based icon (better visual quality at larger sizes).
- `insideSlotWidget` ThreadLocal coordination for EMI's nested render calls.
- `RenderSystem.disableDepthTest()` defensive pose protection.

### Recommended ports
- **HIGH PRIORITY**: Port the `IItemDecorator` registration. Add a `LockedItemDecorator` registered for every item via `RegisterItemDecorationsEvent` so lock icons appear in vanilla inventories, modded backpacks, chests, etc. Keep mine's existing EMI mixin for the EMI panel. Add the JEI stack-walk skip to avoid double-rendering when the decorator runs inside JEI's render pipeline.

---

## Feature 7: LockSounds

### Files
- mine: `src/main/java/com/enviouse/progressivestages/server/enforcement/ItemEnforcer.java` (`playLockSound` lines 194-211 + `notifyLockedWithCooldown` lines 216-250 + `clearCooldowns` line 255).
- fork: `com/stagesqx/neoforge/LockSounds.java`.

### Side-by-side
| Capability | Mine | Fork |
|---|---|---|
| Resolve sound id via `BuiltInRegistries.SOUND_EVENT.get(parse(...))` | YES | YES |
| Fallback when sound id invalid | YES (NOTE_BLOCK_PLING) | NO (silent no-op if `null`) |
| Play via `player.playNotifySound` | YES (`SoundSource.PLAYERS`) | YES (`SoundSource.MASTER`) |
| Configurable volume / pitch | YES | YES |
| Per-player cooldown | YES (ms-based, `notification_cooldown` config, default 3000ms) — also keyed by item id (so each unique item has its own cooldown) | YES (4-tick fixed, not configurable) |
| Cooldown granularity | per (player, itemId) and per (player, type+stageId) | per player only |
| Cleanup on logout | YES (`clearCooldowns(playerId)` called) | NO (`COOLDOWN.put` never cleared on logout — minor leak) |
| Configurable cooldown | YES | NO |

### Verdict
**MINE BETTER**

Mine has finer-grained cooldown (per-item, configurable, cleaned up). Fork's 4-tick (200ms) flat cooldown is simpler but coarser.

### What fork has that mine doesn't
- Nothing meaningful. (`SoundSource.MASTER` over `SoundSource.PLAYERS` is a stylistic choice; both are reasonable.)

### What mine has that fork doesn't
- Configurable cooldown duration.
- Per-(player, itemId) granularity — different blocked items each get their own cooldown window so messages don't smother each other.
- Per-(player, type+stage) granularity for non-item locks (block/dim/entity).
- Cleanup on logout.
- Fallback sound on invalid id.

### Recommended ports
- None.

---

## Feature 8: v2.0 inversion semantics

### Files
- mine: `LockRegistry.getRequiredStage(Item)` returns `Optional<StageId>` — first-match-wins. `StageManager.hasStage(player, StageId)` checks team data.
- fork: `StageAccess.blockedByMissingRequiredStages(gating, accessStages)` — `!gating.isEmpty() && !accessStages.containsAll(gating)`.

### Side-by-side
| Capability | Mine | Fork |
|---|---|---|
| Multi-stage gating per item | NO — `getRequiredStage(Item)` returns ONE stage; the first matching `PrefixEntry` wins (insertion order = stage load order) | YES — `effectiveStagesGatingItem` returns a SET of stage ids, and the player must own ALL of them |
| Single-stage required check | YES — `hasStage(player, stageId)` | YES — special case of the all-of check |
| Per-stage `[unlocks]` whitelist subtraction | partial — mine has `alwaysUnlocked` per-category whitelist (`isWhitelisted(id)` short-circuits) but it's a single global per-category set, not per-stage carve-outs | YES — `effectiveStagesGatingItem` removes stage ids whose own `[unlocks]` whitelists this item |
| Vanilla-namespace inheritance (`minecraft=true` child stage extends parent's vanilla locks) | NO — mine has no `minecraft = true` toggle that auto-extends to the namespace; locks are per-id only | YES — `effectiveLockStagesForVanillaNamespace` |
| Transitive dependency expansion in access set | YES (via `linear_progression`) — but it's a config-gated behavior, not a free-running BFS | YES — `transitiveDependencyIds` BFS, always on |
| Behavior when two stages lock same item | mine returns the FIRST stage in iteration order (deterministic but order-dependent) | fork blocks until ALL are owned |

### Verdict
**FORK BETTER (semantically richer)**

The fork's "must own all gating stages" is a strictly more expressive model than mine's "first match wins". For most modpacks the difference doesn't surface (each item is usually locked by exactly one stage), but it does manifest if a pack dev splits ownership of a single item between two stages.

### What fork has that mine doesn't
- Multi-stage gating: an item can require multiple stages simultaneously.
- Per-stage `[unlocks]` whitelists that subtract specific stage ids from the gating set (so a stage can lock a whole mod but exempt specific items, and another stage's exemption doesn't apply to the first).
- `effectiveLockStagesForVanillaNamespace` — child-stage inheritance for `minecraft=true` parents.

### What mine has that fork doesn't
- Insertion-order determinism: when multiple stages CAN match (mine's first-match wins), the order is the stage load order, which is documented and predictable.
- Per-category `alwaysUnlocked` whitelist for global carve-outs.

### Recommended ports
- This is the deepest semantic gap and the riskiest to port. Two options:
  1. Stay with single-stage model (mine's current). Document it as a 1-stage-per-item invariant.
  2. Port multi-stage gating: change `getRequiredStage(Item)` to return `Set<StageId>` and update every `hasStage(player, ...)` call site to require all. This is a major refactor touching every enforcer.
- The `effectiveLockStagesForVanillaNamespace` (12-line vanilla-namespace child inheritance) is a smaller, isolated win and worth porting on its own merits — but only if mine has a `minecraft=true` shorthand, which it currently does NOT.

---

## Feature 9: Visual Workbench predicate

### Files
- mine: `compat/visualworkbench/VisualWorkbenchShim.resolveVanillaEquivalent(state)` + `BlockEnforcer.canInteractWithBlock(BlockState)` + `BlockEnforcer.isBlockLockedForPlayer(ServerPlayer, BlockState)`.
- fork: `StageAccess.isBlockBlocked` calls VW reflectively if the block isn't blocked under its own id.

### Side-by-side
| Capability | Mine | Fork |
|---|---|---|
| Reflection-only VW lookup | YES — `VisualWorkbenchShim.resolveVanillaEquivalent` (reflective) | YES — same pattern |
| Order: check own id first, fall back to vanilla | YES — `BlockEnforcer.isBlockLockedForPlayer(state)` first checks `state.getBlock()`, then falls back to VW-resolved vanilla | YES — `StageAccess.isBlockBlocked` does the same |
| Notifies with vanilla id when own id is unlocked but VW-replaced is locked | YES — `BlockEnforcer.notifyInteractionLocked(BlockState)` rechecks VW | implicit (notification path uses `primaryRestrictingStageForAbstractId` on whichever id matches) |
| Used in placement check | NO — `canPlaceBlock(Block)` only takes a Block, not a state. VW shim only consulted on interact, not on place | similar — fork's `isBlockBlocked` is the universal entry point |
| Used in interact check | YES (`canInteractWithBlock(BlockState)`) | YES |

### Verdict
**EQUIVALENT** with one small asymmetry: mine's placement-check overload doesn't take a BlockState so VW substitution doesn't engage on place. In practice, placement uses the held-item id which is checked through the item path, so this is rarely user-visible.

### What fork has that mine doesn't
- Universal entry point (`StageAccess.isBlockBlocked`) so VW substitution applies everywhere uniformly. Mine has both Block and BlockState overloads, only the latter does VW.

### What mine has that fork doesn't
- Equivalent capability via the BlockState overload (covers interact). Plus mine's notification helper rechecks VW for the lock message.

### Recommended ports
- Optional minor: add a `canPlaceBlock(BlockState)` overload that consults VW. Not load-bearing.

---

## Final remarks on the 9-feature audit

| # | Feature | Verdict | Notable port? |
|---|---|---|---|
| 1 | starting_stages | DIFFERENT | Add `isValidStageName` regex skip; reconsider re-grant policy |
| 2 | Inventory eject | MINE BETTER | none (consider spectator bypass) |
| 3 | `&` color text | MINE BETTER | audit gaps (see TEXT AUDIT) |
| 4 | FTB Library StageProvider | MINE BETTER | Optional team-mode delegation |
| 5 | FTB Quests Quest/Chapter | FORK BETTER (small) | **Port `TeamData.canStartTasks` mixin** |
| 6 | Lock overlay z-order | FORK BETTER (coverage) | **Port `IItemDecorator` registration** |
| 7 | LockSounds | MINE BETTER | none |
| 8 | v2.0 inversion | FORK BETTER (semantically) | optional `effectiveLockStagesForVanillaNamespace` if `minecraft=true` is added |
| 9 | Visual Workbench | EQUIVALENT | optional placement overload |

---

## TEXT AUDIT (Item 3 follow-up)

### Hardcoded user-visible strings in mine that should be configurable

The `&`-color message-config system covers the hot-path (item locked, type locked, dropped, moved, tooltip). But several command-feedback paths and the dependency-tree/info/validate/ftb-status output use direct `Component.literal(...)` and are not configurable:

| File:Line | String | Suggested config key |
|---|---|---|
| `server/commands/StageCommand.java:298` | `"  &7• " + color + displayName + check + "&7" + depStr` (each line of `/stage list`, includes literal `(requires: %s)`) | `messages.cmd_list_entry` (placeholders: `{prefix}, {name}, {check}, {deps}`) and `messages.cmd_list_requires_suffix` |
| `server/commands/StageCommand.java:291` | `" (requires: ...)"` literal substring | `messages.cmd_list_requires_format` (placeholder: `{deps}`) |
| `server/commands/StageCommand.java:334` | `"Stage not found: " + stageName` (only this duplicate uses `Component.literal`, the other call sites use `MSG_CMD_STAGE_NOT_FOUND`) | reuse existing `messages.cmd_stage_not_found` |
| `server/commands/StageCommand.java:341` | `"=== " + def.getDisplayName() + " ===")` (`/stage info` header) | `messages.cmd_info_header` |
| `server/commands/StageCommand.java:343` | `"  ID: " + def.getId().toString()` | `messages.cmd_info_id` |
| `server/commands/StageCommand.java:349` | `"  Dependencies: (none)"` | `messages.cmd_info_deps_none` |
| `server/commands/StageCommand.java:353` | `"  Dependencies: " + depStr` | `messages.cmd_info_deps` |
| `server/commands/StageCommand.java:357` | `"  Description: " + def.getDescription()` | `messages.cmd_info_description` |
| `server/commands/StageCommand.java:379` | `"  Total locks: " + lockCount` | `messages.cmd_info_total_locks` |
| `server/commands/StageCommand.java:390` | `"=== Stage Dependency Tree ==="` | `messages.cmd_tree_header` |
| `server/commands/StageCommand.java:403` | `"  (No stages defined)"` | `messages.cmd_tree_empty` |
| `server/commands/StageCommand.java:417` | `"  ⚠ " + stageId.getPath() + " (orphaned - dependency not found)"` | `messages.cmd_tree_orphaned` |
| `server/commands/StageCommand.java:436-438` | `indent + displayName + " [" + stageId.getPath() + "]"` (`/stage tree` node) | `messages.cmd_tree_node` |
| `server/commands/StageCommand.java:482` | `"=== Stage Validation ==="` | `messages.cmd_validate_header` |
| `server/commands/StageCommand.java:484` | `"[ProgressiveStages] Validating stage files..."` | `messages.cmd_validate_starting` |
| `server/commands/StageCommand.java:486` | `"  Found " + totalFiles + " stage files"` | `messages.cmd_validate_found` |
| `server/commands/StageCommand.java:495` | `"  SUCCESS: " + fname + " validated"` | `messages.cmd_validate_success` |
| `server/commands/StageCommand.java:501` | `"  ERROR: " + fname + " has " + errorMsg` | `messages.cmd_validate_syntax_error` |
| `server/commands/StageCommand.java:507` | `"  ERROR: " + fname + " - " + errorMsg` | `messages.cmd_validate_validation_error` |
| `server/commands/StageCommand.java:512` | `"      - " + invalidItem` | `messages.cmd_validate_invalid_item` |
| `server/commands/StageCommand.java:523` | `"  ⚠ " + depError` | `messages.cmd_validate_dep_warning` |
| `server/commands/StageCommand.java:536` | `"  ✗ Starting stage not found: " + stageName` | `messages.cmd_validate_starting_not_found` |
| `server/commands/StageCommand.java:549,552` | `"  SUMMARY: ... valid, all passed!"` and the partial-error variant | `messages.cmd_validate_summary_ok`, `messages.cmd_validate_summary_errors` |
| `server/commands/StageCommand.java:569-627` | The whole `/stage diagnose ftbquests` output (15+ lines of `Component.literal` for status fields) | `messages.cmd_ftb_status_*` family — or skip (this is dev/debug output, less critical to configure) |
| `client/ClientEventHandler.java:81` | `.orElse("None")` (used as `currentStageName` when player has no stage) | `messages.tooltip_current_stage_none` |

Notes:
- `StageCommand.java:484`'s prefix `"[ProgressiveStages]"` should probably be derived from a single configurable prefix string (e.g., `messages.prefix`), echoing the fork's `message.stagesqx.prefix` pattern. Mine has the prefix baked into individual messages today (e.g., `MSG_MISSING_DEPENDENCIES` includes `"[ProgressiveStages]"`).
- Most chat/tooltip hot paths ARE already configurable (item lock, type lock, drops, hotbar, missing deps, all `MSG_CMD_*` for grant/revoke/list-header/check/reload/trigger). The audit gap is mostly in `/stage info`, `/stage tree`, `/stage validate`, `/stage diagnose ftbquests`, and `/stage list` row format.
- The `"Unknown Item"` fallback inside `getMsgTooltipMaskedName()` (line 725 of StageConfig.java) is already configurable — flagged as not-an-issue.
- `"None"` literal in `ClientEventHandler.java:81` is the only hardcoded user-facing string in the tooltip path.

### Total hardcoded user-visible strings found in audit

**~25 strings** spread across `StageCommand.java` (`/stage info`, `/stage tree`, `/stage validate`, `/stage list` row format, `/stage diagnose ftbquests`) and 1 string in `ClientEventHandler.java`.

If the user wants full text customization parity, this list is what to make configurable. Most are command-debug surface that pack devs see during configuration; non-priority for end-user UX.
