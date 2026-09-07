---
title: Overview
description: Prepare the environments and players shared by automated tests and human-run scenarios.
---

Anvil's building blocks are the resources your code prepares and controls. They are shared by both
workflows: an automated test can drive a journey through them, and a prepared scenario can leave
the environment running while you join with a real Minecraft client.

## Prepare the environment

[Environments](./environments/index.md) covers the server and proxy side: definitions, platforms,
executable distributions, workspace files, runtime settings, and process actions. Start with the
smallest topology the behavior needs, then add a proxy or another backend when it matters.

For example, a plugin test may need one Paper server with the packaged plugin installed. A manual
routing scenario may need a Velocity proxy, a lobby, and a second backend. Both use the same
Anvil environment declarations.

## Add players when needed

[Players](./players/index.md) covers simulated native clients: their connection targets, account
profiles, capabilities, and lifecycle. Create them in a test journey or in scenario setup when you
want them present in a prepared environment.

A simulated player is optional. A manual scenario can run with only the real client you join from.
When it does include simulated players, their actions are still supplied by your code; Anvil does
not turn them into autonomous actors.

## Choose how to use the result

Once the building blocks are ready, choose a [workflow](../workflows/index.md):

- [Testing](../workflows/testing/index.md) performs actions and evaluates assertions in code.
- [Scenarios](../workflows/scenarios/index.md) prepares reusable environments for a human to launch and test.

Use [Integrations](../integrations/index.md) for JUnit, Gradle, or direct embedding. Those entry points
supply the execution context around the same building blocks.

For method-level contracts, browse the [global API sources](https://github.com/whereareiam/anvil/tree/dev/anvil-api/src/main/java/me/whereareiam/anvil/api).
This link targets development code; select the matching repository tag when using a release.
