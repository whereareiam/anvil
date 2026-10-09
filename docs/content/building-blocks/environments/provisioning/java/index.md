---
title: Java
description: Select the exact LTS Java version for each process and choose local installations or verified Java archives.
---

Anvil itself targets Java 21. Every managed process runs on one exact LTS release chosen during
planning, before anything is downloaded or launched. Without a request, each process uses its
platform's preferred LTS for the process version, raised when the Anvil agent installed into the
process needs newer Java.

## Java per platform version

This is the one table of Java versions per platform version; the platform pages link here. Each
provider ships the same data in its `<platform>-versions.toml`, and a test of the runtime test suite
fails when this table and that data differ. Versions are written in their canonical form, so Velocity
`3.3` is release `3.3.0`.

| Platform | Versions | Range | Default | Verified by the live matrix |
|---|---|---|---|---|
| Paper | `1.16.5` | Java 11 to 16; newer with `-DPaper.IgnoreJavaVersion=true` | 11 | None |
| Paper | `1.17` to `1.17.1` | Java 17; newer with `-DPaper.IgnoreJavaVersion=true` | 17 | None |
| Paper | `1.18` to `1.20.4` | Java 17 or newer | 17 | `1.18.2` on 17 |
| Paper | `1.20.5` to `1.21.11` | Java 21 or newer | 21 | `1.21.11` on 21 |
| Paper | `26.1` and newer | Java 25 or newer | 25 | `26.1.2` on 25 |
| Spigot | `1.16.5` | Java 11 to 16 | 11 | None |
| Spigot | `1.17` to `1.17.1` | Java 17 | 17 | None |
| Spigot | `1.18` to `1.18.2` | Java 17 to 18 | 17 | None |
| Spigot | `1.19` to `1.20.4` | Java 17 to 20 | 17 | None |
| Spigot | `1.20.5` to `1.20.6` | Java 21 to 22 | 21 | None |
| Spigot | `1.21` to `1.21.11` | Java 21 or newer | 21 | `1.21.11` on 21 |
| Spigot | `26.1` and newer | Java 25 or newer | 25 | `26.1.2` on 25 |
| Velocity | `3.3` to `3.4` | Java 17 or newer; the Anvil agent needs 21 | 21 | None |
| Velocity | `3.5` to `3.5.1` | Java 21 or newer | 21 | `3.5.1` on 21 |
| Velocity | `4.0` and newer | Java 25 or newer | 25 | None |
| BungeeCord | every build | Java 17 or newer; the Anvil agent needs 21 | 21 | None |

The range is the platform's own limit. The Anvil agent installed into every managed process needs
Java 11 on Paper and Spigot and Java 21 on Velocity and BungeeCord, whose agents compile for Java 21.
Planning never selects older Java than the agent needs, so Velocity `3.3.x` and `3.4.x` and every
BungeeCord build run Java 21 by default although they start on Java 17. Each provider declares its
agent's minimum in its version data, and Anvil's build fails when the agent's classes need newer Java.

The last column lists the combinations Anvil's live matrix runs; planning assesses exactly these as
`VERIFIED`, and every other version a provider's data knows as `COMPATIBLE`. The matrix runs one
Minecraft version of each MCProtocolLib release directly on Paper, and its current versions on
Spigot and behind Velocity `3.5.1` and BungeeCord build `2085` as well. BungeeCord builds carry no
release version, so its single row applies to every build and Anvil assesses BungeeCord by its Java
range only.

Versions older than a platform's first row are refused. A Velocity JAR supplied through
`Distribution.local(...)` or `Distribution.artifact(...)` carries no release, so it uses the newest
Velocity row.

## Request a Java version

Anvil runs processes on LTS releases only: 11, 17, 21, 25, then every fourth release; Java 8 is not
one of them. Planning refuses a request that is not an LTS release, below the platform's minimum, or
below the Java the platform's Anvil agent needs. The refusal names the process, the platform version,
and the range it accepts:

```text
Process 'proxy' requests Java 17 (requested by process 'proxy'), but the Anvil agent installed into velocity requires Java 21 or newer. velocity 3.4 runs Java 17 or newer, and its Anvil agent requires Java 21 or newer (default 21).
```

Use this fragment inside a scenario definition. It requests Java 25 and supplies an installed JDK for a
Paper process:

```java
import me.whereareiam.anvil.api.model.java.JavaRequirement;
import me.whereareiam.anvil.api.model.java.JavaSelection;
import me.whereareiam.anvil.api.model.java.JavaSource;
import me.whereareiam.anvil.api.model.process.Distribution;
import me.whereareiam.anvil.api.model.process.MinecraftServer;
import me.whereareiam.anvil.api.type.Platforms;

import java.nio.file.Path;

MinecraftServer server = MinecraftServer.builder()
		.name("server")
		.platform(Platforms.PAPER)
		.distribution(Distribution.remote("26.1.2", "74"))
		.javaSelection(JavaSelection.builder()
				.requirement(JavaRequirement.builder().featureVersion(25).build())
				.source(JavaSource.home(Path.of("/opt/jdk-25")))
				.build())
		.build();
```

Replace `/opt/jdk-25` with a JDK on the machine running Anvil. Add `server` to your scenario and
run its test with `./gradlew anvilTest --tests 'your.package.YourTest'`. Startup validates the JVM
before starting the server; the path alone is not proof of its version.

Requirements can also constrain `.distribution("temurin")` and an exact `.release(...)`. A release
such as `17.0.12+7` without a feature version selects Java 17; a feature version that disagrees with
the release is refused. An explicit source can be `JavaSource.executable(path)` or
`JavaSource.archive(uri, sha256)`. For an archive, supply the actual archive URI and its verified
SHA-256; Anvil validates both the artifact bytes and the Java installation extracted from them.

`EngineOptions`, `AnvilScenario`, and process declarations all accept `.javaSelection(...)`.
A process declaration overrides the scenario, which overrides the engine. Omitted requirements and
sources inherit independently: configuring only a source does not reset the inherited requirement.
An explicitly empty `JavaRequirement.builder().build()` overrides an inherited version and uses the
platform's preferred LTS, while still inheriting the source. With no requirement at any level,
planning uses the preferred LTS. A source without a feature version must therefore contain exactly
that preferred release.

An engine or scenario version applies to every process that does not declare its own. When it does
not fit one process, for example `anvil.java.version=21` with a Spigot `1.16.5` server, the refusal
names the process and asks for a process-level `JavaRequirement`.

## Run above a platform's maximum

Paper `1.16.5` and `1.17.x` refuse Java newer than their maximum unless the
`Paper.IgnoreJavaVersion` system property is set. When a declaration explicitly requests a newer LTS,
planning adds `-DPaper.IgnoreJavaVersion=true` before the provider defaults and your own
`.jvmArgument(...)` values, so it applies to every start and restart. Anvil never adds it for the
default version. Paper prints `Unsupported Java detected (61.0). Only up to Java 16 is supported.`
and continues.

The live matrix runs no combination above a maximum, so each of them, such as Paper `1.16.5` on
Java 17 or 21, is `UNTESTED`: the
default `LENIENT` support policy runs them with an `[Anvil] Warning:` line, and `STRICT` refuses them.
Set the policy with `EngineOptions.supportPolicy(...)`, `AnvilScenario.supportPolicy(...)`, or
`-Danvil.supportPolicy=strict`.

## Spigot above its maximum

Spigot has no bypass for its Java check, so planning refuses an explicit request above its maximum:

```text
Process 'server' requests Java 17 (requested by process 'server'), but spigot 1.16.5 refuses Java above 16 and has no bypass. Remove the Java requirement to use Java 11 (default), or use 'paper', which runs Minecraft 1.16.5 on Java 17 with -DPaper.IgnoreJavaVersion=true.
```

The suggestion lists installed platforms that run the same Minecraft version on the requested Java
and that the active support policy accepts. Under `STRICT`, the same request suggests no other
platform, because Paper `1.16.5` on Java 17 is `UNTESTED`.

## Support levels

Planning assesses each process as the weakest of its platform version and its Java version:

| Level | Platform version | Java version |
|---|---|---|
| `VERIFIED` | Run by Anvil's live matrix, as the provider's version data records | Run by the live matrix on that platform version |
| `COMPATIBLE` | Known to the provider's data, not verified | Inside the accepted range, not verified |
| `UNTESTED` | Newer than every known version, or unknown | Above the maximum through a bypass |
| `UNSUPPORTED` | Older than the platform's first row | Refused as described above |

[Java per platform version](#java-per-platform-version) lists every verified combination.

`COMPATIBLE` prints an `[Anvil] Info:` line, `UNTESTED` prints an `[Anvil] Warning:` line under
`LENIENT` and is refused under `STRICT`, and `UNSUPPORTED` is always refused. A process whose
distribution carries no version, such as BungeeCord or a local Velocity JAR, is assessed by its Java
range only.

## Use automatic local selection

With local execution and no explicit Java source, Anvil looks for exactly the planned feature version:

1. The Java installation running Anvil, only when its feature version is equal.
2. `JAVA_<feature>_HOME`, such as `JAVA_17_HOME`.
3. A matching cached installation.
4. A verified download when Java downloads are enabled.

Built-in download distributions are `temurin` and `graalvm-community`; Temurin is the default when
no distribution is selected and publishes every LTS release. A matching installed JVM can satisfy an
unspecified distribution. When nothing matches, the error states the running JVM's version, whether
`JAVA_<feature>_HOME` was set, and the cache path that was checked.

## Reuse an installation offline

Provide a matching local installation or prepare the required Java selection before going offline.
`anvil.java.download=false` requires an already available installation; it does not disable platform
artifact downloads. Use [Cache](../cache/artifacts/index.md) for the complete offline workflow and the
[engine options](../../configuration/engine/index.md) for setting these flags on the correct JVM.

## Use Java inside Docker

Docker selects an image through a `distribution:feature` mapping such as `temurin:17`, then inspects
its JVM against the same requirement. Map every LTS release your processes use. It rejects host Java
homes, host executables, and archive sources. See
[execution providers](../../configuration/execution/index.md) for the supported embedding setup.
