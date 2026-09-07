---
title: Cleanup
description: Delete selected workspace paths at explicit phases and preserve useful diagnostic files.
---

Use cleanup rules to reset particular generated files. A rule deletes its path; it does not install an
asset or create a cache. Choose paths that leave the evidence you need to diagnose a failed journey.

## Reset a plugin's scratch directory

In a scenario definition, import:

```java
import me.whereareiam.anvil.api.model.workspace.WorkspaceCleanup;
import me.whereareiam.anvil.api.model.workspace.WorkspacePlan;
import me.whereareiam.anvil.api.type.CleanupPhase;
import java.nio.file.Path;
```

Add to the process builder, or combine with its existing plan:

```java
.workspace(WorkspacePlan.builder()
		.cleanup(WorkspaceCleanup.builder()
				.group("plugin-scratch")
				.path(Path.of("plugins", "MyPlugin", "scratch"))
				.phase(CleanupPhase.BEFORE_START)
				.build())
		.build())
```

Here `MyPlugin/scratch` is an example directory owned by the plugin being tested. Use a real generated
path from your application. On the next workspace preparation, Anvil deletes it before restoring
caches and installing assets.

## Choose the phase

| Phase | When deletion runs |
| --- | --- |
| `BEFORE_START` | During workspace preparation, before caches and assets. |
| `AFTER_STOP` | During workspace finalization after normal or failed execution. |
| `ON_FAILURE` | During unsuccessful workspace finalization, such as failed startup or setup. |

Paths must remain inside the workspace and must not overlap declared cache paths. Cleanup still runs
where applicable during failure handling; an `ON_FAILURE` rule can remove exactly the data you hoped
to inspect. Keep logs and diagnostic exports outside explicit deletion targets.

## Understand retention

The engine normally removes fresh run directories after successful finalization. Its default
`keepFailedWorkspaces` setting retains them after recorded startup or lifecycle failures. Process
output is written to `anvil-console.log` in the process workspace.

An exception thrown only by a JUnit assertion does not currently mark the scenario as failed for
workspace finalization. Therefore, neither failed-workspace retention nor `ON_FAILURE` is guaranteed
for an assertion-only failure. Use a [persistent workspace](../persistence/index.md) when diagnosis
requires files after every test outcome, and preserve useful output in the test report.

Closing a scenario attempts independent cleanup steps even when one fails. Check suppressed failures
as well as the first exception when shutdown is incomplete. See
[troubleshooting](../../../../help/troubleshooting/index.md) for interpreting retained logs.
