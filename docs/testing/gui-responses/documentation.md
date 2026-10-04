# GUI response regression

`GuiResponseGameTests` exercises the actual server GUI request handler, rejected purchase handler,
public command dispatcher, script binding and server tick callback. A fixture connection captures outbound
`StageGuiDataPayload` packets before transport. It does not render a screen or establish client
acceptance.

The first test warms the GUI handler with 128 requests, then sends 1,000 requests alternating
between a GUI packet, purchase of a nonexistent stage and the public `stage gui` command at
permission level 0. All three entry points must share one immediate response. Thread allocation
for the measured burst must stay below 8 MiB, including reflection and command dispatch overhead.
This is a regression bound for redundant view reconstruction, not a general network benchmark.

The second test gives two independent players one immediate response each and queues another.
It adds a definition before the pending send, disconnects the second player, and waits for actual
server ticks. The first player must receive exactly one more response containing the new definition.
The disconnected player must receive none. Clearing the first player's connection state must allow
a fresh immediate response. Fixture players, queued requests and definitions are restored afterward.

The third test invokes `stage gui` and `pstages` with an optional opening channel negotiated.
Both explicit commands and `ProgressiveStages.openGui(player)` must send an opening instruction,
including during the data cooldown.
An ordinary GUI request and rejected purchase must not send that instruction, and the queued
response must contain data only. A second peer without the optional channel must receive the
legacy data payload without an unsupported opening packet for both command and script calls.

Use a disposable no GUI development server on Java 21, Minecraft 1.21.1 and NeoForge 21.1.248,
with the `progressivestages` and `minecraft` GameTest namespaces enabled. The production launch
does not register these GameTests merely because `neoforge.enableGameTest` is true. Prepare the
repository's `forgeserverdev` launch and its complete runtime classpath. Bind only to the owned
private endpoint and verify readiness before executing each test separately:

```text
fill -2 180 -2 12 215 15 air
fill -2 179 -2 12 179 15 stone
execute positioned 0 180 0 run test run guipacketpurchaseandcommandburstsshareoneresponsebudget
```

With the platform above and no rotation, inspect `data get block 0 180 3` for the exact test name.
Require `minecraft:lime_stained_glass` at `-1 179 2` after the scheduled assertions finish. The
command places the structure using the world surface and a Z offset, not the source position
alone. Clear and rebuild the same disposable platform before running `queuedguiresponsesusecurrentdataandstopafterdisconnect` and
`explicitguicommandsopenwhilequeuedresponsesonlyrefresh`. The platform
fixes terrain dependent placement; the command's Y coordinate alone is not a result oracle.

Keep Minecraft client GUI presentation and reconnect acceptance separate. After the bounded
server suite, stop the exact owned process and remove its runtime and temporary build output.
Record the tested source and artifact identities, measurements, results, and cleanup status
in the test report.

## Laptop presentation regression

Use matching client and server artifacts. Verify the owned laptop window, NVIDIA renderer,
muted application stream, exact private endpoint and joined player before input. Open the GUI
twice within one response interval, close it with Escape, and observe it after the interval.
The older client reopens on the queued response; a matching corrected client remains in the
world. Deliberately reopen through an alias during the same cooldown and verify the GUI appears.
Also exercise the configured keybind after reconnect. Capture only the owned game window.
A reduced tick rate may make the close interval observable, but record that fixture rate and
restore normal ticks before the remaining tests. This case does not prove the full purchase,
provider, builder or interaction acceptance matrix.

## Live stage synchronization

Keep a stage selected while its ownership changes through a normal command or provider update.
The map must update its owned count, node style and details together. With matching peers, its
purchase offer must converge through the existing server response queue. Repeat with several
updates in one client tick, with a full sync whose effective stages are unchanged after an
independent grant beside temporary access, and with changed stage definitions. Verify the map
keeps its camera position and the selected stage when that stage remains visible.

Close the map before a deferred offer response arrives and require that it stays closed. A map
closed before the next client tick must not initiate an automatic request. Reconnect and repeat
the grant and revoke case. The [purchase lease fixture](../fixtures/purchase-lease/README.md) retains
the real client baseline where the count changed but the cached details remained unlocked.
