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

This fragment belongs in the consumer `build.gradle.kts` after applying the JUnit
plugin. It allows thirty seconds for graceful process shutdown and requires each process's planned
Java installation to be available without a download. It does not change `anvilScenario`.

## Which entry point reads what?

| Entry point | Configuration path |
|---|---|
| JUnit extension | Decodes properties in the test JVM. The Gradle integration supplies shared DSL settings and artifact paths. |
| Gradle `anvilScenario` | Runs in a separate JVM with the declared project properties. The consumer integration supplies supported Gradle JVM properties, DSL settings, and artifacts. |
| IDE session | Uses the prepared project runtime and its explicit launch properties; it does not inherit a JUnit test JVM's properties. |
| Direct engine/runner embedding | Uses the supplied `EngineOptions`. It does not implicitly merge system properties. |

Do not assume `./gradlew -Danvil.someProperty=... anvilTest` forwards an arbitrary property into
the forked test JVM. Use the `Test.systemProperty` configuration above. To decode properties in
an embedding application, call `EngineProperties.from(properties)` or
`EngineProperties.fromSystemProperties()` from `me.whereareiam.anvil.launcher.config` explicitly.

## Properties

| Property                       | Value                                                | Default                                                                            |
|--------------------------------|------------------------------------------------------|------------------------------------------------------------------------------------|
| `anvil.protocolLibrary`        | Installed protocol library ID                        | Each player uses the installed library with the strongest support for its version |
| `anvil.supportPolicy`          | `lenient` or `strict`, case-insensitive              | `lenient`                                                                          |
| `anvil.protocolReleases.<id>`  | Additional release data file for library `<id>`      | No additional releases                                                             |
| `anvil.accountsDir`            | Directory path                                       | `~/.anvil/accounts`                                                                |
| `anvil.eula.accepted`          | `true` or `false`                                    | `false`                                                                            |
| `anvil.cacheDir`               | Directory path                                       | `~/.anvil`                                                                         |
| `anvil.workDir`                | Directory path                                       | `build/anvil`                                                                      |
| `anvil.keepFailedWorkspaces`   | `true` or `false`                                    | `true`                                                                             |
| `anvil.execution`              | Installed execution-provider ID                      | `local`                                                                            |
| `anvil.console.colors`         | Request ANSI colors from supported platform consoles | `true` for IDE sessions; `false` elsewhere                                         |
| `anvil.offline`                | `true` or `false`                                    | `false`                                                                            |
| `anvil.refresh`                | `true` or `false`                                    | `false`                                                                            |
| `anvil.parallelism`            | Positive integer                                     | Half the available processors, clamped to 1–8                                      |
| `anvil.startupMemoryMegabytes` | Positive integer in MiB                              | Half detected host memory, clamped to 1024–8192 MiB                                |
| `anvil.downloadParallelism`    | Positive integer                                     | Available processors, clamped to 1–8                                               |
| `anvil.startupTimeout`         | Positive ISO-8601 duration                           | `PT2M`                                                                             |
| `anvil.stopTimeout`            | Positive ISO-8601 duration                           | `PT15S`                                                                            |
| `anvil.java.download`          | `true` or `false`                                    | `true`                                                                             |
| `anvil.java.version`           | LTS Java feature version: 11, 17, 21, 25, ...        | Each platform's preferred LTS for the process version                              |
| `anvil.java.distribution`      | Java distribution identifier                         | `temurin` when provisioning an unspecified distribution                            |
| `anvil.java.release`           | Exact release selector                               | No exact release; provider selects one if provisioning is needed                   |
| `anvil.java.home`              | Installed JDK directory                              | No override                                                                        |
| `anvil.java.executable`        | Java executable path                                 | No override                                                                        |
| `anvil.java.archive.uri`       | Verified archive URI                                 | No override                                                                        |
| `anvil.java.archive.sha256`    | 64 hexadecimal characters                            | No override                                                                        |
| `anvil.artifact.<name>`        | Local path to one named artifact                     | No artifact registered                                                             |

`PT30S` means thirty seconds. Invalid booleans, nonpositive limits, invalid timeout values, and
malformed archive checksums fail during decoding. Archive URI and checksum must be supplied
together. Configure at most one explicit source: home, executable, or archive.

`anvil.console.colors` accepts `true` or `false`. IDE sessions enable it unless the prepared launch
properties explicitly set it to `false`. Direct embedding uses
`EngineOptions.builder().consoleColors(true)`. Platform providers translate the request into their
own console options; explicit JVM arguments on a server or proxy take precedence over those defaults.

Java settings describe the engine default. A process declaration overrides the scenario's Java
selection, and the scenario overrides the engine default. An engine Java version applies to every
process without its own requirement; planning refuses it for a process whose platform version does
not accept it, so give that process its own `JavaRequirement`. Execution selection can also be
overridden by the scenario. Docker images need provider-specific configuration; there is no
`anvil.docker.image` property. See [Execution providers](../execution/index.md).

## Lifetimes and limits

`EngineOptions.processTimeouts` supplies startup and shutdown defaults. A scenario's
`processTimeouts` can override either member independently. Startup limits the time for each process
to become ready; shutdown is the grace period before forced termination. Capability waits keep
their own deadlines.

`EngineOptions.processScheduling` groups preparation/start parallelism and the declared heap
allowance for simultaneous starts. An omitted value is selected from host capacity. These limits
apply within each scenario preparation or bulk-start operation; they are not an aggregate cap
across all active scenarios. A process whose declared heap exceeds the allowance starts alone.
Already-running processes are not counted against this startup allowance. Scenarios do not override
scheduling limits. Artifact download concurrency remains a separate engine option.

For direct embedding, configure the groups explicitly:

```java
import me.whereareiam.anvil.api.model.EngineOptions;
import me.whereareiam.anvil.api.model.process.lifecycle.ProcessScheduling;
import me.whereareiam.anvil.api.model.process.lifecycle.ProcessTimeouts;
import java.time.Duration;

EngineOptions options = EngineOptions.builder()
		.executionProviderId("local")
		.processTimeouts(ProcessTimeouts.builder()
				.startup(Duration.ofMinutes(2))
				.shutdown(Duration.ofSeconds(15))
				.build())
		.processScheduling(ProcessScheduling.builder()
				.parallelism(2)
				.startupMemoryMegabytes(4096)
				.build())
		.build();
```

`keepFailedWorkspaces` applies when the scenario lifecycle is marked unsuccessful, including
startup and restart failures. The JUnit integration also passes a failed test outcome to scenario
finalization. See [Cleanup diagnostics](../../../../help/troubleshooting/cleanup/index.md) for inspecting
retained workspaces.

The shared cache can include private authentication state. Selectively cache verified artifacts in
CI; never upload the protocol library's private account store or tokens. Offline mode uses previously
acquired artifacts and metadata; it is not a machine-wide network firewall for plugins or game sessions.
