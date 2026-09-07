---
title: Spigot
description: Run a checksum-pinned prebuilt Spigot release or a supplied executable artifact.
---

Use Spigot to exercise Bukkit plugins without Paper-specific behavior. Apply
`me.whereareiam.anvil.platform.spigot` at the same version as your main Anvil plugin.
The unit includes the Bukkit agent used for native server observations.

## Pin the prebuilt JAR

The bundled provider downloads prebuilt Spigot JARs from the third-party GetBukkit supplier.
It does not run BuildTools. Add this declaration fragment to your scenario definition:

```java
import me.whereareiam.anvil.api.model.process.Distribution;
import me.whereareiam.anvil.api.model.process.MinecraftServer;
import me.whereareiam.anvil.api.type.Platforms;

MinecraftServer server = MinecraftServer.builder()
		.name("server")
		.platform(Platforms.SPIGOT)
		.distribution(Distribution.pinned(
				"1.21.11",
				"6481503fca2838776b3da5a3f1c030e1328abc2fd77d9ea1bb4814889b540dcd"
		))
		.build();
```

Add `server` to the scenario and use its name as the entrypoint for a direct test. Install your plugin
with [workspace assets](../../../workspaces/index.md), then run the consuming test:
`./gradlew anvilTest --tests 'your.package.YourTest'`.

## Recorded content pins

These are the content hashes recorded in Anvil's compatibility catalog. They identify the expected
executable bytes; they are not an upstream signature or a selector that follows a moving release.

| Minecraft version | SHA-256 |
|---|---|
| `1.21.11` | `6481503fca2838776b3da5a3f1c030e1328abc2fd77d9ea1bb4814889b540dcd` |
| `26.1.2` | `95f871fd6d055ba10b5a058768ddad43b0be0286480c8eba435c835c95d5f19c` |

The provider downloads `https://cdn.getbukkit.org/spigot/spigot-<version>.jar` and verifies SHA-256
before accepting it. If the supplier's bytes change, a fresh download fails until you deliberately
verify and select different content. It does not silently change the pin or compile a replacement.

Remote Spigot selection requires an exact numeric version and a 64-digit SHA-256 in both automated
and manual scenarios. Build-number and `latest` selectors are rejected.

## Use your own executable

Use `Distribution.local(path)` or `Distribution.artifact(name)` and declare `minecraftVersion` on the
server. See [distribution selection](../../../provisioning/platform/index.md) for registration and invocation.

## Configure and route Spigot

`.setting(...)` updates `server.properties`. Anvil manages the listener, authentication, EULA, and
legacy forwarding settings. Spigot supports legacy forwarding and cannot participate in modern
forwarding; both bundled proxy providers can form a compatible route to it.

Downloaded JARs are cached under
`<cacheDirectory>/distributions/getbukkit/spigot/<version>/<sha256>/spigot-<version>.jar`.
Plugin data remains part of the scenario workspace and has its own
[persistence policy](../../../workspaces/index.md).
