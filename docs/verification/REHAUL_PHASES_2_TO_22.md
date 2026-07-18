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
| 11 | Contextual item attributes, effects, transforms, aggregation, stacking, and safe reconciliation | `ModifierResolverTest`, `CameraStabilityTest` |
| 12 | Generic challenge sessions, ordered steps, registered measures, budgets, retries, actions, and a live configured HUD | `ChallengeEngineTest` and client payload compilation |
| 13 | Affinity, proficiency, templates, parameters, variables, formulas, multi-state stages, and live runtime evaluation | `AdvancedProgressionModelTest`, `FormulaAndTemplateTest` |
| 14 | Registered action pipelines, retries, failure policy, compensation, and extension adapters | `ActionExecutorTest` |
| 15 | Immutable Java API plus KubeJS registrations, typed metadata, callbacks, catalogs, and capabilities | API and metadata codec tests in the complete suite |
| 16 | Migration scan, plan, split, write, checksum, semantic verify, rollback, and path containment | `LegacyMigrationServiceTest` |
| 17 | Revisioned permission-filtered compressed chunked client snapshot, safe deltas, exact base verification, decompression bounds, checksum, atomic activation, request, and acknowledgement | `ClientSnapshotAssemblerTest`, client cache tests |
| 18 | Integrated and dedicated server operator authorization, expiring secrets, client loopback bridge, CSP, host and origin checks, limits, and cleanup | `EditorAvailabilityTest`, `PackagedEditorAssetsTest`, and bridge security inspection |
| 19 | Server drafts, revisions, undo, redo, recovery, collaboration, diff, validation, conflict, atomic apply, backup, audit, and rollback | `EditorDraftTest` and editor apply tests |
| 20 | Packaged Preact and TypeScript shell, neutral dark gray and gold stage-first easy builder, inline plain-name stage creation, visual all, any, and minimum dependency path policies, ancestry preview, cycle prevention, upward automatic graph layout, category and mod filtered live-registry rule cards, action, effect, priority, exception, JEI and EMI, temporary condition and lifetime controls, guided grants, revokes, rewards, costs, challenges, variables, modifiers, formulas, states, profiles, templates, draggable rules, contained draggable graph, optional three-tab source, inspector, create, duplicate, rename, move, archive, restore, import, export, delete, collaborator, priority analyzer, simulation, review, and apply | `npm run check`, `npm run build`, `EditorSchemaRegistryTest`, `PackagedEditorAssetsTest` |
| 21 | Advancement style player screen, panning, scroll, layering, background, triggers, why, live challenge HUD, effective held-item modifier and affinity preview, history, docs, and tested starter, Diamond, mage, knight, structure, End, Wither, migration, KubeJS, and Java examples | UI regression tests, `RehaulExamplesTest`, `RehaulDocumentationTest` |
| 22 | Restart saved data, dependency audit, full unit suite, clean build, and JAR inspection | `RehaulStateCodecTest` and final evidence below |

## Final evidence

Recorded on July 18, 2026 from branch `envy/3.0.1`.

### Frontend

- `node --check editor-ui/public/legacy.js` passed.
- `npm run check` passed with TypeScript emitting no files and no errors.
- `npm run build` passed with Vite 6.4.3.
- Production output contains `index.html`, `app.css`, `app.js`, and `legacy.js`.
- `npm audit --audit-level=high` reported zero vulnerabilities.

### Java and dedicated server

- `./gradlew clean test build --no-daemon` completed with `BUILD SUCCESSFUL` in eleven seconds.
- The clean suite ran 111 tests with zero failures, zero errors, and zero
  skipped tests.
- `./gradlew runServer --no-daemon` initialized ProgressiveStages 3.0.1, generated or loaded the
  main config from `config/progressivestages/progressivestages.toml`, reached the dedicated-server
  ready line in 0.956 seconds, and loaded three stage definitions without a ProgressiveStages error.

### Release artifact

- Artifact: `build/libs/progressivestages-3.0.1.jar`.
- Size: 1,566,718 bytes.
- SHA-256: `01b51304b374383d1a1e41470630b563dad3f98621f7e38cdd047314d274a27f`.
- The JAR contains `META-INF/neoforge.mods.toml`, the 512 by 512 `progressivestages.png` mod-list
  logo, `assets/progressivestages/lang/en_us.json`, and all four production editor assets.
- The source logo SHA-256 is
  `7c38835022f7ace8aa94070801272d190ab8bcd49b38e252fec6d7ea6f7af992`.

### Remaining human acceptance gate

Rendering, browser interaction, narrator behavior, and current versus future multi-client visual
agreement require interactive Minecraft clients. Follow the exact final checklist in
`REHAUL_GUIDE.md` section 26 before publishing the JAR. This is a manual release acceptance gate,
not an unimplemented engine or editor feature.
