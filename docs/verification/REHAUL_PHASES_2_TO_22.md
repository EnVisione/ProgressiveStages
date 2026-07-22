# Rehaul phases 2 through 22 verification

This record maps the approved rehaul plan to implementation and automated evidence. Final release
commands, test totals, artifact name, size, and checksum are recorded after the clean build.

| Phase | Implemented surface | Primary automated evidence |
|---|---|---|
| 2 | Schema 4 DTOs, provenance, editor schemas | `SchemaMetadataCoverageTest`, `StagePackageParserTest` |
| 3 | Immutable compiled stage, rule, progression, condition, action, and trace models | `CompiledModelImmutabilityTest` |
| 4 | Marker based config and datapack package discovery with deterministic includes | `StagePackageParserTest` |
| 5 | In memory legacy translation and unchanged one file loading | `LegacyCompatibilityBaselineTest` |
| 6 | Authoritative registries, resources, tags, recipes, stages, extension catalogs, paging, cursors, selectors, and a bounded one hundred thousand entry index | `CatalogSnapshotTest`, `SelectorMatcherRegistryTest` |
| 7 | Shared priority cascade, lock, allow, local exclusion, viewer policy, and explanation resolver | `DecisionResolverTest` |
| 8 | Public condition registry, compiler, boolean trees, sequences, references, comparisons, and interests | `ConditionCompilerTest` |
| 9 | Symmetric transactional grants and revokes with cycle and stability controls | `LifecycleTransactionEngineTest` |
| 10 | Health, damage, death, hit, respawn, no damage, and rolling counters | `WindowCounterStoreTest` |
| 11 | Contextual item attributes, effects, transforms, aggregation, stacking, safe reconciliation, and selector based block output modifiers with tool, enchantment, stage, condition, priority, bounds, and exclusivity | `ModifierResolverTest`, `DropModifierResolverTest`, `CameraStabilityTest` |
| 12 | Generic challenge sessions, ordered steps, registered measures, budgets, retries, actions, and a live configured HUD | `ChallengeEngineTest` and client payload compilation |
| 13 | Affinity, proficiency, templates, parameters, variables, formulas, multi-state stages, and live runtime evaluation | `AdvancedProgressionModelTest`, `FormulaAndTemplateTest` |
| 14 | Registered action pipelines, retries, failure policy, compensation, and extension adapters | `ActionExecutorTest` |
| 15 | Immutable Java API plus KubeJS registrations, typed metadata, callbacks, catalogs, and capabilities | API and metadata codec tests in the complete suite |
| 16 | Migration scan, plan, split, write, checksum, semantic verify, rollback, and path containment | `LegacyMigrationServiceTest` |
| 17 | Revisioned permission-filtered compressed chunked client snapshot, safe deltas, exact base verification, decompression bounds, checksum, atomic activation, request, and acknowledgement | `ClientSnapshotAssemblerTest`, client cache tests |
| 18 | Integrated and dedicated server operator authorization, expiring secrets, client loopback bridge, CSP, loopback aware host and origin checks, browser header fallback, limits, and cleanup | `EditorAvailabilityTest`, `LoopbackRequestGuardTest`, `PackagedEditorAssetsTest`, and bridge security inspection |
| 19 | Server drafts, revisions, undo, redo, recovery, collaboration, diff, validation, conflict, atomic apply, backup, audit, and rollback | `EditorDraftTest` and editor apply tests |
| 20 | Packaged React and TypeScript stage editor, real mod logo, restrained dark gray and gold design, one navigation bar, two column stage editing, focused stage tabs, plain name creation, interchangeable namespaces, visual all, any, and minimum dependency policies, cycle prevention, slot limits, every rule category, prefixes, live registries, mod filters, JEI and EMI controls, priorities, exceptions, temporary conditions, grants, revokes, purchases, rewards, abilities, attributes, item modifiers, drop modifiers, challenges, variables, formulas, states, profiles, templates, optional source, settings, extensions, collaboration, simulation, movable and zoomable player layout, direct branch creation and removal, validation, review, apply, and rollback | `npm run check`, `npm test`, `npm run build`, `EditorSchemaRegistryTest`, `PackagedEditorAssetsTest` |
| 21 | Advancement style player screen, centered upward automatic layout, author coordinates, panning, scroll, layering, background, triggers, why, readable pluralized purchase costs, live challenge HUD, effective held-item modifier and affinity preview, history, docs, and tested starter, Diamond, mage, knight, structure, End, Wither, migration, KubeJS, and Java examples | `StageTreeLayoutTest`, `NetworkCostSummaryTest`, UI regression tests, `RehaulExamplesTest`, `RehaulDocumentationTest` |
| 22 | Restart saved data, dependency audit, fifty stage and one hundred fifty file first-launch showcase validation, stage slot runtime validation, full unit suite, clean build, and JAR inspection | `RehaulStateCodecTest`, `DefaultShowcaseStagesTest`, `StageSlotResolverTest`, and final evidence below |

## Final evidence

Recorded on July 19, 2026 from branch `envy/3.0.1`.

### Frontend

- `npm run check` passed with TypeScript emitting no files and no errors.
- `npm test` passed nine React editor model and TOML preservation tests.
- `npm run build` passed with Vite 6.4.3.
- Production output contains `index.html`, `app.css`, `app.js`, and `favicon.svg`.
- The application uses React 19.1.1. The previous Preact shell and `legacy.js` controller were removed.
- The loopback bridge serves the existing 512 by 512 mod logo at `/logo.png` from the JAR.
- `npm audit --audit-level=high` reported zero vulnerabilities.

### Java and dedicated server

- `./gradlew clean test build --no-daemon` completed with `BUILD SUCCESSFUL` in seven seconds.
- The clean suite ran 150 tests with zero failures, zero errors, and zero
  skipped tests.
- `DefaultShowcaseStagesTest` wrote all one hundred fifty generated files into an empty temporary
  stages directory, discovered fifty packages, parsed and compiled every package, validated the
  dependency graph, checked exactly two purchased beginner paths and one trigger-earned beginner,
  fifteen total purchases, thirty five trigger paths, secret reveal policies, stackable engineers,
  mutually exclusive mining modes, temporary stages, exact kill-with-item conditions, and the
  Diamond Engineer cost and Fortune multiplier.
- `StageTreeLayoutTest` verifies that automatic player nodes grow upward and center narrow layers.
  `StageTreeInventoryButtonTest` verifies that `client.show_inventory_button` defaults to true and
  gates survival inventory button registration. The editor schema
  coverage test verifies that Settings exposes the option. `NetworkCostSummaryTest` verifies
  readable singular, plural, and uncountable payment names.
- `StageSlotResolverTest`, `StageFileParserTest`, and `StageOrderTest` verify unlimited stacking,
  denial, oldest and priority replacement, schema parsing, and consistent group validation.
- `LoopbackRequestGuardTest` verifies single player browser host aliases, absent and opaque origin headers,
  both supported session headers, the active random port, remote origin rejection, remote host
  rejection, and incorrect token rejection. `PackagedEditorAssetsTest` verifies the simple stage
  first shell, direct graph connection creation and removal controls, CSP safe branch hit areas,
  and change only operator chat language. `EditorApplyChatTest` verifies
  gold headings, green additions, yellow modifications, green synchronization results, and silence
  when no file changed. `AbilityEnforcerTest` verifies that jump, elytra, sprint, swim, and climb
  all remain in the authoritative enforcement set. `ClientAbilityStateTest` verifies normalized,
  replacing client snapshots so stale restrictions cannot survive a server state change.
- `PrefixEntryTest` and `SelectorMatcherRegistryTest` verify `all:*`, specificity, priority, and
  exception behavior. `EntityPresenceEnforcerTest` verifies simulation distance boundaries and the
  mixed multiplayer spawn matrix. `ClientEntityVisibilityTest` verifies replacing immutable
  concealment snapshots.
- `./gradlew runServer --no-daemon` reached the dedicated server ready state in an isolated world.
  NeoForge applied `ChunkMapAccessor` and `ChunkMapTrackedEntityMixin`, loaded the three existing
  legacy stages, and saved every dimension cleanly during shutdown.

### Release artifact

- Artifact: `build/libs/progressivestages-3.0.1.jar`.
- Size: 1,716,583 bytes.
- SHA-256: `439b6a58924f1e1bcf121c29bc0a6ef31bcfeafc2e67a9562317c23999cc6ec4`.
- The JAR contains `META-INF/neoforge.mods.toml`, the 512 by 512 `progressivestages.png` mod-list
  logo, `assets/progressivestages/lang/en_us.json`, and all four production React editor assets.
- The source logo SHA-256 is
  `7c38835022f7ace8aa94070801272d190ab8bcd49b38e252fec6d7ea6f7af992`.

### Remaining human acceptance gate

Rendering, browser interaction, narrator behavior, and current versus future multi-client visual
agreement require interactive Minecraft clients. Follow the exact final checklist in
`REHAUL_GUIDE.md` section 26 before publishing the JAR. This is a manual release acceptance gate,
not an unimplemented engine or editor feature.
