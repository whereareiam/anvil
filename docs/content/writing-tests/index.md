---
title: Writing tests
description: Build Minecraft environments, create native protocol players, and assert observable behavior.
---

# Writing tests

An Anvil test has two parts: an `AnvilScenario` that describes the managed processes and a test
journey that drives one or more `SimulatedPlayer` instances. The scenario owns process startup,
workspace preparation, networking, and cleanup. The journey uses public capabilities to perform
actions and wait for observations.

For a new project, start with [installation and the first test](../getting-started/index.md). Once
Anvil is configured, follow this sequence:

1. [Define a scenario](scenarios/index.md) with a pinned process distribution and entrypoint.
2. [Select the JUnit integration](junit/index.md) or a scenario catalog for your runner.
3. [Create players](players/index.md) and choose their connection targets.
4. [Use player capabilities](capabilities/index.md) to drive protocol behavior.
5. [Wait for observations and make assertions](assertions/index.md).
6. [Install workspace assets and caches](workspaces/index.md) when a process needs files on disk.

Place consumer scenarios and journeys in `src/anvil`. Keep ordinary unit tests in `src/test`, and
run managed journeys with `./gradlew anvilTest`.

## The test lifecycle

Anvil prepares declared assets and caches, validates the topology, starts servers before proxies,
and supplies a `ScenarioContext` to the test. When the test finishes, the context destroys players
and stops processes in reverse order. A failed run retains its diagnostic workspace and bounded
console output so that the failure can be investigated.

Configuration for Java runtimes, process execution, platforms, and proxy topology lives under
[running environments](../running-environments/index.md). Anvil implementation tests belong under
[contributing](../contributing/testing/index.md).
