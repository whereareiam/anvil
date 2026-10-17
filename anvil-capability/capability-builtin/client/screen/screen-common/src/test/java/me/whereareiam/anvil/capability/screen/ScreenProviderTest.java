package me.whereareiam.anvil.capability.screen;

import me.whereareiam.anvil.api.model.player.PlayerConnection;
import me.whereareiam.anvil.api.model.player.PlayerProfile;
import me.whereareiam.anvil.api.player.PlayerCapability;
import me.whereareiam.anvil.capability.api.model.channel.ChannelOperation;
import me.whereareiam.anvil.capability.protocol.api.model.EventDescriptor;
import me.whereareiam.anvil.capability.protocol.api.player.ProtocolPlayerCapabilityContext;
import me.whereareiam.anvil.capability.protocol.api.player.channel.CapabilityChannel;
import me.whereareiam.anvil.capability.protocol.api.player.channel.Subscription;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScreenProviderTest {
	@Test
	void describesTheScreenCapabilityForTheLibrariesWithAWorkerSide() {
		ScreenProvider provider = new ScreenProvider();

		assertEquals("me.whereareiam.anvil.screen", ScreenProvider.ID);
		assertEquals(ScreenProvider.ID, provider.descriptor().getId());
		assertTrue(provider.descriptor().getRequiredCapabilities().isEmpty());
		assertEquals(Set.of("mcprotocol"), provider.supportedLibraries());
		assertEquals(Screen.class, provider.capability());
	}

	@Test
	void observesTitlesActionBarsAndBossBarsThroughThePlayersChannelWithoutRequesting() {
		RecordingChannel channel = new RecordingChannel();
		Screen screen = new ScreenProvider().create(new ChannelContext(channel));

		assertEquals(List.of(ScreenOperations.TITLE.getId(), ScreenOperations.ACTION_BAR.getId(), ScreenOperations.BOSS_BAR.getId()),
				channel.subscriptions);
		assertTrue(channel.requests.isEmpty());
		assertTrue(screen.bossBars().isEmpty());
	}

	/**
	 * Records requests and subscriptions without delivering events.
	 */
	private static final class RecordingChannel implements CapabilityChannel {
		private final List<String> requests = new ArrayList<>();
				private final List<String> subscriptions = new ArrayList<>();

		@Override
		public <Q, R> @Nullable R request(@NotNull ChannelOperation<Q, R> channelOperation, @Nullable Q request) {
			requests.add(channelOperation.getId());
			return null;
		}

		@Override
		public <E> @NotNull Subscription subscribe(@NotNull EventDescriptor<E> eventDescriptor, @NotNull Consumer<E> listener) {
			subscriptions.add(eventDescriptor.getId());
			return () -> { };
		}

		@Override
		public void await(@NotNull BooleanSupplier condition, @NotNull String description, @NotNull Duration timeout) {
			throw new AssertionError("Creating the screen does not wait");
		}

		@Override
		public @NotNull Set<String> installedCapabilities() {
			return Set.of(ScreenProvider.ID);
		}
	}

	/**
	 * Supplies only the channel; the screen needs no services or capabilities.
	 */
	private record ChannelContext(CapabilityChannel channel) implements ProtocolPlayerCapabilityContext {
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
		public void onDestroy(@NotNull Runnable action) {
			throw new AssertionError("The screen registers no cleanup");
		}

		@Override
		public @NotNull <T extends PlayerCapability> Optional<T> findCapability(@NotNull Class<T> type) {
			return Optional.empty();
		}
	}
}
