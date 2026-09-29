# ProgressiveStages support diagnostics

Diagnostic capture is off until an operator starts it. Use it only for a specific, bounded report;
the server chooses a new output file and never overwrites an earlier capture.

## Capture one issue

An operator with permission level 3 or the server console can start one category for one online
player. Use only the category that matches the report:

```text
/stage debug structures on <player>
/stage debug abilities on <player>
/stage debug editor on <player>
/stage debug interactions on <player>
/stage debug permissions on <player>
/stage debug progression on <player>
```

For an actorless structure fixture, use the structure form instead of a player target:

```text
/stage debug structures actorless <dimension> <structure_id>
```

Check the active capture and its output path, then stop it after the smallest controlled reproduction:

```text
/stage debug <category> status
/stage debug <category> off
```

The same capture accepts at most one active category. It stops on reload, shutdown, restart,
disconnect, timeout, or an exhausted limit. Limits include 60 seconds, 200 samples, 20 samples per
second, a queue of 256 records, and 128 KiB of output. Status reports the actual state and writer;
wait for `Writer: drained` before reading a finished file. `Writer: failed` means the capture is
incomplete.

## Review and share safely

Read only the exact path returned by status. Keep the smallest excerpt that answers the support
question. Captures omit player names, UUIDs, inventories, NBT, credentials, network addresses, raw
commands, and arbitrary paths, but review any excerpt before sharing it. Do not attach a complete
server log or an unredacted configuration file when one decision record is enough.

After collecting the excerpt, remove only the task's exact temporary copy. Keep the original capture
only as long as the support case needs it, then delete that exact file and verify it is gone. Do not
use broad cleanup commands or remove shared logs. For interaction-specific fields and file format,
see the [interaction lock support guide](../../troubleshooting/interaction-locks/documentation.md).
