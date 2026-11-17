package me.whereareiam.anvil.capability.packets;

import me.whereareiam.anvil.capability.packets.channel.PacketsChannel;
import me.whereareiam.anvil.capability.protocol.api.player.TestProtocolPlayerCapabilityContext;
import me.whereareiam.anvil.protocol.api.channel.TestProtocolChannel;
import me.whereareiam.anvil.protocol.java.api.JavaProtocol;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PacketsProviderTest {
	@Test
	void describesThePacketsCapabilityForTheLibrariesWithAWorkerSide() {
		PacketsProvider provider = new PacketsProvider();

		assertEquals("me.whereareiam.anvil.packets", PacketsChannel.ID);
		assertEquals(PacketsChannel.ID, provider.descriptor().getId());
		assertTrue(provider.descriptor().getRequiredCapabilities().isEmpty());
		assertEquals(Set.of(JavaProtocol.class), provider.requiredProtocols());
		assertEquals(Packets.class, provider.capability());
	}

	@Test
	void receivesFramesThroughThePlayersChannelWithoutOpeningATap() {
		TestProtocolChannel channel = new TestProtocolChannel(Set.of(PacketsChannel.ID));

		new PacketsProvider().create(new TestProtocolPlayerCapabilityContext(channel));

		assertEquals(List.of(PacketsChannel.FRAMES.getId()), channel.subscriptions());
		assertTrue(channel.requests().isEmpty());
	}
}
