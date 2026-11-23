package me.whereareiam.anvil.capability.session;

import me.whereareiam.anvil.capability.protocol.api.player.TestProtocolPlayerCapabilityContext;
import me.whereareiam.anvil.player.api.channel.TestProtocolChannel;
import me.whereareiam.anvil.player.api.model.player.PlayerConnectionEvent;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionProviderTest {
	@Test
	void describesTheSessionCapabilityForTheLibrariesWhoseWorkersBindSessions() {
		SessionProvider provider = new SessionProvider();

		assertEquals(SessionProvider.ID, provider.descriptor().id());
		assertEquals(SessionProvider.ID, new SessionBinding().id());
		assertTrue(provider.descriptor().requiredCapabilities().isEmpty());
		assertTrue(provider.workerBacked());
		assertEquals(Session.class, provider.capability());
	}

	@Test
	void createsASessionThatFollowsConnectionEventsAndRequestsThroughThePlayersChannel() {
		TestProtocolChannel channel = new TestProtocolChannel(Set.of(SessionProvider.ID));
		channel.answer(SessionOperations.CONNECT, ignored -> null);
		Session session = new SessionProvider().create(new TestProtocolPlayerCapabilityContext(channel));
		assertEquals(List.of(PlayerConnectionEvent.CHANGED.id(), PlayerConnectionEvent.DESTROYED.id()), channel.subscriptions());
		assertTrue(channel.requests().isEmpty());

		session.connect();

		assertEquals(List.of(SessionOperations.CONNECT.id()), channel.requests());
	}
}
