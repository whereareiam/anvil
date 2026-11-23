package me.whereareiam.anvil.capability.overlays;

import me.whereareiam.anvil.capability.overlays.channel.OverlaysChannel;
import me.whereareiam.anvil.capability.protocol.api.player.TestProtocolPlayerCapabilityContext;
import me.whereareiam.anvil.edition.java.protocol.JavaProtocol;
import me.whereareiam.anvil.player.api.channel.TestProtocolChannel;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OverlaysProviderTest {
	@Test
	void describesTheOverlaysCapabilityForTheLibrariesWithAWorkerSide() {
		OverlaysProvider provider = new OverlaysProvider();

		assertEquals("me.whereareiam.anvil.overlays", OverlaysChannel.ID);
		assertEquals(OverlaysChannel.ID, provider.descriptor().id());
		assertTrue(provider.descriptor().requiredCapabilities().isEmpty());
		assertEquals(Set.of(JavaProtocol.class), provider.requiredProtocols());
		assertEquals(Overlays.class, provider.capability());
	}

	@Test
	void observesTitlesActionBarsAndBossBarsThroughThePlayersChannelWithoutRequesting() {
		TestProtocolChannel channel = new TestProtocolChannel(Set.of(OverlaysChannel.ID));
		Overlays overlays = new OverlaysProvider().create(new TestProtocolPlayerCapabilityContext(channel));

		assertEquals(List.of(OverlaysChannel.TITLE.id(), OverlaysChannel.ACTION_BAR.id(), OverlaysChannel.BOSS_BAR.id()),
				channel.subscriptions());
		assertTrue(channel.requests().isEmpty());
		assertTrue(overlays.bossBars().isEmpty());
	}
}
