---
title: Defining scenarios
description: Declare pinned process groups and select environments for JUnit or catalog discovery.
---

# Defining scenarios

`AnvilScenario` describes a named process group. Its `entrypoint` is the default connection target
for newly created players. Server and proxy names are unique within the group.

Use `AnvilScenarioDefinition` for one environment selected by a JUnit `@AnvilTest` annotation.
Use `AnvilScenarioProvider` to register multiple scenarios and named groups for discovery, matrices,
or the foreground runner. The catalog layer does not replace the type-selected JUnit definition.

## Distribution selection

- `Distribution.remote(version, build)` selects a provider's explicit build.
- `Distribution.pinned(version, sha256)` selects checksum-pinned content when the provider supports it;
  this is the remote [Spigot](../../running-environments/platforms/spigot/index.md) selector.
- `Distribution.local(path)` uses a supplied executable JAR.
- `Distribution.artifact(name)` resolves a named Gradle/Maven artifact.

Local and named server artifacts also require `MinecraftServer.minecraftVersion`. Automated runs
use immutable builds or content pins; `latest` is reserved for manual scenarios and depends on
provider support. GetBukkit Spigot always requires an exact version and checksum.

## Choose the entrypoint

The entrypoint must name a declared server or proxy. Tests can override that target when creating a
player; see [creating players](../players/index.md). For a network with a proxy and several backends,
see [proxies and forwarding](../../running-environments/proxies/index.md).

Continue with [workspace assets and caches](../workspaces/index.md) or
[manual environments](../../running-environments/manual/index.md).
