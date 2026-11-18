package me.whereareiam.anvil.capability.sounds;

import me.whereareiam.anvil.capability.protocol.api.player.TestProtocolPlayerCapabilityContext;
import me.whereareiam.anvil.capability.sounds.channel.SoundsChannel;
import me.whereareiam.anvil.edition.java.protocol.JavaProtocol;
import me.whereareiam.anvil.player.api.channel.TestProtocolChannel;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SoundsProviderTest {
	@Test
	void describesTheSoundsCapabilityForTheLibrariesWithAWorkerSide() {
		SoundsProvider provider = new SoundsProvider();

		assertEquals("me.whereareiam.anvil.sounds", SoundsChannel.ID);
		assertEquals(SoundsChannel.ID, provider.descriptor().getId());
		assertTrue(provider.descriptor().getRequiredCapabilities().isEmpty());
		assertEquals(Set.of(JavaProtocol.class), provider.requiredProtocols());
		assertEquals(Sounds.class, provider.capability());
	}

	@Test
	void observesHeardSoundsThroughThePlayersChannelWithoutRequesting() {
		TestProtocolChannel channel = new TestProtocolChannel(Set.of(SoundsChannel.ID));
		Sounds sounds = new SoundsProvider().create(new TestProtocolPlayerCapabilityContext(channel));

		assertEquals(List.of(SoundsChannel.HEARD.getId()), channel.subscriptions());
		assertTrue(channel.requests().isEmpty());
		assertTrue(sounds.history().isEmpty());
	}
}
