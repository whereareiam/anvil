package me.whereareiam.anvil.capability.cookies;

import me.whereareiam.anvil.capability.cookies.exchange.AnsweredRequest;
import me.whereareiam.anvil.capability.cookies.exchange.CookieKey;
import me.whereareiam.anvil.capability.cookies.exchange.HeldCookies;
import me.whereareiam.anvil.capability.cookies.exchange.StoredCookie;
import me.whereareiam.anvil.capability.cookies.packet.CookiesPacketListener;
import me.whereareiam.anvil.capability.cookies.packet.CookiesPackets;
import me.whereareiam.anvil.protocol.api.channel.Subscription;
import me.whereareiam.anvil.protocol.api.model.channel.EventDescriptor;
import me.whereareiam.anvil.protocol.api.model.channel.OperationDescriptor;
import me.whereareiam.anvil.protocol.api.model.player.ViewRotation;
import me.whereareiam.anvil.protocol.api.worker.OperationRegistry;
import me.whereareiam.anvil.protocol.api.worker.PlayerBindingContext;
import me.whereareiam.anvil.protocol.api.worker.WorkerBinding;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CookiesBindingTest {
	private static final byte[] TICKET = "ticket".getBytes(StandardCharsets.UTF_8);

	@Test
	void answersARequestWithTheCookieTheServerStoredOnTheSameConnection() {
		RecordingPackets packets = new RecordingPackets();
		RecordingPlayer player = new RecordingPlayer();
		new CookiesBinding<>(packets).bind(player, new RecordingOperations());
		NativeSession session = new NativeSession();
		player.startSession(session);

		packets.listener(session).stored("example:ticket", TICKET);
		packets.listener(session).requested("example:ticket");
		packets.listener(session).requested("example:other");

		assertEquals(List.of("example:ticket", "example:other"), packets.answeredKeys);
		assertArrayEquals(TICKET, packets.answers.get(0));
		assertNull(packets.answers.get(1));
		assertEquals(List.of(session, session), packets.answeredSessions);
		List<AnsweredRequest> requests = player.events(CookiesOperations.REQUESTED);
		assertEquals("example:ticket", requests.get(0).getKey());
		assertArrayEquals(TICKET, requests.get(0).getAnswer());
		assertNull(requests.get(1).getAnswer());
	}

	@Test
	void dropsTheCookiesOfAConnectionWhenItEnds() {
		RecordingPackets packets = new RecordingPackets();
		RecordingPlayer player = new RecordingPlayer();
		new CookiesBinding<>(packets).bind(player, new RecordingOperations());
		NativeSession first = new NativeSession();
		player.startSession(first);
		packets.listener(first).stored("example:ticket", TICKET);
		packets.listener(first).closed();

		NativeSession second = new NativeSession();
		player.startSession(second);
		packets.listener(second).requested("example:ticket");

		assertNull(packets.answers.getLast());
		assertEquals(Map.of(), player.lastHeld().getCookies());
	}

	@Test
	void startsTheNextConnectionWithTheCookiesGivenWhileDisconnected() {
		RecordingPackets packets = new RecordingPackets();
		RecordingPlayer player = new RecordingPlayer();
		RecordingOperations operations = new RecordingOperations();
		new CookiesBinding<>(packets).bind(player, operations);

		HeldCookies before = operations.execute(CookiesOperations.STORE, new StoredCookie("example:ticket", TICKET));
		NativeSession session = new NativeSession();
		player.startSession(session);
		packets.listener(session).requested("example:ticket");

		assertArrayEquals(TICKET, before.getCookies().get("example:ticket"));
		assertArrayEquals(TICKET, packets.answers.getLast());
		assertArrayEquals(TICKET, player.lastHeld().getCookies().get("example:ticket"));
	}

	@Test
	void givesAndTakesAwayCookiesOfTheOpenConnection() {
		RecordingPackets packets = new RecordingPackets();
		RecordingPlayer player = new RecordingPlayer();
		RecordingOperations operations = new RecordingOperations();
		new CookiesBinding<>(packets).bind(player, operations);
		NativeSession session = new NativeSession();
		player.startSession(session);

		operations.execute(CookiesOperations.STORE, new StoredCookie("example:ticket", TICKET));
		packets.listener(session).requested("example:ticket");
		HeldCookies removed = operations.execute(CookiesOperations.REMOVE, new CookieKey("example:ticket"));
		packets.listener(session).requested("example:ticket");

		assertArrayEquals(TICKET, packets.answers.get(0));
		assertNull(packets.answers.get(1));
		assertEquals(Map.of(), removed.getCookies());
	}

	@Test
	void ignoresALateCloseAndLateRequestsOfAReplacedConnection() {
		RecordingPackets packets = new RecordingPackets();
		RecordingPlayer player = new RecordingPlayer();
		RecordingOperations operations = new RecordingOperations();
		new CookiesBinding<>(packets).bind(player, operations);
		NativeSession first = new NativeSession();
		player.startSession(first);
		CookiesPacketListener firstListener = packets.listener(first);
		NativeSession second = new NativeSession();
		player.startSession(second);
		operations.execute(CookiesOperations.STORE, new StoredCookie("example:ticket", TICKET));

		firstListener.closed();
		firstListener.stored("example:late", TICKET);
		firstListener.requested("example:ticket");
		packets.listener(second).requested("example:ticket");

		assertNull(packets.answers.get(0));
		assertArrayEquals(TICKET, packets.answers.get(1));
		assertEquals(1, player.events(CookiesOperations.REQUESTED).size());
		assertEquals(List.of("example:ticket"), List.copyOf(player.lastHeld().getCookies().keySet()));
	}

	@Test
	void numbersEveryChangeOfTheHeldCookies() {
		RecordingPackets packets = new RecordingPackets();
		RecordingPlayer player = new RecordingPlayer();
		RecordingOperations operations = new RecordingOperations();
		new CookiesBinding<>(packets).bind(player, operations);
		NativeSession session = new NativeSession();

		player.startSession(session);
		packets.listener(session).stored("example:ticket", TICKET);
		operations.execute(CookiesOperations.REMOVE, new CookieKey("example:ticket"));
		packets.listener(session).closed();

		assertEquals(List.of(1L, 2L, 3L, 4L), player.events(CookiesOperations.HELD).stream().map(HeldCookies::getVersion).toList());
	}

	@Test
	void keepsACopyOfEveryPayload() {
		RecordingPackets packets = new RecordingPackets();
		RecordingPlayer player = new RecordingPlayer();
		RecordingOperations operations = new RecordingOperations();
		new CookiesBinding<>(packets).bind(player, operations);
		NativeSession session = new NativeSession();
		player.startSession(session);
		byte[] payload = TICKET.clone();

		packets.listener(session).stored("example:ticket", payload);
		payload[0] = 'X';
		packets.listener(session).requested("example:ticket");

		assertArrayEquals(TICKET, packets.answers.getLast());
	}

	@Test
	void removesTheListenerOfTheCurrentSessionWhenClosed() {
		RecordingPackets packets = new RecordingPackets();
		RecordingPlayer player = new RecordingPlayer();
		WorkerBinding binding = new CookiesBinding<>(packets).bind(player, new RecordingOperations());
		player.startSession(new NativeSession());

		binding.close();

		assertEquals(Map.of(), packets.listeners);
	}

	@Test
	void refusesToListenToASessionOfAnotherType() {
		RecordingPackets packets = new RecordingPackets();
		RecordingPlayer player = new RecordingPlayer();
		new CookiesBinding<>(packets).bind(player, new RecordingOperations());

		assertThrows(ClassCastException.class, () -> player.startSession("not a native session"));

		assertTrue(packets.listeners.isEmpty());
	}

	private static final class NativeSession {
	}

	/**
	 * Records the answers the binding sends and hands out the listener of each session.
	 */
	private static final class RecordingPackets implements CookiesPackets<NativeSession> {
		private final List<String> answeredKeys = new ArrayList<>();
		private final List<byte @Nullable []> answers = new ArrayList<>();
		private final List<NativeSession> answeredSessions = new ArrayList<>();
		private final Map<NativeSession, CookiesPacketListener> listeners = new LinkedHashMap<>();

		private CookiesPacketListener listener(NativeSession session) {
			return listeners.get(session);
		}

		@Override
		public @NotNull Class<NativeSession> sessionType() {
			return NativeSession.class;
		}

		@Override
		public void answer(@NotNull NativeSession session, @NotNull String key, byte @Nullable [] payload) {
			answeredSessions.add(session);
			answeredKeys.add(key);
			answers.add(payload);
		}

		@Override
		public @NotNull Runnable listen(@NotNull NativeSession session, @NotNull CookiesPacketListener listener) {
			listeners.put(session, listener);
			return () -> listeners.remove(session);
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
	 * A player whose sessions start when the test says so, recording what the binding emits.
	 */
	private static final class RecordingPlayer implements PlayerBindingContext<Object> {
		private final List<Object[]> emitted = new ArrayList<>();
		private @Nullable Object session;
		private @Nullable Function<Object, Subscription> listener;
		private @Nullable Subscription generation;

		private void startSession(Object nativeSession) {
			if (generation != null) generation.close();
			generation = null;
			session = nativeSession;
			if (listener != null) generation = listener.apply(nativeSession);
		}

		@SuppressWarnings("unchecked")
		private <E> List<E> events(EventDescriptor<E> descriptor) {
			return emitted.stream()
					.filter(event -> event[0] == descriptor)
					.map(event -> (E) event[1])
					.toList();
		}

		private HeldCookies lastHeld() {
			return events(CookiesOperations.HELD).getLast();
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
			throw new AssertionError("Cookies do not use the view rotation");
		}

		@Override
		public void viewRotation(@NotNull ViewRotation rotation) {
			throw new AssertionError("Cookies do not change the view rotation");
		}

		@Override
		public <E> void emit(@NotNull EventDescriptor<E> eventDescriptor, @Nullable E payload) {
			emitted.add(new Object[]{eventDescriptor, payload});
		}
	}
}
