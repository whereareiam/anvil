package me.whereareiam.anvil.capability.vitals;

import me.whereareiam.anvil.capability.protocol.api.player.TestProtocolPlayerCapabilityContext;
import me.whereareiam.anvil.capability.vitals.channel.VitalsChannel;
import me.whereareiam.anvil.edition.java.protocol.JavaProtocol;
import me.whereareiam.anvil.player.api.channel.TestProtocolChannel;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VitalsProviderTest {
	@Test
	void describesTheVitalsCapabilityForTheLibrariesWithAWorkerSide() {
		VitalsProvider provider = new VitalsProvider();

		assertEquals("me.whereareiam.anvil.vitals", VitalsChannel.ID);
		assertEquals(VitalsChannel.ID, provider.descriptor().id());
		assertTrue(provider.descriptor().requiredCapabilities().isEmpty());
		assertEquals(Set.of(JavaProtocol.class), provider.requiredProtocols());
		assertEquals(Vitals.class, provider.capability());
	}

	@Test
	void observesTheStatusAndDeathsThroughThePlayersChannelWithoutRequesting() {
		TestProtocolChannel channel = new TestProtocolChannel(Set.of(VitalsChannel.ID));
		Vitals vitals = new VitalsProvider().create(new TestProtocolPlayerCapabilityContext(channel));

		assertEquals(List.of(VitalsChannel.STATUS.id(), VitalsChannel.DIED.id()), channel.subscriptions());
		assertTrue(channel.requests().isEmpty());
		assertTrue(vitals.deaths().isEmpty());
	}
}
