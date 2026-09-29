# ProgressiveStages 3.0.4 Release Record

ProgressiveStages 3.0.4 was published on September 3, 2026. The current stable
build is 3.0.4-fix, published on September 4, 2026 after correcting a survival
armor-slot regression in the new inventory insertion enforcement.

## Published Files

The 3.0.4-fix files on CurseForge and Modrinth are byte-identical.

| Item | Verified value |
| --- | --- |
| Source commit | `7a38be1a94efa5907fc3d83ab562c130293de263` |
| Signed release tag | `v3.0.4-fix` |
| File name | `progressivestages-3.0.4-fix.jar` |
| File size | `1,852,336` bytes |
| SHA-256 | `8fb519f53f3d3db6c2160577c479b0b55263ec8c3e63331807ce80878e1e541d` |
| SHA-512 | `c98929383457b3a52c9bb4b900fb65c989818152be4e0c43da05db711dcdb8eab7db35e3801506d66297373e5e44087fff5eaa0e7fe623cc7b543e2ef817fb42` |
| CurseForge | [File 8810275](https://www.curseforge.com/minecraft/mc-mods/progressivestages/files/8810275) |
| Modrinth | [Version FWSVEj4J](https://modrinth.com/mod/progressivestages/version/FWSVEj4J) |

Both published files declare ProgressiveStages `3.0.4-fix`, Minecraft `1.21.1`,
NeoForge, and client-and-server support. Independent downloads from both
platforms produced the hashes above.

## Release Validation

The release candidate uses the immutable shared validation revision
`2b264b7f1b1f2c6e94f36921f5c13a3713ece48b`. It builds the candidate, creates
SHA-256 and SHA-512 inventories, generates an SPDX SBOM, and verifies both the
build provenance and SPDX SBOM attestations against the exact reusable workflow
and source commit.

After the untouched candidate passes, the validation job creates a disposable
one-byte-modified copy outside the retained bundle. Both attestation predicates
must reject that copy. An accepted modified copy fails validation.

The final 3.0.4-fix [release validation run](https://github.com/MCEnvision/ProgressiveStages/actions/runs/33928685605)
ran against source commit `7a38be1a94efa5907fc3d83ab562c130293de263`
and completed successfully before publication. The signed `v3.0.4-fix` tag
resolves to that same commit.

## 3.0.4-fix Correction

- Survival players can place armor into armor slots when no matching
  `item_into_inventory` rule is active.
- Inventory menus without a registered menu type are treated as having no menu
  target instead of causing vanilla container clicks to be rejected.
- Configured inventory insertion locks continue to enforce their matching item
  and destination rules.

## Editor Corrections

- The Easy Builder now distinguishes a recipe output item from an exact recipe
  identifier. **Recipe output item** writes `[recipes].locked_items`; **Exact
  recipe identifier** writes `[recipes].locked_ids`.
- The ambiguous generic `[recipes].locked` field is rejected before a draft can
  change active server rules. A narrow legacy generated craft-output rule can be
  normalized safely; ambiguous hand-authored data remains unchanged and produces
  a field-specific error.
- Enchantment generation controls remain available in the Rules tab for maximum
  level and selection-weight restrictions.

## Inventory Insertion Rules

- `[[interactions]]` now supports `type = "item_into_inventory"`.
- One rule pairs an inserted item selector with a receiving inventory selector.
  Both sides support `all:*`, `id:`, `mod:`, `tag:`, `name:`, and `#tag`.
- Rules target a container block, open menu type, or stable server-owned
  inventory identity. A compatible mod can provide an inventory-owner resolver
  through the public API.
- Higher-priority allowances and exclusions can carve out narrow exceptions from
  broad locks. Equal priority conflicts choose denial safely.
- The Easy Builder can also save and restore stable rule IDs, conditional
  lifetimes, durations, activation conditions, and reset conditions for an
  inventory insertion rule without splitting its item and destination pair.
- Standard click, shift-click, drag, and hotbar-swap insertion paths are checked
  before their receiving slot changes. Collection-only double-click actions and
  playerless automation do not infer a player stage.

## Progression Map

- The category list renders and receives input above stage nodes, including item
  icons placed beneath the open list.

## Compatibility

- Minecraft 1.21.1.
- NeoForge 21.1.219.
- Java 21.
- Existing stage packages remain supported. The new inventory rule is additive
  and has no effect unless a stage declares it.
- EMI refreshes stop at the client disconnect boundary, preventing a world exit
  from starting an EMI reload after the client level is gone.

For setup and examples, see [the complete documentation](../../../DOCUMENTATION.md),
[inventory insertion rules](../../features/inventory-insertion/documentation.md), and the
[3.0.4 compatibility evidence](../../verification/3.0.4-phase-004-compatibility.md).
