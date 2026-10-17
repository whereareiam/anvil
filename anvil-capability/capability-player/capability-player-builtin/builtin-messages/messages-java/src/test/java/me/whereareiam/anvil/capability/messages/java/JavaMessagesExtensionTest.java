package me.whereareiam.anvil.capability.messages.java;

import me.whereareiam.anvil.capability.messages.channel.MessagesChannel;
import me.whereareiam.anvil.capability.messages.model.MessageText;
import me.whereareiam.anvil.protocol.api.channel.Subscription;
import me.whereareiam.anvil.protocol.api.exception.ProtocolUnavailableException;
import me.whereareiam.anvil.protocol.api.model.channel.EventDescriptor;
import me.whereareiam.anvil.protocol.api.model.channel.OperationDescriptor;
import me.whereareiam.anvil.protocol.api.model.player.ViewRotation;
import me.whereareiam.anvil.protocol.api.worker.OperationRegistry;
import me.whereareiam.anvil.protocol.api.worker.PlayerBindingContext;
import me.whereareiam.anvil.protocol.api.worker.WorkerBinding;
import me.whereareiam.anvil.protocol.java.api.JavaProtocol;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.JavaPacket;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.chat.ClientboundDisguisedChat;
import me.whereareiam.anvil.protocol.java.api.packet.chat.ClientboundPlayerChat;
import me.whereareiam.anvil.protocol.java.api.packet.chat.ClientboundSystemChat;
import me.whereareiam.anvil.protocol.java.api.packet.chat.ServerboundChat;
import me.whereareiam.anvil.protocol.java.api.packet.chat.ServerboundChatCommand;
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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JavaMessagesExtensionTest {
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
	void emitsTheTextOfEveryKindOfChatTheProtocolDelivers() {
		RecordingProtocol protocol = new RecordingProtocol(true);
		RecordingPlayer player = new RecordingPlayer(protocol);
		extension.bind(player, new RecordingOperations());

		protocol.deliver(new ClientboundSystemChat("multiplayer.player.joined Alice", false));
		protocol.deliver(new ClientboundSystemChat("anvil:action-bar", true));
		protocol.deliver(new ClientboundPlayerChat("decorated hello"));
		protocol.deliver(new ClientboundDisguisedChat("journey-ready-1"));

		assertEquals(List.of(
				new MessageText("multiplayer.player.joined Alice"),
				new MessageText("anvil:action-bar"),
				new MessageText("decorated hello"),
				new MessageText("journey-ready-1")
		), player.emitted);
	}

	@Test
	void leavesDisguisedChatAloneOnAReleaseWithoutIt() {
		RecordingProtocol protocol = new RecordingProtocol(false);
		extension.bind(new RecordingPlayer(protocol), new RecordingOperations());

		assertEquals(Set.of(ClientboundSystemChat.class, ClientboundPlayerChat.class), protocol.listeners.keySet());
	}

	@Test
	void stopsListeningWhenClosed() {
		RecordingProtocol protocol = new RecordingProtocol(true);
		RecordingPlayer player = new RecordingPlayer(protocol);
		WorkerBinding binding = extension.bind(player, new RecordingOperations());

		binding.close();
		protocol.deliver(new ClientboundSystemChat("after close", false));

		assertEquals(Map.of(), protocol.listeners);
		assertEquals(List.of(), player.emitted);
	}

	@Test
	void isUnavailableForAPlayerThatDoesNotSpeakTheJavaProtocol() {
		RecordingPlayer player = new RecordingPlayer(null);

		assertThrows(ProtocolUnavailableException.class, () -> extension.bind(player, new RecordingOperations()));
	}

	/**
	 * Records sent packets and hands delivered packets to the listener of their class.
	 */
	private static final class RecordingProtocol implements JavaProtocol {
		private final boolean disguisedChat;
		private final List<ServerboundPacket> sent = new ArrayList<>();
		private final Map<Class<?>, Consumer<Object>> listeners = new LinkedHashMap<>();

		private RecordingProtocol(boolean disguisedChat) {
			this.disguisedChat = disguisedChat;
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
			return disguisedChat || type != ClientboundDisguisedChat.class;
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
	 * A player that speaks the given protocol, or none, and records what the binding emits.
	 */
	private static final class RecordingPlayer implements PlayerBindingContext<Object> {
		private final @Nullable JavaProtocol protocol;
		private final List<MessageText> emitted = new ArrayList<>();

		private RecordingPlayer(@Nullable JavaProtocol protocol) {
			this.protocol = protocol;
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
			throw new AssertionError("Messages use the protocol, not the native session");
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
			assertSame(MessagesChannel.RECEIVED, event);
			emitted.add((MessageText) payload);
		}
	}
}
