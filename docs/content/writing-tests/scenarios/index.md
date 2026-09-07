---
title: Scenarios
description: Describe the processes, distributions, entrypoint, and lifecycle policy for a test environment.
---

# Scenarios

An `AnvilScenario` is a named, complete Minecraft environment. It declares one or more servers,
optional proxies, the player entrypoint, Java and execution defaults, and lifecycle settings. A
scenario is a declaration; starting it produces the `ScenarioContext` used by a test.

Use [a direct definition](definitions.md) when a test selects one fixed environment. Use [a
catalog provider](catalogs.md) when a build exposes several versions, groups, or manual scenarios.
Process platform and proxy details are documented under [running environments](../../running-environments/index.md).

## Minimal scenario

This definition runs a Paper server and uses that server as the default player entrypoint:

```java
package example.scenario;

import me.whereareiam.anvil.api.model.process.Distribution;
import me.whereareiam.anvil.api.model.process.MinecraftServer;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.scenario.AnvilScenarioDefinition;
import me.whereareiam.anvil.api.type.Platforms;
import org.jetbrains.annotations.NotNull;

public final class PaperScenario implements AnvilScenarioDefinition {
	@Override
	public @NotNull AnvilScenario define() {
		return AnvilScenario.builder()
				.name("paper")
				.server(MinecraftServer.builder()
						.name("server")
						.platform(Platforms.PAPER)
						.distribution(Distribution.remote("1.21.11", "132"))
						.minecraftVersion("1.21.11")
						.build())
				.entrypoint("server")
				.build();
	}
}
```

Automated scenarios use an immutable build or content pin. `Distribution.remote(version, build)`
selects a provider build, while `Distribution.pinned(version, sha256)` selects content by checksum
when the provider supports it. `Distribution.local(path)` and `Distribution.artifact(name)` are
useful for a locally built executable or an artifact registered in the Gradle extension.

Every named server must declare `minecraftVersion` when its distribution is local or artifact-backed.
The native client selected for a player must be compatible with every reachable server; Anvil does
not silently insert a protocol translation layer.

## Entrypoints and process names

`entrypoint` names a declared server or proxy. Every server and proxy name is unique in the
scenario. A player created without an explicit `connectTo` uses this entrypoint. A proxy declares
its backend names and its `defaultServer`; forwarding is negotiated from the provider capabilities
before launch.

See [player routing](../players/routing.md) for per-player overrides and
[running environments](../../running-environments/index.md) for server, proxy, and forwarding
configuration.
