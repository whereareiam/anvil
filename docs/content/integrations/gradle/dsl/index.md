---
title: Gradle DSL
description: Configure shared Anvil task settings and register the artifacts used by scenarios.
---

Configure the extension in the `build.gradle.kts` of the project containing `src/anvil`.
This fragment assumes an Anvil entry-point plugin has already been applied:

```kotlin
anvil {
	acceptEula()
	protocol("mcprotocol")
	workDirectory.set(layout.buildDirectory.dir("anvil"))
	parallelism.set(2)
	startupMemoryMegabytes.set(4096)
	downloadParallelism.set(4)
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
| `protocol(id)` / `protocolId` | Select an installed protocol provider | Sole installed provider |
| `workDirectory` | Root for generated process workspaces | `build/anvil` |
| `cacheDirectory` | Shared artifact, Java, workspace-cache, and provider state root | `~/.anvil` |
| `parallelism` | Concurrent independent preparation/start operations | Engine detects from CPU count |
| `startupMemoryMegabytes` | Combined declared heaps permitted to start together | Engine detects from host memory |
| `downloadParallelism` | Concurrent artifact transfers | Engine detects from CPU count |
| `scenarioProviders` | Fully qualified `AnvilScenarioProvider` class names for the foreground runner | Empty list |
| `protocols.mcprotocol` | Version-aligned MCProtocol provider coordinate | Uses the plugin's framework version |

These shared settings are wired into the tasks. Additional JUnit test JVM properties belong in
`tasks.named<Test>("anvilTest")`; see [Engine options](../../../building-blocks/environments/configuration/engine/index.md) for that distinction.

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

## Register catalogs

```kotlin
anvil {
	scenarioProviders.add("com.example.test.DevelopmentScenarios")
}
```

The class must implement `AnvilScenarioProvider` and be on the Anvil runtime classpath. This setting
does not select the `AnvilScenarioDefinition` used by a JUnit annotation. See
[Catalogs and groups](../../../workflows/scenarios/catalogs/index.md).
