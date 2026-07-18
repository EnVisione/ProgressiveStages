# ProgressiveStages 3.0.1 Structure Session Compatibility

This guide explains the generic structure-session API added in ProgressiveStages 3.0.1. It is
for integration authors, pack authors, server operators, and testers. It does not require or name
any third-party quest implementation in ProgressiveStages core. A companion mod depends on
ProgressiveStages, implements the provider contract, and registers its provider at server startup.

## 1. What problem this solves

A normal `[structures]` lock answers a broad question.

> Does this player have the stage required for this structure type.

A structure-session provider answers exact-instance questions that a static stage file cannot know.

- Which generated Stronghold belongs to this assignment.
- Which player or team owns the assignment.
- Which exact bounding box is protected.
- Whether the assignment is available, active, resetting, or complete.
- Which stage permits entry.
- Which temporary stage should exist only while a participant is inside.

ProgressiveStages remains the authority that grants and revokes stages, rejects actions, repels
players, posts lifecycle events, and evaluates `leave_structure` triggers. The provider only
describes assignment state and returns `PASS`, `PERMIT`, or `DENY` decisions.

## 2. Required dependency direction

The dependency direction is deliberately one way.

```text
Companion assignment mod
        depends on
ProgressiveStages public API
```

ProgressiveStages must never import the companion mod. The provider implementation lives in the
companion mod. If the companion mod is absent, ProgressiveStages continues to load and ordinary
stage files continue to work.

## 3. The five-minute setup

An integration needs to do four things.

1. Implement `StructureContextProvider`.
2. Return immutable `StructureSessionSpec` values from `sessionsFor` and `session`.
3. Return `PASS`, `PERMIT`, or `DENY` from `evaluate`.
4. Register the provider with a unique namespaced ID on the logical server thread.

```java
ResourceLocation providerId = ResourceLocation.parse("mypack:assignments");
ProgressiveStagesAPI.registerStructureContextProvider(providerId, provider);
```

Unregister it during an intentional provider shutdown or integration reload.

```java
ProgressiveStagesAPI.unregisterStructureContextProvider(providerId);
```

Do not unregister merely because one assignment lookup failed. Provider exceptions are isolated,
logged with rate limiting, and fail closed only for a previously known claimed structure.

## 4. Public API package

The structure API lives in:

```text
com.enviouse.progressivestages.common.api.structure
```

### 4.1 Stable identities

`StructureSessionId` wraps a UUID. Store that UUID in the provider's persistent assignment data.
Do not create a new ID every time `sessionsFor` is called.

`StructureInstanceKey` identifies one generated structure using:

- its dimension key,
- its structure registry ID,
- and a stable start position.

Two Strongholds in the same dimension must have different instance keys.

### 4.2 Bounds

`StructureBounds` stores normalized minimum and maximum coordinates. Reversed constructor
coordinates are normalized automatically. Use `contains` for exact membership and `expanded` for
an immutable padded copy.

```java
StructureBounds bounds = new StructureBounds(minX, minY, minZ, maxX, maxY, maxZ);
boolean inside = bounds.contains(player.blockPosition());
StructureBounds exitBounds = bounds.expanded(1);
```

The public API never exposes a mutable structure `BoundingBox` owned by Minecraft.

### 4.3 Session specification

Every `StructureSessionSpec` supplies:

| Field | Meaning |
|---|---|
| `providerId` | The same unique ID used during provider registration. |
| `sessionId` | Persistent assignment or run identity. |
| `instance` | Exact generated structure identity. |
| `bounds` | Exact protected and observed area. |
| `assignmentOwner` | Player UUID or effective team UUID. |
| `ownershipScope` | `PLAYER` or `TEAM`. |
| `accessStage` | Stage that permits ordinary entry. |
| `inProgressStage` | Optional leased stage that exists while participants are inside. |
| `complete` | Provider-known completion state. Providers should still call the completion API. |
| `cleanupPolicy` | Revoke access after completed final exit, or keep it. |
| `availability` | `AVAILABLE`, `RESETTING`, or `UNAVAILABLE`. |

Use `StructureCleanupPolicy.REVOKE_ACCESS_ON_COMPLETED_EXIT` for a consumable assignment. Use
`KEEP_ACCESS` for a permanently unlocked structure.

### 4.4 Read-only views

`StructureSessionView` is returned to commands, events, and integrations. Its participant set is a
defensive immutable snapshot. It additionally reports the current visit sequence and whether an
exit is waiting for debounce confirmation.

## 5. Provider contract

```java
public interface StructureContextProvider {
    StructureAccessDecision evaluate(StructureAccessRequest request);

    Collection<StructureSessionSpec> sessionsFor(ServerPlayer player);

    Optional<StructureSessionSpec> session(StructureSessionId sessionId);
}
```

All three methods are called on the logical server thread. They must be read-only. They must not:

- teleport a player,
- grant or revoke a stage,
- cancel a NeoForge event,
- mutate assignment completion,
- or post a duplicate structure lifecycle event.

`sessionsFor` should return only sessions relevant to the supplied player or effective team. It
may return an empty immutable list.

`session` performs a direct lookup by stable session ID. It may return empty for a deleted or
unknown session.

### 5.1 Request actions

`StructureAccessRequest.action` is one of:

- `ENTRY`
- `BLOCK_BREAK`
- `BLOCK_PLACE`
- `CONTAINER_OPEN`
- `ITEM_USE`
- `BLOCK_INTERACT`
- `ENTITY_INTERACT`

The request includes the player, server level, immutable block position, and an optional candidate
structure instance found by the normal structure gate.

### 5.2 Three-way decisions

Return `PASS` when the provider does not claim the position or action.

Return `PERMIT` when the exact assigned instance belongs to the player or team and the provider has
no additional objection. A permit does not bypass a missing normal ProgressiveStages gate.

Return `DENY` when the provider claims the instance but the player, team, assignment state, or
action is not allowed.

Useful deny reasons include:

- `ASSIGNMENT_REQUIRED`
- `WRONG_OWNER`
- `WRONG_INSTANCE`
- `UNAVAILABLE`
- `MISSING_ACCESS_STAGE`
- `PROVIDER_DENIED`
- `SESSION_CLOSED`

Include the display stage, session ID, and bounds whenever they are known. ProgressiveStages uses
them for feedback, diagnostics, events, and safe repelling.

## 6. Arbitration rules

ProgressiveStages applies normal static and conditional structure rules first, then asks every
registered provider. Final behavior follows this table.

| Static result | Provider result | Final result |
|---|---|---|
| Allow | `PASS` | Allow. |
| Allow | `PERMIT` | Allow and attach provider or session diagnostics. |
| Allow | `DENY` | Deny. |
| Deny | `PASS` | Deny. |
| Deny | `PERMIT` | Deny. A provider cannot erase a missing normal stage. |
| Deny | `DENY` | Deny. |

Any provider denial wins. One provider permit cannot erase another provider denial.

If a provider throws while evaluating a position inside one of its cached known session bounds,
ProgressiveStages returns `PROVIDER_ERROR` and denies the action. If it throws for a position it
has never claimed, other rules continue normally. Error logs identify the provider and are rate
limited.

## 7. Complete provider example

The following is intentionally straightforward. Replace the repository calls with the companion
mod's real persistent assignment store.

```java
public final class AssignmentStructureProvider implements StructureContextProvider {
    private final ResourceLocation id = ResourceLocation.parse("mypack:assignments");
    private final AssignmentRepository assignments;

    public AssignmentStructureProvider(AssignmentRepository assignments) {
        this.assignments = assignments;
    }

    @Override
    public StructureAccessDecision evaluate(StructureAccessRequest request) {
        Optional<Assignment> claim = assignments.at(
            request.level().dimension(), request.position());
        if (claim.isEmpty()) {
            return StructureAccessDecision.pass();
        }

        Assignment assignment = claim.get();
        UUID effectiveOwner = assignment.teamOwned()
            ? TeamProvider.getInstance().getTeamId(request.player())
            : request.player().getUUID();

        if (!assignment.owner().equals(effectiveOwner)) {
            return StructureAccessDecision.deny(
                StructureAccessDecision.Reason.WRONG_OWNER,
                assignment.accessStage(), assignment.sessionId(), assignment.bounds());
        }
        if (!assignment.available()) {
            return StructureAccessDecision.deny(
                StructureAccessDecision.Reason.UNAVAILABLE,
                assignment.accessStage(), assignment.sessionId(), assignment.bounds());
        }
        if (!ProgressiveStagesAPI.hasStage(request.player(), assignment.accessStage())) {
            return StructureAccessDecision.deny(
                StructureAccessDecision.Reason.MISSING_ACCESS_STAGE,
                assignment.accessStage(), assignment.sessionId(), assignment.bounds());
        }
        return StructureAccessDecision.permit(assignment.sessionId(), assignment.bounds());
    }

    @Override
    public Collection<StructureSessionSpec> sessionsFor(ServerPlayer player) {
        return assignments.forPlayerOrTeam(player).stream()
            .map(this::toSpec)
            .toList();
    }

    @Override
    public Optional<StructureSessionSpec> session(StructureSessionId sessionId) {
        return assignments.byId(sessionId).map(this::toSpec);
    }

    private StructureSessionSpec toSpec(Assignment assignment) {
        return new StructureSessionSpec(
            id,
            assignment.sessionId(),
            assignment.instance(),
            assignment.bounds(),
            assignment.owner(),
            assignment.teamOwned() ? StructureOwnershipScope.TEAM : StructureOwnershipScope.PLAYER,
            assignment.accessStage(),
            Optional.of(assignment.inProgressStage()),
            assignment.complete(),
            StructureCleanupPolicy.REVOKE_ACCESS_ON_COMPLETED_EXIT,
            assignment.available()
                ? StructureSessionAvailability.AVAILABLE
                : StructureSessionAvailability.UNAVAILABLE
        );
    }
}
```

## 8. Pack configuration

The compatibility layer uses ordinary stage files. No third-party class name appears in TOML.

### 8.1 Broad access stage

This is the ordinary static gate. It ensures that provider `PERMIT` never becomes a bypass around
the normal progression model.

```toml
[stage]
id = "stronghold_assignment"
display_name = "Stronghold Assignment"
description = "Allows entry to your assigned Stronghold."

[structures]
locked_entry = ["id:minecraft:stronghold"]
entry_padding = 3

[structures.rules]
prevent_block_break = true
prevent_block_place = true
prevent_explosions = true
```

The companion mod grants `stronghold_assignment` when an assignment is accepted. The provider
still denies unassigned or wrong-owner exact instances.

### 8.2 Leased in-progress stage and contextual item locks

`[active_locks]` has the opposite polarity of an ordinary item lock. Its item selectors apply
because the in-progress stage is present and the participant is currently inside its matching
structure session.

```toml
[stage]
id = "stronghold_active"
display_name = "Stronghold Run Active"
description = "Temporary rules used only inside an active assigned Stronghold."

[active_locks]
scope = "structure_session"

[active_locks.items]
locked = [
  "id:minecraft:bow",
  "id:minecraft:crossbow",
  "id:minecraft:diamond_pickaxe"
]
always_unlocked = ["id:minecraft:wooden_pickaxe"]

[active_locks.enforcement]
block_item_use = true
```

In 3.0.1 active locks intentionally affect item use only. They do not hide recipe-viewer entries,
remove items from inventories, block pickup, alter loot, prevent crafting, or change tooltips.

Supported selector prefixes remain the normal unified grammar:

- `id:minecraft:bow`
- `mod:examplemod`
- `tag:mypack:ranged_weapons`
- `name:crossbow`

Only `scope = "structure_session"` is valid in 3.0.1. Unknown scopes fail parsing instead of
silently behaving globally.

### 8.3 Event-driven completion exit trigger

Place this trigger in the stage that should be granted after the committed leave.

```toml
[stage]
id = "stronghold_cleared"
display_name = "Stronghold Cleared"

[[triggers]]
type = "leave_structure"
structure = "id:minecraft:stronghold"
provider = "mypack:assignments"
required_session_stage = "stronghold_active"
outcomes = ["completed"]
description = "Complete the assigned Stronghold and leave its boundary."
```

Accepted aliases are:

- `leave_structure`
- `leave_structures`
- `exit_structure`
- `structure_exit`

`structure` may be an exact ID or a structure tag such as `#mypack:dungeons`.

`provider` is optional. When present, only committed leaves from that provider match.

`required_session_stage` and its alias `session_stage` are optional. They match the session's
in-progress stage, not any unrelated stage the player happens to own.

`outcome = "any"` or an omitted outcome filter accepts every outcome. `outcomes` accepts:

- `incomplete`
- `completed`
- `cancelled`
- `death`
- `teleport`
- `dimension_change`
- `disconnect`
- `recovery`

The leave condition is event-driven. It is evaluated from the committed transition and is not
stored as a permanent visited flag. A new session or visit can produce a new event.

## 9. Session lifecycle

### 9.1 Enter

An enter transaction performs these operations in order.

1. Confirm that the session is available and belongs to the effective owner.
2. Confirm that the normal access stage is owned.
3. Ask providers for the exact `ENTRY` decision.
4. Acquire the in-progress stage lease.
5. Grant the in-progress stage with `StageCause.STRUCTURE_ENTER` if the first lease introduced it.
6. Add the participant and increment the visit sequence.
7. Post `StructureSessionEnterEvent` after the state is committed.

If the stage transaction fails, the participant is not committed and entry remains denied.

### 9.2 Remaining inside

Standing inside does not post repeated enter events. Re-entering the exact bounds during the exit
debounce cancels the pending leave.

### 9.3 Normal exit

The manager uses bounds expanded by one block and a ten-tick debounce. Crossing one noisy piece
edge does not immediately close the visit. Remaining outside through the debounce commits exactly
one leave.

An incomplete exit uses `INCOMPLETE`. It releases the in-progress lease but preserves the access
stage so the assignment can be attempted again.

A completed exit uses `COMPLETED`. The final participant releases the in-progress lease and the
access stage is revoked once when the cleanup policy requests it.

### 9.4 Completion

The provider confirms its own objective and calls:

```java
ProgressiveStagesAPI.markStructureSessionComplete(player, sessionId);
```

The call validates that the session belongs to a session visible to the player. It is idempotent.
Only the first completion transition posts `StructureSessionCompletionEvent`. Access remains until
the completed session's final participant leaves.

### 9.5 Forced outcomes

ProgressiveStages maps lifecycle interruptions to typed outcomes.

| Situation | Outcome | Assignment automatically completed |
|---|---|---|
| Death while participating | `DEATH` | No. |
| Teleport outside the expanded bounds | `TELEPORT` | No. |
| Dimension change | `DIMENSION_CHANGE` | No. |
| Disconnect | `DISCONNECT` | No. |
| Admin close | Operator-selected outcome | Only if the selected provider workflow treats it as complete. |
| Recovery cleanup | `RECOVERY` | No. |

Server shutdown clears runtime caches without producing fake leave events or stage revocations.
Providers and sessions are reconstructed after startup.

## 10. Team-safe stage leases

Leases are keyed by the effective stage storage owner, stage, provider, session, and participant.

- The first participant may introduce the in-progress stage.
- Additional participants add leases without granting the stage again.
- One participant leaving does not revoke the stage while another lease remains.
- The final release revokes only a stage that the lease system originally introduced.
- A stage owned before the first lease is never revoked by lease cleanup.
- Duplicate acquire and release calls are harmless.
- Introduced-stage ownership and participant references persist across a server stop or crash.
- Reconciliation migrates a player-scoped lease when the stage storage team changes and cleans the
  former owner only after its final persisted participant is gone.

For team sessions, `assignmentOwner` must be the same effective team UUID returned by
`TeamProvider`. A teammate who is not a participant does not receive an exact-instance provider
permit merely because another member is inside.

Temporary session grants skip dependency expansion, unlock rewards, purchase refunds, and revoke
cascades. Still keep the in-progress stage dedicated and simple. Do not make it purchasable, and
avoid unrelated progression triggers because validation warns about unsafe temporary stages.

## 11. Item-use decisions

Integrations can inspect the exact item result instead of repeating lock logic.

```java
ItemUseDecision decision = ProgressiveStagesAPI.evaluateItemUse(player, stack);
if (!decision.allowed()) {
    // Use decision.stage, polarity, providerId, sessionId, reason, and message.
}
```

The polarity explains why the item is blocked.

| Polarity | Meaning |
|---|---|
| `MISSING_STAGE` | Ordinary lock. The required stage is absent. |
| `PRESENT_IN_CONTEXT` | A contextual provider denial or active lock inside a managed session. |
| `NONE` | Allowed or bypassed. |

The server evaluates one decision and uses that same decision for cancellation and notification on
right-click item, use start, left-click block, right-click block with a held item, entity
interaction with a held item, and attacks with a held item.

## 12. Events

All structure events are posted after state commits and are not cancellable.

```java
@SubscribeEvent
public static void onEnter(StructureSessionEnterEvent event) {
    StructureSessionView session = event.getSession();
    boolean introducedStage = event.wasStageGranted();
}

@SubscribeEvent
public static void onComplete(StructureSessionCompletionEvent event) {
    StructureSessionId id = event.getSession().sessionId();
}

@SubscribeEvent
public static void onLeave(StructureSessionLeaveEvent event) {
    StructureLeaveOutcome outcome = event.getOutcome();
    boolean revokedStage = event.wasStageRevoked();
}

@SubscribeEvent
public static void onDenied(StructureAccessDeniedEvent event) {
    StructureAction action = event.getRequest().action();
    StructureAccessDecision.Reason reason = event.getDecision().reason();
}
```

Session transition events expose the player and UUID, provider ID, effective owner, immutable
session view, visit sequence, instance, bounds, access stage, optional in-progress stage, and
grant or revoke result.

New stage causes are:

- `STRUCTURE_ENTER`
- `STRUCTURE_LEAVE`
- `STRUCTURE_COMPLETE`

## 13. Commands

The command root is available through `/pstages`, `/stage`, and `/stages`. Use `/pstages` when
another mod owns a shorter alias.

```text
/pstages structure providers
/pstages structure sessions
/pstages structure sessions <player>
/pstages structure reconcile <player>
/pstages structure close <player> <session_uuid> <outcome> confirm
```

`providers` lists the provider ID, implementation class, and cached session count.

`sessions` lists provider, session ID, structure, dimension, owner, access stage, in-progress
stage, completion, visit sequence, participant count, and pending-exit state.

`reconcile` refreshes provider sessions and repairs participant leases without fabricating a
gameplay enter event.

`close` is deliberately explicit and requires the final `confirm` literal. It is an operator
recovery tool, not an ordinary completion path.

## 14. Reconciliation and reload

Registration triggers reconciliation for online players. Login performs a reconstruction pass.
Reconstruction may repair a missing lease stage for a player already inside, but does not post a
fake gameplay enter event.

Datapack or stage reload must keep provider IDs and stored session IDs stable. After a change that
affects stage definitions, run:

```text
/progressivestages reload
/progressivestages validate
/pstages structure reconcile <player>
```

Provider lookup failure preserves the last known session cache and related stages. This prevents a
temporary companion-mod failure from silently opening a claimed dungeon or destroying assignment
state. Persisted lease ownership is checked during reconciliation. A lease-introduced stage with no
matching active exact session is removed instead of remaining stuck after a restart.

## 15. Validation and common mistakes

The parser rejects:

- unknown active-lock scopes,
- malformed active-lock selectors,
- non-ID `always_unlocked` entries,
- invalid provider resource IDs,
- invalid leave outcomes,
- and missing required-session stages during graph validation.

Review these warnings manually during pack testing:

- An in-progress stage with `[cost]`.
- An in-progress stage with rewards or refunds.
- An in-progress stage with dependency cascade behavior.
- An enter trigger that independently grants the same in-progress stage.
- A provider filter that is never registered on the server.
- A team-owned session whose assignment owner is a player UUID.
- A player-owned session that uses a team-scoped stage in a team-mode pack.

### Mistake. Returning `PERMIT` everywhere

Return `PASS` outside claimed exact instances. A global permit makes diagnostics misleading and can
attach the wrong session to unrelated actions.

### Mistake. Creating a session UUID during every query

Session IDs must survive restarts and repeated queries. Generate once when the assignment is
created, then persist it.

### Mistake. Mutating from a provider callback

Do not grant a stage inside `evaluate`. ProgressiveStages may call it while processing an action.
Mutating from the callback creates re-entrancy, duplicate events, and broken rollback behavior.

### Mistake. Using ordinary `[items]` for inside-only restrictions

`[items]` blocks because a stage is missing. Use `[active_locks.items]` when the item should be
blocked because an in-progress stage is present inside its exact session.

## 16. Acceptance test matrix

Use two players, two generated instances of the same structure type, and team mode where noted.
Record server logs and the output of `/pstages structure sessions` at each milestone.

### Provider and gate tests

1. With no provider registered, confirm ordinary `[structures]` behavior is unchanged.
2. Register one provider and confirm `/pstages structure providers` lists it once.
3. Attempt duplicate registration and confirm it is rejected.
4. Give a player an assignment and access stage. Confirm only the assigned exact instance permits.
5. Visit a second structure of the same registry ID. Confirm it does not inherit the first permit.
6. Remove the normal access stage. Confirm provider `PERMIT` cannot bypass the static denial.
7. Throw from the provider inside a cached claimed bound. Confirm fail-closed denial and a
   provider-identified rate-limited error.

### Transition tests

8. Walk outside to inside. Confirm one enter event and one visit increment.
9. Remain inside for thirty seconds. Confirm no repeated enter event.
10. Cross the boundary and return within ten ticks. Confirm no leave event.
11. Remain outside. Confirm exactly one `INCOMPLETE` leave.
12. Re-enter. Confirm a new visit sequence.
13. Mark complete while inside. Confirm one completion event and retained access.
14. Mark complete a second time. Confirm no second completion event.
15. Leave after completion. Confirm one `COMPLETED` leave and one access-stage revoke.

### Lease and team tests

16. Enter with an in-progress stage not previously owned. Confirm `STRUCTURE_ENTER` grants it.
17. Leave incomplete. Confirm the final lease revokes it with `STRUCTURE_LEAVE`.
18. Own the in-progress stage before entering. Confirm leaving does not revoke it.
19. Enter as two team participants. Confirm one participant leaving keeps the stage.
20. Confirm the final participant leaving releases the stage.
21. Confirm a nonparticipant teammate does not receive an exact-instance permit.

### Active item-lock tests

22. Put a bow, crossbow, and ordinary inventory items in the participant's inventory.
23. Outside the session, confirm every item works normally.
24. Inside the session, confirm bow and crossbow use is cancelled.
25. Confirm the items remain in inventory and can still be picked up.
26. Confirm crafting, loot, JEI, EMI, and tooltips are unchanged.
27. Add the bow to `always_unlocked`, reload, reconcile, and confirm it works inside.

### Lifecycle tests

28. Die inside. Confirm one `DEATH` leave, no automatic completion, and access retention.
29. Teleport outside. Confirm one `TELEPORT` leave and no later duplicate generic leave.
30. Change dimension. Confirm one `DIMENSION_CHANGE` leave.
31. Disconnect inside. Confirm `DISCONNECT`, no completed cleanup, and successful login reconcile.
32. Stop the server while inside. Confirm shutdown posts no fake leave and performs no revoke.
33. Restart, register the provider, log in, and reconcile. Confirm session and leases repair.

### Full companion integration flow

34. Accept an assignment and persist its session ID and exact instance.
35. Grant the access stage through the companion mod.
36. Enter the assigned instance and observe the leased stage.
37. Verify protected break, place, container, block, entity, and item actions.
38. Complete the companion objective and call `markStructureSessionComplete`.
39. Leave the structure and confirm the `leave_structure` trigger grants the configured next stage.
40. Confirm the provider clears or archives the assignment only in response to its own committed
    completion workflow, not merely because the player disconnected or died.

## 17. Definition of done for an integration

An integration is ready when:

- ProgressiveStages contains no import of the companion mod.
- Provider registration uses a unique stable resource ID.
- Session and instance IDs persist across restart.
- Exact bounds identify one generated instance.
- `PASS`, `PERMIT`, and `DENY` are used according to the arbitration contract.
- Static missing-stage gates remain authoritative.
- Enter, completion, and leave events occur once per committed transition.
- In-progress leases are safe for pre-owned stages and multiple team participants.
- Contextual active locks affect item use only.
- Every forced lifecycle outcome has been tested.
- Reconciliation restores state after login and restart.
- The full acceptance matrix has recorded evidence.
