---
title: Overview
description: Declare world servers and install the platform unit for Paper, Spigot, or NeoForge.
---

A `MinecraftServer` runs a world and its server plugins or mods. It can be the scenario entrypoint or a backend
registered with one or more proxies.

## Choose a server platform

| Platform | Gradle plugin ID | Distribution |
|---|---|---|
| [Paper](./paper/index.md) | `me.whereareiam.anvil.platform.paper` | PaperMC Fill version and build |
| [Spigot](./spigot/index.md) | `me.whereareiam.anvil.platform.spigot` | GetBukkit version and SHA-256 |
| [NeoForge](./neoforge/index.md) | `me.whereareiam.anvil.platform.neoforge` | Minecraft version and NeoForge release |

Apply the unit at the same Anvil version as the main plugin. Each unit includes its platform's agent,
which enables native server observations and operations: the Bukkit agent for Paper and Spigot, and
the NeoForge agent for NeoForge.

## Configure shared server behavior

Each server declares a unique name, platform, and distribution. The default heap is 1024 MiB and
`onlineMode` defaults to `false`. Use `.memoryMegabytes(...)`, `.onlineMode(...)`, and
`.jvmArgument(...)` for process-specific choices.

A server's `.setting(key, value)` entries update `server.properties`. Install other configuration
files using [workspace assets](../../workspaces/index.md). Anvil applies its listener,
authentication, EULA, and forwarding values after assets are prepared; those runtime values take
precedence. Existing unrelated configuration values are retained, while formatting and comments may
change when a file is serialized.

Add the server with `.server(server)` on the scenario. For a direct environment, set
`.entrypoint(server.getName())`; for a proxied environment, use the
[proxy topology guide](../proxies/index.md). Only Paper and Spigot servers can be proxy backends.
