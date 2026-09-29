# Build and test ProgressiveStages

## Editor checks

From the repository root, use Node.js 22 and the `editor-ui` lockfile to verify the web editor.
The `--prefix` option keeps every command rooted in this checkout:

```sh
npm --prefix editor-ui ci
npm --prefix editor-ui run check
npm --prefix editor-ui test
npm --prefix editor-ui run build
```

`npm run check` validates TypeScript. `npm test` runs the editor regression suite. `npm run build`
updates the packaged editor assets used by the mod.

## Java checks

From the repository root, use Java 21 with the Gradle 8.8 wrapper:

```sh
./gradlew test
./gradlew build
```

Run the unit and integration tests after changing a parser, compiler, configuration model, network
contract, or enforcement path. Build the mod after the editor assets are generated, then inspect
the JAR to confirm it contains the expected resources.

## GameTests

Inspect the GameTest task graph before running a world-dependent suite. Use a dedicated server task
that does not start a client or require a display. Give each test run its own game directory and
retain its logs until the result has been checked. Do not use a shared development world as the
GameTest data directory.

GameTests cover server behavior that a unit test cannot represent, including block interactions,
item drops, registry and tag matching, stage ownership, and reloads. Keep assertions tied to the
actual server event or saved state being tested.

## Client and editor checks

Use Trident for Minecraft client checks. Compare the client and dedicated server mod files by hash
before connecting. Preserve the exact editor draft used by a live check so the saved TOML, server
behavior, and client display can be compared.

Use the project's Brave browser extension for an editor check when browser behavior is part of the
change. Test the form through its visible controls, save and reopen the draft, and confirm the
resulting configuration rather than treating a successful browser load as proof of an applied
change.
