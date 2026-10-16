package me.whereareiam.anvil.capability.cookies.mcprotocol;

import me.whereareiam.anvil.capability.api.channel.OperationRegistry;
import me.whereareiam.anvil.capability.api.model.channel.ChannelOperation;
import me.whereareiam.anvil.capability.cookies.CookiesOperations;
import me.whereareiam.anvil.capability.cookies.exchange.AnsweredRequest;
import me.whereareiam.anvil.capability.cookies.exchange.StoredCookie;
import me.whereareiam.anvil.capability.cookies.packet.CookiesPacketListener;
import me.whereareiam.anvil.capability.cookies.packet.CookiesPackets;
import me.whereareiam.anvil.capability.protocol.api.exception.AdapterUnavailableException;
import me.whereareiam.anvil.capability.protocol.api.model.EventDescriptor;
import me.whereareiam.anvil.capability.protocol.api.model.ViewRotation;
import me.whereareiam.anvil.capability.protocol.api.player.channel.Subscription;
import me.whereareiam.anvil.capability.protocol.api.player.worker.PlayerBindingContext;
import me.whereareiam.anvil.capability.protocol.api.player.worker.WorkerExtension;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.UUID;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class McProtocolCookiesExtensionTest {
	private static final byte[] TICKET = "ticket".getBytes(StandardCharsets.UTF_8);

	@Test
	void isDiscoveredAsTheMcProtocolCookiesExtension() {
		WorkerExtension<?> extension = ServiceLoader.load(WorkerExtension.class).stream()
				.map(ServiceLoader.Provider::get)
				.filter(McProtocolCookiesExtension.class::isInstance)
				.findFirst()
				.orElseThrow();

		assertEquals("me.whereareiam.anvil.cookies", extension.id());
		assertEquals(Optional.of("mcprotocol"), extension.libraryId());
		assertEquals(Object.class, extension.nativeSessionType());
	}

	@Test
	void bindsCookiesThroughTheAdapterTheWorkerSelectedForThePlayersRelease() {
		RecordingPackets packets = new RecordingPackets();
		Player player = new Player(packets);
		Operations operations = new Operations();

		new McProtocolCookiesExtension().bind(player, operations);
		operations.execute(CookiesOperations.STORE, new StoredCookie("example:ticket", TICKET));
		packets.listener.requested("example:ticket");

		assertEquals(List.of(CookiesPackets.class), player.requestedPorts);
		assertEquals(List.of("cookies.store", "cookies.remove"), List.copyOf(operations.handlers.keySet()));
		assertSame(Player.SESSION, packets.listened);
		assertEquals(List.of("example:ticket"), packets.answeredKeys);
		assertArrayEquals(TICKET, packets.answers.getFirst());
		AnsweredRequest request = (AnsweredRequest) player.emitted.stream()
				.filter(AnsweredRequest.class::isInstance)
				.findFirst()
				.orElseThrow();
		assertArrayEquals(TICKET, request.getAnswer());
	}

	@Test
	void leavesTheCapabilityToTheWorkerWhenNoAdapterServesTheRelease() {
		AdapterUnavailableException unavailable = new AdapterUnavailableException("no mcprotocol segment selected for Minecraft 1.18.2 "
				+ "provides an adapter for " + CookiesPackets.class.getName());
		Player player = new Player(null) {
			@Override
			public <P> @NotNull P adapter(@NotNull Class<P> port) {
				throw unavailable;
			}
		};
		Operations operations = new Operations();

		assertSame(unavailable, assertThrows(AdapterUnavailableException.class, () -> new McProtocolCookiesExtension().bind(player, operations)));
		assertEquals(Map.of(), operations.handlers);
	}

	private static final class RecordingPackets implements CookiesPackets<String> {
		private final List<String> answeredKeys = new ArrayList<>();
		private final List<byte @Nullable []> answers = new ArrayList<>();
		private @Nullable String listened;
		private CookiesPacketListener listener;

		@Override
		public @NotNull Class<String> sessionType() {
			return String.class;
		}

		@Override
		public void answer(@NotNull String session, @NotNull String key, byte @Nullable [] payload) {
			answeredKeys.add(key);
			answers.add(payload);
		}

		@Override
		public @NotNull Runnable listen(@NotNull String session, @NotNull CookiesPacketListener listener) {
			listened = session;
			this.listener = listener;
			return () -> listened = null;
		}
	}

	private static class Player implements PlayerBindingContext<Object> {
		private static final String SESSION = "native session";

		private final @Nullable CookiesPackets<?> packets;
		private final List<Class<?>> requestedPorts = new ArrayList<>();
		private final List<Object> emitted = new ArrayList<>();

		private Player(@Nullable CookiesPackets<?> packets) {
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
			emitted.add(payload);
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
