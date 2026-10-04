# LuckPerms bridge troubleshooting

ProgressiveStages can read LuckPerms groups and Boolean permissions for a stage and can project a
stage's existing group or positive permission contributions back to LuckPerms. The integration is
optional and remains dormant when LuckPerms is absent. It uses the compile only API 5.4 surface and
Use LuckPerms NeoForge 5.4.150 with Minecraft 1.21.1 and NeoForge 21.1.248. See the [editor and runtime test guide](../../testing/editor-rule-runtime/documentation.md) for server side cases and limits.

## Configuration

Add the optional tables to the stage file. `inbound_mode` is `synchronized` by default. Use
`permanent` when losing the qualifying rank should leave the stage owned. Independent grants remain
independent in either mode.

```toml
[luckperms]
enabled = true
inbound_mode = "synchronized"

[[luckperms.inbound]]
id = "chef_rank"
groups = ["chef"]
permissions = ["professions.chef"]
match = "any"

[[luckperms.outbound]]
id = "home_permission"
kind = "permission"
value = "neoessentials.teleport.home.set"

[[command_permissions]]
id = "home_gate"
path = "sethome"
descendants = true
```

Inbound rows use `all` or `any` matching. Only a LuckPerms Boolean result of true qualifies a
permission. False and undefined results do not qualify. Context tables use an AND between keys and
an OR between values for each key. Context keys and values are compared without case sensitivity,
while source casing is preserved. A reserved bridge marker, including differently cased aliases,
and duplicate keys differing only in case are rejected in configuration.

The query adapter uses current provider contexts for a loaded online user and static provider
contexts for a loaded offline user. It preserves multiple values per key and removes the bridge
marker from eligibility queries. An unloaded user or query failure is unavailable, not an
authoritative undefined permission. A false or unavailable permission query prevents positive
outbound output and withdraws an existing owned contribution. An authoritative undefined result
can receive a configured positive contribution. Test inherited exclusion and negative precedence against actual LuckPerms provider state.

Outbound rows refer to an existing group or permission. The required behavior is to own only
transient contributions, remove them when no longer required, and preserve administrative nodes
and independent membership. The adapter now writes only transient data and attaches an ownership token to
created nodes. Equal administrative nodes are not adopted or removed. Overlapping rows retain
separate references, row replacement removes the old contribution first, and failed operations
remain retryable. Logout and shutdown attempt exact owned cleanup. A cleanup warning means the
adapter and references remain retained, and another bind cannot silently replace them.

The reserved `progressivestages_bridge=active` context is published for the exact current player
object only after the intended node mutations are confirmed. Dirty state, reload, disconnect and
shutdown invalidate it before cleanup. An old reconciliation ticket cannot activate a replacement
session. Failed publication or cleanup leaves the projection unavailable and retains retry state.
Reload schedules a bounded online scan with at most sixteen subject reconciliations per tick;
it no longer queries every player directly inside the reload call. Context calculator cleanup is
part of shutdown completion. Test these controls with actual provider accounts and native
permission checks before deployment.

User load, unload, node changes and external context update notifications enqueue the affected UUID.
Native LuckPerms game mode and dimension notifications therefore recheck contextual stage eligibility.
Notifications caused by publishing or withdrawing the bridge's own marker are ignored only on the
same thread and for the exact target object. This prevents a reconciliation loop without ignoring
another player's context change. Custom context calculators must notify LuckPerms when their values change.
Group changes, full synchronization
and provider configuration reload request a bounded rescan. Callbacks invalidate calculator state
without querying stages or loading users; provider cache notifications happen during server
reconciliation. Cache recalculation alone does not trigger another pass. Shutdown disables event
delivery before detaching listeners. Failed detachment keeps inactive handles for cleanup retry.

Before removing a persistent permission node after a configuration change, establish its owner.
The transient ledger does not adopt or delete nodes it did not create.

## Inbound source ownership

A contribution is stored under the stage's resolved owner and identifies its player, mapping row
and retention mode. Two players qualifying through the same row remain separate contributors.
Removing one synchronized contribution preserves other rows, other players and independent grants.
A legacy stage without source labels keeps its independent meaning when a new contribution is added.

For a registered stage and its current owner, deleting an inbound row or the LuckPerms table removes
that player's synchronized contribution on reconciliation. Retained permanent contributions survive
these changes. Older labels without player attribution are not guessed or deleted automatically.
Before checking new inbound eligibility, reconciliation removes the current player's synchronized
contributions under obsolete owners or deleted definitions. Other players' contributions and
independent or permanent history are preserved. The existing FTB membership detector invokes this
path before team synchronization. Test offline contributors, startup provider revalidation, legacy migration, membership event
timing, and expiry recovery separately from online reconciliation.

## Saved sources awaiting revalidation

A saved synchronized source is inactive after data loading until it qualifies again. Its record is
retained, while independent and permanent grants remain effective. Pending sources do not satisfy
stage access or appear as effective source kinds. Revalidating an existing source does not create
an independent grant or restart its stored acquisition timestamp.

Explicit revoke and revoke-all still remove pending stored entitlements. Pending progression also
prevents default first-join starter grants from repeating. Do not mistake a pending
source for a lost save record. The current load and reactivation checks do not establish complete
offline loading, provider restart convergence, context invalidation or expiry episode behavior.

## Diagnosis

Use the permissions capture to inspect provider state, stage, row, desired result and reconciliation
reason.

```text
/stage debug permissions on <player>
/stage debug permissions status
/stage debug permissions off
```

The adapter interface defines absent, starting, ready and failed states. Its current reflective
implementation reports ready after obtaining the API; this is not proof of a working player
query, successful mutation, cleanup or login. Missing provider and group warnings preserve source
configuration. Reload, restart, provider loss and source retention still require the complete
runtime acceptance matrix.

The online update queue retains pending subjects when its 256 entry limit is reached and requests
a resumable scan. Dirty updates and scan entries share the limit of sixteen subjects per tick;
continued event traffic does not reset an active scan. Disconnect requests a followup pass, and
shutdown clears pending work. Test provider event convergence after reload and membership changes.

Offline contributor scans use the same queue and a maximum of eight pending or unacknowledged
provider observations. Their queries use fixed server contexts, never a remembered world.
Unverified synchronized sources stay stored but inactive until authoritative reconciliation;
permanent and independent grants remain available. A failed user cache cleanup retains its owned
reference for retry and prevents trusting that result or replacing an incompletely closed adapter.
Current definition and ownership checks reject stale results. When testing these paths, include
LuckPerms, FTB Teams, membership changes, and timed source episodes.

If a stage is not granted, check the stage dependency and slot policy first. Permission
reconciliation must not charge costs, run rewards, increment trigger counters, refresh expiry
episodes, or grant prerequisites. A synchronized source is removed after an authoritative loss;
permanent and independent sources remain subject to normal stage expiry and explicit revocation.
See the [editor and runtime test guide](../../testing/editor-rule-runtime/documentation.md) for server side cases.

A manually revoked permission stage stays revoked while the same eligibility remains positive.
Reloading, reconnecting, changing retention or temporarily losing the provider does not cancel that
suppression. `suppressed_episode` identifies this denial in permission capture. `expired_episode`
identifies a completed timed grant. To rearm through LuckPerms, the bridge must observe the original
independent conditions becoming false and then true in the same query contexts. A context change or
an edited row alone is not evidence of rank loss. A deliberate administrative stage grant also clears
suppression. Offline checks use fixed server contexts, so they cannot prove loss of an earlier
world dependent eligibility. No new stage setting is needed.

Do not delete `permission_episodes` to work around a denial. These records preserve revocation and
expiry even when `stage_sources` no longer contains the grant. Unsupported episode schema versions
disable progression mutations and retain the entire unreadable attachment on subsequent saves.
Restore a compatible backup before changing progression. This also protects malformed ownership
and source records; the server log reports that the original data is retained.

Administrative grant commands add independent ownership when the player already has access derived from a
rank. API and bulk, tag or category revokes suppress current positive eligibility even after an
unavailable provider has withdrawn its synchronized contribution. Repeating the revoke is a no op.
These operations keep the stage's resolved personal, team or server ownership.

## Command gates

`command_permissions` matches resolved command nodes at Minecraft's execution tasks after redirects,
using the effective player at that point. Descendant rules cover the literal
subtree, while a non descendant rule covers the configured deepest literal and its argument values.
Aliases and namespaced literals are compared using their actual dispatcher binding. Native command
permission checks still run, so a stage cannot elevate a player. The gate is evaluated before the
command side effect and preserves the effective player through delegated execution.

An unresolved path produces an inactive rule warning. Reload after the command provider becomes
available. A similarly named command in another namespace is not automatically an alias. The stage requirement applies to the resolved command path and retains native permission checks.

## Recovery

Confirm the player is online, the provider reports ready, and the configured group exists. Check
the row's contexts and whether the permission result is true. If the provider was restarted, wait
for the bounded reconciliation queue to drain or use `/stage sync` after the provider is ready.
Capture output is bounded and redacts long values. Send only the relevant capture lines and the
before and after stage state. Never include a permission tree, credentials, private address or whole
inventory.

Capture status distinguishes stopped recording from completed output. Wait for `Writer: drained`
before reading the finished support file. `Writer: failed` leaves the capture incomplete; a later
write failure remains visible after manual stop. The [shared capture procedure](../interaction-locks/documentation.md)
describes category isolation, fixed limits, server tick timing, and bounded JSON fields.


Online permission input is collected before source changes. A provider event, membership change,
definition reload or stage mutation during collection discards the whole observation and queues a
fresh attempt. A temporarily unavailable permission result cannot grant earlier matching rows from
that incomplete observation. Permanent and independent ownership retain their existing semantics.

Source changes now commit as one subject transaction after qualification in an isolated view.
Listeners and client synchronization run after the complete source and eligibility state is committed.
An administrative revoke from a listener remains effective; it is not overwritten by the original
reconciliation. Callback invalidation schedules fresh work before outbound privileges are updated.

Outbound updates also reject changes during permission queries, node writes and publication. A
rejected batch stops further writes and retains its exact ownership records so fresh reconciliation
or disconnect can remove submitted nodes. Independent earnings survive this retry; a deliberate
stage revoke remains effective. Global stage, provider input and membership revisions protect the
reconciliation transaction. Published markers instead follow their subject's projection ticket and
the compiled definition snapshot. Another player's personal stage or provider change does not expire
unrelated output. Affected subject changes invalidate their tickets; global provider changes and
reload invalidate everyone. Test both scopes with two authenticated players and independent provider changes.

Offline source updates now evaluate qualification and history in an isolated draft. If the provider,
stage state, owner context, server lifecycle or attachment changes before commit, the draft is
rejected without altering live sources or clocks. A fresh valid update commits the subject's source
and history changes together and publishes once. This preserves independent and permanent ownership
under the existing retention rules. It does not make unavailable input authoritative or replace
provider and multiplayer behavior against the target modpack.
