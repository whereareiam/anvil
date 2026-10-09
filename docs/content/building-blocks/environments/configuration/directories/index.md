---
title: Directories and timeouts
description: Choose workspace and cache locations and tune readiness and shutdown deadlines.
---

Keep generated process files separate from reusable downloads. Anvil defaults to `build/anvil` for
workspaces and `~/.anvil` for the shared cache.

## Choose directories

In the build containing `src/anvil`, add this fragment:

```kotlin
anvil {
	workDirectory.set(layout.buildDirectory.dir("anvil"))
	cacheDirectory.set(layout.projectDirectory.dir(".anvil-cache"))
}
```

Add `.anvil-cache/` to `.gitignore` when using this project-local cache.

| Location | Contents and lifetime |
|---|---|
| Work directory | Generated process files, plugins, configuration, and `anvil-console.log`; disposable workspaces are removed after successful lifecycle cleanup |
| Cache directory | Verified distributions, protocol artifacts, Java installations, resolution metadata, and declared workspace snapshots; reused across runs |
| Authentication store | Private provider credentials under the cache root; exclude these from shared cache archives |

A different cache root does not preserve plugin data automatically. Declare
[workspace caches or persistent workspaces](../../workspaces/index.md) for that task.

## Set readiness and shutdown deadlines

Set `processTimeouts` on an `AnvilScenario` to override either engine deadline. Import
`me.whereareiam.anvil.api.model.process.lifecycle.ProcessTimeouts` and `java.time.Duration`:

```java
.processTimeouts(ProcessTimeouts.builder()
		.startup(Duration.ofMinutes(3))
		.shutdown(Duration.ofSeconds(30))
		.build())
```

This builder fragment gives each process three minutes to become ready and thirty seconds of
shutdown grace. Omit either member to inherit its engine default: two minutes for startup and
fifteen seconds for shutdown unless configured otherwise. These settings do not change player
capability waits.

For a longer shutdown grace period in JUnit, configure the test JVM in `build.gradle.kts`:

```kotlin
tasks.named<Test>("anvilTest") {
	systemProperty("anvil.stopTimeout", "PT30S")
}
```

`PT30S` means thirty seconds. The default is fifteen seconds; Anvil escalates termination when the
process has not exited during that grace period. This `Test` task setting applies to `anvilTest`.
It does not configure `anvilScenario`; the runner reads its host JVM's properties and the `anvil` block.
See [engine options](../engine/index.md) for each entry point's configuration.

## Retain useful diagnostics

`keepFailedWorkspaces` defaults to `true` for failures recorded by the scenario lifecycle, including
startup and restart failures. The JUnit integration passes the test outcome to context finalization,
so assertion failures also retain diagnostics under this policy. Directly embedded contexts should
call `finish(false)` when their caller fails. Collect relevant console output before discarding a
completed context.

Run one class to check a configuration change:

```shell
./gradlew anvilTest --tests 'com.example.test.PlayerJoinTest'
```

Replace the name with your test class. A successful run starts the declared processes and completes
the test; a readiness failure should lead you to that process's log rather than an indefinitely longer
wait. See [startup diagnostics](../../../../help/troubleshooting/startup/index.md).
