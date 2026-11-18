package me.whereareiam.anvil.capability.cookies;

import me.whereareiam.anvil.capability.cookies.channel.CookiesChannel;
import me.whereareiam.anvil.capability.protocol.api.player.TestProtocolPlayerCapabilityContext;
import me.whereareiam.anvil.edition.java.protocol.JavaProtocol;
import me.whereareiam.anvil.player.api.channel.TestProtocolChannel;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CookiesProviderTest {
	@Test
	void describesTheCookiesCapabilityForTheLibrariesWithAWorkerSide() {
		CookiesProvider provider = new CookiesProvider();

		assertEquals("me.whereareiam.anvil.cookies", CookiesChannel.ID);
		assertEquals(CookiesChannel.ID, provider.descriptor().getId());
		assertTrue(provider.descriptor().getRequiredCapabilities().isEmpty());
		assertEquals(Set.of(JavaProtocol.class), provider.requiredProtocols());
		assertEquals(Cookies.class, provider.capability());
	}

	@Test
	void observesRequestsAndHeldCookiesWithoutRequestingOnCreation() {
		TestProtocolChannel channel = new TestProtocolChannel(Set.of(CookiesChannel.ID));
		new CookiesProvider().create(new TestProtocolPlayerCapabilityContext(channel));

		assertEquals(List.of(CookiesChannel.REQUESTED.getId(), CookiesChannel.HELD.getId()), channel.subscriptions());
		assertTrue(channel.requests().isEmpty());
	}
}
