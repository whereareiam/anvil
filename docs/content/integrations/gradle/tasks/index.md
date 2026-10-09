---
title: Tasks and options
description: Compile Anvil sources, run JUnit tests, select manual scenarios, and sign in accounts.
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
./gradlew anvilScenario --definition=com.example.test.ManualPaperScenario
```

The names are produced from compiled `AnvilScenarioDefinition` classes in the Anvil source set.
The class name can be selected directly with `--definition` when scenario names are ambiguous.

| Option | Meaning |
|---|---|
| `--list` | List discovered scenarios without starting them |
| `--scenario=<name>` | Start one named scenario |
| `--definition=<class>` | Start one definition class directly |

Use `--list` alone, or exactly one of `--scenario` and `--definition`. The runner
executes in a separate JVM using the declared runtime classpath and properties. These tasks
are interactive and are not Gradle build-cache outputs. The available shell commands are documented
in [Manual environments](../../../workflows/scenarios/running/index.md).

## IDE preparation

```shell
./gradlew anvilTooling
./gradlew anvilTooling --output-file=/absolute/path/to/tooling.json
```

These commands compile the declared source set and required artifacts, then export the runtime
without starting Minecraft. The default output is `build/anvil/tooling.json`. The standard Anvil plugin configures this task for `src/anvil`; the standalone project producer supports an
existing source set. See [IDE project tooling](../tooling/index.md) for declaration and ownership.

## Accounts

```shell
./gradlew anvilAccount --login=main
./gradlew anvilAccount --logout=main
```

| Option | Meaning |
|---|---|
| `--login=<id>` | Sign in through the selected protocol provider and store the account |
| `--logout=<id>` | Remove the stored account |

Use exactly one option. The task runs the provider's interactive sign-in in a separate JVM, using the
same protocol selection and account directory as scenario runs. It prints the provider's prompts;
tokens stay in the account file and never become task inputs or arguments. The task is untracked and
always runs. See [Authentication](../../../building-blocks/players/authentication/index.md) before
using online players.
