# Purchase accounting regression

`StagePurchaseGameTests.repeatedItemCostsRequireTheFullPaymentBeforeAnyMutation` exercises the
real server purchase payload handler with a personal stage parsed from TOML. The cost contains
two rows of four bread, two iron ingots, ten experience levels and a 50 percent refund.

With four bread, two iron ingots and twenty levels, the request must leave every balance,
stage ownership and purchase receipt unchanged. After adding four more bread, it must consume
eight bread, both ingots and ten levels. Repeating the purchase must not charge again. Revocation
returns four bread, one ingot and five levels once. A second definition with two maximum integer
bread costs must remain unaffordable without overflowing its quantity calculation.

The fixture verifies that TOML retains the repeated cost rows. It restores the original stage
registry, owner attachment, purchase data, actor lookup, grant clocks and network runtime state
in a `finally` block. It requires an isolated dedicated server without real players.

`purchasesCreateIndependentOwnershipWithoutConsumingTemporaryAccess` starts with a personal
stage held only through its temporary source. It proves that missing prerequisites and insufficient
experience reject a purchase without charges or a receipt, while keeping a disabled GUI offer.
After qualification, the purchase must add independent ownership, charge ten levels and four bread,
and award one diamond. Both sources remain stored. A duplicate purchase must preserve the balances,
reward and receipt, and the completed purchase must no longer be offered. Removing only temporary
access must leave independent ownership and its receipt intact. Explicit stage revocation then
returns five levels and two bread and restores the ordinary purchase offer.

`rewardRevocationUsesTheCommittedPurchaseReceipt` parses a stage costing ten levels and four
bread with a 50 percent refund and a sixty second cooldown. Its configured rewards give one
diamond and run `stage revoke @s progressivestages:purchase_reward_revoke`. Starting with twenty
levels and eight bread, the actual purchase handler must finish with fifteen levels, six bread,
one diamond and no stage or outstanding receipt. An immediate second purchase and another revoke
must not change those balances. The fixture restores its own cooldown entry as well as the
registry, attachment, purchase data, actor lookup and grant clock. This exercises an actual
reward command, not a simulated cancellation of the noncancellable grant notification.

Prepare the repository's no GUI `forgeserverdev` launch with Java 21, Minecraft 1.21.1 and
NeoForge 21.1.248, enabling the `progressivestages` and `minecraft` GameTest namespaces. The
production launch does not register these tests. Run each case separately on a cleared platform:

```text
fill -2 180 -2 12 215 15 air
fill -2 179 -2 12 179 15 stone
execute positioned 0 180 0 run test run repeateditemcostsrequirethefullpaymentbeforeanymutation
```

Ensure the platform chunks are loaded before setup. Verify the structure metadata names the
intended test and inspect its success marker. Also run
`rewardrevocationusesthecommittedpurchasereceipt`,
`purchasescreateindependentownershipwithoutconsumingtemporaryaccess`,
`refundsreturnonlytothepayeracrossofflinedelivery` and
`serverpurchasespreservepayersandpersonalhistory` to protect payer isolation and stored refund
terms across logout, saved data reload and definition changes. The GUI response regression
protects shared purchase feedback without claiming client presentation from a captured packet.

Stop the exact owned process and remove the disposable runtime and temporary build output after
recording required results and artifact identities in the test report.
These server assertions do not replace the separate laptop purchase presentation or real provider
acceptance matrix.

The separate [real lease fixture](../fixtures/purchase-lease/README.md) exercises actual laptop movement,
purchase clicks, restart persistence, and the displayed balance and map after command revocation.
