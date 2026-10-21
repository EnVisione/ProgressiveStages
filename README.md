# ProgressiveStages

![ProgressiveStages logo](src/main/resources/progressivestages.png)

A NeoForge mod for Minecraft 1.21.1 that gives modpack developers complete control over stage-based progression. Define stages as TOML files; ProgressiveStages locks items, blocks, entities, fluids, dimensions, recipes, enchantments, crops, mob spawns, pets, regions, structures, screens, and player interactions until the player has earned the right stage(s).

**ProgressiveStages 3.1.1** includes the 3.1.0 player guide and editor improvements, plus the audited source-scoped stage grant API introduced on the 3.0.6 maintenance line.

See the [configuration and technical documentation](DOCUMENTATION.md) for setup and stage files.

---

📦 **CurseForge:** [ProgressiveStages](https://www.curseforge.com/minecraft/mc-mods/progressivestages) · 💬 **Community:** [Discord](https://discord.com/invite/9v4gaRSfdJ) · 🐛 **Issues:** [GitHub](https://github.com/EnVisione/ProgressiveStages/issues)

---

## Start here

Start with [configuration and technical documentation](DOCUMENTATION.md). For common editor
questions, see [Block overrides and stage guides](docs/troubleshooting/documentation.md). Contributors
can follow the [build and test guide](docs/testing/documentation.md).

---

## What's new in 3.1.1

Trusted integrations can grant one source-attributed stage despite missing prerequisites through
`ProgressiveStagesAPI.grantStageFromSourceWithDependencyBypass`. It requires an online level-3
operator on the server thread and records an audit reason. Other stage policies still apply.
See the [3.1.1 release guide](docs/release/3.1.1/documentation.md) for the API contract and upgrade
details. Install 3.1.1 on the server and every client.

---

## What's new in 3.0.5

- **Guided ownership and access builder.** The editor's Access tab writes inherited, personal,
  team, and server ownership, optional LuckPerms inbound and outbound mappings, retention mode,
  context values, command descendant gates, and direct interaction definitions without requiring
  raw TOML. Contexts use separate key and value controls, so commas and line breaks remain
  inside their values. Command gates include descendants by default, with an explicit opt out.
  Duplicate keys and failed saves retain editable input. Existing source
  comments and unrelated files remain in the draft. Quoted table paths and multiline values
  remain intact when editing access settings and interaction rows. Config and datapack discovery
  recognize quoted stage headers as well as dotted and inline stage definitions. Ownership
  and retention controls also edit fields inside inline tables while preserving their structure.
  The Access tab supports inline arrays of interactions, command gates, and permission mappings,
  including additions, removals, contexts, and the paired Selling Bin preset. Inventory rule
  condition edits in the Rules tab retain descriptions, aliases, and unknown nested fields.
- **Strict access configuration validation.** Incorrect LuckPerms booleans and non-string
  group or permission entries are rejected instead of silently changing their meaning. The
  builder places returned diagnostics beside ownership and access rules, with a full validation
  summary. Results are tied to the draft revision and disappear after an edit. Provider feedback
  explains current team fallback, dormant LuckPerms mappings, pending or missing groups, and
  unresolved command paths without rewriting the source.
- **Selling Bin interaction presets.** Direct interactions can be authored with
  `tag:c:armors` or `all:*` held item selectors and the exact
  `id:selling_bin:selling_bin` block selector. For selected items such as bread, enable
  **Also restrict GUI insertion** to create matching held item and inventory insertion rules
  together. A whole block `all:*` rule also controls an empty hand menu open. Whole bin access
  remains a separate block right click rule when that behavior should be explicit.
- **Optional LuckPerms configuration.** The builder supports synchronized or permanent inbound
  mappings and outbound group or permission mappings. Outbound mutations use transient nodes with
  tracked ownership and cleanup retries. Queries preserve current context values and separate
  unavailable data from an authoritative permission result.
  The online update queue retains pending work on overflow and resumes its population scan
  within the existing per tick budget. Online reconciliation collects all inbound permissions
  before changing sources and retries the complete observation when input changes during queries.
  Source and eligibility changes commit together before listeners or client updates run, preserving
  deliberate administrative changes made by a listener after that commit.
  Outbound updates recheck the captured state between provider writes and before publication.
  Rejected updates retain exact node ownership for cleanup and retry.
  Offline source and eligibility changes also use an isolated draft and commit together only
  while the captured provider, stage, membership and server state remains current.
  Runtime integration is not accepted. Command gates retain native
  permission checks. See the [LuckPerms troubleshooting guide](docs/troubleshooting/luckperms/documentation.md).

## What's new in 3.0.4

- **Actor-aware stage ownership.** Add optional `team_stage = false` or `true` in a stage's
  `[stage]` table to keep a profession personal or use the active FTB Teams owner. Omit it to
  inherit `general.team_mode`. Server stages remain shared, and each player receives an effective
  view made from personal, team, and server records.
- **Ownership diagnostics.** Admins can run `/stage explain scope <player> <stage>` and use the
  bounded `/stage debug progression on|status|off` capture to inspect owner resolution without
  logging names, UUIDs, inventories, or permission trees.

- **Survival armor-slot correction.** Players can place armor into armor slots when no matching
  `item_into_inventory` lock applies. Configured inventory insertion rules still enforce their
  matching item and destination selectors.

- **Stage aware enchantment generation.** `[enchants].max_levels` and
  `[enchants].selection_weights` now shape enchanting table choices and player aware enchanted loot
  before enchantments are applied. A maximum of zero removes retained copies, while a selection
  weight of zero prevents new rolls without stripping existing items. The existing enchantment
  strip remains a final safety net.
- **Visual enchantment generation controls.** The Rules tab now loads and edits maximum levels and
  selection weights without requiring TOML. Search the connected server's enchantment registry,
  choose an exact enchantment, then set either control or both.
- **Polished in game progression map.** The compact advancement style window now uses readable
  stage-group names, better spacing, shorter dividers, structured bonus cards, item icons for exact
  selectors, cycling tag icons, and focused branch highlighting.
- **Recipe book style inventory button.** The progression map button now uses a parchment map icon,
  matching vanilla button states, and a wider default gap from the recipe book button. Existing
  visibility, position, size, and icon-size configuration remains available.
- **Category menu foreground repair.** The open progression map category list now renders above
  every stage frame and item icon, including nodes positioned directly beneath the menu. Category
  selection, scrolling, filtering, graph movement, zoom, and the pinned inspector are unchanged.
- **Safe EMI world exit.** An EMI refresh now stops at the client disconnect boundary. Leaving a
  world with EMI installed cannot start a reload after the client level has closed.
- **World generation safe structure rules.** Structure aware entity decisions use an immutable
  server published session snapshot during asynchronous chunk generation. Ocean ruins and similar
  structures no longer call server thread only session state from a chunk worker.
- **Smithing table enforcement repair.** Smithing upgrades now honor locked recipe identifiers,
  locked recipe outputs, locked result items, and transitive ingredient gates. The server rejects
  result pickup even when another mod changes the visible smithing output.
- **Clearer player layout toolbar.** The redundant `Arrange and save` action is gone. Dragged stage
  positions still save to the draft, `Use automatic layout` still removes manual coordinates, and
  `Apply changes` remains the single server publication flow.
- **Correct recipe choices in the Easy Builder.** Choose **Recipe output item** to write
  `[recipes].locked_items`, or **Exact recipe identifier** to write `[recipes].locked_ids`. The
  editor rejects ambiguous generic recipe fields before they can change a live stage.
- **Player inventory insertion rules.** A stage can now gate one item selector entering a block,
  menu, or stable inventory-owner target. The new Easy Builder flow writes paired
  `[[interactions]]` rules, supports the complete prefix system on both selectors, and keeps
  playerless automation outside player-stage enforcement.

---

## What's new in 3.1.0

See the [3.1.0 release guide](docs/release/3.1.0/documentation.md) for the new player guide, editor
settings, structure rules, block overrides, and upgrade notes.

- **Safe movement defaults.** Fresh showcase files and editor-created stages leave jump, sprint,
  swim, climb, and elytra available. Pack authors can still add an explicit `[abilities].locked`
  list whenever a movement gate is wanted. Existing files are never rewritten by default generation.
- **Clearer editor help.** Common labels use plain names such as `Stage name`, `Stage icon`, and
  `Required stages`. Hover or focus the question mark beside a field for a short explanation and
  example. The layout graph also offers Edit, Connect, Duplicate, and Delete beside a selected node
  or from its right-click menu.
- **Translation friendly editor.** Browser page translation can translate labels and help text while
  keeping the editor itself independent of a translation service. External browser providers may
  process visible page content, so verify IDs, commands, TOML, and selectors after translation.
- **Structure entry and protection controls.** The Rules tab can edit the protected structure list,
  entry policy, signed priority, entry padding, block breaking, block placement, explosions, and mob
  spawning independently. `entry_allowed = true` keeps a protection stage active after another stage
  opens entry.
- **A stage-specific player guide.** Pack authors can write unlock steps, next steps, and locations
  in the editor or `[guide]`. Players see a gold `?` on the selected stage, with a matching
  `How to unlock <stage name>` tooltip and a separate unbound keybind. A global Settings switch and
  individual stage switches control visibility.
- **Clear block override names and selectors.** New rows use `[[blocks.overrides]]` for any block.
  Exact IDs, lists, tags, and `mod:` selectors are supported; existing `[[ores.overrides]]` rows
  remain compatible. A mod selector includes every block from that mod, not only ores.
- **Typed server settings.** The Settings page is generated from the server config spec. Invalid
  types, ranges, enum values, and unknown keys stay in the draft with an inline error. Accepted live
  settings apply with the stage transaction, while world or client restart settings are shown as
  pending. The transaction can be rolled back.

## What's new in 3.0.2

- **Reliable single-player editor access.** `/pstages editor` now accepts the authenticated
  loopback browser flow while connected to an integrated single-player server. Permission level 3
  is still required, and remote browser requests remain rejected.
- **Simpler stage editor.** The editor uses a restrained dark gray and gold layout centered on
  stages, the easy builder, player layout, TOML source, validation, and review. Advanced tools
  remain available without crowding the primary workflow.
- **Optional inventory button.** Set `client.show_inventory_button = false` to remove the
  progression map button from the survival inventory without disabling commands or the optional
  keybind.
- **Move and resize the inventory button.** Configure `inventory_button_x`,
  `inventory_button_y`, `inventory_button_width`, `inventory_button_height`, and
  `inventory_button_icon_size` under `[client]`. Positions are relative to the inventory, and the
  centered lock icon automatically shrinks to fit.

---

## What's new in 3.0.1

- **Everything selector.** Rules can use `all:*` to match every registered entry in the selected
  category. Items means every item, fluids means every fluid, structures means every structure,
  and so on. Parent linked exceptions and normal priority resolution still apply, so a broad rule
  can safely keep selected IDs, mods, tags, or names available.
- **Multiplayer safe entity presence.** Entity rules now include the `presence` action. If every
  player inside simulation distance is denied a mob, its spawn is cancelled. If nearby players
  disagree, the mob remains for allowed players and is concealed only from denied players. Denied
  players cannot render, target, attack, interact with, or mount that entity. Denied mobs cannot
  target or damage those players. An owned negative stage uses `effect = "deny"`, while a normal
  missing stage progression gate uses `effect = "lock"`.

---

## What's new in 3.0

- **Fifty stage first-launch showcase tree** — an empty stages directory now receives exactly three
  beginner paths, nine evolutions, nine direct masteries, nine merged classes, six stackable
  engineers, four mutually exclusive mining modes, five temporary powers, four world gates, and an
  at-least-three Grandmaster finale. Configurable stage slots can deny a choice, replace the oldest
  choice, replace the lowest-priority choice, replace every active choice, or allow every buff to
  stack. Existing packs are never overwritten or injected with showcase stages.
- **Direct category picker and repaired maps** — the in-game progression menu opens a category list
  instead of cycling one category per click. The editor graph adds category filtering, search with
  prerequisite context, curved connectors that follow dragged nodes, crossing reduction, zoom,
  true fit-to-panel behavior, exact player-map coordinates, drag placement, arrange-and-save, and a
  real automatic-layout reset that removes manual coordinates instead of overlapping nodes at zero.
- **Item-purchased classes in the easy editor** — **How players obtain this stage** now has a
  searchable live-registry payment builder. Choose items and amounts plus optional XP, cooldown,
  trigger bypass, and revoke refund. The in-game tree uses the existing atomic server-authoritative
  purchase engine. Cost summaries use readable translated names such as `12x Gold Ingots, 2s
  cooldown` instead of registry paths. The showcase uses exactly two purchased beginner paths and
  one gameplay-triggered beginner, while only fifteen of fifty stages are purchases.
- **Selector-based `[[drop_modifiers]]`** — target source blocks, final output items, optional tools,
  enchantments, owned or missing stages, conditions, priorities, caps, and exclusive stacking. The
  showcase Diamond Engineer costs 32 diamonds and doubles only final diamond drops produced with
  Fortune from diamond or deepslate diamond ore.
- **Temporary and triggered lock rules** — `[[temporary_locks]]`, `[[temporary_unlocks]]`, `[[triggered_locks]]`, and `[[triggered_unlocks]]` apply priority-based locks or permissions by dimension, structure, biome, height, health, stage state, effect, movement state, KubeJS predicate, combat, attack, hurt, kill, command, or API timer. A single rule can target items, blocks, fluids, entities, recipes, dimensions, structures, and `jump`/`elytra`/`sprint`/`swim`/`climb`, with per-rule exceptions. Static gates are priority `0`, conditional rules default to `100`, the highest priority wins, and a lock safely wins an equal-priority tie.
* **Vanilla advancement style stage map.** `/stage`, `/stages`, `/pstages`, `/stage gui`, the keybind, or the progression map button beside the inventory recipe book button opens a compact, draggable, and mouse wheel zoomable progression graph using vanilla task, goal, and challenge frames, dependency connectors, tiled backgrounds, hover cards, search, owned stage filtering, click to pin details, trigger progress, structured equipment and affinity previews, cycling tag icons, unlock previews, and server validated purchases. Set `client.show_inventory_button = false` in `progressivestages.toml` to remove only the inventory button.
- **Author-controlled map layout** — each stage's `[display]` can set `x`, `y`, `frame`, `background`, `reveal`, and `sort_order`; omit `x` + `y` for automatic dependency graph layout. Reveal can be `always`, `dependencies`, or `unlocked`. The React editor's **Player layout** page pans, zooms, drags icons using these same coordinates, draws new prerequisite branches, removes existing branch lines, and saves exact X and Y positions.
- **Named trigger counters** — `type = "custom_counter", counter = "quest_points", count = 10` bridges stage TOML to `/stage counter get|add|set|reset ...` and `ProgressiveStages.counter/addCounter/setCounter/resetCounter(...)` in KubeJS.
- **Expanded KubeJS API** — actual-change `grant`/`revoke`, plus `toggle`, `exists`, slot-aware `available`, `slot` decision details, dependency queries, tag queries/bulk operations, counters, immediate trigger evaluation, and `openGui`.
- **KubeJS callback lifecycle**. Server script callbacks survive the transition from startup loading to the server thread. Reload replaces their registrations.
- **Explicit actor APIs**. Java integrations can resolve ownership and read immutable individual snapshots for online or offline actors. Fresh offline contexts support grants and revocation, with actor specific deferred rewards, cascades and refunds. Legacy UUID team queries retain their meaning. See the [API contract](DOCUMENTATION.md#151-progressivestagesapi).
- **Purchase attribution**. Players with temporary access can still purchase independent ownership, retaining their separate lease. Repeated item cost rows require their combined quantity before any payment is taken. New purchase refunds belong to the payer, including deferred refunds after logout. Grant callbacks and reward commands use the committed purchase receipt, so immediate revocation applies the configured refund and preserves the purchase cooldown. Personal and team receipts remain separate, and later configuration edits preserve the original refund terms. See [purchase configuration](DOCUMENTATION.md#426-cost--skill-tree-purchasable-stages).
- **Live progression map updates**. An open map updates its nodes and details after stage synchronization. Matching clients and servers also refresh purchase offers through the existing response limit, without reopening a map that the player closes.
- **Pack-authored What to do next guide.** In a stage's **Setup → What to do next** section, edit `How to unlock`, `Next steps`, and `Where to find it`. The optional `[guide]` table also accepts `enabled = true` (the default) for a per-stage on/off switch and `recommendation = "auto"`, `"include"`, or `"exclude"` for the guide keybind list. The main `general.enable_stage_guide` setting is the master switch: when it is off, every guide is hidden, and turning it back on restores each stage's saved `enabled` choice. The editor presents these as plain language fields with hover help, a placeholder chooser, preview, and character limits. Use `{stage_name}`, `{stage_requirements}`, `{remaining_requirements}`, and `{stage_progress}` for displayed values, or doubled braces for literal braces. The `{stage_requirements}` placeholder expands to the full visible unlock summary, including routes, costs, cooldowns, and group slots. If any guide field uses it, that summary appears in the authored field instead of being repeated above it. Put it on its own line after an introduction such as `Meet these requirements:`; the value includes full sentences and should not be inserted after words like `Complete`. While the gold `?` help is open, the inspector hides details already covered by that summary and the purchase button; close the help to use the purchase option. `{remaining_requirements}` reports known missing stages, trigger steps, item or XP costs, full stage groups, and purchase cooldowns; it says `Unavailable` when the server cannot safely confirm them. Players use the normal advancement style map, then open an enabled stage's gold `?` control beside the close button to read its unlock help. Long text wraps in the inspector; when it overflows, scroll with the mouse wheel over the panel and use the colored bar at its right edge to track position. Short guides show no scrollbar. The separate guide keybind selects the next visible suggestion.
- **Correctness and dedicated-server parity** — per-stage exemptions and `[unlocks]` are scoped to their owning stage, secondary categories retain every gating stage, mixed team/server-scope dependencies store correctly, reload/stop clears runtime state, biome time is exact for any polling interval, and block/fluid/mod locks now sync to dedicated clients and recipe viewers.
- **Reproducible build + tests** — optional integrations resolve from publisher Maven repositories; a fresh clone no longer needs ignored `libs/*.jar` files.
- **`[rewards]` on grant** — the companion to `[cost]`. `[rewards]` hands a stage's loot out **the moment it's granted**: `items = ["minecraft:diamond:5"]`, `effects = ["minecraft:strength:60:1"]` (`id:seconds:amplifier`), `commands = ["give {player} ..."]` (run as the player at permission 2; `{player}` substituted; singular `command` also accepted), `teleport = "[dim] x y z"` (dimension optional), `xp_levels`, `xp_points`. Fires **once per real grant** — applied to the player who earned/bought the stage (not per team member), not on login/sync — for every cause.
- **Six new `[[triggers]]` conditions** — `reach_y` (state: while current Y ≥ `count`; aliases `altitude`/`y_level`/`height`); `fish` (vanilla `FISH_CAUGHT`, retroactive); `sleep` (vanilla `SLEEP_IN_BED`, retroactive); `ride` (blocks ridden on any vehicle — minecart/boat/pig/horse/strider, retroactive); `biome_time` (seconds spent in a target biome/`#tag`; `count` = seconds or a friendly `duration = "5m"`); and `stage_held_for` (held another stage for ≥ `count` seconds; `count` seconds or `duration = "3d"` — grant times are now recorded for **every** stage).
- **Stage tags + bulk ops** — tag stages with `[stage].tags = ["combat","tier2"]`, then `/stage tag grant <players> <tag>`, `/stage tag revoke <players> <tag>`, and `/stage tag list <tag>`. Grant **bypasses dependencies** and **skips stages already owned**.
- **`[cost]` cooldown + refund** — `cooldown` (or `cooldown_seconds`; accepts `"5m"`) is a **per-player** rate limit between skill-tree purchases (server-enforced); `refund_percent` returns that % of a purchased stage's item/XP cost when the stage is later **revoked**.
- **Ability gating expanded** — `[abilities].locked` gates `jump`, `elytra`, `sprint`, `swim`, and `climb`. The server resolves static and conditional rules, then client and server enforcement prevents predicted movement and animation from restoring a blocked ability.
- **Jade + WTHIT in-world overlay** — looking at a locked **block OR mob** shows a red `🔒 Requires: <stage>` line. Pulled from the **Modrinth Maven** (`compileOnly maven.modrinth:jade:...` / `...:wthit:...`) — not bundled jars, inert when the mods aren't installed. Entity locks are now synced to the client to power the mob overlay.
- **`[display].encrypt_blocks`** — `encrypt_blocks = true` masquerades this stage's exact-id locked **blocks** as `encrypt_as` (default `minecraft:stone`) until owned, reusing the ore-spoof pipeline (chunk rewrite, break-speed, drop replacement). Per-stage on/off.
- **Authoring / debug commands** — `/stage simulate [player]` (dry-run: reachable-next stages with % and which conditions are short, plus dep-blocked stages); `/stage new <id>` (scaffold a stage TOML); `/stage export` (write a markdown progression guide to the config folder).
- **Three finer-grained gates (now shipped — were "planned" in earlier 3.0 drafts):**
  - **Stage-aware enchantment selection** — `[enchants].locked` removes locked candidates before enchanting-table and player-aware loot rolls. `max_levels = ["minecraft:sharpness:3"]` limits generated and retained levels. `selection_weights = ["minecraft:mending:0", "minecraft:fortune:10"]` changes exact enchantment roll weights until the stage is owned; weight `0` prevents new rolls without stripping existing copies. The immediate loot pass and periodic inventory scan remain safety nets.
  - **`[beacon].locked`** — `locked = ["id:minecraft:strength", "id:minecraft:haste"]` (MobEffect ids). A player missing the gating stage simply doesn't receive that beacon effect; other players in range are unaffected.
  - **`[brewing].locked`** — `locked = ["id:minecraft:strength", "id:minecraft:swiftness"]` (Potion ids). A player missing the gating stage can't TAKE the brewed potion out of a brewing stand's slots (it brews and sits there until they unlock it). Hopper/automation extraction is gated too, best-effort via the nearest player.
- **Stage map search + reveal controls** — search by stage name, id, description, category, or **locked item id**; hide owned stages; use per-stage `reveal` rules to conceal future branches until prerequisites are met.
- **EMI + JEI both optional (independent controls)** — locked items and recipes can be filtered in EMI and JEI without making either viewer a hard dependency. `[jei].enabled` and `[emi].enabled` are independent and both default to enabled. Stage grants and revokes resend each player's authoritative viewer snapshot before refreshing the enabled viewer. The recipe-viewer reload path remains safe when EMI is absent.

---

## What's new in 2.5

- **`[professions]` lock category** — `[professions].locked = ["id:minecraft:weaponsmith", "mod:somemod", "name:cleric"]` gates **opening a villager's trade GUI by the villager's PROFESSION**. A player lacking the gating stage can't trade with that villager at all (vs. `[trades]`, which hides individual offers by result item). `id:` / `mod:` / `name:` matching (no tags). Wandering traders have no profession and are unaffected — use `[trades]` for those. Fully opt-in (no overhead when unused).
- **`[advancements]` lock category** — `[advancements].locked = ["id:minecraft:nether/root", "mod:somemod"]` **hides locked advancements from the advancements screen entirely** — server-side, the client is never even told they exist. When the gating stage is gained, a full advancement re-send makes them pop into view (no relog). `id:` / `mod:` / `name:` matching; opt-in fast-path when unused.
- **Structure gating enhancements** — `[structures]` now supports `entry_padding` (an integer block buffer that places repelled players well clear of the boundary; also accepted under `[structures.rules]`), and a player who breaches a gated structure is now teleported **back to their last safe position** (where they last stood outside any locked structure) rather than just shoved to the nearest edge.
- **New `[[triggers]]` conditions** — `world_time` (aliases `time_of_day`/`daytime`/`clock`: the current time-of-day tick `0..23999`, e.g. trigger at night); `breed` now takes an **optional** species/tag target (no target = all bred animals, retroactive; with a target = that species/tag, event-counted); `kill_with` now accepts a **`#tag`** victim (summed over the tag's members); and `script` (aliases `js`/`kubejs`/`custom`: a fully custom condition evaluated by a KubeJS-registered predicate via `type="script", id="<conditionId>"`).
- **Datapack-loaded stages** — stage TOML files can ship inside datapacks at `data/<namespace>/progressivestages/stages/*.toml`. They load at world load and on `/reload` and merge with the config-folder stages — **a config file with the same stage id always wins**, so datapacks provide overridable defaults.
- **`[stage]` hidden / color / category now work in the GUI** — previously parsed but inert. Now `hidden = true` omits the stage from the Stage Tree, `color = "#RRGGBB"` tints the stage name (status colour is the fallback), and `category = "..."` shows as a tag in the detail header.
- **Deep KubeJS integration** — a KubeJS plugin binds a global `ProgressiveStages` object: `onGranted`/`onRevoked` callbacks that fire on **every** engine grant/revoke (commands, triggers, quest rewards, skill-tree purchase, regression), `condition('id', player => bool)` to register custom `script:` trigger conditions, plus `has` / `grant` / `revoke` / `list` / `percent` helpers. (Corrects a prior doc claim: KubeJS 7.x has **no** native stage events that fire on engine grants — `onGranted`/`onRevoked` are the reliable hook.) Reset each server-script reload.
- **Deeper `/stage validate`** — now detects full multi-node dependency cycles (not just self-loops), transitively-unreachable stages, and dead trigger targets (a `[[triggers]]` condition whose exact-id entity/block/item/effect — or `kill_with` item — doesn't resolve). Profession ids are validated too.

---

## What's new in 2.4

- **`[attribute]` stage buffs** — a stage can grant attribute modifiers (`[[attribute]]` with `id` / `operation` / `amount`) that apply while the team **owns** the stage. Any vanilla or modded attribute (`minecraft:generic.max_health`, `generic.scale`, `generic.movement_speed`, …), `add` / `multiply_base` / `multiply_total` operations. Applied on grant/login, removed on revoke (transient, reconciled); current health clamps down if max health drops.
- **`[revoke]` + temporary stages (regression)** — stages can now be *lost*. `[revoke]` supports `on_death = true`, `xp_below = N` (XP-maintained — hold the stage only while total XP ≥ N), and `cascade = true` (also revoke dependents). `[stage].duration = "30m"` makes a temporary stage that auto-expires after that much **real** time (counting down even while offline; units `s`/`m`/`h`/`d`, bare number = minutes). Both report `StageCause.REGRESSION`.
- **`[cost]` skill-tree stages** — a `[cost]` table (`xp_levels`, `items = ["minecraft:diamond:5", …]`, `bypass_requirements`) makes a stage **purchasable** from an Unlock button in the in-game tree GUI (`/stage gui`). Purchases are fully server-validated (no double-spend / bypass) and report `StageCause.PURCHASE`. `bypass_requirements = true` skips the `[[triggers]]` (but never the prerequisite stages).
- **New `[[triggers]]` condition types** — `effect` (currently has a status effect), `breed`, `day_count` (reached world day N), `weather` (`rain`/`thunder`/`clear`, one-shot), `enter_structure` (one-shot), `tame`, and `kill_with` (kill `entity` while holding `with`). `tame` / `kill_with` use mod-tracked counters; the rest read vanilla stats or live state.
- **`[unlock]` unlock juice** — optional `toast`, `title` + `subtitle`, `sound`, `particle`, `progress_nudges` (chat hints at 50/75/90%), and `hud_bar` (a blue "progress to next stage" bar above the XP bar). Every field is optional; absent = off. (Note: singular `[unlock]` ≠ plural `[unlocks]` carve-outs.)
- **`[abilities]` gating** — `[abilities].locked = ["elytra"]` blocks elytra gliding until the stage is owned (dropped out of flight each tick). Other movement abilities are better done via `[attribute]` or KubeJS.
- **New `[stage]` metadata** — `hidden = true` (hide from the GUI tree), `color = "#55FF55"` (GUI tint / `&`-code), `category = "…"` (group label), and `scope = "server"` (a **server-wide** stage — the first team to satisfy it unlocks it for everyone; default `"team"`).
- **First-class KubeJS stages** — PS stages work through the normal `player.stages.has(...)` / `.add(...)` / `.remove(...)` API and fire `PlayerEvents.stageAdded` / `stageRemoved`. The Java `StageChangeEvent` (NeoForge bus) now carries `PURCHASE` / `REGRESSION` causes alongside `COMMAND` / `TRIGGER` / etc.

---

## What's new in 2.3

- **Per-stage `[[triggers]]`** — auto-grant triggers now live inside each stage's TOML (the global `triggers.toml` is gone). Each `[[triggers]]` block is one OR-ed rule of `all_of` / `any_of` conditions over kills, mining, crafting, pickups, item use/drop/breaking, distance travelled, raw statistics, play time, level/XP, inventory holdings, advancements, dimensions, and biomes. Counter conditions read vanilla statistics, so they're retroactive and need no extra save files.
- **In-game Stage Tree viewer** — an "Open Progression Tree" keybind (unbound by default) and `/stage gui` open a read-only two-pane screen: a status-coloured stage tree on the left, and the selected stage's description, prerequisite checklist, live `[[triggers]]` % progress, and an icon grid of the items it unlocks on the right.
- **Per-stage `[display]` overrides** — `display_as_unknown_item`, `obscure_icon` (mask the icon with a `?`), `show_tooltip`, and `show_description_on_tooltip` let a stage override the global tooltip/icon defaults for its own locked items.
- **Per-stage `[enforcement]` overrides** — a stage's `[enforcement]` can now override the global enforcement toggles (`block_item_use`, `block_item_pickup`, `block_item_inventory`, `block_block_placement`, `block_block_interaction`, `block_dimension_travel`, `block_entity_attack`, `block_screen_open`, `block_crop_growth`, `block_pet_interact`) for just that stage's gated resources — opt a stage out of (or in to) a category without touching global config. Most-restrictive-wins where several stages gate the same resource.
- **Triggers respect dependencies** — a stage's `[[triggers]]` no longer auto-grant until all of its `dependency` prerequisites are owned; counter progress keeps accruing in the meantime. Omit a stage's `dependency` to let its triggers fire freely.

---

## What's new in 2.0

- **Unified prefix lock model** — `all:*`, `id:`, `mod:`, `tag:`, `name:` (no prefix = `id:`) replace v1's scattered `items` / `item_tags` / `item_mods` arrays. One list, one syntax.
- **Multi-stage gating** — the same item can be locked by multiple stages simultaneously. The player must own **all** gating stages to access it. First-match-wins is gone.
- **Per-stage `[unlocks]` carve-outs** — a stage can lock an entire mod yet exempt specific items via its own `[unlocks]` list. Unlike v1's global per-category whitelist, each stage's carve-outs are scoped to that stage.
- **`minecraft = true` shorthand** — equivalent to `mods = ["minecraft"]`, with parent-stage inheritance: granting a child stage that doesn't itself set `minecraft = true` doesn't leak vanilla content the parent had locked.
- **New lock categories**: enchantments, crops, mob spawns, mob replacements (substitute on spawn), pet taming/breeding/commanding, regions, structures, screens, interactions, curio slots.
- **Vanilla inventory lock icons** — an `IItemDecorator` paints the lock overlay in chests, hotbar, modded backpacks, anvils — every Slot the game renders. EMI's panel still uses the dedicated SlotWidget mixin; JEI's own greying is honored via call-stack detection.
- **Anvil gate** — `AnvilMenuMixin` clears the result slot when input/result is stage-locked, so anvils can't repair, rename, or combine locked items.
- **Visual Workbench compat** — block locks apply through VW-replaced workbenches via reflective resolution to the underlying vanilla block.
- **Reveal-stage-names-only-to-operators** — non-ops see generic lock messages without the stage name (spoiler control); ops always see the full stage.
- **Every player-visible message is a config template** with `&` color codes and named placeholders (`{stage}`, `{type}`, `{count}`, `{player}`, `{progress}`, ...). Pack devs can retheme the entire UX from `progressivestages.toml`.
- **JEI deep hiding** — multi-`FluidStack` ingredient-type enumeration, two-pass refresh (JEI clears the blacklist on add), reflective `IngredientFilter.rebuildItemFilter()`, JEI uid embedded namespace regex, generic ingredient sweep for Mekanism gases/pigments via `Class.getModule().getName()`.
- **EMI hide-by-class** — `removeEmiStacks` predicate covers items, fluids, abstract ids, AND class-module owning mod, catching Mekanism chemicals whose registry id is `mekanism:*` but whose class lives in the mekanism module.
- **FTB Quests deep gating** — the `Quest` / `Chapter` `required_stage` field now also gates `TeamData.canStartTasks`, so progression is blocked even if visibility is overridden by another mod. Defined stages use ProgressiveStages ownership for native quest checks, grants and removals, including FTB team reward and task settings. The optional `integration.ftbquests.team_mode` retains legacy helper reads and removals for undefined stages. An initial import preserves existing defined helper grants in their original team namespace without replaying rewards.
- **Spectator bypass parity** — spectators always bypass locks (matching the existing creative-bypass pathway).
- **`reapply_starting_stages_on_login`** opt-in lets pack devs add a starting stage to an existing world and have all online players pick it up next login.

---

## Installation

1. For 3.1.1, install [NeoForge](https://neoforged.net/) 21.1.248 for Minecraft 1.21.1 on both the server and client.
2. Drop the jar into `mods/`.
3. Optional integrations (auto-detected when their mods are present): EMI, JEI, FTB Quests + FTB Library, FTB Teams, Curios, Lootr, Mekanism, KubeJS, NaturesCompass, Visual Workbench.
4. Launch the game once. ProgressiveStages generates the main file at `config/progressivestages/progressivestages.toml` and fifty schema 4 showcase packages under `config/progressivestages/stages/` when that folder is empty.
5. Run `/pstages editor` as a permission level 3 operator to open the local React stage editor.
   It opens on the stage list. Use the guided stage workspace, movable and zoomable Player UI,
   interactive Player preview, Registry, Settings, and Extensions without editing TOML by hand.
   In Player preview, select a stage to edit its nearby fields or rules. Switch simulated player
   states to see how it will appear. Edits remain in the draft until you review and apply them;
   simulation never changes a player's progress. Direct TOML remains available under Source.
   Reload at runtime with `/pstages reload`.

The editor's package export and import preserve nested TOML includes and helper TOML files,
including comments and quoted keys. Import requires `stage.toml` and safe relative TOML paths.
An invalid package leaves the draft and its undo history unchanged.
Apply also rejects external configuration file changes made since the draft opened, even when
those changes have not been reloaded. The external files and editable draft are preserved.

---

## Quick Start

This compact legacy one-file example is still valid if you create
`config/progressivestages/stages/iron_age.toml` yourself:

```toml
[stage]
id             = "iron_age"
display_name   = "Iron Age"
dependency     = ["stone_age"]
unlock_message = "&6&lIron Age Unlocked!"

[items]
locked = [
    "id:minecraft:iron_pickaxe",
    "mod:create",
    "tag:c:gems/diamond",
    "name:steel"
]
# Per-category whitelist: the Create wrench is exempt from any of the locks above.
always_unlocked = ["id:create:wrench"]

[blocks]
locked = ["id:minecraft:enchanting_table"]

[[blocks.overrides]]
targets = ["tag:c:ores", "mod:immersiveengineering"]
display_as = "minecraft:stone"
drop_as = "minecraft:cobblestone"
priority = 100

[fluids]
locked = ["id:minecraft:lava"]
```

Grant it in-game:

```
/stage grant YourName iron_age
```

The same player can be required to also have `tutorial_done` to use a specific item by listing it under both stages' `[items].locked` — multi-stage gating happens automatically.

---

## Lock Categories

Each category lives in its own TOML section. Lists accept the unified prefix syntax: `all:*`, `id:`, `mod:`, `tag:`, `name:` (no prefix defaults to `id:`).

| Section | Field | Effect |
|---|---|---|
| `[items]` | `locked`, `always_unlocked` | Block use, pickup, holding in inventory/hotbar |
| `[blocks]` | `locked`, `always_unlocked` | Block placement and right-click interaction |
| `[[blocks.overrides]]` | `target` or `targets`, `display_as`, `drop_as`, `priority` | Show matching blocks as another block and change their drops until the stage is owned. Targets accept block IDs, tags, and whole mod namespaces. The older `[[ores.overrides]]` name still works. See [block replacement guide](DOCUMENTATION.md#419-blocksoverrides--block-display-and-drop-overrides). |
| `[fluids]` | `locked`, `always_unlocked` | Pickup/place buckets, hide from EMI/JEI, prevent submersion effects |
| `[dimensions]` | `locked` (exact ids only) | Cancel travel, eject players from locked dimensions |
| `[entities]` | `locked`, `always_unlocked` | Legacy complete presence gate. Cancel spawns when every nearby player is denied. Conceal mixed access entities per player. Block attacking, interaction, mounting, mob targeting, and mob damage for denied players. Schema 4 can instead choose `presence`, `attack`, `interact`, or `mount`. |
| `[recipes]` | `locked_ids`, `locked_items` | `locked_ids` blocks specific recipe IDs; `locked_items` blocks every recipe producing the output item |
| `[enchants]` | `locked`, **`max_levels`**, **`selection_weights`** | Filter table and player-aware loot candidates before they roll, refuse locked anvil and trade results, and sanitize generated loot and inventories. `max_levels = ["minecraft:sharpness:3", ...]` limits generated and retained levels. `selection_weights = ["minecraft:mending:0", ...]` changes exact enchantment roll weights until the stage is owned; weight `0` prevents generation without stripping existing copies. |
| `[crops]` | `locked`, `always_unlocked` | Block planting, growth ticks, bonemeal application |
| `[screens]` | `locked` | Block opening container / GUI blocks |
| `[professions]` | `locked` | **New in 2.5.** Gate opening a villager's trade GUI by the villager's PROFESSION (`id:`/`mod:`/`name:`; no tags). Wandering traders unaffected (use `[trades]`). |
| `[advancements]` | `locked` | **New in 2.5.** Hide locked advancements from the advancements screen entirely (server never tells the client they exist; re-sent when the stage is gained). `id:`/`mod:`/`name:`; no tags. |
| `[loot]` | `locked` | Filter locked items out of loot tables and mob/block drops |
| `[pets]` | `locked_taming`, `locked_breeding`, `locked_commanding` | Block taming, breeding, or commanding specific entity types |
| `[curios]` | `locked_slots` | Block equipping into specific Curios slot ids |
| `[mobs]` | `locked_spawns` | Cancel only when every relevant player inside server simulation distance is denied. Mixed access preserves the mob for allowed players and conceals it from denied players. |
| `[[mobs.replacements]]` | `target`, `replace_with` | Substitute one mob type for another at spawn |
| `[[interactions]]` | `type`, `held_item`, `target_block` / `target_entity`, `description` | Block right-click / item-on-block / item-on-entity combos |
| `[[interactions]]` | `type = "item_into_inventory"`, `held_item`, `target_kind`, `target`, `effect`, `priority` | Gate player item insertion into block, menu, or registered inventory targets. See [inventory insertion rules](docs/features/inventory-insertion/documentation.md). |

Block replacement is separate from placement locks. Use `[blocks].locked` to stop placing or
interacting with selected blocks. Use `[[blocks.overrides]]` to hide selected blocks and change
their drops until a stage is owned. A `mod:` target includes every block in that mod, while a
`tag:` target can select a pack or mod supplied group such as ores. New rules use
`[[blocks.overrides]]`; existing `[[ores.overrides]]` rules remain supported. The old
`[ores].locked` field is not a replacement rule and is rejected with a pointer to the correct
table. See [Block overrides and stage guides](docs/troubleshooting/documentation.md)
for editor steps and examples.

For `item_on_block` and `block_right_click`, selectors are checked against the live item and block holders. Use `tag:c:armors`, `id:selling_bin:selling_bin`, or `all:*`; the legacy `#c:armors` form remains valid. A selective item-on-block rule requires a nonempty held stack. A whole block `all:*` item-on-block rule also gates an empty hand menu open. See the [interaction lock troubleshooting guide](docs/troubleshooting/interaction-locks/documentation.md).

Matching clients suppress local item and block prediction for denied direct interactions using synchronized rules and stage ownership. The server still checks the ordinary request and resends authoritative inventory state when it denies the interaction. Selective restrictions inside an open GUI use a separate `item_into_inventory` rule; `block_right_click` controls access independently. The [contributor testing guide](docs/testing/documentation.md) describes the local checks and runtime boundaries.

Client snapshot acknowledgements must match the revision, checksum and enforcement policy sent
to that player. Repeated acknowledgements reuse the stored offer instead of rebuilding the
snapshot. Disconnect clears the offer. Recovery requests receive one immediate response; repeats
coalesce into one pending response per player every 20 server ticks. A full recovery request takes
precedence over a delta base, and normal server updates remain immediate. Disconnect cancels
pending recovery. See the [contributor testing guide](docs/testing/documentation.md) for local test commands.

Clients enforce the snapshot's declared compressed size as chunks arrive. Duplicate chunks do
not consume the remaining byte budget, and excess data cannot accumulate until the final chunk.

Stage GUI requests, including purchase feedback and public GUI commands, share a response budget
per player. The first view is immediate; repeated requests combine into one current view after
20 server ticks. Matching clients open the screen for an explicit command, keybind or script
`ProgressiveStages.openGui(player)` call. A delayed refresh cannot reopen a screen the player closed. Disconnect cancels pending GUI work.
See the [contributor testing guide](docs/testing/documentation.md) for local test commands.

Stages can optionally integrate with LuckPerms. Add `[luckperms]`, `[[luckperms.inbound]]`, and `[[luckperms.outbound]]` to read inherited groups or true Boolean permissions and to contribute existing groups or positive permissions. `inbound_mode = "synchronized"` removes access after a qualifying rank is lost. `inbound_mode = "permanent"` keeps the attributed stage. Native team changes withdraw the moving player's synchronized contribution from the old owner while preserving independent and permanent grants. Add `[[command_permissions]]` rows to require a stage at an actual literal command path while native command permissions remain required. See the [LuckPerms troubleshooting guide](docs/troubleshooting/luckperms/documentation.md).

Inbound grants track each contributing player and mapping separately. Losing one synchronized
contribution preserves other contributors and independently earned access. Removing a mapping
withdraws its synchronized source from the current owner; permanent grants remain retained.
Reconciliation also removes that player's synchronized contributions from obsolete owners and
deleted definitions before evaluating new grants.

Stage expiry and held duration checks use separate personal, team and server clocks. A personal
profession cannot overwrite a team clock even when their UUIDs match. Legacy team and server
timestamps remain readable without copying them into personal ownership.

Permission grants retain eligibility history after their source is removed. Manual revocation
suppresses the current positive grant, and reloads, provider outages or context changes do not
renew its timer. A confirmed loss and return of the same independent permission conditions, or a
deliberate administrative grant, can start a new grant. Timed permanent grants still expire.
Administrative grants record independent ownership even when a rank already provides access. Bulk,
tag and category revokes also suppress existing positive eligibility while its source is unavailable.
Run provider transition checks separately from the core permission tests and confirm the result with
an authenticated player before relying on a provider integration.

After loading saved data, synchronized grants wait for authoritative revalidation. Independent and
permanent grants remain available. Pending synchronized grants stay stored so they can be rechecked
or explicitly revoked; they do not satisfy access checks while pending.

Outbound privileges use a reserved provider context that becomes active only after their current
node changes are confirmed. Dirty state, reload, disconnect and shutdown invalidate that context
before cleanup or reevaluation. Reload work shares the bounded reconciliation queue.

Provider user, node, group, configuration reload and synchronization events now schedule the same
bounded reconciliation work. Callbacks invalidate projection state without querying stages or
loading users. Shutdown disables callbacks before removing their subscriptions and retries failed
listener cleanup.

Offline contributors now share the reconciliation queue. Up to eight provider loads can be pending,
and offline checks use fixed server contexts instead of a remembered world. Current ownership,
definitions and provider observations are checked before applying results. Synchronized sources
wait inactive for unavailable data; permanent and independent grants remain retained.

Command gates now check Minecraft's command execution tasks after redirects resolve. Registered
node and execution bindings identify aliases; namespace text alone does not establish equivalence.
Test command-node and execution bindings separately; namespace text alone does not establish that
two registered commands are equivalent.

Actor context mutations reject stale membership and definition revisions before changing stages.
Integrations must resolve a fresh context on the server thread after membership or session changes.

Easy Builder keeps generic rule actions separate, preserves additional targets and conditions
when a rule is edited, and lets permanent conditional rules follow explicit stage ownership.
Legacy lists keep their original enforced format. The [contributor testing guide](docs/testing/documentation.md)
describes how to run the local editor and server checks.

Easy Builder applies only the reviewed draft revision. If another edit changes the draft, it
reloads the current source and asks for a new review before applying.
Removing a draft collaborator revokes their existing sessions. Adding them again requires
a fresh session before they can read, review, or apply that draft.

Diagnostic capture status shows the category, remaining limits, and writer completion.
Capture headers identify the candidate archives and captured effective configuration.
`/stage debug editor` also records authenticated draft operations and apply outcomes.
See the [capture procedure](docs/troubleshooting/interaction-locks/documentation.md) before collecting support logs.

LuckPerms 5.4.150 is the tested provider version on NeoForge 21.1.248. Published stage permissions
follow the affected player, and native LuckPerms context notifications recheck conditional access.
Confirm provider login and multiplayer behavior with the pack's installed versions.
| `[[regions]]` | `dimension`, `pos1`, `pos2`, `prevent_entry`, `prevent_explosions`, ... | 3D bounding-box gates |
| `[structures]` | `locked_entry` + `[structures.rules]` (`entry_allowed`, `priority`, `prevent_block_break`, `prevent_block_place`, `prevent_explosions`, `disable_mob_spawning`, **`entry_padding`**) | Attribute entry and protection independently for exact generated structures. `entry_allowed = true` removes only that stage's entry denial, so a protection stage can still deny placement after another stage unlocks entry. |
| `[enforcement]` | `allowed_use`, `allowed_pickup`, `allowed_hotbar`, `allowed_mouse_pickup`, `allowed_inventory` | Per-stage exception lists for in-inventory enforcement |
| `[enforcement]` (overrides) | `block_item_use`, `block_item_pickup`, `block_item_inventory`, `block_block_placement`, `block_block_interaction`, `block_dimension_travel`, `block_entity_attack`, `block_screen_open`, `block_crop_growth`, `block_pet_interact` | **New in 2.3.** Per-stage override of the same-named global enforcement toggle, applied only to this stage's gated resources (omit = inherit global). Most-restrictive-wins across multi-stage gates. |
| `[abilities]` | `locked` | **New in 2.4.** Block movement/action abilities (e.g. `["elytra"]` — no gliding) until the stage is owned |
| `[[attribute]]` | `id`, `operation`, `amount` | **New in 2.4.** *Reward* — grant attribute modifiers (any vanilla/modded attribute; `add`/`multiply_base`/`multiply_total`) while the team **owns** the stage |
| `[revoke]` | `on_death`, `xp_below`, `cascade` | **New in 2.4.** Regression — lose the stage on death, while total XP < N, and optionally cascade the revoke to dependents |
| `[cost]` | `xp_levels`, `items`, `bypass_requirements`, **`cooldown`/`cooldown_seconds`**, **`refund_percent`** | **New in 2.4** (`cooldown`/`refund_percent` **New in 3.0**). Make the stage **purchasable** from the in-game tree GUI (Unlock button); server-validated. `cooldown` = per-player rate limit between purchases (accepts `"5m"`); `refund_percent` = % of item/XP cost returned when a purchased stage is revoked |
| `[rewards]` | `items`, `effects`, `commands`/`command`, `teleport`, `xp_levels`, `xp_points` | **New in 3.0.** *Reward* — items / status effects (`id:seconds:amplifier`) / commands (run as the player, `{player}` substituted) / teleport (`"[dim] x y z"`) / xp handed out **once** the moment the stage is granted (companion to `[cost]`) |
| `[unlock]` | `toast`, `title`/`subtitle`, `sound`, `particle`, `progress_nudges`, `hud_bar` | **New in 2.4.** Unlock "juice" — toast/title/sound/particle on unlock, progress hints, and a blue progress bar above the XP bar (all optional) |
| `[abilities]` (3.0) | `locked` += `sprint`, `swim`, `climb` | **New in 3.0.** `[abilities].locked` now also gates `sprint` / `swim` / `climb` (upward motion on ladders/vines clamped) in addition to `elytra` (`crawl` on land isn't vanilla-enforceable) |
| `[display]` (3.0) | `encrypt_blocks`, `encrypt_as` | **New in 3.0.** `encrypt_blocks = true` masquerades this stage's exact-id locked **blocks** as `encrypt_as` (default `minecraft:stone`) until owned, via the ore-spoof pipeline. Per-stage on/off |
| `[beacon]` | `locked` | **New in 3.0.** `locked = ["id:minecraft:strength", ...]` (MobEffect ids; `id:`/`mod:`/`name:`). A player missing the gating stage doesn't receive that beacon effect; other players in range are unaffected |
| `[brewing]` | `locked` | **New in 3.0.** `locked = ["id:minecraft:strength", ...]` (Potion ids; `id:`/`mod:`/`name:`). A player missing the gating stage can't TAKE the brewed potion out of a brewing stand's slots; hopper/automation extraction is gated too (best-effort, nearest player) |
| `[stage]` metadata | `hidden`, `color`, `category`, `scope`, `duration`, **`tags`** | **New in 2.4** (`hidden`/`color`/`category` GUI behaviour **activated in 2.5**; `tags` **New in 3.0**). Hide from the GUI tree, tint the stage name (`#RRGGBB`), group label/tag, `scope = "server"` (server-wide stage), temporary `duration` (auto-expires after real time), and `tags = ["combat","tier2"]` (labels for `/stage tag ...` bulk ops — no gating effect) |
| (root) | `minecraft = true` | Shorthand: equivalent to `mods = ["minecraft"]` across items/blocks/fluids/entities |

Two whitelist mechanisms exist:

- **`always_unlocked`** inside a category — exempts specific entries from THIS stage's locks within THAT category. Simple and local.
- **`[unlocks]` table** — per-stage carve-outs that subtract this stage from the multi-stage gating set for items / mods / fluids / dimensions / entities. Use this when a stage owns a broad lock (e.g., `mod:create`) and you want a specific carve-out that survives even if other stages also gate the same resource.

---

## Multi-Stage Gating

When two or more stages list the same resource under their category section, the player must own **all** gating stages to access it.

```toml
# stage_A.toml
[stage]
id = "stage_A"

[items]
locked = ["id:minecraft:diamond"]
```

```toml
# stage_B.toml
[stage]
id = "stage_B"

[items]
locked = ["id:minecraft:diamond"]
```

A player with only `stage_A` still can't use diamond — they need both `stage_A` and `stage_B`. This lets pack devs split a single resource between two progression branches (e.g. "you must clear the questline AND defeat the boss").

The single-stage accessor `getRequiredStage(Item)` still exists for callers that don't need the full set; it returns the first gating stage in canonical order. Internally, all enforcers use the multi-stage `isItemBlockedFor` predicate which AND-checks every gating stage against the player's owned set.

---

## Per-Stage `[unlocks]`

Each stage can carve out scoped exceptions from its own locks:

```toml
[stage]
id = "industrial_age"

[items]
locked = ["mod:create", "mod:mekanism"]   # all Create and Mekanism items

# Per-stage carve-outs (scoped — only removes this stage from the gating set):
[unlocks]
items = ["id:create:wrench"]              # Create wrench is allowed
mods  = ["mekanism_tools"]                # Mekanism Tools is allowed even though mekanism is locked
```

The `[unlocks]` table mirrors the lock side and accepts these fields: `items`, `mods`, `fluids`, `dimensions`, `entities`. Each entry uses bare `namespace:path` (or for `mods`, a bare modid) — the `id:` and `mod:` prefixes are accepted but optional in this table.

Carve-outs are scoped to the declaring stage: `[unlocks]` on stage A removes stage A from the gating set for those resources. If stage B independently locks the same resource without its own `[unlocks]` entry, stage B still gates.

For simpler whitelists scoped to a single category, use `always_unlocked` inside that category section instead.

---

## `minecraft = true` Shorthand

Set as a root-level key on the stage file:

```toml
[stage]
id = "civilized"

# Root-level shorthand: gates the entire minecraft: namespace.
minecraft = true
```

This registers a synthetic `mod:minecraft` entry into items, blocks, fluids, and entities for this stage — equivalent to listing `"mod:minecraft"` under each of `[items].locked`, `[blocks].locked`, etc. Useful for "no vanilla anything until you've earned this stage" packs.

**Parent-stage inheritance**: if `civilized` has `minecraft = true` and `iron_age` depends on `civilized` without setting `minecraft = true`, owning `iron_age` does **not** unlock `civilized`'s vanilla locks — the player must explicitly own `civilized` for vanilla content to be reachable. Without this, granting a child stage would silently leak vanilla content the parent had locked.

---

## Automatic Triggers (`[[triggers]]`)

A stage grants **itself** when a player meets its triggers. Declare them per-stage with `[[triggers]]` blocks (the old global `triggers.toml` is gone). Each block is one **rule**; rules are **OR-ed** (any complete rule unlocks the stage). Inside a rule, one or more `[[triggers.conditions]]` combine via `mode = "all_of"` (default) or `mode = "any_of"`; a single-condition rule can put the condition fields straight on the `[[triggers]]` table.

```toml
# Grant when the player mines 10 diamond ore (single-condition shorthand)
[[triggers]]
type  = "mine"
block = "minecraft:diamond_ore"
count = 10

# ...or kills 10 endermen AND the ender dragon (a second, OR-ed rule)
[[triggers]]
mode = "all_of"

  [[triggers.conditions]]
  type   = "kill"
  entity = "minecraft:enderman"
  count  = 10

  [[triggers.conditions]]
  type   = "kill"
  entity = "minecraft:ender_dragon"
```

Condition types: `kill`, `mine`, `craft`, `pickup`, `use`, `drop`, `break_item`, `distance` (blocks travelled, by movement kind or `all`), `stat` (any vanilla custom statistic), `play_time` (minutes), `level`, `xp`, `has_item`, `advancement`, `dimension`, `biome`, and **new in 2.4** `effect` (currently has a status effect), `breed`, `day_count` (reached world day N), `weather` (`rain`/`thunder`/`clear`, one-shot), `enter_structure` (one-shot), `tame`, `kill_with` (kill an entity while holding a given item), **new in 2.5** `world_time` (time-of-day tick `0..23999`, e.g. at night) and `script` (a custom KubeJS-registered predicate, referenced by `type="script", id="<conditionId>"`), and **new in 3.0** `reach_y` (state — while current Y ≥ `count`), `fish` / `sleep` / `ride` (vanilla-stat counters — fish caught, beds slept in, blocks ridden on any vehicle — all **retroactive**), `biome_time` (seconds in a target biome/`#tag`; `count` seconds or `duration = "5m"`), and `stage_held_for` (held another stage for ≥ `count` seconds; `count` seconds or `duration = "3d"`). Subjects accept `#tag`/`tag:` form; `count` defaults to 1. (`tame`/`kill_with`/`biome_time` use mod-tracked counters rather than vanilla stats.) **New in 2.5:** `breed` takes an optional species/tag target (no target = all bred animals, retroactive; with a target = that species/tag, event-counted), and `kill_with` accepts a `#tag` victim (summed over the tag's members). The vanilla-stat counter types read Minecraft's statistics, so they're **retroactive** and survive restarts with no extra save files; `dimension`/`biome` "visited" one-shots are persisted per player (reset with `/progressivestages trigger reset <player> <stage>`). Progress is per-player and the first team member to satisfy a rule unlocks the stage for the whole team. Poll cadence is `enforcement.trigger_poll_interval` ticks (relevant events also force an immediate re-check).

**Triggers respect dependencies:** a stage is not auto-granted by its triggers until every `[stage].dependency` prerequisite is owned. Counters keep accruing while a prerequisite is missing, so the next poll after the last prerequisite is granted completes the unlock. Omit a stage's `dependency` to let its triggers fire freely, regardless of progression.

---

## Stage Tree Viewer & Per-Stage `[display]`

Players can open the **vanilla style progression map** with `/stage`, `/stages`, `/pstages`, `/stage gui`, the "Open Progression Tree" keybind, or the progression map button beside the recipe book button in the survival inventory. Its custom 20 by 18 pixel background follows the recipe-book button silhouette instead of using the generic dark widget. Separate light silver and pale blue hover sprites provide rounded corners, a dark pixel outline, and a bottom shadow. A centered gold folded map with a cyan route and three stage markers distinguishes the action. The `[client]` config can hide the button or customize its inventory relative X position, Y position, width, height, and centered icon size. Drag from empty space or a stage node to pan. Roll the mouse wheel over the map to zoom toward the pointer, from 65 percent through 165 percent. Stage icons stay at a readable size while their positions and connector paths zoom. WASD and the arrow keys pan by the same visible distance at every zoom level. Press Space or use the header home button to center and fit the complete graph. Hover or select a node to emphasize its ancestors and descendants while unrelated branches recede. Available stages use a restrained gold corner pulse, selected stages use a pixel rounded frame, and the map controls use vanilla widget surfaces. Click without dragging to pin prerequisites, clearly grouped live trigger routes, unlock previews, and any purchase button. Search matches stage text and locked item ids. The Owned control filters completed stages.

Each stage can override the global tooltip/icon defaults for its own locked items with a `[display]` block — all keys optional, inheriting the global default when omitted:

```toml
[display]
display_as_unknown_item     = true    # mask the item NAME as "Unknown Item"
obscure_icon                = true    # also replace the ICON with a "?" placeholder
show_tooltip                = true    # show the lock / required-stage tooltip lines
show_description_on_tooltip = true    # append [stage].description to the tooltip
x = 168                               # optional explicit map coordinates; omit both for auto-layout
y = 0
frame = "challenge"                   # task | goal | challenge
background = "minecraft:block/deepslate_tiles"
reveal = "dependencies"               # always | dependencies | unlocked
sort_order = 20                        # ordering hint for automatic layout
```

`background` accepts any client texture resource. For a custom file at
`assets/mypack/textures/gui/progression.png`, use `background = "mypack:gui/progression"`.

---

## Mod Compatibility

| Mod | Integration |
|---|---|
| **EMI** | **Optional** (`type = "optional"`, mixins `required: false`). Items + fluids + abstract ingredients hidden via `removeEmiStacks` predicate; class-module fallback for Mekanism gases/pigments; re-shown on unlock. **3.0:** the recipe-viewer reload no longer crashes when EMI is absent (a `NoClassDefFoundError` class-load guard was fixed) |
| **JEI** | **Optional** (`type = "optional"`, no hard dependency). Items + fluids (every `FluidStack` ingredient type, not just `NeoForgeTypes.FLUID_STACK`) hidden; two-pass refresh; reflective ingredient-filter rebuild; uid namespace regex scan; live `IIngredientListener`; re-shown on unlock |
| **FTB Quests** | `Quest` / `Chapter` `required_stage` field gates both `isVisible` and `canStartTasks`; required-stage entry in the editor config UI |
| **FTB Library** | `StageProvider` proxy registration and native quest storage hooks preserve each defined stage's personal, team or server owner. FTB reward distribution settings remain separate |
| **FTB Teams** | Default backend when `team_mode = "ftb_teams"`; defined quest stages use the same owner backend as other progression; optional `integration.ftbquests.team_mode` retains legacy helper reads and removals for undefined stages |
| **Curios** | Optional Curios 9.5.1 public-API bridge. Per-slot stage locks via `[curios].locked_slots`; the configured inventory sweep safely removes stale locked curio contents. |
| **Lootr** | `ILootrFilterProvider` filters stage-locked loot from per-player chest snapshots |
| **Mekanism** | Entity-join + block-break hooks honor stage gates; gases/pigments hidden in EMI via class-module detection |
| **KubeJS** | **New in 2.5:** a global `ProgressiveStages` object with `onGranted`/`onRevoked` callbacks (fire on every engine grant/revoke), `condition('id', player => bool)` for custom `script:` trigger conditions, and `has`/`grant`/`revoke`/`list`/`percent` helpers. Also first-class `player.stages.has/add/remove(...)`; Java `StageChangeEvent` (NeoForge bus) carries the `StageCause`. (KubeJS 7.x has no native stage events on engine grants — use `onGranted`/`onRevoked`.) |
| **NaturesCompass** | Filters dimension/structure search results so locked dimensions don't appear |
| **Visual Workbench** | Reflective shim — locks targeting `minecraft:crafting_table` apply through VW-replaced workbenches |
| **Jade / WTHIT** | **New in 3.0:** looking at a locked **block or mob** appends a red `🔒 Requires: <stage>` line to the overlay (blocks via the item-lock cache, entities via a new client entity-lock sync; both honor creative bypass). Sourced from the **Modrinth Maven** as `compileOnly` dev jars, inert when neither mod is installed |

All integrations are reflection-loaded; absent mods are silently skipped. Each can be disabled via the `[integration]` config section.

---

## Commands

| Command | Permission | Description |
|---|---|---|
| `/stage grant <player> <stage>` | OP | Grant a stage |
| `/stage revoke <player> <stage>` | OP | Revoke a stage |
| `/stage`, `/stages`, `/pstages`, `/stage gui` | Player | Open the vanilla-style progression map |
| `/stage list [player]` | Player | Show a player's owned stages |
| `/stage check <player> <stage>` | Player | Check if a player has a stage |
| `/stage info <stage>` | Player | Print a stage's full definition |
| `/stage tree` | Player | Print the stage dependency tree |
| `/stage progress [next\|all\|<stage>] [player]` | Player | Show live `[[triggers]]` rule/condition progress toward stages |
| `/stage counter get\|add\|set\|reset ...` | OP | Read or mutate named counters used by `custom_counter` triggers |
| `/pstages rule info <rule>` | Player | Inspect a conditional rule, its owner, effect, activation, priority, trigger, duration, targets, and exceptions |
| `/pstages rule list [player]` | Player or OP for another player | List active timed rules and their remaining seconds |
| `/pstages rule activate <player> <rule> [seconds]` | OP | Start a triggered rule with its configured duration or an explicit duration |
| `/pstages rule clear <player> <rule>` | OP | Stop one active triggered rule |
| `/pstages rule clearall <player>` | OP | Stop every active triggered rule for the player |
| `/stage tag grant\|revoke <players> <tag>` | OP | **New in 3.0.** Grant/revoke every stage tagged `<tag>` (from `[stage].tags`) to the selected players. Grant bypasses dependencies and skips already-owned stages |
| `/stage tag list <tag>` | OP | **New in 3.0.** List every stage that declares `<tag>` |
| `/stage category grant\|revoke <players> <category>` | OP | Bulk-change every stage in a GUI category (quote names containing spaces) |
| `/stage category list <category>` | OP | List stages in a category |
| `/stage bulk grant\|revoke <players>` | OP | Grant or revoke the complete stage set |
| `/stage sync <players>` | OP | Force a full definitions/locks/ownership client refresh |
| `/stage simulate [player]` | OP | **New in 3.0.** Dry-run: reachable-next stages with % + which conditions are still short, then dependency-blocked stages and what they're missing |
| `/stage new <id>` | OP | **New in 3.0.** Scaffold a commented stage TOML at `config/progressivestages/stages/<id>.toml` (won't overwrite) |
| `/stage export` | OP | **New in 3.0.** Write a markdown progression guide (`progressivestages_guide.md`) built from the stage graph to the config folder |
| `/progressivestages validate` | Admin | Validate every stage TOML file (registry existence, dependency cycles, malformed entries). **Deepened in 2.5:** full multi-node dependency cycles, transitively-unreachable stages, dead trigger targets (unresolved exact-id entity/block/item/effect or `kill_with` item), and profession ids. |
| `/progressivestages reload` | Admin | Reload stage configs (incl. each stage's `[[triggers]]`) from disk |
| `/progressivestages triggers list [player]` | OP | List every stage that declares `[[triggers]]`; with a player, show their live per-condition progress |
| `/progressivestages trigger reset <player> <stage>` | OP | Clear a stage's persisted one-shot (dimension / biome visited) progress |
| `/progressivestages ftb status [player]` | Admin | Dump FTB Quests integration status (provider registered, team mode, recheck queue) |

Every line of every command's output is a configurable template via `messages.cmd_*` keys in `progressivestages.toml`. `&` color codes and `{placeholder}` substitution work everywhere.

---

## Configuration

`config/progressivestages/progressivestages.toml` — high-level groups. Stage files live beside it in `config/progressivestages/stages/`:

- **`[general]`** — `starting_stages`, `team_mode` (`ftb_teams` / `solo`), `linear_progression`, `reapply_starting_stages_on_login`, `debug_logging`, and `enable_stage_guide`.
- **`[client]`** — `show_inventory_button`, `inventory_button_x`, `inventory_button_y`, `inventory_button_width`, `inventory_button_height`, and `inventory_button_icon_size`. Hide, move, or resize the survival inventory progression button without affecting commands or the optional keybind. The default X position is `132`, leaving a visible gap after the recipe-book button.
- **`[enforcement]`** — every per-category enforcement toggle (`block_item_use`, `block_block_placement`, `block_dimension_travel`, `block_enchants`, `block_crop_growth`, `block_pet_interact`, `block_loot_drops`, `block_mob_spawns`, `block_mob_replacements`, `block_region_entry`, `block_structure_entry`, `block_screen_open`, ...), plus `allow_creative_bypass`, `mask_locked_item_names`, `obscure_locked_item_icons` (new — replace a locked item's icon with a `?`), `trigger_poll_interval` (new — `[[triggers]]` poll cadence in ticks), `notification_cooldown`, `reveal_stage_names_only_to_operators`, lock-sound config, eject-blocked-inventory frequency.
- **`[messages]`** — every player-facing string. Supports `&` color codes (`&0`–`&f`, `&l/m/n/o/k/r`) and named placeholders. Generic `*_generic` variants are emitted when `reveal_stage_names_only_to_operators = true` and the player is non-op. Includes the `messages.prefix` template, `messages.tooltip_*` (now including `tooltip_stage_description`), `messages.cmd_*`, `messages.type_label_*`, and `messages.cmd_ftb_status_*` families.
- **`[jei]` and `[emi]`** — independent `enabled` controls for each optional viewer. `[emi]` also keeps `show_lock_icon`, `lock_icon_position`, `lock_icon_size`, `show_highlight`, `highlight_color`, `show_tooltip`, `show_stage_description_on_tooltip` (new — append the gating stage's description to a locked item's tooltip), and `show_locked_recipes` for compatibility.
- **`[integration.ftbquests]`** — `enabled`, `team_mode` (retains legacy helper reads and removals for undefined stages), `recheck_budget_per_tick`.
- **`[integration.ftbteams]`** — `enabled`.
- **`[performance]`** — `enable_lock_cache`, `lock_cache_size`.

The full list of keys is generated into `progressivestages.toml` on first launch with descriptive comments for each.

---

## Privacy & Disclosure

By default, `enforcement.reveal_stage_names_only_to_operators = true`. Non-operators (permission level &lt; 2) see generic lock messages — `🔒 You haven't unlocked this item yet!` instead of `Required: Iron Age`. Operators always see the stage name in chat and tooltips. The flag is mirrored to the client via a dedicated payload so client-side tooltip rendering also respects it.

Set the flag to `false` in `progressivestages.toml` to expose stage names to everyone.

---

## Architecture (high level)

```
common/
  config/StageConfig          — every config key + cached getters + ConfigValue<String> message templates
  lock/PrefixEntry            — the "id:foo" / "mod:bar" / "tag:c:gems" / "name:diamond" entry type
  lock/CategoryLocks          — category scaffolding (items / blocks / fluids / ...)
  lock/LockRegistry           — central registry; multi-stage gating; per-stage [unlocks]; minecraft=true expansion
  network/NetworkHandler      — payloads (StageSync, LockSync multi-row, RevealPolicy, CreativeBypass, StageDefinitionsSync)
  stage/StageManager          — grant / revoke, dependency walks, FTB Quests recheck triggers
  util/StageDisclosure        — chokepoint for op-vs-non-op stage-name reveal
  util/TextUtil               — & color code parser used by every message template

server/
  enforcement/*Enforcer       — one per category, all multi-stage; spectator + creative bypass
  loader/StageFileParser      — parses the per-category sections + [unlocks] + minecraft=true schema
  loader/DefaultShowcaseStages — first-launch fifty stage schema 4 class tree
  loader/DefaultStageTemplates — legacy one-file documentation reference
  ServerEventHandler          — wires every NeoForge event (block-place, item-pickup, dim-travel, mob-spawn, ...)
  triggers/                   — advancement / item-pickup / boss-kill / dimension grants

client/
  jei/ProgressiveStagesJEIPlugin   — multi-FluidStack types, 2-pass refresh, filter rebuild, uid regex, IIngredientListener
  emi/ProgressiveStagesEMIPlugin   — removeEmiStacks predicate covering items + fluids + class-module
  renderer/LockedItemDecorator     — IItemDecorator for vanilla inventories
  renderer/LockIconRenderer        — z=1000 lock icon + z=199 highlight
  ClientLockCache + ClientStageCache — multi-stage maps + reveal-policy mirror
  ClientEventHandler               — tooltip emission with multi-stage stage-list
  ClientModBusEvents               — RegisterItemDecorationsEvent subscription on the mod-event bus

mixin/
  AbstractContainerMenuMixin       — multi-stage carried-item gate
  AnvilMenuMixin                   — clears result slot when locked
  CraftingMenuMixin                — recipe + recipe-item gate
  ResultSlotMixin                  — pickup gate
  EnchantmentMenuMixin             — filters table candidates for the exact opener
  EnchantWithLevelsFunctionMixin   — filters table-style loot enchantment rolls
  EnchantRandomlyFunctionMixin     — filters uniformly random loot enchantments
  ServerPlayerMerchantMixin        — gates villager trades
  client/EmiStackWidgetMixin       — paints lock in EMI panel
  client/EmiScreenManagerMixin     — EMI search / sidebar
  ftbquests/QuestMixin             — required_stage field + isVisible
  ftbquests/ChapterMixin           — required_stage field + isVisible
  ftbquests/TeamDataMixin          — canStartTasks gate

compat/
  curios/                — CurioCanEquipEvent gate
  ftbquests/             — StageProvider Proxy + RequiredStageHolder + ftbquests_team_mode
  kubejs/                — script bindings
  lootr/                 — ILootrFilterProvider implementation (registered via META-INF/services)
  mekanism/              — entity + block hooks
  naturescompass/        — search filter
  recipeviewer/          — RecipeViewerModHints (Class.getModule().getName())
  visualworkbench/       — reflective vanilla-equivalent resolution
```

---

## Changelog

### v3.0.3
- **Stage aware enchantment generation**. Enchanting table choices and player aware enchanted loot
  now honor per stage maximum levels and selection weights, with the existing strip retained as a
  safety net.
- **Easy builder enchantment controls**. Pack authors can search the live enchantment registry and
  edit maximum levels or roll weights without opening TOML.
- **Progression map polish**. The compact map now has clearer inspector content, translated labels,
  selector icons, cycling tag previews, focused branch highlighting, and a parchment map inventory
  button styled after vanilla controls.
- **Progression category layering**. The open category list now uses a dedicated foreground depth
  so stage item icons cannot appear above its panel, labels, active marker, or scrollbar.
- **Asynchronous structure safety**. Structure aware entity decisions use immutable session data
  during chunk generation instead of reading server thread only state.
- **Smithing recipe enforcement**. Smithing upgrades now use the same recipe id, recipe output,
  direct item, ingredient, creative bypass, and notification policies as normal crafting.
- **Player layout toolbar cleanup**. The redundant `Arrange and save` action was removed without
  changing manual drag persistence, automatic layout reset, graph fitting, or review and apply.

### v3.0.2
- **Integrated-server editor repair** — authenticated localhost editor requests now work for
  permission level 3 operators in single-player worlds while remote hosts, origins, ports, and
  incorrect session secrets remain rejected.
- **Focused editor design** — a restrained dark gray and gold stage-first workspace keeps the easy
  builder, player layout, TOML source, review, and advanced tools available without crowding the
  normal stage workflow.
- **Inventory button controls** — the progression map button can be hidden, moved relative to the
  survival inventory, resized, and given a custom centered icon size. Commands and the optional
  keybind remain available when the button is hidden.

### v3.0.1
- **Generic exact-structure session API** — one-way provider registration for assignment and dungeon mods, immutable instance/bounds/session contracts, three-way `PASS`/`PERMIT`/`DENY` arbitration, cached fail-closed claims, and no hard dependency on companion mods.
- **Committed structure lifecycle** — debounced enter/leave state, idempotent completion, typed death/teleport/dimension/disconnect/recovery outcomes, post-commit NeoForge events, login reconciliation, and team-safe temporary stage leases.
- **Contextual active item locks** — `[active_locks] scope = "structure_session"` blocks selected item use only while the leased in-progress stage is present inside its matching exact session. Inventory, pickup, crafting, loot, tooltips, JEI, and EMI remain untouched.
- **Event-driven structure exits** — `leave_structure` and its aliases support exact IDs or tags, provider filters, required session stages, and typed outcome filters without permanently consuming future visits.
- **Structure diagnostics** — `/pstages structure providers`, `sessions`, `reconcile`, and confirmation-gated `close`, plus a complete provider and forty-step acceptance guide.
- **Conditional access engine** — live location/state rules and event-driven timers can lock or unlock eight target families with explicit priority, safe tie behavior, per-rule exceptions, stage ownership modes, combat entity filters, `/pstages rule` operations, KubeJS bindings, Java APIs, validation, and worked Stronghold/Wither/End examples.
- **Conflict-free map command** — `/pstages` is the dedicated player-facing map command, leaving the previously used short command available to other mods.
- **Nineteen-phase pack-building guide** — a copy-ready path now covers installation, every lock and trigger family, the vanilla-style map, commands, KubeJS, Java integration, optional mods, migration, testing, and release evidence.
- **Progression-map polish** — menu blur stays below the interface, map node icons no longer overlap the pinned inspector, trigger routes are labeled clearly, dragging may begin on nodes or empty space, and a progression map button beside the survival inventory recipe-book button opens the map.
- **Custom backgrounds documented and tested** — `[display].background` accepts any namespaced client texture such as `mypack:gui/progression`.
- **Stable camera during live progression changes** — stage grants, revokes, and game-mode changes now refresh ore disguises with targeted block updates instead of unloading the chunk beneath the player. Unchanged scale attribute modifiers are also preserved during stage synchronization.

### v3.0
- **`[rewards]` on grant** — companion to `[cost]`: `items`, `effects` (`id:seconds:amplifier`), `commands`/`command` (run as the player at permission 2, `{player}` substituted), `teleport` (`"[dim] x y z"`), `xp_levels`, `xp_points`. Fires once per real grant (not on login/sync), for every cause.
- **Six new `[[triggers]]` conditions** — `reach_y` (state: Y ≥ `count`), `fish` / `sleep` / `ride` (vanilla-stat counters, retroactive), `biome_time` (seconds in a biome/`#tag`; `count` seconds or `duration = "5m"`), `stage_held_for` (held a stage ≥ `count` seconds; `count` seconds or `duration = "3d"` — grant times now recorded for every stage).
- **Stage tags + bulk ops** — `[stage].tags = ["combat","tier2"]` + `/stage tag grant|revoke <players> <tag>` and `/stage tag list <tag>`. Grant bypasses dependencies and skips already-owned stages.
- **`[cost]` cooldown + refund** — `cooldown` / `cooldown_seconds` (per-player rate limit between purchases; accepts `"5m"`) and `refund_percent` (% of item/XP cost returned when a purchased stage is revoked).
- **Ability gating expanded** — `[abilities].locked` now also gates `sprint`, `swim`, and `climb` (clamps upward motion on ladders/vines), in addition to `elytra`.
- **Jade + WTHIT overlay** — looking at a locked block or mob shows `🔒 Requires: <stage>`. Sourced from the Modrinth Maven as `compileOnly` dev jars, inert when uninstalled. Entity locks are now synced to the client for the mob overlay.
- **`[display].encrypt_blocks`** — masquerades this stage's exact-id locked blocks as `encrypt_as` (default `minecraft:stone`) until owned, reusing the ore-spoof pipeline. Per-stage on/off.
- **Authoring/debug commands** — `/stage simulate [player]` (dry-run reachable-next stages + short conditions + dep-blocked stages), `/stage new <id>` (scaffold a stage TOML), `/stage export` (markdown progression guide).
- **Stage-aware enchantment generation** — `[enchants].locked` now removes every locked candidate before enchanting-table and player-aware loot rolls. `max_levels = ["minecraft:sharpness:3", ...]` limits both newly generated and retained levels. `selection_weights = ["minecraft:mending:0", "minecraft:fortune:10"]` changes exact enchantment roll weights while the declaring stage is missing; weight `0` disables new generation without deleting existing copies. Multiple missing-stage caps and weights use the strictest value. Immediate loot sanitization and the periodic inventory scan remain defense in depth.
- **`[beacon].locked` beacon-effect gating** — `["id:minecraft:strength", ...]` (MobEffect ids). A player missing the gating stage doesn't receive that beacon effect; other players in range are unaffected.
- **`[brewing].locked` brewed-potion gating** — `["id:minecraft:strength", ...]` (Potion ids). A player missing the gating stage can't take the brewed potion out of a brewing stand's slots; hopper/automation extraction is gated too (best-effort, via the nearest player).
- **Stage Tree GUI search + hide** — a search box filters by stage name OR by a locked item id (type an item to find the stage(s) that gate it; flat results), an "☑ owned" toggle hides already-unlocked stages, and the detail pane shows a "Gates mods: create ×42, mekanism ×18" breakdown.
- **EMI + JEI both optional (reload-crash fix)** — locked items/recipes hidden and re-shown on unlock in EMI and JEI; both `type = "optional"` (EMI mixins `required: false`); the recipe-viewer reload no longer crashes when EMI is absent (a `NoClassDefFoundError` class-load guard was fixed).

### v2.5
- **`[professions]` lock category** — gate opening a villager's trade GUI by the villager's PROFESSION (`id:`/`mod:`/`name:`; no tags). Wandering traders unaffected (use `[trades]`). Opt-in (no overhead when unused).
- **`[advancements]` lock category** — hide locked advancements from the advancements screen entirely (stripped from the update packet server-side; the client never learns they exist; full re-send when the stage is gained). `id:`/`mod:`/`name:` matching.
- **Structure gating enhancements** — `entry_padding` (block buffer; accepted under `[structures]` or `[structures.rules]`) and last-safe-position teleport: a breaching player is sent back to where they last stood outside any locked structure.
- **New `[[triggers]]` conditions** — `world_time` (time-of-day tick `0..23999`; aliases `time_of_day`/`daytime`/`clock`); `breed` gains an optional species/tag target (no target = all/retroactive; target = event-counted); `kill_with` accepts a `#tag` victim; `script` (`js`/`kubejs`/`custom`) — a custom KubeJS-registered predicate via `type="script", id="<id>"`.
- **Datapack-loaded stages** — `data/<namespace>/progressivestages/stages/*.toml` load at world load and on `/reload` and merge with config-folder stages; a same-id config file always wins (datapacks = overridable defaults).
- **`[stage]` hidden/color/category now live in the GUI** — previously inert: `hidden` omits the stage from the tree, `color = "#RRGGBB"` tints its name, `category` shows as a tag in the detail header.
- **Deep KubeJS integration** — global `ProgressiveStages` object: `onGranted`/`onRevoked` (fire on every engine grant/revoke), `condition('id', player => bool)` for `script:` conditions, `has`/`grant`/`revoke`/`list`/`percent`. Reset each server-script reload. Corrects the prior doc claim that KubeJS native `STAGE_ADDED`/`STAGE_REMOVED` fire on engine grants — they do not (KubeJS 7.x has no native stage events).
- **Deeper `/stage validate`** — full multi-node dependency cycles, transitively-unreachable stages, dead trigger targets (unresolved exact-id entity/block/item/effect or `kill_with` item), and profession-id validation.

### v2.4
- **`[attribute]` stage buffs** — `[[attribute]]` entries (`id` / `operation` / `amount`) grant attribute modifiers while the team owns the stage; any vanilla or modded attribute, `add` / `multiply_base` / `multiply_total`. Applied on grant/login, removed on revoke (transient + reconciled); current health clamps down if max health drops.
- **`[revoke]` + temporary stages (regression)** — `[revoke]` with `on_death`, `xp_below = N` (XP-maintained), `cascade` (revoke dependents). `[stage].duration = "30m"` makes a temporary stage that auto-expires after that much real time, counting down while offline (units `s`/`m`/`h`/`d`; bare number = minutes). Both report `StageCause.REGRESSION`.
- **`[cost]` skill-tree purchases** — `[cost]` (`xp_levels`, `items`, `bypass_requirements`) makes a stage purchasable from an Unlock button in `/stage gui`; fully server-validated; reports `StageCause.PURCHASE`.
- **New `[[triggers]]` condition types** — `effect`, `breed`, `day_count`, `weather` (one-shot), `enter_structure` (one-shot), `tame`, `kill_with`. `tame` / `kill_with` use mod-tracked counters.
- **`[unlock]` unlock juice** — optional `toast`, `title`/`subtitle`, `sound`, `particle`, `progress_nudges` (50/75/90% chat hints), `hud_bar` (blue progress bar above the XP bar).
- **`[abilities]` gating** — `[abilities].locked = ["elytra"]` blocks elytra gliding until the stage is owned.
- **New `[stage]` metadata** — `hidden`, `color`, `category`, and `scope = "server"` (server-wide stage; first team to satisfy unlocks it for everyone).
- **First-class KubeJS stages** — `player.stages.has/add/remove(...)` + `PlayerEvents.stageAdded` / `stageRemoved`; Java `StageChangeEvent` causes extended with `PURCHASE` / `REGRESSION`.

### v2.3
- **Per-stage `[[triggers]]`** — auto-grant triggers moved into each stage's TOML; global `triggers.toml` removed. OR-ed rules of `all_of` / `any_of` conditions over kills, mining, crafting, pickups, use/drop/break, distance, raw stats, play time, level/XP, inventory, advancements, dimensions, and biomes. Counter conditions are retroactive (read vanilla statistics).
- **Triggers respect dependencies** — a stage's triggers don't grant until all its `dependency` prerequisites are owned; counters keep accruing while waiting. Omit `dependency` to fire freely.
- **In-game Stage Tree viewer** — read-only two-pane master/detail screen. "Open Progression Tree" keybind (unbound by default) + `/stage gui`; left pane = status-coloured stage tree by dependency depth, right pane = selected stage's description, prerequisite checklist (✓/✗), live `[[triggers]]` % progress, and an icon grid of the items it unlocks.
- **Per-stage `[display]`** — `display_as_unknown_item`, `obscure_icon`, `show_tooltip`, `show_description_on_tooltip` override the global tooltip/icon defaults.
- **Per-stage `[enforcement]` overrides** — override the same-named global enforcement toggles (`block_item_use/pickup/inventory`, `block_block_placement/interaction`, `block_dimension_travel`, `block_entity_attack`, `block_screen_open`, `block_crop_growth`, `block_pet_interact`) for just that stage's gated resources; most-restrictive-wins across multi-stage gates.
- **New config keys** — `enforcement.obscure_locked_item_icons`, `enforcement.trigger_poll_interval`, `emi.show_stage_description_on_tooltip`, message template `messages.tooltip_stage_description`.
- **Command changes** — added `/stage gui` and `/progressivestages triggers list`; `/progressivestages trigger reset` now takes `<stage>`; removed `/progressivestages multi list`.

### v2.0
- **Unified prefix lock model** — `all:*`, `id:`, `mod:`, `tag:`, `name:` replace v1's scattered arrays.
- **Multi-stage gating** — player must own ALL gating stages to access a resource.
- **Per-stage `[unlocks]`** — scoped carve-outs, no more global-only whitelist.
- **`minecraft = true`** namespace shorthand with parent-stage inheritance.
- **New lock categories**: enchants, crops, mob spawns + replacements, pets, regions, structures, screens, interactions, curio slots.
- **Vanilla inventory lock icons** via `IItemDecorator` — chests, hotbar, modded backpacks all show lock overlays.
- **AnvilMenuMixin** result-slot gate.
- **Visual Workbench compat** via reflective shim.
- **Reveal-stage-names-only-to-operators** privacy flag with client mirror.
- **All player-visible text configurable** with `&` color codes and named placeholders.
- **JEI deep hiding**: multi-FluidStack types, 2-pass refresh, ingredient-filter rebuild, uid namespace regex, `IIngredientListener`.
- **EMI hide-by-class** for Mekanism gases / pigments / chemicals.
- **FTB Quests `canStartTasks` gate** — task progression blocked even if visibility is overridden.
- **`ftbquests_team_mode`** reflective delegation to FTB Teams' `TeamStagesHelper`.
- **Spectator bypass parity** across every enforcer.
- **`reapply_starting_stages_on_login`** opt-in.
- **Mod compat added**: Curios, KubeJS, Lootr, Mekanism, NaturesCompass, Visual Workbench. FTB Library `StageProvider` proxy.
- **`isValidStageName` regex** validation on `starting_stages` to skip malformed config entries.

### v1.4
- Mob spawn gating with a creeper example in the former default `diamond_age.toml`.
- Tick-based dimension safety net for modded portals.
- Recipe ID lock debug logging in `CraftingMenuMixin`.
- EMI / JEI / FTB Teams / FTB Quests all optional dependencies.

### v1.3
- Linear progression mode (auto-grant dependencies).
- Multiple `starting_stages` support.
- Admin bypass confirmation for missing dependencies.
- Stage definitions sync to client with dependencies.

### v1.2
- Entity locks, fluid locks, name pattern matching.
- NBT-aware EMI/JEI hiding for all stack variants (Mekanism Chemical Tanks, Meka-Suit, etc.).
- EMI search index lock icons via `EmiScreenManagerMixin`.
- Creative-mode toggle instantly refreshes EMI/JEI without relog.

### v1.1
- FTB Quests integration.
- EMI handling improvements.
- Trigger system expanded.

---

## Building the editor

Use Node.js 22 and the checked in lockfile. From `editor-ui`, run:

```sh
npm ci
npm run typecheck
npm test
npm run build
```

`typecheck` runs the existing `check` script and is recognized by the shared quality workflow.
The build updates the editor assets packaged with the mod. See the
[frontend verification reference](DOCUMENTATION.md#frontend-development-checks).

## Documentation

- [Configuration, commands, and technical reference](DOCUMENTATION.md).
- [Progression rehaul guide](docs/rehaul/3.0.1/documentation.md).
- [Block overrides and stage guides](docs/troubleshooting/documentation.md).
- [Build and test instructions](docs/testing/documentation.md).
- [Documentation index](docs/README.md).
- [Copy ready example stages](examples/beginner_pack/README.md).
- [CurseForge 3.0 Description](docs/release/3.0.1/documentation.md).

## License

All Rights Reserved
