---
title: Configure an environment
description: Choose workspace storage, Java installations, and timeouts for Anvil tests and embedded runners.
---

# Configure an environment

Anvil needs a place to create server workspaces, a cache for downloaded artifacts, and a compatible
Java installation for each server or proxy. Configure these for the machine running your tests.
Declare the servers, proxies, and plugin JARs separately in your
[scenario](../../writing-tests/scenarios/index.md).

If you have not installed Anvil yet, complete [Getting started](../../getting-started/index.md) first.

## Configure a Gradle project

Add environment settings to the `anvil` block in the project that contains `src/anvil`:

```kotlin
anvil {
    acceptEula()
    protocol("mcprotocol")
    workDirectory.set(layout.buildDirectory.dir("anvil"))
    cacheDirectory.set(layout.projectDirectory.dir(".anvil-cache"))
}
```

`acceptEula()` records your acceptance of the Minecraft EULA. `protocol("mcprotocol")` selects an
installed protocol provider; it does not install one. The getting-started configuration includes the
MCProtocol dependency.

The two directories have different lifetimes:

| Directory | Contains | Choose a location that… |
|---|---|---|
| `workDirectory` | Server files, installed plugins, configuration, and process logs | Can be recreated for disposable tests and inspected after a failure |
| `cacheDirectory` | Downloaded distributions, protocol artifacts, JDKs, and declared workspace-cache snapshots | Can be retained between runs to avoid downloading the same artifacts again |

The defaults are `build/anvil` for workspaces and `~/.anvil` for the shared cache. The example uses a
project-local cache; add `.anvil-cache/` to `.gitignore` if you use it. Changing the cache directory
does not select which plugin data is preserved. Declare that with
[workspace caches](../../writing-tests/workspaces/index.md).

Run a test after changing the settings:

```shell
./gradlew anvilTest --tests 'com.example.test.PlayerJoinTest'
```

Replace the test name with a class in your project. Inspect its process workspaces under the chosen
`workDirectory` if startup fails.

## Select Java installations

Anvil asks each platform provider which Java version its distribution requires. To supply an
installation explicitly, add its executable path:

```kotlin
anvil {
    javaExecutables.put(21, "/opt/jdk-21/bin/java")
    javaExecutables.put(25, "/opt/jdk-25/bin/java")
}
```

Replace these paths with installations on the test machine. The map key is the Java feature version
required by the provider. Anvil first checks that override, then the current Java installation, then
`JAVA_<version>_HOME`. When none is suitable, automatic provisioning downloads a verified Temurin
installation into the shared cache.

See [Platforms and versions](../platforms/index.md) for the requirements of supported distributions.
A Java override chooses an executable; it does not change the Minecraft version in your scenario.

## Set startup and shutdown deadlines

Set `startupTimeout` on an `AnvilScenario` when a server needs more time to become ready. For example,
when building a scenario, `.startupTimeout(Duration.ofMinutes(3))` gives each launched process a
three-minute readiness deadline. The default is two minutes. Increasing this value does not extend
player capability waits such as `session.connected()`.

The process shutdown grace period defaults to fifteen seconds. For automated Gradle tests, configure
the test JVM when you need a longer grace period or want to disable automatic Java downloads:

```kotlin
tasks.named<Test>("anvilTest") {
    systemProperty("anvil.stopTimeout", "PT30S")
    systemProperty("anvil.autoDownloadJavaRuntimes", "false")
}
```

These settings apply to `anvilTest`, not to the foreground `anvilScenario` task. `PT30S` is an
ISO-8601 duration meaning thirty seconds. After the grace period Anvil escalates process termination;
it does not leave a process running indefinitely. With Java downloads disabled, supply a suitable
installation locally or startup fails.

## Embed Anvil in another application

Use the `me.whereareiam.anvil:launcher` distribution and add the selected protocol provider, platform
provider, and matching platform-agent artifacts to your application's classpath. Keep their Anvil
versions aligned. The launcher supplies the engine; it does not select a Minecraft platform or
protocol for you.

This helper runs an existing scenario using explicit options. Pass a scenario whose workspace uses
`AssetSource.artifact("plugin-under-test")`, and the path to the plugin JAR it should install:

```java
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.scenario.AnvilContext;
import me.whereareiam.anvil.api.scenario.ScenarioEngine;
import me.whereareiam.anvil.launcher.AnvilLauncher;
import me.whereareiam.anvil.api.model.EngineOptions;

import java.nio.file.Path;
import java.time.Duration;

public final class EmbeddedScenario {
    public static void run(AnvilScenario scenario, Path pluginJar) {
        EngineOptions options = EngineOptions.builder()
                .eulaAccepted(true)
                .protocolId("mcprotocol")
                .workDirectory(Path.of("build", "anvil"))
                .keepFailedWorkspaces(true)
                .stopTimeout(Duration.ofSeconds(30))
                .artifact("plugin-under-test", pluginJar)
                .build();

        try (ScenarioEngine engine = AnvilLauncher.create(options);
             AnvilContext context = engine.start(scenario)) {
            var entrypoint = context.processes().get(scenario.getEntrypoint());
            System.out.println("Ready at " + entrypoint.address());
            // Perform assertions or player actions here while the context is open.
        }
    }
}
```

`start()` returns after the processes and agents are ready and the scenario setup hook completes.
Closing the context releases its players and processes. Closing the engine also closes any remaining
contexts and the shared protocol backend. Keep both resources in try-with-resources, including when
your test is expected to fail.

For an interactive shell, pass the same `EngineOptions` to
`AnvilRunner.run(arguments, options)` with your terminal input and output streams. See
[Manual environments](../manual/index.md) for scenario-provider selection and shell commands.

## Property reference

JUnit and the standalone runner read the following JVM properties. An embedding application can
use `EngineProperties.fromSystemProperties()` from `me.whereareiam.anvil.launcher.config`, or
`EngineProperties.from(properties)` to decode a supplied `java.util.Properties` object. Passing
explicit `EngineOptions` to the engine or runner uses those options directly.

| Property | Accepted value | Default |
|---|---|---|
| `anvil.protocol` | Installed provider ID | Select the sole installed provider |
| `anvil.eula.accepted` | `true` or `false` | `false` |
| `anvil.cacheDir` | Directory path | `~/.anvil` |
| `anvil.workDir` | Directory path | `build/anvil` |
| `anvil.keepFailedWorkspaces` | `true` or `false` | `true` |
| `anvil.autoDownloadJavaRuntimes` | `true` or `false` | `true` |
| `anvil.stopTimeout` | Positive ISO-8601 duration | `PT15S` |
| `anvil.defaultJavaExecutable` | Executable path for the current Java installation | Current installation's `bin/java` or `bin/java.exe` |
| `anvil.java.<feature>` | Executable path, with a positive integer feature version in the key | No override |
| `anvil.artifact.<name>` | Local path for a named scenario artifact | No artifact registered |

Set these on the JVM running Anvil. JVM arguments configured for a managed Minecraft process affect
that child process instead. Invalid booleans, timeout values, and Java-version keys fail during
property parsing; inspect the reported property before retrying.
