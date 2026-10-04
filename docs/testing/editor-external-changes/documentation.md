# Editor External File Changes

`sessionapplyrejectsexternalchangesbeforereload` exercises the actual editor session boundary
on a dedicated server. It creates and loads a unique stage, opens a permission level 3 draft,
edits and reviews that draft, then changes the same live file without reloading the server.

Apply must return `configuration_conflict`. The externally edited file must remain unchanged,
the compiled configuration revision must remain the last valid revision, and the draft must
retain its own edited source and revision. This distinguishes source file conflicts from stale
review conflicts. The earlier implementation overwrote the external edit because it checked
files only after a compiled revision change.

Use Minecraft 1.21.1, NeoForge 21.1.248 and Java 21 with both `progressivestages` and `minecraft`
GameTest namespaces enabled. Run through the owned dedicated server console after readiness:

```text
fill -2 180 -2 12 195 15 air
fill -2 179 -2 12 179 15 stone
execute positioned 0 180 0 run test run sessionapplyrejectsexternalchangesbeforereload
```

Prepare this platform only inside the disposable test world. The command selects terrain height,
so its supplied Y coordinate alone does not determine the structure position. Keep the test area
loaded and inspect the actual structure metadata and lime stained glass marker before teardown.

Also run `sessionapplyrejectsunreviewedchanges`, `applywritesreloadsandrestorescanonicalrules`,
`invalidrecipealiasdoesnotmutateliveconfiguration` and
`contextapplypreservessourceandrejectsoverflow`. These cover ordinary successful apply, a stale
review, invalid configuration and source preserving context edits. The direct apply fixture must
start with a snapshot of the actual live files, as a real editor session does.

The external edit fixture discards its session and draft, removes its unique stage and any
transaction backup, reloads the baseline and discards its controlled server player in `finally`.
After the suite, stop the owned server and remove its disposable runtime and scratch output.
Retain only sanitized observations and artifact identities in the
test report. These checks do not establish browser
presentation or protection against concurrent writes by another filesystem process.
