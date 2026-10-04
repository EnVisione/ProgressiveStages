# Editor Session Authorization

`removedcollaboratorsloseeverysessionaction` exercises the actual server editor session service
with an owner, a collaborator and another operator. Use Minecraft 1.21.1, NeoForge 21.1.248,
Java 21 and the exact candidate classes in an isolated dedicated development server. NeoForge
disables GameTests in a production launch, so compare the tested class bytes with the packaged
candidate and verify production startup separately.

The fixture opens two sessions for the invited collaborator, edits a unique draft file as the
owner, and removes the collaborator through the ordinary session action. Each old session must
return `unauthorized` for all twenty read, edit, validation, review, apply, rollback, package and
sharing actions exercised. The attempted requests must leave the live configuration revision
and filesystem unchanged. The owner's session must remain usable.

After the collaborator is added again, both old sessions must remain invalid. A fresh resumed
session must work. A different operator cannot use it, even when supplied its session credentials.
A wrong credential must fail without invalidating the legitimate session. Losing permission
level 3 revokes that session on the next request; restoring operator permission cannot revive it.

The fixture uses server player objects with controlled permission levels. It proves service
authorization and dispatch, not authenticated login, browser presentation or packet transport.
Secrets remain in process memory and are never included in assertions, logs or retained evidence.

Run from the owned server console after readiness, at an unused test location:

```text
fill -2 179 -2 12 190 15 air
fill -2 179 -2 12 179 15 stone
execute positioned 0 180 0 run test run removedcollaboratorsloseeverysessionaction
```

Run the existing `sessionapplyrejectsunreviewedchanges` regression separately to confirm that
normal owner review and apply still work and stale revisions still reject. Repeating the
authorization test after its cleanup checks that no fixture session or draft survives a run.

The authorization fixture discards its owned draft file and every session associated with it
in `finally`, then discards its server player objects. No successful live apply is performed.
After the bounded suite, stop the owned server, verify its process and private listener are
gone, and remove the test runtime and newly created build output. Preserve only sanitized
results and the source and artifact identities in the
test report.
