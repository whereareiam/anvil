package me.whereareiam.anvil.capability.world;

import me.whereareiam.anvil.capability.protocol.api.player.TestProtocolPlayerCapabilityContext;
import me.whereareiam.anvil.capability.world.channel.WorldChannel;
import me.whereareiam.anvil.edition.java.protocol.JavaProtocol;
import me.whereareiam.anvil.protocol.api.channel.TestProtocolChannel;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldProviderTest {
	@Test
	void describesTheWorldCapabilityForTheLibrariesWithAWorkerSide() {
		WorldProvider provider = new WorldProvider();

		assertEquals("me.whereareiam.anvil.world", WorldChannel.ID);
		assertEquals(WorldChannel.ID, provider.descriptor().getId());
		assertTrue(provider.descriptor().getRequiredCapabilities().isEmpty());
		assertEquals(Set.of(JavaProtocol.class), provider.requiredProtocols());
		assertEquals(World.class, provider.capability());
	}

	@Test
	void observesDimensionsThroughThePlayersChannelWithoutRequesting() {
		TestProtocolChannel channel = new TestProtocolChannel(Set.of(WorldChannel.ID));
		World world = new WorldProvider().create(new TestProtocolPlayerCapabilityContext(channel));

		assertEquals(List.of(WorldChannel.SERVER.getId(), WorldChannel.DIMENSION.getId(), WorldChannel.LEFT.getId(),
				WorldChannel.STATE.getId(), WorldChannel.CHANGES.getId()), channel.subscriptions());
		assertTrue(channel.requests().isEmpty());
		assertTrue(world.dimension().isEmpty());
	}
}
