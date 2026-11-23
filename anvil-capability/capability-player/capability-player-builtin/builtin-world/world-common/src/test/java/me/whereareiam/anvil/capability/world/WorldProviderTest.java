package me.whereareiam.anvil.capability.world;

import me.whereareiam.anvil.capability.protocol.api.player.TestProtocolPlayerCapabilityContext;
import me.whereareiam.anvil.capability.world.channel.WorldChannel;
import me.whereareiam.anvil.edition.java.protocol.JavaProtocol;
import me.whereareiam.anvil.player.api.channel.TestProtocolChannel;
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
		assertEquals(WorldChannel.ID, provider.descriptor().id());
		assertTrue(provider.descriptor().requiredCapabilities().isEmpty());
		assertEquals(Set.of(JavaProtocol.class), provider.requiredProtocols());
		assertEquals(World.class, provider.capability());
	}

	@Test
	void observesDimensionsThroughThePlayersChannelWithoutRequesting() {
		TestProtocolChannel channel = new TestProtocolChannel(Set.of(WorldChannel.ID));
		World world = new WorldProvider().create(new TestProtocolPlayerCapabilityContext(channel));

		assertEquals(List.of(WorldChannel.SERVER.id(), WorldChannel.DIMENSION.id(), WorldChannel.LEFT.id(),
				WorldChannel.STATE.id(), WorldChannel.CHANGES.id()), channel.subscriptions());
		assertTrue(channel.requests().isEmpty());
		assertTrue(world.dimension().isEmpty());
	}
}
