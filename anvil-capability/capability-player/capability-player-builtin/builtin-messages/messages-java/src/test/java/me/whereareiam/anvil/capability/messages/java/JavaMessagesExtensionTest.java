package me.whereareiam.anvil.capability.messages.java;

import me.whereareiam.anvil.capability.messages.channel.MessagesChannel;
import me.whereareiam.anvil.capability.messages.model.ChatMessage;
import me.whereareiam.anvil.capability.messages.model.DroppedChat;
import me.whereareiam.anvil.capability.messages.model.MessageText;
import me.whereareiam.anvil.capability.messages.type.ChatDropReason;
import me.whereareiam.anvil.protocol.api.channel.Subscription;
import me.whereareiam.anvil.protocol.api.exception.ProtocolUnavailableException;
import me.whereareiam.anvil.protocol.api.model.channel.EventDescriptor;
import me.whereareiam.anvil.protocol.api.model.channel.OperationDescriptor;
import me.whereareiam.anvil.protocol.api.model.player.ViewRotation;
import me.whereareiam.anvil.protocol.api.worker.OperationRegistry;
import me.whereareiam.anvil.protocol.api.worker.PlayerBindingContext;
import me.whereareiam.anvil.protocol.api.worker.WorkerBinding;
import me.whereareiam.anvil.protocol.java.api.JavaProtocol;
import me.whereareiam.anvil.protocol.java.api.model.chat.MessageSignature;
import me.whereareiam.anvil.protocol.java.api.model.chat.PackedMessageSignature;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.JavaPacket;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.chat.ClientboundDeleteChat;
import me.whereareiam.anvil.protocol.java.api.packet.chat.ClientboundDisguisedChat;
import me.whereareiam.anvil.protocol.java.api.packet.chat.ClientboundPlayerChat;
import me.whereareiam.anvil.protocol.java.api.packet.chat.ClientboundSystemChat;
import me.whereareiam.anvil.protocol.java.api.packet.chat.ServerboundChat;
import me.whereareiam.anvil.protocol.java.api.packet.chat.ServerboundChatCommand;
import me.whereareiam.anvil.protocol.java.api.packet.connection.ClientboundLogin;
import me.whereareiam.anvil.protocol.java.api.packet.connection.ClientboundStartConfiguration;
import me.whereareiam.anvil.protocol.java.api.type.FilterMaskType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JavaMessagesExtensionTest {
	private static final MessageSignature FIRST = signature(1);
	private static final MessageSignature SECOND = signature(2);

	private final JavaMessagesExtension extension = new JavaMessagesExtension();

	@Test
	void sendsChatAndCommandsAsTheirPackets() {
		RecordingProtocol protocol = new RecordingProtocol(true);
		RecordingOperations operations = new RecordingOperations();
		extension.bind(new RecordingPlayer(protocol), operations);

		assertNull(operations.execute(MessagesChannel.CHAT, new MessageText("hello")));
		assertNull(operations.execute(MessagesChannel.COMMAND, new MessageText("anvil-fixture ping")));

		assertEquals(List.of(new ServerboundChat("hello"), new ServerboundChatCommand("anvil-fixture ping")), protocol.sent);
	}

	@Test
	void addsChatMessagesButNeitherActionBarsNorFullyFilteredPlayerChat() {
		RecordingProtocol protocol = new RecordingProtocol(true);
		RecordingPlayer player = new RecordingPlayer(protocol);
		extension.bind(player, new RecordingOperations());

		protocol.deliver(new ClientboundLogin(7));
		protocol.deliver(new ClientboundSystemChat("welcome", false));
		protocol.deliver(new ClientboundSystemChat("anvil:action-bar", true));
		protocol.deliver(new ClientboundPlayerChat("hidden", FilterMaskType.FULLY_FILTERED, null, List.of()));
		protocol.deliver(ClientboundPlayerChat.unsigned("hello"));
		protocol.deliver(new ClientboundDisguisedChat("journey-ready-1"));

		assertEquals(List.of(new ChatMessage(0, "welcome"), new ChatMessage(1, "hello"), new ChatMessage(2, "journey-ready-1")),
				player.payloads(MessagesChannel.CHAT_ADDED));
		assertEquals(List.of(new DroppedChat("hidden", ChatDropReason.FULLY_FILTERED)),
				player.payloads(MessagesChannel.CHAT_DROPPED));
	}

	@Test
	void clearsTheChatWhenANewConnectionStarts() {
		RecordingProtocol protocol = new RecordingProtocol(true);
		RecordingPlayer player = new RecordingPlayer(protocol);
		extension.bind(player, new RecordingOperations());

		player.startConnection();
		protocol.deliver(new ClientboundLogin(7));
		protocol.deliver(new ClientboundSystemChat("welcome", false));
		player.startConnection();

		assertEquals(List.of("messages.chat-cleared", "messages.chat-added", "messages.chat-cleared"),
				player.events);
	}

	@Test
	void deletesAShownMessageByItsWholeSignature() {
		RecordingProtocol protocol = new RecordingProtocol(true);
		RecordingPlayer player = new RecordingPlayer(protocol);
		extension.bind(player, new RecordingOperations());

		protocol.deliver(new ClientboundLogin(7));
		protocol.deliver(signed("first", FIRST));
		protocol.deliver(signed("second", SECOND));
		protocol.deliver(new ClientboundDeleteChat(PackedMessageSignature.full(SECOND)));

		assertEquals(List.of(1L), player.payloads(MessagesChannel.CHAT_DELETED));
	}

	@Test
	void unpacksADeletionByTheIndexTheMessagesPushedIntoTheSignatureCache() {
		RecordingProtocol protocol = new RecordingProtocol(true);
		RecordingPlayer player = new RecordingPlayer(protocol);
		extension.bind(player, new RecordingOperations());

		protocol.deliver(new ClientboundLogin(7));
		protocol.deliver(signed("first", FIRST));
		protocol.deliver(new ClientboundPlayerChat("second", FilterMaskType.PASS_THROUGH, SECOND,
				List.of(PackedMessageSignature.cached(0))));
		protocol.deliver(new ClientboundDeleteChat(PackedMessageSignature.cached(1)));

		assertEquals(List.of(0L), player.payloads(MessagesChannel.CHAT_DELETED));
	}

	@Test
	void startsANewSignatureCacheWithEveryLoginButKeepsTheShownMessages() {
		RecordingProtocol protocol = new RecordingProtocol(true);
		RecordingPlayer player = new RecordingPlayer(protocol);
		extension.bind(player, new RecordingOperations());

		protocol.deliver(new ClientboundLogin(7));
		protocol.deliver(signed("first", FIRST));
		protocol.deliver(new ClientboundLogin(8));
		protocol.deliver(new ClientboundDeleteChat(PackedMessageSignature.cached(0)));
		protocol.deliver(new ClientboundDeleteChat(PackedMessageSignature.full(FIRST)));

		assertEquals(List.of(0L), player.payloads(MessagesChannel.CHAT_DELETED));
	}

	@Test
	void ignoresDeletionsOfMessagesTheChatNeverShowed() {
		RecordingProtocol protocol = new RecordingProtocol(true);
		RecordingPlayer player = new RecordingPlayer(protocol);
		extension.bind(player, new RecordingOperations());

		protocol.deliver(new ClientboundLogin(7));
		protocol.deliver(new ClientboundPlayerChat("hidden", FilterMaskType.FULLY_FILTERED, FIRST, List.of()));
		protocol.deliver(new ClientboundDeleteChat(PackedMessageSignature.full(FIRST)));
		protocol.deliver(new ClientboundDeleteChat(PackedMessageSignature.full(SECOND)));

		assertEquals(List.of(), player.payloads(MessagesChannel.CHAT_DELETED));
	}

	@Test
	void leavesDisguisedChatAndDeletionsAloneOnAReleaseWithoutThem() {
		RecordingProtocol protocol = new RecordingProtocol(false);
		extension.bind(new RecordingPlayer(protocol), new RecordingOperations());

		assertEquals(Set.of(ClientboundLogin.class, ClientboundStartConfiguration.class, ClientboundSystemChat.class,
				ClientboundPlayerChat.class), protocol.listeners.keySet());
	}

	@Test
	void dropsChatThatArrivesBeforeTheLogin() {
		RecordingProtocol protocol = new RecordingProtocol(true);
		RecordingPlayer player = new RecordingPlayer(protocol);
		extension.bind(player, new RecordingOperations());

		protocol.deliver(new ClientboundSystemChat("configuring", false));
		protocol.deliver(ClientboundPlayerChat.unsigned("player configuring"));
		protocol.deliver(new ClientboundDisguisedChat("disguised configuring"));
		protocol.deliver(new ClientboundLogin(7));
		protocol.deliver(new ClientboundSystemChat("welcome", false));

		assertEquals(List.of(
				new DroppedChat("configuring", ChatDropReason.NO_PLAYER),
				new DroppedChat("player configuring", ChatDropReason.NO_PLAYER),
				new DroppedChat("disguised configuring", ChatDropReason.NO_PLAYER)
		), player.payloads(MessagesChannel.CHAT_DROPPED));
		assertEquals(List.of(new ChatMessage(0, "welcome")), player.payloads(MessagesChannel.CHAT_ADDED));
	}

	@Test
	void showsNoChatFromAReturnToConfigurationUntilTheNextLogin() {
		RecordingProtocol protocol = new RecordingProtocol(true);
		RecordingPlayer player = new RecordingPlayer(protocol);
		extension.bind(player, new RecordingOperations());

		protocol.deliver(new ClientboundLogin(7));
		protocol.deliver(new ClientboundSystemChat("on auth", false));
		protocol.deliver(new ClientboundStartConfiguration());
		protocol.deliver(new ClientboundSystemChat("switching", false));
		protocol.deliver(new ClientboundLogin(8));
		protocol.deliver(new ClientboundSystemChat("on lobby", false));

		assertEquals(List.of(new ChatMessage(0, "on auth"), new ChatMessage(1, "on lobby")),
				player.payloads(MessagesChannel.CHAT_ADDED));
		assertEquals(List.of(new DroppedChat("switching", ChatDropReason.NO_PLAYER)),
				player.payloads(MessagesChannel.CHAT_DROPPED));
	}

	@Test
	void startsEveryConnectionWithoutAPlayer() {
		RecordingProtocol protocol = new RecordingProtocol(true);
		RecordingPlayer player = new RecordingPlayer(protocol);
		extension.bind(player, new RecordingOperations());

		protocol.deliver(new ClientboundLogin(7));
		player.startConnection();
		protocol.deliver(new ClientboundSystemChat("before the login", false));

		assertEquals(List.of(), player.payloads(MessagesChannel.CHAT_ADDED));
	}

	@Test
	void showsEveryMessageOnAProtocolWithoutLogins() {
		RecordingProtocol protocol = new RecordingProtocol(true, ClientboundLogin.class, ClientboundStartConfiguration.class);
		RecordingPlayer player = new RecordingPlayer(protocol);
		extension.bind(player, new RecordingOperations());

		protocol.deliver(new ClientboundSystemChat("welcome", false));

		assertEquals(List.of(new ChatMessage(0, "welcome")), player.payloads(MessagesChannel.CHAT_ADDED));
	}

	@Test
	void stopsListeningWhenClosed() {
		RecordingProtocol protocol = new RecordingProtocol(true);
		RecordingPlayer player = new RecordingPlayer(protocol);
		WorkerBinding binding = extension.bind(player, new RecordingOperations());

		binding.close();
		protocol.deliver(new ClientboundSystemChat("after close", false));
		player.startConnection();

		assertEquals(Map.of(), protocol.listeners);
		assertEquals(List.of(), player.events);
	}

	@Test
	void isUnavailableForAPlayerThatDoesNotSpeakTheJavaProtocol() {
		RecordingPlayer player = new RecordingPlayer(null);

		assertThrows(ProtocolUnavailableException.class, () -> extension.bind(player, new RecordingOperations()));
	}

	private static ClientboundPlayerChat signed(String text, MessageSignature signature) {
		return new ClientboundPlayerChat(text, FilterMaskType.PASS_THROUGH, signature, List.of());
	}

	private static MessageSignature signature(int seed) {
		byte[] bytes = new byte[256];
		bytes[0] = (byte) seed;

		return new MessageSignature(bytes);
	}

	/**
	 * Records sent packets and hands delivered packets to the listener of their class.
	 */
	private static final class RecordingProtocol implements JavaProtocol {
		private final boolean signedChat;
		private final Set<Class<? extends JavaPacket>> missing;
		private final List<ServerboundPacket> sent = new ArrayList<>();
		private final Map<Class<?>, Consumer<Object>> listeners = new LinkedHashMap<>();

		@SafeVarargs
		private RecordingProtocol(boolean signedChat, Class<? extends JavaPacket>... missing) {
			this.signedChat = signedChat;
			this.missing = Set.of(missing);
		}

		@Override
		public <P extends ClientboundPacket> @NotNull Subscription listen(
				@NotNull Class<P> type,
				@NotNull Consumer<? super P> listener
		) {
			listeners.put(type, packet -> listener.accept(type.cast(packet)));
			return () -> listeners.remove(type);
		}

		@Override
		public void send(@NotNull ServerboundPacket packet) {
			sent.add(packet);
		}

		@Override
		public <P extends ServerboundPacket> @NotNull Subscription listenSent(
				@NotNull Class<P> type,
				@NotNull Consumer<? super P> listener
		) {
			throw new AssertionError("The messages binding observes no sent packets");
		}

		@Override
		public boolean supports(@NotNull Class<? extends JavaPacket> type) {
			if (missing.contains(type)) return false;

			return signedChat || type != ClientboundDisguisedChat.class && type != ClientboundDeleteChat.class;
		}

		private void deliver(ClientboundPacket packet) {
			Consumer<Object> listener = listeners.get(packet.getClass());
			if (listener != null) listener.accept(packet);
		}
	}

	private static final class RecordingOperations implements OperationRegistry {
		private final Map<String, Function<Object, Object>> handlers = new LinkedHashMap<>();

		@Override
		@SuppressWarnings("unchecked")
		public <Q, R> void register(@NotNull OperationDescriptor<Q, R> operation, @NotNull Function<Q, R> handler) {
			handlers.put(operation.getId(), (Function<Object, Object>) handler);
		}

		private <Q> @Nullable Object execute(OperationDescriptor<Q, ?> operation, Q request) {
			return handlers.get(operation.getId()).apply(request);
		}
	}

	/**
	 * A player that speaks the given protocol, or none, and records what the binding emits;
	 * {@link #startConnection()} runs the connection listener as the worker does before a new session connects.
	 */
	private static final class RecordingPlayer implements PlayerBindingContext<Object> {
		private final @Nullable JavaProtocol protocol;
		private final List<String> events = new ArrayList<>();
		private final List<Object> payloads = new ArrayList<>();
		private @Nullable Function<Object, Subscription> connections;

		private RecordingPlayer(@Nullable JavaProtocol protocol) {
			this.protocol = protocol;
		}

		private void startConnection() {
			if (connections != null) connections.apply(new Object());
		}

		private <E> List<E> payloads(EventDescriptor<E> event) {
			List<E> matching = new ArrayList<>();
			for (int index = 0; index < events.size(); index++) {
				if (events.get(index).equals(event.getId())) matching.add(event.getPayloadType().cast(payloads.get(index)));
			}

			return matching;
		}

		@Override
		public <P> @NotNull P protocol(@NotNull Class<P> type) {
			if (protocol == null) return PlayerBindingContext.super.protocol(type);

			return type.cast(protocol);
		}

		@Override
		public @NotNull String name() {
			return "Alice";
		}

		@Override
		public @NotNull UUID uniqueId() {
			return UUID.nameUUIDFromBytes("Alice".getBytes());
		}

		@Override
		public void connect() {
			throw new AssertionError("Messages do not drive the lifecycle");
		}

		@Override
		public void disconnect() {
			throw new AssertionError("Messages do not drive the lifecycle");
		}

		@Override
		public void rejoin() {
			throw new AssertionError("Messages do not drive the lifecycle");
		}

		@Override
		public @NotNull Object nativeSession() {
			throw new AssertionError("Messages use the protocol, not the native session");
		}

		@Override
		public boolean isCurrentNativeSession(@NotNull Object nativeSession) {
			throw new AssertionError("Messages use the protocol, not the native session");
		}

		@Override
		public @NotNull Subscription bindNativeSession(@NotNull Function<Object, Subscription> listener) {
			connections = listener;
			return () -> connections = null;
		}

		@Override
		public @NotNull ViewRotation viewRotation() {
			throw new AssertionError("Messages do not use the view rotation");
		}

		@Override
		public void viewRotation(@NotNull ViewRotation rotation) {
			throw new AssertionError("Messages do not change the view rotation");
		}

		@Override
		public <E> void emit(@NotNull EventDescriptor<E> event, @Nullable E payload) {
			events.add(event.getId());
			payloads.add(payload);
		}
	}
}
