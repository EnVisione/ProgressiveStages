# Block overrides and stage guides

## How do I lock ores?

Use a **Blocks** rule when players should not break or place a block until they own a stage. For
example, add the exact ore IDs to the stage's locked block list. This is the same kind of rule as
`[blocks].locked` in a stage file.

Use **Block overrides** when an ore should look like another block and drop a different item while
the player does not own the stage. Overrides can target any block, not only ores. They do not stop
placing, breaking, or interacting with the block. Add a separate Blocks rule for those actions.

In the editor, select the stage, open **Rules**, and choose **Block overrides**. Add one target for
each block in the list, then choose the block to show and the item to drop. Every target in one row
uses the same replacement block, item, and priority. Make another row when different targets need
different replacements.

The saved format is `[[blocks.overrides]]`:

```toml
[[blocks.overrides]]
targets = [
    "id:minecraft:diamond_ore",
    "id:minecraft:deepslate_diamond_ore",
]
display_as = "minecraft:stone"
drop_as = "minecraft:cobblestone"
priority = 100
```

Targets can be exact block IDs, block tags, or a mod namespace. A tag must exist in the pack and
contain the blocks you mean to mask. A `mod:` target includes every block from that mod, such as
machines as well as ores. Use an ore tag or a list of exact IDs when you only want ores. A higher
priority wins when more than one active override matches a block.

New editor rows use `[[blocks.overrides]]`. Existing `[[ores.overrides]]` rows are still supported
and remain in their original table when edited. Do not put a block list under `[ores].locked`; that
is not a block lock or an override. The separate `[display].encrypt_blocks` option is a shortcut
for exact IDs in `[blocks].locked`.

Regions are also separate from generic rules. A region needs a dimension, two XYZ corners, and
prevention flags, so edit it in the stage's **Source** view as `[[regions]]`.

## Where do I edit “How to unlock”?

Select the stage in the web editor, open **Setup**, then open **What to do next**. Enter the unlock
steps, next steps, and location there. The Player preview reads your current draft and can show
sample player states. Editing a stage from the preview changes only the draft until you review and
apply it; changing the simulated state never grants a stage to a player.

The per-stage **Show this stage's player guide** switch controls that stage's gold `?` button and
whether it appears in the guide keybind list. The global `general.enable_stage_guide` setting is the
master switch. When it is off, all stage guides are hidden. Turning it back on restores each stage's
saved choice. The guide does not appear after the player owns that stage.

The `?` button sits next to the close button in the stages menu. Hover over it to see which stage
the guide belongs to. Select the stage and click `?` to read its instructions. If the unlock field
is blank, the guide reports the configured requirements it can confirm. It says “Unavailable” when
the server cannot safely confirm the complete unlock route; this does not mean no requirements
remain.

If a guide field uses `{stage_requirements}`, its full visible unlock summary appears inside that
field instead of being repeated as a separate block. The help view also hides prerequisite and stage
group details already covered by the summary, and hides the purchase button until the help is closed.
This lets pack authors write a sentence around the placeholder without showing the same summary
twice.

The guide scrolls only when its text is taller than the open panel. In that case, move the pointer
over the stage details and use the mouse wheel; a narrow colored bar appears at the right edge of the
panel to show the scroll position. Short guides have no scrollbar because all of their text is
already visible.

For the complete configuration fields, selectors, limits, and placeholder list, see
[Configuration and technical documentation](../../DOCUMENTATION.md).
