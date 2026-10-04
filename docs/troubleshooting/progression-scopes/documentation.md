# progression scopes and ownership

ProgressiveStages resolves a stage owner for the actual player before it checks dependencies,
slots, locks, triggers, rewards, or integration callbacks. This keeps profession stages private
when a pack uses FTB Teams for other progression.

## stage settings

```toml
[stage]
id = "profession:chef"
scope = "team"
team_stage = false
```

The `team_stage` key is optional. When omitted, the stage inherits `general.team_mode` and the
available team provider. `false` stores an actor-owned record. `true` requests the active FTB Teams
owner and falls back to the actor UUID when FTB Teams is disabled or unavailable. A stage with
`scope = "server"` is always stored in the server namespace and cannot declare `team_stage`.
The parser rejects `scope = "player"`; personal ownership is selected with `team_stage = false`.

Counters retain their own player, team, or server subject scope. Changing `team_stage` does not
rewrite counter history.

## effective views and membership

The server stores team records in `team_stages`, personal records in `personal_stages`, and source
labels in `stage_sources`. A player's effective view is the union of their personal records, the
resolved team record, and server records. Personal records never move when team membership changes.
Legacy team records remain in their original namespace and are not copied to every member.

Native FTB membership events immediately withdraw the moving player's synchronized contributions
from obsolete owners, including when the player is offline. Other contributors, independently
earned stages, permanent grants and still compatible personal or server sources remain. The
bridge invalidates old observations and queues current qualification for the new owner. Joining
or rejoining alone does not restore a withdrawn synchronized source.

An owner or provider change masks incompatible history while retaining the original record. Restoring
the compatible setting reveals that history again. Use an explicit administrative regrant when a
pack intentionally wants a new owner to receive a stage.

An integration using `StageActorContext` must resolve a fresh context after membership, login,
logout or provider initialization changes. `mutateStage` returns `stale_membership` instead of
applying a delayed grant or revoke, including when the owner UUID remains the same. The revision
is server wide, so another player's membership change can also invalidate a captured context.
Resolve and reevaluate on the server thread before retrying. Rejected contexts do not publish
committed changes or alter stored stages.

Use `ProgressiveStagesAPI.resolveActorOwner(actorId, stageId)` and
`ProgressiveStagesAPI.getActorSnapshot(actorId)` for explicit individual queries while an actor
is offline. Call them on the running server thread. An unavailable offline team lookup raises
an error rather than returning guessed or partial access. The snapshot reports active sources,
so persisted synchronized grants awaiting revalidation stay excluded. These queries preserve
legacy UUID team API meanings. A freshly resolved context can grant or revoke offline ownership
through `mutateStage`; unavailable required owners reject without partial changes. Grants reserve
original rewards for the actual actor's return. Missing dependencies and rejected slots do not
reserve rewards, and repeated grants do not reset acquisition clocks. See the
[Java API contract](../../../DOCUMENTATION.md#151-progressivestagesapi) for errors and examples.

## Quest provider ownership

Defined stages use their ProgressiveStages owner for quest checks, grants and removals,
including native FTB team reward and team stage task settings. A stale FTB helper record cannot
authorize a defined stage. The legacy `integration.ftbquests.team_mode` option applies only to
helper reads and removals for undefined stages.

Stage ownership and reward distribution are separate. A team reward may be claimable once by
the quest team, but a personal profession goes only to its actual claimant. Use per player
rewards if each teammate should claim their own profession. The storage hooks preserve saved
quest settings and FTB's handling of undefined external stages. See the [server side rule test guide](../../testing/editor-rule-runtime/documentation.md) for related stage behavior.

Existing helper grants for defined stages are imported once when both integrations are enabled
and definitions are ready. They remain independent team records, including when current personal
or server scope masks them. Import preserves the native properties and does not replay rewards
or reset grant clocks. The persisted import receipt prevents a revoked stage from returning from
stale helper data. Later helper property edits are not a progression source; use stage commands
or quest rewards for new grants. Undefined external stages remain with FTB.

## Permission contributors

Source labels distinguish the contributing player, mapping row and retention mode within each
owner namespace. Removing one synchronized source preserves other contributors and independently
earned access. Adding the first derived source to an existing stage with no source labels preserves
that legacy stage as independent. A newly derived stage receives no independent grant.

When the current subject reconciles after an owner change, obsolete synchronized contributions are
removed from their original owner before new grants are evaluated. Deleting a definition also
withdraws that subject's synchronized contribution. Independent and permanent owner history stays
in place. This path does not migrate a contribution to another owner or grant an offline player.

Synchronized records loaded from disk remain stored but do not contribute effective access until
revalidated. Independent and permanent records remain effective. Explicit revocation and bulk
revocation still address pending stored entitlements. Inactive contributions cannot satisfy stage
dependencies or appear as effective source kinds.

See the [LuckPerms guide](../luckperms/documentation.md#inbound-source-ownership) for ownership cleanup behavior
and related test cases.

## Purchase refunds

New skill tree purchases retain the actual payer and typed owner in a separate receipt. A teammate
revoking a shared stage cannot collect that payer's refund. When the payer is offline, the receipt
survives saving and loading and is delivered once through their login path, even if membership or
the definition has since changed. Item amounts, XP levels and the refund percentage come from the
original purchase. Legacy receipts have no payer information and retain their old team or server
behavior; they cannot be consumed as personal purchase history. See
[purchase persistence](../../../DOCUMENTATION.md#426-cost--skill-tree-purchasable-stages) before recovery
from a schema or receipt error.

## Grant clocks

Temporary expiry, slot age and both held duration condition paths read the resolved stage owner's
clock. Personal, team and server clocks remain separate even when UUIDs match. Changing a stage's
ownership setting does not copy its old clock. Legacy UUID timestamps remain team or server history;
they are never inferred to be personal history. A personal timed stage with no personal clock
follows the existing missing timestamp behavior and starts its clock when checked.

The regression save now includes `clock_schema = 1` and `owner_grant_times` alongside retained
legacy `grant_times`. Do not rewrite owner prefixes to transfer progression. Keep the original
save if loading reports an unsupported clock schema or malformed timestamp. The current file must
be read by a compatible version before restarting the server and retrying. Test LuckPerms expiry
episodes and manual revoke suppression after restoring the stage owner and source history.

## diagnostics

Operators can inspect one resolved owner with:

```
/stage explain scope <player> <stage>
```

For a bounded redacted trace, use one capture at a time:

```
/stage debug progression on <player>
/stage debug progression status
/stage debug progression off
```

The capture is off by default and uses the same fixed limits as interaction diagnostics. Output is
written to the server selected `logs/progressivestages/progression/<capture-id>.log` path. Records
include scope presence, owner kind, pseudonymous owner label, effective before and after sets,
recipient count, cause, and mutation revision. Names, UUIDs, inventories, NBT, addresses, and raw
permission trees are not written.

If a reload fails, the last valid stage snapshot remains active. Stop an active capture before a
reload or shutdown; the lifecycle hooks stop it automatically and drain the bounded writer.

Capture status distinguishes stopped recording from completed output. Wait for `Writer: drained`
before reading the finished support file. `Writer: failed` leaves the capture incomplete; a later
write failure remains visible after manual stop. The [shared capture procedure](../interaction-locks/documentation.md)
describes category isolation, fixed limits, server tick timing, and bounded JSON fields.
