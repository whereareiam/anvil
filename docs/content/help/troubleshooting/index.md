---
title: Overview
description: Locate the failure stage before changing the scenario or repeating a test.
---

Run the smallest failing test in your consumer project, keeping its distribution pins unchanged:

```shell
./gradlew anvilTest --tests 'com.example.test.PlayerJoinTest' --rerun-tasks
```

Replace the test class with yours. Read the Gradle test report and the first exception cause.
For process failures, find the named process's `anvil-console.log` in its workspace below the
configured `workDirectory`, normally `build/anvil`.

## Choose the failure stage

| Symptom | Guide |
|---|---|
| EULA, distribution, provider, Java, or startup failure | [Startup and provisioning](./startup/index.md) |
| Login, capability, native-version, identity, or routing failure | [Players and routing](./players/index.md) |
| Missing diagnostics, locked directories, or shutdown failure | [Cleanup and retained files](./cleanup/index.md) |

## Read the exception contract

| Exception | Meaning | Next check |
|---|---|---|
| `ScenarioValidationException` | A declaration cannot run as configured | Correct the named declaration before retrying |
| `ProvisioningException` | An artifact, Java installation, or workspace could not be prepared | Inspect the reported path, URI, checksum, and original cause |
| `ProcessException` | A process could not start, communicate, or stop | Use `getProcessName()` to find its console and workspace |
| `ScenarioStartupException` | A scenario setup hook failed | Use `getScenarioName()` and inspect the setup failure in `getCause()` |
| `CapabilityUnavailableException` | A player lacks a requested capability | Check installed wiring and selected protocol compatibility |

The general exceptions live in `me.whereareiam.anvil.api.exception`; scenario exceptions are in
its `scenario` subpackage. They extend `AnvilException`. A setup assertion can retain its original
assertion type, so use the stage and cause as well as the exception class. Provider implementations
can report their own exceptions.

Engine startup and lifecycle failures can retain workspaces. A JUnit test-body assertion alone
has different retention behavior in the current integration; read [Cleanup](./cleanup/index.md)
if the expected files are missing. If the cause remains unclear, collect a
[minimal issue report](../reporting-issues/index.md).
