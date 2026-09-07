---
title: Overview
description: Run automated journeys against a declared environment and assert observable application behavior.
---

An automated test combines an environment declaration with a journey executed by your Java code.
The environment supplies the running processes and their files. The journey creates players, performs
actions, and uses assertions to decide whether the application behaved as expected.

Both automated tests and [interactive scenarios](../scenarios/index.md) use the same
[environment](../../building-blocks/environments/index.md) and
[player](../../building-blocks/players/index.md) building blocks. In this workflow, code drives the
steps and determines the result. Use the Scenarios workflow when you want to join or control a
running environment by hand.

## Build an automated journey

Start with the [first test](../../getting-started/first-test/index.mdx) for a runnable example, then:

1. [Define the environment](../../building-blocks/environments/definitions/index.md), choosing only
   the processes and connection routes needed by the behavior under test.
2. [Install the test inputs](../../building-blocks/environments/workspaces/assets/index.md), including
   the packaged plugin and any configuration or fixture data it needs.
3. [Organize the journey](./journeys/index.md), separating reusable setup from the assertions that
   describe each result.
4. [Create players](../../building-blocks/players/connections/index.md) and use their
   [capabilities](../../building-blocks/players/capabilities/index.md) to trigger the behavior.
5. [Wait for and assert the result](./assertions/index.md), choosing an observation that establishes
   the application's promise.

## Connect the workflow to a runner

The [JUnit integration](../../integrations/junit/index.md) selects a scenario, supplies its running
context, and closes it after each test method. The [Gradle integration](../../integrations/gradle/index.md)
resolves dependencies and artifacts and provides a dedicated live-test source set and task. An
[embedding application](../../integrations/embedding/index.md) can own execution directly.

Keep process configuration and player behavior in their shared building-block guides; these workflow
pages explain how to combine them into repeatable tests. Testing Anvil's own implementation has
additional repository conventions in [Contributing](../../contributing/index.md).
