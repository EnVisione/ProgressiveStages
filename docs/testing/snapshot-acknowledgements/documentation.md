# Snapshot Acknowledgement and Recovery Tests

These dedicated server GameTests exercise `NetworkHandler.handleClientSnapshotAck` through its
payload context with controlled nonoperator server players. They use the actual snapshot sender
to establish offers. They do not establish client rendering, transport delivery or reconnect
behavior from a laptop.

`snapshotacknowledgementsrequireanofferedrevision` checks unsolicited packets, an incorrect
checksum, another player using the offer, an enforcement policy change without a revision
change, valid acceptance, a new compiled revision, a stale packet and disconnect cleanup.
The fixture restores the original compiled snapshot, enforcement policy and snapshot history,
then clears only its players' network state and discards those players in `finally`.

`snapshotacknowledgementburstsavoidsnapshotreconstruction` first offers the current snapshot,
warms the actual handler with 128 calls, then measures 1,000 repeated valid acknowledgements.
It requires less than 1 MiB of Java thread allocation for that burst and retains a valid
acknowledged base. This is a regression ceiling for redundant reconstruction, not a configurable
packet rate limit or a guarantee about other request handlers. The fixture records allocation
and elapsed time, restores the JVM allocation measurement setting and snapshot history, and
removes its player's state in `finally`.

`snapshotrequestburstsdonotrebuildeveryresponse` measures 1,000 recovery requests after 128 warmup
calls. The first request must send an actual offer; the burst must keep that offer and allocate
less than 1 MiB on the Java server thread. It restores allocation measurement, history and the
test player's state in `finally`.

`pendingsnapshotrecoveryusescurrentstateonserverticks` establishes independent immediate offers
for two controlled players and acknowledges one base. It queues delta, full and subsequent delta
requests, proves ordinary server synchronization remains immediate, and disconnects the second
player before its queued work runs. After changing the compiled revision, it waits 25 actual
server ticks and verifies that the pending response uses the current revision, clears the old
acknowledged base and does not recreate the disconnected player's offer. The fixture temporarily
registers only its controlled players in the server's UUID lookup so the normal tick callback
can resolve them. It restores that lookup, compiled snapshot, history and player state on success
or failure. No laptop transport delivery is inferred from these controlled players.

`SnapshotRequestQueueTest` checks the exact 20 tick boundary, repeated bursts without deadline
extension, full request precedence, latest nonzero base, independent players, idle expiration,
disconnect, shutdown and negative revision handling. No clock sleeps or external provider is
required for these unit tests.

Use Java 21, Minecraft 1.21.1 and NeoForge 21.1.248 with the `progressivestages` and `minecraft`
GameTest namespaces enabled. After the owned dedicated server is ready, run each test separately:

```text
fill -2 180 -2 12 215 15 air
fill -2 179 -2 12 179 15 stone
execute positioned 0 180 0 run test run snapshotacknowledgementsrequireanofferedrevision
```

After inspecting that test's actual structure metadata and result marker, clear the test area
and substitute `snapshotacknowledgementburstsavoidsnapshotreconstruction` in the final command.
Run each of the two recovery tests the same way, separately from the acknowledgement tests.
The platform is only for a disposable world. The test framework chooses placement from the
terrain height, so a command's Y coordinate alone does not prove the result location.

Stop the owned server after the suite and remove its exact runtime, worlds, logs and temporary
build outputs after recording sanitized results and artifact identities in the test report.
Preserve shared dependency caches and unrelated processes. Review other packet resource
limits when changing payload sizes or recovery behavior.


## Client assembly byte limits

`ClientSnapshotAssemblerTest.rejectsExcessBytesBeforeRetainingAllAdvertisedChunks` announces
4096 chunks with only three compressed bytes. After accepting two bytes, an identical duplicate
and an unrelated revision must consume no further budget. A second unique two byte chunk must
reject immediately, without waiting for the other advertised chunks. The scaled fixture proves
the admission condition without allocating a large malicious snapshot.

`ignoresDuplicateChunksWithoutConsumingTheRemainingByteBudget` completes a real compressed
snapshot in reverse chunk order with a duplicate, using exactly its advertised byte count.
Existing tests retain protocol rejection, checksum verification, valid deltas and exact base
requirements. These are pure Java tests of the assembly and cache boundaries, not evidence of
packet transport, a rendered client, disconnect behavior or the remaining mixed gameplay matrix.
