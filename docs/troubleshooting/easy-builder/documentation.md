# Easy Builder troubleshooting

The localhost Easy Builder edits the authenticated server draft. It does not publish a change
until `Apply changes` passes validation and the review is confirmed. The browser can keep editing a
rejected draft while the last valid runtime remains active.

## block replacements

Use **Block overrides** to hide blocks and change their drops until a stage is owned. This works
for any block, not only ores. Add exact block IDs, block tags, or a `mod:` namespace. A mod target
matches all blocks from that mod. Add more targets to cover a list, then choose a registered
display block and a registered drop item. The optional priority is a signed whole number; a higher
value wins when more than one override matches a block. Add `|priority=number` to one selector to
override the row priority for that target.

Block overrides do not stop placement or right click. Use the separate block lock fields when the
player must not place or interact with a block. New rules are saved as `[[blocks.overrides]]`.
Existing `[[ores.overrides]]` rows are still read and stay in that table when edited. Do not put
replacement selectors under `[ores].locked`; that field is rejected because it does not create an
override.

An unmatched selector produces a server warning with the exact target field and a match count of
zero. Check that the mod is installed or that the selected block tag exists in the active pack.

## structure entry and protection

Structure targets use exact resource IDs, for example `minecraft:stronghold`. Tag, namespace,
wildcard, and name selectors are rejected so the server can show which stage and source key made
each decision. In the Structures section, `entry_allowed` means that this stage does not block
entry. It does not grant access and does not remove another stage's entry gate. Use it with
`prevent_block_place` or `prevent_block_break` when entry should be unlocked by a separate stage.

`prevent_explosions` and `disable_mob_spawning` are actorless protections. They apply to the
matching structure without looking up a player or inventing an owner. `priority` is optional and
accepts signed 32 bit values. Higher priority wins, and a denial wins an equal priority tie.
Container access follows an active entry denial, so a protection-only stage does not create an
implicit chest lock.

If the editor reports an invalid Boolean, selector, or priority, correct the field and review
again. The rejected draft and last accepted server snapshot remain available. Do not delete the
stage file to recover from a validation error.

## main settings

The Settings page is generated from the connected `StageConfig.SPEC`. It edits the main
`progressivestages.toml` file in the same draft as stage files. Whole numbers, decimals, booleans,
lists, and enum values are checked before they reach review. Unknown keys and values outside the
server's configured range are rejected with the exact setting path. A blank numeric field is kept as
an error instead of being silently changed to zero.

After review, live settings update the running server. Settings marked for a world or client restart
are saved but appear as pending in the apply result. No restart is started automatically. If a stage
reload or write fails, the editor restores the previous file and loaded setting snapshot, then keeps
the draft available for correction.

## ownership choices

The Setup and Access tabs expose four choices.

* Inherited removes both ownership overrides and follows the global team setting.
* Personal writes `scope = "team"` with `team_stage = false`, keeping profession stages with the
  triggering player.
* Team writes `scope = "team"` with `team_stage = true`.
* Server writes `scope = "server"` and removes `team_stage`.

The editor never writes the rejected `scope = "player"` value. Counter scope remains a separate
progression setting.

## LuckPerms mappings

The Access tab keeps the optional bridge dormant when LuckPerms is missing. Inbound rows accept
groups, Boolean permissions, `all` or `any` matching, and context values. Synchronized retention
removes only the bridge derived source after an authoritative loss. Permanent retention keeps that
source. Independent grants are unaffected. Outbound rows create only owned transient group or
permission contributions.

Use **Add context**, enter its key, then add each accepted value with **Add value to context**.
All keys must match; any listed value can match within a key. Commas and line breaks are part of
that individual value. Combine duplicate keys into one row before saving. The server permits
8 keys, 8 values per key, and at most 256 combinations per mapping. Empty keys, empty arrays,
blank values, and reserved bridge keys fail validation. The controls show bounds from the server
schema and retain invalid drafts for correction.

Context edits preserve unchanged mapping fields, omitted defaults, quoted keys, comments, and
unrelated child tables. A malformed existing context source shows an explanation; correct it in
Source before changing that map. Saving an unrelated field preserves the malformed source for
server validation. A failed save keeps the dialog and entered values available.

If validation reports a missing group, provider absence, invalid context, duplicate row id, or an
unsupported command path, fix the row in the draft and review again. The source text and last valid
runtime are retained when apply is rejected.

## Command gates

**Gate descendants** is enabled for new command gates and when `descendants` is omitted from
existing source. Leave it enabled to require the stage for child literals and their arguments.
Turn it off to gate only the selected literal and its arguments. A stage never replaces the
command's native permission requirement. Editing a path preserves an omitted setting and the
row's comments. If saving fails, the dialog keeps your input and displays the error.

## direct interactions

Use Access, Direct interactions, Add interaction. For Selling Bin access, choose Use an item on a
block, set the held selector to `tag:c:armors` or `all:*`, and set the target block to
`id:selling_bin:selling_bin`. The server checks the live holders and the nonempty held stack before
the interaction continues.

For a selective restriction, enter `id:minecraft:bread` and the Selling Bin block selector, then
enable **Also restrict GUI insertion**. Set **Inventory rule priority** if another inventory rule
also matches. Saving creates an `item_on_block` rule and a matching `item_into_inventory` rule
in one draft change. Both require the containing stage, and both remain independently editable.
A selective item rule does not restrict an empty hand menu open by itself. Use `all:*` for a whole block item rule when empty hand access should require the stage, or use **Right click a block** as a separate rule when whole bin access should be explicit.

Editing an existing interaction changes its own row and preserves conditional activation and
comments. If saving fails, the dialog keeps the entered selectors and reports the error.

Quoted table paths and multiline text are supported by the access row editors. Text that looks
like a table header inside a string stays part of that string. An unterminated string or array
must be corrected in source before a guided edit can safely find its field. Ownership and
retention controls support inline tables such as `stage = { id = "chef", team_stage = false }`.
Editing these fields preserves other keys and nested values. The Access tab also supports
inline arrays of interactions, command gates, and permission mappings. You can edit, add, or
remove rows and configure their contexts without converting the file to array table syntax.
The paired Selling Bin preset works inside an existing inline interaction array.

The Rules tab also opens inline inventory insertion rules. Editing their priority, destination,
activation, or reset preserves the other fields and existing condition aliases. A failed save
keeps your input available. If the source row changed while the form was open, reopen the rule
before saving to avoid overwriting that edit.

The pairing option is available when adding a held item rule. For an existing rule, add its
inventory counterpart with the same item, choose **Container block**, enter the same block
selector, and select **Deny until stage is owned**. Conditional activation remains editable in
the Rules tab. Use the same activation on both rules when they should apply together.

## browser and draft recovery

### translating labels safely

Use the browser's page translation control, including Brave or a Google Translate browser action,
on visible labels and help text only. Keep stage identifiers, registry IDs, commands, file paths,
TOML, and selector values unchanged. The editor does not submit source text or session data to a
translation service. If translation is unavailable, the English labels and hover help remain the
source of truth.

Editor responses complete independently of Minecraft's render thread. A hidden or minimized game
window must not prevent the browser from receiving a completed server operation. Server draft
mutations still run on the server thread. A timeout does not prove that a mutation was rejected;
reopen the current draft and inspect its state before repeating the operation.

`configuration_conflict` means the live files differ from the snapshot taken when the draft
opened. This includes manual edits that have not yet been reloaded. Apply preserves those files,
the draft and the last valid runtime. Keep the draft source, open a new draft from the current
files, and reconcile the intended changes before validating and reviewing again. Refreshing the
review on the old draft does not replace its original file snapshot. Avoid simultaneous external
file writes during apply; the comparison is not a filesystem lock.

If a field disappears after reload, reopen the stage from the current session. Do not copy the
generated source over the original package. Targeted edits preserve comments, unknown sections,
and unrelated files. A stale revision or failed reload leaves the editable draft and reports the
field or capability diagnostic. Use undo, redo, or reopen to restore the last valid source, then
run `Apply changes` again.

Apply checks the revision shown in the review against the server draft. If another tab or
collaborator changes the draft, the server returns `draft_conflict` without writing live configuration or
reloading stages. The editor closes that stale review and refreshes the current draft. Open
Review again, inspect the updated changes, and confirm them. Older cached tabs that omit the
reviewed revision are also rejected; reopen the editor to load the matching packaged assets.

If the tab reports `403 Forbidden`, close it and open `/pstages editor` again from a permission
level 3 operator. The token belongs to one loopback session and cannot be reused in an older tab.

If a shared draft request reports `unauthorized`, ask its owner to check your collaborator access.
Removing a collaborator invalidates every existing session they hold for that draft. After the
owner adds you again, resume the shared draft to obtain a fresh session. Reusing the old session
does not restore access. Losing operator permission also invalidates the session on its next request.

### guide fields and placeholders

The **What to do next** section writes `guide.how_to_unlock`, `guide.next_steps`, and
`guide.where_to_find` as inert text. Each field accepts at most 2048 Unicode code points and 8 KiB
of UTF 8 data. The editor shows both counters, refuses an overlong save, and keeps the last valid
server draft when validation fails. The placeholder chooser inserts `{stage_name}`,
`{stage_requirements}`, `{remaining_requirements}`, or `{stage_progress}`. Unknown placeholders
stay visible with a warning. Double braces, such as `{{stage_name}}`, display a literal brace form.

The preview uses sample values and cannot apply, grant, purchase, execute a command, or change a
rule. After applying the draft, open the ordinary advancement style stages map, select the stage,
and press the gold `?` beside the close control. The help title includes the selected stage name and
the guide disappears for an owned stage or when `general.enable_stage_guide = false`. The separate
guide keybind starts unbound and selects the next visible suggestion; it never grants a stage.


## Access configuration validation

Use TOML booleans for `luckperms.enabled` and command `descendants`, for example `enabled = false`.
Quoted `"false"` and numeric values are rejected. Write groups and permissions as string arrays,
for example `groups = ["chef"]`; non-string entries are not silently discarded. Omission keeps
the documented default.

The server validation response retains the ordinary error summary and adds structured field
and stable row details for ownership and LuckPerms parsing failures. Package identity failures
name their draft relative `stage.toml` path. A rejected apply preserves the editable source and
installed definitions. After Validate, ownership and access rules show matching field details.
Expand the draft validation summary or open Review to see errors across all files. Errors and
warnings have distinct text labels. Editing the draft hides earlier results until it is validated
again. Rejected apply diagnostics remain available for the matching draft. Bootstrap also supplies
validation for the initial draft. Actual browser acceptance remains open.

Ownership feedback uses the current server's global sharing setting and team provider availability.
A forced or inherited team stage uses solo fallback when that provider is absent or disabled.
Personal and server choices retain their separate meanings. An unapplied edit to the global
configuration does not change this current server observation.

The Access tab distinguishes LuckPerms absence, disablement, startup, readiness, and failure.
Disabled stage mappings, missing groups, and unresolved commands remain editable. These are warnings,
so an otherwise valid draft can still apply. Command gates are checked even without LuckPerms.
A resolved command path exists in the current dispatcher; native permission checks still apply.
An ambiguous path reaches multiple literal nodes and requires checking each affected command branch.

A group marked unknown is pending or could not be checked. Use Validate again to refresh it.
Only a completed negative provider lookup reports a missing group. The lookup does not create
groups or wait on the server thread. New draft groups and commands are checked before apply,
including groups used only by outbound mappings.

## Editor operation capture

An operator with permission level 3 or the server console can capture one online player's editor
operations with `/stage debug editor on <player>`. Use `/stage debug editor status` for the
server selected output path, limits and writer state, and `/stage debug editor off` to stop.
This uses the same single capture as interactions, progression and permissions. A second capture
is rejected while one is active or draining, and stopping an inactive capture is harmless.

Authenticated draft operations record the action, requested and actual draft revisions, installed
definition and apply revisions, validation outcome, and before and after source digests. These
source digests cover the package file map, including comments and unknown fields, without emitting
file paths, source text, session tokens or error messages. They differ from the header's effective
configuration fingerprint. When validation returns structured diagnostics, a capture records the
first error, or the first warning if there are no errors. It includes the file role, field, stable
rule ID when available, severity and validation code. The full diagnostic count and truncation
flag show when the editor response contains additional entries. `operation_code` retains the
request outcome, such as `validation_failed`, separately from a field code such as `server_override`.
Operations without a diagnostic retain `field = "draft"` and `severity = "NONE"`. File paths and
error messages are never copied into these records. Bootstrap, validation, review and apply
record the observed LuckPerms provider state when their response supplies typed capabilities.
Dedicated group and command capture fields remain acceptance work.

A record is reserved when an operation begins. A reload stops new observations immediately, while
an already accepted apply record can finish with its result and installed revision. The writer
waits at most 60 seconds for an accepted operation to complete and reports output failure if it
cannot. No new operation is accepted after stop. Source hashing and record serialization run on
the writer thread; the server supplies immutable before and after snapshots. Status bytes include
up to 4096 reserved bytes for each pending editor operation until its actual output size is known.
All reservations and the capture header share the 128 KiB limit. The usual 60 second, 200 sample,
20 sample per second and 256 queued record limits still apply. Exhaustion reports `sample_limit`
or `byte_limit`, with `rate_limit` and the existing lifecycle reasons where appropriate.

Use `invalid_field` with `operation_code = "validation_failed"` to identify a rejected apply, `stale_revision` for a
revision conflict, and `applied` with matching apply and definition revisions for a successful
transaction. A draft edit uses `draft_changed`; `source_preserved` and equal source digests identify
an unchanged source observation. Provider state is `not_observed` unless the operation already
produced an authoritative capability response. Do not interpret that value as provider readiness.
Use the [capture privacy and lifecycle guide](../interaction-locks/documentation.md) when collecting a support report.

## Rule editing and runtime actions

A generic rule preserves fields that the current form does not edit, including additional targets,
compound conditions, reset conditions, independent JEI and EMI settings, exceptions, cooldowns,
and extension values. Scalar and array target syntax are both editable. A category change with
multiple targets or dependent exceptions requires an explicit Source edit so it cannot silently
change their meaning. Reopen a stale dialog before saving.

Stage ownership and lifetime are independent. Adding a condition to a permanent rule does not
turn it into an owned stage rule. Weather conditions use `value = "rain"`, `"thunder"`, or `"clear"`.
Conditional crafting is rejected with instructions to use progression conditions instead. Legacy
category lists retain their original format and apply to the category's supported actions.
Attributes created in a schema 4 package are saved in its rules file.

The [runtime regression guide](../../test/editor-rule-runtime.md) lists the native action checks and
the nearby player limits for fluid flow and brewing.
