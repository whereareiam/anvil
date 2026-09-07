---
title: Cache snapshots
description: Reuse declared generated files without treating a whole mutable workspace as a cache.
---

A workspace cache snapshot restores and saves a selected generated path across executions. It is
useful for expensive reproducible outputs such as server libraries. `WorkspaceCache` and its policy
control these snapshots; they are separate from [Persistence](../persistence/index.md), which reuses
the process directory itself, and the [provisioning cache](../../provisioning/cache/index.md), which
retains acquired software and resolution metadata.

## Declare a reusable path

In a scenario definition, import:

```java
import me.whereareiam.anvil.api.model.workspace.WorkspaceCache;
import me.whereareiam.anvil.api.model.workspace.WorkspacePlan;
import me.whereareiam.anvil.api.type.CachePolicy;
import java.nio.file.Path;
```

Add to the process builder, combining it with any existing workspace plan:

```java
.workspace(WorkspacePlan.builder()
		.cache(WorkspaceCache.builder()
				.group("libraries")
				.path(Path.of("libraries"))
				.policy(CachePolicy.RESTORE_AND_SAVE)
				.build())
		.build())
```

The cache restores before assets are installed and platform configuration is written. Enabled outputs
are saved when the engine finalizes the run successfully. Preparation or recorded lifecycle failures
do not produce ordinary successful cache snapshots.

## Choose a policy

| Policy | Restore before startup | Save after successful finalization |
| --- | --- | --- |
| `RESTORE_AND_SAVE` | Yes | Yes |
| `RESTORE_ONLY` | Yes | No |
| `SAVE_ONLY` | No | Yes |
| `DISABLED` | No | No |

Platforms can contribute defaults. Declare the same path to override a default, including
`CachePolicy.DISABLED` to turn it off. Cache paths cannot overlap cleanup paths, because those policies
would compete over the same files.

The cache identity includes process/distribution inputs and asset inputs. Anvil keeps incompatible
snapshots separate; do not depend on a hand-built cache directory name in consumer code. An optional
`.key(...)` names a declared cache, while the default derives from its group and path.

## Keep reproducibility visible

Do not cache changing application state merely to make a failing test pass. A journey testing an empty
world or first-time registration needs a deliberate initial state. Use `RESTORE_ONLY` for a fixed
prepared fixture when appropriate, and assert that the fixture contains the state the test expects.

JUnit assertion-only failures currently do not mark the scenario lifecycle as unsuccessful. A test
body failure can therefore still reach successful workspace finalization. Account for this when
choosing which paths are allowed to be saved; see [cleanup](../cleanup/index.md).

Snapshots are stored under `<cacheDirectory>/workspaces`. Other directories under the shared root
hold acquired artifacts, Java installations, and private authentication state. The
[provisioning cache guide](../../provisioning/cache/index.md) explains that boundary and selective
sharing; do not treat the entire cache root as a workspace snapshot.
