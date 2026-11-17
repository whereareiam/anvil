package me.whereareiam.anvil.capability.overlays;

import me.whereareiam.anvil.capability.overlays.channel.OverlaysChannel;
import me.whereareiam.anvil.capability.protocol.api.player.TestProtocolPlayerCapabilityContext;
import me.whereareiam.anvil.protocol.api.channel.TestProtocolChannel;
import me.whereareiam.anvil.protocol.java.api.JavaProtocol;
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
		assertEquals(OverlaysChannel.ID, provider.descriptor().getId());
		assertTrue(provider.descriptor().getRequiredCapabilities().isEmpty());
		assertEquals(Set.of(JavaProtocol.class), provider.requiredProtocols());
		assertEquals(Overlays.class, provider.capability());
	}

	@Test
	void observesTitlesActionBarsAndBossBarsThroughThePlayersChannelWithoutRequesting() {
		TestProtocolChannel channel = new TestProtocolChannel(Set.of(OverlaysChannel.ID));
		Overlays overlays = new OverlaysProvider().create(new TestProtocolPlayerCapabilityContext(channel));

		assertEquals(List.of(OverlaysChannel.TITLE.getId(), OverlaysChannel.ACTION_BAR.getId(), OverlaysChannel.BOSS_BAR.getId()),
				channel.subscriptions());
		assertTrue(channel.requests().isEmpty());
		assertTrue(overlays.bossBars().isEmpty());
	}
}
