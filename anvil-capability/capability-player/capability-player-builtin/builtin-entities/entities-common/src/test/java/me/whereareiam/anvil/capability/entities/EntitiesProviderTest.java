package me.whereareiam.anvil.capability.entities;

import me.whereareiam.anvil.capability.entities.channel.EntitiesChannel;
import me.whereareiam.anvil.capability.protocol.api.player.TestProtocolPlayerCapabilityContext;
import me.whereareiam.anvil.edition.java.protocol.JavaProtocol;
import me.whereareiam.anvil.player.api.channel.TestProtocolChannel;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntitiesProviderTest {
	@Test
	void describesTheEntitiesCapabilityForTheLibrariesWithAWorkerSide() {
		EntitiesProvider provider = new EntitiesProvider();

		assertEquals("me.whereareiam.anvil.entities", EntitiesChannel.ID);
		assertEquals(EntitiesChannel.ID, provider.descriptor().getId());
		assertTrue(provider.descriptor().getRequiredCapabilities().isEmpty());
		assertEquals(Set.of(JavaProtocol.class), provider.requiredProtocols());
		assertEquals(Entities.class, provider.capability());
	}

	@Test
	void observesEntityChangesThroughThePlayersChannelWithoutRequestingOnCreation() {
		TestProtocolChannel channel = new TestProtocolChannel(Set.of(EntitiesChannel.ID));
		Entities entities = new EntitiesProvider().create(new TestProtocolPlayerCapabilityContext(channel));

		assertEquals(List.of(EntitiesChannel.CHANGED.getId()), channel.subscriptions());
		assertTrue(channel.requests().isEmpty());
		assertTrue(entities.watch().unread().isEmpty());
	}
}
