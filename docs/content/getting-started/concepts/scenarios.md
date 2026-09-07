---
title: Scenarios
description: Declare the complete environment that a live journey runs against.
---

# Scenarios

An `AnvilScenario` is an immutable declaration of one complete environment. It names an
`entrypoint`, lists one or more `MinecraftServer` and `MinecraftProxy` processes, and can set
execution, network, Java, timeout, and manual-run policies.

Use `AnvilScenarioDefinition` when a JUnit annotation should select one scenario directly:

```java
import me.whereareiam.anvil.api.model.process.Distribution;
import me.whereareiam.anvil.api.model.process.MinecraftServer;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.scenario.AnvilScenarioDefinition;
import me.whereareiam.anvil.api.type.Platforms;

public final class LobbyScenario implements AnvilScenarioDefinition {
	@Override
	public AnvilScenario define() {
		MinecraftServer lobby = MinecraftServer.builder()
				.name("lobby")
				.platform(Platforms.PAPER)
				.distribution(Distribution.remote("1.21.11", "132"))
				.minecraftVersion("1.21.11")
				.build();
		return AnvilScenario.builder()
				.name("lobby")
				.entrypoint(lobby.getName())
				.server(lobby)
				.build();
	}
}
```

The entrypoint must name a declared process. Players created with `PlayerManager.create(String)`
use it as their connection target and receive the newest compatible verified client version unless
`PlayerOptions` supplies an explicit version or target.

Use `AnvilScenarioProvider` and `ScenarioRegistry` when a project exposes a catalog, release matrix,
or manual groups. A provider can register multiple definitions and `ScenarioGroup` values for the
foreground runner.

Keep distributions pinned for automated runs. A provider build identifier uses
`Distribution.remote(version, build)`; a content-pinned provider can use
`Distribution.pinned(version, sha256)`. Local development artifacts use `Distribution.local(path)`
or a named `Distribution.artifact(reference)` supplied by the embedding tool.
