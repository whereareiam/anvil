---
title: Overview
description: Choose automated verification or a prepared environment for human testing.
---

Use the same [environment and player building blocks](../building-blocks/index.md) in either of
Anvil's two workflows. Choose according to who performs the actions and evaluates the result.

| Workflow | Actions and evaluation | Typical use |
|---|---|---|
| [Testing](./testing/index.md) | Java code drives the journey and assertions decide whether it passed | Repeat a plugin regression test after each change |
| [Scenarios](./scenarios/index.md) | A human launches a prepared environment, joins through a real client, and inspects behavior | Explore a bug, test an interactive feature, or reproduce a network setup |

## Automated testing

Write a test when you can express the expected behavior as a repeatable sequence with observable
results. The test can use a simulated player, interact through the process console, or call an
installed agent operation. A failing assertion makes a regression visible without repeating the
steps by hand.

Follow [Testing](./testing/index.md) for journey design and assertions. The
[JUnit integration](../integrations/junit/index.md) supplies scenario lifecycle and context injection;
[Gradle](../integrations/gradle/index.md) supplies the corresponding build and test task.

## Prepared scenarios for human testing

Create a named scenario when you want to bring up the same environment repeatedly and test it
yourself. Its declaration supplies the platforms, plugins, configuration, and common setup. The
runner starts it and exposes the address you use in your own Minecraft client.

Follow [Scenarios](./scenarios/index.md) to prepare a catalog, start an environment, join it, and use
the interactive controls. Scenario setup can also create simulated players when human testing
needs other participants; their behavior is defined by code.

## Share definitions between the two

`AnvilScenario` is the environment declaration used by both workflows. A manual scenario can be
derived from an automated test's definition and registered in a catalog. Moving a manual check into
an automated test means adding code-driven actions and assertions, rather than rebuilding the
whole server setup.

For an application that owns execution directly, see [Embedding](../integrations/embedding/index.md).
Embedding provides another integration with Anvil; the application still decides how to drive and
evaluate the environment.
