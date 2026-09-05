---
title: Overview
description: Test packaged Minecraft plugins against real local servers and native-protocol players.
---

# Anvil

Anvil starts real Minecraft servers and proxies, installs your packaged plugin, and gives your tests
control of players that speak the native Minecraft protocol. The same scenario can run as a JUnit
test or remain open as a foreground environment for a developer to join.

Use ordinary unit tests for isolated plugin logic. Use Anvil for behavior that depends on an actual
server, network session, installed plugin, proxy route, or player identity.

## What a scenario does

1. Declares pinned distributions, server/proxy topology, and workspace assets.
2. Prepares the processes and installs the selected platform agents.
3. Creates players on demand and exposes their installed capabilities.
4. Runs assertions or a manual session.
5. Releases players and stops processes, preserving diagnostic workspaces on failure.

The framework targets Java 21. Managed servers can require a newer Java runtime; the selected
platform provider supplies that requirement.

## Supported runtime

| Role | Implementations |
|---|---|
| Servers | Paper and Spigot |
| Proxies | Velocity and BungeeCord |
| Bundled native client backend | MCProtocolLib |
| Verified Minecraft versions | `1.21.11` and `26.1.2` |

Both versions are covered directly and through every supported proxy/server combination. Clients
must be natively compatible with every server reachable through their selected connection target.
Anvil does not automatically install ViaVersion or translate between versions.

## Choose a starting point

- [Getting started](./getting-started/index.md): configure Gradle and run a JUnit scenario.
- [Writing tests](./writing-tests/index.md): define scenarios, install assets, and drive player journeys.
- [Running environments](./running-environments/index.md): select platforms, configure proxies, and run manual sessions.
- [Extending Anvil](./extending/index.md): add capabilities, protocol backends, or agent operations.
- [Contributing](./contributing/index.md): work on Anvil's architecture, internal tests, builds, and documentation.

Anvil currently orchestrates local JVM processes for Java Edition. Docker, Kubernetes, SSH/hosted
orchestration, Fabric, Sponge, Bedrock, rendering, pathfinding, and autonomous player AI are outside
the current scope.
