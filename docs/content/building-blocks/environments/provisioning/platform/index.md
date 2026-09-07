---
title: Platform
description: Pin remote server and proxy executables or supply local and named artifacts.
---

A distribution selects the executable server or proxy JAR. Use a provider build number or verified
content checksum in automated scenarios so another machine resolves the same intended artifact.

## Select a source

| Source | Declaration | Required context |
|---|---|---|
| Remote build | `Distribution.remote(version, build)` | Provider-specific immutable build selector |
| Remote content | `Distribution.pinned(version, sha256)` | A provider supporting checksums, including Spigot |
| Local JAR | `Distribution.local(path)` | Existing executable file on the Anvil host |
| Named artifact | `Distribution.artifact(name)` | Artifact registered in Gradle or `EngineOptions` |

Import `me.whereareiam.anvil.api.model.process.Distribution` in the scenario definition.
The platform pages provide actual pinned remote selections for [servers](../../platforms/servers/index.md) and
[proxies](../../platforms/proxies/index.md).

`latest` is allowed only for manual scenarios by providers that support it. Spigot requires its
checksum pin even in manual mode. A version number alone does not identify a repeatable Spigot download.

## Supply an executable from your build

Register a file in your existing `build.gradle.kts`:

```kotlin
anvil {
	artifact("server-under-test", layout.projectDirectory.file("fixtures/server.jar"))
}
```

Place your executable at `fixtures/server.jar`. Alternatively register a task provider, project
dependency, or Maven coordinate through the same artifact API; Gradle resolves its output before the run.
Use this fragment inside the scenario definition:

```java
import me.whereareiam.anvil.api.model.process.Distribution;
import me.whereareiam.anvil.api.model.process.MinecraftServer;
import me.whereareiam.anvil.api.type.Platforms;

MinecraftServer server = MinecraftServer.builder()
		.name("server")
		.platform(Platforms.PAPER)
		.distribution(Distribution.artifact("server-under-test"))
		.minecraftVersion("1.21.11")
		.build();
```

Choose the platform and Minecraft version that actually match your executable. Add `server` to your
scenario, then run its JUnit test with `./gradlew anvilTest --tests 'your.package.YourTest'`.
Anvil resolves the registered artifact before provisioning and uses the declared version for native
client validation.

A server executable differs from the plugin JAR being tested. Install plugin JARs through
[workspace assets](../../workspaces/index.md), usually under the managed process's
`plugins` directory.

## Supply the runtime and retain downloads

The selected JAR also needs a compatible [Java](../java/index.md) installation. Provisioning can keep
the executable in the shared cache; its location is independent of the process's working directory.

[Cache](../cache/index.md) explains acquired artifacts, resolution metadata, offline use, and refresh
behavior. A checksum mismatch is a failed acquisition; inspect the expected source and bytes before
selecting a new pin. Do not replace a recorded checksum merely to bypass the mismatch.
