---
title: Running tests
description: Keep live journeys separate and choose when Gradle starts Minecraft.
---

Apply the JUnit [Gradle plugin](../plugins/index.md) and select scenarios through
[JUnit annotations](../../junit/selection/index.md). The plugin creates a dedicated source set and task.

Keep live scenario definitions and journeys in `src/anvil/java`. Use `src/anvil/resources` for resources
on that source set's classpath. Put ordinary application unit tests in `src/test` so the normal test
workflow stays useful without launching Minecraft.

## Run only the journey you are changing

```shell
./gradlew anvilTest --tests 'com.example.test.PlayerJoinTest'
```

`anvilTest` is a Gradle `Test` task configured for JUnit Jupiter. Its `--tests` filter selects test
classes or methods; it does not select a scenario by name. The test's annotation supplies the
definition class.

## Run the live tests

```shell
./gradlew anvilTest
```

`anvilTest` runs every live test that belongs to no [suite](#split-the-live-tests-into-suites). To run all
live tests, including every suite, run the `anvil` task:

```shell
./gradlew anvil
```

Ordinary `test`, `check` and `build` never start Minecraft; only these tasks do.

## Split the live tests into suites

Some live tests should not run with the rest, for example slow ones that run only before a release. Tag
them with JUnit's `@Tag` and register a suite for the tag:

```kotlin
anvil {
	suites {
		register("upgrade") { includeTags("upgrade") }
	}
}
```

```java
@Tag("upgrade")
class DatabaseUpgradeTest {
	// ...
}
```

Each suite gets its own task, named `anvil<Name>Test`: here `anvilUpgradeTest`, which runs only the tests
tagged `upgrade`. `anvilTest` leaves out every tag a suite includes. A suite name starts with a letter and
holds only letters and digits, and a suite must include at least one tag.

A suite task is wired exactly like `anvilTest`: the same classes and classpath from the `anvil` source set,
the same engine settings and artifacts, its own reports. Both are `AnvilTest` tasks, so configure settings
they share, such as JUnit's parallel execution, once for all of them:

```kotlin
import me.whereareiam.anvil.integration.junit.gradle.AnvilTest

tasks.withType<AnvilTest>().configureEach {
	systemProperty("junit.jupiter.execution.parallel.enabled", "true")
}
```

Use `tasks.named<AnvilTest>("anvilUpgradeTest")` for a setting of one suite only. `./gradlew anvil`
runs `anvilTest` and every suite.

## Read the report

Gradle writes the HTML report to `build/reports/tests/anvilTest/index.html` and XML results to
`build/test-results/anvilTest`; a suite's task writes under its own name, such as
`build/reports/tests/anvilUpgradeTest`. A failure before the journey begins usually points to scenario
validation, provisioning, or process startup. A timeout inside the journey needs the relevant player
or process observation checked.

The engine retains workspaces for failures it records during preparation or lifecycle operations.
An assertion thrown only by the JUnit test body does not currently mark the scenario as failed for
workspace retention. Use a [persistent workspace](../../../building-blocks/environments/workspaces/persistence/index.md) during diagnosis
when you need its files after every test outcome.

## Keep shared data intentional

The Anvil source set can consume main output and configured test dependencies. Keep reusable journey
helpers alongside the live tests, or publish a dedicated test fixture artifact when multiple projects
need it. Register the packaged plugin with Gradle so its producing task runs before the live test;
see [plugin artifacts and assets](../../../building-blocks/environments/workspaces/assets/index.md).

Parallel live executions multiply process and memory requirements. Fresh workspaces separate process
files, while the same persistent workspace cannot be used concurrently. Configure execution capacity
under [environments](../../../building-blocks/environments/configuration/index.md).
