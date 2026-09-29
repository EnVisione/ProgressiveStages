# Structure entry and protection rules

Structure rules can gate entry and protect a structure independently. A stage can open the door
while another unowned stage continues to stop block placement, breaking, explosions, or mob spawns.
The rules use exact structure IDs so diagnostics can name the structure and the stage that added
each decision.

## Separate entry from protection

These two stage files demonstrate the split. The first stage controls entry. The second has no
automatic grant route, so it remains unowned and keeps its protection active. Do not add the
protection stage to starting stages or give it a trigger or purchase route if it must remain active.

[`stronghold_access.toml`](../../../examples/3.1.0/stages/stronghold_access.toml):

```toml
[structures]
locked_entry = ["id:minecraft:stronghold"]
```

[`stronghold_protection.toml`](../../../examples/3.1.0/stages/stronghold_protection.toml):

```toml
[structures]
locked_entry = ["id:minecraft:stronghold"]

[structures.rules]
entry_allowed = true
priority = 50
prevent_block_break = true
prevent_block_place = true
prevent_explosions = true
disable_mob_spawning = true
```

Granting the entry stage removes its entry gate. `entry_allowed = true` removes only the protection
stage's own entry denial; it does not grant entry and does not cancel another stage's denial. The
protection stage can therefore leave entry open while its placement, breaking, explosion, and
spawning rules remain active. The `priority` field applies to that stage's structure contributions.

`prevent_explosions` and `disable_mob_spawning` are actorless. The server does not look up a player
or invent an owner for an explosion or spawn. The stage's ownership state still decides whether its
configured protection is active.

## Priority and explicit overrides

Structure priorities are signed 32 bit integers. The most specific authored value wins: a selector
suffix such as `|priority=20`, then `[structures.rules].priority`, `[structures].priority`,
`[stage].priority`, and finally `[structures].global_priority`. If every level is omitted, the
effective priority is zero. A written `priority = 0` is an explicit value and overrides an inherited
nonzero priority; omitting the field uses the next configured value.

For a real permission override, use an explicit unlock rule such as
[`stronghold_allow.toml`](../../../examples/3.1.0/stages/stronghold_allow.toml). Setting
`entry_allowed = true` is not an ALLOW rule.

| Matching rules | Result |
|---|---|
| Deny at priority 10; no matching unlock | Denied |
| Deny at 10; unlock at 20 | Allowed by the higher-priority unlock |
| Deny at 20; unlock at 10 | Denied by the higher-priority denial |
| Deny at 10; unlock at 10 | Denied; denial wins a tie |

An explicit unlock only participates in the matching rule contest. It does not erase a separate
structure contribution, another higher-priority denial, or a structure-session provider's `DENY`.
Structure-session providers remain a separate input: a provider denial denies access; a provider
permit can permit access only when no static structure rule has already denied it; `PASS` leaves
the decision to the ordinary rules. For exact-instance assignments and provider registration, see
the [structure session API guide](../3.0.1/documentation.md).

When structures overlap, keep each structure's source and action separate. A rule for one structure
is not a global allow for another structure or session. If another independently matching
contribution's resolved decision is a denial, `entry_allowed` on a different contribution does not
remove that denial.

## Editor workflow and recovery

In the web editor, select the stage and open **Rules → Structures**. Choose the exact structure,
then set entry, priority, block breaking, block placement, explosion, and spawn behavior separately.
The stage's structure table is authored in Source as `[structures]` and `[structures.rules]`.

If validation rejects an ID, Boolean, or priority, correct that field and review the draft again.
Do not delete the stage file. A failed apply keeps the last accepted configuration active and the
editable draft available. For the complete editor and release notes, see the
[3.1.0 release guide](../../release/3.1.0/documentation.md).
