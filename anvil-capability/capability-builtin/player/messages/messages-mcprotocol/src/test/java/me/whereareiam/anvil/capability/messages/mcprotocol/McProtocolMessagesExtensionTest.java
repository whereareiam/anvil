package me.whereareiam.anvil.capability.messages.mcprotocol;

import me.whereareiam.anvil.capability.api.channel.OperationRegistry;
import me.whereareiam.anvil.capability.api.model.channel.ChannelOperation;
import me.whereareiam.anvil.capability.messages.MessagesOperations;
import me.whereareiam.anvil.capability.messages.model.MessageText;
import me.whereareiam.anvil.capability.messages.packet.MessagesPackets;
import me.whereareiam.anvil.capability.protocol.api.exception.AdapterUnavailableException;
import me.whereareiam.anvil.capability.protocol.api.model.EventDescriptor;
import me.whereareiam.anvil.capability.protocol.api.model.ViewRotation;
import me.whereareiam.anvil.capability.protocol.api.player.channel.Subscription;
import me.whereareiam.anvil.capability.protocol.api.player.worker.PlayerBindingContext;
import me.whereareiam.anvil.capability.protocol.api.player.worker.WorkerExtension;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class McProtocolMessagesExtensionTest {
	@Test
	void isDiscoveredAsTheMcProtocolMessagesExtension() {
		WorkerExtension<?> extension = ServiceLoader.load(WorkerExtension.class).stream()
				.map(ServiceLoader.Provider::get)
				.filter(McProtocolMessagesExtension.class::isInstance)
				.findFirst()
				.orElseThrow();

		assertEquals("me.whereareiam.anvil.messages", extension.id());
		assertEquals(Optional.of("mcprotocol"), extension.libraryId());
		assertEquals(Object.class, extension.nativeSessionType());
	}

	@Test
	void bindsMessagesThroughTheAdapterTheWorkerSelectedForThePlayersRelease() {
		RecordingPackets packets = new RecordingPackets();
		Player player = new Player(packets);
		Operations operations = new Operations();

		new McProtocolMessagesExtension().bind(player, operations);
		assertNull(operations.execute(MessagesOperations.CHAT, new MessageText("hello")));
		assertNull(operations.execute(MessagesOperations.COMMAND, new MessageText("list")));
		packets.listener.accept("anvil:welcome");

		assertEquals(List.of(MessagesPackets.class), player.requestedPorts);
		assertEquals(List.of("messages.chat", "messages.command"), List.copyOf(operations.handlers.keySet()));
		assertEquals(List.of("chat hello", "command list"), packets.sent);
		assertSame(Player.SESSION, packets.listened);
		assertEquals(List.of(new MessageText("anvil:welcome")), player.emitted);
	}

	@Test
	void leavesTheCapabilityToTheWorkerWhenNoAdapterServesTheRelease() {
		AdapterUnavailableException unavailable = new AdapterUnavailableException("no mcprotocol segment selected for Minecraft 1.16.5 "
				+ "provides an adapter for " + MessagesPackets.class.getName());
		Player player = new Player(null) {
			@Override
			public <P> @NotNull P adapter(@NotNull Class<P> port) {
				throw unavailable;
			}
		};
		Operations operations = new Operations();

		assertSame(unavailable, assertThrows(AdapterUnavailableException.class, () -> new McProtocolMessagesExtension().bind(player, operations)));
		assertEquals(Map.of(), operations.handlers);
	}

	private static final class RecordingPackets implements MessagesPackets<String> {
		private final List<String> sent = new ArrayList<>();
		private @Nullable String listened;
		private Consumer<String> listener = text -> {
			throw new AssertionError("No session is observed");
		};

		@Override
		public @NotNull Class<String> sessionType() {
			return String.class;
		}

		@Override
		public void chat(@NotNull String session, @NotNull String text) {
			sent.add("chat " + text);
		}

		@Override
		public void command(@NotNull String session, @NotNull String commandWithoutSlash) {
			sent.add("command " + commandWithoutSlash);
		}

		@Override
		public @NotNull Runnable listen(@NotNull String session, @NotNull Consumer<String> plainText) {
			listened = session;
			listener = plainText;
			return () -> listened = null;
		}
	}

	private static class Player implements PlayerBindingContext<Object> {
		private static final String SESSION = "native session";

		private final @Nullable MessagesPackets<?> packets;
		private final List<Class<?>> requestedPorts = new ArrayList<>();
		private final List<MessageText> emitted = new ArrayList<>();

		private Player(@Nullable MessagesPackets<?> packets) {
			this.packets = packets;
		}

		public @NotNull String name() { return "Alice"; }
		public @NotNull UUID uniqueId() { return new UUID(0, 1); }
		public void connect() { throw new AssertionError(); }
		public void disconnect() { throw new AssertionError(); }
		public void rejoin() { throw new AssertionError(); }
		public @NotNull Object nativeSession() { return SESSION; }
		public boolean isCurrentNativeSession(@NotNull Object nativeSession) { return nativeSession == SESSION; }
		public @NotNull ViewRotation viewRotation() { throw new AssertionError(); }
		public void viewRotation(@NotNull ViewRotation rotation) { throw new AssertionError(); }

		@Override
		public @NotNull Subscription bindNativeSession(@NotNull Function<Object, Subscription> listener) {
			return listener.apply(SESSION);
		}

		@Override
		public <E> void emit(@NotNull EventDescriptor<E> eventDescriptor, @Nullable E payload) {
			assertSame(MessagesOperations.RECEIVED, eventDescriptor);
			emitted.add((MessageText) payload);
		}

		@Override
		public <P> @NotNull P adapter(@NotNull Class<P> port) {
			requestedPorts.add(port);
			return port.cast(packets);
		}
	}

	private static final class Operations implements OperationRegistry {
		private final Map<String, Function<Object, Object>> handlers = new LinkedHashMap<>();

		@Override
		@SuppressWarnings("unchecked")
		public <Q, R> void register(@NotNull ChannelOperation<Q, R> operation, @NotNull Function<Q, R> handler) {
			handlers.put(operation.getId(), request -> handler.apply((Q) request));
		}

		private <Q, R> @Nullable Object execute(ChannelOperation<Q, R> operation, Q request) {
			return handlers.get(operation.getId()).apply(request);
		}
	}
}
