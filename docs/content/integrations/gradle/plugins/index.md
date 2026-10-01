---
title: Plugins and dependencies
description: Choose the Anvil Gradle entry point and install platform, protocol, and capability artifacts.
---

Choose scenario execution, JUnit testing, or both. Keep all Anvil plugin and library versions
aligned. The [installation guide](../../../getting-started/installation/index.md) contains
repository configuration and a complete build script.

## Entry points

| Plugin ID | Adds |
|---|---|
| `me.whereareiam.anvil` | Foreground scenario listing/execution and IDE project discovery |
| `me.whereareiam.anvil.junit` | JUnit integration and `anvilTest` |

Each entry point installs shared Gradle declarations, the `anvil` source set, and artifact registration.
Neither selects platform, protocol, or capability providers. Apply both
plugins when the same project needs automated tests and interactive environments:

```kotlin
plugins {
	id("me.whereareiam.anvil") version "0.0.1"
	id("me.whereareiam.anvil.junit") version "0.0.1"
}
```

Plugin order does not change the shared configuration. For JUnit only, omit the first plugin;
for scenario execution and IntelliJ discovery without JUnit, omit the second. Installing the
[IntelliJ plugin](../../intellij/index.md) requires no additional IDE dependency in the build.
The JUnit entry point does not register project discovery; apply the standard Anvil plugin to expose
its compiled scenario definitions to IntelliJ as well. JUnit methods still execute through JUnit.

Platform and capability unit plugins add their dependencies and shared declarations. They do not
select an execution workflow. Use `me.whereareiam.anvil.capability.default` explicitly when you
want the complete built-in capability set.

The standard `me.whereareiam.anvil` plugin also supplies IDE discovery and the executable tooling
runtime. See [IDE project tooling](../tooling/index.md) for preparation and definition discovery.

## Platform units

| Plugin ID | Provider and matching agent |
|---|---|
| `me.whereareiam.anvil.platform.paper` | Paper and the Bukkit agent |
| `me.whereareiam.anvil.platform.spigot` | Spigot and the Bukkit agent |
| `me.whereareiam.anvil.platform.velocity` | Velocity and its agent |
| `me.whereareiam.anvil.platform.bungeecord` | BungeeCord and its agent |

Apply a unit for every platform used in the scenario. The execution plugins do not select them.
Server and proxy setup lives under [Platforms](../../../building-blocks/environments/platforms/index.md).

## Capability units

The prefix is `me.whereareiam.anvil.capability.`. Available suffixes are `session`, `messages`,
`movement`, `inventory`, `interaction`, `server`, `console`, and `default`. The `default` unit supplies all
built-in capabilities when explicitly selected. Individual wiring artifacts bring
their required capability dependencies; the `server` unit does not install Session.

See [Player capabilities](../../../building-blocks/players/capabilities/index.md) for player actions
and observations. The `console` unit supplies the process's agent-backed
[Console capability](../../../building-blocks/environments/actions/agents/index.md).

## Dependency configurations

Protocol providers can be registered through the standard Anvil source-set configuration:

```kotlin
dependencies {
	add("anvilRuntimeOnly", "com.example:custom-protocol:1.0.0")
}
```

| Configuration | Purpose |
|---|---|
| `anvilImplementation` | Scenario and integration implementation dependencies |
| `anvilCompileOnly` | Compile-time-only scenario dependencies |
| `anvilRuntimeOnly` | Runtime providers, launcher, and platform artifacts |
| `anvilCompileClasspath` | Resolved compile classpath for the Anvil source set |
| `anvilRuntimeClasspath` | Resolved runtime classpath for Anvil tasks |

The plugins add their dependencies directly to the source-set configurations. Platform and
capability units configure those dependencies internally. Custom protocol providers belong on
`anvilRuntimeOnly`; `anvil { engine { protocol("mcprotocol") } }` selects one when an explicit
choice is needed. Anvil does not need to be packaged into the plugin under test.
