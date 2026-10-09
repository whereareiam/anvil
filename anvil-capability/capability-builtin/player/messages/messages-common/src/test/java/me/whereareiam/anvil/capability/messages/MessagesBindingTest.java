package me.whereareiam.anvil.capability.messages;

import me.whereareiam.anvil.capability.api.channel.OperationRegistry;
import me.whereareiam.anvil.capability.api.model.channel.ChannelOperation;
import me.whereareiam.anvil.capability.messages.model.MessageText;
import me.whereareiam.anvil.capability.messages.packet.MessagesPackets;
import me.whereareiam.anvil.capability.protocol.api.model.EventDescriptor;
import me.whereareiam.anvil.capability.protocol.api.model.ViewRotation;
import me.whereareiam.anvil.capability.protocol.api.player.channel.Subscription;
import me.whereareiam.anvil.capability.protocol.api.player.worker.PlayerBindingContext;
import me.whereareiam.anvil.capability.protocol.api.player.worker.WorkerBinding;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessagesBindingTest {
	@Test
	void sendsChatAndCommandsThroughThePortWithThePlayersSession() {
		RecordingPackets packets = new RecordingPackets();
		RecordingPlayer player = new RecordingPlayer();
		player.startSession(new NativeSession());
		RecordingOperations operations = new RecordingOperations();
		new MessagesBinding<>(packets).bind(player, operations);

		assertNull(operations.execute(MessagesOperations.CHAT, new MessageText("hello")));
		assertNull(operations.execute(MessagesOperations.COMMAND, new MessageText("anvil-fixture ping")));

		assertEquals(List.of("messages.chat", "messages.command"), List.copyOf(operations.handlers.keySet()));
		assertEquals(List.of("chat hello", "command anvil-fixture ping"), packets.sent);
		assertEquals(List.of(player.session, player.session), packets.sessions);
	}

	@Test
	void refusesASessionOfAnotherTypeBeforeSending() {
		RecordingPackets packets = new RecordingPackets();
		RecordingPlayer player = new RecordingPlayer();
		player.session = "not a native session";
		RecordingOperations operations = new RecordingOperations();
		new MessagesBinding<>(packets).bind(player, operations);

		assertThrows(ClassCastException.class, () -> operations.execute(MessagesOperations.CHAT, new MessageText("hello")));
		assertThrows(ClassCastException.class, () -> operations.execute(MessagesOperations.COMMAND, new MessageText("list")));

		assertEquals(List.of(), packets.sent);
	}

	@Test
	void sendsNothingWhileThePlayerIsDisconnected() {
		RecordingPackets packets = new RecordingPackets();
		RecordingPlayer player = new RecordingPlayer();
		RecordingOperations operations = new RecordingOperations();
		new MessagesBinding<>(packets).bind(player, operations);

		IllegalStateException failure = assertThrows(IllegalStateException.class,
				() -> operations.execute(MessagesOperations.CHAT, new MessageText("hello")));

		assertTrue(failure.getMessage().contains("not connected"), failure.getMessage());
		assertEquals(List.of(), packets.sent);
	}

	@Test
	void emitsMessagesOfTheCurrentSessionOnlyAndListensToEachNewSession() {
		RecordingPackets packets = new RecordingPackets();
		RecordingPlayer player = new RecordingPlayer();
		new MessagesBinding<>(packets).bind(player, new RecordingOperations());
		NativeSession first = new NativeSession();
		NativeSession second = new NativeSession();

		player.startSession(first);
		packets.deliver(first, "first hello");
		player.session = second;
		packets.deliver(first, "late from the first session");
		player.startSession(second);
		packets.deliver(second, "second hello");

		assertEquals(List.of(new MessageText("first hello"), new MessageText("second hello")), player.emitted);
		assertEquals(List.of(second), List.copyOf(packets.listeners.keySet()));
	}

	@Test
	void removesTheListenerOfTheCurrentSessionWhenClosed() {
		RecordingPackets packets = new RecordingPackets();
		RecordingPlayer player = new RecordingPlayer();
		WorkerBinding binding = new MessagesBinding<>(packets).bind(player, new RecordingOperations());
		NativeSession session = new NativeSession();
		player.startSession(session);

		binding.close();
		packets.deliver(session, "after close");

		assertEquals(Map.of(), packets.listeners);
		assertEquals(List.of(), player.emitted);
	}

	@Test
	void refusesToListenToASessionOfAnotherType() {
		RecordingPackets packets = new RecordingPackets();
		RecordingPlayer player = new RecordingPlayer();
		new MessagesBinding<>(packets).bind(player, new RecordingOperations());

		assertThrows(ClassCastException.class, () -> player.startSession("not a native session"));

		assertEquals(Map.of(), packets.listeners);
	}

	private static final class NativeSession {
	}

	/**
	 * Records what the binding sends and delivers messages to the listener of each session.
	 */
	private static final class RecordingPackets implements MessagesPackets<NativeSession> {
		private final List<String> sent = new ArrayList<>();
		private final List<NativeSession> sessions = new ArrayList<>();
		private final Map<NativeSession, Consumer<String>> listeners = new LinkedHashMap<>();

		@Override
		public @NotNull Class<NativeSession> sessionType() {
			return NativeSession.class;
		}

		@Override
		public void chat(@NotNull NativeSession session, @NotNull String text) {
			sessions.add(session);
			sent.add("chat " + text);
		}

		@Override
		public void command(@NotNull NativeSession session, @NotNull String commandWithoutSlash) {
			sessions.add(session);
			sent.add("command " + commandWithoutSlash);
		}

		@Override
		public @NotNull Runnable listen(@NotNull NativeSession session, @NotNull Consumer<String> plainText) {
			listeners.put(session, plainText);
			return () -> listeners.remove(session);
		}

		private void deliver(NativeSession session, String text) {
			Consumer<String> listener = listeners.get(session);
			if (listener != null) listener.accept(text);
		}
	}

	private static final class RecordingOperations implements OperationRegistry {
		private final Map<String, Function<Object, Object>> handlers = new LinkedHashMap<>();

		@Override
		@SuppressWarnings("unchecked")
		public <Q, R> void register(@NotNull ChannelOperation<Q, R> channelOperation, @NotNull Function<Q, R> handler) {
			handlers.put(channelOperation.getId(), (Function<Object, Object>) handler);
		}

		private <Q> @Nullable Object execute(ChannelOperation<Q, ?> operation, Q request) {
			return handlers.get(operation.getId()).apply(request);
		}
	}

	/**
	 * A player whose native session generations the test starts: each {@link #startSession(Object)} closes the listeners
	 * of the previous generation and binds them for the new session, as the worker does before a session connects.
	 */
	private static final class RecordingPlayer implements PlayerBindingContext<Object> {
		private final List<MessageText> emitted = new ArrayList<>();
		private @Nullable Object session;
		private @Nullable Function<Object, Subscription> listener;
		private @Nullable Subscription generation;

		private void startSession(Object nativeSession) {
			if (generation != null) generation.close();
			generation = null;
			session = nativeSession;
			if (listener != null) generation = listener.apply(nativeSession);
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
			if (session == null) throw new IllegalStateException("Player Alice is not connected");

			return session;
		}

		@Override
		public boolean isCurrentNativeSession(@NotNull Object nativeSession) {
			return nativeSession == session;
		}

		@Override
		public @NotNull Subscription bindNativeSession(@NotNull Function<Object, Subscription> listener) {
			this.listener = listener;
			return () -> {
				this.listener = null;
				if (generation != null) generation.close();
				generation = null;
			};
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
		public <E> void emit(@NotNull EventDescriptor<E> eventDescriptor, @Nullable E payload) {
			assertSame(MessagesOperations.RECEIVED, eventDescriptor);
			emitted.add((MessageText) payload);
		}
	}
}
