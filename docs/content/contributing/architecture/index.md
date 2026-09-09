---
title: Overview
description: Understand the global engine lifecycle and the scoped services assembled around it.
---

Anvil combines managed server/proxy processes and native-protocol players. The engine owns the
global scenario lifecycle. Scoped APIs describe cache access, provisioning, execution, platforms,
protocols, capabilities, and agents; the launcher binds those services into a scenario executor.

```text
Gradle / JUnit / embedded application
                 │
          launcher and extensions
                 │
             global engine
                 │
          ScenarioExecutor
                 │
           scenario assembly
                 ├─ platform planning → providers
                 ├─ execution → processes → process capabilities
                 └─ protocol → players → player capabilities
```

The global API contains scenario definitions, public running handles, and extension/lifecycle
registration. A service does not become global merely because several families need it. Each
consumer declares the channelOperation it needs, and assembly binds it to the appropriate scoped provider.

## Find the boundary you need

- [Module boundaries](./modules/index.md) maps responsibilities to Gradle families and dependencies.
- [Lifecycle and ownership](./lifecycle/index.md) explains startup, restarts, and cleanup.
- [Runtime composition](./composition/index.md) explains discovery and typed assembly bindings.

Packet behavior belongs to native capability implementations; native server services belong to
platform agents; endpoint translation belongs to execution providers. The engine works through
`ScenarioExecutor` and the public context rather than invoking any of those scoped services itself.

Capabilities have two owners: players and processes. Their shared composition validates
dependencies and owns instance cleanup; scoped providers supply the behavior. Player capabilities
can use either protocol channels or agent clients. Process capabilities expose process-wide behavior
without a player. Their public contracts stay independent of implementation; agent-backed providers
execute requests through native handlers in the server or proxy.

Run `./gradlew verifyArchitecture` after dependency or module changes. Dependency checks complement
the appropriate [behavioral tests](../testing/index.md).
