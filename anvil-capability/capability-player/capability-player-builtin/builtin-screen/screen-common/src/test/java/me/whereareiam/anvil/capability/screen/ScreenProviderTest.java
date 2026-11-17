package me.whereareiam.anvil.capability.screen;

import me.whereareiam.anvil.capability.protocol.api.player.TestProtocolPlayerCapabilityContext;
import me.whereareiam.anvil.capability.screen.channel.ScreenChannel;
import me.whereareiam.anvil.protocol.api.channel.TestProtocolChannel;
import me.whereareiam.anvil.protocol.java.api.JavaProtocol;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScreenProviderTest {
	@Test
	void describesTheScreenCapabilityForTheLibrariesWithAWorkerSide() {
		ScreenProvider provider = new ScreenProvider();

		assertEquals("me.whereareiam.anvil.screen", ScreenChannel.ID);
		assertEquals(ScreenChannel.ID, provider.descriptor().getId());
		assertTrue(provider.descriptor().getRequiredCapabilities().isEmpty());
		assertEquals(Set.of(JavaProtocol.class), provider.requiredProtocols());
		assertEquals(Screen.class, provider.capability());
	}

	@Test
	void observesTitlesActionBarsAndBossBarsThroughThePlayersChannelWithoutRequesting() {
		TestProtocolChannel channel = new TestProtocolChannel(Set.of(ScreenChannel.ID));
		Screen screen = new ScreenProvider().create(new TestProtocolPlayerCapabilityContext(channel));

		assertEquals(List.of(ScreenChannel.TITLE.getId(), ScreenChannel.ACTION_BAR.getId(), ScreenChannel.BOSS_BAR.getId()),
				channel.subscriptions());
		assertTrue(channel.requests().isEmpty());
		assertTrue(screen.bossBars().isEmpty());
	}
}
