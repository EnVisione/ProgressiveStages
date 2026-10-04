# ProgressiveStages 3.0.6

## Quest and integration rewards

Trusted server integrations can now use `ProgressiveStagesAPI.grantStageFromSourceWithDependencyBypass` to award one stage even when its prerequisites are unmet.

- 🎯 The override affects only the requested stage. It does not award prerequisite stages or independent ownership.
- 🔐 The call requires an online level-3 operator, the server thread, a stable source identity, and a printable audit reason. The server log records the actor, owner, stage, source, reason, and mutation revision.
- 🛡️ Stage existence, slot limits, ownership, purchase rules, and source policy still apply. Repeating the same source grant creates no new mutation.
- 📦 No configuration migration is required.

For the API signature and integration details, see the [ProgressiveStages API documentation](../../DOCUMENTATION.md).
