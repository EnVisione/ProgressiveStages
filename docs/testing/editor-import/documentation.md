# Editor Package Import

Two GameTests exercise the actual authenticated `EditorSessionService.handle` package actions
with controlled permission level 3 server player objects. Use Minecraft 1.21.1, NeoForge
21.1.248 and Java 21 in an isolated dedicated development server. Enable both `progressivestages`
and `minecraft` GameTest namespaces because the fixtures use the vanilla `igloo/top` template.

`packageimportrejectsinvalidfilesatomically` submits a valid `stage.toml` followed by an invalid
file. It covers a script, parent traversal, nested traversal, an absolute path, a Windows drive
path and a second path normalizing to `stage.toml`. Each request must fail without changing
the draft revision, file map, undo availability or redo availability.

`packageexportreimportsnestedtoml` creates a package containing `stage.toml`, `rules.toml`,
`nested/foods.toml` and `notes.toml`. It exports the package, deletes the original from the
draft, imports the exported map into another folder and validates its included bread rule.
Reexport must preserve every file byte for byte, including comments and quoted keys. Undoing
each imported file must restore the earlier draft. Redo must restore the complete package.

Run each command separately after server readiness at an unused test location:

```text
execute positioned 0 180 0 run test run packageimportrejectsinvalidfilesatomically
execute positioned 20 180 0 run test run packageexportreimportsnestedtoml
```

Run the existing `sessionapplyrejectsunreviewedchanges` and
`removedcollaboratorsloseeverysessionaction` regressions alongside these tests. Inspect the actual
test results. A command reporting that no test exists is a setup failure, not an executed test.

Both import fixtures discard their owned draft, associated sessions and server player object in
`finally`. They do not apply live configuration. After the suite, stop the owned server, verify
its private listener and process exited, and remove its disposable runtime and scratch output.
Compare tested classes with the packaged candidate and check production startup separately.
These tests establish server service behavior. Browser interaction and authenticated client
transport require separate client tests.
