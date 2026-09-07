---
title: Overview
description: Connect Anvil to a test framework or build tool and choose how your environments run.
---

Anvil's integrations serve different parts of a test workflow. Choose the test framework that owns
your assertions and the build tooling that prepares and runs them.

| Integration | Responsibility |
| --- | --- |
| [JUnit Jupiter](./junit/index.md) | Select a scenario with `@AnvilTest`, start it before each test, inject `ScenarioContext`, and close it afterwards. |
| [Gradle](./gradle/index.md) | Resolve dependencies and registered artifacts, compile scenario sources, configure execution, and provide test and foreground tasks. |
| [Embedding](./embedding/index.md) | Let a Java application own the engine and context lifetimes directly |

JUnit's scenario lifecycle can be used wherever JUnit Jupiter runs with the required Anvil runtime
dependencies. Gradle configures that runtime for its `anvilTest` task and also supplies foreground
scenario tooling that can run without JUnit.

## Use Gradle with JUnit

1. Select [Gradle plugins and dependencies](./gradle/plugins/index.md) for your environment.
2. Configure [build settings and artifacts](./gradle/dsl/index.md).
3. Choose a scenario through [JUnit selection and context injection](./junit/selection/index.md).
4. [Run the tests with Gradle](./gradle/testing/index.md) and inspect the results.

The [first test](../getting-started/first-test/index.mdx) walks through this path with a complete
example. Use [manual environments](../workflows/scenarios/running/index.md) for interactive sessions,
or [embed Anvil](./embedding/index.md) to own the engine and context directly.
