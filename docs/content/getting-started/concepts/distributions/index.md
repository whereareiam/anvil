---
title: Distributions
description: Select the exact server or proxy executable used by a scenario.
---

A **distribution** selects the executable JAR for a server or proxy. The platform tells Anvil how to
run Paper, Spigot, Velocity, or BungeeCord; the distribution supplies the particular release and build
or the file that should run.

This matters when reproducing a bug. If a test passes on one Paper build and fails on another, the
scenario should record that choice so another developer can run the same environment. A moving
selector can change the test's inputs without any change to the test itself.

## Read a pinned declaration

This is the server declaration from [Scenarios](../scenarios/index.md), with the executable choice
shown in `.distribution(...)`. It belongs inside the definition's `define()` method; the scenario
registers its result with `.server(server)`. The setup to run it comes after this concept tour.

```java
import me.whereareiam.anvil.api.model.process.Distribution;
import me.whereareiam.anvil.api.model.process.MinecraftServer;
import me.whereareiam.anvil.api.type.Platforms;
```

```java
MinecraftServer server = MinecraftServer.builder()
		.name("server")
		.platform(Platforms.PAPER)
		.distribution(Distribution.remote("1.21.11", "132"))
		.build();
```

Here `PAPER` is the platform, `1.21.11` is the Minecraft version of that Paper release, and `132` is
its build identifier. The server name is only its identity inside the scenario. The completed test
asks the Paper provider to resolve that build and verifies the behavior against it.

For Velocity, the distribution version is the proxy's release version, such as `3.5.1`; the reachable
backend servers determine the native Minecraft version Alice needs. Keep those two version concepts
separate when building a proxy environment.

## Choose how to identify the executable

An immutable build identifier selects an upstream build. A SHA-256 pin identifies the expected
artifact bytes. Spigot's bundled GetBukkit provider uses `Distribution.pinned(version, sha256)` because
its release URL is version-based. The [Spigot guide](../../../building-blocks/environments/platforms/servers/spigot/index.md)
provides recorded hashes and explains what happens if the supplier's bytes change.

Use `Distribution.local(path)` when your application supplies an existing executable file. Use
`Distribution.artifact("server-under-test")` when Gradle or the embedding application registers that
name to an exact file. These are useful for testing a locally built server or an executable from your
own artifact registry.

For either supplied-server source, declare `.minecraftVersion("1.21.11")` on the server builder,
changing the value to match the actual JAR. Anvil needs that version to validate native clients;
a filename alone does not establish compatibility. A plugin JAR being tested belongs in the
[workspace](../workspaces/index.md), not in the server's executable distribution.

Automated scenarios require a fixed build or supported content pin. Providers that support `latest`
allow it only for manual scenarios; Spigot still requires its checksum. The detailed
[distribution guide](../../../building-blocks/environments/provisioning/platform/index.md) covers supplied
artifacts, and the [compatibility guide](../../../building-blocks/environments/platforms/versions/index.md)
lists matching server and client inputs.

Next: [Workspaces](../workspaces/index.md) explains how to supply the plugins, configuration, and
data used alongside that executable.
