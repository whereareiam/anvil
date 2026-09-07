---
title: Direct scenario definitions
description: Select one type-safe environment directly from a JUnit test.
---

# Direct scenario definitions

Implement `AnvilScenarioDefinition` when a test class should select one complete environment by
type. The definition must have an accessible no-argument constructor because the JUnit extension
instantiates it for each test lifecycle.

```java
import me.whereareiam.anvil.api.model.process.Distribution;
import me.whereareiam.anvil.api.model.process.MinecraftServer;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.scenario.AnvilScenarioDefinition;
import me.whereareiam.anvil.api.type.Platforms;
import org.jetbrains.annotations.NotNull;

public final class RegistrationScenario implements AnvilScenarioDefinition {
	@Override
	public @NotNull AnvilScenario define() {
		return AnvilScenario.builder()
				.name("registration")
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

The test selects the definition with `@AnvilTest`:

```java
import me.whereareiam.anvil.api.player.SimulatedPlayer;
import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.anvil.capability.messages.Messages;
import me.whereareiam.anvil.capability.session.Session;
import me.whereareiam.anvil.junit.AnvilTest;
import org.junit.jupiter.api.Test;

final class RegistrationTest {
	@Test
	@AnvilTest(RegistrationScenario.class)
	void registration(ScenarioContext anvil) {
		SimulatedPlayer alice = anvil.players().create("Alice");
		Session session = alice.capability(Session.class);

		session.connect();
		session.connected();
		alice.capability(Messages.class).command("register secret");
	}
}
```

The extension starts a fresh scenario around each test and injects only `ScenarioContext` parameters.
It closes the context after the test, including when an assertion fails. The engine options used by
the extension come from the configured Anvil system properties and Gradle setup.

For several versions or named run groups, use an [explicit provider catalog](catalogs.md).
