# KubeJS ownership regression

`actualKubeScriptsRespectPersonalTeamAndServerOwnership` exercises an actual KubeJS server script,
the `ProgressiveStages` global binding, native `player.stages` methods, and Java API calls made
through Rhino. It uses the normal FTB Teams manager to create two members of one party.

## Dependencies and setup

Use the project Java 21 wrapper build with Minecraft 1.21.1 and NeoForge 21.1.248. Install these
exact optional artifacts in an isolated dedicated server runtime:

| Artifact | Version |
|---|---|
| KubeJS NeoForge | `2101.7.2-build.348` |
| Rhino | `2101.2.7-build.81` |
| FTB Teams NeoForge | `2101.1.9` |
| FTB Library NeoForge | `2101.1.30` |
| Architectury NeoForge | `13.0.8` |

Copy [ownership.js](../../../src/test/resources/kubejs/ownership.js) into the owned runtime at
`kubejs/server_scripts/ownership.js`. Do not install it in a personal or production server. Set
`enabled` to `false` in `kubejs/config/web_server.json` before launch. The test needs no KubeJS web
listener. Verify artifact identities, dependency metadata, the dedicated launch target, EULA
acceptance, and the private test endpoint before starting the server.

Enable the `progressivestages,minecraft` GameTest namespaces. Wait for dedicated readiness and
confirm the actual server script loaded with zero script errors. Run this command from the owned
server console at an empty test position:

```text
execute positioned 0 180 0 run test run actualkubescriptsrespectpersonalteamandserverownership
```

Inspect the matching GameTest structure metadata and fresh success glass. Do not count the
optional dependency absence path as integration evidence. With both KubeJS and FTB Teams
installed, a missing script, script exception, or duplicate callback fails the test.

Run once immediately after startup, before `/reload`. Then reload the server scripts through
`reload` and run the test again. Repeat after another reload or server restart. A cold startup
result is necessary because loading scripts and creating their server thread context happen on
different threads. Successful verification only after a manual reload would miss this regression.

## Assertions and boundaries

The script checks all of these contracts:

1. Personal grants, queries, and revocation affect the addressed actor while teammates remain
   independent. A duplicate grant returns no change.
2. Owner queries identify personal, team, and server storage correctly.
3. Legacy UUID grant, query, and revoke calls retain team storage. They do not infer a personal
   actor from the UUID or overwrite personal ownership when the UUID values coincide.
4. Native `player.stages.add`, `has`, and `remove` use ProgressiveStages personal ownership.
5. Team grants and revocation reach both members, while server grants and revocation use the
   global owner.
6. Explicit actor Java mutation calls made from the script report the resolved owner and produce
   the correct effective view for each actor in all three scopes. UUID actor resolution and
   snapshot queries remain callable through Rhino without legacy team overload ambiguity.
7. Exactly one callback completes all assertions for each invocation, including after reload.
8. With the first actor removed from the connected player lookup, actual Rhino calls resolve
   personal, native FTB team, and server owners, grant and revoke each entitlement, and distinguish
   duplicate operations. Fresh snapshots reflect each change while retained snapshots stay immutable.
9. `NativeEvents.onEvent` receives exactly one `StageActorChangeEvent` per changed offline operation,
   with the original actor context, stage, and script cause. Committed subscriptions run on the server
   thread and report strictly increasing revisions. Legacy connected player callbacks remain silent.
10. Offline grants reserve two, three, and five XP levels for the initiating actor. A teammate's
    return receives none. Returning the original actor delivers ten levels once, even after those
    independently acquired stages were revoked. Repeated synchronization does not deliver again or
    replay either native actor events or legacy player callbacks.
11. A native FTB party leave invalidates a captured actor context even when its personal owner
    remains the same. Both grant and revoke reject that context without another committed result
    or acquisition event. The script restores the teammate's membership afterward.

The fixture uses ordinary server player objects with an embedded transport and a test packet sink.
KubeJS deliberately returns `NoStages` for NeoForge fake players before posting its stage creation
event, so fake players cannot test the native stage adapter. These ordinary objects are temporarily
entered in the UUID lookup for actor API resolution. They are not authenticated, connected clients.
The fixture proves actual script execution, native FTB owner resolution, offline actor mutations,
event delivery, and authoritative reward state. It does not prove authenticated login, packet
delivery, client rendering, LuckPerms qualification, or crash recovery across separate player saves.

The outer fixture restores stage definitions, ownership attachments, clocks, and provider state.
The script fixture removes its UUID lookup entries and releases both embedded channels. During a
bounded repeated suite, retain the real FTB manager cleanup results until inspected, then remove
only the two test identities' owned `world/ftbteams/deleted` tombstones before reusing those fixed
identities. FTB's native deletion moves records without replacing an existing tombstone. Record any
cleanup error rather than accepting a green marker alone.

Stop the owned server after the final consumer, verify process and listener exit, and remove its
disposable runtime, script copy, logs, worlds, and temporary output. Preserve only the required
sanitized results and artifact identities in the test report.
