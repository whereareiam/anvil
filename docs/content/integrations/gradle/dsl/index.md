---
title: Gradle DSL
description: Configure shared Anvil task settings and register the artifacts used by scenarios.
---

Configure the extension in the `build.gradle.kts` of the project containing `src/anvil`.
This fragment assumes an Anvil entry-point plugin has already been applied:

```kotlin
anvil {
	acceptEula()
	engine {
		protocolLibrary("mcprotocol")
		workDirectory.set(layout.buildDirectory.dir("anvil"))
		parallelism.set(2)
		startupMemoryMegabytes.set(4096)
		downloadParallelism.set(4)
	}
	artifact("plugin-under-test", tasks.named("jar"))
}
```

Choose limits for the machine running the tests. `startupMemoryMegabytes` accounts for the
declared heaps of processes starting at the same time; it is not a total memory limit on all
running processes or a JVM heap setting.

## Settings

| Member | Meaning | Default |
|---|---|---|
| `acceptEula()` | Record explicit acceptance for managed servers | Not accepted |
| `engine.protocolLibrary(id)` | Default protocol library for players; a scenario or player may choose another | The installed library with the strongest support for each player's version |
| `engine.supportPolicy(policy)` | `lenient` runs `UNTESTED` versions with a warning; `strict` refuses them | `lenient` |
| `engine.protocolReleases(library, file)` | Additional release data for one protocol library, in that library's format | No additional releases |
| `engine.workDirectory` | Root for generated process workspaces | `build/anvil` |
| `engine.cacheDirectory` | Shared artifact, Java, and workspace-cache root | `~/.anvil` |
| `engine.accountsDirectory` | Local authenticated account files used by simulated players | `~/.anvil/accounts` |
| `engine.parallelism` | Concurrent independent preparation/start operations | Engine detects from CPU count |
| `engine.startupMemoryMegabytes` | Combined declared heaps permitted to start together | Engine detects from host memory |
| `engine.downloadParallelism` | Concurrent artifact transfers | Engine detects from CPU count |
| `module(artifact)` | Coordinate of one Anvil module at the plugin's version, such as `module("protocol-mcprotocol")` | Not applicable |

JUnit, foreground runs, and IDE preparation use the same lazy mapping of these settings. Task
realization does not freeze DSL values before the build script finishes configuring them.
Supported engine JVM properties, such as `-Danvil.offline=true`, `-Danvil.stopTimeout=PT9S`, and
`-Danvil.console.colors=false`, are forwarded to each workflow. The DSL's EULA and directory
conventions take precedence over those JVM properties; a configured protocol library, support
policy, and concurrency values also take precedence. An `-Danvil.protocolReleases.<library>=<file>`
property replaces the DSL file for that library. Unrelated JVM properties are not forwarded.

Additional JUnit test JVM properties belong in `tasks.named<Test>("anvilTest")`; see
[Engine options](../../../building-blocks/environments/configuration/engine/index.md) for that distinction.

## Register artifacts

`artifact(name, notation)` accepts a task provider, path/file notation, project, Gradle dependency,
or Maven coordinate. For example, in a multi-project build:

```kotlin
anvil {
	artifact("plugin-under-test", project(":plugin"))
	artifact("support-plugin", "com.example:support-plugin:1.2.3")
}
```

The second coordinate is an example; replace it with the dependency your test requires.
Names may contain letters, digits, `.`, `_`, and `-`. Register each name once, and ensure it resolves
to exactly one file. Gradle carries the producer task dependencies into scenario/test execution.

Use the name through `AssetSource.artifact(name)` to install a plugin or asset, or through
`Distribution.artifact(name)` to select a server/proxy executable. A named server executable also
needs its `minecraftVersion` in the scenario. See [Workspace assets](../../../building-blocks/environments/workspaces/assets/index.md).

Scenario definitions are discovered from the compiled `anvil` source set. There is no definition list
to maintain in Gradle. The standard Anvil plugin feeds the generated definition index into the
[IDE project declaration](../tooling/index.md). Sync the project after changing scenario sources,
then refresh the IDE scenario list.
