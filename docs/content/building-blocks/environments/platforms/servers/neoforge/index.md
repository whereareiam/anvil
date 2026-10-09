---
title: NeoForge
description: Run a NeoForge server installed from a pinned NeoForge release and load your mods into it.
---

Use NeoForge to exercise server-side mods. Apply `me.whereareiam.anvil.platform.neoforge` at the same
version as your main Anvil plugin. The unit includes the NeoForge agent, a small mod that Anvil installs
for native server observations and console commands.

## Select a NeoForge release

A remote distribution names the Minecraft version and, as its build, the NeoForge release. Add this
declaration fragment to your scenario definition:

```java
import me.whereareiam.anvil.api.model.process.Distribution;
import me.whereareiam.anvil.api.model.process.MinecraftServer;
import me.whereareiam.anvil.api.type.Platforms;

MinecraftServer server = MinecraftServer.builder()
		.name("server")
		.platform(Platforms.NEOFORGE)
		.distribution(Distribution.remote("1.21.1", "21.1.256"))
		.build();
```

Add `server` to the scenario and use its name as the entrypoint. The release must belong to the
Minecraft version: NeoForge drops Minecraft's leading `1.`, so releases for `1.21.1` start with
`21.1.` and releases for `1.21.11` with `21.11.`; from Minecraft `26.1` on they keep the whole
version, such as `26.1.2.114`. A release of another version, a missing release, and `latest` are
refused before anything is downloaded.

Anvil downloads the installer from the NeoForged Maven repository and verifies it against the
SHA-256 that repository publishes for the release. To pin the installer yourself, add the checksum:

```java
Distribution pinned = Distribution.builder()
		.version("1.21.1")
		.build("21.1.256")
		.sha256("<64-digit SHA-256 of neoforge-21.1.256-installer.jar>")
		.build();
```

## How the server is installed

NeoForge publishes an installer, not a server JAR. The first scenario that uses a release runs its
installer once with the JVM that runs Anvil. The installer downloads the Minecraft server and NeoForge's
libraries itself, so the first run needs network access even when Anvil's own downloads are cached.
Its output is kept in `<cacheDirectory>/distributions/neoforge/<release>/install.log`.

The installed server is cached under `<cacheDirectory>/distributions/neoforge/<release>/server`. Each
process workspace receives hard links to those files, or copies when the file system has no hard
links, and starts through the installer's server starter JAR.

## Install mods

Install mods into `mods/` with [workspace assets](../../../workspaces/index.md):

```java
import me.whereareiam.anvil.api.model.workspace.AssetSource;
import me.whereareiam.anvil.api.model.workspace.WorkspaceAsset;
import me.whereareiam.anvil.api.model.workspace.WorkspacePlan;

import java.nio.file.Path;

WorkspacePlan workspace = WorkspacePlan.builder()
		.asset(WorkspaceAsset.builder()
				.group("mod")
				.source(AssetSource.artifact("mod"))
				.target(Path.of("mods", "example-mod.jar"))
				.build())
		.build();
```

Pass it to the server with `.workspace(workspace)`. A simulated player is a vanilla client: it joins a
NeoForge server as long as no installed mod requires a matching mod on the client.

## Use your own installer

Use `Distribution.local(path)` or `Distribution.artifact(name)` with a NeoForge installer JAR and
declare `minecraftVersion` on the server. See
[distribution selection](../../../provisioning/platform/index.md) for registration and invocation.

## Java

NeoForge runs on the LTS release its Minecraft version prefers; see
[Java per platform version](../../../provisioning/java/index.md#java-per-platform-version).

## Limitations

- NeoForge servers are supported from Minecraft `1.21.1`. The agent does not load on older releases.
- NeoForge has no identity forwarding, so a NeoForge server cannot be a proxy backend. Planning refuses
  such a scenario because the server and the proxy share no forwarding mode.
- `.setting(...)` updates `server.properties`. Anvil manages the listener, authentication, and EULA
  settings.
