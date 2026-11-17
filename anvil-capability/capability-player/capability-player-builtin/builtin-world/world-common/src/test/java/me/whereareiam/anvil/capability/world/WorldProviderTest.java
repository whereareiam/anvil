package me.whereareiam.anvil.capability.world;

import me.whereareiam.anvil.api.model.player.PlayerConnection;
import me.whereareiam.anvil.api.model.player.PlayerProfile;
import me.whereareiam.anvil.api.player.PlayerCapability;
import me.whereareiam.anvil.capability.api.event.CapabilityEvent;
import me.whereareiam.anvil.capability.protocol.api.player.ProtocolPlayerCapabilityContext;
import me.whereareiam.anvil.capability.world.channel.WorldChannel;
import me.whereareiam.anvil.protocol.api.channel.ProtocolChannel;
import me.whereareiam.anvil.protocol.api.channel.TestProtocolChannel;
import me.whereareiam.anvil.protocol.java.api.JavaProtocol;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

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
		World world = new WorldProvider().create(new ChannelContext(channel));

		assertEquals(List.of(WorldChannel.SERVER.getId(), WorldChannel.DIMENSION.getId(), WorldChannel.LEFT.getId(),
				WorldChannel.STATE.getId(), WorldChannel.CHANGES.getId()), channel.subscriptions());
		assertTrue(channel.requests().isEmpty());
		assertTrue(world.dimension().isEmpty());
	}

	/**
	 * Supplies only the channel; the world needs no services or capabilities.
	 */
	private record ChannelContext(ProtocolChannel channel) implements ProtocolPlayerCapabilityContext {
		@Override
		public @NotNull <T> Optional<T> findService(@NotNull Class<T> type) {
			return Optional.empty();
		}

		@Override
		public @NotNull String playerName() {
			return "Alice";
		}

		@Override
		public @NotNull String clientVersion() {
			return "1.21.11";
		}

		@Override
		public @NotNull PlayerProfile requestedProfile() {
			return new PlayerProfile("Alice", UUID.nameUUIDFromBytes("Alice".getBytes()));
		}

		@Override
		public @Nullable PlayerProfile grantedProfile() {
			return null;
		}

		@Override
		public @NotNull PlayerConnection connection() {
			return PlayerConnection.to("proxy");
		}


		@Override
		public <E extends CapabilityEvent> void on(@NotNull Class<E> event, @NotNull Consumer<E> action) {
			throw new AssertionError("The world registers no cleanup");
		}

		@Override
		public @NotNull <T extends PlayerCapability> Optional<T> findCapability(@NotNull Class<T> type) {
			return Optional.empty();
		}
	}
}
