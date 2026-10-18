package me.whereareiam.anvil.testkit.tests.server.release;

import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.anvil.capability.messages.Messages;
import me.whereareiam.anvil.testkit.tests.server.fixture.FixtureServer;
import org.junit.jupiter.api.Test;

import java.time.Duration;

/**
 * Sends chat and commands, and receives the server's messages.
 */
@Release
class MessagesReleaseTest {
	@Test
	void receivesItsOwnChat(ScenarioContext anvil) {
		Messages messages = new FixtureServer(anvil).join().capability(Messages.class);

		messages.chat("hello-from-anvil");

		messages.received("hello-from-anvil", Duration.ofSeconds(10));
	}

	@Test
	void runsACommandTheServerAnswers(ScenarioContext anvil) {
		Messages messages = new FixtureServer(anvil).join().capability(Messages.class);

		messages.command("anvil-fixture ping");

		messages.received("anvil:pong", Duration.ofSeconds(10));
	}
}
