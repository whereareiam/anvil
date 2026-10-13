---
title: IntelliJ IDEA plugin
description: Resolve scenario discovery, sync, preparation, and version problems in the Anvil tool window.
---

The Anvil tool window reports its current problem in place of the scenario list, with a retry or
details action where one applies. Match the message to the checks below.

| Message or symptom | Next check |
|---|---|
| **Project is in Safe Mode** | Trust the project through IntelliJ; discovery resumes after the trust decision |
| **Project sync needs attention** | Select **View Sync Details**, fix the reported import error, then **Retry Sync** |
| **Sync to discover scenarios** or **Set up Anvil** | Check that the build applies the standard Anvil Gradle plugin, then sync the project |
| **Sync Project** is unavailable | Hover it for the reason. Open or link the Gradle build in IntelliJ first; nested builds must be linked to appear |
| A scenario source appears but **No scenarios** | Check that the source set contains compiled `AnvilScenarioDefinition` classes, then **Load Scenarios** |
| **Could not load scenarios** | Select **Show Output** and fix the compilation or dependency-resolution error, then **Try Again** |
| **Run Scenario** is unavailable | Hover it: the tooltip names the environment to stop first, or the sync or load to wait for |
| A definition class has no Run icon in the gutter | Check that the class is concrete, implements `AnvilScenarioDefinition`, and lies in an imported Anvil source |
| Unsupported tooling protocol | Update the IDE plugin and the project's Anvil artifacts to matching versions, then sync |
| A run fails after its processes start | Read the run's Console, then follow [Startup and provisioning](../startup/index.md) |

Initial discovery requests a sync at most once when the project's model is missing. A failed or
cancelled sync keeps its diagnostics and does not start scenario preparation, and Anvil never retries
in a loop. Resource configuration warnings alone do not mean the sync failed.

Preparation compiles the project's scenario source set in a separate JVM, so its errors are ordinary
Gradle compilation and resolution errors. After fixing them, use **Load Scenarios** or
**Reload Scenarios**; the IDE does not watch source edits.

If a problem remains, collect a [minimal issue report](../../reporting-issues/index.md) that includes
the preparation output or the run's console.
