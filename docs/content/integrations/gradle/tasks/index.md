---
title: Tasks and options
description: Compile Anvil sources, run JUnit tests, select manual scenarios, and sign in accounts.
---

Run these commands from your consumer project's root. The corresponding entry-point plugin must
be installed; see [Plugins and dependencies](../plugins/index.md). `./gradlew tasks` lists every task
below under **Anvil tasks**, except the IDE preparation task, which editors run themselves.

| Task | Plugin | Result |
|---|---|---|
| `anvil` | JUnit | Run `anvilTest` and every suite task |
| `anvilTest` | JUnit | Run the live tests that belong to no suite |
| `anvil<Name>Test` | JUnit | Run one [suite](../testing/index.md#split-the-live-tests-into-suites) |
| `anvilScenarios` | Anvil | List the discovered scenarios |
| `anvilRun` | Anvil | Start one scenario in the foreground |
| `anvilLogin` | Anvil | Sign an account in |
| `anvilLogout` | Anvil | Remove a stored account |

## Compilation and JUnit

| Command | Result |
|---|---|
| `./gradlew anvilClasses` | Compile sources and process resources without starting Minecraft |
| `./gradlew anvil` | Run every live test: `anvilTest` and every suite task |
| `./gradlew anvilTest` | Run the JUnit tests in the Anvil source set that belong to no suite |
| `./gradlew anvilTest --tests 'com.example.test.PlayerJoinTest'` | Select a test class; replace the example name with yours |
| `./gradlew anvil<Suite>Test` | Run the JUnit tests of one [suite](../testing/index.md#split-the-live-tests-into-suites), such as `anvilUpgradeTest` |

`anvilTest` and every suite task are `AnvilTest` tasks, a Gradle `Test` type using JUnit Platform. Their
results are under `build/test-results/<task>` and their HTML reports under `build/reports/tests/<task>`.
Gradle's normal task up-to-date rules apply; use `--rerun-tasks` when you need to repeat an otherwise
unchanged run. The plugin does not make every invocation bypass Gradle's task state. Ordinary `test`
and `check` never start these tasks.

## Foreground scenarios

```shell
./gradlew anvilScenarios
./gradlew anvilRun --scenario=manual-paper
./gradlew anvilRun --definition=com.example.test.ManualPaperScenario
```

`anvilScenarios` lists the scenarios without starting them. Their names come from compiled
`AnvilScenarioDefinition` classes in the Anvil source set. `anvilRun` starts one of them:

| Option | Meaning |
|---|---|
| `--scenario=<name>` | Start one named scenario |
| `--definition=<class>` | Start one definition class directly, for example when scenario names are ambiguous |

Give `anvilRun` exactly one of `--scenario` and `--definition`. The runner
executes in a separate JVM using the declared runtime classpath and properties. These tasks
are interactive and are not Gradle build-cache outputs. The available shell commands are documented
in [Manual environments](../../../workflows/scenarios/running/index.md).

## IDE preparation

```shell
./gradlew anvilTooling
./gradlew anvilTooling --output-file=/absolute/path/to/tooling.json
```

These commands compile the declared source set and required artifacts, then export the runtime
without starting Minecraft. `anvilTooling` belongs to no task group, so `./gradlew tasks` does not list it. The default output is `build/anvil/tooling.json`. The standard Anvil plugin configures this task for `src/anvil`; the standalone project producer supports an
existing source set. See [IDE project tooling](../tooling/index.md) for declaration and ownership.

## Accounts

```shell
./gradlew anvilLogin --account=main
./gradlew anvilLogout --account=main
```

| Task | Meaning |
|---|---|
| `anvilLogin --account=<id>` | Sign a Microsoft account in and store it |
| `anvilLogout --account=<id>` | Remove the stored account |

Both tasks require `--account`. `anvilLogin` runs the Microsoft device-code sign-in in a separate JVM and stores
the account in the engine's account directory. It chooses no protocol library: afterwards one installed library of
each Minecraft edition checks what that edition's players can do with the account, and the task prints one line
per edition, such as `java: available as Steve (<uuid>)` or `bedrock: unavailable, ` followed by the checking
library's reason. Tokens stay in the account file and never become task inputs or arguments. Both tasks are untracked and
always run. See [Authentication](../../../building-blocks/players/authentication/index.md) before
using online players.
