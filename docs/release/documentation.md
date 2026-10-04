# ProgressiveStages 3.0.6

## Source aware stage reward API

Quest and other trusted server integrations can use
`ProgressiveStagesAPI.grantStageFromSourceWithDependencyBypass` to grant one stage to an online player
when that stage’s prerequisites are not met. Call it on the server thread. The call requires an online
operator with permission level 3, a stable printable source identity, and a printable audit reason. Each value
is limited to 256 characters.
A successful grant records the source identity and writes the actor, stage owner, stage, source, reason,
and mutation revision to the server log.

The bypass affects prerequisite checks only. Stage existence, ownership, slot, purchase, and source policy
checks continue to apply. The API does not grant prerequisite stages or independent ownership. Repeating
an already applied source does not create another mutation.

For API signatures and integration examples, see the
[ProgressiveStages API documentation](../../DOCUMENTATION.md).
