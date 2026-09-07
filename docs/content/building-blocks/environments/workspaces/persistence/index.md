---
title: Persistence
description: Reuse process files across executions while keeping runtime ownership explicit.
---

Use a persistent workspace when your test deliberately needs files from an earlier execution, or when
you are diagnosing a failure and want the process directory retained after teardown. Fresh workspaces
remain the default for independent automated tests.

## Retain a process directory

In a scenario definition, import:

```java
import me.whereareiam.anvil.api.model.workspace.WorkspacePlan;
import me.whereareiam.anvil.api.type.WorkspaceMode;
```

Add this to the server or proxy builder before `.build()`:

```java
.workspace(WorkspacePlan.builder()
		.mode(WorkspaceMode.PERSISTENT)
		.build())
```

Combine `.mode(...)` with existing asset, cache, and cleanup declarations in the same workspace plan.
A persistent workspace is selected per process, so a scenario can retain one backend while using fresh
files for another.

With the default work root, a retained process is stored under
`build/anvil/<scenario>/persistent/<process>`, using filesystem-safe scenario and process names.
Anvil holds an exclusive lock while that workspace is in use. A concurrent run requesting the same
workspace fails rather than sharing mutable process files.

## Seed configuration once

For an initial configuration that users or plugins may later change, set this on its `WorkspaceAsset`
builder:

```java
.mode(AssetInstallMode.SEED_ONCE)
```

Import `me.whereareiam.anvil.api.type.AssetInstallMode`. Anvil copies the source when the target is
absent and leaves an existing target in place. Continue using `ALWAYS` for the plugin JAR when every
execution should load the newly built plugin. See [assets](../assets/index.md) for the full declaration.

## Verify what survived

Persistence retains files, not live objects. A subsequent execution starts new processes and players.
Reconnect and read the application's stored state to verify persistence; a successful connection
alone does not prove that world data, account progress, or plugin configuration survived.

A process restart inside one scenario also uses its prepared workspace. It does not create a new
scenario execution or rerun the scenario setup hook. See
[process restarts](../../actions/index.md).

## Reset deliberately

Add a [before-start cleanup rule](../cleanup/index.md) for a particular generated path when you need a
repeatable reset. To discard the entire persistent fixture during development, stop every run using
it first and remove the retained process directory. Changing a scenario or process name selects a
different persistent location; it does not migrate the previous data.
