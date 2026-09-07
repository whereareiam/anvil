---
title: JUnit integration
description: Start a typed Anvil scenario around a JUnit Jupiter test and receive its context.
---

# JUnit integration

The JUnit integration starts one scenario around each test and injects its active
`ScenarioContext`. Add the Anvil JUnit extension dependency supplied by the Gradle plugin, then
annotate the test with `@AnvilTest`.

```java
import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.anvil.capability.messages.Messages;
import me.whereareiam.anvil.capability.session.Session;
import me.whereareiam.anvil.junit.AnvilTest;
import org.junit.jupiter.api.Test;

@AnvilTest(PaperScenario.class)
class RegistrationTest {
	@Test
	void registersAPlayer(ScenarioContext anvil) {
		var player = anvil.players().create("Alice");
		var session = player.capability(Session.class);
		session.connect();
		session.connected();
		player.capability(Messages.class).command("register secret");
	}
}
```

The annotation can be placed on a test class or on an individual test method. A method annotation
selects that method's definition; otherwise the extension uses the class annotation. The selected
definition must implement `AnvilScenarioDefinition` and expose a no-argument constructor.

## Test source sets and task

Keep scenario definitions and managed journeys in `src/anvil`. The Anvil Gradle integration builds
that source set and supplies the configured `anvilTest` task. Run it from the project root:

```shell
./gradlew anvilTest
```

Use the normal `test` task for unit tests that do not start managed Minecraft processes. Gradle
setup and protocol dependencies are described in [Installation](../../getting-started/installation/index.md)
and [Configure an environment](../../running-environments/configuration/index.md).
