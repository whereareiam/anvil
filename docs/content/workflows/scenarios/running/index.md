---
title: Running scenarios
description: List prepared environments and launch one discovered scenario from Gradle.
---

Run these commands from the Gradle project containing your `AnvilScenarioDefinition` classes.
Preparation compiles the source set and resolves its runtime; the selected scenario starts only
after the tooling JVM is ready.

## List and start a scenario

```shell
./gradlew anvilScenario --list --console=plain
./gradlew anvilScenario --scenario=local-paper --console=plain
./gradlew anvilScenario --definition=com.example.test.LocalPaperScenario --console=plain
```

`--list` evaluates the indexed definitions without starting Minecraft. Select exactly one of
`--scenario` and `--definition` when starting. A scenario name must be unique within its module;
the fully qualified definition class is always unambiguous.

Anvil prepares the declared inputs, starts every server and proxy in dependency order, and runs the
setup hook. The scenario's entrypoint is the default player connection target; it does not limit
which declared processes start.

## Control a running environment

The interactive session supports:

- `startAll` to start the remaining processes and complete setup;
- `start <process>` to start one prepared process;
- `stop <process>` and `restart <process>` for individual lifecycle operations;
- `stop` to finish the complete scenario;
- `quit` or `exit` to close the runner.

Starting one process does not start its peers or execute the scenario setup hook. A whole-scenario
restart prepares fresh process generations and runs setup again.
