---
title: Overview
description: Understand the boundaries between scenario orchestration, execution, players, and agents.
---

Anvil coordinates two kinds of work: managed server/proxy processes and native-protocol players.
The engine connects them through public contracts. Provider implementations own the technologies
that provision distributions, execute processes, or communicate with Minecraft.

```text
Gradle / JUnit / embedded application
                 │
              launcher
                 │
               engine
       ┌─────────┼───────────┐
   platforms   execution   protocol backend
       │          │             │
 configuration  processes    native players
                  │             │
             platform agents ← capabilities
```

A capability may act through a protocol service, an agent connection, or both. Its public API remains
usable without exposing the chosen packet library or platform SDK.

## Find the boundary you need

- [Module boundaries](./modules/index.md) maps responsibilities to Gradle families and dependencies.
- [Lifecycle and ownership](./lifecycle/index.md) explains startup, restarts, and cleanup.
- [Runtime composition](./composition/index.md) explains provider selection, worker isolation, and assemblies.

A new operation should enter through the owner of its behavior. Packet features belong to capability
adapters; native services belong to platform agents; endpoint translation belongs to execution
providers. Keep the engine focused on validation and lifecycle rather than platform-name or packet-type
branches.

Run `./gradlew verifyArchitecture` after dependency or module changes. Architecture checks protect
dependency direction; observable behavior still needs the appropriate [tests](../testing/index.md).
