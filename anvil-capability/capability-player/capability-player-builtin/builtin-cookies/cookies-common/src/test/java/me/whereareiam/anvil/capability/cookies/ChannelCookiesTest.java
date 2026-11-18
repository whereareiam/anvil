package me.whereareiam.anvil.capability.cookies;

import me.whereareiam.anvil.capability.cookies.channel.CookiesChannel;
import me.whereareiam.anvil.capability.cookies.model.CookieKey;
import me.whereareiam.anvil.capability.cookies.model.CookieRequest;
import me.whereareiam.anvil.capability.cookies.model.HeldCookies;
import me.whereareiam.anvil.capability.cookies.model.StoredCookie;
import me.whereareiam.anvil.player.api.channel.TestProtocolChannel;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
	void keepsEveryRequestInOrderWithItsAnswer() {
		TestProtocolChannel channel = new TestProtocolChannel(Set.of(CookiesChannel.ID));
		Cookies cookies = new ChannelCookies(channel);

		channel.publish(CookiesChannel.REQUESTED, new CookieRequest("example:ticket", TICKET));
		channel.publish(CookiesChannel.REQUESTED, new CookieRequest("example:ticket", null));

		List<CookieRequest> requests = cookies.requests();
		assertEquals(2, requests.size());
		assertArrayEquals(TICKET, requests.get(0).getAnswer());
		assertNull(requests.get(1).getAnswer());
	}

	@Test
	void watchesOnlyRequestsMadeSinceItAndContinuesAfterEachMatch() {
		TestProtocolChannel channel = new TestProtocolChannel(Set.of(CookiesChannel.ID));
		Cookies cookies = new ChannelCookies(channel);
		channel.publish(CookiesChannel.REQUESTED, new CookieRequest("example:ticket", TICKET));
		CookieWatch asked = cookies.watch();

		assertThrows(IllegalStateException.class, () -> asked.requested("example:ticket", WAIT));
		assertFalse(asked.expectRequested("example:ticket").met());
		asked.neverRequested("example:ticket", WAIT);

		channel.publish(CookiesChannel.REQUESTED, new CookieRequest("example:ticket", null));

		assertTrue(asked.expectRequested("example:ticket").met());
		assertThrows(IllegalStateException.class, () -> asked.neverRequested("example:ticket", WAIT));
		assertNull(asked.requested("example:ticket", WAIT).getAnswer());
		assertEquals(List.of(), asked.unread());
		assertThrows(IllegalStateException.class, () -> asked.requested("example:ticket", WAIT));
		asked.neverRequested("example:ticket", WAIT);
	}

	@Test
	void readsAKeyWithoutANamespaceAsAMinecraftKey() {
		TestProtocolChannel channel = new TestProtocolChannel(Set.of(CookiesChannel.ID));
		Cookies cookies = new ChannelCookies(channel);
		channel.publish(CookiesChannel.HELD, new HeldCookies(1, Map.of("minecraft:ticket", TICKET)));

		CookieWatch asked = cookies.watch();
		channel.publish(CookiesChannel.REQUESTED, new CookieRequest("minecraft:ticket", TICKET));

		assertArrayEquals(TICKET, asked.requested("ticket", WAIT).getAnswer());
		assertArrayEquals(TICKET, cookies.stored("ticket", WAIT));
	}

	@Test
	void keepsTheLatestHeldCookiesWhicheverWayTheyArrive() {
		TestProtocolChannel channel = new TestProtocolChannel(Set.of(CookiesChannel.ID));
		Cookies cookies = new ChannelCookies(channel);
		channel.answer(CookiesChannel.STORE, cookie -> new HeldCookies(3, Map.of("example:ticket", TICKET)));

		cookies.store("example:ticket", TICKET);
		channel.publish(CookiesChannel.HELD, new HeldCookies(2, Map.of()));

		assertArrayEquals(TICKET, cookies.stored().get("example:ticket"));
		channel.publish(CookiesChannel.HELD, new HeldCookies(4, Map.of()));
		assertEquals(Map.of(), cookies.stored());
	}

	@Test
	void sendsNormalizedKeysAndCopiesOfPayloads() {
		TestProtocolChannel channel = new TestProtocolChannel(Set.of(CookiesChannel.ID));
		channel.answer(CookiesChannel.STORE, cookie -> new HeldCookies(1, Map.of()));
		channel.answer(CookiesChannel.REMOVE, key -> new HeldCookies(2, Map.of()));
		Cookies cookies = new ChannelCookies(channel);
		byte[] payload = TICKET.clone();

		cookies.store("ticket", payload);
		payload[0] = 'X';
		cookies.remove("example:ticket");

		assertEquals(List.of(CookiesChannel.STORE.getId(), CookiesChannel.REMOVE.getId()), channel.requests());
		StoredCookie stored = (StoredCookie) channel.payloads().get(0);
		assertEquals("minecraft:ticket", stored.getKey());
		assertArrayEquals(TICKET, stored.getPayload());
		assertEquals(new CookieKey("example:ticket"), channel.payloads().get(1));
	}

	@Test
	void returnsCopiesOfTheHeldPayloads() {
		TestProtocolChannel channel = new TestProtocolChannel(Set.of(CookiesChannel.ID));
		Cookies cookies = new ChannelCookies(channel);
		channel.publish(CookiesChannel.HELD, new HeldCookies(1, Map.of("example:ticket", TICKET.clone())));

		cookies.stored().get("example:ticket")[0] = 'X';
		cookies.stored("example:ticket", WAIT)[1] = 'X';

		assertArrayEquals(TICKET, cookies.stored().get("example:ticket"));
	}

	@Test
	void refusesAnInvalidKeyOrAPayloadLargerThanACookieHolds() {
		TestProtocolChannel channel = new TestProtocolChannel(Set.of(CookiesChannel.ID));
		channel.answer(CookiesChannel.STORE, cookie -> new HeldCookies(1, Map.of()));
		Cookies cookies = new ChannelCookies(channel);

		assertThrows(IllegalArgumentException.class, () -> cookies.store("Example:Ticket", TICKET));
		assertThrows(IllegalArgumentException.class, () -> cookies.store("example:ticket", new byte[Cookies.MAX_PAYLOAD + 1]));

		cookies.store("example:ticket", new byte[Cookies.MAX_PAYLOAD]);
		assertEquals(List.of(CookiesChannel.STORE.getId()), channel.requests());
	}

	@Test
	void holdsNoCookiesBeforeTheWorkerReportsAny() {
		Cookies cookies = new ChannelCookies(new TestProtocolChannel(Set.of(CookiesChannel.ID)));

		assertEquals(Map.of(), cookies.stored());
		assertEquals(List.of(), cookies.requests());
		assertThrows(IllegalStateException.class, () -> cookies.stored("example:ticket", WAIT));
	}
}
