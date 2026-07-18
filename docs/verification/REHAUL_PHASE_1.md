# Rehaul phase 1 verification

Phase 1 freezes the approved 3.0.1 behavior before schema 4 changes the loader and runtime model.

## Frozen sources

The following checked in stage files are golden compatibility inputs.

| Source | SHA 256 |
|---|---|
| `examples/reference/diamond_stage.toml` | `7031d94a7158dc0f9c4089f72f7dc54f352f7aec6e9c2d1ebb1c7be8f8c5f7d5` |
| `examples/beginner_pack/stages/stone_age.toml` | `36ad8de47bf1709d841f1def05e46ed3c82c9e00f3ea9cbac198874f2c6625d5` |
| `examples/beginner_pack/stages/iron_age.toml` | `398a160b66e546672691e9a8f63899416f20631b55d43cf5e1bbcc348e930b1c` |
| `examples/beginner_pack/stages/diamond_age.toml` | `4de5d55a469f4d5b13e5a49177aef855a2db0227f3eda324667d1d5ceac76d14` |

`LegacyCompatibilityBaselineTest` verifies every checksum, parses every source through the legacy
parser, and proves the generated defaults still match the checked in examples.

## Existing behavioral coverage

The existing tests remain part of the frozen baseline. They cover identifiers, dependencies,
prefixes, lock categories, temporary and triggered rules, structure sessions, command aliases,
attributes, client lock caches, GUI render ordering, camera stability, optional integrations, stage
purchases, and reference documentation.

## Required command

```text
./gradlew clean test build --no-daemon
```

The phase passes only when the golden source test and the complete existing suite pass together.
