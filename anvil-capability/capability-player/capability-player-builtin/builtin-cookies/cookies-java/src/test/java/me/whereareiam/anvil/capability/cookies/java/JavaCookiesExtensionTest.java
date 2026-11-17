package me.whereareiam.anvil.capability.cookies.java;

import me.whereareiam.anvil.capability.cookies.channel.CookiesChannel;
import me.whereareiam.anvil.capability.cookies.model.CookieKey;
import me.whereareiam.anvil.capability.cookies.model.CookieRequest;
import me.whereareiam.anvil.capability.cookies.model.HeldCookies;
import me.whereareiam.anvil.capability.cookies.model.StoredCookie;
import me.whereareiam.anvil.protocol.api.exception.ProtocolUnavailableException;
import me.whereareiam.anvil.protocol.api.worker.WorkerBinding;
import me.whereareiam.anvil.protocol.api.worker.WorkerExtension;
import me.whereareiam.anvil.protocol.java.api.packet.cookie.ClientboundCookieRequest;
import me.whereareiam.anvil.protocol.java.api.packet.cookie.ClientboundStoreCookie;
import me.whereareiam.anvil.protocol.java.api.packet.cookie.ServerboundCookieResponse;
import me.whereareiam.anvil.protocol.java.worker.TestJavaPlayer;
import me.whereareiam.anvil.protocol.worker.TestOperations;
import me.whereareiam.anvil.protocol.worker.TestWorkerPlayer;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JavaCookiesExtensionTest {
	private static final byte[] TICKET = "ticket".getBytes(StandardCharsets.UTF_8);

	private final JavaCookiesExtension extension = new JavaCookiesExtension();
	private final TestJavaPlayer player = new TestJavaPlayer("Alice");
	private final TestOperations operations = new TestOperations();

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
		extension.bind(player, operations);
		player.connect();

		player.deliver(new ClientboundStoreCookie("example:ticket", TICKET));
		player.deliver(new ClientboundCookieRequest("example:ticket"));
		player.deliver(new ClientboundCookieRequest("example:other"));

		List<ServerboundCookieResponse> sent = player.sent(ServerboundCookieResponse.class);
		assertEquals(List.of("cookies.store", "cookies.remove"), operations.registered());
		assertEquals(List.of("example:ticket", "example:other"),
				sent.stream().map(ServerboundCookieResponse::getKey).toList());
		assertArrayEquals(TICKET, sent.get(0).getPayload());
		assertNull(sent.get(1).getPayload());

		List<CookieRequest> requests = player.emitted(CookiesChannel.REQUESTED);
		assertEquals("example:ticket", requests.get(0).getKey());
		assertArrayEquals(TICKET, requests.get(0).getAnswer());
		assertNull(requests.get(1).getAnswer());
	}

	@Test
	void dropsTheCookiesOfAConnectionWhenItEnds() {
		extension.bind(player, operations);
		player.connect();
		player.deliver(new ClientboundStoreCookie("example:ticket", TICKET));
		player.disconnect();
		Map<String, byte[]> afterEnd = lastHeld().getCookies();

		player.connect();
		player.deliver(new ClientboundCookieRequest("example:ticket"));

		assertEquals(Map.of(), afterEnd);
		assertNull(lastSent().getPayload());
		assertEquals(Map.of(), lastHeld().getCookies());
	}

	@Test
	void startsTheNextConnectionWithTheCookiesGivenWhileDisconnected() {
		extension.bind(player, operations);

		HeldCookies before = operations.call(CookiesChannel.STORE, new StoredCookie("example:ticket", TICKET));
		player.connect();
		player.deliver(new ClientboundCookieRequest("example:ticket"));

		assertArrayEquals(TICKET, before.getCookies().get("example:ticket"));
		assertArrayEquals(TICKET, lastSent().getPayload());
		assertArrayEquals(TICKET, lastHeld().getCookies().get("example:ticket"));
	}

	@Test
	void givesCookiesToTheNextConnectionOnceTheOpenOneEnded() {
		extension.bind(player, operations);
		player.connect();
		player.deliver(new ClientboundStoreCookie("example:ticket", TICKET));
		player.disconnect();

		HeldCookies given = operations.call(CookiesChannel.STORE, new StoredCookie("example:arrival", TICKET));
		player.connect();
		player.deliver(new ClientboundCookieRequest("example:ticket"));
		player.deliver(new ClientboundCookieRequest("example:arrival"));

		List<ServerboundCookieResponse> sent = player.sent(ServerboundCookieResponse.class);
		assertEquals(List.of("example:arrival"), List.copyOf(given.getCookies().keySet()));
		assertNull(sent.get(0).getPayload());
		assertArrayEquals(TICKET, sent.get(1).getPayload());
	}

	@Test
	void givesAndTakesAwayCookiesOfTheOpenConnection() {
		extension.bind(player, operations);
		player.connect();

		operations.call(CookiesChannel.STORE, new StoredCookie("example:ticket", TICKET));
		player.deliver(new ClientboundCookieRequest("example:ticket"));
		HeldCookies removed = operations.call(CookiesChannel.REMOVE, new CookieKey("example:ticket"));
		player.deliver(new ClientboundCookieRequest("example:ticket"));

		List<ServerboundCookieResponse> sent = player.sent(ServerboundCookieResponse.class);
		assertArrayEquals(TICKET, sent.get(0).getPayload());
		assertNull(sent.get(1).getPayload());
		assertEquals(Map.of(), removed.getCookies());
	}

	@Test
	void numbersEveryChangeOfTheHeldCookies() {
		extension.bind(player, operations);

		player.connect();
		player.deliver(new ClientboundStoreCookie("example:ticket", TICKET));
		operations.call(CookiesChannel.REMOVE, new CookieKey("example:ticket"));
		player.rejoin();

		// The replaced connection ends before the next one starts: both are changes of the held cookies.
		List<Long> versions = player.emitted(CookiesChannel.HELD).stream().map(HeldCookies::getVersion).toList();
		assertEquals(List.of(1L, 2L, 3L, 4L, 5L), versions);
	}

	@Test
	void keepsACopyOfEveryPayload() {
		extension.bind(player, operations);
		player.connect();
		byte[] payload = TICKET.clone();

		player.deliver(new ClientboundStoreCookie("example:ticket", payload));
		payload[0] = 'X';
		player.deliver(new ClientboundCookieRequest("example:ticket"));

		assertArrayEquals(TICKET, lastSent().getPayload());
	}

	@Test
	void stopsListeningAndObservingConnectionsWhenClosed() {
		WorkerBinding binding = extension.bind(player, operations);
		player.connect();

		binding.close();
		List<String> emitted = player.emittedEvents();
		player.rejoin();

		assertFalse(player.protocol().wants(ClientboundCookieRequest.class));
		assertFalse(player.protocol().wants(ClientboundStoreCookie.class));
		assertEquals(emitted, player.emittedEvents());
	}

	@Test
	void isUnavailableForAReleaseWithoutCookies() {
		TestJavaPlayer old = new TestJavaPlayer("Alice", type -> false);

		assertThrows(ProtocolUnavailableException.class, () -> extension.bind(old, operations));
		old.connect();

		assertEquals(List.of(), operations.registered());
		assertFalse(old.protocol().wants(ClientboundCookieRequest.class));
		assertFalse(old.protocol().wants(ClientboundStoreCookie.class));
		assertEquals(List.of(), old.emittedEvents());
	}

	@Test
	void isUnavailableForAPlayerThatDoesNotSpeakTheJavaProtocol() {
		TestWorkerPlayer silent = new TestWorkerPlayer("Alice");

		assertThrows(ProtocolUnavailableException.class, () -> extension.bind(silent, operations));
		silent.connect();

		assertEquals(List.of(), operations.registered());
		assertEquals(List.of(), silent.emittedEvents());
	}

	private HeldCookies lastHeld() {
		return player.emitted(CookiesChannel.HELD).getLast();
	}

	private ServerboundCookieResponse lastSent() {
		return player.sent(ServerboundCookieResponse.class).getLast();
	}
}
