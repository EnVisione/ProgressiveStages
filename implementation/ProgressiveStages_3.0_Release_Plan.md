# ProgressiveStages 3.0 Release Plan

This records the implemented 3.0 baseline and its release gates. The authoritative roadmap for
the complete 15 feature release is [`../plan.md`](../plan.md).

## Release vision

ProgressiveStages 3.0 is an authorable progression platform rather than a fixed linear
stage gate. Pack makers define the graph, presentation, unlock rules, enforcement,
automation hooks, scope, costs, rewards, and visibility while players receive a UI that
looks and behaves like a native Minecraft screen.

## Implemented in this release

### 1. Vanilla-standard progression map

- Advancement-window styling with vanilla task, goal, and challenge frames.
- Draggable canvas, mouse-wheel and keyboard panning, dependency connectors, tiled
  backgrounds, search, hide-owned filtering, hover cards, and click-to-pin details.
- Trigger progress, prerequisite state, unlock previews, and server-validated purchases.
- Category cycling, text/item search, owned filtering, mouse dragging, wheel/WASD navigation,
  and a scrollable inspector.
- Automatic dependency-graph layout, with per-stage author overrides:

```toml
[display]
x = 80
y = 20
frame = "goal"                 # task | goal | challenge
background = "minecraft:textures/block/deepslate_tiles.png"
reveal = "dependencies"        # always | dependencies | unlocked
sort_order = 10
```

### 2. Stage-owned triggers and extensibility

- `[[triggers]]` remains part of each stage TOML; the retired global `triggers.toml` is
  ignored with a migration warning.
- Named `custom_counter` conditions connect arbitrary scripts, quests, machines, and
  integrations to declarative stage files.
- Counter values can be read, added, set, or reset from commands, KubeJS, or Java, with
  immediate trigger re-evaluation.
- Live `scoreboard`, `health`, `food`, `stage_count`, and `online_team_size` conditions,
  plus numeric `script_value` providers, let stage files consume existing pack state directly.
- `[stage].dependency_mode = "all" | "any" | "at_least"` and `dependency_count` support
  linear chains, alternate routes, and quorum progression in the same graph.

```toml
[[triggers]]
mode = "all_of"
conditions = [
  { type = "custom_counter", counter = "factory_quests", count = 12 }
]
```

### 3. Command and KubeJS authoring surfaces

- `/stage`, `/stages`, and `/ps` open the stage map for ordinary players.
- Public inspection commands remain usable without operator permission; mutations and
  authoring commands are permission-gated.
- `/stage counter get|add|set|reset` manages named trigger counters.
- The global KubeJS `ProgressiveStages` object supports actual-change and bypass mutations,
  collection/all-stage bulk operations, ownership and graph queries, category/tag discovery,
  definition and trigger-progress snapshots, counters, immediate evaluation, GUI/sync controls,
  boolean/numeric trigger providers, and rich lifecycle callbacks with cause/team context.
- `player.stages.add/remove` routes through the same engine and reports `SCRIPT` as its
  cause instead of bypassing normal stage behavior.

### 3.1 Unified config hierarchy

- Main config: `config/progressivestages/progressivestages.toml`.
- Stage definitions: `config/progressivestages/stages/*.toml`.
- Version 3 safely migrates the legacy root config and `config/ProgressiveStages/*.toml`
  files without overwriting an existing destination.
- Stage files may be organized in deterministic nested folders below `stages/`.
- Reload compiles a candidate snapshot first and retains the previous live state when parsing,
  duplicate identifier, or dependency validation fails.

### 4. Correctness and multiplayer repair

- Per-stage `[unlocks]` carve-outs and enforcement overrides stay attached to the stage
  that declared them.
- Multi-stage requirements are preserved for dimensions, interactions, curio slots,
  structures, and other secondary categories.
- Team-scoped and server-scoped dependencies use the correct storage owner and effective
  stage union during checks and synchronization.
- Runtime registries, trigger state, timers, caches, and server references reset safely
  across reloads and integrated-server restarts.
- Biome-time counters accumulate exact ticks at arbitrary polling intervals.
- Inventory override categories operate independently.

### 5. Dedicated-client parity

- Item, block, fluid, recipe, recipe-output, entity, and whole-mod locks synchronize to
  clients with every gating stage retained.
- Native tooltips, EMI, JEI, Jade, and WTHIT consume synced state instead of reading
  server-only registries on dedicated clients.

### 6. Release engineering

- Version and metadata are normalized to `3.0.0`.
- Optional integration APIs resolve from their publisher repositories; a clean clone does
  not rely on ignored developer-local JAR files.
- JUnit 5 is wired into the standard build and covers the new trigger-type contract.
- README, full documentation, changelog, default templates, and `/stage new` scaffolding
  describe the 3.0 authoring surface.

## Automated release gates

- [x] Clean Java compilation without local `libs/*.jar` dependencies.
- [x] Unit test suite.
- [x] Full Gradle build and distributable JAR assembly.
- [x] Patch whitespace validation.

## Manual release gates

These require launching real clients and are intentionally not represented as automated
successes:

- [ ] Open the stage map at small, medium, and 4K GUI scales; verify dragging, wheel
  panning, hover cards, search, pinned details, and purchase controls.
- [ ] Dedicated server with two clients: verify team grants, server-scope grants, revoke
  cascades, reconnect, `/reload`, and server restart.
- [ ] Optional-mod matrix: no recipe viewer, EMI only, JEI only, FTB Teams/Quests,
  KubeJS, Jade, and WTHIT.
- [ ] Migration world: load existing 2.x stage TOML and saved team data, then confirm the
  old progression remains intact.
- [ ] Stress pack: 1,000+ lock entries and a branching graph of at least 100 stages.

## Release sequence

1. Complete the manual gates above and log any failures as release blockers.
2. Re-run `./gradlew clean build` on the release commit.
3. Test the resulting `build/libs/progressivestages-3.0.0.jar` in a clean instance.
4. Tag the exact tested commit as `v3.0.0` and publish that same JAR with `changelog.md`.

The manual matrix remains required for this baseline because rendering, optional-mod mixins,
and multi-client behavior cannot be proven by compilation alone. The additional architecture,
authoring, policy, progression, diagnostics, and performance phases are tracked in `plan.md`.
