# Selling Bin transaction GameTests

These opt in dedicated server tests exercise the installed Selling Bin implementation. They
add no production dependency and do not simulate its inventory or economy. The suite is
registered only when `progressivestages.sellingBinGameTests=true`. Enabling it without the
Selling Bin mod fails explicitly. A normal run with the option absent does not register these
tests and must not count that absence as passing Selling Bin coverage.

Use Minecraft 1.21.1, NeoForge 21.1.248, Java 21, and Selling Bin 1.6. Keep the server and every
client artifact manifest separate from the mod JAR hash. Never install the fixture datapack in
a personal or production world.

## Fixture setup

1. Allocate an owned disposable dedicated server directory under the project anchor. Record
   preexisting files, process ownership and exact cleanup targets. Keep the listener private.
2. Place the verified Selling Bin artifact in that runtime's `mods` directory. Its bundled
   libraries resolve through the normal loader. Do not add Selling Bin to the production build.
3. Copy [`selling_bin_datapack`](../../../src/test/resources/selling_bin_datapack) into the disposable
   world's `datapacks` directory before loading it. This pack prices bread at 10, carrots at 7,
   iron chestplates at 30, and emerald currency at 1. Processors are empty for deterministic
   values. The upstream artifact has empty default value and currency tables.
4. Set and read back `eula=true`, retain authentication for client connections, and enable
   GameTest namespaces `progressivestages,minecraft`. These tests use `minecraft:igloo/top`.
5. Add JVM property `progressivestages.sellingBinGameTests=true` to that owned server. Inspect
   the actual task graph and launch arguments before starting it with `--nogui`. A temporary
   Gradle init can set the existing server run's `gameDirectory`, the two system properties and
   `standardInput = System.in`. Keep its terminal input available for an ordinary `stop`.
6. Confirm exact loader and mod versions, loaded fixture pack and server readiness before tests.

## Cases and assertions

Run each exact case through the server console's `test run` command. Each generated test has
its own batch so delayed assertions cannot overlap a different case's stage registry changes.
Before accepting a case, verify its current success result rather than the previous case's
beacon. A failed or timed out case is not a pass.

| Case names | Actual behavior checked |
| --- | --- |
| `selling_bin_armor_manual`, `selling_bin_armor_automatic` | The original `tag:c:armors` held item rule denies the actual valued armor on both bin parts, a grant permits insertion and sale, and revoke denies the offhand attempt. |
| `selling_bin_wildcard_manual`, `selling_bin_wildcard_automatic` | The original standalone `all:*` held item rule denies valued bread without requiring a GUI insertion rule. Grant and revoke change the real insertion result. |
| `selling_bin_selective_manual`, `selling_bin_selective_automatic` | Paired bread rules deny direct, normal carried stack and shift click transfers. Carrots remain sellable without the bread stage. Grant permits three bread through the real menu, and revoke blocks new bread through that same open menu. |
| `selling_bin_partial_manual`, `selling_bin_partial_automatic` | A denied merge preserves three held bread and a previously accepted input. Only that prior deposit may sell. After grant, a 63 item input accepts exactly one bread, preserves the two that do not fit, and sells only its accepted contents. |

Automatic cases use the bin's actual block entity ticker. Manual cases use its real sell all
menu button. Assertions wait at least 20 server ticks and compare held counts, bin input and
the bin's total stored plus output currency value. Each test places the actual two block bin,
uses `ServerPlayerGameMode.useItemOn` for both physical parts, and creates the real bin menu
for container transactions. The test actor is a detached server player; no client input,
rendering, networking or authentication is simulated as acceptance evidence.

Repeat the partial automatic case when checking timing or stack conservation changes. Record
source, fixture, loader and artifact hashes, every case result and any loader diagnostics.
Stop the owned server, confirm process exit, preserve only the required sanitized evidence and
remove its runtime, worlds, logs, temporary launch configuration and newly created build output.

## Evidence boundary

These tests prove only their listed server routes. Actual laptop input, repeated click and
prediction behavior, GUI opening policy, drag and hotbar swap behavior in the bin, multiple
players, custom components, and reconnect still require the corresponding acceptance matrix.
Generic container tests are supporting checks and do not cover the actual Selling Bin
behavior described above.

For laptop acceptance, also test an empty hand menu denial for the whole `all:*` rule, armor
denial for `tag:c:armors`, and selective bread insertion while the real menu remains open.
