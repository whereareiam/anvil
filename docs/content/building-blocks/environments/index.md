---
title: Overview
description: Define, prepare, and control servers and proxies for either Anvil workflow.
---

An environment contains the server and proxy processes a test or prepared scenario needs. Describe
its topology and inputs once, then reuse that declaration for automated verification or a manual
session with a real client.

The Java declaration is `AnvilScenario`. `AnvilScenarioDefinition` supplies one reusable declaration;
`AnvilScenarioProvider` can expose several declarations through a catalog. Neither requires that
players be created inside the declaration: a running context can create them when needed.

## Build the environment

1. [Define the environment](./definitions/index.md): name its processes and select an entrypoint.
2. [Choose platforms](./platforms/index.md): select server and proxy implementations.
3. [Provision the software](./provisioning/index.md): select executable artifacts, Java, and acquisition policy.
4. [Prepare workspaces](./workspaces/index.md): install your plugin and choose file persistence, snapshots, and cleanup.
5. [Configure execution](./configuration/index.md): choose host directories, execution settings, and limits.
6. [Prepare live state](./definitions/index.md#prepare-live-state) with a setup hook when the shared
   starting point needs ready processes or players.

The [first test](../../getting-started/first-test/index.mdx) provides a minimal Paper declaration you
can expand. Declare only the processes and inputs relevant to the behavior you want to inspect.

## Control the running processes

Use [Actions](./actions/index.md) to issue console commands, call installed agent operations, and
restart a server or proxy. These guides use a running `ScenarioContext` and its named process
handles. The scenario owns process startup and shutdown.

[Players](../players/index.md) is the separate building block for native clients and their
capabilities. Its APIs let a test or setup hook add participants to this environment.

## Reuse it across workflows

For [Testing](../../workflows/testing/index.md), the test starts the environment, performs a journey,
and asserts the results. For [Scenarios](../../workflows/scenarios/index.md), a catalog exposes a
prepared environment that stays available while a human joins and tests it. A manual variant can
reuse the same definition while changing only the settings needed for that workflow.

[Integrations](../../integrations/index.md) covers the entry points. If preparation or execution
fails, start with [Troubleshooting](../../help/troubleshooting/index.md).
