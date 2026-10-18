package me.whereareiam.anvil.testkit.tests.server.release;

import me.whereareiam.anvil.api.player.SimulatedPlayer;
import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.anvil.capability.messages.MessageWatch;
import me.whereareiam.anvil.capability.messages.Messages;
import me.whereareiam.anvil.capability.messages.model.ChatLines;
import me.whereareiam.anvil.testkit.tests.server.fixture.FixtureServer;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sends chat and commands, receives the server's messages, follows what the chat shows and models the chat lines a
 * player can scroll back to.
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

	@Test
	void showsTheServersMessagesInTheChatOnceThePlayerJoined(ScenarioContext anvil) {
		FixtureServer server = new FixtureServer(anvil);
		SimulatedPlayer player = server.join();
		MessageWatch chat = player.capability(Messages.class).watch();

		server.console("tellraw " + player.name() + " \"anvil:shown\"");

		chat.received("anvil:shown", Duration.ofSeconds(10));
	}

	@Test
	void pushesEarlierChatOutOfTheChatWithAHundredBlankLines(ScenarioContext anvil) {
		FixtureServer server = new FixtureServer(anvil);
		SimulatedPlayer player = server.join();
		Messages messages = player.capability(Messages.class);
		MessageWatch chat = messages.watch();
		server.console("tellraw " + player.name() + " \"anvil:before\\nanvil:second-line\"");
		chat.received("anvil:before", Duration.ofSeconds(10));
		assertTrue(messages.chatLines().contains("anvil:second-line"), messages.chatLines().toString());

		for (int line = 0; line < ChatLines.MAX_LINES; line++)
			server.console("tellraw " + player.name() + " \"\"");
		server.console("tellraw " + player.name() + " \"anvil:after\"");
		chat.received("anvil:after", Duration.ofSeconds(10));

		List<String> expected = new ArrayList<>(Collections.nCopies(ChatLines.MAX_LINES - 1, ""));
		expected.add("anvil:after");
		assertEquals(expected, messages.chatLines().getLines());
	}
}
