package me.whereareiam.anvil.capability.messages.java;

import me.whereareiam.anvil.capability.messages.channel.MessagesChannel;
import me.whereareiam.anvil.capability.messages.model.ChatMessage;
import me.whereareiam.anvil.capability.messages.model.DroppedChat;
import me.whereareiam.anvil.capability.messages.model.MessageText;
import me.whereareiam.anvil.capability.messages.type.ChatDropReason;
import me.whereareiam.anvil.edition.java.protocol.model.chat.MessageSignature;
import me.whereareiam.anvil.edition.java.protocol.model.chat.PackedMessageSignature;
import me.whereareiam.anvil.edition.java.protocol.model.entity.EntityId;
import me.whereareiam.anvil.edition.java.protocol.model.world.DimensionType;
import me.whereareiam.anvil.edition.java.protocol.packet.ClientboundPacket;
import me.whereareiam.anvil.edition.java.protocol.packet.chat.ClientboundDeleteChat;
import me.whereareiam.anvil.edition.java.protocol.packet.chat.ClientboundDisguisedChat;
import me.whereareiam.anvil.edition.java.protocol.packet.chat.ClientboundPlayerChat;
import me.whereareiam.anvil.edition.java.protocol.packet.chat.ClientboundSystemChat;
import me.whereareiam.anvil.edition.java.protocol.packet.chat.ServerboundChat;
import me.whereareiam.anvil.edition.java.protocol.packet.chat.ServerboundChatCommand;
import me.whereareiam.anvil.edition.java.protocol.packet.connection.ClientboundLogin;
import me.whereareiam.anvil.edition.java.protocol.packet.connection.ClientboundStartConfiguration;
import me.whereareiam.anvil.edition.java.protocol.type.chat.FilterMaskType;
import me.whereareiam.anvil.edition.java.protocol.worker.TestJavaPlayer;
import me.whereareiam.anvil.player.api.exception.ProtocolUnavailableException;
import me.whereareiam.anvil.player.api.model.text.Text;
import me.whereareiam.anvil.player.api.model.text.TextColor;
import me.whereareiam.anvil.player.api.model.text.TextStyle;
import me.whereareiam.anvil.player.api.model.text.click.RunCommandClick;
import me.whereareiam.anvil.player.api.model.text.content.LiteralContent;
import me.whereareiam.anvil.player.api.worker.WorkerBinding;
import me.whereareiam.anvil.player.worker.TestOperations;
import me.whereareiam.anvil.player.worker.TestWorkerPlayer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static me.whereareiam.anvil.player.api.type.TextDecoration.BOLD;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JavaMessagesExtensionTest {
	private static final MessageSignature FIRST = signature(1);
	private static final MessageSignature SECOND = signature(2);

	private final JavaMessagesExtension extension = new JavaMessagesExtension();
	private final TestJavaPlayer player = new TestJavaPlayer("Alice");
	private final TestOperations operations = new TestOperations();

	@Test
	void sendsChatAndCommandsAsTheirPackets() {
		extension.bind(player, operations);
		player.connect();

		assertNull(operations.call(MessagesChannel.CHAT, new MessageText("hello")));
		assertNull(operations.call(MessagesChannel.COMMAND, new MessageText("anvil-fixture ping")));

		assertEquals(List.of(new ServerboundChat("hello"), new ServerboundChatCommand("anvil-fixture ping")),
				player.sent());
	}

	@Test
	void addsChatMessagesButNeitherActionBarsNorFullyFilteredPlayerChat() {
		extension.bind(player, operations);
		player.connect();

		player.deliver(login(7));
		player.deliver(new ClientboundSystemChat(Text.literal("welcome"), false));
		player.deliver(new ClientboundSystemChat(Text.literal("anvil:action-bar"), true));
		player.deliver(new ClientboundPlayerChat(Text.literal("hidden"), FilterMaskType.FULLY_FILTERED, null, List.of()));
		player.deliver(ClientboundPlayerChat.unsigned(Text.literal("hello")));
		player.deliver(new ClientboundDisguisedChat(Text.literal("journey-ready-1")));

		assertEquals(List.of(
				new ChatMessage(0, Text.literal("welcome")),
				new ChatMessage(1, Text.literal("hello")),
				new ChatMessage(2, Text.literal("journey-ready-1"))
		), player.emitted(MessagesChannel.CHAT_ADDED));
		assertEquals(List.of(new DroppedChat("hidden", ChatDropReason.FULLY_FILTERED)),
				player.emitted(MessagesChannel.CHAT_DROPPED));
	}

	@Test
	void reportsAShownMessageWithItsStylesAndADroppedOneAsPlainText() {
		extension.bind(player, operations);
		player.connect();
		Text welcome = Text.builder()
				.content(new LiteralContent("Welcome to "))
				.style(TextStyle.builder().color(TextColor.GRAY).build())
				.child(Text.builder()
						.content(new LiteralContent("Arbor"))
						.style(TextStyle.builder().color(TextColor.GOLD).decoration(BOLD, true)
								.click(new RunCommandClick("/arbor help")).build())
						.build())
				.build();

		player.deliver(new ClientboundSystemChat(welcome, false));
		player.deliver(login(7));
		player.deliver(new ClientboundSystemChat(welcome, false));

		assertEquals(List.of(new ChatMessage(0, welcome)), player.emitted(MessagesChannel.CHAT_ADDED));
		assertEquals(List.of(new DroppedChat("Welcome to Arbor", ChatDropReason.NO_PLAYER)),
				player.emitted(MessagesChannel.CHAT_DROPPED));
	}

	@Test
	void clearsTheChatWhenANewConnectionStarts() {
		extension.bind(player, operations);

		player.connect();
		player.deliver(login(7));
		player.deliver(new ClientboundSystemChat(Text.literal("welcome"), false));
		player.rejoin();

		assertEquals(List.of("messages.chat-cleared", "messages.chat-added", "messages.chat-cleared"),
				player.emittedEvents());
	}

	@Test
	void deletesAShownMessageByItsWholeSignature() {
		extension.bind(player, operations);
		player.connect();

		player.deliver(login(7));
		player.deliver(signed("first", FIRST));
		player.deliver(signed("second", SECOND));
		player.deliver(new ClientboundDeleteChat(PackedMessageSignature.full(SECOND)));

		assertEquals(List.of(1L), player.emitted(MessagesChannel.CHAT_DELETED));
	}

	@Test
	void unpacksADeletionByTheIndexTheMessagesPushedIntoTheSignatureCache() {
		extension.bind(player, operations);
		player.connect();

		player.deliver(login(7));
		player.deliver(signed("first", FIRST));
		player.deliver(new ClientboundPlayerChat(Text.literal("second"), FilterMaskType.PASS_THROUGH, SECOND,
				List.of(PackedMessageSignature.cached(0))));
		player.deliver(new ClientboundDeleteChat(PackedMessageSignature.cached(1)));

		assertEquals(List.of(0L), player.emitted(MessagesChannel.CHAT_DELETED));
	}

	@Test
	void startsANewSignatureCacheWithEveryLoginButKeepsTheShownMessages() {
		extension.bind(player, operations);
		player.connect();

		player.deliver(login(7));
		player.deliver(signed("first", FIRST));
		player.deliver(login(8));
		player.deliver(new ClientboundDeleteChat(PackedMessageSignature.cached(0)));
		player.deliver(new ClientboundDeleteChat(PackedMessageSignature.full(FIRST)));

		assertEquals(List.of(0L), player.emitted(MessagesChannel.CHAT_DELETED));
	}

	@Test
	void ignoresDeletionsOfMessagesTheChatNeverShowed() {
		extension.bind(player, operations);
		player.connect();

		player.deliver(login(7));
		player.deliver(new ClientboundPlayerChat(Text.literal("hidden"), FilterMaskType.FULLY_FILTERED, FIRST, List.of()));
		player.deliver(new ClientboundDeleteChat(PackedMessageSignature.full(FIRST)));
		player.deliver(new ClientboundDeleteChat(PackedMessageSignature.full(SECOND)));

		assertEquals(List.of(), player.emitted(MessagesChannel.CHAT_DELETED));
	}

	@Test
	void leavesDisguisedChatAndDeletionsAloneOnAReleaseWithoutThem() {
		TestJavaPlayer unsigned = new TestJavaPlayer("Alice",
				type -> type != ClientboundDisguisedChat.class && type != ClientboundDeleteChat.class);

		extension.bind(unsigned, operations);

		for (Class<? extends ClientboundPacket> type : List.of(
				ClientboundLogin.class,
				ClientboundStartConfiguration.class,
				ClientboundSystemChat.class,
				ClientboundPlayerChat.class
		))
			assertTrue(unsigned.protocol().wants(type), type.getSimpleName());

		assertFalse(unsigned.protocol().wants(ClientboundDisguisedChat.class));
		assertFalse(unsigned.protocol().wants(ClientboundDeleteChat.class));
	}

	@Test
	void dropsChatThatArrivesBeforeTheLogin() {
		extension.bind(player, operations);
		player.connect();

		player.deliver(new ClientboundSystemChat(Text.literal("configuring"), false));
		player.deliver(ClientboundPlayerChat.unsigned(Text.literal("player configuring")));
		player.deliver(new ClientboundDisguisedChat(Text.literal("disguised configuring")));
		player.deliver(login(7));
		player.deliver(new ClientboundSystemChat(Text.literal("welcome"), false));

		assertEquals(List.of(
				new DroppedChat("configuring", ChatDropReason.NO_PLAYER),
				new DroppedChat("player configuring", ChatDropReason.NO_PLAYER),
				new DroppedChat("disguised configuring", ChatDropReason.NO_PLAYER)
		), player.emitted(MessagesChannel.CHAT_DROPPED));
		assertEquals(List.of(new ChatMessage(0, Text.literal("welcome"))), player.emitted(MessagesChannel.CHAT_ADDED));
	}

	@Test
	void showsNoChatFromAReturnToConfigurationUntilTheNextLogin() {
		extension.bind(player, operations);
		player.connect();

		player.deliver(login(7));
		player.deliver(new ClientboundSystemChat(Text.literal("on auth"), false));
		player.deliver(new ClientboundStartConfiguration());
		player.deliver(new ClientboundSystemChat(Text.literal("switching"), false));
		player.deliver(login(8));
		player.deliver(new ClientboundSystemChat(Text.literal("on lobby"), false));

		assertEquals(List.of(new ChatMessage(0, Text.literal("on auth")), new ChatMessage(1, Text.literal("on lobby"))),
				player.emitted(MessagesChannel.CHAT_ADDED));
		assertEquals(List.of(new DroppedChat("switching", ChatDropReason.NO_PLAYER)),
				player.emitted(MessagesChannel.CHAT_DROPPED));
	}

	@Test
	void startsEveryConnectionWithoutAPlayer() {
		extension.bind(player, operations);
		player.connect();

		player.deliver(login(7));
		player.rejoin();
		player.deliver(new ClientboundSystemChat(Text.literal("before the login"), false));

		assertEquals(List.of(), player.emitted(MessagesChannel.CHAT_ADDED));
	}

	@Test
	void showsEveryMessageOnAProtocolWithoutLogins() {
		TestJavaPlayer loginless = new TestJavaPlayer("Alice",
				type -> type != ClientboundLogin.class && type != ClientboundStartConfiguration.class);
		extension.bind(loginless, operations);
		loginless.connect();

		loginless.deliver(new ClientboundSystemChat(Text.literal("welcome"), false));

		assertEquals(List.of(new ChatMessage(0, Text.literal("welcome"))), loginless.emitted(MessagesChannel.CHAT_ADDED));
	}

	@Test
	void stopsListeningWhenClosed() {
		WorkerBinding binding = extension.bind(player, operations);

		binding.close();
		player.connect();
		player.deliver(new ClientboundSystemChat(Text.literal("after close"), false));

		for (Class<? extends ClientboundPacket> type : List.of(
				ClientboundLogin.class,
				ClientboundStartConfiguration.class,
				ClientboundSystemChat.class,
				ClientboundPlayerChat.class,
				ClientboundDisguisedChat.class,
				ClientboundDeleteChat.class
		))
			assertFalse(player.protocol().wants(type), type.getSimpleName());

		assertEquals(List.of(), player.emittedEvents());
	}

	@Test
	void isUnavailableForAPlayerThatDoesNotSpeakTheJavaProtocol() {
		TestWorkerPlayer silent = new TestWorkerPlayer("Alice");

		assertThrows(ProtocolUnavailableException.class, () -> extension.bind(silent, operations));
	}

	private static ClientboundPlayerChat signed(String text, MessageSignature signature) {
		return new ClientboundPlayerChat(Text.literal(text), FilterMaskType.PASS_THROUGH, signature, List.of());
	}

	private static MessageSignature signature(int seed) {
		byte[] bytes = new byte[256];
		bytes[0] = (byte) seed;

		return new MessageSignature(bytes);
	}

	private static ClientboundLogin login(int entityId) {
		return ClientboundLogin.builder()
				.entityId(new EntityId(entityId))
				.level("minecraft:overworld")
				.dimension("minecraft:overworld")
				.dimensionType(DimensionType.known("minecraft:overworld"))
				.build();
	}
}
