---
title: Plugins and dependencies
description: Choose the Anvil Gradle entry point and install platform, protocol, and capability artifacts.
---

Use the umbrella plugin for the usual JUnit and foreground workflows. Keep all Anvil plugin and
library versions aligned. The [installation guide](../../../getting-started/installation/index.md)
contains repository configuration and a complete build script.

## Entry points

| Plugin ID | Adds |
|---|---|
| `me.whereareiam.anvil` | JUnit tests, foreground scenarios, and the default capability set |
| `me.whereareiam.anvil.junit` | JUnit integration and `anvilTest` |
| `me.whereareiam.anvil.scenarios` | Foreground scenario listing and execution |

All three install the shared source set, artifact registry, and authentication tasks. The narrower
entry points let you choose capability dependencies separately. Platform and capability unit
plugins also apply the scenarios plugin, so adding a unit can make foreground tooling available
in a JUnit project.

## Platform units

| Plugin ID | Provider and matching agent |
|---|---|
| `me.whereareiam.anvil.platform.paper` | Paper and the Bukkit agent |
| `me.whereareiam.anvil.platform.spigot` | Spigot and the Bukkit agent |
| `me.whereareiam.anvil.platform.velocity` | Velocity and its agent |
| `me.whereareiam.anvil.platform.bungeecord` | BungeeCord and its agent |

Apply a unit for every platform used in the scenario. The umbrella plugin does not select them.
Server and proxy setup lives under [Platforms](../../../building-blocks/environments/platforms/index.md).

## Capability units

The prefix is `me.whereareiam.anvil.capability.`. Available suffixes are `session`, `messages`,
`movement`, `inventory`, `interaction`, `server`, `console`, and `default`. The `default` unit supplies all
built-in capabilities and is applied by the umbrella plugin. Individual wiring artifacts bring
their required capability dependencies; the `server` unit does not install Session.

See [Player capabilities](../../../building-blocks/players/capabilities/index.md) for player actions
and observations. The `console` unit supplies the process's agent-backed
[Console capability](../../../building-blocks/environments/actions/agents/index.md).

## Dependency configurations

Use `add` in Kotlin DSL when registering dependencies explicitly:

```kotlin
dependencies {
	add("anvilProtocols", anvil.protocols.mcprotocol)
}
```

| Configuration | Purpose |
|---|---|
| `anvilFramework` | Compile-time framework APIs and integrations |
| `anvilLauncher` | Runtime launcher distribution |
| `anvilCapabilities` | Capability APIs and their selected wiring |
| `anvilProtocols` | Protocol providers, including authentication services |
| `anvilPlatforms` | Platform providers and matching agent artifacts |

The plugins populate framework and launcher dependencies. Platform units populate
`anvilPlatforms`; capability units populate `anvilCapabilities`. Adding a protocol dependency
installs its provider. `anvil { protocol("mcprotocol") }` selects that provider; it does not install
it by itself. External capability wiring can be added to `anvilCapabilities` using its published
Maven coordinate. Anvil does not need to be packaged into the plugin under test.
