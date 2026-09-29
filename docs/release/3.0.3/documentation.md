# ProgressiveStages 3.0.3

ProgressiveStages 3.0.3 improves enchantment control, polishes the in game progression map, and
closes several enforcement and rendering gaps found during 3.0 testing.

## Highlights

- **Stage aware enchantment generation.** `[enchants].max_levels` and
  `[enchants].selection_weights` now shape enchanting table choices and player aware enchanted loot
  before enchantments are applied. Set a maximum level to cap retained and generated levels. Set a
  maximum of zero to remove the enchantment. Set a selection weight of zero to prevent new rolls
  without stripping existing items. The normal enchantment strip remains a final safety net.
- **Easy builder enchantment controls.** The localhost editor exposes maximum levels and selection
  weights in the Rules tab. Search the live server registry, choose an exact enchantment, and edit
  either control without touching TOML.
- **Progression map polish.** The in game advancement style map has clearer stage names, better
  spacing, shorter dividers, structured equipment and affinity previews, exact item icons, cycling
  tag icons, focused branch highlighting, and a more compact inspector.
- **Vanilla style inventory entry point.** The progression button uses a parchment map emblem and
  matching vanilla button states with a wider default gap from the recipe book button. Existing
  settings for visibility, position, button size, and icon size remain supported.

## Fixes

- The open category list now renders above every stage frame and item icon. Nodes positioned under
  the list can no longer bleed through the menu.
- Smithing upgrades now enforce locked recipe identifiers, recipe outputs, result items, and
  transitive ingredients. Result pickup is rejected on the server even if another mod redraws the
  smithing output.
- Structure aware entity rules now read an immutable session snapshot during asynchronous chunk
  generation. Ocean ruins and similar structures no longer access server thread only session state
  from a chunk worker.
- The localhost editor no longer shows the redundant `Arrange and save` button. Dragging still
  stores individual coordinates, `Use automatic layout` still removes manual coordinates, and
  `Apply changes` remains the single publication flow.

## Compatibility

- Minecraft 1.21.1.
- NeoForge 21.1.219.
- Java 21.
- Client and server installation required.
- Existing 3.0 stage files and configuration remain compatible. No migration is required.

## Verification

The release passed the React test and production build, the complete Java test and Gradle build,
dedicated server startup, virtual display client startup, browser verification of the localhost
editor, transformed mixin target loading, and final JAR inspection. The reported smithing and
progression menu cases were also confirmed in game before release approval.

## Published files

- [CurseForge file 8621291](https://www.curseforge.com/minecraft/mc-mods/progressivestages/files/8621291)
- [Modrinth version 3.0.3](https://modrinth.com/mod/progressivestages/version/KxB8xxEe)

Both platforms received the same `progressivestages-3.0.3.jar` artifact. The file is 1,758,353 bytes
and has SHA-512
`2d9561af1f982d1fb1f9678df248bc240c4f2247811cd4fe7ae73a69606a8d931220c5ba07ccf01dbb1ee67efda1fdcbb8f3e967ffbeb1df6d48f70fdde6dca2`.
CurseForge may delay public access while the accepted file completes platform moderation and cache
publication.
