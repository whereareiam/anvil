---
title: Overview
description: A developer framework for repeatable Minecraft server, proxy, and player journeys.
---

# Anvil

Anvil is a Java framework for testing Minecraft behavior against real processes. A scenario describes
the servers, proxies, distributions, and artifacts that make up an environment. A JUnit journey then
creates native-protocol players, performs actions, and asserts what the environment observes.

Anvil lives beside your plugin or integration project as a developer dependency. It does not run as a
plugin on your production server and does not replace your platform API. It builds or receives the
artifact under test, starts the declared processes, and cleans them up when the journey ends.

Use ordinary unit tests for isolated logic. Use Anvil when the behavior depends on a real server,
network session, installed artifact, proxy route, or observed player identity.

## The model

An Anvil run has four pieces:

- **Scenario** — an immutable declaration of named servers, proxies, entrypoint, distributions,
  workspace assets, and runtime policy.
- **Process** — one managed server or proxy with its own workspace, listener, console, and agent.
- **Player** — a context-owned client that speaks the selected native protocol.
- **Capability** — a typed player behavior, such as session control, messages, movement, inventory,
  interaction, or server observations.

The engine validates the complete topology before startup, prepares assets and caches, starts servers
before proxies, and releases resources in reverse order. Failed runs retain bounded diagnostics and
their workspace when configured to do so.

## What Anvil is for

Use ordinary unit tests for isolated logic. Use Anvil when behavior depends on an actual server or
proxy process, a packaged artifact, a network session, a native player, forwarding, or a reconnect and
restart sequence.

The same scenario can run as an automated JUnit test or as a foreground environment that a developer
can join. One declaration can therefore serve repeatable checks and interactive investigation.

The framework requires Java 21 or newer. Managed servers and proxies can require a newer Java runtime;
the selected platform provider supplies that requirement.

## Supported runtime

| Role | Implementations |
|---|---|
| Servers | Paper and Spigot |
| Proxies | Velocity and BungeeCord |
| Bundled native client backend | MCProtocolLib |
| Verified Minecraft versions | `1.21.11` and `26.1.2` |

The current compatibility matrix covers those versions directly and through every supported
server/proxy combination. Clients must be natively compatible with every server reachable through
their selected connection target. Anvil does not install ViaVersion or translate between versions
automatically. Docker is available as an execution provider for scenarios that need container
networking; local execution remains the default.

## Choose a starting point

- [Getting started](./getting-started/index.md) — install the Gradle integration and run a first live test.
- [Writing tests](./writing-tests/index.md) — compose scenarios, players, capabilities, and waits.
- [Running environments](./running-environments/index.md) — configure platforms, execution, Java,
  proxies, and process lifecycle.
- [Extending Anvil](./extending/index.md) — add capabilities, protocol providers, platform providers,
  or agent operations.
- [Contributing](./contributing/index.md) — change Anvil itself and run its verification suites.

Anvil currently orchestrates local JVM processes for Java Edition. Docker, Kubernetes, SSH or
hosted orchestration, Fabric, Sponge, Bedrock, rendering, pathfinding, and autonomous player AI are
outside the current scope.
