---
title: Core concepts
description: Understand how scenarios, processes, players, and capabilities fit together.
---

# Core concepts

Anvil has four concepts that appear in every live test:

- A [scenario](./scenarios.md) is the complete named environment, including its processes,
  entrypoint, and lifecycle policy.
- A [process](./processes.md) is one managed Minecraft server or proxy with a workspace, listener,
  console, and lifecycle state.
- A [player](./players.md) is a context-owned native-protocol client created for a journey.
- A [capability](./capabilities.md) is a typed group of player actions or observations supplied by
  the selected protocol and capability providers.

The scenario owns processes and players. A JUnit method receives a `ScenarioContext` while the
scenario is active, and the context releases its resources when the method finishes.

```text
AnvilTest
  └── ScenarioContext
      ├── ScenarioProcesses ── servers and proxies
      └── PlayerManager ────── SimulatedPlayer
                                └── PlayerCapability
```

Use [Writing tests](../../writing-tests/index.md) for task guides and [Running environments](../../running-environments/index.md)
for platform and process configuration.
