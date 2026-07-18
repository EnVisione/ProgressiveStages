# ProgressiveStages 3.0 Testing Handbook

This handbook explains how to prove that a ProgressiveStages build and stage pack work. It covers
project builds, automated tests, configuration validation, single-player smoke tests, dedicated
servers, multiplayer scope, optional integrations, regressions, and a release evidence template.

Use [GETTING_STARTED.md](GETTING_STARTED.md) if you are learning stage authoring. Use
[DOCUMENTATION.md](DOCUMENTATION.md) for the full schema and API reference.
Schema 4 package, editor bridge, migration, client snapshot, persistence, and rehaul release gates
are documented in [REHAUL_GUIDE.md](REHAUL_GUIDE.md).

## 1. Testing vocabulary

- A build test proves the Java project compiles and packages.
- A unit test proves a focused code contract in isolation.
- A configuration test proves stage TOML parses and the graph is valid.
- A smoke test proves the most important real-game flow works.
- A regression test proves a previously fixed defect stays fixed.
- An integration test proves ProgressiveStages and another mod cooperate.
- A matrix test repeats a scenario across environments or mod combinations.

No single test type proves everything. A successful Gradle build cannot prove that a button looks
correct at every GUI scale. A successful manual click test cannot prove dependency-cycle handling.
Release confidence comes from using the layers together.

## 2. Build prerequisites

Install:

- A Java 21 JDK.
- Git.
- A network connection for the first dependency resolution.

Confirm Java:

```bash
java -version
```

The major version must be 21. Then run from the repository root:

```bash
./gradlew clean build --no-configuration-cache
```

Expected final line:

```text
BUILD SUCCESSFUL
```

The release JAR appears at:

```text
build/libs/progressivestages-3.0.1.jar
```

`clean build` performs compilation, resource processing, packaging, unit-test compilation, and
the test task. The project enables NeoForge's Minecraft-aware unit-test classpath, so tests can use
Minecraft types rather than being limited to plain Java models.

## 3. Force tests to execute

Gradle may reuse a correct cached result. To force every test task to execute during an audit:

```bash
./gradlew test --rerun-tasks --no-configuration-cache
```

Review results under:

```text
build/test-results/test/
build/reports/tests/test/index.html
```

Every suite must report zero failures and zero errors. Skipped tests require an explanation in the
release record.

## 4. What the automated tests cover

The test sources live under `src/test/java`.

| Test | Contract |
|---|---|
| `StageIdTest` | Namespaces, normalization, locale safety, and traversal rejection. |
| `DependencyModeTest` | `all`, `any`, and `at_least` requirement counts. |
| `StageOrderTest` | Converging dependency graphs and cycle handling. |
| `StageFileParserTest` | Namespaced IDs, malformed data, trigger validation, costs, custom map backgrounds, generated templates, and all temporary or triggered rule forms. |
| `ConditionalLockEngineTest` | Priority ordering, static-gate overrides, safe lock-on-tie behavior, and deterministic same-effect ties. |
| `StageModelImmutabilityTest` | Defensive copies for rewards, regions, and enforcement maps. |
| `StagePurchaseDataTest` | Namespaced offline refund persistence. |
| `ScriptHooksTest` | Normalized script provider identifiers. |
| `OptionalCompatMixinPluginTest` | Optional integration mixins apply only when their target classes exist. |
| `ClientLockCacheTest` | Immutable defensive client lock snapshots. |
| `StageTreeScreenRenderOrderTest` | One blur pass, inspector depth, and drag-from-node behavior. |
| `StageTreeInventoryButtonTest` | Survival inventory registration, dynamic position, and map request wiring. |
| `StageCommandAliasTest` | `/pstages` is registered while the conflicting short alias remains free. |
| `OreSpoofCameraStabilityTest` | Stage and game-mode refreshes never unload the chunk beneath the player. |
| `StageAttributeApplierTest` | Unchanged scale modifiers remain attached while changed and revoked modifiers reconcile correctly. |
| `TriggerConditionTypeTest` | Trigger aliases and invalid condition types. |
| `StructureCompatibilityTypesTest` | Stable session IDs, normalized immutable bounds, and defensive session views. |
| `StructureAccessArbitrationTest` | Static and provider deny or permit truth table. |
| `StructureContextRegistryTest` | Unique provider registration and idempotent unregister behavior. |
| `StructureLeaseDataTest` | Restart persistence for lease-introduced stages, storage owners, and participant references. |
| `StructureLeasePolicyTest` | Duplicate acquire safety, multiple participants, final release, and pre-owned stage preservation. |
| `StructureSessionPolicyTest` | Access revocation only on the final committed completed exit, never on lifecycle interruption. |
| `StructureVisitTransitionsTest` | Enter deduplication, expanded bounds, leave debounce, re-entry cancellation, and committed leave timing. |
| `BeginnerExamplePackTest` | Every copy-ready beginner TOML parses and its graph validates. |
| `DocumentationReferenceTest` | The Diamond Stage, nineteen-phase guide, release presentation, and documentation links stay connected. |

When fixing a defect, add a test that fails before the fix and passes after it whenever the
behavior can be isolated. Manual-only fixes must receive a written reproduction recipe.

The complete exact-structure provider, transition, lease, contextual item-lock, lifecycle, and
companion-mod acceptance matrix is in
[STRUCTURE_SESSION_COMPATIBILITY.md](STRUCTURE_SESSION_COMPATIBILITY.md#16-acceptance-test-matrix).

## 5. Validate a stage pack in game

Start the game or server with the pack installed. As an operator, run:

```text
/progressivestages validate
```

Validation checks include:

- TOML syntax.
- Required stage identity.
- Stage ID format.
- Duplicate IDs.
- Missing dependencies.
- Dependency cycles.
- Transitively unreachable stages.
- Invalid exact registry IDs in supported categories.
- Invalid statically resolvable trigger targets.
- Invalid exact conditional item, block, fluid, entity, effect, and trigger-entity targets.
- Duplicate canonical conditional rule IDs across stage files.
- Missing stage IDs in conditional `when.stages` and `when.missing_stages` contexts.

Do not reload an invalid candidate. After validation succeeds:

```text
/progressivestages reload
/stage tree
/stage simulate <player>
```

A rejected reload must leave the previous valid snapshot active. Test this deliberately in a copy
of the server by adding one malformed file, attempting reload, and confirming existing locks still
behave exactly as before.

## 6. Minimal single-player smoke test

Use a disposable world and the tested pack in `examples/beginner_pack`.

1. Join the world.
2. Run `/progressivestages validate`.
3. Run `/stage list` and confirm the configured starting stage behavior.
4. Open `/stage`.
5. Pan the map by dragging empty space, then repeat by beginning the drag on a stage node.
6. Scroll with the wheel and keyboard.
7. Search for `diamond`.
8. Hover each visible node.
9. Click a node and inspect dependencies and trigger progress. Confirm no map icon appears above the inspector.
10. Run `/stage revoke <player> iron_age`.
11. Attempt to use an iron pickaxe and confirm it is blocked.
12. Complete or grant Iron Age.
13. Use the iron pickaxe and confirm it works.
14. Revoke Iron Age again and confirm the lock returns.
15. Repeat the before, after, revoke pattern for a block and recipe.
16. Run `/stage sync <player>` and confirm the map and lock visuals remain correct.
17. Enable Video Settings → Menu Background Blur, reopen the map, and confirm only the world is blurred.
18. Open the survival inventory and use the lock button beside the recipe book to open the map.
19. Toggle the recipe book, reopen the inventory, and confirm the lock button remains beside it.
20. Load the Stronghold and End examples from `TEMPORARY_AND_TRIGGERED_LOCKS.md`.
21. Confirm the normal Stronghold gate blocks, the priority one hundred End Fight permission allows,
    and the priority two hundred End restriction wins only inside the End.
22. Start a manual timer with `/pstages rule activate`, inspect it with `/pstages rule list`, wait for
    expiry, and repeat with explicit clear.
23. Test the structure weapon rule just outside and inside the generated structure bounds.
24. Trigger combat against the configured mob and verify target exception, refresh, and expiry.

Record unexpected chat, logs, missing textures, stale icons, crashes, and visual overlap.

## 7. UI matrix

Test the progression map at these GUI scales when the display supports them:

- Auto.
- Small.
- Normal.
- Large.

At each scale verify:

- The window stays on screen.
- Search text can be typed without WASD moving the map.
- Nodes remain clickable after panning.
- Dragging can begin on empty map space or on a node without accidentally opening details.
- Tooltips remain readable near every edge.
- The inspector scrolls when content is taller than its panel.
- The close control only closes the inspector.
- Escape closes the inspector first and the screen second.
- Category and Owned controls do not overlap search.
- Long translated text truncates without crashing.
- Hidden and unrevealed stages do not leak names.
- Purchase buttons show the authoritative server state.
- Node icons never overlap the pinned inspector.
- Every configured trigger route and condition is visible in the inspector.
- Custom `[display].background` textures tile without missing-texture markers.

Also test keyboard-only navigation paths, narrator output, and controller behavior available through
the user's input setup. Accessibility failures belong in the release record even when they do not
crash the game.

## 8. Dedicated server test

Create a clean NeoForge 1.21.1 server with Java 21.

1. Put the release JAR in the server `mods` folder.
2. Add only server-compatible dependencies required by the test instance.
3. Start once and accept the Minecraft EULA.
4. Confirm `config/progressivestages/progressivestages.toml` exists.
5. Confirm `config/progressivestages/stages` contains the defaults.
6. Confirm startup has no client-class loading failure.
7. Join with a matching client.
8. Run validation, reload, tree, simulate, grant, revoke, and sync commands.
9. Stop the server cleanly.
10. Start it again and confirm owned stages persisted.

Search logs for:

```text
ERROR
Exception
NoClassDefFoundError
Mixin apply failed
Reload rejected
```

Some unrelated mods log harmless errors, so record the full surrounding context rather than only
the matching line.

## 9. Two-player scope matrix

Use players A and B.

### Solo mode

1. Set `general.team_mode = "solo"`.
2. Grant Iron Age to A.
3. Confirm A can use iron content.
4. Confirm B remains blocked.
5. Restart and repeat the checks.

### FTB Teams mode

1. Install supported FTB Teams and set `team_mode = "ftb_teams"`.
2. Put A and B on the same team.
3. Grant Iron Age through A.
4. Confirm both players receive the stage and synchronized visuals.
5. Move B to another team.
6. Confirm team transition behavior matches configuration.

### Server scope

1. Create a test stage with `scope = "server"`.
2. Grant it through A.
3. Confirm B receives effective ownership even on another team.
4. Revoke it.
5. Confirm both players lose effective ownership.

## 10. Trigger matrix

For each trigger used by the pack, record:

- The initial value.
- The required threshold.
- The action performed.
- The value reported by `/stage progress`.
- Whether dependencies were owned.
- Whether the stage granted at the exact expected moment.
- Whether progress persisted across restart when documented.

Test nested or multi-condition rules one condition at a time. Confirm `all_of` waits for every
condition and `any_of` completes after one. Confirm separate `[[triggers]]` rules act as alternatives.

For custom counters:

```text
/stage counter get <player> quest_points
/stage counter add <player> quest_points 1
/stage counter set <player> quest_points 10
/stage counter reset <player> quest_points
```

Every mutation should immediately update trigger progress.

## 11. Purchase and reward tests

For a purchasable stage:

1. Attempt purchase without the cost.
2. Confirm nothing is consumed and no stage is granted.
3. Supply exactly the required items and XP.
4. Purchase once.
5. Confirm the cost is consumed once.
6. Confirm the stage and rewards apply once.
7. Attempt immediate repeat purchase.
8. Confirm ownership or cooldown prevents double spending.
9. Revoke the stage.
10. Confirm the configured paid refund is returned once.
11. Restart with a pending offline team refund and confirm it is delivered once.

Test full inventories so restored costs and refunds safely drop only the remainder that cannot fit.

## 12. Optional integration matrix

Test core alone first. Then add one integration at a time:

| Combination | Required checks |
|---|---|
| Core only | Startup, locks, map, commands, reload. |
| EMI only | Locked ingredients disappear and reappear after grant or revoke. |
| JEI only | Same visibility behavior without EMI classes present. |
| Jade only | Locked blocks and entities show the required-stage overlay. |
| WTHIT only | Plugin discovery and overlay match Jade behavior. |
| FTB Quests and Library | Provider status, tasks, rewards, required quest and chapter stages. |
| FTB Teams | Team ownership and transitions. |
| KubeJS | Global API, player stage bridge, callbacks, conditions, and counters. |
| Lootr | Per-player loot filtering remains correct. |
| Curios | Slot rules block and permit equipment correctly. |
| Mekanism | Item and chemical visibility plus supported enforcement hooks. |

After individual tests, test the combinations used by the real pack. The highest-risk recipe viewer
combination is EMI and JEI together because both react to stage synchronization and reload events.

## 13. Reload and lifecycle regression tests

Run these in an integrated server and a dedicated server:

1. Load a valid stage set.
2. Join and synchronize a client.
3. Introduce a syntax error.
4. Attempt reload.
5. Confirm the old snapshot stays active.
6. Fix the syntax error and reload.
7. Confirm definitions and locks update without relog.
8. Stop the world and open a different world in the same client process.
9. Confirm no stages, locks, cooldowns, counters, or integration queues leak between worlds.
10. Return to the first world and verify its persisted state.
11. Activate a timed conditional rule, log out, reconnect, and confirm transient timer state cleared.
12. Activate another timer, stop the server, restart it, and confirm no timer leaked across runtime.
13. Reload a changed rule set and confirm removed rule timers are discarded while retained rule IDs
    remain safe.

## 14. Performance checks

Use a representative production pack, not only three tutorial stages.

Record:

- Number of stages.
- Number of direct dependency edges.
- Number of lock entries per category.
- Number of online players.
- Trigger polling interval.
- Average and worst server tick time.
- Client frame time with EMI or JEI open.
- Reload duration.
- Stage synchronization packet behavior after login and bulk changes.

Pay special attention to screens showing hundreds of recipe-viewer ingredients, large tag or mod
locks, chunk ore substitution, inventory scanning, structure checks, and mass grant or revoke.
For conditional rules, record packs with many structure selectors, broad `mod:` or `name:` targets,
and many online players. Context snapshots are cached once per game tick per player, so investigate
unexpected repeated structure scans rather than increasing a polling interval.

## 15. Release evidence template

Copy this into a release issue or local report:

```markdown
# ProgressiveStages release verification

- Commit:
- Branch:
- Minecraft:
- NeoForge:
- Java:
- Operating system:

## Automated

- [ ] `./gradlew clean build --no-configuration-cache`
- [ ] `./gradlew test --rerun-tasks --no-configuration-cache`
- Tests executed:
- Failures:
- JAR path and checksum:

## Configuration

- [ ] Default stages validate
- [ ] Production stages validate
- [ ] Invalid reload preserves previous snapshot

## Runtime

- [ ] Single player smoke test
- [ ] Dedicated server startup and restart
- [ ] Two-player solo test
- [ ] Two-player team test
- [ ] Server-scope test
- [ ] UI scale and input matrix
- [ ] Conditional priority and context matrix
- [ ] Combat and manual timer lifecycle

## Integrations

- [ ] EMI
- [ ] JEI
- [ ] Jade
- [ ] WTHIT
- [ ] FTB Quests
- [ ] FTB Teams
- [ ] KubeJS
- [ ] Pack-specific integrations

## Findings

- Blocking:
- Non-blocking:
- Deferred:
- Evidence paths:
```

Do not mark a release approved while a required row is unknown. Write `not applicable` with a
reason when the pack intentionally does not use an integration.

## 16. Final release gate

A release candidate is ready only when:

- The worktree contains only intended changes.
- The commit author and committer are correct.
- The branch and remote point to the same commit.
- The clean build passes without compiler warnings.
- Every automated test executes and passes.
- Stage validation passes.
- The JAR contains required metadata, mixin configs, services, and integration descriptors.
- The single-player and dedicated-server smoke tests pass.
- Player, team, and server scope behave correctly with two clients.
- Required optional integrations pass their matrix.
- Known limitations are written down rather than hidden.
- Configuration and world backups exist before deployment.
