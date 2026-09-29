# ProgressiveStages 3.1.0 release guide

This guide documents the 3.1.0 changes for Minecraft 1.21.1 and NeoForge 21.1. Install the same
ProgressiveStages release on the server and every client. The server, editor, and player guide must
use matching mod versions; mixed-version setups are not supported.

For installation and the complete TOML reference, see the [project README](../../../README.md) and
[configuration manual](../../../DOCUMENTATION.md). The runnable files linked below are parsed by
the project test suite.

## What changes for players

The player progression screen remains one advancement-style map. It does not add a guide tab or
advancement-style headers. Select a stage, then use the gold `?` inside that stage's inspector,
beside the `X` close button. The question mark has no visible text; hover or keyboard-focus it to
see `How to unlock <stage name>`. The `X` tooltip says `Close`. The help panel closes when the player
selects another stage or changes the search or category filter. Longer help can scroll with the
mouse wheel while the pointer is over the details; a scrollbar appears only when content overflows.

The guide keybind is separate and starts unbound. It opens the next eligible suggestion in the same
map. The server owns whether help is enabled, what it says, and which stages are eligible. A stage's
help disappears when the player owns that stage. Hiding a stage or concealing it with its reveal
setting also keeps it out of player-facing guide suggestions.

## Author stage guidance

In the web editor, select a stage and use **Setup → What to do next** to write the unlock steps,
next steps, and location. The per-stage switch controls that stage's question mark and keybind
eligibility. It defaults to enabled. The global switch in the editor's main **Settings** view is the
master control:

```toml
[general]
enable_stage_guide = true
```

When the global switch is false, no stage guide or guide keybind opens. Turning it back on restores
each stage's saved choice. The setting defaults to true.

The equivalent stage-file form is:

```toml
[guide]
enabled = true
how_to_unlock = "Complete the listed requirements to unlock {stage_name}."
next_steps = "Use your new tools to prepare for the next age."
where_to_find = "Smelt iron in a furnace."
recommendation = "auto"
```

Each text field accepts up to 2048 Unicode code points and 8 KiB of UTF 8 data. The text is inert:
it cannot run commands, grant stages, or change rules. The supported placeholders are
`{stage_name}`, `{stage_requirements}`, `{remaining_requirements}`, and `{stage_progress}`. Unknown
placeholders remain visible and produce an editor warning. Double braces display literal braces,
for example `{{stage_name}}` displays `{stage_name}`. If a field includes `{stage_requirements}`, the
full visible unlock summary appears there instead of repeating it as a separate block. The server
uses `Unavailable` if it cannot safely confirm the complete route; do not describe that value as
proof that no requirements remain.

Recommendations can be `auto`, `include`, or `exclude`. Hidden or concealed stages are never listed.
The guide and editor Player preview only present information; preview states do not grant, revoke,
purchase, or activate a stage. The in-game view always names its current selection as
`How to unlock <stage name>`.

The [runnable guide example](../../../examples/3.1.0/stages/iron_guide.toml) is visible while locked,
uses the vanilla iron-smelting advancement for its unlock, and includes a conditional rule whose
`stage_state = "owned"` becomes active only after the stage is acquired. The main guide switch is
shown in [`progressivestages.toml`](../../../examples/3.1.0/progressivestages.toml).

## Editor help, translation, and preview

Common labels use plain names such as **Stage name**, **Stage icon**, and **Required stages**. Hover
or keyboard-focus the nearby question mark for a short explanation and example. Actions also have
hover and focus help. On the progression graph, select a stage to use nearby edit/connect/duplicate/
delete actions, or right-click the stage for its actions. The browser editor keeps its existing
sections and save/review flow.

Use your browser's page-translation control if you want translated labels and help. ProgressiveStages
does not include a Google Translate button or send configuration to a translation provider itself.
An external browser translation provider may process visible page content, including text currently
entered in fields. Do not translate sensitive draft text. After translation, verify every stage ID,
registry ID, command, file path, selector, TOML value, and error message in the original language
before reviewing or applying a change.

The **Player UI** editor preview shows the current draft's player-facing stage name, icon, description,
guide, category, search, reveal, and dependency behavior. **Locked**, **Requirements met**, and
**Unlocked** are sample states. Hidden stages are absent from player preview surfaces; reveal and
rule activation remain separate. Player simulation does not grant or alter ownership. Click a visible
stage to open its matching inspector, then use its nearby edit action to change that stage. Preview
edits update the draft only. Use validation, review, and **Apply changes** to publish an accepted
server draft.

## Structure access and protection

Use a structure entry stage and a separate protection stage when one stage should open a door while
another continues to prevent building. `entry_allowed = true` removes only its own stage's entry
denial; it does not create a global ALLOW. The protection stage can keep block placement, breaking,
explosion, and spawning rules active while unowned. Those flags are attributed to the selected
structure and stage. Explosion and mob-spawn checks do not invent a player owner.

See [structure rules and priorities](../../structures/rules/documentation.md) for the priority
cascade, explicit ALLOW behavior, overlap and provider boundaries. Runnable files:

- [Entry stage](../../../examples/3.1.0/stages/stronghold_access.toml)
- [Protection stage](../../../examples/3.1.0/stages/stronghold_protection.toml)
- [Explicit permit rule](../../../examples/3.1.0/stages/stronghold_allow.toml)

## Block overrides

Use **Block locks** to restrict actions such as breaking or placing a block. Use **Block overrides**
to change how a matching block looks and what it drops while its stage is missing. Overrides work
for any block, not only ores; they do not stop placement, breaking, or interaction by themselves.

New rows use `[[blocks.overrides]]`. Existing `[[ores.overrides]]` rows remain a supported alias and
stay in their original table when edited. `[ores].locked` is not a block lock or override. Selectors
can be one exact block ID, a list of IDs, a block tag, or `mod:<namespace>`. A `mod:` selector
includes every block from that mod, including machines and other non-ore blocks. A tag works only
when it exists in the active pack and contains the blocks you intend. Each row chooses the displayed
block and dropped item separately. See [Block overrides and stage guides](../../troubleshooting/documentation.md)
for a list example and selector details.

## Safe movement defaults and recovery

Fresh showcase files and stages created by the editor leave jump, sprint, swim, climb, and elytra
available. Add an explicit `[abilities].locked` list only when a pack intentionally restricts one:

```toml
[abilities]
locked = ["jump"]
```

Existing stage files are not rewritten by this default change. If an upgraded pack still blocks an
ability, back up its configuration and inspect that stage's explicit `[abilities].locked` list and
conditional ability rules. Remove only the restriction the pack author no longer wants, then
validate and reload. Do not delete the stage file or reset all player stage data as a recovery step.

## Main settings and recovery

The editor's **Settings** view is generated from the complete server configuration schema and edits
`config/progressivestages/progressivestages.toml` in the same draft as stage files. It checks number,
decimal, Boolean, list, and enum values against the server's ranges and reports the exact key for an
invalid value. After review, live settings apply in the stage transaction. World or client restart
settings are saved as pending; the editor does not restart the server. If validation, writing, or
reload fails, the prior files and loaded snapshot remain active and the draft stays available.

Protocol or snapshot errors require matching client and server mod versions. Install the same 3.1.0
JAR on both sides, restart the affected client/server as required, reconnect, and reopen the editor
from `/pstages editor` to load packaged assets that match the server.

## Bounded support capture

Use the default-off `/stage debug` categories to collect only the evidence needed for one issue. The
[diagnostics procedure](../../testing/diagnostics/documentation.md) explains permission, status,
stop, output review, limits, redaction, and cleanup. Do not share a complete server log when a small
sanitized decision excerpt is enough.
