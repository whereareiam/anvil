---
title: Overview
description: Configure Anvil dependencies, source sets, artifacts, and tasks in a Gradle build.
---

The Gradle integration prepares the code and runtime that Anvil runs. It resolves dependencies and
registered artifacts, compiles scenario sources, and supplies tasks for automated tests and
foreground environments.

Choose the JUnit entry point when Gradle should run automated journeys. Inside that task's JVM,
[JUnit](../junit/index.md) owns scenario selection, context injection, test execution, and cleanup.
The foreground scenarios entry point can run without JUnit.

## Configure and run the build

1. [Plugins and dependencies](./plugins/index.md): choose the entry point and install the runtime components.
2. [Gradle DSL](./dsl/index.md): configure shared settings and register your plugin artifacts.
3. [Running tests](./testing/index.md): execute JUnit journeys and inspect Gradle reports.
4. [Tasks and options](./tasks/index.md): look up compilation, foreground, and authentication commands.

For a complete first project, follow [Installation](../../getting-started/installation/index.md).
For a manual environment, proceed from build configuration to the
[foreground runner](../../workflows/scenarios/running/index.md).

## Source-set conventions

The plugins create an `anvil` source set. Java sources belong in `src/anvil/java`, and resources
belong in `src/anvil/resources`. Production code remains in `src/main`; ordinary tests remain in
`src/test`. These paths are Gradle integration conventions, not requirements of Anvil's JUnit API.

The Anvil source set sees the main project's output and test dependencies. Test classes from
`src/test` are not automatically compiled into `src/anvil`; place reusable test code in a fixture
dependency when both source sets need it.
