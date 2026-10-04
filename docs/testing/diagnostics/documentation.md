# Diagnostic Capture Performance

`captureoverheadstaysboundedacrosscategories` measures the public diagnostic recording paths
on the dedicated server thread. Run it on an isolated server with Minecraft 1.21.1, NeoForge
21.1.248, Java 21, and the exact candidate classes. No optional provider is required.

The fixture covers interactions, progression, inbound permission observations, command
permission observations, and editor observations. Interaction lists contain 32 matched and
missing stages. Progression compares 31 and 32 effective stages. The command context is parsed
from `time query gametime` without executing it. The editor fixture completes a validation
observation with immutable source maps. These are recording stimuli, not gameplay or browser
acceptance.

## Measurement

Each category receives one discarded 1,000 attempt warmup and three measured 1,000 attempt
rounds. Each round contains fifty independent windows of twenty accepted records. The real
capture manager starts its writer and stops at its unchanged twenty event rate limit. Before
starting another window, the fixture waits across server ticks for the writer to drain,
checks every sequence number, capture ID and category, and deletes that exact capture file.
It rejects missing or dropped records. This prevents inactive calls after a limit from being
counted as enabled capture work.

Each enabled window is paired with twenty calls to the same public recording path while
capture is disabled. Arguments and operation callbacks are prepared outside the timed region.
The editor observation is constructed only after the recording path accepts the operation.
Server thread CPU time, elapsed time, and allocated bytes come from Java's enabled thread
counters and `System.nanoTime`. Each measured round must add at most 100,000 nanoseconds per
attempt for both CPU and elapsed time, with zero bytes allocated by the disabled path.
The fixture fails if the required thread counters are unavailable instead of silently
omitting allocation or CPU measurement.

Capture startup, configuration snapshot construction, asynchronous header hashing and writing,
output inspection, and tick scheduling are outside the timed recording region. This measures
per attempt server thread overhead, not capture startup cost, total disk bandwidth, end to end
gameplay latency, or performance with an arbitrary modpack or source document size. The writer
runs normally during the enabled window, so contention remains observable.

## Run and Cleanup

Start an owned dedicated server through NeoForge's development launch without a GUI and wait
for readiness. NeoForge 21.1.248 disables GameTests in a production launch. Compare the tested
class bytes with the packaged candidate and verify production startup separately. Enable the
`progressivestages,minecraft` GameTest namespaces. Use the owned console to prepare an empty
test location and run the exact test, for example:

```text
fill -2 179 -2 12 190 15 air
fill -2 179 -2 12 179 15 stone
execute positioned 0 180 0 run test run captureoverheadstaysboundedacrosscategories
```

The test requires no connected players and no active or draining capture. It temporarily
registers one server player object in the UUID lookup, then removes that exact object when
the run ends. This permits the public capture manager to resolve its target; it does not
prove authenticated login, command authorization, network delivery, or disconnect handling.
Run this benchmark independently of tests that also own the global capture manager.

Record the measured results, source and artifact identities, runtime details, and cleanup
status in the test report.
Do not retain all one thousand transient capture files. After the bounded suite, stop the
owned server, confirm its writer and server processes exited, and remove the owned runtime,
world, remaining logs and test build output while preserving source, shared dependencies,
and requested candidate artifacts. A passing performance result does not close the separate
capture control, lifecycle, privacy, browser, provider, and real client acceptance gates.

A desktop run should also check operator controls, denial for players without permission,
manual stop, reload, timeout, disconnect, and process restart.

The allocation probe warms every capture operation before measured rounds and measures allocations
separately from CPU and wall time. An empty operation must allocate zero bytes, and an escaping
allocation control must be detected on every attempt. The disabled capture assertion remains
exactly zero. Resetting a drained rate limited capture must release its inactive runtime reference
and return an off status while preserving any writer that is still draining.
