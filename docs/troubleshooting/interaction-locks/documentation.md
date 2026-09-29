# Interaction locks

ProgressiveStages evaluates `item_on_block` and `block_right_click` rules on the server before a player interaction continues. The held item and clicked block are matched against their live registry holders, so tags use the actual tag membership of the item or block.

Use either modern selectors such as `tag:c:armors`, `id:selling_bin:selling_bin`, and `all:*`, or the legacy `#c:armors` tag form. An unprefixed namespaced identifier remains an exact identifier. Selective `item_on_block` rules require a nonempty held stack. A whole block `all:*` `item_on_block` rule also gates an empty hand menu open. Use `block_right_click` when whole block access should be expressed separately.

A missing required stage cancels the server interaction before the protected action continues. The client never decides whether an interaction is allowed. Creative bypass and unrelated interactions retain their existing behavior.

A denied direct interaction returns a terminal failure and resends the authoritative player inventory, any open menu, and the clicked block entity's normal update packet when available. The correction is sent even when the server inventory did not change. Matching clients also use the server compiled snapshot to stop denied local block and item prediction. Grant and revoke updates change that decision through the effective stage cache. Reload replaces the selector pairs, and disconnect clears them. A client connected to an older server without the prediction capability still relies on server enforcement and correction. A visible lock message alone does not prove that a sale was prevented. Compare server input, output and stored currency before and after the attempt, then repeat after reconnecting. Client prediction and actual server sale are separate observations.

An `item_on_block` rule checks the held stack during block use. GUI insertion is a separate `item_into_inventory` rule, and menu opening can be restricted independently with `block_right_click`. To restrict one sellable item through both player insertion routes, configure the same item selector and destination in both insertion rules. Do not leave an `all:*` rule active when testing a selective item rule. Existing accepted deposits and playerless automation keep their normal behavior.

For a bounded support capture, an operator or console can run:

```
/stage debug interactions on <player>
/stage debug interactions status
/stage debug interactions off
```

The command requires permission level 3 and an online target. Only one capture can run at a time. The server chooses the output file and reports it through status. A capture lasts at most 60 seconds, records at most 200 decisions and 20 decisions per second, queues at most 256 records, and writes at most 128 KiB. Strings are limited to 256 characters and stage lists to 32 entries. Captures stop on reload, shutdown, restart, disconnect, timeout, or the first exhausted limit. The disabled path does not create records or perform file I/O.

Status reports the actual active category, remaining server tick time, records, UTF-8 bytes,
queued records, and writer state. `off` stops new records immediately. Wait for `Writer: drained`
before collecting the finished file; `Writer: failed` means the capture is incomplete even if it
was stopped manually first. A replacement capture waits until the previous writer has closed.
Output is created under `logs/progressivestages/<category>/<capture-id>.log` and never overwrites
an existing file. Record timing and timeout checks use the same server tick clock, independent
of the world's persisted age. Capture never records another selected category's events.

Each file starts with a `capture_header` at sequence zero. It records the loaded stage revision,
a versioned fingerprint of the captured effective configuration, and up to 32 artifact identities.
The mod, loader, and Minecraft entries come first. Artifact paths and configuration values are not
written. The complete loaded ID/version set has a separate fingerprint and truncation count.
Archive SHA256 and build commit fields help match a report to a candidate. `build_dirty = true`
means the archive included uncommitted production inputs; its commit alone cannot identify it.
Development directories report an unavailable archive hash instead of claiming a packaged build.

Header preparation and artifact hashing run on the writer thread. The header shares the 128 KiB
limit and reserves 16 KiB while it is being prepared. Decision counters exclude sequence zero;
byte counters include the header once prepared. A header failure makes the capture incomplete.
Main configuration reload, as well as stage reload, stops capture before settings change.

Stage lists contain at most 32 entries, with separate `_total` and `_truncated` fields. Truncated
strings include their ellipsis within the 256 character limit. JSON control characters are escaped.
Progression owner labels are assigned locally within each capture and do not encode UUID hashes.

Inspect only the path returned by status. Keep the smallest sanitized excerpt containing the capture id, selected hand, item and block identifiers, matched and missing stages, reason, cancellation flags, result, and mutation outcome. Captures never include player names, UUIDs, NBT, inventories, permission trees, credentials, addresses, raw commands, or arbitrary paths.

To reproduce a report, first enable the capture, perform one controlled click with the exact held item and target block, check status, then disable the capture. Compare the server decision with the resulting stack, menu, and sale state. A valid sale fixture must be tested separately from an unvalued item. If the candidate mod or dependency does not meet the pinned loader requirements, record that compatibility limit and do not call it a report reproduction.
