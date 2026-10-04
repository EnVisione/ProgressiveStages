# Purchase and structure lease fixture

This disposable server fixture registers the public `StructureContextProvider` contract. It
supplies one assigned arena and read only status reporting. Normal ProgressiveStages session
tracking grants and removes the temporary source when the player walks across the arena boundary.
The fixture never grants the purchasable stage, deducts payment, delivers rewards, stores receipts,
or handles purchase packets. The ordinary client button and server handler perform those actions.

The `open` command calls `PSKubeBindings.openGui` directly. This verifies that Java binding and
its actual client transport and presentation. It does not load the KubeJS engine or prove an
external structure provider's implementation. Keep this fixture out of production packages.

## Build and install

Use Java 21, Minecraft 1.21.1, exact NeoForge 21.1.248, and the already verified
`build/libs/progressivestages-3.0.5.jar`. Register a disposable output directory before compilation.
Run `./gradlew --no-daemon prepareServerRun compileJava` in the active checkout to make the
mapped loader artifact and `build/moddev/serverLegacyClasspath.txt` available. Inspect the task
graph first; this preparation must not launch a client.

Compile `PurchaseLeaseFixture.java` with `javac -proc:none`. Its classpath comprises the verified
ProgressiveStages JAR, `build/moddev/artifacts/neoforge-21.1.248.jar`, and every path in
`build/moddev/serverLegacyClasspath.txt`. Write classes only to the registered disposable output.
Package those classes with the supplied `neoforge.mods.toml` at `META-INF/neoforge.mods.toml` in a
separate `purchase-fixture.jar`. Record the source and binary hashes. The fixture is server only;
install it beside the verified ProgressiveStages JAR only in the disposable dedicated runtime.
The laptop receives the identical ProgressiveStages JAR and no fixture JAR.

Copy `access.toml` and `lease.toml` into the runtime's `config/progressivestages/stages` directory.
Use a disposable world with no other stage definitions. The lease costs ten levels and four bread,
rewards one diamond, and refunds 50 percent on explicit revocation. The deliberately combined
lease and permanent progression configuration produces a warning; it remains accepted and is
necessary to exercise explicit independent acquisition while a lease is active.

## Runtime procedure

Start an authenticated, loopback bound, no GUI server on the headless host after reading back
`eula=true`. Connect the matching isolated laptop client over the authorized private route.
Before input, verify the owned window, discrete GPU renderer, exact endpoint and player, master
volume zero, and the exact application playback stream's muted state. Continue monitoring that
stream through restarts and stop the owned client if its identity or mute state becomes uncertain.

The following console setup uses the acceptance player's name. Substitute the actual authenticated
fixture player consistently. Do not grant the purchasable stage from the console.

```text
gamerule doDaylightCycle false
gamerule doWeatherCycle false
time set day
fill -5 200 -5 15 200 20 minecraft:smooth_stone
fill 0 200 0 10 200 10 minecraft:lime_concrete
setworldspawn 5 201 14
clear EnVyOnMyMind
experience set EnVyOnMyMind 20 levels
give EnVyOnMyMind minecraft:bread 8
gamemode survival EnVyOnMyMind
stage grant EnVyOnMyMind purchase_fixture_access
purchasefixture assign EnVyOnMyMind
tp EnVyOnMyMind 5 201 14 180 0
purchasefixture status EnVyOnMyMind
```

1. Outside the arena, require no effective or independent profession, no sources or receipt,
   twenty levels, eight bread and no diamond. Walk forward into the green arena using actual
   client input. Require effective access, only the temporary source, and unchanged balances.
2. Run `purchasefixture open EnVyOnMyMind` while the map is closed. Select the profession with
   an actual click. It must show **Unlocked** and an enabled **Purchase** offer.
3. Set the player's experience to five levels and request fresh data with the same open command.
   Click the disabled offer. Require no payment, reward, receipt or independent source.
4. Restore twenty levels and request fresh data. Click Purchase. Require ten remaining levels,
   four bread, one diamond, independent and temporary sources, and a receipt. The offer disappears.
   Clicking its former location must not repeat payment or reward.
5. Close the details panel and then the map. Walk backward out of the arena. Verify the actual
   position and zero session participants with the ordinary structure session diagnostics.
   Require only independent ownership and unchanged balances and receipt. Reopen the map and
   verify the unlocked state with no purchase offer.
6. Stop the client and server normally. Restart the same world with identical artifacts and
   reconnect the matching muted client. The assignment is intentionally not persisted by this
   fixture. Verify independent ownership, the receipt and balances survived. Reopen the map and
   verify there is still no purchase offer.
7. Keep the selected details panel open and run
   `stage revoke EnVyOnMyMind purchase_fixture_lease`. Require no sources or receipt, fifteen
   levels, six bread and one diamond. Repeat revocation and require unchanged balances.
   The visible map must update to **Ready** and offer **Unlock** without explicit reopening.
   Record a failure if the header, node, details or button disagree with authoritative state.
   An explicit refresh may localize the defect but does not satisfy this live update gate.

## Evidence and cleanup

During laptop testing, check purchase, lease withdrawal, restart persistence, refund count,
and live map updates after grant and revoke. Repeat the checks after reconnect and after
changing a source without changing effective ownership.

After the final consumer, preserve only the fixture sources, minimal screenshots, hashes and
structured results. Stop exact owned clients, watchers, launchers, servers and tunnel; verify
process, playback stream and listener absence. Restore a reused instance's original game directory
and configuration. Remove disposable worlds, runtime files, fixture classes and JAR, temporary
build output and scratch on both hosts. Preserve preexisting instances, source, candidate artifacts,
shared dependencies and unrelated worktrees. Never traverse a shared library symlink during cleanup.
