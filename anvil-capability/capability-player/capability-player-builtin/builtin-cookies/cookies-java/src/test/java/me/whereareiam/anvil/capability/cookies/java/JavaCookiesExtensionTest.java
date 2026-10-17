package me.whereareiam.anvil.capability.cookies.java;

import me.whereareiam.anvil.capability.cookies.channel.CookiesChannel;
import me.whereareiam.anvil.capability.cookies.model.CookieKey;
import me.whereareiam.anvil.capability.cookies.model.CookieRequest;
import me.whereareiam.anvil.capability.cookies.model.HeldCookies;
import me.whereareiam.anvil.capability.cookies.model.StoredCookie;
import me.whereareiam.anvil.protocol.api.channel.Subscription;
import me.whereareiam.anvil.protocol.api.exception.ProtocolUnavailableException;
import me.whereareiam.anvil.protocol.api.model.channel.EventDescriptor;
import me.whereareiam.anvil.protocol.api.model.channel.OperationDescriptor;
import me.whereareiam.anvil.protocol.api.model.player.ViewRotation;
import me.whereareiam.anvil.protocol.api.worker.OperationRegistry;
import me.whereareiam.anvil.protocol.api.worker.PlayerBindingContext;
import me.whereareiam.anvil.protocol.api.worker.WorkerBinding;
import me.whereareiam.anvil.protocol.api.worker.WorkerExtension;
import me.whereareiam.anvil.protocol.java.api.JavaProtocol;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.JavaPacket;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.cookie.ClientboundCookieRequest;
import me.whereareiam.anvil.protocol.java.api.packet.cookie.ClientboundStoreCookie;
import me.whereareiam.anvil.protocol.java.api.packet.cookie.ServerboundCookieResponse;
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
import java.util.function.Consumer;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JavaCookiesExtensionTest {
	private static final byte[] TICKET = "ticket".getBytes(StandardCharsets.UTF_8);

	private final JavaCookiesExtension extension = new JavaCookiesExtension();

	@Test
	void isDiscoveredAsTheEditionWideCookiesExtension() {
		WorkerExtension<?> discovered = ServiceLoader.load(WorkerExtension.class).stream()
				.map(ServiceLoader.Provider::get)
				.filter(JavaCookiesExtension.class::isInstance)
				.findFirst()
				.orElseThrow();

		assertEquals("me.whereareiam.anvil.cookies", discovered.id());
		assertEquals(Optional.empty(), discovered.libraryId());
		assertEquals(Object.class, discovered.nativeSessionType());
	}

	@Test
	void answersARequestWithTheCookieTheServerStoredOnTheSameConnection() {
		RecordingProtocol protocol = new RecordingProtocol(true);
		RecordingPlayer player = new RecordingPlayer(protocol);
		RecordingOperations operations = new RecordingOperations();
		extension.bind(player, operations);
		player.startConnection();

		protocol.deliver(new ClientboundStoreCookie("example:ticket", TICKET));
		protocol.deliver(new ClientboundCookieRequest("example:ticket"));
		protocol.deliver(new ClientboundCookieRequest("example:other"));

		assertEquals(List.of("cookies.store", "cookies.remove"), List.copyOf(operations.handlers.keySet()));
		assertEquals(List.of("example:ticket", "example:other"),
				protocol.sent.stream().map(ServerboundCookieResponse::getKey).toList());
		assertArrayEquals(TICKET, protocol.sent.get(0).getPayload());
		assertNull(protocol.sent.get(1).getPayload());
		List<CookieRequest> requests = player.events(CookiesChannel.REQUESTED);
		assertEquals("example:ticket", requests.get(0).getKey());
		assertArrayEquals(TICKET, requests.get(0).getAnswer());
		assertNull(requests.get(1).getAnswer());
	}

	@Test
	void dropsTheCookiesOfAConnectionWhenItEnds() {
		RecordingProtocol protocol = new RecordingProtocol(true);
		RecordingPlayer player = new RecordingPlayer(protocol);
		extension.bind(player, new RecordingOperations());
		player.startConnection();
		protocol.deliver(new ClientboundStoreCookie("example:ticket", TICKET));
		player.endConnection();
		Map<String, byte[]> afterEnd = player.lastHeld().getCookies();

		player.startConnection();
		protocol.deliver(new ClientboundCookieRequest("example:ticket"));

		assertEquals(Map.of(), afterEnd);
		assertNull(protocol.sent.getLast().getPayload());
		assertEquals(Map.of(), player.lastHeld().getCookies());
	}

	@Test
	void startsTheNextConnectionWithTheCookiesGivenWhileDisconnected() {
		RecordingProtocol protocol = new RecordingProtocol(true);
		RecordingPlayer player = new RecordingPlayer(protocol);
		RecordingOperations operations = new RecordingOperations();
		extension.bind(player, operations);

		HeldCookies before = operations.execute(CookiesChannel.STORE, new StoredCookie("example:ticket", TICKET));
		player.startConnection();
		protocol.deliver(new ClientboundCookieRequest("example:ticket"));

		assertArrayEquals(TICKET, before.getCookies().get("example:ticket"));
		assertArrayEquals(TICKET, protocol.sent.getLast().getPayload());
		assertArrayEquals(TICKET, player.lastHeld().getCookies().get("example:ticket"));
	}

	@Test
	void givesCookiesToTheNextConnectionOnceTheOpenOneEnded() {
		RecordingProtocol protocol = new RecordingProtocol(true);
		RecordingPlayer player = new RecordingPlayer(protocol);
		RecordingOperations operations = new RecordingOperations();
		extension.bind(player, operations);
		player.startConnection();
		protocol.deliver(new ClientboundStoreCookie("example:ticket", TICKET));
		player.endConnection();

		HeldCookies given = operations.execute(CookiesChannel.STORE, new StoredCookie("example:arrival", TICKET));
		player.startConnection();
		protocol.deliver(new ClientboundCookieRequest("example:ticket"));
		protocol.deliver(new ClientboundCookieRequest("example:arrival"));

		assertEquals(List.of("example:arrival"), List.copyOf(given.getCookies().keySet()));
		assertNull(protocol.sent.get(0).getPayload());
		assertArrayEquals(TICKET, protocol.sent.get(1).getPayload());
	}

	@Test
	void givesAndTakesAwayCookiesOfTheOpenConnection() {
		RecordingProtocol protocol = new RecordingProtocol(true);
		RecordingPlayer player = new RecordingPlayer(protocol);
		RecordingOperations operations = new RecordingOperations();
		extension.bind(player, operations);
		player.startConnection();

		operations.execute(CookiesChannel.STORE, new StoredCookie("example:ticket", TICKET));
		protocol.deliver(new ClientboundCookieRequest("example:ticket"));
		HeldCookies removed = operations.execute(CookiesChannel.REMOVE, new CookieKey("example:ticket"));
		protocol.deliver(new ClientboundCookieRequest("example:ticket"));

		assertArrayEquals(TICKET, protocol.sent.get(0).getPayload());
		assertNull(protocol.sent.get(1).getPayload());
		assertEquals(Map.of(), removed.getCookies());
	}

	@Test
	void numbersEveryChangeOfTheHeldCookies() {
		RecordingProtocol protocol = new RecordingProtocol(true);
		RecordingPlayer player = new RecordingPlayer(protocol);
		RecordingOperations operations = new RecordingOperations();
		extension.bind(player, operations);

		player.startConnection();
		protocol.deliver(new ClientboundStoreCookie("example:ticket", TICKET));
		operations.execute(CookiesChannel.REMOVE, new CookieKey("example:ticket"));
		player.startConnection();

		// The replaced connection ends before the next one starts: both are changes of the held cookies.
		List<Long> versions = player.events(CookiesChannel.HELD).stream().map(HeldCookies::getVersion).toList();
		assertEquals(List.of(1L, 2L, 3L, 4L, 5L), versions);
	}

	@Test
	void keepsACopyOfEveryPayload() {
		RecordingProtocol protocol = new RecordingProtocol(true);
		RecordingPlayer player = new RecordingPlayer(protocol);
		extension.bind(player, new RecordingOperations());
		player.startConnection();
		byte[] payload = TICKET.clone();

		protocol.deliver(new ClientboundStoreCookie("example:ticket", payload));
		payload[0] = 'X';
		protocol.deliver(new ClientboundCookieRequest("example:ticket"));

		assertArrayEquals(TICKET, protocol.sent.getLast().getPayload());
	}

	@Test
	void stopsListeningAndObservingConnectionsWhenClosed() {
		RecordingProtocol protocol = new RecordingProtocol(true);
		RecordingPlayer player = new RecordingPlayer(protocol);
		WorkerBinding binding = extension.bind(player, new RecordingOperations());
		player.startConnection();

		binding.close();

		assertEquals(Map.of(), protocol.listeners);
		assertNull(player.connections);
	}

	@Test
	void isUnavailableForAReleaseWithoutCookies() {
		RecordingProtocol protocol = new RecordingProtocol(false);
		RecordingPlayer player = new RecordingPlayer(protocol);
		RecordingOperations operations = new RecordingOperations();

		assertThrows(ProtocolUnavailableException.class, () -> extension.bind(player, operations));

		assertEquals(Map.of(), operations.handlers);
		assertTrue(protocol.listeners.isEmpty());
		assertNull(player.connections);
	}

	@Test
	void isUnavailableForAPlayerThatDoesNotSpeakTheJavaProtocol() {
		RecordingPlayer player = new RecordingPlayer(null);
		RecordingOperations operations = new RecordingOperations();

		assertThrows(ProtocolUnavailableException.class, () -> extension.bind(player, operations));

		assertEquals(Map.of(), operations.handlers);
		assertNull(player.connections);
	}

	/**
	 * Hands delivered packets to the listener of their class and records the answers sent; it carries the cookie
	 * packets only when told so.
	 */
	private static final class RecordingProtocol implements JavaProtocol {
		private final boolean cookies;
		private final Map<Class<?>, Consumer<Object>> listeners = new LinkedHashMap<>();
		private final List<ServerboundCookieResponse> sent = new ArrayList<>();

		private RecordingProtocol(boolean cookies) {
			this.cookies = cookies;
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
			sent.add((ServerboundCookieResponse) packet);
		}

		@Override
		public <P extends ServerboundPacket> @NotNull Subscription listenSent(
				@NotNull Class<P> type,
				@NotNull Consumer<? super P> listener
		) {
			throw new AssertionError("The cookies binding observes no sent packets");
		}

		@Override
		public boolean supports(@NotNull Class<? extends JavaPacket> type) {
			return cookies;
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

		@SuppressWarnings("unchecked")
		private <Q, R> R execute(OperationDescriptor<Q, R> operation, Q request) {
			return (R) handlers.get(operation.getId()).apply(request);
		}
	}

	/**
	 * A player that speaks the given protocol, or none, whose connections the test starts and ends: each
	 * {@link #startConnection()} replaces the native session and runs the connection listener as the worker does
	 * before a new session connects, and {@link #endConnection()} ends the current one as the worker does.
	 */
	private static final class RecordingPlayer implements PlayerBindingContext<Object> {
		private final @Nullable JavaProtocol protocol;
		private final List<Object[]> emitted = new ArrayList<>();
		private @Nullable Object session;
		private boolean connected;
		private @Nullable Function<Object, Subscription> connections;
		private @Nullable Subscription generation;

		private RecordingPlayer(@Nullable JavaProtocol protocol) {
			this.protocol = protocol;
		}

		private void startConnection() {
			if (generation != null) generation.close();
			generation = null;
			session = new Object();
			connected = true;
			if (connections != null) generation = connections.apply(session);
		}

		private void endConnection() {
			connected = false;
			if (generation != null) generation.close();
			generation = null;
		}

		@SuppressWarnings("unchecked")
		private <E> List<E> events(EventDescriptor<E> descriptor) {
			return emitted.stream()
					.filter(event -> event[0] == descriptor)
					.map(event -> (E) event[1])
					.toList();
		}

		private HeldCookies lastHeld() {
			return events(CookiesChannel.HELD).getLast();
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
			return UUID.nameUUIDFromBytes("Alice".getBytes(StandardCharsets.UTF_8));
		}

		@Override
		public void connect() {
			throw new AssertionError("Cookies do not drive the lifecycle");
		}

		@Override
		public void disconnect() {
			throw new AssertionError("Cookies do not drive the lifecycle");
		}

		@Override
		public void rejoin() {
			throw new AssertionError("Cookies do not drive the lifecycle");
		}

		@Override
		public @NotNull Object nativeSession() {
			if (session == null || !connected) throw new IllegalStateException("Player Alice is not connected");

			return session;
		}

		@Override
		public boolean isCurrentNativeSession(@NotNull Object nativeSession) {
			return nativeSession == session;
		}

		@Override
		public @NotNull Subscription bindNativeSession(@NotNull Function<Object, Subscription> listener) {
			connections = listener;
			return () -> {
				connections = null;
				if (generation != null) generation.close();
				generation = null;
			};
		}

		@Override
		public @NotNull ViewRotation viewRotation() {
			throw new AssertionError("Cookies do not use the view rotation");
		}

		@Override
		public void viewRotation(@NotNull ViewRotation rotation) {
			throw new AssertionError("Cookies do not change the view rotation");
		}

		@Override
		public <E> void emit(@NotNull EventDescriptor<E> event, @Nullable E payload) {
			emitted.add(new Object[]{event, payload});
		}
	}
}
