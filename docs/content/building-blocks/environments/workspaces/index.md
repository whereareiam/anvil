---
title: Overview
description: Install process files, choose their lifetime, reuse selected snapshots, and control cleanup.
---

A workspace is the working directory of one server or proxy. It holds the plugins, configuration,
worlds, logs, and other files that process reads or generates. Each process has its own workspace.

[Provisioning](../provisioning/index.md) supplies the executable and Java runtime separately. An
executable can remain in the download cache or at a supplied path while the process uses its
workspace as the working directory. Do not assume every executable JAR is copied into that directory.

## Choose the workspace behavior

Declare a `WorkspacePlan` on the server or proxy builder. Its four concerns are independent:

| Guide | What it controls |
|---|---|
| [Assets](./assets/index.md) | Place supplied plugin JARs, configuration, and fixture files at workspace-relative targets |
| [Persistence](./persistence/index.md) | Choose whether a process gets a fresh directory or reuses its retained files |
| [Snapshots](../provisioning/cache/snapshots/index.md) | Restore and save selected generated paths across executions |
| [Cleanup](./cleanup/index.md) | Delete selected paths at declared lifecycle phases |

Start with assets in a fresh workspace for a repeatable test. Choose persistence when retaining the
whole directory is part of the task, or use snapshots to reuse specific generated paths. Add cleanup
rules for files that should be reset at a defined point. Assigning the same `group` label to declarations
does not couple their behavior.

Process workspaces normally live below `build/anvil`.

These file rules are shared by [Testing](../../../workflows/testing/index.md) and
[Scenarios](../../../workflows/scenarios/index.md). Choose the workflow after preparing the inputs;
[environment configuration](../configuration/index.md) sets the host directories and execution limits.
