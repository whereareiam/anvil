---
title: Overview
description: Configure the host running Anvil and distinguish engine options from managed process settings.
---

Configure the host running Anvil, then keep topology, process memory, and readiness deadlines with
the environment declaration that needs them. These settings apply to both automated tests and
prepared scenarios.

The examples use the [Gradle integration](../../../integrations/gradle/index.md), where shared
settings belong in `build.gradle.kts`. An [embedding application](../../../integrations/embedding/index.md)
can supply the same engine settings through `EngineOptions`.

## Set the shared environment

Add this fragment to your existing build:

```kotlin
anvil {
	acceptEula()
	engine {
		protocolLibrary("mcprotocol")
		workDirectory.set(layout.buildDirectory.dir("anvil"))
		parallelism.set(2)
		startupMemoryMegabytes.set(2048)
		downloadParallelism.set(4)
		processors.set(4)
		processPriority.set("low")
	}
}
```

`acceptEula()` records explicit EULA acceptance. `protocolLibrary("mcprotocol")` makes an installed
protocol library the default for every player; it does not add the dependency. Without it, each
player uses the installed library with the strongest support for its Minecraft version, and a
scenario or player declaration can still choose another library.

The concurrency limits affect independent preparation and startup work. The memory value limits
the sum of declared process heaps starting at once; it does not set each process's heap or cap
all memory used by the running environment. Set `.memoryMegabytes(...)` on a server or proxy to
change its heap allocation.

`processors` limits the processors each server and proxy assumes, and `processPriority` set to `low`
lets them yield to other work on the machine; see
[engine options](./engine/index.md#lifetimes-and-limits).

## Choose the right configuration layer

| Layer | Put these choices here |
|---|---|
| Gradle `anvil` block | EULA, artifacts, and `engine` launch defaults |
| `AnvilScenario` | Processes, entrypoint, execution provider, Java defaults, and startup timeout |
| `MinecraftServer` or `MinecraftProxy` | Distribution, workspace, Java override, heap, platform settings, and child JVM arguments |
| `EngineOptions` | Shared options when [embedding](../../../integrations/embedding/index.md) |
| JVM properties on the Anvil host | Options for JUnit or the runner without a dedicated DSL field |

JVM arguments supplied through `.jvmArgument(...)` run in the managed server or proxy. They do not
configure Anvil's cache, provider selection, or shutdown timeout.

## Choose storage and execution

- [Directories and timeouts](./directories/index.md) covers workspace retention and deadlines.
- [Execution providers](./execution/index.md) covers host processes and the embedded Docker path.

Software selection belongs under [Provisioning](../provisioning/index.md), including
[Java](../provisioning/java/index.md). For exact engine option names, accepted values, and defaults,
use [Engine options and JVM properties](./engine/index.md).
