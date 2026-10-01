---
title: IntelliJ IDEA plugin
description: Resolve scenario discovery, sync, preparation, and version problems in the Anvil tool window.
---

The Anvil tool window reports its current problem in place of the scenario list, with a retry or
details action where one applies. Match the message to the checks below.

| Message or symptom | Next check |
|---|---|
| **Project is in Safe Mode** | Trust the project through IntelliJ; discovery resumes after the trust decision |
| **Project sync needs attention** | Select **View sync details**, fix the reported import error, then **Retry sync** |
| **Sync to discover scenarios** or **Set up Anvil** | Check that the build applies the standard Anvil Gradle plugin, then sync the project |
| **Sync project** is unavailable | Open or link the Gradle build in IntelliJ first; nested builds must be linked to appear |
| A scenario source appears but **No scenarios available** | Check that the source set contains compiled `AnvilScenarioDefinition` classes, then **Load scenarios** |
| **Could not load scenarios** | Select **Show preparation output** and fix the compilation or dependency-resolution error |
| Unsupported tooling protocol | Update the IDE plugin and the project's Anvil artifacts to matching versions, then sync |
| A run fails after its processes start | Read the run's Console, then follow [Startup and provisioning](../startup/index.md) |

Initial discovery requests a sync at most once when the project's model is missing. A failed or
cancelled sync keeps its diagnostics and does not start scenario preparation, and Anvil never retries
in a loop. Resource configuration warnings alone do not mean the sync failed.

Preparation compiles the project's scenario source set in a separate JVM, so its errors are ordinary
Gradle compilation and resolution errors. After fixing them, use **Load scenarios** or
**Refresh scenarios**; the IDE does not watch source edits.

If a problem remains, collect a [minimal issue report](../../reporting-issues/index.md) that includes
the preparation output or the run's console.
