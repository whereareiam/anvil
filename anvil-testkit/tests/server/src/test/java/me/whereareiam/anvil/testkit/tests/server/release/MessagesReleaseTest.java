package me.whereareiam.anvil.testkit.tests.server.release;

import me.whereareiam.anvil.api.player.SimulatedPlayer;
import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.anvil.capability.messages.MessageWatch;
import me.whereareiam.anvil.capability.messages.Messages;
import me.whereareiam.anvil.capability.messages.model.ChatLines;
import me.whereareiam.anvil.capability.world.DimensionWatch;
import me.whereareiam.anvil.capability.world.World;
import me.whereareiam.anvil.protocol.api.model.text.SpanStyle;
import me.whereareiam.anvil.protocol.api.model.text.Text;
import me.whereareiam.anvil.protocol.api.model.text.TextColor;
import me.whereareiam.anvil.protocol.api.model.text.TextSpan;
import me.whereareiam.anvil.protocol.api.model.text.click.RunCommandClick;
import me.whereareiam.anvil.protocol.api.model.text.content.TranslatedContent;
import me.whereareiam.anvil.testkit.tests.server.fixture.FixtureServer;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sends chat and commands, receives the server's messages with their styles, follows what the chat shows and models
 * the chat lines a player can scroll back to.
 */
@Release
class MessagesReleaseTest {
	/**
	 * Number of commands, and of chat messages, a burst sends without waiting between them.
	 */
	private static final int BURST = 20;

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
	void deliversEveryCommandAndChatMessageOfABurst(ScenarioContext anvil) {
		SimulatedPlayer player = new FixtureServer(anvil).join();
		Messages messages = player.capability(Messages.class);
		// The server kicks a player who is not an operator for sending this much at once.
		messages.command("anvil-fixture op");
		messages.received("anvil:op", Duration.ofSeconds(10));
		MessageWatch replies = messages.watch();

		for (int message = 0; message < BURST; message++) {
			messages.command("anvil-fixture dialog-echo burst-%02d".formatted(message));
			messages.chat("anvil-burst-chat-%02d".formatted(message));
		}

		// The server runs commands in the order they arrive, but Paper handles chat on several threads.
		for (int message = 0; message < BURST; message++)
			replies.received("anvil:dialog-echo:burst-%02d".formatted(message), Duration.ofSeconds(10));
		for (int message = 0; message < BURST; message++)
			messages.received("anvil-burst-chat-%02d".formatted(message), Duration.ofSeconds(10));
	}

	@Test
	void runsACommandRightAfterConnecting(ScenarioContext anvil) {
		Messages messages = new FixtureServer(anvil).connect().capability(Messages.class);

		messages.command("anvil-fixture dialog-echo connected");

		messages.received("anvil:dialog-echo:connected", Duration.ofSeconds(10));
	}

	@Test
	void runsCommandsInEveryDimensionThePlayerMovesTo(ScenarioContext anvil) {
		FixtureServer server = new FixtureServer(anvil);
		SimulatedPlayer player = server.join();
		Messages messages = player.capability(Messages.class);
		MessageWatch chat = messages.watch();
		// The server refuses a command that reaches it while it moves the player, so the player sends once it arrived.
		DimensionWatch dimensions = player.capability(World.class).watch();

		server.console("execute in minecraft:the_nether run tp " + player.name() + " 0 120 0");
		dimensions.entered("minecraft:the_nether", Duration.ofSeconds(30));
		messages.command("anvil-fixture dialog-echo nether");
		chat.received("anvil:dialog-echo:nether", Duration.ofSeconds(10));
		server.console("execute in minecraft:overworld run tp " + player.name() + " 0 120 0");
		dimensions.entered("minecraft:overworld", Duration.ofSeconds(30));
		messages.command("anvil-fixture dialog-echo overworld");

		chat.received("anvil:dialog-echo:overworld", Duration.ofSeconds(10));
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
	void receivesAMessageWithTheColourDecorationsAndClickOfEachPart(ScenarioContext anvil) {
		FixtureServer server = new FixtureServer(anvil);
		SimulatedPlayer player = server.join();
		MessageWatch chat = player.capability(Messages.class).watch();
		// Minecraft 1.21.5 renamed a component's click event and named its command.
		String click = TestedRelease.isAtLeast("1.21.5")
				? "\"click_event\":{\"action\":\"run_command\",\"command\":\"/anvil-fixture ping\"}"
				: "\"clickEvent\":{\"action\":\"run_command\",\"value\":\"/anvil-fixture ping\"}";

		server.console("tellraw " + player.name() + " [{\"text\":\"anvil:styled \",\"color\":\"gray\"},"
				+ "{\"text\":\"ping\",\"color\":\"gold\",\"bold\":true," + click + "}]");

		Text message = chat.received("anvil:styled ping", Duration.ofSeconds(10));
		assertEquals(List.of(
				new TextSpan("anvil:styled ", SpanStyle.builder().color(TextColor.GRAY).build()),
				new TextSpan("ping", SpanStyle.builder()
						.color(TextColor.GOLD)
						.bold(true)
						.click(new RunCommandClick("/anvil-fixture ping"))
						.build())
		), message.spans());
	}

	@Test
	void receivesATranslationWithItsKeyAndArguments(ScenarioContext anvil) {
		FixtureServer server = new FixtureServer(anvil);
		SimulatedPlayer player = server.join();
		MessageWatch chat = player.capability(Messages.class).watch();

		server.console("tellraw " + player.name() + " [\"anvil:translated \",{\"translate\":\"multiplayer.player.joined\","
				+ "\"with\":[{\"text\":\"Ivan\",\"color\":\"aqua\"}]}]");

		Text message = chat.received("anvil:translated", Duration.ofSeconds(10));
		TranslatedContent joined = message.find(TranslatedContent.class).orElseThrow();
		assertEquals("multiplayer.player.joined", joined.getKey());
		assertEquals("Ivan", joined.getArguments().getFirst().plain());
		assertEquals(TextColor.AQUA, message.span("Ivan").orElseThrow().getStyle().getColor());
		assertEquals("anvil:translated multiplayer.player.joined Ivan", message.plain());
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
