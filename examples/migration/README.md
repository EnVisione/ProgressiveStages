# Migration example

`before/iron_age.toml` is a valid legacy one file stage. `after/iron_age/` is the equivalent schema
4 package produced by the safe migration workflow.

Use the real server commands instead of copying the after files by hand:

```text
/pstages migrate scan
/pstages migrate plan all
/pstages migrate write examples:migrated_iron_age confirm
/pstages migrate verify
```

The migration service creates a checksummed backup, compares the semantic stage document, and can
restore the original with `/pstages migrate rollback <migration_id> confirm`.
