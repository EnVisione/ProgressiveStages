# ProgressiveStages 3.1.1 release guide

ProgressiveStages 3.1.1 combines the 3.1.0 player guide, editor, structure rules, and block
overrides with the audited source-scoped grant API from the 3.0.6 maintenance line. Install the
same 3.1.1 version on the server and every client. No stage-file migration is required.

Trusted server integrations can call
`ProgressiveStagesAPI.grantStageFromSourceWithDependencyBypass(player, stageId, source, auditReason, cause)`
to grant one stage without granting its prerequisites. The call must run on the server thread with
an online level-3 operator as the actor. `source` must be an unpadded, nonempty identity and
`auditReason` must be nonempty. Both are limited to 256 printable characters. The grant keeps its
source attribution; it does not create independent ownership. Stage existence, slot, purchase,
ownership, and source policies still apply. A successful change logs the actor, owner, stage,
source, reason, and mutation revision. Repeating the same source grant is idempotent.

Use `grantStageFromSource` when prerequisites should remain required. Integrations can later
remove their grant with `revokeStageFromSource` without revoking independently earned access.
The [3.1.0 release guide](../3.1.0/documentation.md) covers the player-facing changes retained
in this release.
