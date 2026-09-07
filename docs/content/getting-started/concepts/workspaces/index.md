---
title: Workspaces
description: Give each process its plugins, configuration, and generated files with an explicit lifetime.
---

A workspace is the working directory prepared for one declared server or proxy. It contains its
installed plugins, configuration, worlds, logs, and other working files. Each server or proxy has a
separate workspace, even when they use the same platform.

The executable is supplied through [platform provisioning](../../../building-blocks/environments/provisioning/platform/index.md)
and can remain in the shared cache or at a supplied path. A process's working directory does not
necessarily contain the JAR used to launch it.

For a welcome-message test, the workspace is how your packaged plugin reaches the server. For a
world-dependent test, it is where you seed the world and plugin data that make the test repeatable.
A Paper workspace might contain these files after startup:

```text
server workspace/
├── plugins/
│   └── plugin-under-test.jar
├── server.properties
├── world/
├── logs/
└── anvil-console.log
```

This illustrates the contents, not a fixed directory name or layout contract for every platform.

## Declare the plugin input

Assume the test runtime has registered your deployable JAR under the artifact name
`plugin-under-test`. With Gradle, [artifact registration](../../../building-blocks/environments/workspaces/assets/index.md)
resolves the build output and its producing task; an embedded application supplies the named path
through `EngineOptions`.

The following fragment creates a workspace plan inside a scenario definition. Add these imports:

```java
import me.whereareiam.anvil.api.model.workspace.AssetSource;
import me.whereareiam.anvil.api.model.workspace.WorkspaceAsset;
import me.whereareiam.anvil.api.model.workspace.WorkspacePlan;

import java.nio.file.Path;
```

```java
WorkspacePlan workspace = WorkspacePlan.builder()
		.asset(WorkspaceAsset.builder()
				.source(AssetSource.artifact("plugin-under-test"))
				.target(Path.of("plugins", "plugin-under-test.jar"))
				.build())
		.build();
```

Pass `workspace` to `.workspace(workspace)` on the `MinecraftServer` builder before `.build()`.
When that scenario starts, Anvil copies the registered JAR to this server's `plugins` directory.
The target is relative to the process workspace. The JAR must already be a valid plugin for the
selected platform. Confirm that it loaded through its startup output or an application assertion;
the presence of a file alone does not prove the plugin works.

## Choose how files are reused

| Choice | Typical use |
|---|---|
| Asset | Install an input such as your plugin JAR, a configuration file, or a fixture world |
| Fresh workspace | Start a test with a new process directory instead of leftovers from an earlier run |
| Persistent workspace | Keep the process directory between runs, for example while debugging manually |
| Workspace cache | Restore and save a selected generated path, such as downloaded server libraries |
| Cleanup rule | Remove a selected path at a declared lifecycle phase |

These are separate choices. A persistent workspace does not keep the process running after the
scenario closes. A cache does not automatically include all plugin data, and an asset's install
mode determines whether existing files at its target are replaced.

## Keep the two directory lifetimes distinct

Process workspaces normally live below `build/anvil`. The shared cache normally lives at
`~/.anvil` and holds reusable distributions, Java runtimes, and declared workspace snapshots.
Changing the cache directory does not make a process workspace persistent.

A successful scenario normally removes disposable workspaces. Retention after failure depends on
the recorded lifecycle outcome; a JUnit test-body assertion alone does not guarantee retention.
Use a [persistent workspace](../../../building-blocks/environments/workspaces/persistence/index.md) when you need
the files after every outcome, and read [cleanup behavior](../../../help/troubleshooting/cleanup/index.md)
before relying on diagnostics or cached state.

The [workspace guides](../../../building-blocks/environments/workspaces/index.md) cover detailed installation,
persistence, caching, and cleanup workflows.

Next: [Running context](../context/index.md) connects the declaration and its prepared inputs to
the active environment your test can use.
