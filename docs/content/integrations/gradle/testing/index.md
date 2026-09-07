---
title: Running tests
description: Keep live journeys separate and choose when Gradle starts Minecraft.
---

Apply the JUnit or umbrella [Gradle plugin](../plugins/index.md) and select scenarios through
[JUnit annotations](../../junit/selection/index.md). The plugin creates a dedicated source set and task.

Keep live scenario definitions and journeys in `src/anvil/java`. Use `src/anvil/resources` for resources
on that source set's classpath. Put ordinary application unit tests in `src/test` so the normal test
workflow stays useful without launching Minecraft.

## Run only the journey you are changing

```shell
./gradlew anvilTest --tests 'com.example.test.PlayerJoinTest'
```

`anvilTest` is a Gradle `Test` task configured for JUnit Jupiter. Its `--tests` filter selects test
classes or methods; it does not select a catalog scenario name. The test's annotation supplies the
definition.

## Run the suite

```shell
./gradlew anvilTest
```

To make the ordinary `test` task also run `anvilTest`, use:

```shell
./gradlew test -Panvil.testMode=full
```

Without that project property, running `test` does not add the live Anvil task. Explicitly running
`anvilTest` starts the selected live tests regardless of the property.

## Read the report

Gradle writes the HTML report to `build/reports/tests/anvilTest/index.html` and XML results to
`build/test-results/anvilTest`. A failure before the journey begins usually points to scenario
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
