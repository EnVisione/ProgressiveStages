# Beginner Pack

This is a deliberately small ProgressiveStages pack for learning and smoke testing. Every TOML
inside `stages` is parsed by `BeginnerExamplePackTest` during the Gradle test suite.

## Install

1. Stop the game or server.
2. Back up `config/progressivestages/stages`.
3. Copy the three files in this directory's `stages` folder into the active
   `config/progressivestages/stages` folder.
4. Start the game or server.
5. Run `/progressivestages validate`.
6. Run `/progressivestages reload` if the files were copied while the server was already running.

## Expected progression

1. `stone_age` has no dependency and is suitable as the configured starting stage.
2. `iron_age` requires Stone Age and unlocks when the player completes the vanilla Smelt Iron
   advancement.
3. `diamond_age` requires Iron Age and unlocks after the player mines one diamond ore.

The pack intentionally does not lock the action required by its own triggers. Iron smelting stays
possible before Iron Age, and diamond ore stays mineable before Diamond Age. The stages lock tools
and advanced blocks that become useful after those milestones.

## Smoke test

Use the exact before and after pattern:

1. Revoke Iron Age and Diamond Age.
2. Confirm an iron pickaxe is blocked.
3. Smelt iron and confirm Iron Age grants.
4. Confirm the iron pickaxe works.
5. Confirm a diamond pickaxe is still blocked.
6. Mine one diamond ore and confirm Diamond Age grants.
7. Confirm the diamond pickaxe works.
8. Revoke Diamond Age and confirm the diamond pickaxe is blocked again.

See [the beginner guide](../../GETTING_STARTED.md) for explanations and
[the testing handbook](../../TESTING.md) for the complete release matrix.
