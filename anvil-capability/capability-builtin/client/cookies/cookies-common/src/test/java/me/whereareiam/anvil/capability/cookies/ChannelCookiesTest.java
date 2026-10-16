package me.whereareiam.anvil.capability.cookies;

import me.whereareiam.anvil.capability.api.model.channel.ChannelOperation;
import me.whereareiam.anvil.capability.cookies.exchange.AnsweredRequest;
import me.whereareiam.anvil.capability.cookies.exchange.CookieKey;
import me.whereareiam.anvil.capability.cookies.exchange.HeldCookies;
import me.whereareiam.anvil.capability.cookies.exchange.StoredCookie;
import me.whereareiam.anvil.capability.cookies.model.CookieRequest;
import me.whereareiam.anvil.capability.protocol.api.model.EventDescriptor;
import me.whereareiam.anvil.capability.protocol.api.player.channel.CapabilityChannel;
import me.whereareiam.anvil.capability.protocol.api.player.channel.Subscription;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChannelCookiesTest {
	private static final byte[] TICKET = "ticket".getBytes(StandardCharsets.UTF_8);
	private static final Duration WAIT = Duration.ofSeconds(1);

	@Test
	void keepsEveryRequestInOrderWithItsAnswerAndCheckpoint() {
		RecordingChannel channel = new RecordingChannel();
		Cookies cookies = new ChannelCookies(channel);

		channel.publish(CookiesOperations.REQUESTED, new AnsweredRequest("example:ticket", TICKET));
		channel.publish(CookiesOperations.REQUESTED, new AnsweredRequest("example:ticket", null));

		List<CookieRequest> requests = cookies.requests();
		assertEquals(2, cookies.checkpoint());
		assertArrayEquals(TICKET, requests.get(0).getAnswer());
		assertEquals(1, requests.get(0).getCheckpoint());
		assertNull(requests.get(1).getAnswer());
		assertEquals(2, requests.get(1).getCheckpoint());
	}

	@Test
	void waitsOnlyForRequestsAfterACheckpoint() {
		RecordingChannel channel = new RecordingChannel();
		Cookies cookies = new ChannelCookies(channel);
		channel.publish(CookiesOperations.REQUESTED, new AnsweredRequest("example:ticket", TICKET));
		int before = cookies.checkpoint();

		assertThrows(IllegalStateException.class, () -> cookies.requested("example:ticket", before, WAIT));
		assertFalse(cookies.expectRequested("example:ticket", before).met());
		cookies.notRequested("example:ticket", before, WAIT);

		channel.publish(CookiesOperations.REQUESTED, new AnsweredRequest("example:ticket", null));

		assertNull(cookies.requested("example:ticket", before, WAIT).getAnswer());
		assertTrue(cookies.expectRequested("example:ticket", before).met());
		assertThrows(IllegalStateException.class, () -> cookies.notRequested("example:ticket", before, WAIT));
	}

	@Test
	void readsAKeyWithoutANamespaceAsAMinecraftKey() {
		RecordingChannel channel = new RecordingChannel();
		Cookies cookies = new ChannelCookies(channel);
		channel.publish(CookiesOperations.REQUESTED, new AnsweredRequest("minecraft:ticket", TICKET));
		channel.publish(CookiesOperations.HELD, new HeldCookies(1, Map.of("minecraft:ticket", TICKET)));

		assertArrayEquals(TICKET, cookies.requested("ticket", 0, WAIT).getAnswer());
		assertArrayEquals(TICKET, cookies.stored("ticket", WAIT));
	}

	@Test
	void keepsTheLatestHeldCookiesWhicheverWayTheyArrive() {
		RecordingChannel channel = new RecordingChannel();
		Cookies cookies = new ChannelCookies(channel);
		channel.response = new HeldCookies(3, Map.of("example:ticket", TICKET));

		cookies.store("example:ticket", TICKET);
		channel.publish(CookiesOperations.HELD, new HeldCookies(2, Map.of()));

		assertArrayEquals(TICKET, cookies.stored().get("example:ticket"));
		channel.publish(CookiesOperations.HELD, new HeldCookies(4, Map.of()));
		assertEquals(Map.of(), cookies.stored());
	}

	@Test
	void sendsNormalizedKeysAndCopiesOfPayloads() {
		RecordingChannel channel = new RecordingChannel();
		Cookies cookies = new ChannelCookies(channel);
		byte[] payload = TICKET.clone();

		cookies.store("ticket", payload);
		payload[0] = 'X';
		cookies.remove("example:ticket");

		assertEquals(List.of(CookiesOperations.STORE.getId(), CookiesOperations.REMOVE.getId()), channel.requests);
		StoredCookie stored = (StoredCookie) channel.payloads.get(0);
		assertEquals("minecraft:ticket", stored.getKey());
		assertArrayEquals(TICKET, stored.getPayload());
		assertEquals(new CookieKey("example:ticket"), channel.payloads.get(1));
	}

	@Test
	void returnsCopiesOfTheHeldPayloads() {
		RecordingChannel channel = new RecordingChannel();
		Cookies cookies = new ChannelCookies(channel);
		channel.publish(CookiesOperations.HELD, new HeldCookies(1, Map.of("example:ticket", TICKET.clone())));

		cookies.stored().get("example:ticket")[0] = 'X';
		cookies.stored("example:ticket", WAIT)[1] = 'X';

		assertArrayEquals(TICKET, cookies.stored().get("example:ticket"));
	}

	@Test
	void refusesAnInvalidKeyOrAPayloadLargerThanACookieHolds() {
		RecordingChannel channel = new RecordingChannel();
		Cookies cookies = new ChannelCookies(channel);

		assertThrows(IllegalArgumentException.class, () -> cookies.store("Example:Ticket", TICKET));
		assertThrows(IllegalArgumentException.class, () -> cookies.store("example:ticket", new byte[Cookies.MAX_PAYLOAD + 1]));

		cookies.store("example:ticket", new byte[Cookies.MAX_PAYLOAD]);
		assertEquals(List.of(CookiesOperations.STORE.getId()), channel.requests);
	}

	@Test
	void holdsNoCookiesBeforeTheWorkerReportsAny() {
		Cookies cookies = new ChannelCookies(new RecordingChannel());

		assertEquals(Map.of(), cookies.stored());
		assertEquals(List.of(), cookies.requests());
		assertThrows(IllegalStateException.class, () -> cookies.stored("example:ticket", WAIT));
	}

	/**
	 * Records requests, answers them with a set response and delivers published events synchronously; a wait fails at
	 * once when its condition is not met.
	 */
	private static final class RecordingChannel implements CapabilityChannel {
		private final List<String> requests = new ArrayList<>();
		private final List<Object> payloads = new ArrayList<>();
		private final Map<String, List<Consumer<Object>>> listeners = new HashMap<>();
		private @Nullable Object response;

		private <E> void publish(EventDescriptor<E> eventDescriptor, @Nullable E payload) {
			listeners.getOrDefault(eventDescriptor.getId(), List.of()).forEach(listener -> listener.accept(payload));
		}

		@Override
		@SuppressWarnings("unchecked")
		public <Q, R> @Nullable R request(@NotNull ChannelOperation<Q, R> channelOperation, @Nullable Q request) {
			requests.add(channelOperation.getId());
			payloads.add(request);
			return (R) response;
		}

		@Override
		@SuppressWarnings("unchecked")
		public <E> @NotNull Subscription subscribe(@NotNull EventDescriptor<E> eventDescriptor, @NotNull Consumer<E> listener) {
			listeners.computeIfAbsent(eventDescriptor.getId(), ignored -> new ArrayList<>()).add((Consumer<Object>) listener);
			return () -> listeners.get(eventDescriptor.getId()).remove(listener);
		}

		@Override
		public void await(@NotNull BooleanSupplier condition, @NotNull String description, @NotNull Duration timeout) {
			if (!condition.getAsBoolean()) throw new IllegalStateException("Player did not " + description);
		}

		@Override
		public @NotNull Set<String> installedCapabilities() {
			return Set.of(CookiesProvider.ID);
		}
	}
}
