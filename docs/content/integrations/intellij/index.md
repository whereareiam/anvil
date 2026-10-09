---
title: Overview
description: Run Anvil environments and inspect their processes, players, and console output in IntelliJ IDEA.
---

The Anvil plugin lists your project's scenario definitions in an **Anvil** tool window. Start a
complete environment or a single server or proxy, then follow it in a session tab: live process
state, per-process console output and commands, and the players the scenario connects.

Your project supplies the scenario definitions, platforms, protocol library, artifacts, and runtime
settings. The plugin supports Gradle projects that apply the standard Anvil Gradle plugin; Maven and
other build tools are not supported.

## Get started

1. [Install the plugin](./installation/index.md) from JetBrains Marketplace and match your project's
   Anvil version.
2. [Run a scenario](./scenarios/index.md): open the Anvil tool window, select a scenario, and start it.
3. [Inspect the run](./sessions/index.md) in its Environment, Console, and Players views.

Manage [project accounts and pools](./accounts/index.md) from the **Accounts…** toolbar action, and
adjust [preferences](./settings/index.md) in **Settings → Tools → Anvil**. If scenarios do not load or
a run fails, see [IntelliJ troubleshooting](../../help/troubleshooting/intellij/index.md).

Use [scenario definitions](../../workflows/scenarios/definitions/index.md) to declare the environments
shown in the IDE. [Gradle project tooling](../gradle/tooling/index.md) explains how the imported
project model and runtime preparation work.

## Limitations

- One environment runs per IntelliJ project at a time.
- Running an environment does not execute its JUnit journey or report a test result. Use the
  [testing workflow](../../workflows/testing/index.md) for automated assertions.
- Debugger attachment, gutter actions, Gradle DSL inspections, and log severity filters are not available.
