---
title: Cleanup and retained files
description: Understand missing workspaces, persistent locks, cache saves, and shutdown failures.
---

A disposable workspace is normally removed when its scenario closes successfully. A scenario
marked unsuccessful by startup or lifecycle failure retains its workspace when
`keepFailedWorkspaces` is enabled. Process logs are written to `anvil-console.log` inside that
process's workspace.

## Files after a failed assertion

The current JUnit extension closes the context without passing a test-body assertion failure to
workspace finalization. Consequently, a failed JUnit assertion can still remove a disposable
workspace and save enabled workspace caches if the scenario lifecycle otherwise succeeded.

For a test whose files you need to inspect, declare `WorkspaceMode.PERSISTENT` in that process's
`WorkspacePlan`. This retains its directory across runs, so reset or reseed plugin state explicitly
when test independence requires it. See [Persistent workspaces](../../../building-blocks/environments/workspaces/persistence/index.md)
and [workspace caches](../../../building-blocks/environments/provisioning/cache/snapshots/index.md).

A setup-hook failure happens during engine startup and uses the failed-startup path. A failed
process restart also marks the run unsuccessful, even if the test catches the restart exception.
Do not assume these cases have the same finalization behavior as an assertion that only fails in
the JUnit method body.

## Persistent workspace in use

Persistent directories are locked against concurrent use. Check for another test, foreground
session, or embedding application that still owns the same workspace. Close that owner cleanly
before reusing it. Deleting a live process's files is not a substitute for releasing its context.

If files are missing despite retention, inspect your own cleanup declarations. An `ON_FAILURE` or
`AFTER_STOP` rule can deliberately remove paths you expected to inspect. Keep diagnostic files out
of those paths. See [Workspace cleanup](../../../building-blocks/environments/workspaces/cleanup/index.md).

## Shutdown errors

The scenario attempts remaining cleanup operations when one resource fails. Inspect suppressed
exceptions as well as the primary error: a failing assertion and a stuck process can be separate
problems. The engine releases players and agent connections, then stops processes in reverse
startup order before finalizing workspaces.

`anvil.stopTimeout` is the graceful shutdown interval before termination escalates. Increasing it
can help a process that is correctly saving data; it does not repair a deadlock or an invalid
shutdown command. See [Engine options](../../../building-blocks/environments/configuration/engine/index.md).
