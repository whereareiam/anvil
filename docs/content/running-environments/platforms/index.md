---
title: Platforms and versions
description: Select server and proxy providers, Java runtimes, and native client versions.
---

# Platforms and versions

Platforms are explicit Gradle unit plugins. Apply one for every platform used in your scenarios:

| Unit plugin suffix | Role | Distribution source |
|---|---|---|
| `platform.paper` | Server | PaperMC Fill build |
| `platform.spigot` | Server | SHA-256-pinned GetBukkit JAR |
| `platform.velocity` | Proxy | PaperMC Fill build |
| `platform.bungeecord` | Proxy | Selected BungeeCord Jenkins build |

The full plugin prefix is `me.whereareiam.anvil.`. Each unit supplies its provider and matching
platform-agent artifact. Paper and Spigot share the Bukkit agent. Agents are installed into managed
workspaces automatically; consumers do not allocate agent ports or provide session tokens.

## Verified combinations

Minecraft `1.18.2`, `1.19.4`, `1.20.6`, `1.21.11`, `26.1.2`, and `26.2` are covered by direct Paper
and Spigot connections and Velocity/BungeeCord routes to either server: 36 native routes in total.
The Paper builds are respectively `388`, `550`, `151`, `132`, `74`, and `121`. The matrix uses
Velocity `3.5.1` build `615` and BungeeCord build `2085`. GetBukkit pins are listed on the
[Spigot page](./spigot/index.md).

Server and client versions are separate from the proxy's own version. A local or named server JAR
must declare `minecraftVersion` so Anvil can select a compatible native client.

## Java selection

The framework and protocol workers target Java 21. Servers in `26.*` require Java 25; 1.20.6 and
1.21.11 use Java 21. Older Paper/Spigot declare Java 17 as their minimum; Spigot 1.18.2 rejects JVMs
above Java 18 and Spigot 1.19.4 rejects JVMs above Java 20. The engine honors those upper bounds and
can provision Java 17. Bukkit agents support Java 17; plugins under test must also be compiled for
their server JVM. Provider requirements guide engine Java selection.
Set explicit executables where needed:

```kotlin
anvil {
    javaExecutables.put(25, "/path/to/jdk-25/bin/java")
}
```

The engine also considers its current Java runtime and `JAVA_<version>_HOME`, and can provision a
checksum-verified Temurin runtime when no suitable local runtime is available. Configure executable
paths on the machine running the scenarios, not on the Minecraft client machine.

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
