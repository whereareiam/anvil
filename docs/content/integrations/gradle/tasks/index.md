---
title: Tasks and options
description: Compile Anvil sources, run JUnit tests, select manual scenarios, and manage authentication profiles.
---

Run these commands from your consumer project's root. The corresponding entry-point plugin must
be installed; see [Plugins and dependencies](../plugins/index.md).

## Compilation and JUnit

| Command | Result |
|---|---|
| `./gradlew anvilClasses` | Compile sources and process resources without starting Minecraft |
| `./gradlew anvilTest` | Run JUnit tests in the Anvil source set |
| `./gradlew anvilTest --tests 'com.example.test.PlayerJoinTest'` | Select a test class; replace the example name with yours |
| `./gradlew test -Panvil.testMode=full` | Include `anvilTest` as a dependency of ordinary `test` |

`anvilTest` is a Gradle `Test` task using JUnit Platform. Its results are under
`build/test-results/anvilTest` and its HTML report is under `build/reports/tests/anvilTest`.
Gradle's normal task up-to-date rules apply; use `--rerun-tasks` when you need to repeat an otherwise
unchanged run. The plugin does not make every invocation bypass Gradle's task state.

## Foreground scenarios

```shell
./gradlew anvilScenario --list
./gradlew anvilScenario --scenario=manual-paper
./gradlew anvilScenario --group=development
```

The names are examples declared by your catalog. Configure `anvil.scenarioProviders` or provide
`--provider=com.example.test.DevelopmentScenarios` explicitly.

| Option | Meaning |
|---|---|
| `--list` | List scenarios and groups without starting them |
| `--scenario=<name>` | Start one named scenario |
| `--group=<name>` | Open one named interactive group |
| `--provider=<class>` | Select one scenario-provider class |

Use `--list` alone, or exactly one of `--scenario` and `--group`. Listing can enumerate all
configured providers; starting requires a single provider or an explicit selection. These tasks
are interactive and are not Gradle build-cache outputs. The available shell commands are documented
in [Manual environments](../../../workflows/scenarios/running/index.md).

## Authentication

```shell
./gradlew anvilLogin --auth-profile=main
./gradlew anvilLogout --auth-profile=main
```

`--auth-profile` is required. It names private provider-owned account state, not an access token.
Gradle reserves `--profile` for its build profiler. Authentication tasks use the selected provider
from `anvilProtocols`; an offline-only provider may have no interactive authentication service.
See [Authentication](../../../building-blocks/players/authentication/index.md) before using online players.
