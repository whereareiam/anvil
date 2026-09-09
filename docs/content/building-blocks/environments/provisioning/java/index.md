---
title: Java
description: Select the required Java version and choose local installations or verified Java archives.
---

Anvil itself targets Java 21. Managed processes can require a different Java version: the bundled
Paper and Spigot providers require Java 21 for `1.21.11` and Java 25 for `26.1.2`.
Velocity and BungeeCord require Java 21 in the current provider contract.

## Use automatic local selection

With local execution and no explicit Java source, Anvil checks:

1. The Java installation running Anvil.
2. `JAVA_<feature>_HOME`, such as `JAVA_25_HOME`.
3. A matching cached installation.
4. A verified download when Java downloads are enabled.

Each candidate must satisfy the process's Java requirement and the platform minimum.
Built-in download distributions are `temurin` and `graalvm-community`; Temurin is the default when
no distribution is selected. A compatible installed JVM can satisfy an unspecified distribution.

## Declare a requirement and source

Use this fragment inside a scenario definition. It supplies the required feature version and an
installed JDK, then creates a Paper process:

```java
import me.whereareiam.anvil.api.model.java.JavaRequirement;
import me.whereareiam.anvil.api.model.java.JavaSource;
import me.whereareiam.anvil.api.model.process.Distribution;
import me.whereareiam.anvil.api.model.process.MinecraftServer;
import me.whereareiam.anvil.api.type.Platforms;

import java.nio.file.Path;

MinecraftServer server = MinecraftServer.builder()
		.name("server")
		.platform(Platforms.PAPER)
		.distribution(Distribution.remote("26.1.2", "74"))
		.javaRequirement(JavaRequirement.builder().featureVersion(25).build())
		.javaSource(JavaSource.home(Path.of("/opt/jdk-25")))
		.build();
```

Replace `/opt/jdk-25` with a JDK on the machine running Anvil. Add `server` to your scenario and
run its test with `./gradlew anvilTest --tests 'your.package.YourTest'`. Startup validates the JVM
before starting the server; the path alone is not proof of its version.

Requirements can also constrain `.distribution("temurin")` and an exact `.release(...)`.
An explicit source can be `JavaSource.executable(path)` or
`JavaSource.archive(uri, sha256)`. For an archive, supply the actual archive URI and its verified
SHA-256; Anvil validates both the artifact bytes and the Java installation extracted from them.

A process declaration overrides the scenario, which overrides `EngineOptions`. Requirements and
sources inherit independently. A process-specific source must still satisfy the effective requirement.

## Reuse an installation offline

Provide a compatible local installation or prepare the required Java selection before going offline.
`anvil.java.download=false` requires an already available installation; it does not disable platform
artifact downloads. Use [Cache](../cache/artifacts/index.md) for the complete offline workflow and the
[engine options](../../configuration/engine/index.md) for setting these flags on the correct JVM.

## Use Java inside Docker

Docker selects an image through a `distribution:feature` mapping, then inspects its JVM against the
same requirement. It rejects host Java homes, host executables, and archive sources.
See [execution providers](../../configuration/execution/index.md) for the supported embedding setup.
