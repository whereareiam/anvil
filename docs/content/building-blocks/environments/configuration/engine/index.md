---
title: Engine options and JVM properties
description: Configure the JVM running Anvil and understand defaults, precedence, and entry-point differences.
---

Engine options configure Anvil itself. A managed server's JVM arguments configure that child
process separately. For a JUnit run, put additional engine properties on the test JVM:

```kotlin
import org.gradle.api.tasks.testing.Test

tasks.named<Test>("anvilTest") {
	systemProperty("anvil.stopTimeout", "PT30S")
	systemProperty("anvil.java.download", "false")
}
```

This fragment belongs in the consumer `build.gradle.kts` after applying the JUnit or umbrella
plugin. It allows thirty seconds for graceful process shutdown and requires a suitable Java
installation to be available without a download. It does not change `anvilScenario`.

## Which entry point reads what?

| Entry point | Configuration path |
|---|---|
| JUnit extension | Decodes properties in the test JVM. The Gradle integration supplies shared DSL settings and artifact paths. |
| Gradle `anvilScenario` | Reads Gradle JVM properties, then overlays task settings from the `anvil` extension and registered artifacts. |
| Direct engine/runner embedding | Uses the supplied `EngineOptions`. It does not implicitly merge system properties. |

Do not assume `./gradlew -Danvil.someProperty=... anvilTest` forwards an arbitrary property into
the forked test JVM. Use the `Test.systemProperty` configuration above. To decode properties in
an embedding application, call `EngineProperties.from(properties)` or
`EngineProperties.fromSystemProperties()` from `me.whereareiam.anvil.launcher.config` explicitly.

## Properties

| Property | Value | Default |
|---|---|---|
| `anvil.protocol` | Installed provider ID | Sole installed provider |
| `anvil.eula.accepted` | `true` or `false` | `false` |
| `anvil.cacheDir` | Directory path | `~/.anvil` |
| `anvil.workDir` | Directory path | `build/anvil` |
| `anvil.keepFailedWorkspaces` | `true` or `false` | `true` |
| `anvil.execution` | Installed execution-provider ID | `local` |
| `anvil.offline` | `true` or `false` | `false` |
| `anvil.refresh` | `true` or `false` | `false` |
| `anvil.parallelism` | Positive integer | Half the available processors, clamped to 1–8 |
| `anvil.startupMemoryMegabytes` | Positive integer in MiB | Half detected host memory, clamped to 1024–8192 MiB |
| `anvil.downloadParallelism` | Positive integer | Available processors, clamped to 1–8 |
| `anvil.stopTimeout` | Positive ISO-8601 duration | `PT15S` |
| `anvil.java.download` | `true` or `false` | `true` |
| `anvil.java.version` | Positive Java feature version | No exact version; reuse a compatible JVM, provision the platform minimum if needed |
| `anvil.java.distribution` | Java distribution identifier | `temurin` when provisioning an unspecified distribution |
| `anvil.java.release` | Exact release selector | No exact release; provider selects one if provisioning is needed |
| `anvil.java.home` | Installed JDK directory | No override |
| `anvil.java.executable` | Java executable path | No override |
| `anvil.java.archive.uri` | Verified archive URI | No override |
| `anvil.java.archive.sha256` | 64 hexadecimal characters | No override |
| `anvil.artifact.<name>` | Local path to one named artifact | No artifact registered |

`PT30S` means thirty seconds. Invalid booleans, nonpositive limits, invalid timeout values, and
malformed archive checksums fail during decoding. Archive URI and checksum must be supplied
together. Configure at most one explicit source: home, executable, or archive.

Java settings describe the engine default. A process declaration overrides the scenario's Java
selection, and the scenario overrides the engine default. Execution selection can also be
overridden by the scenario. Docker images need provider-specific configuration; there is no
`anvil.docker.image` property. See [Execution providers](../execution/index.md).

## Lifetimes and limits

`stopTimeout` controls shutdown grace. The scenario's `startupTimeout` controls readiness;
capability waits have their own deadlines. Raising one does not change the others.

`keepFailedWorkspaces` applies when the scenario lifecycle is marked unsuccessful, including
startup and restart failures. In the current JUnit integration, an assertion that fails only in the
test body does not automatically mark the context unsuccessful before close. Use a persistent
workspace when you need files after such a failure; see [Cleanup diagnostics](../../../../help/troubleshooting/cleanup/index.md).

The shared cache can include private authentication state. Selectively cache verified artifacts in
CI; never upload the provider's private account store or tokens. Offline mode uses previously
acquired artifacts and metadata; it is not a machine-wide network firewall for plugins or game sessions.
