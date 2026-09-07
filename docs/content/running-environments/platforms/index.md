---
title: Platforms and versions
description: Select server and proxy providers, Java runtimes, and native client versions.
---

# Platforms and versions

Platforms are explicit Gradle unit plugins. Apply one for every platform used in your scenarios:

| Unit plugin suffix    | Role   | Distribution source               |
|-----------------------|--------|-----------------------------------|
| `platform.paper`      | Server | PaperMC Fill build                |
| `platform.spigot`     | Server | SHA-256-pinned GetBukkit JAR      |
| `platform.velocity`   | Proxy  | PaperMC Fill build                |
| `platform.bungeecord` | Proxy  | Selected BungeeCord Jenkins build |

The full plugin prefix is `me.whereareiam.anvil.`. Each unit supplies its provider and matching
platform-agent artifact. Paper and Spigot share the Bukkit agent. Agents are installed into managed
workspaces automatically; consumers do not allocate agent ports or provide session tokens.

## Verified combinations

Minecraft `1.21.11` and `26.1.2` are verified with direct Paper and Spigot connections and with
Velocity or BungeeCord routing to either server. The internal matrix uses Paper builds `132` and
`74`, Velocity `3.5.1` build `615`, and BungeeCord build `2085`. GetBukkit pins are listed on the
[Spigot page](./spigot/index.md).

Server and client versions are separate from the proxy's own version. A local or named server JAR
must declare `minecraftVersion` so Anvil can select a compatible native client.

## Java requirements

Platform providers declare their minimum Java feature version. A server or proxy can declare a
separate `JavaRequirement` with a distribution and exact release. Local homes and verified archives
are execution-provider inputs; Docker image mappings are Docker settings.

## Configuration formats

Server `.setting(key, value)` entries are Java properties. BungeeCord settings use YAML values.
Velocity settings use TOML literals: `"128"` is an integer, `"true"` a boolean, `"[1, 2]"` an array,
and `"'hello'"` a string. Velocity dotted keys update nested tables.

Writers parse existing files, preserve unrelated values, and replace runtime-owned settings.
Formatting and comments can change during serialization. Duplicate YAML keys and malformed
documents fail rather than becoming an ambiguous generated configuration.

Another platform can implement the `PlatformProvider` SPI. Its provider owns distribution,
configuration, Java, readiness, and forwarding support; its native API types stay outside the
global Anvil API. See [architecture](../../contributing/architecture/index.md).
